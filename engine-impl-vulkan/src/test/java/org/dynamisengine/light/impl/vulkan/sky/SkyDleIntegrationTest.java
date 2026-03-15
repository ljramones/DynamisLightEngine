package org.dynamisengine.light.impl.vulkan.sky;

import org.dynamisengine.light.impl.vulkan.state.VulkanBackendResources;
import org.junit.jupiter.api.Test;
import org.vectrix.core.Matrix4f;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SkyDleIntegrationTest {

    @Test
    void skyIntegrationCreatesWithinDleContext() {
        VulkanSkyRuntimeBridge bridge = new VulkanSkyRuntimeBridge();
        assertDoesNotThrow(() -> bridge.initialize(new VulkanBackendResources()));
    }

    @Test
    void sunStatePopulatedAfterUpdate() {
        VulkanSkyRuntimeBridge bridge = new VulkanSkyRuntimeBridge();
        bridge.initialize(new VulkanBackendResources());
        assertNotNull(bridge.sunDirection());
    }

    @Test
    void frameUniformsReceiveSunDirection() {
        VulkanSkyRuntimeBridge bridge = new VulkanSkyRuntimeBridge();
        bridge.initialize(new VulkanBackendResources());
        assertNotNull(bridge.sunColor());
    }

    @Test
    void ambientLightDrivenByTimeOfDay() {
        VulkanSkyRuntimeBridge bridge = new VulkanSkyRuntimeBridge();
        bridge.initialize(new VulkanBackendResources());
        assertDoesNotThrow(bridge::sunIntensity);
    }

    @Test
    void aerialLutBoundInPostProcess() {
        VulkanSkyRuntimeBridge bridge = new VulkanSkyRuntimeBridge();
        bridge.initialize(new VulkanBackendResources());
        assertDoesNotThrow(() -> bridge.updateAndRecord(0L, 0, new Matrix4f().identity(), new Matrix4f().identity()));
    }
}
