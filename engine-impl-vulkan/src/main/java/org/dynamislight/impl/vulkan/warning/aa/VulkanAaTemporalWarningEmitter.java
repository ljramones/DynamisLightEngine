package org.dynamislight.impl.vulkan.warning.aa;

import java.util.ArrayList;
import java.util.List;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.impl.vulkan.runtime.config.AaMode;

/**
 * Emits AA temporal envelope and promotion-readiness warnings.
 */
public final class VulkanAaTemporalWarningEmitter {
    private VulkanAaTemporalWarningEmitter() {
    }

    public static Result emit(Input input) {
        Input safe = input == null ? Input.defaults() : input;
        List<EngineWarning> warnings = new ArrayList<>();
        String aaModeId = safe.aaMode().name().toLowerCase(java.util.Locale.ROOT);
        boolean temporalPathRequested = isTemporalMode(safe.aaMode());
        boolean temporalPathActive = temporalPathRequested && safe.taaEnabled();

        if (!temporalPathActive) {
            warnings.add(new EngineWarning(
                    "AA_TEMPORAL_POLICY_ACTIVE",
                    "AA temporal policy (mode=" + aaModeId
                            + ", temporalRequested=" + temporalPathRequested
                            + ", temporalActive=false)"
            ));
            return new Result(
                    warnings,
                    aaModeId,
                    temporalPathRequested,
                    false,
                    safe.rejectRate(),
                    safe.confidenceMean(),
                    safe.confidenceDropEvents(),
                    safe.rejectWarnMax(),
                    safe.confidenceWarnMin(),
                    safe.dropWarnMin(),
                    safe.promotionReadyMinFrames(),
                    0,
                    false,
                    false,
                    0
            );
        }

        boolean envelopeRisk = safe.rejectRate() >= safe.rejectWarnMax()
                || safe.confidenceMean() <= safe.confidenceWarnMin()
                || safe.confidenceDropEvents() >= safe.dropWarnMin();
        int highStreak = envelopeRisk ? safe.previousHighStreak() + 1 : 0;
        int cooldownRemaining = safe.previousCooldownRemaining() <= 0 ? 0 : safe.previousCooldownRemaining() - 1;
        boolean breached = false;
        if (envelopeRisk && highStreak >= safe.warnMinFrames() && cooldownRemaining <= 0) {
            breached = true;
            cooldownRemaining = safe.warnCooldownFrames();
            warnings.add(new EngineWarning(
                    "AA_TEMPORAL_ENVELOPE_BREACH",
                    "AA temporal envelope breach (mode=" + aaModeId
                            + ", rejectRate=" + safe.rejectRate()
                            + ", confidenceMean=" + safe.confidenceMean()
                            + ", drops=" + safe.confidenceDropEvents() + ")"
            ));
        }
        int stableStreak = envelopeRisk ? 0 : safe.previousStableStreak() + 1;
        boolean promotionReady = stableStreak >= safe.promotionReadyMinFrames();

        warnings.add(new EngineWarning(
                "AA_TEMPORAL_ENVELOPE",
                "AA temporal envelope (mode=" + aaModeId
                        + ", risk=" + envelopeRisk
                        + ", rejectRate=" + safe.rejectRate()
                        + ", confidenceMean=" + safe.confidenceMean()
                        + ", drops=" + safe.confidenceDropEvents()
                        + ", rejectWarnMax=" + safe.rejectWarnMax()
                        + ", confidenceWarnMin=" + safe.confidenceWarnMin()
                        + ", dropWarnMin=" + safe.dropWarnMin()
                        + ", stableStreak=" + stableStreak
                        + ", promotionReadyMinFrames=" + safe.promotionReadyMinFrames()
                        + ", promotionReady=" + promotionReady + ")"
        ));
        if (promotionReady) {
            warnings.add(new EngineWarning(
                    "AA_TEMPORAL_PROMOTION_READY",
                    "AA temporal promotion-ready envelope satisfied (mode=" + aaModeId + ")"
            ));
        }
        warnings.add(new EngineWarning(
                "AA_TEMPORAL_POLICY_ACTIVE",
                "AA temporal policy (mode=" + aaModeId
                        + ", temporalRequested=true, temporalActive=true)"
        ));

        return new Result(
                warnings,
                aaModeId,
                true,
                true,
                safe.rejectRate(),
                safe.confidenceMean(),
                safe.confidenceDropEvents(),
                safe.rejectWarnMax(),
                safe.confidenceWarnMin(),
                safe.dropWarnMin(),
                safe.promotionReadyMinFrames(),
                stableStreak,
                breached,
                promotionReady,
                highStreak,
                cooldownRemaining
        );
    }

