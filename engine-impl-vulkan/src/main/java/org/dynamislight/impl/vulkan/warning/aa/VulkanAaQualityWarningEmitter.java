package org.dynamislight.impl.vulkan.warning.aa;

import java.util.ArrayList;
import java.util.List;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.impl.vulkan.runtime.config.AaMode;

/**
 * Emits DLAA/specular-AA quality policy and promotion warnings.
 */
public final class VulkanAaQualityWarningEmitter {
    private VulkanAaQualityWarningEmitter() {
    }

    public static Result emit(Input input) {
        Input safe = input == null ? Input.defaults() : input;
        Analysis analysis = analyze(safe.materials());
        List<EngineWarning> warnings = new ArrayList<>();

        String modeId = safe.aaMode().name().toLowerCase(java.util.Locale.ROOT);
        boolean dlaaMode = safe.aaMode() == AaMode.DLAA;
        boolean dlaaRisk = dlaaMode && (!safe.temporalPathActive()
                || safe.taaBlend() < safe.dlaaWarnMinBlend()
                || safe.taaRenderScale() < safe.dlaaWarnMinRenderScale());
        int dlaaHighStreak = dlaaRisk ? safe.previousDlaaHighStreak() + 1 : 0;
        int dlaaCooldown = safe.previousDlaaCooldownRemaining() <= 0 ? 0 : safe.previousDlaaCooldownRemaining() - 1;
        boolean dlaaBreached = false;
        if (dlaaMode && dlaaRisk && dlaaHighStreak >= safe.dlaaWarnMinFrames() && dlaaCooldown <= 0) {
            dlaaBreached = true;
            dlaaCooldown = safe.dlaaWarnCooldownFrames();
            warnings.add(new EngineWarning(
                    "AA_DLAA_ENVELOPE_BREACH",
                    "AA DLAA envelope breach (temporalPathActive=" + safe.temporalPathActive()
                            + ", blend=" + safe.taaBlend()
                            + ", warnMinBlend=" + safe.dlaaWarnMinBlend()
                            + ", renderScale=" + safe.taaRenderScale()
                            + ", warnMinRenderScale=" + safe.dlaaWarnMinRenderScale() + ")"
            ));
        }
        int dlaaStableStreak = dlaaRisk ? 0 : (dlaaMode ? safe.previousDlaaStableStreak() + 1 : 0);
        boolean dlaaPromotionReady = dlaaMode && dlaaStableStreak >= safe.dlaaPromotionReadyMinFrames();
        if (dlaaMode) {
            warnings.add(new EngineWarning(
                    "AA_DLAA_POLICY_ACTIVE",
                    "AA DLAA policy active (temporalPathActive=" + safe.temporalPathActive()
                            + ", blend=" + safe.taaBlend()
                            + ", renderScale=" + safe.taaRenderScale() + ")"
            ));
            warnings.add(new EngineWarning(
                    "AA_DLAA_ENVELOPE",
                    "AA DLAA envelope (risk=" + dlaaRisk
                            + ", blend=" + safe.taaBlend()
                            + ", warnMinBlend=" + safe.dlaaWarnMinBlend()
                            + ", renderScale=" + safe.taaRenderScale()
                            + ", warnMinRenderScale=" + safe.dlaaWarnMinRenderScale()
                            + ", stableStreak=" + dlaaStableStreak
                            + ", promotionReady=" + dlaaPromotionReady + ")"
            ));
            if (dlaaPromotionReady) {
                warnings.add(new EngineWarning(
                        "AA_DLAA_PROMOTION_READY",
                        "AA DLAA promotion-ready envelope satisfied"
                ));
            }
        }

        boolean specularPolicyActive = safe.temporalPathActive() && analysis.normalMappedMaterialCount() > 0;
        boolean specularRisk = specularPolicyActive && safe.taaClipScale() > safe.specularWarnMaxClipScale();
        int specHighStreak = specularRisk ? safe.previousSpecularHighStreak() + 1 : 0;
        int specCooldown = safe.previousSpecularCooldownRemaining() <= 0 ? 0 : safe.previousSpecularCooldownRemaining() - 1;
        boolean specBreached = false;
        if (specularRisk && specHighStreak >= safe.specularWarnMinFrames() && specCooldown <= 0) {
            specBreached = true;
            specCooldown = safe.specularWarnCooldownFrames();
            warnings.add(new EngineWarning(
                    "AA_SPECULAR_ENVELOPE_BREACH",
                    "AA specular envelope breach (taaClipScale=" + safe.taaClipScale()
                            + ", warnMaxClipScale=" + safe.specularWarnMaxClipScale()
                            + ", normalMappedRatio=" + analysis.normalMappedMaterialRatio() + ")"
            ));
        }
        int specStableStreak = specularRisk ? 0 : (specularPolicyActive ? safe.previousSpecularStableStreak() + 1 : 0);
        boolean specPromotionReady = specularPolicyActive && specStableStreak >= safe.specularPromotionReadyMinFrames();
        warnings.add(new EngineWarning(
                "AA_SPECULAR_POLICY_ACTIVE",
                "AA specular policy active (active=" + specularPolicyActive
                        + ", materialCount=" + analysis.materialCount()
                        + ", normalMappedCount=" + analysis.normalMappedMaterialCount()
                        + ", normalMappedRatio=" + analysis.normalMappedMaterialRatio()
                        + ", taaClipScale=" + safe.taaClipScale() + ")"
        ));
        warnings.add(new EngineWarning(
                "AA_SPECULAR_ENVELOPE",
                "AA specular envelope (risk=" + specularRisk
                        + ", taaClipScale=" + safe.taaClipScale()
                        + ", warnMaxClipScale=" + safe.specularWarnMaxClipScale()
                        + ", stableStreak=" + specStableStreak
                        + ", promotionReady=" + specPromotionReady + ")"
        ));
        if (specPromotionReady) {
            warnings.add(new EngineWarning(
                    "AA_SPECULAR_PROMOTION_READY",
                    "AA specular promotion-ready envelope satisfied"
            ));
        }

        return new Result(
                warnings,
                modeId,
                dlaaMode,
                safe.temporalPathActive(),
                safe.taaBlend(),
                safe.taaRenderScale(),
                safe.dlaaWarnMinBlend(),
                safe.dlaaWarnMinRenderScale(),
                safe.dlaaPromotionReadyMinFrames(),
                dlaaStableStreak,
                dlaaBreached,
                dlaaPromotionReady,
                specularPolicyActive,
                analysis.materialCount(),
                analysis.normalMappedMaterialCount(),
                analysis.normalMappedMaterialRatio(),
                safe.taaClipScale(),
                safe.specularWarnMaxClipScale(),
                safe.specularPromotionReadyMinFrames(),
                specStableStreak,
                specBreached,
                specPromotionReady,
                dlaaHighStreak,
                dlaaCooldown,
                specHighStreak,
                specCooldown
        );
    }

