package org.dynamislight.impl.vulkan;

import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import java.util.Set;
import org.dynamislight.api.runtime.EngineCapabilities;
import org.dynamislight.api.config.EngineConfig;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.FogDesc;
import org.dynamislight.api.scene.FogMode;
import org.dynamislight.api.scene.SmokeEmitterDesc;
import org.dynamislight.api.scene.TransformDesc;
import org.dynamislight.api.scene.Vec3;
import org.dynamislight.impl.common.AbstractEngineRuntime;

public final class VulkanEngineRuntime extends AbstractEngineRuntime {
    private final VulkanContext context = new VulkanContext();
    private boolean mockContext = true;
    private boolean windowVisible;
    private boolean forceDeviceLostOnRender;
    private boolean deviceLostRaised;
    private QualityTier qualityTier = QualityTier.MEDIUM;
    private long plannedDrawCalls = 1;
    private long plannedTriangles = 1;
    private long plannedVisibleObjects = 1;
    private Path assetRoot = Path.of(".");
    private VulkanMeshAssetLoader meshLoader = new VulkanMeshAssetLoader(assetRoot);
    private int viewportWidth = 1280;
    private int viewportHeight = 720;
    private FogRenderConfig currentFog = new FogRenderConfig(false, 0.5f, 0.5f, 0.5f, 0f, 0, false);
    private SmokeRenderConfig currentSmoke = new SmokeRenderConfig(false, 0.6f, 0.6f, 0.6f, 0f, false);

    public VulkanEngineRuntime() {
        super(
                "Vulkan",
                new EngineCapabilities(
                        Set.of("vulkan"),
                        true,
                        true,
                        true,
                        true,
                        7680,
                        4320,
                        Set.of(QualityTier.LOW, QualityTier.MEDIUM, QualityTier.HIGH, QualityTier.ULTRA)
                ),
                16.2,
                7.8
        );
    }

