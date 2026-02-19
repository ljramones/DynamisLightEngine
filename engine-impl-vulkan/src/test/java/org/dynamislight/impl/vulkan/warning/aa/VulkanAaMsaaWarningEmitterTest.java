package org.dynamislight.impl.vulkan.warning.aa;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.ReactivePreset;
import org.dynamislight.api.scene.ReflectionOverrideMode;
import org.dynamislight.api.scene.Vec3;
import org.dynamislight.impl.vulkan.runtime.config.AaMode;
import org.junit.jupiter.api.Test;

class VulkanAaMsaaWarningEmitterTest {
    @Test
    void inactiveForNonMsaaModes() {
        VulkanAaMsaaWarningEmitter.Result result = VulkanAaMsaaWarningEmitter.emit(
                new VulkanAaMsaaWarningEmitter.Input(
                        AaMode.TAA,
                        List.of(defaultMaterial()),
                        true,
                        true,
                        0.05,
                        true,
                        1,
                        0,
                        1,
                        0,
                        0,
                        0
                )
        );
        assertFalse(result.msaaModeActive());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void breachTriggersWithLowCandidateCoverage() {
        VulkanAaMsaaWarningEmitter.Result result = VulkanAaMsaaWarningEmitter.emit(
                new VulkanAaMsaaWarningEmitter.Input(
                        AaMode.MSAA_SELECTIVE,
                        List.of(defaultMaterial()),
                        true,
                        false,
                        0.8,
                        true,
                        1,
                        0,
                        2,
                        0,
                        0,
                        0
                )
        );
        assertTrue(result.msaaModeActive());
        assertTrue(result.envelopeRiskLastFrame());
        assertTrue(result.envelopeBreachedLastFrame());
        assertTrue(result.warnings().stream().anyMatch(w -> "AA_MSAA_ENVELOPE_BREACH".equals(w.code())));
    }

    private static MaterialDesc defaultMaterial() {
        return new MaterialDesc(
                "mat",
                new Vec3(1f, 1f, 1f),
                0f,
                0.5f,
                null,
                null,
                null,
                null,
                0f,
                false,
                false,
                1.0f,
                1.0f,
                1.0f,
                ReactivePreset.AUTO,
                ReflectionOverrideMode.AUTO
        );
    }
}
