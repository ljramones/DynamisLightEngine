package org.dynamisengine.light.impl.vulkan.scene;

import org.dynamisengine.light.api.scene.ReflectionProbeDesc;
import org.vectrix.core.Matrix4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class VulkanReflectionProbeRuntimeCoordinator {
    public record SlotAssignment(Map<String, Integer> slots, int slotCount) {
    }

    public record MetadataUploadResult(
            int activeProbeCount,
            int frustumVisibleCount,
            int deferredProbeCount,
            int visibleUniquePathCount,
            int missingSlotPathCount,
            int lodTier0Count,
            int lodTier1Count,
            int lodTier2Count,
            int lodTier3Count,
            long nextFrameTick
    ) {
    }

    public record MetadataUploadRequest(
            long mappedAddress,
            int metadataBufferBytes,
            int metadataMaxCount,
            int metadataStrideBytes,
            Matrix4f projMatrix,
            Matrix4f viewMatrix,
            List<ReflectionProbeDesc> reflectionProbes,
            Map<String, Integer> reflectionProbeCubemapSlots,
            int reflectionProbeCubemapSlotCount,
            long reflectionProbeFrameTick,
            int reflectionProbeUpdateCadenceFrames,
            int reflectionProbeMaxVisible,
            float reflectionProbeLodDepthScale
    ) {
    }

    public static SlotAssignment assignCubemapSlots(List<ReflectionProbeDesc> reflectionProbes, int maxSlots) {
        TreeSet<String> uniquePaths = new TreeSet<>();
        for (ReflectionProbeDesc probe : reflectionProbes) {
            if (probe == null) {
                continue;
            }
            String path = probe.cubemapAssetPath();
            if (path == null || path.isBlank()) {
                continue;
            }
            uniquePaths.add(path);
        }
        Map<String, Integer> slots = new HashMap<>();
        int nextSlot = 0;
        for (String path : uniquePaths) {
            if (nextSlot >= maxSlots) {
                break;
            }
            slots.put(path, nextSlot);
            nextSlot++;
        }
        return new SlotAssignment(Map.copyOf(slots), slots.size());
    }

    public static MetadataUploadResult updateMetadataBuffer(MetadataUploadRequest request) {
        if (request.mappedAddress() == 0L || request.metadataBufferBytes() <= 0) {
            return new MetadataUploadResult(0, 0, 0, 0, 0, 0, 0, 0, 0, request.reflectionProbeFrameTick());
        }
        float[] viewProj = new Matrix4f(request.projMatrix())
                .mul(request.viewMatrix(), new Matrix4f())
                .get(new float[16]);
        VulkanReflectionProbeCoordinator.UploadStats stats = VulkanReflectionProbeCoordinator.uploadVisibleProbes(
                request.reflectionProbes(),
                viewProj,
                request.metadataMaxCount(),
                request.metadataStrideBytes(),
                request.reflectionProbeCubemapSlots(),
                request.reflectionProbeCubemapSlotCount(),
                request.mappedAddress(),
                request.metadataBufferBytes(),
                request.reflectionProbeFrameTick(),
                request.reflectionProbeUpdateCadenceFrames(),
                request.reflectionProbeMaxVisible(),
                request.reflectionProbeLodDepthScale()
        );
        return new MetadataUploadResult(
                stats.activeProbeCount(),
                stats.frustumVisibleCount(),
                stats.deferredProbeCount(),
                stats.visibleUniquePaths(),
                stats.missingSlotPaths(),
                stats.lodTier0Count(),
                stats.lodTier1Count(),
                stats.lodTier2Count(),
                stats.lodTier3Count(),
                request.reflectionProbeFrameTick() + 1L
        );
    }

    private VulkanReflectionProbeRuntimeCoordinator() {
    }
}