    @Override
    protected void onInitialize(EngineConfig config) throws EngineException {
        mockContext = Boolean.parseBoolean(config.backendOptions().getOrDefault("vulkan.mockContext", "true"));
        windowVisible = Boolean.parseBoolean(config.backendOptions().getOrDefault("vulkan.windowVisible", "false"));
        forceDeviceLostOnRender = Boolean.parseBoolean(config.backendOptions().getOrDefault("vulkan.forceDeviceLostOnRender", "false"));
        assetRoot = config.assetRoot() == null ? Path.of(".") : config.assetRoot();
        meshLoader = new VulkanMeshAssetLoader(assetRoot);
        qualityTier = config.qualityTier();
        viewportWidth = config.initialWidthPx();
        viewportHeight = config.initialHeightPx();
        deviceLostRaised = false;
        if (Boolean.parseBoolean(config.backendOptions().getOrDefault("vulkan.forceInitFailure", "false"))) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "Forced Vulkan init failure", false);
        }
        if (!mockContext) {
            context.initialize(config.appName(), config.initialWidthPx(), config.initialHeightPx(), windowVisible);
            context.setPlannedWorkload(plannedDrawCalls, plannedTriangles, plannedVisibleObjects);
        }
    }

    @Override
    protected void onLoadScene(SceneDescriptor scene) throws EngineException {
        CameraDesc camera = selectActiveCamera(scene);
        CameraMatrices cameraMatrices = cameraMatricesFor(camera, safeAspect(viewportWidth, viewportHeight));
        LightingConfig lighting = mapLighting(scene == null ? null : scene.lights());
        FogRenderConfig fog = mapFog(scene == null ? null : scene.fog(), qualityTier);
        SmokeRenderConfig smoke = mapSmoke(scene == null ? null : scene.smokeEmitters(), qualityTier);
        currentFog = fog;
        currentSmoke = smoke;
        List<VulkanContext.SceneMeshData> sceneMeshes = buildSceneMeshes(scene);
        plannedDrawCalls = sceneMeshes.size();
        plannedTriangles = sceneMeshes.stream().mapToLong(m -> m.indices().length / 3).sum();
        plannedVisibleObjects = plannedDrawCalls;
        if (!mockContext) {
            context.setCameraMatrices(cameraMatrices.view(), cameraMatrices.proj());
            context.setLightingParameters(
                    lighting.directionalDirection(),
                    lighting.directionalColor(),
                    lighting.directionalIntensity(),
                    lighting.pointPosition(),
                    lighting.pointColor(),
                    lighting.pointIntensity()
            );
            context.setFogParameters(fog.enabled(), fog.r(), fog.g(), fog.b(), fog.density(), fog.steps());
            context.setSmokeParameters(smoke.enabled(), smoke.r(), smoke.g(), smoke.b(), smoke.intensity());
            context.setSceneMeshes(sceneMeshes);
            context.setPlannedWorkload(plannedDrawCalls, plannedTriangles, plannedVisibleObjects);
        }
    }

    @Override
    protected RenderMetrics onRender() throws EngineException {
        if (mockContext) {
            if (forceDeviceLostOnRender && !deviceLostRaised) {
                deviceLostRaised = true;
                throw new EngineException(EngineErrorCode.DEVICE_LOST, "Forced Vulkan device loss on render", false);
            }
            return renderMetrics(0.2, 0.1, plannedDrawCalls, plannedTriangles, plannedVisibleObjects, 0);
        }
        VulkanContext.VulkanFrameMetrics frame = context.renderFrame();
        return renderMetrics(
                frame.cpuFrameMs(),
                frame.gpuFrameMs(),
                frame.drawCalls(),
                frame.triangles(),
                frame.visibleObjects(),
                frame.gpuMemoryBytes()
        );
    }

    @Override
    protected void onResize(int widthPx, int heightPx, float dpiScale) throws EngineException {
        viewportWidth = widthPx;
        viewportHeight = heightPx;
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
    protected java.util.List<EngineWarning> baselineWarnings() {
        return java.util.List.of(new EngineWarning("FEATURE_BASELINE", "Vulkan backend active with baseline indexed render path"));
    }

    @Override
    protected java.util.List<EngineWarning> frameWarnings() {
        java.util.List<EngineWarning> warnings = new java.util.ArrayList<>();
        if (currentSmoke.enabled() && currentSmoke.degraded()) {
            warnings.add(new EngineWarning(
                    "SMOKE_QUALITY_DEGRADED",
                    "Smoke rendering quality reduced for tier " + qualityTier + " to maintain performance"
            ));
        }
        if (currentFog.enabled() && currentFog.degraded()) {
            warnings.add(new EngineWarning(
                    "FOG_QUALITY_DEGRADED",
                    "Fog sampling reduced at LOW quality tier"
            ));
        }
        return warnings;
    }

    private List<VulkanContext.SceneMeshData> buildSceneMeshes(SceneDescriptor scene) {
        if (scene == null || scene.meshes() == null || scene.meshes().isEmpty()) {
            return List.of(VulkanContext.SceneMeshData.defaultTriangle());
        }
        Map<String, MaterialDesc> materials = scene.materials() == null ? Map.of() : scene.materials().stream()
                .filter(m -> m != null && m.id() != null)
                .collect(java.util.stream.Collectors.toMap(MaterialDesc::id, m -> m, (a, b) -> a));
        Map<String, TransformDesc> transforms = scene.transforms() == null ? Map.of() : scene.transforms().stream()
                .filter(t -> t != null && t.id() != null)
                .collect(java.util.stream.Collectors.toMap(TransformDesc::id, t -> t, (a, b) -> a));

        List<VulkanContext.SceneMeshData> out = new java.util.ArrayList<>(scene.meshes().size());
        for (int i = 0; i < scene.meshes().size(); i++) {
            MeshDesc mesh = scene.meshes().get(i);
            if (mesh == null) {
                continue;
            }
            VulkanGltfMeshParser.MeshGeometry geometry = meshLoader.loadMeshGeometry(mesh, i);
            MaterialDesc material = materials.get(mesh.materialId());
            VulkanMaterialTextureSignal textureSignal = VulkanMaterialTextureSignal.fromMaterialTextures(
                    assetRoot,
                    material == null ? null : material.albedoTexturePath(),
                    material == null ? null : material.normalTexturePath()
            );
            float[] color = materialToColor(material, textureSignal.albedoTint());
            float metallic = material == null ? 0.0f : clamp01(material.metallic());
            float roughness = material == null ? 0.6f : clamp01(material.roughness() / textureSignal.normalStrength());
            float[] model = modelMatrixOf(transforms.get(mesh.transformId()), i);
            VulkanContext.SceneMeshData meshData = new VulkanContext.SceneMeshData(
                    geometry.vertices(),
                    geometry.indices(),
                    model,
                    color,
                    metallic,
                    roughness
            );
            out.add(meshData);
        }
        return out.isEmpty() ? List.of(VulkanContext.SceneMeshData.defaultTriangle()) : List.copyOf(out);
    }

    private static float[] materialToColor(MaterialDesc material, float[] textureTint) {
        Vec3 albedo = material == null ? null : material.albedo();
        if (albedo == null) {
            return new float[]{
                    clamp01(textureTint[0]),
                    clamp01(textureTint[1]),
                    clamp01(textureTint[2]),
                    1f
            };
        }
        return new float[]{
                clamp01(albedo.x() * textureTint[0]),
                clamp01(albedo.y() * textureTint[1]),
                clamp01(albedo.z() * textureTint[2]),
                1f
        };
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private record FogRenderConfig(boolean enabled, float r, float g, float b, float density, int steps, boolean degraded) {
    }

    private record SmokeRenderConfig(boolean enabled, float r, float g, float b, float intensity, boolean degraded) {
    }

    private record CameraMatrices(float[] view, float[] proj) {
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

    private static CameraDesc selectActiveCamera(SceneDescriptor scene) {
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

    private static CameraMatrices cameraMatricesFor(CameraDesc camera, float aspectRatio) {
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

    private static FogRenderConfig mapFog(FogDesc fogDesc, QualityTier qualityTier) {
        if (fogDesc == null || !fogDesc.enabled() || fogDesc.mode() == FogMode.NONE) {
            return new FogRenderConfig(false, 0.5f, 0.5f, 0.5f, 0f, 0, false);
        }
        float tierDensityScale = switch (qualityTier) {
            case LOW -> 0.55f;
            case MEDIUM -> 0.75f;
            case HIGH -> 1.0f;
            case ULTRA -> 1.2f;
        };
        int tierSteps = switch (qualityTier) {
            case LOW -> 4;
            case MEDIUM -> 8;
            case HIGH -> 16;
            case ULTRA -> 0;
        };
        float density = Math.max(0f, fogDesc.density() * tierDensityScale);
        return new FogRenderConfig(
                true,
                fogDesc.color() == null ? 0.5f : fogDesc.color().x(),
                fogDesc.color() == null ? 0.5f : fogDesc.color().y(),
                fogDesc.color() == null ? 0.5f : fogDesc.color().z(),
                density,
                tierSteps,
                qualityTier == QualityTier.LOW
        );
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
        return new SmokeRenderConfig(true, avgR, avgG, avgB, Math.min(0.85f, baseIntensity * tierScale),
                qualityTier == QualityTier.LOW || qualityTier == QualityTier.MEDIUM);
    }

    private static float[] modelMatrixOf(TransformDesc transform, int meshIndex) {
        if (transform == null) {
            float[] model = identityMatrix();
            model[12] = (meshIndex - 1) * 0.35f;
            return model;
        }
        Vec3 pos = transform.position() == null ? new Vec3(0f, 0f, 0f) : transform.position();
        Vec3 rot = transform.rotationEulerDeg() == null ? new Vec3(0f, 0f, 0f) : transform.rotationEulerDeg();
        Vec3 scl = transform.scale() == null ? new Vec3(1f, 1f, 1f) : transform.scale();

        float[] translation = translationMatrix(pos.x(), pos.y(), pos.z());
        float[] rotation = mul(mul(rotationZ(radians(rot.z())), rotationY(radians(rot.y()))), rotationX(radians(rot.x())));
        float[] scale = scaleMatrix(scl.x(), scl.y(), scl.z());
        return mul(translation, mul(rotation, scale));
    }

    private static float safeAspect(int width, int height) {
        if (height <= 0) {
            return 16f / 9f;
        }
        return Math.max(0.1f, (float) width / (float) height);
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
