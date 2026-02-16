package org.dynamislight.sample;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.event.DeviceLostEvent;
import org.dynamislight.api.runtime.EngineApiVersion;
import org.dynamislight.api.config.EngineConfig;
import org.dynamislight.api.error.EngineErrorReport;
import org.dynamislight.api.event.EngineEvent;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.runtime.EngineFrameResult;
import org.dynamislight.api.runtime.EngineHostCallbacks;
import org.dynamislight.api.input.EngineInput;
import org.dynamislight.api.scene.EnvironmentDesc;
import org.dynamislight.api.scene.FogDesc;
import org.dynamislight.api.scene.FogMode;
import org.dynamislight.api.input.KeyCode;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.logging.LogMessage;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.scene.PostProcessDesc;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.event.ResourceHotReloadedEvent;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.event.SceneLoadFailedEvent;
import org.dynamislight.api.event.SceneLoadedEvent;
import org.dynamislight.api.scene.ShadowDesc;
import org.dynamislight.api.scene.SmokeEmitterDesc;
import org.dynamislight.api.scene.TransformDesc;
import org.dynamislight.api.scene.Vec3;
import org.dynamislight.spi.EngineBackendProvider;
import org.dynamislight.api.validation.EngineConfigValidator;
import org.dynamislight.api.validation.SceneValidator;
import org.dynamislight.spi.registry.BackendRegistry;

public final class SampleHostApp {
    private static final EngineApiVersion HOST_REQUIRED_API = new EngineApiVersion(1, 0, 0);

    private SampleHostApp() {
    }

    public static void main(String[] args) throws Exception {
        String backendId = args.length > 0 ? args[0] : "opengl";
        SceneOptions sceneOptions = parseSceneOptions(args);
        boolean resourceProbe = java.util.Arrays.asList(args).contains("--resources");
        boolean compareMode = java.util.Arrays.asList(args).contains("--compare");
        boolean interactive = java.util.Arrays.asList(args).contains("--interactive");
        boolean overlay = java.util.Arrays.asList(args).contains("--overlay") || interactive;
        int maxFrames = parseIntArg(args, "--frames=", interactive ? Integer.MAX_VALUE : 3, 1, Integer.MAX_VALUE);
        if (compareMode) {
            QualityTier compareTier = parseCompareTier(args);
            String compareTag = parseCompareTag(args, compareTier);
            boolean compareOpenGlMock = parseBooleanArg(
                    args,
                    "--compare-opengl-mock=",
                    Boolean.parseBoolean(System.getProperty("dle.compare.opengl.mockContext", "true"))
            );
            boolean compareVulkanMock = parseBooleanArg(
                    args,
                    "--compare-vulkan-mock=",
                    Boolean.parseBoolean(System.getProperty("dle.compare.vulkan.mockContext", "true"))
            );
            boolean compareVulkanOffscreen = parseBooleanArg(
                    args,
                    "--compare-vulkan-offscreen=",
                    Boolean.parseBoolean(System.getProperty("dle.compare.vulkan.postOffscreen", "true"))
            );
            System.setProperty("dle.compare.opengl.mockContext", Boolean.toString(compareOpenGlMock));
            System.setProperty("dle.compare.vulkan.mockContext", Boolean.toString(compareVulkanMock));
            System.setProperty("dle.compare.vulkan.postOffscreen", Boolean.toString(compareVulkanOffscreen));
            Path outDir = Path.of("artifacts", "compare");
            var report = BackendCompareHarness.run(outDir, defaultScene(sceneOptions), compareTier, compareTag);
            System.out.printf(
                    "compare tier=%s tag=%s glMock=%s vkMock=%s vkOffscreen=%s openGl=%s vulkan=%s diff=%.5f%n",
                    compareTier,
                    compareTag,
                    compareOpenGlMock,
                    compareVulkanMock,
                    compareVulkanOffscreen,
                    report.openGlImage(),
                    report.vulkanImage(),
                    report.diffMetric()
            );
            return;
        }
        EngineBackendProvider provider = resolveProvider(backendId);
        EngineConfig config = defaultConfig(backendId, sceneOptions.qualityTier());
        SceneDescriptor scene = defaultScene(sceneOptions);
        EngineConfigValidator.validate(config);
        SceneValidator.validate(scene);

        try (var runtime = provider.createRuntime()) {
            runtime.initialize(config, new ConsoleCallbacks());
            runtime.loadScene(scene);
            SceneOptions currentOptions = sceneOptions;
            if (resourceProbe) {
                printResources(runtime.resources().loadedResources(), "loaded");
                if (!runtime.resources().loadedResources().isEmpty()) {
                    var first = runtime.resources().loadedResources().getFirst();
                    var reloaded = runtime.resources().reload(first.descriptor().id());
                    System.out.println("reloaded=" + reloaded.descriptor().id().value() + " checksum=" + reloaded.lastChecksum());
                }
            }

            BufferedReader commandReader = interactive ? new BufferedReader(new InputStreamReader(System.in)) : null;
            if (interactive) {
                printInteractiveHelp();
            }
            for (int i = 0; i < maxFrames; i++) {
                EngineFrameResult updateResult = runtime.update(1.0 / 60.0, emptyInput());
                EngineFrameResult renderResult = runtime.render();
                System.out.printf(
                        "frame=%d updateCpuMs=%.2f renderCpuMs=%.2f warnings=%d%n",
                        renderResult.frameIndex(),
                        updateResult.cpuFrameMs(),
                        renderResult.cpuFrameMs(),
                        renderResult.warnings().size()
                );
                if (overlay) {
                    printOverlay(runtime, renderResult, currentOptions);
                }
                if (interactive && commandReader != null) {
                    while (commandReader.ready()) {
                        String line = commandReader.readLine();
                        if (line == null) {
                            break;
                        }
                        CommandResult command = applyInteractiveCommand(line, currentOptions);
                        if (!command.message().isBlank()) {
                            System.out.println(command.message());
                        }
                        if (command.updatedOptions() != null) {
                            currentOptions = command.updatedOptions();
                        }
                        if (command.reloadScene()) {
                            runtime.loadScene(defaultScene(currentOptions));
                        }
                        if (command.quit()) {
                            runtime.shutdown();
                            System.out.println("Shutdown complete.");
                            return;
                        }
                    }
                }
            }

            runtime.shutdown();
            System.out.println("Shutdown complete.");
        }
    }

