package org.dynamislight.impl.vulkan;

final class VulkanReflectionRtStateMachine {
    static final class State {
        PostProcessRenderConfig currentPost;
        boolean mockContext;
        boolean reflectionRtSingleBounceEnabled;
        boolean reflectionRtMultiBounceEnabled;
        boolean reflectionRtDedicatedPipelineEnabled;
        boolean reflectionRtDedicatedDenoisePipelineEnabled;
        boolean reflectionRtPromotionReadyLastFrame;
        boolean reflectionPlanarScopeIncludeAuto;
        boolean reflectionPlanarScopeIncludeProbeOnly;
        boolean reflectionPlanarScopeIncludeSsrOnly;
        boolean reflectionPlanarScopeIncludeOther;
        String reflectionSsrTaaReprojectionPolicyActive;
        String reflectionSsrTaaHistoryPolicyActive;
        long plannedVisibleObjects;

        boolean reflectionRtLaneRequested;
        boolean reflectionRtTraversalSupported;
        boolean reflectionRtDedicatedCapabilitySupported;
        boolean reflectionRtLaneActive;
        boolean reflectionRtDedicatedHardwarePipelineActive;
        String reflectionRtFallbackChainActive;
        boolean reflectionRtRequireActive;
        boolean reflectionRtRequireMultiBounce;
        boolean reflectionRtRequireDedicatedPipeline;
        boolean reflectionRtRequireActiveUnmetLastFrame;
        boolean reflectionRtRequireMultiBounceUnmetLastFrame;
        boolean reflectionRtRequireDedicatedPipelineUnmetLastFrame;
        String reflectionRtBlasLifecycleState;
        String reflectionRtTlasLifecycleState;
        String reflectionRtSbtLifecycleState;
        int reflectionRtBlasObjectCount;
        int reflectionRtTlasInstanceCount;
        int reflectionRtSbtRecordCount;
    }

    static void refreshRtPathState(State state, int reflectionBaseMode, VulkanContext context) {
        state.reflectionRtLaneRequested = (state.currentPost.reflectionsMode() & 1024) != 0 || reflectionBaseMode == 4;
        state.reflectionRtTraversalSupported = state.mockContext || context.isHardwareRtShadowTraversalSupported();
        state.reflectionRtDedicatedCapabilitySupported = state.mockContext || context.isHardwareRtShadowBvhSupported();
        state.reflectionRtLaneActive = state.reflectionRtLaneRequested && state.reflectionRtSingleBounceEnabled && state.reflectionRtTraversalSupported;
        boolean reflectionRtMultiBounceActive = state.reflectionRtLaneActive && state.reflectionRtMultiBounceEnabled;
        state.reflectionRtDedicatedHardwarePipelineActive = state.reflectionRtLaneActive
                && state.reflectionRtDedicatedPipelineEnabled
                && state.reflectionRtDedicatedCapabilitySupported;
        state.reflectionRtFallbackChainActive = state.reflectionRtLaneActive ? "rt->ssr->probe" : "ssr->probe";
        state.reflectionRtRequireActiveUnmetLastFrame = state.reflectionRtRequireActive && state.reflectionRtLaneRequested && !state.reflectionRtLaneActive;
        state.reflectionRtRequireMultiBounceUnmetLastFrame =
                state.reflectionRtRequireMultiBounce && state.reflectionRtLaneRequested && !reflectionRtMultiBounceActive;
        state.reflectionRtRequireDedicatedPipelineUnmetLastFrame =
                state.reflectionRtRequireDedicatedPipeline && state.reflectionRtLaneRequested && !state.reflectionRtDedicatedHardwarePipelineActive;
        if (!(state.reflectionRtLaneRequested || reflectionBaseMode == 4)) {
            state.reflectionRtBlasLifecycleState = "disabled";
            state.reflectionRtTlasLifecycleState = "disabled";
            state.reflectionRtSbtLifecycleState = "disabled";
            state.reflectionRtBlasObjectCount = 0;
            state.reflectionRtTlasInstanceCount = 0;
            state.reflectionRtSbtRecordCount = 0;
            return;
        }
        if (state.reflectionRtDedicatedHardwarePipelineActive) {
            state.reflectionRtBlasLifecycleState = state.mockContext ? "mock_active" : "active";
            state.reflectionRtTlasLifecycleState = state.mockContext ? "mock_active" : "active";
            state.reflectionRtSbtLifecycleState = state.mockContext ? "mock_active" : "active";
            int sceneObjectEstimate = (int) Math.max(0L, Math.min((long) Integer.MAX_VALUE, state.plannedVisibleObjects));
            state.reflectionRtBlasObjectCount = sceneObjectEstimate;
            state.reflectionRtTlasInstanceCount = sceneObjectEstimate;
            state.reflectionRtSbtRecordCount = Math.max(1, sceneObjectEstimate + 2);
        } else {
            state.reflectionRtBlasLifecycleState = "pending";
            state.reflectionRtTlasLifecycleState = "pending";
            state.reflectionRtSbtLifecycleState = "pending";
            state.reflectionRtBlasObjectCount = 0;
            state.reflectionRtTlasInstanceCount = 0;
            state.reflectionRtSbtRecordCount = 0;
        }
    }

