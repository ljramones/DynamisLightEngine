package org.dynamislight.impl.vulkan.shadow;

import org.dynamislight.impl.vulkan.runtime.model.*;

import org.dynamislight.impl.vulkan.runtime.model.*;

import java.util.List;
import org.dynamislight.api.event.EngineWarning;

public final class VulkanShadowCoverageMomentWarningEmitter {
    public static final class State {
        public ShadowRenderConfig currentShadows;
        public boolean shadowSchedulerEnabled;
        public int shadowMaxFacesPerFrame;
        public int shadowPointBudgetRenderedFacesLastFrame;
        public int shadowMaxLocalLayers;
        public boolean shadowMomentResourcesAvailable;
        public boolean shadowMomentInitialized;
    }

    public static void emit(List<EngineWarning> warnings, State state) {
        if (state == null || state.currentShadows == null) {
            return;
        }
        if (state.currentShadows.renderedLocalShadowLights() < state.currentShadows.selectedLocalShadowLights()) {
            boolean budgetBoundDeferral = state.currentShadows.deferredShadowLightCount() > 0
                    || (state.shadowMaxFacesPerFrame > 0
                    && state.shadowPointBudgetRenderedFacesLastFrame >= state.shadowMaxFacesPerFrame)
                    || (state.shadowMaxLocalLayers > 0
                    && state.currentShadows.maxShadowedLocalLights() > state.shadowMaxLocalLayers)
                    || state.shadowSchedulerEnabled;
            if (budgetBoundDeferral) {
                warnings.add(new EngineWarning(
                        "SHADOW_LOCAL_RENDER_DEFERRED_POLICY",
                        "Vulkan local-shadow deferral active by policy/budget "
                                + "(requestedLocalShadows=" + state.currentShadows.selectedLocalShadowLights()
                                + ", renderedLocalShadows=" + state.currentShadows.renderedLocalShadowLights()
                                + ", deferredShadowLights=" + state.currentShadows.deferredShadowLightCount()
                                + ", schedulerEnabled=" + state.shadowSchedulerEnabled
                                + ", maxShadowFacesPerFrameConfigured=" + (state.shadowMaxFacesPerFrame > 0 ? Integer.toString(state.shadowMaxFacesPerFrame) : "auto")
                                + ", maxLocalShadowLayersConfigured=" + (state.shadowMaxLocalLayers > 0 ? Integer.toString(state.shadowMaxLocalLayers) : "auto")
                                + ")"
                ));
            } else {
                warnings.add(new EngineWarning(
                        "SHADOW_LOCAL_RENDER_BASELINE",
                        "Vulkan local-shadow render path is below expected coverage without explicit policy constraint "
                                + "(requestedLocalShadows=" + state.currentShadows.selectedLocalShadowLights()
                                + ", renderedLocalShadows=" + state.currentShadows.renderedLocalShadowLights()
                                + ", deferredShadowLights=" + state.currentShadows.deferredShadowLightCount() + ")"
                ));
            }
        }
        if (state.currentShadows.momentFilterEstimateOnly()) {
            warnings.add(new EngineWarning(
                    "SHADOW_FILTER_MOMENT_ESTIMATE_ONLY",
                    "Shadow moment filter requested: " + state.currentShadows.filterPath()
                            + " (runtime active filter path=" + state.currentShadows.runtimeFilterPath()
                            + ", moment atlas sizing/telemetry is estimate-only)"
            ));
        }
        if (state.currentShadows.momentPipelineRequested() && !state.currentShadows.momentPipelineActive()) {
            if (state.shadowMomentResourcesAvailable && !state.shadowMomentInitialized) {
                warnings.add(new EngineWarning(
                        "SHADOW_MOMENT_PIPELINE_INITIALIZING",
                        "Shadow moment resources are allocated but awaiting first-use initialization "
                                + "(requested=" + state.currentShadows.momentPipelineRequested()
                                + ", active=" + state.currentShadows.momentPipelineActive() + ")"
                ));
            } else {
                warnings.add(new EngineWarning(
                        "SHADOW_MOMENT_PIPELINE_PENDING",
                        "Shadow moment pipeline requested but not yet active "
                                + "(requested=" + state.currentShadows.momentPipelineRequested()
                                + ", active=" + state.currentShadows.momentPipelineActive() + ")"
                ));
            }
        } else if (state.currentShadows.momentPipelineActive()) {
            warnings.add(new EngineWarning(
                    "SHADOW_MOMENT_APPROX_ACTIVE",
                    "Shadow moment pipeline active with provisional "
                            + state.currentShadows.runtimeFilterPath()
                            + " approximation path (full production filter chain pending)"
            ));
        } else if (!state.currentShadows.filterPath().equals(state.currentShadows.runtimeFilterPath())) {
            warnings.add(new EngineWarning(
                    "SHADOW_FILTER_PATH_REQUESTED",
                    "Shadow filter path requested: " + state.currentShadows.filterPath()
                            + " (runtime active filter path=" + state.currentShadows.runtimeFilterPath() + ")"
            ));
        }
    }

    private VulkanShadowCoverageMomentWarningEmitter() {
    }
}
