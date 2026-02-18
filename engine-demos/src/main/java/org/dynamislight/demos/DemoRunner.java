package org.dynamislight.demos;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.dynamislight.api.config.EngineConfig;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.error.EngineErrorReport;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.event.EngineEvent;
import org.dynamislight.api.input.EngineInput;
import org.dynamislight.api.input.KeyCode;
import org.dynamislight.api.logging.LogMessage;
import org.dynamislight.api.runtime.EngineApiVersion;
import org.dynamislight.api.runtime.EngineFrameResult;
import org.dynamislight.api.runtime.EngineHostCallbacks;
import org.dynamislight.api.runtime.EngineRuntime;
import org.dynamislight.api.runtime.EngineStats;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.validation.EngineConfigValidator;
import org.dynamislight.api.validation.SceneValidator;
import org.dynamislight.spi.EngineBackendProvider;
import org.dynamislight.spi.registry.BackendRegistry;

public final class DemoRunner {
    private static final EngineApiVersion HOST_REQUIRED_API = new EngineApiVersion(1, 0, 0);
    private static final Set<String> DEMO_PROFILE_WARNING_CODES = Set.of(
            "FEATURE_BASELINE",
            "SCENE_REUSE_PROFILE",
            "MESH_GEOMETRY_CACHE_PROFILE",
            "SHADOW_POLICY_ACTIVE",
            "SHADOW_CASCADE_PROFILE",
            "VULKAN_POST_PROCESS_PIPELINE",
            "VULKAN_FRAME_RESOURCE_PROFILE",
            "REFLECTIONS_BASELINE_ACTIVE",
            "TAA_BASELINE_ACTIVE"
    );

    private DemoRunner() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> cli = parseArgs(args);
        Map<String, DemoDefinition> demos = DemoRegistry.demos();

        if (cli.containsKey("help") || cli.containsKey("h")) {
            printUsage(demos);
            return;
        }
        if (cli.containsKey("list")) {
            printDemoList(demos);
            return;
        }

        String demoId = cli.getOrDefault("demo", "hello-triangle");
        DemoDefinition demo = demos.get(demoId);
        if (demo == null) {
            System.err.println("Unknown demo '" + demoId + "'. Use --list.");
            System.exit(2);
            return;
        }

        String backendId = cli.getOrDefault("backend", "vulkan").toLowerCase(Locale.ROOT);
        QualityTier qualityTier = parseQuality(cli.getOrDefault("quality", "high"));
        int seconds = parseInt(cli.getOrDefault("seconds", "10"), 1, 3600, 10);
        int width = parseInt(cli.getOrDefault("width", "1280"), 320, 8192, 1280);
        int height = parseInt(cli.getOrDefault("height", "720"), 240, 8192, 720);
        boolean mockContext = parseBoolean(cli.getOrDefault("mock", "true"), true);
        String aaPreset = cli.getOrDefault("aa-preset", "balanced");
        int taaDebugView = parseInt(cli.getOrDefault("taa-debug", "0"), 0, 5, 0);
        Path telemetryPath = resolveTelemetryPath(cli, demoId, backendId);
        Path summaryPath = resolveSummaryPath(cli, telemetryPath);

        DemoRequest request = new DemoRequest(
                demoId,
                backendId,
                qualityTier,
                width,
                height,
                seconds,
                mockContext,
                aaPreset,
                taaDebugView,
                Map.copyOf(cli)
        );

        int frames = Math.max(1, seconds * 60);
        SceneDescriptor scene = demo.sceneForFrame(request, 0, frames, 0.0);
        Map<String, String> backendOptions = buildBackendOptions(request, demo.backendOptions(request));
        EngineConfig config = new EngineConfig(
                backendId,
                "DynamicLightEngine Demos",
                width,
                height,
                1.0f,
                true,
                60,
                qualityTier,
                Path.of("assets"),
                backendOptions
        );

        EngineConfigValidator.validate(config);
        SceneValidator.validate(scene);

        EngineBackendProvider provider = resolveProvider(backendId);
        HostTelemetryCallbacks callbacks = new HostTelemetryCallbacks();
        StatsAccumulator statsAccumulator = new StatsAccumulator();

        Files.createDirectories(telemetryPath.getParent());
        Files.createDirectories(summaryPath.getParent());

