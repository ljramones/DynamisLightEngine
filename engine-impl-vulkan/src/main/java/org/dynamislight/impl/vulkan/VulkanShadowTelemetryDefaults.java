package org.dynamislight.impl.vulkan;

import java.util.Map;

import org.dynamislight.api.config.QualityTier;

final class VulkanShadowTelemetryDefaults {
    private VulkanShadowTelemetryDefaults() {
    }

    static final class State {
        double shadowCadenceWarnDeferredRatioMax;
        int shadowCadenceWarnMinFrames;
        int shadowCadenceWarnCooldownFrames;
        int shadowCadencePromotionReadyMinFrames;
        double shadowPointFaceBudgetWarnSaturationMin;
        int shadowPointFaceBudgetWarnMinFrames;
        int shadowPointFaceBudgetWarnCooldownFrames;
        int shadowPointFaceBudgetPromotionReadyMinFrames;
        double shadowCacheChurnWarnMax;
        int shadowCacheMissWarnMax;
        int shadowCacheWarnMinFrames;
        int shadowCacheWarnCooldownFrames;
        double shadowRtDenoiseWarnMin;
        int shadowRtSampleWarnMin;
        double shadowRtPerfMaxGpuMsLow;
        double shadowRtPerfMaxGpuMsMedium;
        double shadowRtPerfMaxGpuMsHigh;
        double shadowRtPerfMaxGpuMsUltra;
        int shadowRtWarnMinFrames;
        int shadowRtWarnCooldownFrames;
        double shadowHybridRtShareWarnMin;
        double shadowHybridContactShareWarnMin;
        int shadowHybridWarnMinFrames;
        int shadowHybridWarnCooldownFrames;
        double shadowTransparentReceiverCandidateRatioWarnMax;
        int shadowTransparentReceiverWarnMinFrames;
        int shadowTransparentReceiverWarnCooldownFrames;
        int shadowSpotProjectedPromotionReadyMinFrames;
        double shadowTopologyLocalCoverageWarnMin;
        double shadowTopologySpotCoverageWarnMin;
        double shadowTopologyPointCoverageWarnMin;
        int shadowTopologyWarnMinFrames;
        int shadowTopologyWarnCooldownFrames;
        int shadowTopologyPromotionReadyMinFrames;
        int shadowPhaseAPromotionReadyMinFrames;
        int shadowPhaseDPromotionReadyMinFrames;
    }