    private static void printResources(List<org.dynamislight.api.resource.ResourceInfo> resources, String label) {
        System.out.println("resources(" + label + ") count=" + resources.size());
        for (var info : resources) {
            System.out.printf("  id=%s state=%s ref=%d path=%s checksum=%s%n",
                    info.descriptor().id().value(),
                    info.state(),
                    info.refCount(),
                    info.resolvedPath(),
                    info.lastChecksum());
        }
    }

    private static EngineBackendProvider resolveProvider(String backendId) throws EngineException {
        return BackendRegistry.discover().resolve(backendId, HOST_REQUIRED_API);
    }

    private static EngineConfig defaultConfig(String backendId, QualityTier qualityTier) {
        Map<String, String> backendOptions = switch (backendId.toLowerCase()) {
            case "opengl" -> Map.of("opengl.mockContext", System.getProperty("dle.opengl.mockContext", "true"));
            case "vulkan" -> Map.of("vulkan.mockContext", System.getProperty("dle.vulkan.mockContext", "true"));
            default -> Map.of();
        };
        return new EngineConfig(
                backendId,
                "DynamicLightEngine Sample Host",
                1280,
                720,
                1.0f,
                true,
                60,
                qualityTier,
                Path.of("assets"),
                backendOptions
        );
    }

