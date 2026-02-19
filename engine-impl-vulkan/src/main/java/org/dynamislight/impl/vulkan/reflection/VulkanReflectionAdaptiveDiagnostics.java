package org.dynamislight.impl.vulkan.reflection;

final class VulkanReflectionAdaptiveDiagnostics {
    static final class ProbeChurnState {
        int lastActiveReflectionProbeCount;
        int reflectionProbeLastDelta;
        int reflectionProbeActiveChurnEvents;
        long reflectionProbeActiveDeltaAccum;
        int reflectionProbeChurnHighStreak;
        int reflectionProbeChurnWarnCooldownRemaining;
        int reflectionProbeChurnWarnMinDelta;
        int reflectionProbeChurnWarnMinStreak;
        int reflectionProbeChurnWarnCooldownFrames;
    }

    static final class ProbeChurnUpdateResult {
        final ProbeChurnState state;
        final ReflectionProbeChurnDiagnostics diagnostics;

        ProbeChurnUpdateResult(ProbeChurnState state, ReflectionProbeChurnDiagnostics diagnostics) {
            this.state = state;
            this.diagnostics = diagnostics;
        }
    }

    static final class SsrTaaRiskState {
        int reflectionSsrTaaRiskHighStreak;
        int reflectionSsrTaaRiskWarnCooldownRemaining;
        double reflectionSsrTaaEmaReject;
        double reflectionSsrTaaEmaConfidence;
        double reflectionSsrTaaLatestRejectRate;
        double reflectionSsrTaaLatestConfidenceMean;
        long reflectionSsrTaaLatestDropEvents;
        double reflectionSsrTaaInstabilityRejectMin;
        double reflectionSsrTaaInstabilityConfidenceMax;
        long reflectionSsrTaaInstabilityDropEventsMin;
        int reflectionSsrTaaInstabilityWarnMinFrames;
        int reflectionSsrTaaInstabilityWarnCooldownFrames;
        double reflectionSsrTaaRiskEmaAlpha;
    }

    static final class SsrTaaRiskUpdateResult {
        final SsrTaaRiskState state;
        final ReflectionSsrTaaRiskDiagnostics diagnostics;

        SsrTaaRiskUpdateResult(SsrTaaRiskState state, ReflectionSsrTaaRiskDiagnostics diagnostics) {
            this.state = state;
            this.diagnostics = diagnostics;
        }
    }

    static ProbeChurnUpdateResult updateProbeChurn(ProbeChurnState state, int activeProbeCount) {
        int active = Math.max(0, activeProbeCount);
        boolean warningTriggered = false;
        if (state.lastActiveReflectionProbeCount < 0) {
            state.lastActiveReflectionProbeCount = active;
            state.reflectionProbeLastDelta = 0;
            if (state.reflectionProbeChurnWarnCooldownRemaining > 0) {
                state.reflectionProbeChurnWarnCooldownRemaining--;
            }
            return new ProbeChurnUpdateResult(state, snapshotProbeChurn(state, false));
        }
        int delta = Math.abs(active - state.lastActiveReflectionProbeCount);
        state.reflectionProbeLastDelta = delta;
        if (delta > 0) {
            state.reflectionProbeActiveChurnEvents++;
            state.reflectionProbeActiveDeltaAccum += delta;
        }
        if (delta >= state.reflectionProbeChurnWarnMinDelta) {
            state.reflectionProbeChurnHighStreak++;
            if (state.reflectionProbeChurnHighStreak >= state.reflectionProbeChurnWarnMinStreak
                    && state.reflectionProbeChurnWarnCooldownRemaining <= 0) {
                state.reflectionProbeChurnWarnCooldownRemaining = state.reflectionProbeChurnWarnCooldownFrames;
                warningTriggered = true;
            }
        } else {
            state.reflectionProbeChurnHighStreak = 0;
        }
        if (state.reflectionProbeChurnWarnCooldownRemaining > 0) {
            state.reflectionProbeChurnWarnCooldownRemaining--;
        }
        state.lastActiveReflectionProbeCount = active;
        return new ProbeChurnUpdateResult(state, snapshotProbeChurn(state, warningTriggered));
    }

