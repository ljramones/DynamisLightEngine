package org.dynamislight.impl.vulkan.reflection;

import org.dynamislight.impl.vulkan.runtime.config.*;

import org.dynamislight.api.event.EngineWarning;

public final class VulkanReflectionTelemetryProfileWarning {
    private VulkanReflectionTelemetryProfileWarning() {
    }

    public static final class State {
        ReflectionProfile reflectionProfile;
        int reflectionProbeChurnWarnMinDelta;
        int reflectionProbeChurnWarnMinStreak;
        int reflectionProbeChurnWarnCooldownFrames;
        int reflectionProbeQualityOverlapWarnMaxPairs;
        int reflectionProbeQualityBleedRiskWarnMaxPairs;
        int reflectionProbeQualityMinOverlapPairsWhenMultiple;
        double reflectionOverrideProbeOnlyRatioWarnMax;
        double reflectionOverrideSsrOnlyRatioWarnMax;
        int reflectionOverrideOtherWarnMax;
        int reflectionOverrideWarnMinFrames;
        int reflectionOverrideWarnCooldownFrames;
        double reflectionContactHardeningMinSsrStrength;
        double reflectionContactHardeningMinSsrMaxRoughness;
        int reflectionContactHardeningWarnMinFrames;
        int reflectionContactHardeningWarnCooldownFrames;
        double reflectionProbeQualityBoxProjectionMinRatio;
        int reflectionProbeQualityInvalidBlendDistanceWarnMax;
        double reflectionProbeQualityOverlapCoverageWarnMin;
        double reflectionSsrTaaInstabilityRejectMin;
        double reflectionSsrTaaInstabilityConfidenceMax;
        long reflectionSsrTaaInstabilityDropEventsMin;
        int reflectionSsrTaaInstabilityWarnMinFrames;
        int reflectionSsrTaaInstabilityWarnCooldownFrames;
        double reflectionSsrTaaRiskEmaAlpha;
        boolean reflectionSsrTaaAdaptiveEnabled;
        double reflectionSsrTaaAdaptiveTemporalBoostMax;
        double reflectionSsrTaaAdaptiveSsrStrengthScaleMin;
        double reflectionSsrTaaAdaptiveStepScaleBoostMax;
        int reflectionSsrTaaAdaptiveTrendWindowFrames;
        double reflectionSsrTaaAdaptiveTrendHighRatioWarnMin;
        int reflectionSsrTaaAdaptiveTrendWarnMinFrames;
        int reflectionSsrTaaAdaptiveTrendWarnCooldownFrames;
        int reflectionSsrTaaAdaptiveTrendWarnMinSamples;
        double reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax;
        double reflectionSsrTaaAdaptiveTrendSloHighRatioMax;
        int reflectionSsrTaaAdaptiveTrendSloMinSamples;
        long reflectionSsrTaaDisocclusionRejectDropEventsMin;
        double reflectionSsrTaaDisocclusionRejectConfidenceMax;
        double reflectionSsrEnvelopeRejectWarnMax;
        double reflectionSsrEnvelopeConfidenceWarnMin;
        long reflectionSsrEnvelopeDropWarnMin;
        int reflectionSsrEnvelopeWarnMinFrames;
        int reflectionSsrEnvelopeWarnCooldownFrames;
        double reflectionPlanarEnvelopePlaneDeltaWarnMax;
        double reflectionPlanarEnvelopeCoverageRatioWarnMin;
        int reflectionPlanarEnvelopeWarnMinFrames;
        int reflectionPlanarEnvelopeWarnCooldownFrames;
        double reflectionPlanarPerfMaxGpuMsLow;
        double reflectionPlanarPerfMaxGpuMsMedium;
        double reflectionPlanarPerfMaxGpuMsHigh;
        double reflectionPlanarPerfMaxGpuMsUltra;
        double reflectionPlanarPerfDrawInflationWarnMax;
        double reflectionPlanarPerfMemoryBudgetMb;
        int reflectionPlanarPerfWarnMinFrames;
        int reflectionPlanarPerfWarnCooldownFrames;
        boolean reflectionPlanarPerfRequireGpuTimestamp;
        boolean reflectionPlanarScopeIncludeAuto;
        boolean reflectionPlanarScopeIncludeProbeOnly;
        boolean reflectionPlanarScopeIncludeSsrOnly;
        boolean reflectionPlanarScopeIncludeOther;
        boolean reflectionRtSingleBounceEnabled;
        boolean reflectionRtMultiBounceEnabled;
        boolean reflectionRtRequireActive;
        boolean reflectionRtRequireMultiBounce;
        boolean reflectionRtRequireDedicatedPipeline;
        boolean reflectionRtDedicatedPipelineEnabled;
        boolean reflectionRtDedicatedCapabilitySupported;
        boolean reflectionRtDedicatedDenoisePipelineEnabled;
        double reflectionRtDenoiseStrength;
        double reflectionRtPerfMaxGpuMsLow;
        double reflectionRtPerfMaxGpuMsMedium;
        double reflectionRtPerfMaxGpuMsHigh;
        double reflectionRtPerfMaxGpuMsUltra;
        int reflectionRtPerfWarnMinFrames;
        int reflectionRtPerfWarnCooldownFrames;
        double reflectionRtHybridProbeShareWarnMax;
        int reflectionRtHybridWarnMinFrames;
        int reflectionRtHybridWarnCooldownFrames;
        double reflectionRtDenoiseSpatialVarianceWarnMax;
        double reflectionRtDenoiseTemporalLagWarnMax;
        int reflectionRtDenoiseWarnMinFrames;
        int reflectionRtDenoiseWarnCooldownFrames;
        double reflectionRtAsBuildGpuMsWarnMax;
        double reflectionRtAsMemoryBudgetMb;
        int reflectionRtPromotionReadyMinFrames;
        double reflectionTransparencyCandidateReactiveMin;
        double reflectionTransparencyProbeOnlyRatioWarnMax;
        int reflectionTransparencyWarnMinFrames;
        int reflectionTransparencyWarnCooldownFrames;
        int reflectionProbeUpdateCadenceFrames;
        int reflectionProbeMaxVisible;
        double reflectionProbeLodDepthScale;
        int reflectionProbeStreamingWarnMinFrames;
        int reflectionProbeStreamingWarnCooldownFrames;
        double reflectionProbeStreamingMissRatioWarnMax;
        double reflectionProbeStreamingDeferredRatioWarnMax;
        double reflectionProbeStreamingLodSkewWarnMax;
        double reflectionProbeStreamingMemoryBudgetMb;

