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

class VulkanAaQualityWarningEmitterTest {
    @Test
    void dlaaBreachTriggersWhenBlendBelowFloor() {
        VulkanAaQualityWarningEmitter.Result result = VulkanAaQualityWarningEmitter.emit(
                new VulkanAaQualityWarningEmitter.Input(
                        AaMode.DLAA,
                        List.of(defaultMaterial(false)),
                        true,
                        0.90,
                        1.0,
                        1.0,
                        0.95,
                        1.0,
                        1,
                        0,
                        2,
                        0,
                        0,
                        0,
                        1.1,
                        2,
                        0,
                        2,
                        0,
                        0,
                        0
                )
        );
        assertTrue(result.dlaaModeActive());
        assertTrue(result.dlaaEnvelopeBreachedLastFrame());
        assertTrue(result.warnings().stream().anyMatch(w -> "AA_DLAA_ENVELOPE_BREACH".equals(w.code())));
    }

    @Test
    void specularBreachTriggersWithNormalMappedMaterialsAndHighClipScale() {
        VulkanAaQualityWarningEmitter.Result result = VulkanAaQualityWarningEmitter.emit(
                new VulkanAaQualityWarningEmitter.Input(
                        AaMode.TAA,
                        List.of(defaultMaterial(true)),
                        true,
                        0.8,
                        1.0,
                        1.2,
                        0.90,
                        1.0,
                        2,
                        0,
                        2,
                        0,
                        0,
                        0,
                        1.0,
                        1,
                        0,
                        2,
                        0,
                        0,
                        0
                )
        );
        assertFalse(result.dlaaModeActive());
        assertTrue(result.specularPolicyActive());
        assertTrue(result.specularEnvelopeBreachedLastFrame());
        assertTrue(result.warnings().stream().anyMatch(w -> "AA_SPECULAR_ENVELOPE_BREACH".equals(w.code())));
    }

    private static MaterialDesc defaultMaterial(boolean withNormalMap) {
        return new MaterialDesc(
                "mat",
                new Vec3(1f, 1f, 1f),
                0f,
                0.5f,
                null,
                withNormalMap ? "n.png" : null,
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
