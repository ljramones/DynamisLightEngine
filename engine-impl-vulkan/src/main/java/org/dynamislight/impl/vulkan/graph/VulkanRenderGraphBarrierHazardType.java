package org.dynamislight.impl.vulkan.graph;

/**
 * Logical hazard classification for render-graph barrier planning.
 */
public enum VulkanRenderGraphBarrierHazardType {
    IMPORT_TO_READ,
    IMPORT_TO_WRITE,
    READ_AFTER_WRITE,
    WRITE_AFTER_READ,
    WRITE_AFTER_WRITE
}
