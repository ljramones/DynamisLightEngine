package org.dynamislight.impl.vulkan.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dynamislight.spi.render.RenderResourceType;

import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_HOST_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_HOST_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;

/**
 * Phase B.2 barrier derivation from compiled graph resource access events.
 */
public final class VulkanRenderGraphBarrierPlanner {
    public VulkanRenderGraphBarrierPlan plan(VulkanRenderGraphPlan graphPlan) {
        if (graphPlan == null) {
            return new VulkanRenderGraphBarrierPlan(List.of());
        }

        Map<String, VulkanImportedResource> importedByName = new HashMap<>();
        for (VulkanImportedResource imported : graphPlan.importedResources()) {
            importedByName.put(imported.resourceName(), imported);
        }

        Map<String, Integer> lastKnownLayout = new HashMap<>();
        List<VulkanRenderGraphBarrier> out = new ArrayList<>();
        Map<String, List<VulkanRenderGraphResourceAccessEvent>> accessByResource = graphPlan.resourceAccessOrder();

        for (Map.Entry<String, List<VulkanRenderGraphResourceAccessEvent>> entry : accessByResource.entrySet()) {
            String resource = entry.getKey();
            List<VulkanRenderGraphResourceAccessEvent> events = entry.getValue();
            if (events == null || events.isEmpty()) {
                continue;
            }

            VulkanImportedResource imported = importedByName.get(resource);
            RenderResourceType type = imported == null ? inferType(resource) : imported.resourceType();
            boolean imageResource = isImage(type);

            VulkanRenderGraphResourceAccessEvent first = events.getFirst();
            if (imported != null) {
                AccessSemantics dst = semantics(type, first.accessType(), resource);
                AccessSemantics src = importSource(imported, resource, dst);
                out.add(buildBarrier(
                        resource,
                        "import:" + imported.provider().name().toLowerCase(),
                        accessId(first),
                        first.accessType() == VulkanRenderGraphResourceAccessType.READ
                                ? VulkanRenderGraphBarrierHazardType.IMPORT_TO_READ
                                : VulkanRenderGraphBarrierHazardType.IMPORT_TO_WRITE,
                        src,
                        dst,
                        imageResource,
                        true,
                        false,
                        lastKnownLayout
                ));
            }

            for (int i = 1; i < events.size(); i++) {
                VulkanRenderGraphResourceAccessEvent previous = events.get(i - 1);
                VulkanRenderGraphResourceAccessEvent current = events.get(i);
                Hazard hazard = classify(previous.accessType(), current.accessType());
                if (hazard == Hazard.NONE) {
                    continue;
                }
                AccessSemantics src = semantics(type, previous.accessType(), resource);
                AccessSemantics dst = semantics(type, current.accessType(), resource);
                out.add(buildBarrier(
                        resource,
                        accessId(previous),
                        accessId(current),
                        toHazardType(hazard),
                        src,
                        dst,
                        imageResource,
                        false,
                        hazard == Hazard.WAR,
                        lastKnownLayout
                ));
            }
        }

        return new VulkanRenderGraphBarrierPlan(out);
    }

    private static VulkanRenderGraphBarrier buildBarrier(
            String resource,
            String sourceId,
            String destinationId,
            VulkanRenderGraphBarrierHazardType hazard,
            AccessSemantics src,
            AccessSemantics dst,
            boolean imageResource,
            boolean importBarrier,
            boolean executionOnly,
            Map<String, Integer> lastKnownLayout
    ) {
        int oldLayout = -1;
        int newLayout = -1;
        if (imageResource) {
            Integer tracked = lastKnownLayout.get(resource);
            oldLayout = tracked != null ? tracked : src.layout;
            newLayout = dst.layout;
            lastKnownLayout.put(resource, newLayout);
            if (executionOnly) {
                oldLayout = -1;
                newLayout = -1;
            }
        }

        int srcAccess = executionOnly ? 0 : src.accessMask;
        int dstAccess = executionOnly ? 0 : dst.accessMask;
        int srcStage = src.stageMask;
        int dstStage = dst.stageMask;

        if (importBarrier && srcStage == 0) {
            srcStage = VK_PIPELINE_STAGE_HOST_BIT;
        }
        if (dstStage == 0) {
            dstStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
        }

        return new VulkanRenderGraphBarrier(
                resource,
                sourceId,
                destinationId,
                hazard,
                srcStage,
                dstStage,
                srcAccess,
                dstAccess,
                oldLayout,
                newLayout,
                executionOnly
        );
    }

    private static String accessId(VulkanRenderGraphResourceAccessEvent event) {
        return event.nodeId() + "#" + event.nodeIndex() + ":" + event.accessType().name().toLowerCase();
    }

    private static RenderResourceType inferType(String resourceName) {
        String name = resourceName == null ? "" : resourceName.toLowerCase();
        if (name.contains("ubo") || name.contains("uniform")) {
            return RenderResourceType.UNIFORM_BUFFER;
        }
        if (name.contains("ssbo") || name.contains("probe") || name.contains("buffer")) {
            return RenderResourceType.STORAGE_BUFFER;
        }
        if (name.contains("depth") || name.contains("shadow") || name.contains("color") || name.contains("history") || name.contains("image") || name.contains("velocity")) {
            return RenderResourceType.SAMPLED_IMAGE;
        }
        return RenderResourceType.ATTACHMENT;
    }

