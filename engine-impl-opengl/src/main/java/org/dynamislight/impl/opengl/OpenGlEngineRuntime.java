package org.dynamislight.impl.opengl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.dynamislight.api.runtime.EngineCapabilities;
import org.dynamislight.api.config.EngineConfig;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.scene.FogDesc;
import org.dynamislight.api.scene.FogMode;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.scene.SmokeEmitterDesc;
import org.dynamislight.impl.common.AbstractEngineRuntime;
import org.dynamislight.impl.common.framegraph.FrameGraph;
import org.dynamislight.impl.common.framegraph.FrameGraphBuilder;
import org.dynamislight.impl.common.framegraph.FrameGraphExecutor;
import org.dynamislight.impl.common.framegraph.FrameGraphPass;

public final class OpenGlEngineRuntime extends AbstractEngineRuntime {
    private record FogRenderConfig(boolean enabled, float r, float g, float b, float density, int steps) {
    }

    private record SmokeRenderConfig(boolean enabled, float r, float g, float b, float intensity, boolean degraded) {
    }

    private final OpenGlContext context = new OpenGlContext();
    private final FrameGraphExecutor frameGraphExecutor = new FrameGraphExecutor();
    private FrameGraph frameGraph;
    private boolean mockContext;
    private boolean windowVisible;
    private QualityTier qualityTier = QualityTier.MEDIUM;
    private long plannedDrawCalls = 1;
    private long plannedTriangles = 1;
    private OpenGlMeshAssetLoader meshLoader = new OpenGlMeshAssetLoader(java.nio.file.Path.of("."));
    private FogRenderConfig fog = new FogRenderConfig(false, 0.5f, 0.5f, 0.5f, 0f, 0);
    private SmokeRenderConfig smoke = new SmokeRenderConfig(false, 0.6f, 0.6f, 0.6f, 0f, false);

    public OpenGlEngineRuntime() {
        super(
                "OpenGL",
                new EngineCapabilities(
                        Set.of("opengl"),
                        true,
                        false,
                        false,
                        false,
                        7680,
                        4320,
                        Set.of(QualityTier.LOW, QualityTier.MEDIUM, QualityTier.HIGH)
                ),
                16.6,
                8.3
        );
    }

