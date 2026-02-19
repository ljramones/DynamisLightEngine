package org.dynamislight.impl.vulkan.warning.aa;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.ReactivePreset;
import org.dynamislight.api.scene.ReflectionOverrideMode;
import org.dynamislight.api.scene.Vec3;
import org.junit.jupiter.api.Test;

class VulkanAaTemporalMaterialWarningEmitterTest {
    @Test
    void noRiskWhenTemporalInactive() {
        VulkanAaTemporalMaterialWarningEmitter.Result result = VulkanAaTemporalMaterialWarningEmitter.emit(
                new VulkanAaTemporalMaterialWarningEmitter.Input(
                        List.of(defaultMaterial()),
                        false,
                        0.5,
                        2,
                        10,
                        0.5,
                        2,
                        10,
                        0,
                        0,
                        0,
                        0
                )
        );
        assertFalse(result.reactiveRisk());
        assertFalse(result.historyRisk());
        assertFalse(result.reactiveBreach());
        assertFalse(result.historyBreach());
    }

    @Test
    void risksTriggerBreachAfterMinFrames() {
        VulkanAaTemporalMaterialWarningEmitter.Result result = VulkanAaTemporalMaterialWarningEmitter.emit(
                new VulkanAaTemporalMaterialWarningEmitter.Input(
                        List.of(defaultMaterial()),
                        true,
                        0.9,
                        2,
                        0,
                        0.9,
                        2,
                        0,
                        1,
                        0,
                        1,
                        0
                )
        );
        assertTrue(result.reactiveRisk());
        assertTrue(result.historyRisk());
        assertTrue(result.reactiveBreach());
        assertTrue(result.historyBreach());
        assertTrue(result.warnings().stream().anyMatch(w -> "AA_REACTIVE_MASK_ENVELOPE_BREACH".equals(w.code())));
        assertTrue(result.warnings().stream().anyMatch(w -> "AA_HISTORY_CLAMP_ENVELOPE_BREACH".equals(w.code())));
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
