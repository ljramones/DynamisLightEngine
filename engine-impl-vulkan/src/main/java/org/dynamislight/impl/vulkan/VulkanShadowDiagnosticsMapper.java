package org.dynamislight.impl.vulkan;

import java.util.List;
import org.dynamislight.api.runtime.ShadowCacheDiagnostics;
import org.dynamislight.api.runtime.ShadowCadenceDiagnostics;
import org.dynamislight.api.runtime.ShadowCapabilityDiagnostics;
import org.dynamislight.api.runtime.ShadowExtendedModeDiagnostics;
import org.dynamislight.api.runtime.ShadowHybridDiagnostics;
import org.dynamislight.api.runtime.ShadowPhaseAPromotionDiagnostics;
import org.dynamislight.api.runtime.ShadowPhaseDPromotionDiagnostics;
import org.dynamislight.api.runtime.ShadowPointBudgetDiagnostics;
import org.dynamislight.api.runtime.ShadowRtDiagnostics;
import org.dynamislight.api.runtime.ShadowSpotProjectedDiagnostics;
import org.dynamislight.api.runtime.ShadowTopologyDiagnostics;
import org.dynamislight.api.runtime.ShadowTransparentReceiverDiagnostics;

final class VulkanShadowDiagnosticsMapper {
    private VulkanShadowDiagnosticsMapper() {
    }

    static ShadowCapabilityDiagnostics capability(String featureId, String mode, List<String> signals) {
        boolean available = !"unavailable".equals(featureId) && !"unavailable".equals(mode);
        return new ShadowCapabilityDiagnostics(available, featureId, mode, signals);
    }

    static ShadowCadenceDiagnostics cadence(
            boolean available,
            int selectedLocalLights,
            int deferredLocalLights,
            int staleBypassCount,
            double deferredRatio,
            double warnDeferredRatioMax,
            int warnMinFrames,
            int warnCooldownFrames,
            int highStreak,
            int warnCooldownRemaining,
            int stableStreak,
            int promotionReadyMinFrames,
            boolean promotionReady,
            boolean envelopeBreached
    ) {
        return new ShadowCadenceDiagnostics(
                available,
                selectedLocalLights,
                deferredLocalLights,
                staleBypassCount,
                deferredRatio,
                warnDeferredRatioMax,
                warnMinFrames,
                warnCooldownFrames,
                highStreak,
                warnCooldownRemaining,
                stableStreak,
                promotionReadyMinFrames,
                promotionReady,
                envelopeBreached
        );
    }

    static ShadowPointBudgetDiagnostics pointBudget(
            boolean available,
            int maxFacesPerFrame,
            int renderedCubemaps,
            int renderedFaces,
            int deferredCount,
            double saturationRatio,
            double warnSaturationMin,
            int warnMinFrames,
            int warnCooldownFrames,
            int highStreak,
            int warnCooldownRemaining,
            int stableStreak,
            int promotionReadyMinFrames,
            boolean promotionReady,
            boolean envelopeBreached
    ) {
        return new ShadowPointBudgetDiagnostics(
                available,
                Math.max(0, maxFacesPerFrame),
                renderedCubemaps,
                renderedFaces,
                deferredCount,
                saturationRatio,
                warnSaturationMin,
                warnMinFrames,
                warnCooldownFrames,
                highStreak,
                warnCooldownRemaining,
                stableStreak,
                promotionReadyMinFrames,
                promotionReady,
                envelopeBreached
        );
    }

    static ShadowSpotProjectedDiagnostics spotProjected(
            boolean available,
            boolean requested,
            boolean active,
            int renderedCount,
            String contractStatus,
            boolean contractBreached,
            int stableStreak,
            int promotionReadyMinFrames,
            boolean promotionReady
    ) {
        return new ShadowSpotProjectedDiagnostics(
                available,
                requested,
                active,
                renderedCount,
                contractStatus,
                contractBreached,
                stableStreak,
                promotionReadyMinFrames,
                promotionReady
        );
    }

    static ShadowCacheDiagnostics cache(
            boolean available,
            String mode,
            int missCount,
            int deferredLocalLights,
            int hitCount,
            int evictionCount,
            double hitRatio,
            double churnRatio,
            String invalidationReason,
            double churnWarnMax,
            int missWarnMax,
            int warnMinFrames,
            int warnCooldownFrames,
            int highStreak,
            int warnCooldownRemaining,
            boolean envelopeBreached
    ) {
        return new ShadowCacheDiagnostics(
                available,
                "cached_static_dynamic".equals(mode),
                missCount > 0 || deferredLocalLights > 0,
                hitCount,
                missCount,
                evictionCount,
                hitRatio,
                churnRatio,
                invalidationReason,
                churnWarnMax,
                missWarnMax,
                warnMinFrames,
                warnCooldownFrames,
                highStreak,
                warnCooldownRemaining,
                envelopeBreached
        );
    }

    static ShadowRtDiagnostics rt(
            boolean available,
            String rtMode,
            boolean rtActive,
            float effectiveDenoiseStrength,
            double denoiseWarnMin,
            int effectiveSampleCount,
            int sampleWarnMin,
            double perfGpuMsEstimate,
            double perfGpuMsWarnMax,
            int warnMinFrames,
            int warnCooldownFrames,
            int highStreak,
            int warnCooldownRemaining,
            boolean envelopeBreached
    ) {
        return new ShadowRtDiagnostics(
                available,
                rtMode,
                rtActive,
                effectiveDenoiseStrength,
                denoiseWarnMin,
                effectiveSampleCount,
                sampleWarnMin,
                perfGpuMsEstimate,
                perfGpuMsWarnMax,
                warnMinFrames,
                warnCooldownFrames,
                highStreak,
                warnCooldownRemaining,
                envelopeBreached
        );
    }

