package org.dynamislight.impl.vulkan;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.dynamislight.api.runtime.EngineCapabilities;
import org.dynamislight.api.config.EngineConfig;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.Vec3;
import org.dynamislight.impl.common.AbstractEngineRuntime;

public final class VulkanEngineRuntime extends AbstractEngineRuntime {
    private final VulkanContext context = new VulkanContext();
    private boolean mockContext = true;
    private boolean windowVisible;
    private boolean forceDeviceLostOnRender;
    private boolean deviceLostRaised;
    private long plannedDrawCalls = 1;
    private long plannedTriangles = 1;
    private long plannedVisibleObjects = 1;

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
        plannedDrawCalls = scene.meshes() == null || scene.meshes().isEmpty() ? 1 : scene.meshes().size();
        plannedTriangles = estimateTriangles(scene.meshes());
        plannedVisibleObjects = plannedDrawCalls;
        if (!mockContext) {
            context.setSceneMeshes(buildSceneMeshes(scene));
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
        return java.util.List.of(new EngineWarning("FEATURE_LIMITED", "Vulkan backend currently renders baseline clear + triangle path"));
    }

    private static long estimateTriangles(List<MeshDesc> meshes) {
        if (meshes == null || meshes.isEmpty()) {
            return 1;
        }
        long triangles = 0;
        for (MeshDesc mesh : meshes) {
            String path = mesh == null || mesh.meshAssetPath() == null ? "" : mesh.meshAssetPath().toLowerCase(Locale.ROOT);
            triangles += (path.contains("quad") || path.contains("box")) ? 2 : 1;
        }
        return Math.max(1, triangles);
    }

    private static List<VulkanContext.SceneMeshData> buildSceneMeshes(SceneDescriptor scene) {
        if (scene == null || scene.meshes() == null || scene.meshes().isEmpty()) {
            return List.of(VulkanContext.SceneMeshData.defaultTriangle());
        }
        Map<String, MaterialDesc> materials = scene.materials() == null ? Map.of() : scene.materials().stream()
                .filter(m -> m != null && m.id() != null)
                .collect(java.util.stream.Collectors.toMap(MaterialDesc::id, m -> m, (a, b) -> a));

        List<VulkanContext.SceneMeshData> out = new java.util.ArrayList<>(scene.meshes().size());
        for (int i = 0; i < scene.meshes().size(); i++) {
            MeshDesc mesh = scene.meshes().get(i);
            if (mesh == null) {
                continue;
            }
            MaterialDesc material = materials.get(mesh.materialId());
            float[] color = materialToColor(material);
            String path = mesh.meshAssetPath() == null ? "" : mesh.meshAssetPath().toLowerCase(Locale.ROOT);
            VulkanContext.SceneMeshData meshData = path.contains("quad") || path.contains("plane")
                    ? VulkanContext.SceneMeshData.quad(color, i)
                    : VulkanContext.SceneMeshData.triangle(color, i);
            out.add(meshData);
        }
        return out.isEmpty() ? List.of(VulkanContext.SceneMeshData.defaultTriangle()) : List.copyOf(out);
    }

    private static float[] materialToColor(MaterialDesc material) {
        Vec3 albedo = material == null ? null : material.albedo();
        if (albedo == null) {
            return new float[]{1f, 1f, 1f, 1f};
        }
        return new float[]{
                clamp01(albedo.x()),
                clamp01(albedo.y()),
                clamp01(albedo.z()),
                1f
        };
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