    private static Analysis analyze(List<MaterialDesc> materials) {
        if (materials == null || materials.isEmpty()) {
            return new Analysis(0, 0, 0.0);
        }
        int count = 0;
        int normalMapped = 0;
        for (MaterialDesc material : materials) {
            if (material == null) {
                continue;
            }
            count++;
            if (material.normalTexturePath() != null && !material.normalTexturePath().isBlank()) {
                normalMapped++;
            }
        }
        if (count <= 0) {
            return new Analysis(0, 0, 0.0);
        }
        return new Analysis(count, normalMapped, (double) normalMapped / (double) count);
    }

    public record Input(
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
            int previousSpecularCooldownRemaining
    ) {
        public Input {
            aaMode = aaMode == null ? AaMode.TAA : aaMode;
            materials = materials == null ? List.of() : List.copyOf(materials);
            taaBlend = clamp01(taaBlend);
            taaRenderScale = clamp(taaRenderScale, 0.1, 2.0);
            taaClipScale = clamp(taaClipScale, 0.1, 2.0);
            dlaaWarnMinBlend = clamp01(dlaaWarnMinBlend);
            dlaaWarnMinRenderScale = clamp(dlaaWarnMinRenderScale, 0.1, 2.0);
            dlaaWarnMinFrames = Math.max(1, dlaaWarnMinFrames);
            dlaaWarnCooldownFrames = Math.max(0, dlaaWarnCooldownFrames);
            dlaaPromotionReadyMinFrames = Math.max(1, dlaaPromotionReadyMinFrames);
            previousDlaaStableStreak = Math.max(0, previousDlaaStableStreak);
            previousDlaaHighStreak = Math.max(0, previousDlaaHighStreak);
            previousDlaaCooldownRemaining = Math.max(0, previousDlaaCooldownRemaining);
            specularWarnMaxClipScale = clamp(specularWarnMaxClipScale, 0.1, 2.0);
            specularWarnMinFrames = Math.max(1, specularWarnMinFrames);
            specularWarnCooldownFrames = Math.max(0, specularWarnCooldownFrames);
            specularPromotionReadyMinFrames = Math.max(1, specularPromotionReadyMinFrames);
            previousSpecularStableStreak = Math.max(0, previousSpecularStableStreak);
            previousSpecularHighStreak = Math.max(0, previousSpecularHighStreak);
            previousSpecularCooldownRemaining = Math.max(0, previousSpecularCooldownRemaining);
        }

        static Input defaults() {
            return new Input(
                    AaMode.TAA,
                    List.of(),
                    false,
                    0.0,
                    1.0,
                    1.0,
                    0.90,
                    1.0,
                    3,
                    120,
                    6,
                    0,
                    0,
                    0,
                    1.1,
                    3,
                    120,
                    6,
                    0,
                    0,
                    0
            );
        }
    }