    static int composeExecutionMode(State state, int configuredMode, boolean rtLaneActive, boolean planarSelectiveEligible, boolean transparencyCandidatesPresent) {
        int mode = configuredMode & (0x7
                | (1 << 3)
                | (0x7 << 4)
                | (1 << 7)
                | (1 << 8)
                | (1 << 9)
                | (1 << 10));
        if (rtLaneActive && state.reflectionRtMultiBounceEnabled) {
            mode |= (1 << 17);
        }
        if (rtLaneActive) {
            mode |= (1 << 15);
            if (state.reflectionRtDedicatedDenoisePipelineEnabled) {
                mode |= (1 << 19);
            }
        }
        if (state.reflectionRtDedicatedHardwarePipelineActive
                || (state.mockContext && rtLaneActive && state.reflectionRtDedicatedPipelineEnabled)) {
            mode |= (1 << 25);
        }
        if (state.reflectionRtPromotionReadyLastFrame) {
            mode |= (1 << 26);
        }
        if (planarSelectiveEligible) {
            mode |= (1 << 14);
            mode |= (1 << 18);
            mode |= (1 << 20);
        }
        if (state.reflectionPlanarScopeIncludeAuto) {
            mode |= (1 << 21);
        }
        if (state.reflectionPlanarScopeIncludeProbeOnly) {
            mode |= (1 << 22);
        }
        if (state.reflectionPlanarScopeIncludeSsrOnly) {
            mode |= (1 << 23);
        }
        if (state.reflectionPlanarScopeIncludeOther) {
            mode |= (1 << 24);
        }
        if (transparencyCandidatesPresent) {
            mode |= (1 << 16);
        }
        if (isReflectionSpaceReprojectionPolicyActive(state)) {
            mode |= (1 << 11);
        }
        if (isStrictReflectionHistoryRejectPolicyActive(state)) {
            mode |= (1 << 12);
        }
        if (isDisocclusionRejectPolicyActive(state)) {
            mode |= (1 << 13);
        }
        return mode;
    }

    private static boolean isReflectionSpaceReprojectionPolicyActive(State state) {
        return "reflection_space_reject".equals(state.reflectionSsrTaaReprojectionPolicyActive)
                || "reflection_space_bias".equals(state.reflectionSsrTaaReprojectionPolicyActive);
    }

    private static boolean isStrictReflectionHistoryRejectPolicyActive(State state) {
        return "reflection_disocclusion_reject".equals(state.reflectionSsrTaaHistoryPolicyActive)
                || "reflection_region_reject".equals(state.reflectionSsrTaaHistoryPolicyActive);
    }

    private static boolean isDisocclusionRejectPolicyActive(State state) {
        return "reflection_disocclusion_reject".equals(state.reflectionSsrTaaHistoryPolicyActive);
    }

    private VulkanReflectionRtStateMachine() {
    }
}
