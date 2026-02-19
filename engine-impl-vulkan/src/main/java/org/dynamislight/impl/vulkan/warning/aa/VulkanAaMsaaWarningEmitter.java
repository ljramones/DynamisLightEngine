package org.dynamislight.impl.vulkan.warning.aa;

import java.util.ArrayList;
import java.util.List;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.impl.vulkan.runtime.config.AaMode;

/**
 * Emits MSAA-selective/hybrid policy warnings and promotion readiness.
 */
public final class VulkanAaMsaaWarningEmitter {
    private VulkanAaMsaaWarningEmitter() {
    }

    public static Result emit(Input input) {
        Input safe = input == null ? Input.defaults() : input;
        boolean msaaMode = safe.aaMode() == AaMode.MSAA_SELECTIVE || safe.aaMode() == AaMode.HYBRID_TUUA_MSAA;
        if (!msaaMode) {
            return Result.inactive();
        }
        Analysis analysis = analyze(safe.materials());
        String modeId = safe.aaMode().name().toLowerCase(java.util.Locale.ROOT);
        boolean hybridTemporalMissing = safe.aaMode() == AaMode.HYBRID_TUUA_MSAA
                && safe.hybridTemporalRequired()
                && !safe.temporalPathActive();
        boolean risk = !safe.smaaEnabled()
                || analysis.candidateRatio() < safe.candidateWarnMinRatio()
                || hybridTemporalMissing;
        int highStreak = risk ? safe.previousHighStreak() + 1 : 0;
        int cooldownRemaining = safe.previousCooldownRemaining() <= 0 ? 0 : safe.previousCooldownRemaining() - 1;
        boolean breached = false;
        List<EngineWarning> warnings = new ArrayList<>();
        if (risk && highStreak >= safe.warnMinFrames() && cooldownRemaining <= 0) {
            breached = true;
            cooldownRemaining = safe.warnCooldownFrames();
            warnings.add(new EngineWarning(
                    "AA_MSAA_ENVELOPE_BREACH",
                    "AA MSAA envelope breach (mode=" + modeId
                            + ", smaaEnabled=" + safe.smaaEnabled()
                            + ", temporalPathActive=" + safe.temporalPathActive()
                            + ", candidateRatio=" + analysis.candidateRatio()
                            + ", candidateWarnMinRatio=" + safe.candidateWarnMinRatio()
                            + ", hybridTemporalMissing=" + hybridTemporalMissing + ")"
            ));
        }
        int stableStreak = risk ? 0 : safe.previousStableStreak() + 1;
        boolean promotionReady = stableStreak >= safe.promotionReadyMinFrames();
        warnings.add(new EngineWarning(
                "AA_MSAA_ENVELOPE",
                "AA MSAA envelope (mode=" + modeId
                        + ", risk=" + risk
                        + ", smaaEnabled=" + safe.smaaEnabled()
                        + ", temporalPathActive=" + safe.temporalPathActive()
                        + ", candidateRatio=" + analysis.candidateRatio()
                        + ", candidateWarnMinRatio=" + safe.candidateWarnMinRatio()
                        + ", hybridTemporalMissing=" + hybridTemporalMissing
                        + ", stableStreak=" + stableStreak
                        + ", promotionReadyMinFrames=" + safe.promotionReadyMinFrames()
                        + ", promotionReady=" + promotionReady + ")"
        ));
        warnings.add(new EngineWarning(
                "AA_MSAA_POLICY_ACTIVE",
                "AA MSAA policy active (mode=" + modeId
                        + ", materialCount=" + analysis.materialCount()
                        + ", candidateCount=" + analysis.candidateCount()
                        + ", candidateRatio=" + analysis.candidateRatio()
                        + ", smaaEnabled=" + safe.smaaEnabled()
                        + ", temporalPathActive=" + safe.temporalPathActive() + ")"
        ));
        if (promotionReady) {
            warnings.add(new EngineWarning(
                    "AA_MSAA_PROMOTION_READY",
                    "AA MSAA promotion-ready envelope satisfied (mode=" + modeId + ")"
            ));
        }
        return new Result(
                warnings,
                true,
                modeId,
                safe.smaaEnabled(),
                safe.temporalPathActive(),
                analysis.materialCount(),
                analysis.candidateCount(),
                analysis.candidateRatio(),
                safe.candidateWarnMinRatio(),
                safe.hybridTemporalRequired(),
                safe.promotionReadyMinFrames(),
                stableStreak,
                risk,
                breached,
                promotionReady,
                highStreak,
                cooldownRemaining
        );
    }

