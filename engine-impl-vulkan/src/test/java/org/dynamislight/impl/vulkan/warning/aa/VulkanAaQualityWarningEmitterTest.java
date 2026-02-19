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
        VulkanAaQualityWarningEmitter.Result result = VulkanAaQualityWarningEmitter.emit(input(
                AaMode.DLAA,
                List.of(defaultMaterial(true, true)),
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
                0,
                0.05,
                2,
                0,
                2,
                0,
                0,
                0,
                0.05,
                2,
                0,
                2,
                0,
                0,
                0
        ));
        assertTrue(result.dlaaModeActive());
        assertTrue(result.dlaaEnvelopeBreachedLastFrame());
        assertTrue(result.warnings().stream().anyMatch(w -> "AA_DLAA_ENVELOPE_BREACH".equals(w.code())));
    }

    @Test
    void specularBreachTriggersWithNormalMappedMaterialsAndHighClipScale() {
        VulkanAaQualityWarningEmitter.Result result = VulkanAaQualityWarningEmitter.emit(input(
                AaMode.TAA,
                List.of(defaultMaterial(true, true)),
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
                0,
                0.05,
                2,
                0,
                2,
                0,
                0,
                0,
                0.05,
                2,
                0,
                2,
                0,
                0,
                0
        ));
        assertFalse(result.dlaaModeActive());
        assertTrue(result.specularPolicyActive());
        assertTrue(result.specularEnvelopeBreachedLastFrame());
        assertTrue(result.warnings().stream().anyMatch(w -> "AA_SPECULAR_ENVELOPE_BREACH".equals(w.code())));
    }

    @Test
    void geometricAndA2cBreachesTriggerForThinFeatureRatioFloor() {
        VulkanAaQualityWarningEmitter.Result result = VulkanAaQualityWarningEmitter.emit(input(
                AaMode.MSAA_SELECTIVE,
                List.of(defaultMaterial(false, false)),
                false,
                0.8,
                1.0,
                1.0,
                0.90,
                1.0,
                2,
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
                0,
                0.95,
                1,
                0,
                2,
                0,
                0,
                0,
                0.95,
                1,
                0,
                2,
                0,
                0,
                0
        ));
        assertTrue(result.geometricPolicyActive());
        assertTrue(result.geometricEnvelopeBreachedLastFrame());
        assertTrue(result.alphaToCoveragePolicyActive());
        assertTrue(result.alphaToCoverageEnvelopeBreachedLastFrame());
        assertTrue(result.warnings().stream().anyMatch(w -> "AA_GEOMETRIC_ENVELOPE_BREACH".equals(w.code())));
        assertTrue(result.warnings().stream().anyMatch(w -> "AA_A2C_ENVELOPE_BREACH".equals(w.code())));
    }

    private static VulkanAaQualityWarningEmitter.Input input(
            AaMode aaMode,
            List<MaterialDesc> materials,
            boolean temporalPathActive,
            double taaBlend,
            double taaRenderScale,
            double taaClipScale,
            double dlaaWarnMinBlend,
            double dlaaWarnMinRenderScale,
            int dlaaWarnMinFrames,
            int dlaaWarnCooldownFrames,
            int dlaaPromotionReadyMinFrames,
            int previousDlaaStableStreak,
            int previousDlaaHighStreak,
            int previousDlaaCooldownRemaining,
            double specularWarnMaxClipScale,
            int specularWarnMinFrames,
            int specularWarnCooldownFrames,
            int specularPromotionReadyMinFrames,
            int previousSpecularStableStreak,
            int previousSpecularHighStreak,
            int previousSpecularCooldownRemaining,
            double geometricWarnMinThinFeatureRatio,
            int geometricWarnMinFrames,
            int geometricWarnCooldownFrames,
            int geometricPromotionReadyMinFrames,
            int previousGeometricStableStreak,
            int previousGeometricHighStreak,
            int previousGeometricCooldownRemaining,
            double a2cWarnMinThinFeatureRatio,
            int a2cWarnMinFrames,
            int a2cWarnCooldownFrames,
            int a2cPromotionReadyMinFrames,
            int previousA2cStableStreak,
            int previousA2cHighStreak,
            int previousA2cCooldownRemaining
    ) {
        return new VulkanAaQualityWarningEmitter.Input(
                aaMode,
                materials,
                temporalPathActive,
                taaBlend,
                taaRenderScale,
                taaClipScale,
                dlaaWarnMinBlend,
                dlaaWarnMinRenderScale,
                dlaaWarnMinFrames,
                dlaaWarnCooldownFrames,
                dlaaPromotionReadyMinFrames,
                previousDlaaStableStreak,
                previousDlaaHighStreak,
                previousDlaaCooldownRemaining,
                specularWarnMaxClipScale,
                specularWarnMinFrames,
                specularWarnCooldownFrames,
                specularPromotionReadyMinFrames,
                previousSpecularStableStreak,
                previousSpecularHighStreak,
                previousSpecularCooldownRemaining,
                geometricWarnMinThinFeatureRatio,
                geometricWarnMinFrames,
                geometricWarnCooldownFrames,
                geometricPromotionReadyMinFrames,
                previousGeometricStableStreak,
                previousGeometricHighStreak,
                previousGeometricCooldownRemaining,
                a2cWarnMinThinFeatureRatio,
                a2cWarnMinFrames,
                a2cWarnCooldownFrames,
                a2cPromotionReadyMinFrames,
                previousA2cStableStreak,
                previousA2cHighStreak,
                previousA2cCooldownRemaining
        );
    }

    private static MaterialDesc defaultMaterial(boolean withNormalMap, boolean thinFeature) {
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
                thinFeature,
                thinFeature,
                1.0f,
                1.0f,
                1.0f,
                ReactivePreset.AUTO,
                ReflectionOverrideMode.AUTO
        );
    }
}
