package org.dynamislight.impl.opengl;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dynamislight.api.runtime.EngineCapabilities;
import org.dynamislight.api.config.EngineConfig;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.scene.EnvironmentDesc;
import org.dynamislight.api.scene.FogDesc;
import org.dynamislight.api.scene.FogMode;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.scene.PostProcessDesc;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.scene.ShadowDesc;
import org.dynamislight.api.scene.SmokeEmitterDesc;
import org.dynamislight.api.scene.TransformDesc;
import org.dynamislight.api.scene.Vec3;
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

    private record PostProcessRenderConfig(
            boolean tonemapEnabled,
            float exposure,
            float gamma,
            boolean bloomEnabled,
            float bloomThreshold,
            float bloomStrength
    ) {
    }

    private record IblRenderConfig(
            boolean enabled,
            float diffuseStrength,
            float specularStrength,
            boolean textureDriven,
            boolean ktxContainerRequested,
            float prefilterStrength
    ) {
    }

    static record CameraMatrices(float[] view, float[] proj) {
    }

    private final OpenGlContext context = new OpenGlContext();
    private final FrameGraphExecutor frameGraphExecutor = new FrameGraphExecutor();
    private FrameGraph frameGraph;
    private boolean mockContext;
    private boolean windowVisible;
    private QualityTier qualityTier = QualityTier.MEDIUM;
    private long plannedDrawCalls = 1;
    private long plannedTriangles = 1;
    private long plannedVisibleObjects = 1;
    private OpenGlMeshAssetLoader meshLoader = new OpenGlMeshAssetLoader(Path.of("."));
    private Path assetRoot = Path.of(".");
    private int viewportWidth = 1280;
    private int viewportHeight = 720;
    private SceneDescriptor activeScene;
    private FogRenderConfig fog = new FogRenderConfig(false, 0.5f, 0.5f, 0.5f, 0f, 0);
    private SmokeRenderConfig smoke = new SmokeRenderConfig(false, 0.6f, 0.6f, 0.6f, 0f, false);
    private ShadowRenderConfig shadows = new ShadowRenderConfig(false, 0.45f, 0.0015f, 1, 1, 1024, false);
    private PostProcessRenderConfig postProcess = new PostProcessRenderConfig(true, 1.0f, 2.2f, false, 1.0f, 0.8f);
    private IblRenderConfig ibl = new IblRenderConfig(false, 0f, 0f, false, false, 0f);

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
        assetRoot = config.assetRoot() == null ? Path.of(".") : config.assetRoot();
        meshLoader = new OpenGlMeshAssetLoader(assetRoot);
        viewportWidth = config.initialWidthPx();
        viewportHeight = config.initialHeightPx();
        if (Boolean.parseBoolean(config.backendOptions().getOrDefault("opengl.forceInitFailure", "false"))) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "Forced OpenGL init failure", false);
        }
        if (mockContext) {
            return;
        }
        context.initialize(config.appName(), config.initialWidthPx(), config.initialHeightPx(), config.vsyncEnabled(), windowVisible);
        context.setFogParameters(fog.enabled(), fog.r(), fog.g(), fog.b(), fog.density(), fog.steps());
        context.setSmokeParameters(smoke.enabled(), smoke.r(), smoke.g(), smoke.b(), smoke.intensity());
        context.setIblParameters(ibl.enabled(), ibl.diffuseStrength(), ibl.specularStrength(), ibl.prefilterStrength());
        context.setIblTexturePaths(null, null, null);
        context.setPostProcessParameters(
                postProcess.tonemapEnabled(),
                postProcess.exposure(),
                postProcess.gamma(),
                postProcess.bloomEnabled(),
                postProcess.bloomThreshold(),
                postProcess.bloomStrength()
        );
        frameGraph = buildFrameGraph();
    }

    @Override
    protected void onLoadScene(SceneDescriptor scene) throws EngineException {
        activeScene = scene;
        fog = mapFog(scene.fog(), qualityTier);
        smoke = mapSmoke(scene.smokeEmitters(), qualityTier);
        shadows = mapShadows(scene.lights(), qualityTier);
        postProcess = mapPostProcess(scene.postProcess(), qualityTier);
        ibl = mapIbl(scene.environment(), qualityTier);

        List<OpenGlContext.SceneMesh> sceneMeshes = mapSceneMeshes(scene);
        plannedDrawCalls = sceneMeshes.size();
        plannedTriangles = sceneMeshes.stream().mapToLong(mesh -> mesh.geometry().vertexCount() / 3).sum();
        plannedVisibleObjects = plannedDrawCalls;

        CameraDesc camera = selectActiveCamera(scene);
        CameraMatrices cameraMatrices = cameraMatricesFor(camera, safeAspect(viewportWidth, viewportHeight));

        if (!mockContext) {
            context.setSceneMeshes(sceneMeshes);
            context.setCameraMatrices(cameraMatrices.view(), cameraMatrices.proj());
            LightingConfig lighting = mapLighting(scene.lights());
            context.setLightingParameters(
                    lighting.directionalDirection(),
                    lighting.directionalColor(),
                    lighting.directionalIntensity(),
                    lighting.pointPosition(),
                    lighting.pointColor(),
                    lighting.pointIntensity()
            );
            context.setShadowParameters(
                    shadows.enabled(),
                    shadows.strength(),
                    shadows.bias(),
                    shadows.pcfRadius(),
                    shadows.cascadeCount(),
                    shadows.mapResolution()
            );
            context.setFogParameters(fog.enabled(), fog.r(), fog.g(), fog.b(), fog.density(), fog.steps());
            context.setSmokeParameters(smoke.enabled(), smoke.r(), smoke.g(), smoke.b(), smoke.intensity());
            context.setIblParameters(ibl.enabled(), ibl.diffuseStrength(), ibl.specularStrength(), ibl.prefilterStrength());
            context.setIblTexturePaths(
                    resolveIblTexturePath(scene.environment() == null ? null : scene.environment().iblIrradiancePath()),
                    resolveIblTexturePath(scene.environment() == null ? null : scene.environment().iblRadiancePath()),
                    resolveIblTexturePath(scene.environment() == null ? null : scene.environment().iblBrdfLutPath())
            );
            context.setPostProcessParameters(
                    postProcess.tonemapEnabled(),
                    postProcess.exposure(),
                    postProcess.gamma(),
                    postProcess.bloomEnabled(),
                    postProcess.bloomThreshold(),
                    postProcess.bloomStrength()
            );
            frameGraph = buildFrameGraph();
        }
    }

    @Override
    protected RenderMetrics onRender() throws EngineException {
        if (mockContext) {
            return renderMetrics(0.2, 0.1, plannedDrawCalls, plannedTriangles, plannedVisibleObjects, 0);
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
                context.estimatedGpuMemoryBytes()
        );
    }

    @Override
    protected void onResize(int widthPx, int heightPx, float dpiScale) throws EngineException {
        viewportWidth = widthPx;
        viewportHeight = heightPx;
        if (!mockContext) {
            context.resize(widthPx, heightPx);
            if (activeScene != null) {
                CameraDesc camera = selectActiveCamera(activeScene);
                CameraMatrices matrices = cameraMatricesFor(camera, safeAspect(widthPx, heightPx));
                context.setCameraMatrices(matrices.view(), matrices.proj());
            }
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
        if (shadows.enabled() && shadows.degraded()) {
            warnings.add(new EngineWarning(
                    "SHADOW_QUALITY_DEGRADED",
                    "Shadow quality reduced for tier " + qualityTier + " to maintain performance"
            ));
        }
        if (ibl.enabled()) {
            warnings.add(new EngineWarning(
                    "IBL_BASELINE_ACTIVE",
                    "IBL baseline enabled using "
                            + (ibl.textureDriven() ? "texture-driven" : "path-driven")
                            + " environment diffuse/specular approximations"
            ));
            warnings.add(new EngineWarning(
                    "IBL_PREFILTER_APPROX_ACTIVE",
                    "IBL roughness-aware radiance prefilter approximation active (strength=" + ibl.prefilterStrength() + ")"
            ));
            if (ibl.ktxContainerRequested()) {
                warnings.add(new EngineWarning(
                        "IBL_KTX_CONTAINER_FALLBACK",
                        "KTX/KTX2 IBL assets are resolved through sidecar decode paths when available (.png/.hdr/.jpg/.jpeg)"
                ));
            }
        }
        return warnings;
    }

    @Override
    protected List<EngineWarning> baselineWarnings() {
        return List.of(new EngineWarning("FEATURE_BASELINE", "OpenGL backend active with baseline forward render path"));
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

    private static PostProcessRenderConfig mapPostProcess(PostProcessDesc desc, QualityTier qualityTier) {
        if (desc == null || !desc.enabled()) {
            return new PostProcessRenderConfig(false, 1.0f, 2.2f, false, 1.0f, 0.8f);
        }
        float tierExposureScale = switch (qualityTier) {
            case LOW -> 0.9f;
            case MEDIUM -> 1.0f;
            case HIGH -> 1.05f;
            case ULTRA -> 1.1f;
        };
        float exposure = Math.max(0.25f, Math.min(4.0f, desc.exposure() * tierExposureScale));
        float gamma = Math.max(1.6f, Math.min(2.6f, desc.gamma()));
        float bloomThreshold = Math.max(0.2f, Math.min(2.5f, desc.bloomThreshold()));
        float bloomStrength = Math.max(0f, Math.min(1.6f, desc.bloomStrength()));
        boolean bloomEnabled = desc.bloomEnabled() && qualityTier != QualityTier.LOW;
        return new PostProcessRenderConfig(
                desc.tonemapEnabled(),
                exposure,
                gamma,
                bloomEnabled,
                bloomThreshold,
                bloomStrength
        );
    }

    private IblRenderConfig mapIbl(EnvironmentDesc environment, QualityTier qualityTier) {
        if (environment == null) {
            return new IblRenderConfig(false, 0f, 0f, false, false, 0f);
        }
        boolean enabled = !isBlank(environment.iblIrradiancePath())
                && !isBlank(environment.iblRadiancePath())
                && !isBlank(environment.iblBrdfLutPath());
        if (!enabled) {
            return new IblRenderConfig(false, 0f, 0f, false, false, 0f);
        }
        float tierScale = switch (qualityTier) {
            case LOW -> 0.75f;
            case MEDIUM -> 0.9f;
            case HIGH -> 1.0f;
            case ULTRA -> 1.1f;
        };
        float diffuse = 0.42f * tierScale;
        float specular = 0.30f * tierScale;
        float prefilterStrength = switch (qualityTier) {
            case LOW -> 0.5f;
            case MEDIUM -> 0.7f;
            case HIGH -> 0.85f;
            case ULTRA -> 1.0f;
        };
        boolean textureDriven = false;

        Path irrSource = resolveTexturePath(environment.iblIrradiancePath());
        Path radSource = resolveTexturePath(environment.iblRadiancePath());
        Path brdfSource = resolveTexturePath(environment.iblBrdfLutPath());
        boolean ktxContainerRequested = isKtxContainerPath(irrSource)
                || isKtxContainerPath(radSource)
                || isKtxContainerPath(brdfSource);

        Path irr = resolveContainerSourcePath(irrSource);
        Path rad = resolveContainerSourcePath(radSource);
        Path brdf = resolveContainerSourcePath(brdfSource);
        float irrSignal = imageLuminanceSignal(irr);
        float radSignal = imageLuminanceSignal(rad);
        float brdfSignal = imageLuminanceSignal(brdf);
        if (irrSignal >= 0f || radSignal >= 0f || brdfSignal >= 0f) {
            float irrUsed = irrSignal < 0f ? 0.5f : irrSignal;
            float radUsed = radSignal < 0f ? 0.5f : radSignal;
            float brdfUsed = brdfSignal < 0f ? 0.5f : brdfSignal;
            float diffuseScale = 0.82f + 0.36f * irrUsed;
            float specScale = 0.78f + 0.42f * ((radUsed * 0.6f) + (brdfUsed * 0.4f));
            diffuse *= diffuseScale;
            specular *= specScale;
            prefilterStrength = Math.max(prefilterStrength, 0.65f + 0.35f * radUsed);
            textureDriven = true;
        }

        return new IblRenderConfig(
                true,
                Math.max(0f, Math.min(2.0f, diffuse)),
                Math.max(0f, Math.min(2.0f, specular)),
                textureDriven,
                ktxContainerRequested,
                Math.max(0f, Math.min(1f, prefilterStrength))
        );
    }

    private static ShadowRenderConfig mapShadows(List<LightDesc> lights, QualityTier qualityTier) {
        if (lights == null || lights.isEmpty()) {
            return new ShadowRenderConfig(false, 0.45f, 0.0015f, 1, 1, 1024, false);
        }
        for (LightDesc light : lights) {
            if (light == null || !light.castsShadows()) {
                continue;
            }
            ShadowDesc shadow = light.shadow();
            int kernel = shadow == null ? 3 : Math.max(1, shadow.pcfKernelSize());
            int radius = Math.max(0, (kernel - 1) / 2);
            int cascades = shadow == null ? 1 : Math.max(1, shadow.cascadeCount());
            int resolution = shadow == null ? 1024 : Math.max(256, Math.min(4096, shadow.mapResolution()));
            float bias = shadow == null ? 0.0015f : Math.max(0.00002f, shadow.depthBias());
            float base = Math.min(0.9f, 0.25f + (kernel * 0.04f) + (cascades * 0.05f));
            float tierScale = switch (qualityTier) {
                case LOW -> 0.55f;
                case MEDIUM -> 0.75f;
                case HIGH -> 1.0f;
                case ULTRA -> 1.15f;
            };
            return new ShadowRenderConfig(
                    true,
                    Math.max(0.2f, Math.min(0.9f, base * tierScale)),
                    bias,
                    radius,
                    cascades,
                    resolution,
                    qualityTier == QualityTier.LOW || qualityTier == QualityTier.MEDIUM
            );
        }
        return new ShadowRenderConfig(false, 0.45f, 0.0015f, 1, 1, 1024, false);
    }

    private List<OpenGlContext.SceneMesh> mapSceneMeshes(SceneDescriptor scene) {
        if (scene.meshes() == null || scene.meshes().isEmpty()) {
            return List.of(new OpenGlContext.SceneMesh(
                    OpenGlContext.defaultTriangleGeometry(),
                    identityMatrix(),
                    new float[]{1f, 1f, 1f},
                    0.0f,
                    0.6f,
                    null,
                    null,
                    null,
                    null
            ));
        }

        Map<String, TransformDesc> transforms = new HashMap<>();
        for (TransformDesc transform : scene.transforms()) {
            transforms.put(transform.id(), transform);
        }

        Map<String, MaterialDesc> materials = new HashMap<>();
        for (MaterialDesc material : scene.materials()) {
            materials.put(material.id(), material);
        }

        List<OpenGlContext.SceneMesh> sceneMeshes = new ArrayList<>(scene.meshes().size());
        for (int i = 0; i < scene.meshes().size(); i++) {
            MeshDesc mesh = scene.meshes().get(i);
            OpenGlContext.MeshGeometry geometry = meshLoader.loadMeshGeometry(mesh, i);
            TransformDesc transform = transforms.get(mesh.transformId());
            MaterialDesc material = materials.get(mesh.materialId());

            float[] model = modelMatrixOf(transform);
            float[] albedo = albedoOf(material);
            Path albedoTexturePath = resolveTexturePath(material == null ? null : material.albedoTexturePath());
            Path normalTexturePath = resolveTexturePath(material == null ? null : material.normalTexturePath());
            Path metallicRoughnessTexturePath =
                    resolveTexturePath(material == null ? null : material.metallicRoughnessTexturePath());
            Path occlusionTexturePath = resolveTexturePath(material == null ? null : material.occlusionTexturePath());
            float metallic = material == null ? 0.0f : clamp01(material.metallic());
            float roughness = material == null ? 0.6f : clamp01(material.roughness());
            sceneMeshes.add(new OpenGlContext.SceneMesh(
                    geometry,
                    model,
                    albedo,
                    metallic,
                    roughness,
                    albedoTexturePath,
                    normalTexturePath,
                    metallicRoughnessTexturePath,
                    occlusionTexturePath
            ));
        }
        return sceneMeshes;
    }

    private static LightingConfig mapLighting(List<LightDesc> lights) {
        float[] dir = new float[]{0.35f, -1.0f, 0.25f};
        float[] dirColor = new float[]{1.0f, 0.98f, 0.95f};
        float dirIntensity = 1.0f;
        float[] pointPos = new float[]{0f, 1.3f, 1.8f};
        float[] pointColor = new float[]{0.95f, 0.62f, 0.22f};
        float pointIntensity = 1.0f;
        if (lights == null || lights.isEmpty()) {
            return new LightingConfig(dir, dirColor, dirIntensity, pointPos, pointColor, pointIntensity);
        }
        LightDesc first = lights.getFirst();
        if (first != null && first.color() != null) {
            dirColor = new float[]{clamp01(first.color().x()), clamp01(first.color().y()), clamp01(first.color().z())};
        }
        if (first != null) {
            dirIntensity = Math.max(0f, first.intensity());
            if (first.position() != null) {
                pointPos = new float[]{first.position().x(), first.position().y(), first.position().z()};
            }
        }
        if (lights.size() > 1) {
            LightDesc second = lights.get(1);
            if (second != null && second.color() != null) {
                pointColor = new float[]{clamp01(second.color().x()), clamp01(second.color().y()), clamp01(second.color().z())};
            }
            if (second != null) {
                pointIntensity = Math.max(0f, second.intensity());
                if (second.position() != null) {
                    pointPos = new float[]{second.position().x(), second.position().y(), second.position().z()};
                }
            }
        }
        return new LightingConfig(dir, dirColor, dirIntensity, pointPos, pointColor, pointIntensity);
    }

    private record LightingConfig(
            float[] directionalDirection,
            float[] directionalColor,
            float directionalIntensity,
            float[] pointPosition,
            float[] pointColor,
            float pointIntensity
    ) {
    }

    private record ShadowRenderConfig(
            boolean enabled,
            float strength,
            float bias,
            int pcfRadius,
            int cascadeCount,
            int mapResolution,
            boolean degraded
    ) {
    }

    private CameraDesc selectActiveCamera(SceneDescriptor scene) {
        if (scene == null || scene.cameras() == null || scene.cameras().isEmpty()) {
            return new CameraDesc("default", new Vec3(0f, 0f, 5f), new Vec3(0f, 0f, 0f), 60f, 0.1f, 100f);
        }
        if (scene.activeCameraId() != null && !scene.activeCameraId().isBlank()) {
            for (CameraDesc camera : scene.cameras()) {
                if (scene.activeCameraId().equals(camera.id())) {
                    return camera;
                }
            }
        }
        return scene.cameras().getFirst();
    }

    static CameraMatrices cameraMatricesFor(CameraDesc camera, float aspectRatio) {
        CameraDesc effective = camera == null
                ? new CameraDesc("default", new Vec3(0f, 0f, 5f), new Vec3(0f, 0f, 0f), 60f, 0.1f, 100f)
                : camera;

        Vec3 pos = effective.position() == null ? new Vec3(0f, 0f, 5f) : effective.position();
        Vec3 rot = effective.rotationEulerDeg() == null ? new Vec3(0f, 0f, 0f) : effective.rotationEulerDeg();
        float yaw = radians(rot.y());
        float pitch = radians(rot.x());

        float fx = (float) (Math.cos(pitch) * Math.sin(yaw));
        float fy = (float) Math.sin(pitch);
        float fz = (float) (-Math.cos(pitch) * Math.cos(yaw));

        float[] view = lookAt(
                pos.x(), pos.y(), pos.z(),
                pos.x() + fx, pos.y() + fy, pos.z() + fz,
                0f, 1f, 0f
        );

        float near = effective.nearPlane() > 0f ? effective.nearPlane() : 0.1f;
        float far = effective.farPlane() > near ? effective.farPlane() : 100f;
        float fov = effective.fovDegrees() > 1f ? effective.fovDegrees() : 60f;
        float aspect = aspectRatio > 0.01f ? aspectRatio : (16f / 9f);
        float[] proj = perspective(radians(fov), aspect, near, far);
        return new CameraMatrices(view, proj);
    }

    static float[] modelMatrixOf(TransformDesc transform) {
        if (transform == null) {
            return identityMatrix();
        }
        Vec3 pos = transform.position() == null ? new Vec3(0f, 0f, 0f) : transform.position();
        Vec3 rot = transform.rotationEulerDeg() == null ? new Vec3(0f, 0f, 0f) : transform.rotationEulerDeg();
        Vec3 scl = transform.scale() == null ? new Vec3(1f, 1f, 1f) : transform.scale();

        float[] translation = translationMatrix(pos.x(), pos.y(), pos.z());
        float[] rotation = mul(mul(rotationZ(radians(rot.z())), rotationY(radians(rot.y()))), rotationX(radians(rot.x())));
        float[] scale = scaleMatrix(scl.x(), scl.y(), scl.z());
        return mul(translation, mul(rotation, scale));
    }

    private float[] albedoOf(MaterialDesc material) {
        if (material == null || material.albedo() == null) {
            return new float[]{1f, 1f, 1f};
        }
        return new float[]{material.albedo().x(), material.albedo().y(), material.albedo().z()};
    }

    private Path resolveTexturePath(String texturePath) {
        if (texturePath == null || texturePath.isBlank()) {
            return null;
        }
        Path path = Path.of(texturePath);
        return path.isAbsolute() ? path : assetRoot.resolve(path).normalize();
    }

    private Path resolveIblTexturePath(String texturePath) {
        return resolveContainerSourcePath(resolveTexturePath(texturePath));
    }

    private static float safeAspect(int width, int height) {
        if (height <= 0) {
            return 16f / 9f;
        }
        return Math.max(0.1f, (float) width / (float) height);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static float imageLuminanceSignal(Path path) {
        Path sourcePath = resolveContainerSourcePath(path);
        if (sourcePath == null || !Files.isRegularFile(sourcePath)) {
            return -1f;
        }
        String name = sourcePath.getFileName() == null ? "" : sourcePath.getFileName().toString().toLowerCase();
        boolean imageIoSupported = name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
        boolean hdrSupported = name.endsWith(".hdr");
        if (!imageIoSupported && !hdrSupported) {
            return -1f;
        }
        if (hdrSupported) {
            try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
                var x = stack.mallocInt(1);
                var y = stack.mallocInt(1);
                var channels = stack.mallocInt(1);
                FloatBuffer hdr = org.lwjgl.stb.STBImage.stbi_loadf(sourcePath.toAbsolutePath().toString(), x, y, channels, 3);
                if (hdr == null || x.get(0) <= 0 || y.get(0) <= 0) {
                    return -1f;
                }
                try {
                    int width = x.get(0);
                    int height = y.get(0);
                    int stepX = Math.max(1, width / 64);
                    int stepY = Math.max(1, height / 64);
                    double sum = 0.0;
                    int count = 0;
                    for (int yIdx = 0; yIdx < height; yIdx += stepY) {
                        for (int xIdx = 0; xIdx < width; xIdx += stepX) {
                            int idx = (yIdx * width + xIdx) * 3;
                            float r = hdr.get(idx);
                            float g = hdr.get(idx + 1);
                            float b = hdr.get(idx + 2);
                            float ldrR = toneMapLdr(r);
                            float ldrG = toneMapLdr(g);
                            float ldrB = toneMapLdr(b);
                            sum += (0.2126 * ldrR) + (0.7152 * ldrG) + (0.0722 * ldrB);
                            count++;
                        }
                    }
                    if (count == 0) {
                        return -1f;
                    }
                    return (float) Math.max(0.0, Math.min(1.0, sum / count));
                } finally {
                    org.lwjgl.stb.STBImage.stbi_image_free(hdr);
                }
            } catch (Throwable ignored) {
                return -1f;
            }
        }
        try {
            BufferedImage image = javax.imageio.ImageIO.read(sourcePath.toFile());
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                return -1f;
            }
            int stepX = Math.max(1, image.getWidth() / 64);
            int stepY = Math.max(1, image.getHeight() / 64);
            double sum = 0.0;
            int count = 0;
            for (int y = 0; y < image.getHeight(); y += stepY) {
                for (int x = 0; x < image.getWidth(); x += stepX) {
                    int argb = image.getRGB(x, y);
                    float r = ((argb >> 16) & 0xFF) / 255f;
                    float g = ((argb >> 8) & 0xFF) / 255f;
                    float b = (argb & 0xFF) / 255f;
                    sum += (0.2126 * r) + (0.7152 * g) + (0.0722 * b);
                    count++;
                }
            }
            if (count == 0) {
                return -1f;
            }
            return (float) Math.max(0.0, Math.min(1.0, sum / count));
        } catch (IOException ignored) {
            return -1f;
        }
    }

    private static Path resolveContainerSourcePath(Path requestedPath) {
        if (requestedPath == null || !Files.isRegularFile(requestedPath) || !isKtxContainerPath(requestedPath)) {
            return requestedPath;
        }
        String fileName = requestedPath.getFileName() == null ? null : requestedPath.getFileName().toString();
        if (fileName == null) {
            return requestedPath;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return requestedPath;
        }
        String baseName = fileName.substring(0, dot);
        for (String ext : new String[]{".png", ".hdr", ".jpg", ".jpeg"}) {
            Path candidate = requestedPath.resolveSibling(baseName + ext);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return requestedPath;
    }

    private static boolean isKtxContainerPath(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".ktx") || name.endsWith(".ktx2");
    }

    private static float toneMapLdr(float hdrValue) {
        float toneMapped = hdrValue / (1.0f + Math.max(0f, hdrValue));
        return (float) Math.pow(Math.max(0f, toneMapped), 1.0 / 2.2);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
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
        String postDependency = smoke.enabled() ? "smoke" : (fog.enabled() ? "fog" : "geometry");
        builder.addPass(pass("post", Set.of(postDependency), Set.of("color"), Set.of("color"), context::renderPostProcessPass));
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

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    private static float[] identityMatrix() {
        return new float[]{
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
        };
    }

    private static float[] translationMatrix(float x, float y, float z) {
        return new float[]{
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                x, y, z, 1f
        };
    }

    private static float[] scaleMatrix(float x, float y, float z) {
        return new float[]{
                x, 0f, 0f, 0f,
                0f, y, 0f, 0f,
                0f, 0f, z, 0f,
                0f, 0f, 0f, 1f
        };
    }

    private static float[] rotationX(float radians) {
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        return new float[]{
                1f, 0f, 0f, 0f,
                0f, c, s, 0f,
                0f, -s, c, 0f,
                0f, 0f, 0f, 1f
        };
    }

    private static float[] rotationY(float radians) {
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        return new float[]{
                c, 0f, -s, 0f,
                0f, 1f, 0f, 0f,
                s, 0f, c, 0f,
                0f, 0f, 0f, 1f
        };
    }

    private static float[] rotationZ(float radians) {
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        return new float[]{
                c, s, 0f, 0f,
                -s, c, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
        };
    }

    private static float[] lookAt(float eyeX, float eyeY, float eyeZ, float targetX, float targetY, float targetZ,
                                  float upX, float upY, float upZ) {
        float fx = targetX - eyeX;
        float fy = targetY - eyeY;
        float fz = targetZ - eyeZ;
        float fLen = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (fLen < 0.00001f) {
            return identityMatrix();
        }
        fx /= fLen;
        fy /= fLen;
        fz /= fLen;

        float sx = fy * upZ - fz * upY;
        float sy = fz * upX - fx * upZ;
        float sz = fx * upY - fy * upX;
        float sLen = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sLen < 0.00001f) {
            return identityMatrix();
        }
        sx /= sLen;
        sy /= sLen;
        sz /= sLen;

        float ux = sy * fz - sz * fy;
        float uy = sz * fx - sx * fz;
        float uz = sx * fy - sy * fx;

        return new float[]{
                sx, ux, -fx, 0f,
                sy, uy, -fy, 0f,
                sz, uz, -fz, 0f,
                -(sx * eyeX + sy * eyeY + sz * eyeZ),
                -(ux * eyeX + uy * eyeY + uz * eyeZ),
                (fx * eyeX + fy * eyeY + fz * eyeZ),
                1f
        };
    }

    private static float[] perspective(float fovRad, float aspect, float near, float far) {
        float f = 1.0f / (float) Math.tan(fovRad * 0.5f);
        float nf = 1.0f / (near - far);
        return new float[]{
                f / aspect, 0f, 0f, 0f,
                0f, f, 0f, 0f,
                0f, 0f, (far + near) * nf, -1f,
                0f, 0f, (2f * far * near) * nf, 0f
        };
    }

    private static float[] mul(float[] a, float[] b) {
        float[] out = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                out[c * 4 + r] = a[r] * b[c * 4]
                        + a[4 + r] * b[c * 4 + 1]
                        + a[8 + r] * b[c * 4 + 2]
                        + a[12 + r] * b[c * 4 + 3];
            }
        }
        return out;
    }
}