    private static boolean isTemporalMode(AaMode mode) {
        return switch (mode) {
            case TAA, TSR, TUUA, HYBRID_TUUA_MSAA, DLAA -> true;
            case MSAA_SELECTIVE, FXAA_LOW -> false;
        };
    }

    public record Input(
            AaMode aaMode,
            boolean taaEnabled,
            double rejectRate,
            double confidenceMean,
            long confidenceDropEvents,
            double rejectWarnMax,
            double confidenceWarnMin,
            long dropWarnMin,
            int warnMinFrames,
            int warnCooldownFrames,
            int promotionReadyMinFrames,
            int previousStableStreak,
            int previousHighStreak,
            int previousCooldownRemaining
    ) {
        public Input {
            aaMode = aaMode == null ? AaMode.TAA : aaMode;
            rejectRate = clamp01(rejectRate);
            confidenceMean = clamp01(confidenceMean);
            confidenceDropEvents = Math.max(0L, confidenceDropEvents);
            rejectWarnMax = clampPositive(rejectWarnMax);
            confidenceWarnMin = clampPositive(confidenceWarnMin);
            dropWarnMin = Math.max(0L, dropWarnMin);
            warnMinFrames = Math.max(1, warnMinFrames);
            warnCooldownFrames = Math.max(0, warnCooldownFrames);
            promotionReadyMinFrames = Math.max(1, promotionReadyMinFrames);
            previousStableStreak = Math.max(0, previousStableStreak);
            previousHighStreak = Math.max(0, previousHighStreak);
            previousCooldownRemaining = Math.max(0, previousCooldownRemaining);
        }

        public static Input defaults() {
            return new Input(
                    AaMode.TAA,
                    true,
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
            );
        }
    }

    public record Result(
            List<EngineWarning> warnings,
            String aaModeId,
            boolean temporalPathRequested,
            boolean temporalPathActive,
            double rejectRate,
            double confidenceMean,
            long confidenceDropEvents,
            double rejectWarnMax,
            double confidenceWarnMin,
            long dropWarnMin,
            int promotionReadyMinFrames,
            int stableStreak,
            boolean envelopeBreachedLastFrame,
            boolean promotionReadyLastFrame,
            int nextHighStreak,
            int nextCooldownRemaining
    ) {
        public Result(
                List<EngineWarning> warnings,
                String aaModeId,
                boolean temporalPathRequested,
                boolean temporalPathActive,
                double rejectRate,
                double confidenceMean,
                long confidenceDropEvents,
                double rejectWarnMax,
                double confidenceWarnMin,
                long dropWarnMin,
                int promotionReadyMinFrames,
                int stableStreak,
                boolean envelopeBreachedLastFrame,
                boolean promotionReadyLastFrame,
                int nextHighStreak
        ) {
            this(
                    warnings,
                    aaModeId,
                    temporalPathRequested,
                    temporalPathActive,
                    rejectRate,
                    confidenceMean,
                    confidenceDropEvents,
                    rejectWarnMax,
                    confidenceWarnMin,
                    dropWarnMin,
                    promotionReadyMinFrames,
                    stableStreak,
                    envelopeBreachedLastFrame,
                    promotionReadyLastFrame,
                    nextHighStreak,
                    0
            );
        }

        public Result {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            aaModeId = aaModeId == null ? "" : aaModeId;
            rejectRate = clamp01(rejectRate);
            confidenceMean = clamp01(confidenceMean);
            confidenceDropEvents = Math.max(0L, confidenceDropEvents);
            rejectWarnMax = clampPositive(rejectWarnMax);
            confidenceWarnMin = clampPositive(confidenceWarnMin);
            dropWarnMin = Math.max(0L, dropWarnMin);
            promotionReadyMinFrames = Math.max(1, promotionReadyMinFrames);
            stableStreak = Math.max(0, stableStreak);
            nextHighStreak = Math.max(0, nextHighStreak);
            nextCooldownRemaining = Math.max(0, nextCooldownRemaining);
        }
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double clampPositive(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, value);
    }
}
