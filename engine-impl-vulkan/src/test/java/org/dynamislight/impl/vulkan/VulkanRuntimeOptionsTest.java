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
                        "vulkan.shadow.maxShadowFacesPerFrame", "6",
                        "vulkan.shadow.maxShadowedLocalLights", "7",
                        "vulkan.shadow.scheduler.enabled", "false",
                        "vulkan.shadow.scheduler.heroPeriod", "2",
                        "vulkan.shadow.scheduler.midPeriod", "3",
                        "vulkan.shadow.scheduler.distantPeriod", "8"
                ),
                256
        );

        assertEquals(7, parsed.shadowMaxShadowedLocalLights());
        assertEquals(12, parsed.shadowMaxLocalLayers());
        assertEquals(6, parsed.shadowMaxFacesPerFrame());
        assertEquals(false, parsed.shadowSchedulerEnabled());
        assertEquals(2, parsed.shadowSchedulerHeroPeriod());
        assertEquals(3, parsed.shadowSchedulerMidPeriod());
        assertEquals(8, parsed.shadowSchedulerDistantPeriod());
    }

    @Test
    void parseShadowSchedulerOverridesFallbackToBounds() {
        VulkanRuntimeOptions.Parsed parsed = VulkanRuntimeOptions.parse(
                Map.of(
                        "vulkan.shadow.maxLocalShadowLayers", "99",
                        "vulkan.shadow.maxShadowFacesPerFrame", "-1",
                        "vulkan.shadow.maxShadowedLocalLights", "99"
                ),
                256
        );

        assertEquals(8, parsed.shadowMaxShadowedLocalLights());
        assertEquals(24, parsed.shadowMaxLocalLayers());
        assertEquals(0, parsed.shadowMaxFacesPerFrame());
        assertEquals(true, parsed.shadowSchedulerEnabled());
        assertEquals(1, parsed.shadowSchedulerHeroPeriod());
        assertEquals(2, parsed.shadowSchedulerMidPeriod());
        assertEquals(4, parsed.shadowSchedulerDistantPeriod());
    }
}
