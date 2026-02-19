package org.dynamislight.impl.vulkan.shadow;

import org.dynamislight.impl.vulkan.runtime.model.*;

import org.dynamislight.impl.vulkan.shadow.VulkanShadowRuntimeTuning;

import org.dynamislight.impl.vulkan.runtime.model.*;

import org.dynamislight.api.event.EngineWarning;

public final class VulkanShadowPolicyWarningEmitter {
    public static final class State {
        public ShadowRenderConfig currentShadows;
        public String shadowDepthFormatTag;
        public int shadowMaxShadowedLocalLights;
        public int shadowMaxLocalLayers;
        public int shadowMaxFacesPerFrame;
        public boolean shadowSchedulerEnabled;
        public int shadowSchedulerHeroPeriod;
        public int shadowSchedulerMidPeriod;
        public int shadowSchedulerDistantPeriod;
        public boolean shadowDirectionalTexelSnapEnabled;
        public float shadowDirectionalTexelSnapScale;
        public long shadowSchedulerFrameTick;
        public int shadowAllocatorAssignedLights;
        public int shadowAllocatorReusedAssignments;
        public int shadowAllocatorEvictions;
        public float shadowPcssSoftness;
        public float shadowMomentBlend;
        public float shadowMomentBleedReduction;
        public float shadowContactStrength;
        public float shadowContactTemporalMotionScale;
        public float shadowContactTemporalMinStability;
        public boolean shadowMomentResourcesAllocated;
        public String shadowMomentFormatTag;
        public boolean shadowMomentInitialized;
        public String momentPhase;
        public boolean shadowRtTraversalSupported;
        public boolean shadowRtBvhSupported;
        public boolean shadowRtBvhStrict;
        public float shadowRtDenoiseStrength;
        public float shadowRtRayLength;
        public int shadowRtSampleCount;
        public float shadowRtDedicatedDenoiseStrength;
        public float shadowRtDedicatedRayLength;
        public int shadowRtDedicatedSampleCount;
        public float shadowRtProductionDenoiseStrength;
        public float shadowRtProductionRayLength;
        public int shadowRtProductionSampleCount;
    }