    static ReflectionProbeChurnDiagnostics snapshotProbeChurn(ProbeChurnState state, boolean warningTriggered) {
        double meanDelta = state.reflectionProbeActiveChurnEvents <= 0
                ? 0.0
                : (double) state.reflectionProbeActiveDeltaAccum / (double) state.reflectionProbeActiveChurnEvents;
        return new ReflectionProbeChurnDiagnostics(
                state.lastActiveReflectionProbeCount,
                state.reflectionProbeLastDelta,
                state.reflectionProbeActiveChurnEvents,
                meanDelta,
                state.reflectionProbeChurnHighStreak,
                state.reflectionProbeChurnWarnCooldownRemaining,
                warningTriggered
        );
    }

    static ProbeChurnState resetProbeChurn(ProbeChurnState state) {
        state.lastActiveReflectionProbeCount = -1;
        state.reflectionProbeLastDelta = 0;
        state.reflectionProbeActiveChurnEvents = 0;
        state.reflectionProbeActiveDeltaAccum = 0L;
        state.reflectionProbeChurnHighStreak = 0;
        state.reflectionProbeChurnWarnCooldownRemaining = 0;
        return state;
    }

    static SsrTaaRiskUpdateResult updateSsrTaaRisk(SsrTaaRiskState state, double taaReject, double taaConfidence, long taaDrops) {
        state.reflectionSsrTaaLatestRejectRate = taaReject;
        state.reflectionSsrTaaLatestConfidenceMean = taaConfidence;
        state.reflectionSsrTaaLatestDropEvents = taaDrops;
        boolean instantRisk = taaReject > state.reflectionSsrTaaInstabilityRejectMin
                && taaConfidence < state.reflectionSsrTaaInstabilityConfidenceMax
                && taaDrops >= state.reflectionSsrTaaInstabilityDropEventsMin;
        if (state.reflectionSsrTaaEmaReject < 0.0 || state.reflectionSsrTaaEmaConfidence < 0.0) {
            state.reflectionSsrTaaEmaReject = taaReject;
            state.reflectionSsrTaaEmaConfidence = taaConfidence;
        } else {
            double alpha = Math.max(0.01, Math.min(1.0, state.reflectionSsrTaaRiskEmaAlpha));
            state.reflectionSsrTaaEmaReject = (taaReject * alpha) + (state.reflectionSsrTaaEmaReject * (1.0 - alpha));
            state.reflectionSsrTaaEmaConfidence = (taaConfidence * alpha) + (state.reflectionSsrTaaEmaConfidence * (1.0 - alpha));
        }
        boolean warningTriggered = false;
        if (instantRisk) {
            state.reflectionSsrTaaRiskHighStreak++;
            if (state.reflectionSsrTaaRiskHighStreak >= state.reflectionSsrTaaInstabilityWarnMinFrames
                    && state.reflectionSsrTaaRiskWarnCooldownRemaining <= 0) {
                state.reflectionSsrTaaRiskWarnCooldownRemaining = state.reflectionSsrTaaInstabilityWarnCooldownFrames;
                warningTriggered = true;
            }
        } else {
            state.reflectionSsrTaaRiskHighStreak = 0;
        }
        if (state.reflectionSsrTaaRiskWarnCooldownRemaining > 0) {
            state.reflectionSsrTaaRiskWarnCooldownRemaining--;
        }
        return new SsrTaaRiskUpdateResult(
                state,
                new ReflectionSsrTaaRiskDiagnostics(
                        instantRisk,
                        state.reflectionSsrTaaRiskHighStreak,
                        state.reflectionSsrTaaRiskWarnCooldownRemaining,
                        state.reflectionSsrTaaEmaReject,
                        state.reflectionSsrTaaEmaConfidence,
                        warningTriggered
                )
        );
    }

    static SsrTaaRiskState resetSsrTaaRisk(SsrTaaRiskState state) {
        state.reflectionSsrTaaRiskHighStreak = 0;
        state.reflectionSsrTaaRiskWarnCooldownRemaining = 0;
        state.reflectionSsrTaaEmaReject = -1.0;
        state.reflectionSsrTaaEmaConfidence = -1.0;
        state.reflectionSsrTaaLatestRejectRate = 0.0;
        state.reflectionSsrTaaLatestConfidenceMean = 1.0;
        state.reflectionSsrTaaLatestDropEvents = 0L;
        return state;
    }

    private VulkanReflectionAdaptiveDiagnostics() {
    }
}