    static void apply(State state, Map<String, String> backendOptions, QualityTier tier) {
        Map<String, String> safe = backendOptions == null ? Map.of() : backendOptions;
        switch (tier) {
            case LOW -> {
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cadenceWarnDeferredRatioMax")) state.shadowCadenceWarnDeferredRatioMax = 0.75;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cadenceWarnMinFrames")) state.shadowCadenceWarnMinFrames = 4;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cadenceWarnCooldownFrames")) state.shadowCadenceWarnCooldownFrames = 180;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cadencePromotionReadyMinFrames")) state.shadowCadencePromotionReadyMinFrames = 8;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.pointFaceBudgetWarnSaturationMin")) state.shadowPointFaceBudgetWarnSaturationMin = 1.0;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.pointFaceBudgetWarnMinFrames")) state.shadowPointFaceBudgetWarnMinFrames = 4;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.pointFaceBudgetWarnCooldownFrames")) state.shadowPointFaceBudgetWarnCooldownFrames = 180;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.pointFaceBudgetPromotionReadyMinFrames")) state.shadowPointFaceBudgetPromotionReadyMinFrames = 8;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cacheChurnWarnMax")) state.shadowCacheChurnWarnMax = 0.55;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cacheMissWarnMax")) state.shadowCacheMissWarnMax = 3;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cacheWarnMinFrames")) state.shadowCacheWarnMinFrames = 4;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cacheWarnCooldownFrames")) state.shadowCacheWarnCooldownFrames = 180;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtDenoiseWarnMin")) state.shadowRtDenoiseWarnMin = 0.55;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtSampleWarnMin")) state.shadowRtSampleWarnMin = 3;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtPerfMaxGpuMsLow")) state.shadowRtPerfMaxGpuMsLow = 1.0;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtPerfMaxGpuMsMedium")) state.shadowRtPerfMaxGpuMsMedium = 1.6;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtPerfMaxGpuMsHigh")) state.shadowRtPerfMaxGpuMsHigh = 2.2;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtPerfMaxGpuMsUltra")) state.shadowRtPerfMaxGpuMsUltra = 2.8;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtWarnMinFrames")) state.shadowRtWarnMinFrames = 4;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtWarnCooldownFrames")) state.shadowRtWarnCooldownFrames = 180;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.hybridRtShareWarnMin")) state.shadowHybridRtShareWarnMin = 0.30;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.hybridContactShareWarnMin")) state.shadowHybridContactShareWarnMin = 0.18;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.hybridWarnMinFrames")) state.shadowHybridWarnMinFrames = 4;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.hybridWarnCooldownFrames")) state.shadowHybridWarnCooldownFrames = 180;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.transparentReceiverCandidateRatioWarnMax")) state.shadowTransparentReceiverCandidateRatioWarnMax = 0.10;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.transparentReceiverWarnMinFrames")) state.shadowTransparentReceiverWarnMinFrames = 4;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.transparentReceiverWarnCooldownFrames")) state.shadowTransparentReceiverWarnCooldownFrames = 180;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.spotProjectedPromotionReadyMinFrames")) state.shadowSpotProjectedPromotionReadyMinFrames = 8;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.topologyLocalCoverageWarnMin")) state.shadowTopologyLocalCoverageWarnMin = 0.72;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.topologySpotCoverageWarnMin")) state.shadowTopologySpotCoverageWarnMin = 0.70;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.topologyPointCoverageWarnMin")) state.shadowTopologyPointCoverageWarnMin = 0.58;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.topologyWarnMinFrames")) state.shadowTopologyWarnMinFrames = 4;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.topologyWarnCooldownFrames")) state.shadowTopologyWarnCooldownFrames = 180;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.topologyPromotionReadyMinFrames")) state.shadowTopologyPromotionReadyMinFrames = 8;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.phaseAPromotionReadyMinFrames")) state.shadowPhaseAPromotionReadyMinFrames = 6;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.phaseDPromotionReadyMinFrames")) state.shadowPhaseDPromotionReadyMinFrames = 8;
            }
            case MEDIUM -> {
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cadenceWarnDeferredRatioMax")) state.shadowCadenceWarnDeferredRatioMax = 0.55;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cadenceWarnMinFrames")) state.shadowCadenceWarnMinFrames = 3;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cadenceWarnCooldownFrames")) state.shadowCadenceWarnCooldownFrames = 120;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cadencePromotionReadyMinFrames")) state.shadowCadencePromotionReadyMinFrames = 6;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.pointFaceBudgetWarnSaturationMin")) state.shadowPointFaceBudgetWarnSaturationMin = 1.0;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.pointFaceBudgetWarnMinFrames")) state.shadowPointFaceBudgetWarnMinFrames = 3;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.pointFaceBudgetWarnCooldownFrames")) state.shadowPointFaceBudgetWarnCooldownFrames = 120;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.pointFaceBudgetPromotionReadyMinFrames")) state.shadowPointFaceBudgetPromotionReadyMinFrames = 6;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cacheChurnWarnMax")) state.shadowCacheChurnWarnMax = 0.35;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cacheMissWarnMax")) state.shadowCacheMissWarnMax = 2;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cacheWarnMinFrames")) state.shadowCacheWarnMinFrames = 3;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cacheWarnCooldownFrames")) state.shadowCacheWarnCooldownFrames = 120;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtDenoiseWarnMin")) state.shadowRtDenoiseWarnMin = 0.45;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtSampleWarnMin")) state.shadowRtSampleWarnMin = 2;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtPerfMaxGpuMsLow")) state.shadowRtPerfMaxGpuMsLow = 1.2;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtPerfMaxGpuMsMedium")) state.shadowRtPerfMaxGpuMsMedium = 2.0;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtPerfMaxGpuMsHigh")) state.shadowRtPerfMaxGpuMsHigh = 2.8;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtPerfMaxGpuMsUltra")) state.shadowRtPerfMaxGpuMsUltra = 3.6;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtWarnMinFrames")) state.shadowRtWarnMinFrames = 3;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtWarnCooldownFrames")) state.shadowRtWarnCooldownFrames = 120;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.hybridRtShareWarnMin")) state.shadowHybridRtShareWarnMin = 0.20;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.hybridContactShareWarnMin")) state.shadowHybridContactShareWarnMin = 0.10;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.hybridWarnMinFrames")) state.shadowHybridWarnMinFrames = 3;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.hybridWarnCooldownFrames")) state.shadowHybridWarnCooldownFrames = 120;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.transparentReceiverCandidateRatioWarnMax")) state.shadowTransparentReceiverCandidateRatioWarnMax = 0.20;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.transparentReceiverWarnMinFrames")) state.shadowTransparentReceiverWarnMinFrames = 3;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.transparentReceiverWarnCooldownFrames")) state.shadowTransparentReceiverWarnCooldownFrames = 120;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.spotProjectedPromotionReadyMinFrames")) state.shadowSpotProjectedPromotionReadyMinFrames = 6;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.topologyLocalCoverageWarnMin")) state.shadowTopologyLocalCoverageWarnMin = 0.60;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.topologySpotCoverageWarnMin")) state.shadowTopologySpotCoverageWarnMin = 0.60;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.topologyPointCoverageWarnMin")) state.shadowTopologyPointCoverageWarnMin = 0.50;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.topologyWarnMinFrames")) state.shadowTopologyWarnMinFrames = 3;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.topologyWarnCooldownFrames")) state.shadowTopologyWarnCooldownFrames = 120;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.topologyPromotionReadyMinFrames")) state.shadowTopologyPromotionReadyMinFrames = 6;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.phaseAPromotionReadyMinFrames")) state.shadowPhaseAPromotionReadyMinFrames = 4;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.phaseDPromotionReadyMinFrames")) state.shadowPhaseDPromotionReadyMinFrames = 6;
            }
            case HIGH -> {
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cadenceWarnDeferredRatioMax")) state.shadowCadenceWarnDeferredRatioMax = 0.45;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cadenceWarnMinFrames")) state.shadowCadenceWarnMinFrames = 2;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cadenceWarnCooldownFrames")) state.shadowCadenceWarnCooldownFrames = 90;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cadencePromotionReadyMinFrames")) state.shadowCadencePromotionReadyMinFrames = 5;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.pointFaceBudgetWarnSaturationMin")) state.shadowPointFaceBudgetWarnSaturationMin = 0.95;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.pointFaceBudgetWarnMinFrames")) state.shadowPointFaceBudgetWarnMinFrames = 2;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.pointFaceBudgetWarnCooldownFrames")) state.shadowPointFaceBudgetWarnCooldownFrames = 90;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.pointFaceBudgetPromotionReadyMinFrames")) state.shadowPointFaceBudgetPromotionReadyMinFrames = 5;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cacheChurnWarnMax")) state.shadowCacheChurnWarnMax = 0.28;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cacheMissWarnMax")) state.shadowCacheMissWarnMax = 1;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cacheWarnMinFrames")) state.shadowCacheWarnMinFrames = 2;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cacheWarnCooldownFrames")) state.shadowCacheWarnCooldownFrames = 90;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtDenoiseWarnMin")) state.shadowRtDenoiseWarnMin = 0.38;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtSampleWarnMin")) state.shadowRtSampleWarnMin = 2;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtPerfMaxGpuMsLow")) state.shadowRtPerfMaxGpuMsLow = 1.4;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtPerfMaxGpuMsMedium")) state.shadowRtPerfMaxGpuMsMedium = 2.2;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtPerfMaxGpuMsHigh")) state.shadowRtPerfMaxGpuMsHigh = 3.1;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtPerfMaxGpuMsUltra")) state.shadowRtPerfMaxGpuMsUltra = 4.0;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtWarnMinFrames")) state.shadowRtWarnMinFrames = 2;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtWarnCooldownFrames")) state.shadowRtWarnCooldownFrames = 90;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.hybridRtShareWarnMin")) state.shadowHybridRtShareWarnMin = 0.16;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.hybridContactShareWarnMin")) state.shadowHybridContactShareWarnMin = 0.08;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.hybridWarnMinFrames")) state.shadowHybridWarnMinFrames = 2;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.hybridWarnCooldownFrames")) state.shadowHybridWarnCooldownFrames = 90;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.transparentReceiverCandidateRatioWarnMax")) state.shadowTransparentReceiverCandidateRatioWarnMax = 0.28;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.transparentReceiverWarnMinFrames")) state.shadowTransparentReceiverWarnMinFrames = 2;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.transparentReceiverWarnCooldownFrames")) state.shadowTransparentReceiverWarnCooldownFrames = 90;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.spotProjectedPromotionReadyMinFrames")) state.shadowSpotProjectedPromotionReadyMinFrames = 5;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.topologyLocalCoverageWarnMin")) state.shadowTopologyLocalCoverageWarnMin = 0.52;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.topologySpotCoverageWarnMin")) state.shadowTopologySpotCoverageWarnMin = 0.50;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.topologyPointCoverageWarnMin")) state.shadowTopologyPointCoverageWarnMin = 0.42;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.topologyWarnMinFrames")) state.shadowTopologyWarnMinFrames = 2;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.topologyWarnCooldownFrames")) state.shadowTopologyWarnCooldownFrames = 90;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.topologyPromotionReadyMinFrames")) state.shadowTopologyPromotionReadyMinFrames = 5;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.phaseAPromotionReadyMinFrames")) state.shadowPhaseAPromotionReadyMinFrames = 3;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.phaseDPromotionReadyMinFrames")) state.shadowPhaseDPromotionReadyMinFrames = 5;
            }
            case ULTRA -> {
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cadenceWarnDeferredRatioMax")) state.shadowCadenceWarnDeferredRatioMax = 0.35;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cadenceWarnMinFrames")) state.shadowCadenceWarnMinFrames = 2;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cadenceWarnCooldownFrames")) state.shadowCadenceWarnCooldownFrames = 60;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cadencePromotionReadyMinFrames")) state.shadowCadencePromotionReadyMinFrames = 4;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.pointFaceBudgetWarnSaturationMin")) state.shadowPointFaceBudgetWarnSaturationMin = 0.90;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.pointFaceBudgetWarnMinFrames")) state.shadowPointFaceBudgetWarnMinFrames = 2;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.pointFaceBudgetWarnCooldownFrames")) state.shadowPointFaceBudgetWarnCooldownFrames = 60;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.pointFaceBudgetPromotionReadyMinFrames")) state.shadowPointFaceBudgetPromotionReadyMinFrames = 4;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cacheChurnWarnMax")) state.shadowCacheChurnWarnMax = 0.22;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cacheMissWarnMax")) state.shadowCacheMissWarnMax = 1;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cacheWarnMinFrames")) state.shadowCacheWarnMinFrames = 2;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.cacheWarnCooldownFrames")) state.shadowCacheWarnCooldownFrames = 60;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtDenoiseWarnMin")) state.shadowRtDenoiseWarnMin = 0.34;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtSampleWarnMin")) state.shadowRtSampleWarnMin = 2;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtPerfMaxGpuMsLow")) state.shadowRtPerfMaxGpuMsLow = 1.8;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtPerfMaxGpuMsMedium")) state.shadowRtPerfMaxGpuMsMedium = 2.6;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtPerfMaxGpuMsHigh")) state.shadowRtPerfMaxGpuMsHigh = 3.6;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtPerfMaxGpuMsUltra")) state.shadowRtPerfMaxGpuMsUltra = 4.8;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtWarnMinFrames")) state.shadowRtWarnMinFrames = 2;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.rtWarnCooldownFrames")) state.shadowRtWarnCooldownFrames = 60;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.hybridRtShareWarnMin")) state.shadowHybridRtShareWarnMin = 0.12;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.hybridContactShareWarnMin")) state.shadowHybridContactShareWarnMin = 0.06;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.hybridWarnMinFrames")) state.shadowHybridWarnMinFrames = 2;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.hybridWarnCooldownFrames")) state.shadowHybridWarnCooldownFrames = 60;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.transparentReceiverCandidateRatioWarnMax")) state.shadowTransparentReceiverCandidateRatioWarnMax = 0.35;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.transparentReceiverWarnMinFrames")) state.shadowTransparentReceiverWarnMinFrames = 2;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.transparentReceiverWarnCooldownFrames")) state.shadowTransparentReceiverWarnCooldownFrames = 60;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.spotProjectedPromotionReadyMinFrames")) state.shadowSpotProjectedPromotionReadyMinFrames = 4;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.topologyLocalCoverageWarnMin")) state.shadowTopologyLocalCoverageWarnMin = 0.45;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.topologySpotCoverageWarnMin")) state.shadowTopologySpotCoverageWarnMin = 0.42;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.topologyPointCoverageWarnMin")) state.shadowTopologyPointCoverageWarnMin = 0.35;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.topologyWarnMinFrames")) state.shadowTopologyWarnMinFrames = 2;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.topologyWarnCooldownFrames")) state.shadowTopologyWarnCooldownFrames = 60;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.topologyPromotionReadyMinFrames")) state.shadowTopologyPromotionReadyMinFrames = 4;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.phaseAPromotionReadyMinFrames")) state.shadowPhaseAPromotionReadyMinFrames = 2;
                if (!VulkanRuntimeOptionParsing.hasBackendOption(safe, "vulkan.shadow.phaseDPromotionReadyMinFrames")) state.shadowPhaseDPromotionReadyMinFrames = 4;
            }
        }
    }
}