    private static boolean isImage(RenderResourceType type) {
        return type == RenderResourceType.ATTACHMENT
                || type == RenderResourceType.SAMPLED_IMAGE
                || type == RenderResourceType.STORAGE_IMAGE;
    }

    private static AccessSemantics importSource(VulkanImportedResource imported, String resourceName, AccessSemantics defaultDst) {
        if (imported.provider() == VulkanImportedResource.ResourceProvider.CPU_UPLOAD) {
            if (isImage(imported.resourceType())) {
                int initialLayout = imported.lifetime() == VulkanImportedResource.ResourceLifetime.PERSISTENT
                        ? VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                        : VK_IMAGE_LAYOUT_GENERAL;
                return new AccessSemantics(VK_PIPELINE_STAGE_HOST_BIT, VK_ACCESS_HOST_WRITE_BIT, initialLayout);
            }
            return new AccessSemantics(VK_PIPELINE_STAGE_HOST_BIT, VK_ACCESS_HOST_WRITE_BIT, -1);
        }
        if (isImage(imported.resourceType())) {
            int initialLayout = imported.provider() == VulkanImportedResource.ResourceProvider.PREVIOUS_FRAME
                    ? VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                    : defaultDst.layout;
            return new AccessSemantics(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_ACCESS_SHADER_READ_BIT, initialLayout);
        }
        return new AccessSemantics(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_ACCESS_SHADER_READ_BIT, -1);
    }

    private static AccessSemantics semantics(
            RenderResourceType resourceType,
            VulkanRenderGraphResourceAccessType accessType,
            String resourceName
    ) {
        boolean depth = resourceName != null && resourceName.toLowerCase().contains("depth");
        boolean shadow = resourceName != null && resourceName.toLowerCase().contains("shadow");

        if (resourceType == RenderResourceType.UNIFORM_BUFFER || resourceType == RenderResourceType.STORAGE_BUFFER) {
            if (accessType == VulkanRenderGraphResourceAccessType.READ) {
                return new AccessSemantics(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_ACCESS_SHADER_READ_BIT, -1);
            }
            if (accessType == VulkanRenderGraphResourceAccessType.WRITE) {
                return new AccessSemantics(VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_WRITE_BIT, -1);
            }
            return new AccessSemantics(VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_WRITE_BIT, -1);
        }

        if (accessType == VulkanRenderGraphResourceAccessType.READ) {
            return new AccessSemantics(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_ACCESS_SHADER_READ_BIT, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        }
        if (accessType == VulkanRenderGraphResourceAccessType.WRITE) {
            if (depth || shadow) {
                return new AccessSemantics(
                        VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT,
                        VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                        VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL
                );
            }
            if (resourceType == RenderResourceType.STORAGE_IMAGE) {
                return new AccessSemantics(VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_WRITE_BIT, VK_IMAGE_LAYOUT_GENERAL);
            }
            return new AccessSemantics(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        }

        // READ_WRITE
        if (depth || shadow) {
            return new AccessSemantics(
                    VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT,
                    VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL
            );
        }
        return new AccessSemantics(VK_PIPELINE_STAGE_TRANSFER_BIT, VK_ACCESS_TRANSFER_WRITE_BIT | VK_ACCESS_TRANSFER_READ_BIT, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
    }

    private static Hazard classify(VulkanRenderGraphResourceAccessType previous, VulkanRenderGraphResourceAccessType current) {
        boolean prevRead = previous == VulkanRenderGraphResourceAccessType.READ || previous == VulkanRenderGraphResourceAccessType.READ_WRITE;
        boolean prevWrite = previous == VulkanRenderGraphResourceAccessType.WRITE || previous == VulkanRenderGraphResourceAccessType.READ_WRITE;
        boolean currRead = current == VulkanRenderGraphResourceAccessType.READ || current == VulkanRenderGraphResourceAccessType.READ_WRITE;
        boolean currWrite = current == VulkanRenderGraphResourceAccessType.WRITE || current == VulkanRenderGraphResourceAccessType.READ_WRITE;

        if (!prevWrite && !currWrite) {
            return Hazard.NONE;
        }
        if (prevWrite && currRead) {
            return Hazard.RAW;
        }
        if (prevRead && currWrite) {
            return Hazard.WAR;
        }
        if (prevWrite && currWrite) {
            return Hazard.WAW;
        }
        return Hazard.NONE;
    }

    private static VulkanRenderGraphBarrierHazardType toHazardType(Hazard hazard) {
        return switch (hazard) {
            case RAW -> VulkanRenderGraphBarrierHazardType.READ_AFTER_WRITE;
            case WAR -> VulkanRenderGraphBarrierHazardType.WRITE_AFTER_READ;
            case WAW -> VulkanRenderGraphBarrierHazardType.WRITE_AFTER_WRITE;
            case NONE -> VulkanRenderGraphBarrierHazardType.READ_AFTER_WRITE;
        };
    }

    private enum Hazard {
        NONE,
        RAW,
        WAR,
        WAW
    }

    private record AccessSemantics(int stageMask, int accessMask, int layout) {
    }
}