        try (EngineRuntime runtime = provider.createRuntime();
             JsonlTelemetryWriter telemetry = new JsonlTelemetryWriter(telemetryPath, summaryPath)) {

            runtime.initialize(config, callbacks);
            runtime.loadScene(scene);

            telemetry.writeRunStart(demoId, demo.description(), backendId, qualityTier, frames, config.backendOptions());

            for (int i = 0; i < frames; i++) {
                if (demo.isDynamicScene() && i > 0) {
                    double elapsedSeconds = i / 60.0;
                    SceneDescriptor frameScene = demo.sceneForFrame(request, i, frames, elapsedSeconds);
                    runtime.loadScene(frameScene);
                }
                runtime.update(1.0 / 60.0, emptyInput());
                EngineFrameResult render = runtime.render();
                EngineStats stats = runtime.getStats();
                StatsSample sample = StatsSample.from(stats);
                statsAccumulator.record(sample);
                telemetry.writeFrame(demoId, backendId, qualityTier, render, sample);
                int actionableWarnings = actionableWarningCount(render);
                if ((i + 1) % 60 == 0 || i == 0 || i + 1 == frames) {
                    System.out.printf(
                            Locale.ROOT,
                            "demo=%s backend=%s frame=%d/%d fps=%.1f cpu=%.2fms gpu=%.2fms warnings=%d%n",
                            demoId,
                            backendId,
                            i + 1,
                            frames,
                            sample.fps,
                            sample.cpuMs,
                            sample.gpuMs,
                            actionableWarnings
                    );
                }
            }

            runtime.shutdown();
            telemetry.writeSummary(demoId, backendId, qualityTier, statsAccumulator, callbacks);
            System.out.println("Telemetry JSONL: " + telemetryPath);
            System.out.println("Telemetry summary: " + summaryPath);
        }
    }

    private static EngineBackendProvider resolveProvider(String backendId) throws EngineException {
        return BackendRegistry.discover().resolve(backendId, HOST_REQUIRED_API);
    }

    private static Map<String, String> buildBackendOptions(DemoRequest request, Map<String, String> demoOptions) {
        Map<String, String> options = new LinkedHashMap<>();
        String prefix = request.backendId().toLowerCase(Locale.ROOT);
        options.put(prefix + ".mockContext", Boolean.toString(request.mockContext()));
        String explicitWindowVisible = request.arg("window-visible", "");
        boolean windowVisible = explicitWindowVisible.isBlank()
                ? !request.mockContext()
                : parseBoolean(explicitWindowVisible, !request.mockContext());
        options.put(prefix + ".windowVisible", Boolean.toString(windowVisible));
        options.put(prefix + ".aaPreset", request.aaPreset());
        options.put(prefix + ".taaDebugView", Integer.toString(request.taaDebugView()));
        String mode = request.arg("aa-mode", "");
        if (!mode.isBlank()) {
            options.put(prefix + ".aaMode", mode);
        }
        String explicitPostOffscreen = request.arg("post-offscreen", "");
        if (!explicitPostOffscreen.isBlank()) {
            options.put(prefix + ".postOffscreen", Boolean.toString(parseBoolean(explicitPostOffscreen, true)));
        }
        String renderScale = request.arg("aa-render-scale", "");
        if (!renderScale.isBlank()) {
            options.put(prefix + ".tsrRenderScale", renderScale);
            options.put(prefix + ".tuuaRenderScale", renderScale);
        }
        options.putAll(demoOptions);
        return Map.copyOf(options);
    }

    private static EngineInput emptyInput() {
        return new EngineInput(0, 0, 0, 0, false, false, Set.<KeyCode>of(), 0);
    }

    private static int actionableWarningCount(EngineFrameResult frame) {
        int count = 0;
        for (var warning : frame.warnings()) {
            if (isActionableWarningCode(warning.code())) {
                count++;
            }
        }
        return count;
    }

    private static boolean isActionableWarningCode(String warningCode) {
        return warningCode != null && !DEMO_PROFILE_WARNING_CODES.contains(warningCode);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String arg : args) {
            if (arg == null || arg.isBlank()) {
                continue;
            }
            if (!arg.startsWith("--")) {
                continue;
            }
            String body = arg.substring(2);
            int eq = body.indexOf('=');
            if (eq < 0) {
                parsed.put(body.toLowerCase(Locale.ROOT), "true");
            } else {
                String key = body.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                String value = body.substring(eq + 1).trim();
                parsed.put(key, value);
            }
        }
        return parsed;
    }

    private static void printUsage(Map<String, DemoDefinition> demos) {
        System.out.println("DynamicLightEngine Demo Runner");
        System.out.println("Usage:");
        System.out.println("  mvn -pl engine-demos -am exec:java -Dexec.args=\"--list\"");
        System.out.println("  mvn -pl engine-demos -am exec:java -Dexec.args=\"--demo=hello-triangle --backend=vulkan --seconds=10\"");
        System.out.println("Options:");
        System.out.println("  --list");
        System.out.println("  --demo=<id>");
        System.out.println("  --backend=opengl|vulkan");
        System.out.println("  --quality=low|medium|high|ultra");
        System.out.println("  --seconds=<n>");
        System.out.println("  --mock=true|false");
        System.out.println("  --width=<px> --height=<px>");
        System.out.println("  --telemetry=<path.jsonl>");
        System.out.println("  --summary=<path.json>");
        System.out.println("  --window-visible=true|false (default true when --mock=false)");
        System.out.println("  --post-offscreen=true|false (maps to <backend>.postOffscreen)");
        System.out.println("  --aa-mode=<taa|tuua|tsr|msaa_selective|hybrid_tuua_msaa|dlaa|fxaa_low>");
        System.out.println("  --aa-render-scale=<0.5..1.0>");
        System.out.println("  --aa-preset=<performance|balanced|quality|stability>");
        System.out.println("  --taa-debug=<0..5>");
        System.out.println();
        printDemoList(demos);
    }

    private static void printDemoList(Map<String, DemoDefinition> demos) {
        System.out.println("Available demos:");
        for (DemoDefinition demo : demos.values()) {
            System.out.printf("  %-16s %s%n", demo.id(), demo.description());
        }
    }

    private static Path resolveTelemetryPath(Map<String, String> cli, String demoId, String backendId) {
        String explicit = cli.get("telemetry");
        if (explicit != null && !explicit.isBlank()) {
            return Path.of(explicit);
        }
        String ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(java.time.LocalDateTime.now());
        return Path.of("artifacts", "demos", demoId + "-" + backendId + "-" + ts + ".jsonl");
    }

    private static Path resolveSummaryPath(Map<String, String> cli, Path telemetryPath) {
        String explicit = cli.get("summary");
        if (explicit != null && !explicit.isBlank()) {
            return Path.of(explicit);
        }
        String name = telemetryPath.getFileName().toString();
        String summaryName = name.endsWith(".jsonl")
                ? name.substring(0, name.length() - ".jsonl".length()) + ".summary.json"
                : name + ".summary.json";
        Path parent = telemetryPath.getParent();
        return parent == null ? Path.of(summaryName) : parent.resolve(summaryName);
    }

    private static int parseInt(String raw, int min, int max, int fallback) {
        try {
            int parsed = Integer.parseInt(raw);
            if (parsed < min) {
                return min;
            }
            if (parsed > max) {
                return max;
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String raw, boolean fallback) {
        String v = raw.toLowerCase(Locale.ROOT);
        if ("true".equals(v) || "1".equals(v) || "yes".equals(v) || "on".equals(v)) {
            return true;
        }
        if ("false".equals(v) || "0".equals(v) || "no".equals(v) || "off".equals(v)) {
            return false;
        }
        return fallback;
    }

    private static QualityTier parseQuality(String raw) {
        try {
            return QualityTier.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return QualityTier.HIGH;
        }
    }

    private static final class HostTelemetryCallbacks implements EngineHostCallbacks {
        private final Map<String, Integer> eventCounts = new TreeMap<>();
        private final Map<String, Integer> logLevelCounts = new TreeMap<>();
        private int errorCount;

        @Override
        public void onEvent(EngineEvent event) {
            String key = event == null ? "unknown" : event.getClass().getSimpleName();
            eventCounts.merge(key, 1, Integer::sum);
        }

        @Override
        public void onLog(LogMessage message) {
            String key = message == null || message.level() == null ? "UNKNOWN" : message.level().name();
            logLevelCounts.merge(key, 1, Integer::sum);
        }

        @Override
        public void onError(EngineErrorReport error) {
            errorCount++;
        }
    }

    private static final class StatsAccumulator {
        private final List<Double> cpu = new ArrayList<>();
        private final List<Double> gpu = new ArrayList<>();
        private final List<Double> fps = new ArrayList<>();
        private final List<Double> reject = new ArrayList<>();
        private final List<Double> confidence = new ArrayList<>();
        private long confidenceDrops;
        private long drawCallsMax;
        private long trianglesMax;
        private long visibleMax;

        void record(StatsSample stats) {
            cpu.add(stats.cpuMs);
            gpu.add(stats.gpuMs);
            fps.add(stats.fps);
            reject.add(stats.taaRejectRate);
            confidence.add(stats.taaConfidenceMean);
            confidenceDrops += stats.taaConfidenceDrops;
            drawCallsMax = Math.max(drawCallsMax, stats.drawCalls);
            trianglesMax = Math.max(trianglesMax, stats.triangles);
            visibleMax = Math.max(visibleMax, stats.visibleObjects);
        }

        double avg(List<Double> samples) {
            if (samples.isEmpty()) {
                return 0.0;
            }
            double sum = 0.0;
            for (double v : samples) {
                sum += v;
            }
            return sum / samples.size();
        }

        double p95(List<Double> samples) {
            if (samples.isEmpty()) {
                return 0.0;
            }
            List<Double> sorted = new ArrayList<>(samples);
            sorted.sort(Double::compareTo);
            int idx = Math.max(0, Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * 0.95) - 1));
            return sorted.get(idx);
        }
    }

    private static final class JsonlTelemetryWriter implements AutoCloseable {
        private final BufferedWriter jsonl;
        private final Path summaryPath;

        JsonlTelemetryWriter(Path jsonlPath, Path summaryPath) throws IOException {
            this.summaryPath = summaryPath;
            this.jsonl = Files.newBufferedWriter(jsonlPath);
        }

        void writeRunStart(
                String demoId,
                String description,
                String backend,
                QualityTier qualityTier,
                int frames,
                Map<String, String> backendOptions
        ) throws IOException {
            String optionsJson = backendOptions.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> quote(e.getKey()) + ":" + quote(e.getValue()))
                    .collect(Collectors.joining(","));
            writeLine(
                    "{"
                            + "\"type\":\"run_start\","
                            + "\"timestamp\":" + quote(Instant.now().toString()) + ","
                            + "\"demoId\":" + quote(demoId) + ","
                            + "\"description\":" + quote(description) + ","
                            + "\"backend\":" + quote(backend) + ","
                            + "\"qualityTier\":" + quote(qualityTier.name().toLowerCase(Locale.ROOT)) + ","
                            + "\"targetFrames\":" + frames + ","
                            + "\"backendOptions\":{" + optionsJson + "}"
                            + "}"
            );
        }

        void writeFrame(String demoId, String backend, QualityTier qualityTier, EngineFrameResult frame, StatsSample stats) throws IOException {
            String totalWarningCodes = frame.warnings().stream().map(w -> w.code()).collect(Collectors.joining(","));
            String warningCodes = frame.warnings().stream()
                    .map(w -> w.code())
                    .filter(DemoRunner::isActionableWarningCode)
                    .collect(Collectors.joining(","));
            int warningCount = (int) frame.warnings().stream()
                    .map(w -> w.code())
                    .filter(DemoRunner::isActionableWarningCode)
                    .count();
            writeLine(
                    "{"
                            + "\"type\":\"frame\","
                            + "\"timestamp\":" + quote(Instant.now().toString()) + ","
                            + "\"demoId\":" + quote(demoId) + ","
                            + "\"backend\":" + quote(backend) + ","
                            + "\"qualityTier\":" + quote(qualityTier.name().toLowerCase(Locale.ROOT)) + ","
                            + "\"frameIndex\":" + frame.frameIndex() + ","
                            + "\"cpuMs\":" + format(stats.cpuMs) + ","
                            + "\"gpuMs\":" + format(stats.gpuMs) + ","
                            + "\"fps\":" + format(stats.fps) + ","
                            + "\"drawCalls\":" + stats.drawCalls + ","
                            + "\"triangles\":" + stats.triangles + ","
                            + "\"visibleObjects\":" + stats.visibleObjects + ","
                            + "\"gpuMemoryBytes\":" + stats.gpuMemoryBytes + ","
                            + "\"taaRejectRate\":" + format(stats.taaRejectRate) + ","
                            + "\"taaConfidenceMean\":" + format(stats.taaConfidenceMean) + ","
                            + "\"taaConfidenceDrops\":" + stats.taaConfidenceDrops + ","
                            + "\"warningCount\":" + warningCount + ","
                            + "\"warningCodes\":" + quote(warningCodes) + ","
                            + "\"totalWarningCount\":" + frame.warnings().size() + ","
                            + "\"totalWarningCodes\":" + quote(totalWarningCodes)
                            + "}"
            );
        }

        void writeSummary(String demoId, String backend, QualityTier qualityTier, StatsAccumulator stats, HostTelemetryCallbacks callbacks) throws IOException {
            String eventCounts = callbacks.eventCounts.entrySet().stream()
                    .map(e -> quote(e.getKey()) + ":" + e.getValue())
                    .collect(Collectors.joining(","));
            String logCounts = callbacks.logLevelCounts.entrySet().stream()
                    .map(e -> quote(e.getKey()) + ":" + e.getValue())
                    .collect(Collectors.joining(","));
            String json = "{"
                    + "\"timestamp\":" + quote(Instant.now().toString()) + ","
                    + "\"demoId\":" + quote(demoId) + ","
                    + "\"backend\":" + quote(backend) + ","
                    + "\"qualityTier\":" + quote(qualityTier.name().toLowerCase(Locale.ROOT)) + ","
                    + "\"avgCpuMs\":" + format(stats.avg(stats.cpu)) + ","
                    + "\"p95CpuMs\":" + format(stats.p95(stats.cpu)) + ","
                    + "\"avgGpuMs\":" + format(stats.avg(stats.gpu)) + ","
                    + "\"p95GpuMs\":" + format(stats.p95(stats.gpu)) + ","
                    + "\"avgFps\":" + format(stats.avg(stats.fps)) + ","
                    + "\"p95RejectRate\":" + format(stats.p95(stats.reject)) + ","
                    + "\"avgConfidence\":" + format(stats.avg(stats.confidence)) + ","
                    + "\"totalConfidenceDrops\":" + stats.confidenceDrops + ","
                    + "\"maxDrawCalls\":" + stats.drawCallsMax + ","
                    + "\"maxTriangles\":" + stats.trianglesMax + ","
                    + "\"maxVisibleObjects\":" + stats.visibleMax + ","
                    + "\"hostErrorCount\":" + callbacks.errorCount + ","
                    + "\"eventCounts\":{" + eventCounts + "},"
                    + "\"logLevelCounts\":{" + logCounts + "}"
                    + "}";
            Files.writeString(summaryPath, json);
            writeLine("{\"type\":\"run_complete\",\"timestamp\":" + quote(Instant.now().toString()) + "}");
        }

        private void writeLine(String line) throws IOException {
            jsonl.write(line);
            jsonl.newLine();
            jsonl.flush();
        }

        private static String format(double value) {
            return String.format(Locale.ROOT, "%.6f", value);
        }

        private static String quote(String value) {
            String escaped = value
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
            return "\"" + escaped + "\"";
        }

        @Override
        public void close() throws IOException {
            jsonl.close();
        }
    }

    private static final class StatsSample {
        final double fps;
        final double cpuMs;
        final double gpuMs;
        final long drawCalls;
        final long triangles;
        final long visibleObjects;
        final long gpuMemoryBytes;
        final double taaRejectRate;
        final double taaConfidenceMean;
        final long taaConfidenceDrops;

        private StatsSample(
                double fps,
                double cpuMs,
                double gpuMs,
                long drawCalls,
                long triangles,
                long visibleObjects,
                long gpuMemoryBytes,
                double taaRejectRate,
                double taaConfidenceMean,
                long taaConfidenceDrops
        ) {
            this.fps = fps;
            this.cpuMs = cpuMs;
            this.gpuMs = gpuMs;
            this.drawCalls = drawCalls;
            this.triangles = triangles;
            this.visibleObjects = visibleObjects;
            this.gpuMemoryBytes = gpuMemoryBytes;
            this.taaRejectRate = taaRejectRate;
            this.taaConfidenceMean = taaConfidenceMean;
            this.taaConfidenceDrops = taaConfidenceDrops;
        }

        static StatsSample from(EngineStats stats) {
            if (stats == null) {
                return new StatsSample(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            }
            Class<?> c = stats.getClass();
            return new StatsSample(
                    invokeDouble(c, stats, "fps", 0.0),
                    invokeDouble(c, stats, "cpuFrameMs", 0.0),
                    invokeDouble(c, stats, "gpuFrameMs", 0.0),
                    invokeLong(c, stats, "drawCalls", 0L),
                    invokeLong(c, stats, "triangles", 0L),
                    invokeLong(c, stats, "visibleObjects", 0L),
                    invokeLong(c, stats, "gpuMemoryBytes", 0L),
                    invokeDouble(c, stats, "taaHistoryRejectRate", 0.0),
                    invokeDouble(c, stats, "taaConfidenceMean", 0.0),
                    invokeLong(c, stats, "taaConfidenceDropEvents", 0L)
            );
        }

        private static double invokeDouble(Class<?> cls, Object target, String method, double fallback) {
            try {
                Method m = cls.getMethod(method);
                Object value = m.invoke(target);
                if (value instanceof Number n) {
                    return n.doubleValue();
                }
            } catch (ReflectiveOperationException ignored) {
            }
            return fallback;
        }

        private static long invokeLong(Class<?> cls, Object target, String method, long fallback) {
            try {
                Method m = cls.getMethod(method);
                Object value = m.invoke(target);
                if (value instanceof Number n) {
                    return n.longValue();
                }
            } catch (ReflectiveOperationException ignored) {
            }
            return fallback;
        }
    }
}
