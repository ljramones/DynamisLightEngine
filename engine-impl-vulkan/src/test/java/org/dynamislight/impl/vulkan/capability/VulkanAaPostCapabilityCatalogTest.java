package org.dynamislight.impl.vulkan.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class VulkanAaPostCapabilityCatalogTest {
    @Test
    void aaCatalogContainsAllRuntimeAaModes() {
        var aa = VulkanAaPostCapabilityCatalog.aaCapabilities();
        Set<String> ids = aa.stream().map(c -> c.contract().featureId()).collect(Collectors.toSet());

        assertEquals(VulkanAaCapabilityMode.values().length, aa.size());
        assertTrue(ids.contains("vulkan.aa.taa"));
        assertTrue(ids.contains("vulkan.aa.tsr"));
        assertTrue(ids.contains("vulkan.aa.tuua"));
        assertTrue(ids.contains("vulkan.aa.msaa_selective"));
        assertTrue(ids.contains("vulkan.aa.hybrid_tuua_msaa"));
        assertTrue(ids.contains("vulkan.aa.dlaa"));
        assertTrue(ids.contains("vulkan.aa.fxaa_low"));
    }

    @Test
    void taaCapabilityDeclaresHistoryResourcesAsRequired() {
        var taa = VulkanAaCapability.of(VulkanAaCapabilityMode.TAA).contract();
        var historyColor = taa.resourceRequirements().stream()
                .filter(r -> "history_color".equals(r.name()))
                .findFirst()
                .orElseThrow();
        var historyVelocity = taa.resourceRequirements().stream()
                .filter(r -> "history_velocity".equals(r.name()))
                .findFirst()
                .orElseThrow();

        assertTrue(historyColor.required());
        assertTrue(historyVelocity.required());
    }

    @Test
    void fxaaCapabilityDoesNotRequireTemporalHistoryResources() {
        var fxaa = VulkanAaCapability.of(VulkanAaCapabilityMode.FXAA_LOW).contract();
        var historyColor = fxaa.resourceRequirements().stream()
                .filter(r -> "history_color".equals(r.name()))
                .findFirst()
                .orElseThrow();
        var historyVelocity = fxaa.resourceRequirements().stream()
                .filter(r -> "history_velocity".equals(r.name()))
                .findFirst()
                .orElseThrow();

        assertFalse(historyColor.required());
        assertFalse(historyVelocity.required());
    }

    @Test
    void postCatalogContainsExpectedCoreModules() {
        var post = VulkanAaPostCapabilityCatalog.postCapabilities();
        Set<String> ids = post.stream().map(c -> c.contract().featureId()).collect(Collectors.toSet());

        assertEquals(VulkanPostCapabilityId.values().length, post.size());
        assertTrue(ids.contains("vulkan.post.tonemap"));
        assertTrue(ids.contains("vulkan.post.bloom"));
        assertTrue(ids.contains("vulkan.post.ssao"));
        assertTrue(ids.contains("vulkan.post.smaa"));
        assertTrue(ids.contains("vulkan.post.taa_resolve"));
        assertTrue(ids.contains("vulkan.post.fog_composite"));
    }
}
