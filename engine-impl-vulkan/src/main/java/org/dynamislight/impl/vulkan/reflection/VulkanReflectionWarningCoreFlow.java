package org.dynamislight.impl.vulkan.reflection;

import org.dynamislight.impl.vulkan.VulkanContext;
import org.dynamislight.impl.vulkan.runtime.model.*;

import org.dynamislight.impl.vulkan.state.VulkanTelemetryStateBinder;
import org.dynamislight.impl.vulkan.reflection.VulkanReflectionTelemetryProfileWarning;

import org.dynamislight.impl.vulkan.runtime.config.*;

import java.util.List;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.scene.MaterialDesc;

final class VulkanReflectionWarningCoreFlow {
    static void process(
            Object runtime,
            VulkanContext context,
            QualityTier qualityTier,
            List<EngineWarning> warnings,
            int reflectionBaseMode,
            ReflectionOverrideSummary overrideSummary,
            VulkanContext.ReflectionProbeDiagnostics probeDiagnostics,
            ReflectionProbeChurnDiagnostics churnDiagnostics,
            boolean reflectionRtLaneRequested,
            int planarEligible,
            int planarExcluded,
            PostProcessRenderConfig post,
            double lastFrameGpuMs,
            long plannedVisibleObjects,
            ReflectionProfile reflectionProfile,
            boolean mockContext,
            List<MaterialDesc> currentSceneMaterials,
            double reflectionTransparencyCandidateReactiveMin,
            double planarPerfCap,
            double rtPerfCap
    ) {
        VulkanReflectionCoreWarningEmitter.State coreState = new VulkanReflectionCoreWarningEmitter.State();
        VulkanTelemetryStateBinder.copyMatchingFields(runtime, coreState);
        coreState.reflectionBaseMode = reflectionBaseMode;
        VulkanReflectionCoreWarningEmitter.emit(
                warnings,
                coreState,
                overrideSummary,
                churnDiagnostics,
                probeDiagnostics
        );
        VulkanTelemetryStateBinder.copyMatchingFields(coreState, runtime);

        VulkanReflectionPlanarWarningEmitter.State planarState = new VulkanReflectionPlanarWarningEmitter.State();
        VulkanTelemetryStateBinder.copyMatchingFields(runtime, planarState);
        planarState.reflectionBaseMode = reflectionBaseMode;
        VulkanReflectionPlanarWarningEmitter.emit(
                warnings,
                planarState,
                planarEligible,
                planarExcluded,
                post.reflectionsPlanarPlaneHeight(),
                planarPerfCap
        );
        VulkanTelemetryStateBinder.copyMatchingFields(planarState, runtime);

        VulkanReflectionRtWarningEmitter.State rtWarningState = new VulkanReflectionRtWarningEmitter.State();
        VulkanTelemetryStateBinder.copyMatchingFields(runtime, rtWarningState);
        rtWarningState.mockContext = mockContext;
        VulkanReflectionRtWarningEmitter.emit(
                warnings,
                rtWarningState,
                reflectionRtLaneRequested,
                reflectionBaseMode,
                lastFrameGpuMs,
                plannedVisibleObjects,
                post.reflectionsSsrStrength(),
                post.reflectionsSsrMaxRoughness(),
                post.reflectionsTemporalWeight(),
                rtPerfCap
        );
        VulkanTelemetryStateBinder.copyMatchingFields(rtWarningState, runtime);

        TransparencyCandidateSummary transparencySummary =
                VulkanReflectionAnalysis.summarizeReflectionTransparencyCandidates(currentSceneMaterials, reflectionTransparencyCandidateReactiveMin);
        VulkanReflectionRtTransparencyWarningEmitter.State rtTransparencyState = new VulkanReflectionRtTransparencyWarningEmitter.State();
        VulkanTelemetryStateBinder.copyMatchingFields(runtime, rtTransparencyState);
        rtTransparencyState.mockContext = mockContext;
        VulkanReflectionRtTransparencyWarningEmitter.emit(warnings, rtTransparencyState, transparencySummary);
        VulkanTelemetryStateBinder.copyMatchingFields(rtTransparencyState, runtime);

        VulkanReflectionTelemetryProfileWarning.State telemetryProfileState = new VulkanReflectionTelemetryProfileWarning.State();
        VulkanTelemetryStateBinder.copyMatchingFields(runtime, telemetryProfileState);
        telemetryProfileState.setReflectionProfile(reflectionProfile);
        warnings.add(VulkanReflectionTelemetryProfileWarning.warning(telemetryProfileState));
    }

    private VulkanReflectionWarningCoreFlow() {
    }
}
