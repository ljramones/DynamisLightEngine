package org.dynamislight.impl.vulkan.capability;

/**
 * Vulkan AA resolve capability modes mapped from runtime AA selections.
 */
public enum VulkanAaCapabilityMode {
    TAA,
    TSR,
    TUUA,
    MSAA_SELECTIVE,
    HYBRID_TUUA_MSAA,
    DLAA,
    FXAA_LOW
}
