package org.dynamislight.impl.vulkan.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.dynamislight.api.config.QualityTier;
import org.dynamislight.impl.vulkan.state.VulkanRenderState;
import org.junit.jupiter.api.Test;

class VulkanPipelineProfileResolverTest {
    @Test
    void resolveDefaultsToBaselineModes() {
        VulkanRenderState state = new VulkanRenderState();
        VulkanPipelineProfileKey key = VulkanPipelineProfileResolver.resolve(
                QualityTier.MEDIUM,
                state,
                0,
                0,
                0,
                0,
                true,
                false,
                false,
                false,
                false,
                false
        );
        assertEquals("pcf", key.shadowMode().id());
        assertEquals("ibl_only", key.reflectionMode().id());
        assertEquals("fxaa_low", key.aaMode().id());
        assertEquals("tonemap", key.postMode().id());
        assertEquals("baseline_directional_point_spot", key.lightingMode().id());
    }

    @Test
    void resolveReflectsRenderStateSignals() {
        VulkanRenderState state = new VulkanRenderState();
        state.shadowFilterMode = 2;
        state.reflectionsMode = 4;
        state.taaEnabled = false;
        VulkanPipelineProfileKey key = VulkanPipelineProfileResolver.resolve(
                QualityTier.HIGH,
                state,
                3,
                2,
                1,
                1,
                true,
                false,
                false,
                true,
                false,
                false
        );
        assertEquals("point_cubemap_budget", key.shadowMode().id());
        assertEquals("rt_hybrid", key.reflectionMode().id());
        assertEquals("fxaa_low", key.aaMode().id());
        assertEquals("tonemap", key.postMode().id());
        assertEquals("baseline_directional_point_spot", key.lightingMode().id());
    }

    @Test
    void resolvePromotesLightingBudgetModeForHighLocalLightLoad() {
        VulkanPipelineProfileKey key = VulkanPipelineProfileResolver.resolve(
                QualityTier.HIGH,
                new VulkanRenderState(),
                7,
                4,
                0,
                0,
                true,
                false,
                false,
                false,
                false,
                false
        );
        assertEquals("light_budget_priority", key.lightingMode().id());
    }
}