    private static SceneDescriptor defaultScene(SceneOptions options) {
        CameraDesc camera = new CameraDesc("main-cam", new Vec3(0, 2, 5), new Vec3(0, 0, 0), 60f, 0.1f, 1000f);
        TransformDesc transform = new TransformDesc("root", new Vec3(0, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 1, 1));
        MeshDesc mesh = new MeshDesc("mesh-1", "root", "mat-1", "meshes/triangle.glb");
        MaterialDesc material = new MaterialDesc("mat-1", new Vec3(1, 1, 1), 0.1f, 0.7f, null, null);
        ShadowDesc shadow = options.shadowsEnabled()
                ? new ShadowDesc(
                options.shadowMapResolution(),
                options.shadowBias(),
                options.shadowPcfKernel(),
                options.shadowCascades()
        )
                : null;
        LightDesc light = new LightDesc("sun", new Vec3(0, 10, 0), new Vec3(1, 1, 1), 1.0f, 100f, options.shadowsEnabled(), shadow);
        EnvironmentDesc environment = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.12f), 0.25f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);
        PostProcessDesc post = new PostProcessDesc(
                options.postEnabled(),
                options.tonemapEnabled(),
                options.exposure(),
                options.gamma(),
                options.bloomEnabled(),
                options.bloomThreshold(),
                options.bloomStrength()
        );

        return new SceneDescriptor(
                "sample-scene",
                List.of(camera),
                camera.id(),
                List.of(transform),
                List.of(mesh),
                List.of(material),
                List.of(light),
                environment,
                fog,
                List.<SmokeEmitterDesc>of(),
                post
        );
    }

    private static EngineInput emptyInput() {
        return new EngineInput(0, 0, 0, 0, false, false, Set.<KeyCode>of(), 0);
    }

    private static void printOverlay(org.dynamislight.api.runtime.EngineRuntime runtime, EngineFrameResult renderResult, SceneOptions options) {
        var stats = runtime.getStats();
        String warningCodes = renderResult.warnings().isEmpty()
                ? "-"
                : renderResult.warnings().stream().map(w -> w.code()).limit(6).reduce((a, b) -> a + "," + b).orElse("-");
        System.out.printf(
                "overlay fps=%.1f cpu=%.2fms gpu=%.2fms draws=%d tris=%d visible=%d mem=%d tier=%s shadow=%s post=%s tonemap=%s bloom=%s warnings=%s%n",
                stats.fps(),
                stats.cpuFrameMs(),
                stats.gpuFrameMs(),
                stats.drawCalls(),
                stats.triangles(),
                stats.visibleObjects(),
                stats.gpuMemoryBytes(),
                options.qualityTier(),
                options.shadowsEnabled(),
                options.postEnabled(),
                options.tonemapEnabled(),
                options.bloomEnabled(),
                warningCodes
        );
    }

    private static QualityTier parseCompareTier(String[] args) {
        for (String arg : args) {
            if (!arg.startsWith("--compare-tier=")) {
                continue;
            }
            String raw = arg.substring("--compare-tier=".length()).trim();
            if (raw.isEmpty()) {
                break;
            }
            try {
                return QualityTier.valueOf(raw.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                break;
            }
        }
        return QualityTier.MEDIUM;
    }

    private static String parseCompareTag(String[] args, QualityTier tier) {
        for (String arg : args) {
            if (!arg.startsWith("--compare-tag=")) {
                continue;
            }
            String raw = arg.substring("--compare-tag=".length()).trim();
            if (!raw.isEmpty()) {
                return raw;
            }
        }
        return "sample-" + tier.name().toLowerCase();
    }

    private static SceneOptions parseSceneOptions(String[] args) {
        QualityTier tier = parseTierArg(args, "--tier=", QualityTier.MEDIUM);
        boolean shadowsEnabled = parseBooleanArg(args, "--shadow=", false);
        int shadowCascades = parseIntArg(args, "--shadow-cascades=", 3, 1, 4);
        int shadowPcfKernel = parseIntArg(args, "--shadow-pcf=", 3, 1, 9);
        float shadowBias = parseFloatArg(args, "--shadow-bias=", 0.0012f, 0.00002f, 0.02f);
        int shadowMapResolution = parseIntArg(args, "--shadow-res=", 2048, 256, 4096);
        boolean postEnabled = parseBooleanArg(args, "--post=", true);
        boolean tonemapEnabled = parseBooleanArg(args, "--tonemap=", true);
        float exposure = parseFloatArg(args, "--exposure=", 1.05f, 0.25f, 4.0f);
        float gamma = parseFloatArg(args, "--gamma=", 2.2f, 1.2f, 3.0f);
        boolean bloomEnabled = parseBooleanArg(args, "--bloom=", true);
        float bloomThreshold = parseFloatArg(args, "--bloom-threshold=", 1.0f, 0.2f, 2.5f);
        float bloomStrength = parseFloatArg(args, "--bloom-strength=", 0.8f, 0f, 1.6f);
        return new SceneOptions(
                tier,
                shadowsEnabled,
                shadowCascades,
                shadowPcfKernel,
                shadowBias,
                shadowMapResolution,
                postEnabled,
                tonemapEnabled,
                exposure,
                gamma,
                bloomEnabled,
                bloomThreshold,
                bloomStrength
        );
    }

    private static CommandResult applyInteractiveCommand(String rawLine, SceneOptions options) {
        String line = rawLine == null ? "" : rawLine.trim();
        if (line.isEmpty()) {
            return new CommandResult(options, false, false, "");
        }
        String[] parts = line.split("\\s+");
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1] : "";
        try {
            return switch (cmd) {
                case "help" -> new CommandResult(options, false, false, interactiveHelpText());
                case "quit", "exit" -> new CommandResult(options, false, true, "Exiting interactive mode.");
                case "show" -> new CommandResult(options, false, false, "options=" + options);
                case "reload" -> new CommandResult(options, true, false, "Scene reload requested.");
                case "tier" -> new CommandResult(
                        options.withQualityTier(QualityTier.valueOf(arg.toUpperCase())),
                        true,
                        false,
                        "Updated tier=" + arg.toUpperCase()
                );
                case "shadow" -> new CommandResult(options.withShadowsEnabled(parseOnOff(arg)), true, false, "Updated shadow=" + arg);
                case "shadow_cascades" -> new CommandResult(options.withShadowCascades(clampInt(arg, 1, 4)), true, false, "Updated shadow_cascades=" + arg);
                case "shadow_pcf" -> new CommandResult(options.withShadowPcfKernel(clampInt(arg, 1, 9)), true, false, "Updated shadow_pcf=" + arg);
                case "shadow_bias" -> new CommandResult(options.withShadowBias(clampFloat(arg, 0.00002f, 0.02f)), true, false, "Updated shadow_bias=" + arg);
                case "shadow_res" -> new CommandResult(options.withShadowMapResolution(clampInt(arg, 256, 4096)), true, false, "Updated shadow_res=" + arg);
                case "post" -> new CommandResult(options.withPostEnabled(parseOnOff(arg)), true, false, "Updated post=" + arg);
                case "tonemap" -> new CommandResult(options.withTonemapEnabled(parseOnOff(arg)), true, false, "Updated tonemap=" + arg);
                case "exposure" -> new CommandResult(options.withExposure(clampFloat(arg, 0.25f, 4.0f)), true, false, "Updated exposure=" + arg);
                case "gamma" -> new CommandResult(options.withGamma(clampFloat(arg, 1.2f, 3.0f)), true, false, "Updated gamma=" + arg);
                case "bloom" -> new CommandResult(options.withBloomEnabled(parseOnOff(arg)), true, false, "Updated bloom=" + arg);
                case "bloom_threshold" -> new CommandResult(options.withBloomThreshold(clampFloat(arg, 0.2f, 2.5f)), true, false, "Updated bloom_threshold=" + arg);
                case "bloom_strength" -> new CommandResult(options.withBloomStrength(clampFloat(arg, 0f, 1.6f)), true, false, "Updated bloom_strength=" + arg);
                default -> new CommandResult(options, false, false, "Unknown command. Type 'help'.");
            };
        } catch (Exception ex) {
            return new CommandResult(options, false, false, "Invalid command: " + ex.getMessage());
        }
    }

    private static boolean parseOnOff(String token) {
        String value = token == null ? "" : token.trim().toLowerCase();
        if ("on".equals(value) || "true".equals(value) || "1".equals(value) || "yes".equals(value)) {
            return true;
        }
        if ("off".equals(value) || "false".equals(value) || "0".equals(value) || "no".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException("expected on/off");
    }

    private static int clampInt(String token, int min, int max) {
        int parsed = Integer.parseInt(token);
        return Math.max(min, Math.min(max, parsed));
    }

    private static float clampFloat(String token, float min, float max) {
        float parsed = Float.parseFloat(token);
        return Math.max(min, Math.min(max, parsed));
    }

    private static void printInteractiveHelp() {
        System.out.println(interactiveHelpText());
    }

    private static String interactiveHelpText() {
        return String.join(System.lineSeparator(),
                "Interactive commands:",
                "  help | show | reload | quit",
                "  tier <LOW|MEDIUM|HIGH|ULTRA>",
                "  shadow <on|off> | shadow_cascades <1-4> | shadow_pcf <1-9> | shadow_bias <float> | shadow_res <256-4096>",
                "  post <on|off> | tonemap <on|off> | exposure <0.25-4.0> | gamma <1.2-3.0>",
                "  bloom <on|off> | bloom_threshold <0.2-2.5> | bloom_strength <0.0-1.6>"
        );
    }

    private static QualityTier parseTierArg(String[] args, String key, QualityTier fallback) {
        for (String arg : args) {
            if (!arg.startsWith(key)) {
                continue;
            }
            String raw = arg.substring(key.length()).trim();
            if (raw.isEmpty()) {
                break;
            }
            try {
                return QualityTier.valueOf(raw.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                break;
            }
        }
        return fallback;
    }

    private static boolean parseBooleanArg(String[] args, String key, boolean fallback) {
        for (String arg : args) {
            if (!arg.startsWith(key)) {
                continue;
            }
            String raw = arg.substring(key.length()).trim().toLowerCase();
            if ("on".equals(raw) || "true".equals(raw) || "1".equals(raw) || "yes".equals(raw)) {
                return true;
            }
            if ("off".equals(raw) || "false".equals(raw) || "0".equals(raw) || "no".equals(raw)) {
                return false;
            }
            break;
        }
        return fallback;
    }

    private static int parseIntArg(String[] args, String key, int fallback, int min, int max) {
        for (String arg : args) {
            if (!arg.startsWith(key)) {
                continue;
            }
            String raw = arg.substring(key.length()).trim();
            try {
                int parsed = Integer.parseInt(raw);
                return Math.max(min, Math.min(max, parsed));
            } catch (NumberFormatException ignored) {
                break;
            }
        }
        return fallback;
    }

    private static float parseFloatArg(String[] args, String key, float fallback, float min, float max) {
        for (String arg : args) {
            if (!arg.startsWith(key)) {
                continue;
            }
            String raw = arg.substring(key.length()).trim();
            try {
                float parsed = Float.parseFloat(raw);
                return Math.max(min, Math.min(max, parsed));
            } catch (NumberFormatException ignored) {
                break;
            }
        }
        return fallback;
    }

    private record SceneOptions(
            QualityTier qualityTier,
            boolean shadowsEnabled,
            int shadowCascades,
            int shadowPcfKernel,
            float shadowBias,
            int shadowMapResolution,
            boolean postEnabled,
            boolean tonemapEnabled,
            float exposure,
            float gamma,
            boolean bloomEnabled,
            float bloomThreshold,
            float bloomStrength
    ) {
        SceneOptions withQualityTier(QualityTier tier) {
            return new SceneOptions(
                    tier,
                    shadowsEnabled,
                    shadowCascades,
                    shadowPcfKernel,
                    shadowBias,
                    shadowMapResolution,
                    postEnabled,
                    tonemapEnabled,
                    exposure,
                    gamma,
                    bloomEnabled,
                    bloomThreshold,
                    bloomStrength
            );
        }

        SceneOptions withShadowsEnabled(boolean value) {
            return new SceneOptions(
                    qualityTier,
                    value,
                    shadowCascades,
                    shadowPcfKernel,
                    shadowBias,
                    shadowMapResolution,
                    postEnabled,
                    tonemapEnabled,
                    exposure,
                    gamma,
                    bloomEnabled,
                    bloomThreshold,
                    bloomStrength
            );
        }

        SceneOptions withShadowCascades(int value) {
            return new SceneOptions(
                    qualityTier,
                    shadowsEnabled,
                    value,
                    shadowPcfKernel,
                    shadowBias,
                    shadowMapResolution,
                    postEnabled,
                    tonemapEnabled,
                    exposure,
                    gamma,
                    bloomEnabled,
                    bloomThreshold,
                    bloomStrength
            );
        }

        SceneOptions withShadowPcfKernel(int value) {
            return new SceneOptions(
                    qualityTier,
                    shadowsEnabled,
                    shadowCascades,
                    value,
                    shadowBias,
                    shadowMapResolution,
                    postEnabled,
                    tonemapEnabled,
                    exposure,
                    gamma,
                    bloomEnabled,
                    bloomThreshold,
                    bloomStrength
            );
        }

        SceneOptions withShadowBias(float value) {
            return new SceneOptions(
                    qualityTier,
                    shadowsEnabled,
                    shadowCascades,
                    shadowPcfKernel,
                    value,
                    shadowMapResolution,
                    postEnabled,
                    tonemapEnabled,
                    exposure,
                    gamma,
                    bloomEnabled,
                    bloomThreshold,
                    bloomStrength
            );
        }

        SceneOptions withShadowMapResolution(int value) {
            return new SceneOptions(
                    qualityTier,
                    shadowsEnabled,
                    shadowCascades,
                    shadowPcfKernel,
                    shadowBias,
                    value,
                    postEnabled,
                    tonemapEnabled,
                    exposure,
                    gamma,
                    bloomEnabled,
                    bloomThreshold,
                    bloomStrength
            );
        }

        SceneOptions withPostEnabled(boolean value) {
            return new SceneOptions(
                    qualityTier,
                    shadowsEnabled,
                    shadowCascades,
                    shadowPcfKernel,
                    shadowBias,
                    shadowMapResolution,
                    value,
                    tonemapEnabled,
                    exposure,
                    gamma,
                    bloomEnabled,
                    bloomThreshold,
                    bloomStrength
            );
        }

        SceneOptions withTonemapEnabled(boolean value) {
            return new SceneOptions(
                    qualityTier,
                    shadowsEnabled,
                    shadowCascades,
                    shadowPcfKernel,
                    shadowBias,
                    shadowMapResolution,
                    postEnabled,
                    value,
                    exposure,
                    gamma,
                    bloomEnabled,
                    bloomThreshold,
                    bloomStrength
            );
        }

        SceneOptions withExposure(float value) {
            return new SceneOptions(
                    qualityTier,
                    shadowsEnabled,
                    shadowCascades,
                    shadowPcfKernel,
                    shadowBias,
                    shadowMapResolution,
                    postEnabled,
                    tonemapEnabled,
                    value,
                    gamma,
                    bloomEnabled,
                    bloomThreshold,
                    bloomStrength
            );
        }

        SceneOptions withGamma(float value) {
            return new SceneOptions(
                    qualityTier,
                    shadowsEnabled,
                    shadowCascades,
                    shadowPcfKernel,
                    shadowBias,
                    shadowMapResolution,
                    postEnabled,
                    tonemapEnabled,
                    exposure,
                    value,
                    bloomEnabled,
                    bloomThreshold,
                    bloomStrength
            );
        }

        SceneOptions withBloomEnabled(boolean value) {
            return new SceneOptions(
                    qualityTier,
                    shadowsEnabled,
                    shadowCascades,
                    shadowPcfKernel,
                    shadowBias,
                    shadowMapResolution,
                    postEnabled,
                    tonemapEnabled,
                    exposure,
                    gamma,
                    value,
                    bloomThreshold,
                    bloomStrength
            );
        }

        SceneOptions withBloomThreshold(float value) {
            return new SceneOptions(
                    qualityTier,
                    shadowsEnabled,
                    shadowCascades,
                    shadowPcfKernel,
                    shadowBias,
                    shadowMapResolution,
                    postEnabled,
                    tonemapEnabled,
                    exposure,
                    gamma,
                    bloomEnabled,
                    value,
                    bloomStrength
            );
        }

        SceneOptions withBloomStrength(float value) {
            return new SceneOptions(
                    qualityTier,
                    shadowsEnabled,
                    shadowCascades,
                    shadowPcfKernel,
                    shadowBias,
                    shadowMapResolution,
                    postEnabled,
                    tonemapEnabled,
                    exposure,
                    gamma,
                    bloomEnabled,
                    bloomThreshold,
                    value
            );
        }
    }

    private record CommandResult(SceneOptions updatedOptions, boolean reloadScene, boolean quit, String message) {
    }

    private static final class ConsoleCallbacks implements EngineHostCallbacks {
        @Override
        public void onEvent(EngineEvent event) {
            if (event instanceof SceneLoadedEvent || event instanceof SceneLoadFailedEvent || event instanceof ResourceHotReloadedEvent
                    || event instanceof DeviceLostEvent) {
                System.out.println("event=" + event);
            }
        }

        @Override
        public void onLog(LogMessage message) {
            System.out.printf("[%s] %s %s%n", Instant.ofEpochMilli(message.epochMillis()), message.category(), message.message());
        }

        @Override
        public void onError(EngineErrorReport error) {
            System.err.printf("error=%s recoverable=%s message=%s%n", error.code(), error.recoverable(), error.message());
        }
    }
}
