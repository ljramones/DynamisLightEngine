package org.dynamislight.impl.vulkan;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.dynamislight.api.runtime.EngineCapabilities;
import org.dynamislight.api.config.EngineConfig;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.impl.common.AbstractEngineRuntime;

public final class VulkanEngineRuntime extends AbstractEngineRuntime {
    private final VulkanContext context = new VulkanContext();
    private boolean mockContext = true;
    private boolean windowVisible;
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
        if (Boolean.parseBoolean(config.backendOptions().getOrDefault("vulkan.forceInitFailure", "false"))) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "Forced Vulkan init failure", false);
        }
        if (!mockContext) {
            context.initialize(config.appName(), config.initialWidthPx(), config.initialHeightPx(), windowVisible);
            context.setPlannedWorkload(plannedDrawCalls, plannedTriangles, plannedVisibleObjects);
        }
    }

    @Override
    protected void onLoadScene(SceneDescriptor scene) {
        plannedDrawCalls = scene.meshes() == null || scene.meshes().isEmpty() ? 1 : scene.meshes().size();
        plannedTriangles = estimateTriangles(scene.meshes());
        plannedVisibleObjects = plannedDrawCalls;
        if (!mockContext) {
            context.setPlannedWorkload(plannedDrawCalls, plannedTriangles, plannedVisibleObjects);
        }
    }

    @Override
    protected RenderMetrics onRender() {
        if (mockContext) {
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
    protected void onShutdown() {
        if (!mockContext) {
            context.shutdown();
        }
    }

    @Override
    protected java.util.List<EngineWarning> baselineWarnings() {
        return java.util.List.of(new EngineWarning("FEATURE_LIMITED", "Vulkan backend currently runs without presentation surface"));
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
}
