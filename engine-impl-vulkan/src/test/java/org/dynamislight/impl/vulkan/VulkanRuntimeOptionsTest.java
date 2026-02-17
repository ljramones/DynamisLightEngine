package org.dynamislight.impl.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class VulkanRuntimeOptionsTest {
    @Test
    void parseShadowSchedulerOverrides() {
        VulkanRuntimeOptions.Parsed parsed = VulkanRuntimeOptions.parse(
                Map.of(
                        "vulkan.shadow.maxLocalShadowLayers", "12",
                        "vulkan.shadow.maxShadowFacesPerFrame", "6"
                ),
                256
        );

        assertEquals(12, parsed.shadowMaxLocalLayers());
        assertEquals(6, parsed.shadowMaxFacesPerFrame());
    }

    @Test
    void parseShadowSchedulerOverridesFallbackToBounds() {
        VulkanRuntimeOptions.Parsed parsed = VulkanRuntimeOptions.parse(
                Map.of(
                        "vulkan.shadow.maxLocalShadowLayers", "99",
                        "vulkan.shadow.maxShadowFacesPerFrame", "-1"
                ),
                256
        );

        assertEquals(12, parsed.shadowMaxLocalLayers());
        assertEquals(0, parsed.shadowMaxFacesPerFrame());
    }
}