    static ShadowHybridDiagnostics hybrid(
            boolean available,
            String capabilityMode,
            double cascadeShare,
            double contactShare,
            double rtShare,
            double rtShareWarnMin,
            double contactShareWarnMin,
            int warnMinFrames,
            int warnCooldownFrames,
            int highStreak,
            int warnCooldownRemaining,
            boolean envelopeBreached
    ) {
        boolean hybridModeActive = "hybrid_cascade_contact_rt".equals(capabilityMode);
        return new ShadowHybridDiagnostics(
                available,
                hybridModeActive,
                cascadeShare,
                contactShare,
                rtShare,
                rtShareWarnMin,
                contactShareWarnMin,
                warnMinFrames,
                warnCooldownFrames,
                highStreak,
                warnCooldownRemaining,
                envelopeBreached
        );
    }

    static ShadowTransparentReceiverDiagnostics transparentReceivers(
            boolean available,
            boolean requested,
            boolean supported,
            String policy,
            int candidateCount,
            double candidateRatio,
            double candidateRatioWarnMax,
            int warnMinFrames,
            int warnCooldownFrames,
            int highStreak,
            int warnCooldownRemaining,
            boolean envelopeBreached
    ) {
        return new ShadowTransparentReceiverDiagnostics(
                available,
                requested,
                supported,
                policy,
                candidateCount,
                candidateRatio,
                candidateRatioWarnMax,
                warnMinFrames,
                warnCooldownFrames,
                highStreak,
                warnCooldownRemaining,
                envelopeBreached
        );
    }

    static ShadowExtendedModeDiagnostics extendedModes(
            boolean available,
            boolean areaApproxRequested,
            boolean areaApproxSupported,
            boolean areaApproxBreached,
            boolean distanceFieldRequested,
            boolean distanceFieldSupported,
            boolean distanceFieldBreached
    ) {
        return new ShadowExtendedModeDiagnostics(
                available,
                areaApproxRequested,
                areaApproxSupported,
                areaApproxSupported ? "enabled" : "fallback_standard_shadow",
                areaApproxBreached,
                distanceFieldRequested,
                distanceFieldSupported,
                distanceFieldSupported ? "enabled" : "fallback_standard_shadow",
                distanceFieldBreached
        );
    }

    static ShadowTopologyDiagnostics topology(
            boolean available,
            int selectedLocalLights,
            int renderedLocalLights,
            int candidateSpotLights,
            int renderedSpotShadows,
            int candidatePointLights,
            int renderedPointCubemaps,
            double localCoverage,
            double spotCoverage,
            double pointCoverage,
            double localCoverageWarnMin,
            double spotCoverageWarnMin,
            double pointCoverageWarnMin,
            int warnMinFrames,
            int warnCooldownFrames,
            int highStreak,
            int warnCooldownRemaining,
            int stableStreak,
            int promotionReadyMinFrames,
            boolean promotionReady,
            boolean envelopeBreached
    ) {
        return new ShadowTopologyDiagnostics(
                available,
                selectedLocalLights,
                renderedLocalLights,
                candidateSpotLights,
                renderedSpotShadows,
                candidatePointLights,
                renderedPointCubemaps,
                localCoverage,
                spotCoverage,
                pointCoverage,
                localCoverageWarnMin,
                spotCoverageWarnMin,
                pointCoverageWarnMin,
                warnMinFrames,
                warnCooldownFrames,
                highStreak,
                warnCooldownRemaining,
                stableStreak,
                promotionReadyMinFrames,
                promotionReady,
                envelopeBreached
        );
    }

    static ShadowPhaseAPromotionDiagnostics phaseA(
            boolean available,
            boolean cadenceReady,
            boolean pointBudgetReady,
            boolean spotProjectedReady,
            int promotionReadyMinFrames,
            int stableStreak,
            boolean promotionReady
    ) {
        return new ShadowPhaseAPromotionDiagnostics(
                available,
                cadenceReady,
                pointBudgetReady,
                spotProjectedReady,
                promotionReadyMinFrames,
                stableStreak,
                promotionReady
        );
    }

    static ShadowPhaseDPromotionDiagnostics phaseD(
            boolean available,
            boolean cacheEnvelopeBreached,
            int cacheWarnCooldownRemaining,
            boolean rtEnvelopeBreached,
            int rtWarnCooldownRemaining,
            boolean hybridEnvelopeBreached,
            int hybridWarnCooldownRemaining,
            boolean transparentEnvelopeBreached,
            int transparentWarnCooldownRemaining,
            boolean areaApproxBreached,
            boolean distanceFieldBreached,
            int promotionReadyMinFrames,
            int stableStreak,
            boolean promotionReady
    ) {
        boolean cacheStable = !cacheEnvelopeBreached && cacheWarnCooldownRemaining == 0;
        boolean rtStable = !rtEnvelopeBreached && rtWarnCooldownRemaining == 0;
        boolean hybridStable = !hybridEnvelopeBreached && hybridWarnCooldownRemaining == 0;
        boolean transparentStable = !transparentEnvelopeBreached && transparentWarnCooldownRemaining == 0;
        boolean areaStable = !areaApproxBreached;
        boolean distanceStable = !distanceFieldBreached;
        return new ShadowPhaseDPromotionDiagnostics(
                available,
                cacheStable,
                rtStable,
                hybridStable,
                transparentStable,
                areaStable,
                distanceStable,
                promotionReadyMinFrames,
                stableStreak,
                promotionReady
        );
    }
}