    public static EngineWarning warning(State state) {
        return new EngineWarning(
                "SHADOW_POLICY_ACTIVE",
                "Shadow policy active: primary=" + state.currentShadows.primaryShadowLightId()
                        + " type=" + state.currentShadows.primaryShadowType()
                        + " localBudget=" + state.currentShadows.maxShadowedLocalLights()
                        + " localSelected=" + state.currentShadows.selectedLocalShadowLights()
                        + " atlasTiles=" + state.currentShadows.atlasAllocatedTiles() + "/" + state.currentShadows.atlasCapacityTiles()
                        + " atlasUtilization=" + state.currentShadows.atlasUtilization()
                        + " atlasEvictions=" + state.currentShadows.atlasEvictions()
                        + " atlasMemoryD16Bytes=" + state.currentShadows.atlasMemoryBytesD16()
                        + " atlasMemoryD32Bytes=" + state.currentShadows.atlasMemoryBytesD32()
                        + " shadowUpdateBytesEstimate=" + state.currentShadows.shadowUpdateBytesEstimate()
                        + " shadowMomentAtlasBytesEstimate=" + state.currentShadows.shadowMomentAtlasBytesEstimate()
                        + " shadowDepthFormat=" + state.shadowDepthFormatTag
                        + " cadencePolicy=hero:1 mid:2 distant:4"
                        + " renderedLocalShadows=" + state.currentShadows.renderedLocalShadowLights()
                        + " renderedSpotShadows=" + state.currentShadows.renderedSpotShadowLights()
                        + " renderedPointShadowCubemaps=" + state.currentShadows.renderedPointShadowCubemaps()
                        + " maxShadowedLocalLightsConfigured=" + (state.shadowMaxShadowedLocalLights > 0 ? Integer.toString(state.shadowMaxShadowedLocalLights) : "auto")
                        + " maxLocalShadowLayersConfigured=" + (state.shadowMaxLocalLayers > 0 ? Integer.toString(state.shadowMaxLocalLayers) : "auto")
                        + " maxShadowFacesPerFrameConfigured=" + (state.shadowMaxFacesPerFrame > 0 ? Integer.toString(state.shadowMaxFacesPerFrame) : "auto")
                        + " schedulerEnabled=" + state.shadowSchedulerEnabled
                        + " schedulerPeriodHero=" + state.shadowSchedulerHeroPeriod
                        + " schedulerPeriodMid=" + state.shadowSchedulerMidPeriod
                        + " schedulerPeriodDistant=" + state.shadowSchedulerDistantPeriod
                        + " directionalTexelSnapEnabled=" + state.shadowDirectionalTexelSnapEnabled
                        + " directionalTexelSnapScale=" + state.shadowDirectionalTexelSnapScale
                        + " shadowSchedulerFrameTick=" + state.shadowSchedulerFrameTick
                        + " renderedShadowLightIds=" + state.currentShadows.renderedShadowLightIdsCsv()
                        + " deferredShadowLightCount=" + state.currentShadows.deferredShadowLightCount()
                        + " deferredShadowLightIds=" + state.currentShadows.deferredShadowLightIdsCsv()
                        + " staleBypassShadowLightCount=" + state.currentShadows.staleBypassShadowLightCount()
                        + " shadowAllocatorAssignedLights=" + state.shadowAllocatorAssignedLights
                        + " shadowAllocatorReusedAssignments=" + state.shadowAllocatorReusedAssignments
                        + " shadowAllocatorEvictions=" + state.shadowAllocatorEvictions
                        + " filterPath=" + state.currentShadows.filterPath()
                        + " runtimeFilterPath=" + state.currentShadows.runtimeFilterPath()
                        + " shadowPcssSoftness=" + state.shadowPcssSoftness
                        + " shadowMomentBlend=" + state.shadowMomentBlend
                        + " shadowMomentBleedReduction=" + state.shadowMomentBleedReduction
                        + " shadowContactStrength=" + state.shadowContactStrength
                        + " shadowContactTemporalMotionScale=" + state.shadowContactTemporalMotionScale
                        + " shadowContactTemporalMinStability=" + state.shadowContactTemporalMinStability
                        + " momentFilterEstimateOnly=" + state.currentShadows.momentFilterEstimateOnly()
                        + " momentPipelineRequested=" + state.currentShadows.momentPipelineRequested()
                        + " momentPipelineActive=" + state.currentShadows.momentPipelineActive()
                        + " momentResourceAllocated=" + state.shadowMomentResourcesAllocated
                        + " momentResourceFormat=" + state.shadowMomentFormatTag
                        + " momentInitialized=" + state.shadowMomentInitialized
                        + " momentPhase=" + state.momentPhase
                        + " contactShadows=" + state.currentShadows.contactShadowsRequested()
                        + " rtMode=" + state.currentShadows.rtShadowMode()
                        + " rtActive=" + state.currentShadows.rtShadowActive()
                        + " rtTraversalSupported=" + state.shadowRtTraversalSupported
                        + " rtBvhSupported=" + state.shadowRtBvhSupported
                        + " rtBvhStrict=" + state.shadowRtBvhStrict
                        + " rtDenoiseStrength=" + state.shadowRtDenoiseStrength
                        + " rtRayLength=" + state.shadowRtRayLength
                        + " rtSampleCount=" + state.shadowRtSampleCount
                        + " rtDedicatedDenoiseStrength=" + state.shadowRtDedicatedDenoiseStrength
                        + " rtDedicatedRayLength=" + state.shadowRtDedicatedRayLength
                        + " rtDedicatedSampleCount=" + state.shadowRtDedicatedSampleCount
                        + " rtProductionDenoiseStrength=" + state.shadowRtProductionDenoiseStrength
                        + " rtProductionRayLength=" + state.shadowRtProductionRayLength
                        + " rtProductionSampleCount=" + state.shadowRtProductionSampleCount
                        + " rtEffectiveDenoiseStrength=" + VulkanShadowRuntimeTuning.effectiveShadowRtDenoiseStrength(state.currentShadows.rtShadowMode(), state.shadowRtDenoiseStrength, state.shadowRtProductionDenoiseStrength, state.shadowRtDedicatedDenoiseStrength)
                        + " rtEffectiveRayLength=" + VulkanShadowRuntimeTuning.effectiveShadowRtRayLength(state.currentShadows.rtShadowMode(), state.shadowRtRayLength, state.shadowRtProductionRayLength, state.shadowRtDedicatedRayLength)
                        + " rtEffectiveSampleCount=" + VulkanShadowRuntimeTuning.effectiveShadowRtSampleCount(state.currentShadows.rtShadowMode(), state.shadowRtSampleCount, state.shadowRtProductionSampleCount, state.shadowRtDedicatedSampleCount)
                        + " normalBiasScale=" + state.currentShadows.normalBiasScale()
                        + " slopeBiasScale=" + state.currentShadows.slopeBiasScale()
        );
    }

    private VulkanShadowPolicyWarningEmitter() {
    }
}