    public record Result(
            List<EngineWarning> warnings,
            String aaModeId,
            boolean dlaaModeActive,
            boolean dlaaTemporalPathActive,
            double dlaaBlend,
            double dlaaRenderScale,
            double dlaaWarnMinBlend,
            double dlaaWarnMinRenderScale,
            int dlaaPromotionReadyMinFrames,
            int dlaaStableStreak,
            boolean dlaaEnvelopeBreachedLastFrame,
            boolean dlaaPromotionReadyLastFrame,
            boolean specularPolicyActive,
            int materialCount,
            int normalMappedMaterialCount,
            double normalMappedMaterialRatio,
            double specularClipScale,
            double specularWarnMaxClipScale,
            int specularPromotionReadyMinFrames,
            int specularStableStreak,
            boolean specularEnvelopeBreachedLastFrame,
            boolean specularPromotionReadyLastFrame,
            int nextDlaaHighStreak,
            int nextDlaaCooldownRemaining,
            int nextSpecularHighStreak,
            int nextSpecularCooldownRemaining
    ) {
        public Result {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            aaModeId = aaModeId == null ? "" : aaModeId;
            materialCount = Math.max(0, materialCount);
            normalMappedMaterialCount = Math.max(0, normalMappedMaterialCount);
            normalMappedMaterialRatio = clamp01(normalMappedMaterialRatio);
            nextDlaaHighStreak = Math.max(0, nextDlaaHighStreak);
            nextDlaaCooldownRemaining = Math.max(0, nextDlaaCooldownRemaining);
            nextSpecularHighStreak = Math.max(0, nextSpecularHighStreak);
            nextSpecularCooldownRemaining = Math.max(0, nextSpecularCooldownRemaining);
        }
    }

    private record Analysis(int materialCount, int normalMappedMaterialCount, double normalMappedMaterialRatio) {
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