        public void setReflectionProfile(ReflectionProfile reflectionProfile) {
            this.reflectionProfile = reflectionProfile;
        }
    }

    public static EngineWarning warning(State s) {
        return new EngineWarning(
                "REFLECTION_TELEMETRY_PROFILE_ACTIVE",
                "Reflection telemetry profile active (profile=" + s.reflectionProfile.name().toLowerCase()
                        + ", probeWarnMinDelta=" + s.reflectionProbeChurnWarnMinDelta
                        + ", probeWarnMinStreak=" + s.reflectionProbeChurnWarnMinStreak
                        + ", probeWarnCooldownFrames=" + s.reflectionProbeChurnWarnCooldownFrames
                        + ", probeOverlapWarnMaxPairs=" + s.reflectionProbeQualityOverlapWarnMaxPairs
                        + ", probeBleedRiskWarnMaxPairs=" + s.reflectionProbeQualityBleedRiskWarnMaxPairs
                        + ", probeMinOverlapPairsWhenMultiple=" + s.reflectionProbeQualityMinOverlapPairsWhenMultiple
                        + ", overrideProbeOnlyRatioWarnMax=" + s.reflectionOverrideProbeOnlyRatioWarnMax
                        + ", overrideSsrOnlyRatioWarnMax=" + s.reflectionOverrideSsrOnlyRatioWarnMax
                        + ", overrideOtherWarnMax=" + s.reflectionOverrideOtherWarnMax
                        + ", overrideWarnMinFrames=" + s.reflectionOverrideWarnMinFrames
                        + ", overrideWarnCooldownFrames=" + s.reflectionOverrideWarnCooldownFrames
                        + ", contactHardeningMinSsrStrength=" + s.reflectionContactHardeningMinSsrStrength
                        + ", contactHardeningMinSsrMaxRoughness=" + s.reflectionContactHardeningMinSsrMaxRoughness
                        + ", contactHardeningWarnMinFrames=" + s.reflectionContactHardeningWarnMinFrames
                        + ", contactHardeningWarnCooldownFrames=" + s.reflectionContactHardeningWarnCooldownFrames
                        + ", probeBoxProjectionMinRatio=" + s.reflectionProbeQualityBoxProjectionMinRatio
                        + ", probeInvalidBlendDistanceWarnMax=" + s.reflectionProbeQualityInvalidBlendDistanceWarnMax
                        + ", probeOverlapCoverageWarnMin=" + s.reflectionProbeQualityOverlapCoverageWarnMin
                        + ", ssrTaaRejectMin=" + s.reflectionSsrTaaInstabilityRejectMin
                        + ", ssrTaaConfidenceMax=" + s.reflectionSsrTaaInstabilityConfidenceMax
                        + ", ssrTaaDropEventsMin=" + s.reflectionSsrTaaInstabilityDropEventsMin
                        + ", ssrTaaWarnMinFrames=" + s.reflectionSsrTaaInstabilityWarnMinFrames
                        + ", ssrTaaWarnCooldownFrames=" + s.reflectionSsrTaaInstabilityWarnCooldownFrames
                        + ", ssrTaaRiskEmaAlpha=" + s.reflectionSsrTaaRiskEmaAlpha
                        + ", ssrTaaAdaptiveEnabled=" + s.reflectionSsrTaaAdaptiveEnabled
                        + ", ssrTaaAdaptiveTemporalBoostMax=" + s.reflectionSsrTaaAdaptiveTemporalBoostMax
                        + ", ssrTaaAdaptiveSsrStrengthScaleMin=" + s.reflectionSsrTaaAdaptiveSsrStrengthScaleMin
                        + ", ssrTaaAdaptiveStepScaleBoostMax=" + s.reflectionSsrTaaAdaptiveStepScaleBoostMax
                        + ", ssrTaaAdaptiveTrendWindowFrames=" + s.reflectionSsrTaaAdaptiveTrendWindowFrames
                        + ", ssrTaaAdaptiveTrendHighRatioWarnMin=" + s.reflectionSsrTaaAdaptiveTrendHighRatioWarnMin
                        + ", ssrTaaAdaptiveTrendWarnMinFrames=" + s.reflectionSsrTaaAdaptiveTrendWarnMinFrames
                        + ", ssrTaaAdaptiveTrendWarnCooldownFrames=" + s.reflectionSsrTaaAdaptiveTrendWarnCooldownFrames
                        + ", ssrTaaAdaptiveTrendWarnMinSamples=" + s.reflectionSsrTaaAdaptiveTrendWarnMinSamples
                        + ", ssrTaaAdaptiveTrendSloMeanSeverityMax=" + s.reflectionSsrTaaAdaptiveTrendSloMeanSeverityMax
                        + ", ssrTaaAdaptiveTrendSloHighRatioMax=" + s.reflectionSsrTaaAdaptiveTrendSloHighRatioMax
                        + ", ssrTaaAdaptiveTrendSloMinSamples=" + s.reflectionSsrTaaAdaptiveTrendSloMinSamples
                        + ", ssrTaaDisocclusionRejectDropEventsMin=" + s.reflectionSsrTaaDisocclusionRejectDropEventsMin
                        + ", ssrTaaDisocclusionRejectConfidenceMax=" + s.reflectionSsrTaaDisocclusionRejectConfidenceMax
                        + ", ssrEnvelopeRejectWarnMax=" + s.reflectionSsrEnvelopeRejectWarnMax
                        + ", ssrEnvelopeConfidenceWarnMin=" + s.reflectionSsrEnvelopeConfidenceWarnMin
                        + ", ssrEnvelopeDropWarnMin=" + s.reflectionSsrEnvelopeDropWarnMin
                        + ", ssrEnvelopeWarnMinFrames=" + s.reflectionSsrEnvelopeWarnMinFrames
                        + ", ssrEnvelopeWarnCooldownFrames=" + s.reflectionSsrEnvelopeWarnCooldownFrames
                        + ", planarEnvelopePlaneDeltaWarnMax=" + s.reflectionPlanarEnvelopePlaneDeltaWarnMax
                        + ", planarEnvelopeCoverageRatioWarnMin=" + s.reflectionPlanarEnvelopeCoverageRatioWarnMin
                        + ", planarEnvelopeWarnMinFrames=" + s.reflectionPlanarEnvelopeWarnMinFrames
                        + ", planarEnvelopeWarnCooldownFrames=" + s.reflectionPlanarEnvelopeWarnCooldownFrames
                        + ", planarPerfMaxGpuMsLow=" + s.reflectionPlanarPerfMaxGpuMsLow
                        + ", planarPerfMaxGpuMsMedium=" + s.reflectionPlanarPerfMaxGpuMsMedium
                        + ", planarPerfMaxGpuMsHigh=" + s.reflectionPlanarPerfMaxGpuMsHigh
                        + ", planarPerfMaxGpuMsUltra=" + s.reflectionPlanarPerfMaxGpuMsUltra
                        + ", planarPerfDrawInflationWarnMax=" + s.reflectionPlanarPerfDrawInflationWarnMax
                        + ", planarPerfMemoryBudgetMb=" + s.reflectionPlanarPerfMemoryBudgetMb
                        + ", planarPerfWarnMinFrames=" + s.reflectionPlanarPerfWarnMinFrames
                        + ", planarPerfWarnCooldownFrames=" + s.reflectionPlanarPerfWarnCooldownFrames
                        + ", planarPerfRequireGpuTimestamp=" + s.reflectionPlanarPerfRequireGpuTimestamp
                        + ", planarScopeIncludeAuto=" + s.reflectionPlanarScopeIncludeAuto
                        + ", planarScopeIncludeProbeOnly=" + s.reflectionPlanarScopeIncludeProbeOnly
                        + ", planarScopeIncludeSsrOnly=" + s.reflectionPlanarScopeIncludeSsrOnly
                        + ", planarScopeIncludeOther=" + s.reflectionPlanarScopeIncludeOther
                        + ", rtSingleBounceEnabled=" + s.reflectionRtSingleBounceEnabled
                        + ", rtMultiBounceEnabled=" + s.reflectionRtMultiBounceEnabled
                        + ", rtRequireActive=" + s.reflectionRtRequireActive
                        + ", rtRequireMultiBounce=" + s.reflectionRtRequireMultiBounce
                        + ", rtRequireDedicatedPipeline=" + s.reflectionRtRequireDedicatedPipeline
                        + ", rtDedicatedPipelineEnabled=" + s.reflectionRtDedicatedPipelineEnabled
                        + ", rtDedicatedCapabilitySupported=" + s.reflectionRtDedicatedCapabilitySupported
                        + ", rtDedicatedDenoisePipelineEnabled=" + s.reflectionRtDedicatedDenoisePipelineEnabled
                        + ", rtDenoiseStrength=" + s.reflectionRtDenoiseStrength
                        + ", rtPerfMaxGpuMsLow=" + s.reflectionRtPerfMaxGpuMsLow
                        + ", rtPerfMaxGpuMsMedium=" + s.reflectionRtPerfMaxGpuMsMedium
                        + ", rtPerfMaxGpuMsHigh=" + s.reflectionRtPerfMaxGpuMsHigh
                        + ", rtPerfMaxGpuMsUltra=" + s.reflectionRtPerfMaxGpuMsUltra
                        + ", rtPerfWarnMinFrames=" + s.reflectionRtPerfWarnMinFrames
                        + ", rtPerfWarnCooldownFrames=" + s.reflectionRtPerfWarnCooldownFrames
                        + ", rtHybridProbeShareWarnMax=" + s.reflectionRtHybridProbeShareWarnMax
                        + ", rtHybridWarnMinFrames=" + s.reflectionRtHybridWarnMinFrames
                        + ", rtHybridWarnCooldownFrames=" + s.reflectionRtHybridWarnCooldownFrames
                        + ", rtDenoiseSpatialVarianceWarnMax=" + s.reflectionRtDenoiseSpatialVarianceWarnMax
                        + ", rtDenoiseTemporalLagWarnMax=" + s.reflectionRtDenoiseTemporalLagWarnMax
                        + ", rtDenoiseWarnMinFrames=" + s.reflectionRtDenoiseWarnMinFrames
                        + ", rtDenoiseWarnCooldownFrames=" + s.reflectionRtDenoiseWarnCooldownFrames
                        + ", rtAsBuildGpuMsWarnMax=" + s.reflectionRtAsBuildGpuMsWarnMax
                        + ", rtAsMemoryBudgetMb=" + s.reflectionRtAsMemoryBudgetMb
                        + ", rtPromotionReadyMinFrames=" + s.reflectionRtPromotionReadyMinFrames
                        + ", transparencyCandidateReactiveMin=" + s.reflectionTransparencyCandidateReactiveMin
                        + ", transparencyProbeOnlyRatioWarnMax=" + s.reflectionTransparencyProbeOnlyRatioWarnMax
                        + ", transparencyWarnMinFrames=" + s.reflectionTransparencyWarnMinFrames
                        + ", transparencyWarnCooldownFrames=" + s.reflectionTransparencyWarnCooldownFrames
                        + ", probeUpdateCadenceFrames=" + s.reflectionProbeUpdateCadenceFrames
                        + ", probeMaxVisible=" + s.reflectionProbeMaxVisible
                        + ", probeLodDepthScale=" + s.reflectionProbeLodDepthScale
                        + ", probeStreamingWarnMinFrames=" + s.reflectionProbeStreamingWarnMinFrames
                        + ", probeStreamingWarnCooldownFrames=" + s.reflectionProbeStreamingWarnCooldownFrames
                        + ", probeStreamingMissRatioWarnMax=" + s.reflectionProbeStreamingMissRatioWarnMax
                        + ", probeStreamingDeferredRatioWarnMax=" + s.reflectionProbeStreamingDeferredRatioWarnMax
                        + ", probeStreamingLodSkewWarnMax=" + s.reflectionProbeStreamingLodSkewWarnMax
                        + ", probeStreamingMemoryBudgetMb=" + s.reflectionProbeStreamingMemoryBudgetMb
                        + ")"
        );
    }
}
