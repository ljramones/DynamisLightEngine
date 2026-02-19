package org.dynamislight.impl.vulkan.warning.aa;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dynamislight.impl.vulkan.runtime.config.AaMode;
import org.dynamislight.impl.vulkan.runtime.config.UpscalerMode;
import org.junit.jupiter.api.Test;

class VulkanAaUpscaleWarningEmitterTest {
    @Test
    void inactiveForNonUpscaleModes() {
        VulkanAaUpscaleWarningEmitter.Result result = VulkanAaUpscaleWarningEmitter.emit(
                new VulkanAaUpscaleWarningEmitter.Input(
                        AaMode.TAA,
                        true,
                        1.0,
                        UpscalerMode.NONE,
                        false,
                        "none",
                        0.5,
                        0.95,
                        0.95,
                        1,
                        0,
                        1,
                        0,
                        0,
                        0
                )
        );
        assertFalse(result.upscaleModeActive());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void breachTriggersWithUnmetPolicyAfterMinFrames() {
        VulkanAaUpscaleWarningEmitter.Result result = VulkanAaUpscaleWarningEmitter.emit(
                new VulkanAaUpscaleWarningEmitter.Input(
                        AaMode.TSR,
                        true,
                        1.0,
                        UpscalerMode.DLSS,
                        false,
                        "none",
                        0.5,
                        0.9,
                        0.95,
                        2,
                        0,
                        3,
                        0,
                        1,
                        0
                )
        );
        assertTrue(result.upscaleModeActive());
        assertTrue(result.envelopeRiskLastFrame());
        assertTrue(result.envelopeBreachedLastFrame());
        assertTrue(result.warnings().stream().anyMatch(w -> "AA_UPSCALE_ENVELOPE_BREACH".equals(w.code())));
    }
}