    private static Analysis analyze(List<MaterialDesc> materials) {
        if (materials == null || materials.isEmpty()) {
            return new Analysis(0, 0, 0.0);
        }
        int count = 0;
        int candidates = 0;
        for (MaterialDesc material : materials) {
            if (material == null) {
                continue;
            }
            count++;
            if (material.alphaTested() || material.foliage()) {
                candidates++;
            }
        }
        if (count <= 0) {
            return new Analysis(0, 0, 0.0);
        }
        return new Analysis(count, candidates, (double) candidates / (double) count);
    }

    public record Input(
            AaMode aaMode,
            List<MaterialDesc> materials,
            boolean smaaEnabled,
            boolean temporalPathActive,
            double candidateWarnMinRatio,
            boolean hybridTemporalRequired,
            int warnMinFrames,
            int warnCooldownFrames,
            int promotionReadyMinFrames,
            int previousStableStreak,
            int previousHighStreak,
            int previousCooldownRemaining
    ) {
        public Input {
            aaMode = aaMode == null ? AaMode.TAA : aaMode;
            materials = materials == null ? List.of() : List.copyOf(materials);
            candidateWarnMinRatio = clamp01(candidateWarnMinRatio);
            warnMinFrames = Math.max(1, warnMinFrames);
            warnCooldownFrames = Math.max(0, warnCooldownFrames);
            promotionReadyMinFrames = Math.max(1, promotionReadyMinFrames);
            previousStableStreak = Math.max(0, previousStableStreak);
            previousHighStreak = Math.max(0, previousHighStreak);
            previousCooldownRemaining = Math.max(0, previousCooldownRemaining);
        }

        static Input defaults() {
            return new Input(
                    AaMode.TAA,
                    List.of(),
                    false,
                    false,
                    0.05,
                    true,
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
            boolean msaaModeActive,
            String aaModeId,
            boolean smaaEnabled,
            boolean temporalPathActive,
            int materialCount,
            int msaaCandidateCount,
            double msaaCandidateRatio,
            double msaaCandidateWarnMinRatio,
            boolean hybridTemporalRequired,
            int promotionReadyMinFrames,
            int stableStreak,
            boolean envelopeRiskLastFrame,
            boolean envelopeBreachedLastFrame,
            boolean promotionReadyLastFrame,
            int nextHighStreak,
            int nextCooldownRemaining
    ) {
        static Result inactive() {
            return new Result(
                    List.of(),
                    false,
                    "",
                    false,
                    false,
                    0,
                    0,
                    0.0,
                    0.0,
                    true,
                    1,
                    0,
                    false,
                    false,
                    false,
                    0,
                    0
            );
        }

        public Result {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            aaModeId = aaModeId == null ? "" : aaModeId;
            materialCount = Math.max(0, materialCount);
            msaaCandidateCount = Math.max(0, msaaCandidateCount);
            msaaCandidateRatio = clamp01(msaaCandidateRatio);
            msaaCandidateWarnMinRatio = clamp01(msaaCandidateWarnMinRatio);
            promotionReadyMinFrames = Math.max(1, promotionReadyMinFrames);
            stableStreak = Math.max(0, stableStreak);
            nextHighStreak = Math.max(0, nextHighStreak);
            nextCooldownRemaining = Math.max(0, nextCooldownRemaining);
        }
    }

    private record Analysis(int materialCount, int candidateCount, double candidateRatio) {
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
