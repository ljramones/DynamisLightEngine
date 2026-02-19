package org.dynamislight.impl.vulkan.profile;

import org.dynamislight.impl.vulkan.VulkanContext;
import org.dynamislight.impl.vulkan.model.VulkanGpuMesh;
import org.dynamislight.impl.vulkan.model.VulkanSceneMeshData;

import java.util.ArrayList;
import java.util.List;

public final class VulkanContextDiagnosticsCoordinator {
    public static List<Integer> reflectionOverrideModes(List<VulkanGpuMesh> gpuMeshes, List<VulkanSceneMeshData> pendingSceneMeshes) {
        if (gpuMeshes != null && !gpuMeshes.isEmpty()) {
            List<Integer> modes = new ArrayList<>(gpuMeshes.size());
            for (VulkanGpuMesh mesh : gpuMeshes) {
                if (mesh == null) {
                    continue;
                }
                modes.add(Math.max(0, Math.min(3, mesh.reflectionOverrideMode)));
            }
            return List.copyOf(modes);
        }
        if (pendingSceneMeshes == null || pendingSceneMeshes.isEmpty()) {
            return List.of();
        }
        List<Integer> modes = new ArrayList<>(pendingSceneMeshes.size());
        for (VulkanSceneMeshData mesh : pendingSceneMeshes) {
            if (mesh == null) {
                continue;
            }
            modes.add(Math.max(0, Math.min(3, mesh.reflectionOverrideMode())));
        }
        return List.copyOf(modes);
    }

    public static VulkanContext.ReflectionProbeDiagnostics reflectionProbeDiagnostics(
            int configuredProbeCount,
            int activeProbeCount,
            int slotCount,
            int metadataCapacity,
            int frustumVisibleCount,
            int deferredProbeCount,
            int visibleUniquePathCount,
            int missingSlotPathCount,
            int lodTier0Count,
            int lodTier1Count,
            int lodTier2Count,
            int lodTier3Count
    ) {
        return new VulkanContext.ReflectionProbeDiagnostics(
                configuredProbeCount,
                activeProbeCount,
                slotCount,
                metadataCapacity,
                frustumVisibleCount,
                deferredProbeCount,
                visibleUniquePathCount,
                missingSlotPathCount,
                lodTier0Count,
                lodTier1Count,
                lodTier2Count,
                lodTier3Count
        );
    }

    public static SceneReuseStats sceneReuseStats(
            long sceneReuseHitCount,
            long sceneReorderReuseCount,
            long sceneTextureRebindCount,
            long sceneFullRebuildCount,
            long meshBufferRebuildCount,
            long descriptorPoolBuildCount,
            long descriptorPoolRebuildCount
    ) {
        return VulkanContextProfileCoordinator.sceneReuse(
                new VulkanContextProfileCoordinator.SceneReuseRequest(
                        sceneReuseHitCount,
                        sceneReorderReuseCount,
                        sceneTextureRebindCount,
                        sceneFullRebuildCount,
                        meshBufferRebuildCount,
                        descriptorPoolBuildCount,
                        descriptorPoolRebuildCount
                )
        );
    }

    public static FrameResourceProfile frameResourceProfile(VulkanContextProfileCoordinator.FrameResourceRequest request) {
        return VulkanContextProfileCoordinator.frameResource(request);
    }

    public static ShadowCascadeProfile shadowCascadeProfile(
            boolean shadowEnabled,
            int shadowCascadeCount,
            int shadowMapResolution,
            int shadowPcfRadius,
            float shadowBias,
            float[] shadowCascadeSplitNdc
    ) {
        return VulkanContextProfileCoordinator.shadowCascade(
                new VulkanContextProfileCoordinator.ShadowRequest(
                        shadowEnabled,
                        shadowCascadeCount,
                        shadowMapResolution,
                        shadowPcfRadius,
                        shadowBias,
                        shadowCascadeSplitNdc
                )
        );
    }

    public static PostProcessPipelineProfile postProcessPipelineProfile(boolean postOffscreenRequested, boolean postOffscreenActive) {
        return VulkanContextProfileCoordinator.postProcess(
                new VulkanContextProfileCoordinator.PostRequest(postOffscreenRequested, postOffscreenActive)
        );
    }

    private VulkanContextDiagnosticsCoordinator() {
    }
}
