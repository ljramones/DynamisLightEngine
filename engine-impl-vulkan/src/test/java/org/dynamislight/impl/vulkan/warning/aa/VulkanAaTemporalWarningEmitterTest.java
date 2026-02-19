package org.dynamislight.impl.vulkan.warning.aa;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dynamislight.impl.vulkan.runtime.config.AaMode;
import org.junit.jupiter.api.Test;

class VulkanAaTemporalWarningEmitterTest {
    @Test
    void nonTemporalModeMarksTemporalInactiveWithoutBreach() {
        VulkanAaTemporalWarningEmitter.Result result = VulkanAaTemporalWarningEmitter.emit(
                new VulkanAaTemporalWarningEmitter.Input(
                        AaMode.FXAA_LOW,
                        false,
                        0.0,
                        1.0,
                        0L,
                        0.24,
                        0.72,
                        2L,
                        3,
                        120,
                        6,
                        0,
                        0,
                        0
                )
        );
        assertFalse(result.temporalPathRequested());
        assertFalse(result.temporalPathActive());
        assertFalse(result.envelopeBreachedLastFrame());
        assertTrue(result.warnings().stream().anyMatch(w -> "AA_TEMPORAL_POLICY_ACTIVE".equals(w.code())));
    }

    @Test
    void temporalRiskTriggersEnvelopeBreachAfterMinFrames() {
        VulkanAaTemporalWarningEmitter.Result result = VulkanAaTemporalWarningEmitter.emit(
                new VulkanAaTemporalWarningEmitter.Input(
                        AaMode.TAA,
                        true,
                        0.50,
                        0.30,
                        8L,
                        0.24,
                        0.72,
                        2L,
                        2,
                        5,
                        6,
                        0,
                        1,
                        0
                )
        );
        assertTrue(result.temporalPathRequested());
        assertTrue(result.temporalPathActive());
        assertTrue(result.envelopeBreachedLastFrame());
        assertTrue(result.warnings().stream().anyMatch(w -> "AA_TEMPORAL_ENVELOPE_BREACH".equals(w.code())));
    }
}
