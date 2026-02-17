package org.dynamislight.impl.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class VulkanRuntimeOptionsTest {
    @Test
    void parseShadowSchedulerOverrides() {
        VulkanRuntimeOptions.Parsed parsed = VulkanRuntimeOptions.parse(
                Map.ofEntries(
                        Map.entry("vulkan.shadow.maxLocalShadowLayers", "12"),
                        Map.entry("vulkan.shadow.maxShadowFacesPerFrame", "6"),
                        Map.entry("vulkan.shadow.maxShadowedLocalLights", "7"),
                        Map.entry("vulkan.shadow.rtBvhStrict", "true"),
                        Map.entry("vulkan.shadow.rtDenoiseStrength", "0.8"),
                        Map.entry("vulkan.shadow.rtRayLength", "120"),
                        Map.entry("vulkan.shadow.rtSampleCount", "4"),
                        Map.entry("vulkan.shadow.pcssSoftness", "1.4"),
                        Map.entry("vulkan.shadow.momentBlend", "0.9"),
                        Map.entry("vulkan.shadow.momentBleedReduction", "1.2"),
                        Map.entry("vulkan.shadow.contactStrength", "1.5"),
                        Map.entry("vulkan.shadow.contactTemporalMotionScale", "1.8"),
                        Map.entry("vulkan.shadow.contactTemporalMinStability", "0.55"),
                        Map.entry("vulkan.shadow.scheduler.enabled", "false"),
                        Map.entry("vulkan.shadow.scheduler.heroPeriod", "2"),
                        Map.entry("vulkan.shadow.scheduler.midPeriod", "3"),
                        Map.entry("vulkan.shadow.scheduler.distantPeriod", "8"),
                        Map.entry("vulkan.shadow.directionalTexelSnapEnabled", "false"),
                        Map.entry("vulkan.shadow.directionalTexelSnapScale", "1.75")
                ),
                256
        );

        assertEquals("off", VulkanRuntimeOptions.parse(Map.of("vulkan.shadow.rtMode", "invalid"), 256).shadowRtMode());
        assertEquals("bvh", VulkanRuntimeOptions.parse(Map.of("vulkan.shadow.rtMode", "bvh"), 256).shadowRtMode());
        assertEquals(true, parsed.shadowRtBvhStrict());
        assertEquals(7, parsed.shadowMaxShadowedLocalLights());
        assertEquals(12, parsed.shadowMaxLocalLayers());
        assertEquals(6, parsed.shadowMaxFacesPerFrame());
        assertEquals(0.8f, parsed.shadowRtDenoiseStrength());
        assertEquals(120.0f, parsed.shadowRtRayLength());
        assertEquals(4, parsed.shadowRtSampleCount());
        assertEquals(1.4f, parsed.shadowPcssSoftness());
        assertEquals(0.9f, parsed.shadowMomentBlend());
        assertEquals(1.2f, parsed.shadowMomentBleedReduction());
        assertEquals(1.5f, parsed.shadowContactStrength());
        assertEquals(1.8f, parsed.shadowContactTemporalMotionScale());
        assertEquals(0.55f, parsed.shadowContactTemporalMinStability());
        assertEquals(false, parsed.shadowSchedulerEnabled());
        assertEquals(2, parsed.shadowSchedulerHeroPeriod());
        assertEquals(3, parsed.shadowSchedulerMidPeriod());
        assertEquals(8, parsed.shadowSchedulerDistantPeriod());
        assertEquals(false, parsed.shadowDirectionalTexelSnapEnabled());
        assertEquals(1.75f, parsed.shadowDirectionalTexelSnapScale());
    }

    @Test
    void parseShadowSchedulerOverridesFallbackToBounds() {
        VulkanRuntimeOptions.Parsed parsed = VulkanRuntimeOptions.parse(
                Map.ofEntries(
                        Map.entry("vulkan.shadow.maxLocalShadowLayers", "99"),
                        Map.entry("vulkan.shadow.maxShadowFacesPerFrame", "-1"),
                        Map.entry("vulkan.shadow.maxShadowedLocalLights", "99"),
                        Map.entry("vulkan.shadow.rtDenoiseStrength", "7.0"),
                        Map.entry("vulkan.shadow.rtRayLength", "0.1"),
                        Map.entry("vulkan.shadow.rtSampleCount", "99"),
                        Map.entry("vulkan.shadow.pcssSoftness", "9.0"),
                        Map.entry("vulkan.shadow.momentBlend", "0.1"),
                        Map.entry("vulkan.shadow.momentBleedReduction", "7.0"),
                        Map.entry("vulkan.shadow.contactStrength", "-1.0"),
                        Map.entry("vulkan.shadow.contactTemporalMotionScale", "-1.0"),
                        Map.entry("vulkan.shadow.contactTemporalMinStability", "1.5"),
                        Map.entry("vulkan.shadow.directionalTexelSnapScale", "10.0")
                ),
                256
        );

        assertEquals(8, parsed.shadowMaxShadowedLocalLights());
        assertEquals(24, parsed.shadowMaxLocalLayers());
        assertEquals(0, parsed.shadowMaxFacesPerFrame());
        assertEquals(1.0f, parsed.shadowRtDenoiseStrength());
        assertEquals(1.0f, parsed.shadowRtRayLength());
        assertEquals(16, parsed.shadowRtSampleCount());
        assertEquals(2.0f, parsed.shadowPcssSoftness());
        assertEquals(0.25f, parsed.shadowMomentBlend());
        assertEquals(1.5f, parsed.shadowMomentBleedReduction());
        assertEquals(0.25f, parsed.shadowContactStrength());
        assertEquals(0.1f, parsed.shadowContactTemporalMotionScale());
        assertEquals(1.0f, parsed.shadowContactTemporalMinStability());
        assertEquals(false, parsed.shadowRtBvhStrict());
        assertEquals(true, parsed.shadowSchedulerEnabled());
        assertEquals(1, parsed.shadowSchedulerHeroPeriod());
        assertEquals(2, parsed.shadowSchedulerMidPeriod());
        assertEquals(4, parsed.shadowSchedulerDistantPeriod());
        assertEquals(true, parsed.shadowDirectionalTexelSnapEnabled());
        assertEquals(4.0f, parsed.shadowDirectionalTexelSnapScale());
    }
}