    @Override
    protected void onInitialize(EngineConfig config) throws EngineException {
        String mock = config.backendOptions().getOrDefault("opengl.mockContext", "false");
        mockContext = Boolean.parseBoolean(mock);
        windowVisible = Boolean.parseBoolean(config.backendOptions().getOrDefault("opengl.windowVisible", "false"));
        qualityTier = config.qualityTier();
        meshLoader = new OpenGlMeshAssetLoader(config.assetRoot());
        if (Boolean.parseBoolean(config.backendOptions().getOrDefault("opengl.forceInitFailure", "false"))) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "Forced OpenGL init failure", false);
        }
        if (mockContext) {
            return;
        }
        context.initialize(config.appName(), config.initialWidthPx(), config.initialHeightPx(), config.vsyncEnabled(), windowVisible);
        context.setFogParameters(fog.enabled(), fog.r(), fog.g(), fog.b(), fog.density(), fog.steps());
        context.setSmokeParameters(smoke.enabled(), smoke.r(), smoke.g(), smoke.b(), smoke.intensity());
        frameGraph = buildFrameGraph();
    }

    @Override
    protected void onLoadScene(SceneDescriptor scene) throws EngineException {
        fog = mapFog(scene.fog(), qualityTier);
        smoke = mapSmoke(scene.smokeEmitters(), qualityTier);
        List<OpenGlContext.MeshGeometry> sceneMeshes = meshLoader.loadSceneMeshes(scene.meshes());
        plannedDrawCalls = sceneMeshes.size();
        plannedTriangles = sceneMeshes.stream().mapToLong(mesh -> mesh.vertexCount() / 3).sum();
        if (!mockContext) {
            context.setSceneMeshes(sceneMeshes);
            context.setFogParameters(fog.enabled(), fog.r(), fog.g(), fog.b(), fog.density(), fog.steps());
            context.setSmokeParameters(smoke.enabled(), smoke.r(), smoke.g(), smoke.b(), smoke.intensity());
            frameGraph = buildFrameGraph();
        }
    }

    @Override
    protected RenderMetrics onRender() throws EngineException {
        if (mockContext) {
            return renderMetrics(0.2, 0.1, plannedDrawCalls, plannedTriangles, plannedDrawCalls, 0);
        }
        long startNs = System.nanoTime();
        context.beginFrame();
        frameGraphExecutor.execute(frameGraph);
        context.endFrame();
        double cpuMs = (System.nanoTime() - startNs) / 1_000_000.0;
        double gpuMs = context.lastGpuFrameMs() > 0.0 ? context.lastGpuFrameMs() : cpuMs * 0.8;
        return renderMetrics(
                cpuMs,
                gpuMs,
                context.lastDrawCalls(),
                context.lastTriangles(),
                context.lastVisibleObjects(),
                0
        );
    }

    @Override
    protected void onResize(int widthPx, int heightPx, float dpiScale) throws EngineException {
        if (!mockContext) {
            context.resize(widthPx, heightPx);
        }
    }

    @Override
    protected void onShutdown() {
        if (!mockContext) {
            context.shutdown();
        }
    }

    @Override
    protected List<EngineWarning> frameWarnings() {
        List<EngineWarning> warnings = new ArrayList<>();
        if (smoke.enabled() && smoke.degraded()) {
            warnings.add(new EngineWarning(
                    "SMOKE_QUALITY_DEGRADED",
                    "Smoke rendering quality reduced for tier " + qualityTier + " to maintain performance"
            ));
        }
        if (fog.enabled() && qualityTier == QualityTier.LOW) {
            warnings.add(new EngineWarning(
                    "FOG_QUALITY_DEGRADED",
                    "Fog sampling reduced at LOW quality tier"
            ));
        }
        return warnings;
    }

    @Override
    protected List<EngineWarning> baselineWarnings() {
        return List.of(new EngineWarning("FEATURE_LIMITED", "OpenGL backend currently uses simplified forward render path"));
    }

    private static FogRenderConfig mapFog(FogDesc fogDesc, QualityTier qualityTier) {
        if (fogDesc == null || !fogDesc.enabled() || fogDesc.mode() == FogMode.NONE) {
            return new FogRenderConfig(false, 0.5f, 0.5f, 0.5f, 0f, 0);
        }

        float tierDensityScale = fogDensityScale(qualityTier);
        int tierSteps = fogSteps(qualityTier);

        float density = Math.max(0f, fogDesc.density() * tierDensityScale);
        return new FogRenderConfig(
                true,
                fogDesc.color() == null ? 0.5f : fogDesc.color().x(),
                fogDesc.color() == null ? 0.5f : fogDesc.color().y(),
                fogDesc.color() == null ? 0.5f : fogDesc.color().z(),
                density,
                tierSteps
        );
    }

    static int fogSteps(QualityTier qualityTier) {
        return switch (qualityTier) {
            case LOW -> 4;
            case MEDIUM -> 8;
            case HIGH -> 16;
            case ULTRA -> 0;
        };
    }

    static float fogDensityScale(QualityTier qualityTier) {
        return switch (qualityTier) {
            case LOW -> 0.55f;
            case MEDIUM -> 0.75f;
            case HIGH -> 1.0f;
            case ULTRA -> 1.2f;
        };
    }

    private static SmokeRenderConfig mapSmoke(List<SmokeEmitterDesc> emitters, QualityTier qualityTier) {
        if (emitters == null || emitters.isEmpty()) {
            return new SmokeRenderConfig(false, 0.6f, 0.6f, 0.6f, 0f, false);
        }

        int enabledCount = 0;
        float densityAccum = 0f;
        float r = 0f;
        float g = 0f;
        float b = 0f;

        for (SmokeEmitterDesc emitter : emitters) {
            if (!emitter.enabled()) {
                continue;
            }
            enabledCount++;
            densityAccum += Math.max(0f, emitter.density());
            r += emitter.albedo() == null ? 0.6f : emitter.albedo().x();
            g += emitter.albedo() == null ? 0.6f : emitter.albedo().y();
            b += emitter.albedo() == null ? 0.6f : emitter.albedo().z();
        }

        if (enabledCount == 0) {
            return new SmokeRenderConfig(false, 0.6f, 0.6f, 0.6f, 0f, false);
        }

        float avgR = r / enabledCount;
        float avgG = g / enabledCount;
        float avgB = b / enabledCount;
        float baseIntensity = Math.min(0.85f, densityAccum / enabledCount);

        float tierScale = switch (qualityTier) {
            case LOW -> 0.45f;
            case MEDIUM -> 0.7f;
            case HIGH -> 0.9f;
            case ULTRA -> 1.0f;
        };
        boolean degraded = qualityTier == QualityTier.LOW || qualityTier == QualityTier.MEDIUM;
        return new SmokeRenderConfig(
                true,
                avgR,
                avgG,
                avgB,
                Math.min(0.85f, baseIntensity * tierScale),
                degraded
        );
    }

    private FrameGraph buildFrameGraph() {
        FrameGraphBuilder builder = new FrameGraphBuilder()
                .addPass(pass("clear", Set.of(), Set.of(), Set.of("color"), context::renderClearPass))
                .addPass(pass("geometry", Set.of("clear"), Set.of(), Set.of("color"), context::renderGeometryPass));
        if (fog.enabled()) {
            builder.addPass(pass("fog", Set.of("geometry"), Set.of("color"), Set.of("color"), context::renderFogPass));
        }
        if (smoke.enabled()) {
            builder.addPass(pass("smoke", fog.enabled() ? Set.of("fog") : Set.of("geometry"),
                    Set.of("color"), Set.of("color"), context::renderSmokePass));
        }
        return builder.build();
    }

    private static FrameGraphPass pass(String id, Set<String> deps, Set<String> reads, Set<String> writes, Runnable work) {
        return new FrameGraphPass() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public Set<String> dependsOn() {
                return deps;
            }

            @Override
            public Set<String> reads() {
                return reads;
            }

            @Override
            public Set<String> writes() {
                return writes;
            }

            @Override
            public void execute() {
                work.run();
            }
        };
    }
}
