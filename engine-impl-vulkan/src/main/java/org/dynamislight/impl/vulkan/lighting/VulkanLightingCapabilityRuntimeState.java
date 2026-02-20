package org.dynamislight.impl.vulkan.lighting;

import java.util.List;
import java.util.Map;
import org.dynamislight.api.config.QualityTier;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.runtime.LightingCapabilityDiagnostics;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.impl.vulkan.runtime.config.VulkanRuntimeOptionParsing;
import org.dynamislight.impl.vulkan.warning.lighting.VulkanLightingWarningEmitter;

/**
 * Runtime holder for lighting capability mode telemetry and typed diagnostics.
 */
public final class VulkanLightingCapabilityRuntimeState {
    private boolean physicallyBasedUnitsEnabled = true;
    private boolean prioritizationEnabled = true;
    private boolean emissiveMeshEnabled;
    private int localLightBudget = 8;
    private String modeLastFrame = "baseline_directional_point_spot";
    private int directionalCountLastFrame;
    private int pointCountLastFrame;
    private int spotCountLastFrame;
    private List<String> activeCapabilitiesLastFrame = List.of();
    private List<String> prunedCapabilitiesLastFrame = List.of();
    private List<String> signalsLastFrame = List.of();

    public void reset() {
        modeLastFrame = "baseline_directional_point_spot";
        directionalCountLastFrame = 0;
        pointCountLastFrame = 0;
        spotCountLastFrame = 0;
        activeCapabilitiesLastFrame = List.of();
        prunedCapabilitiesLastFrame = List.of();
        signalsLastFrame = List.of();
    }

    public void applyBackendOptions(Map<String, String> backendOptions) {
        Map<String, String> safe = backendOptions == null ? Map.of() : backendOptions;
        physicallyBasedUnitsEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.lighting.physicallyBasedUnitsEnabled", "true")
        );
        prioritizationEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.lighting.prioritizationEnabled", "true")
        );
        emissiveMeshEnabled = Boolean.parseBoolean(
                safe.getOrDefault("vulkan.lighting.emissiveMeshEnabled", "false")
        );
        localLightBudget = VulkanRuntimeOptionParsing.parseBackendIntOption(
                safe,
                "vulkan.lighting.localLightBudget",
                localLightBudget,
                1,
                4096
        );
    }

    public void emitFrameWarning(QualityTier qualityTier, List<LightDesc> lights, List<EngineWarning> warnings) {
        VulkanLightingWarningEmitter.Result emission = VulkanLightingWarningEmitter.emit(
                qualityTier,
                lights == null ? List.of() : lights,
                physicallyBasedUnitsEnabled,
                prioritizationEnabled,
                emissiveMeshEnabled,
                localLightBudget
        );
        modeLastFrame = emission.plan().modeId();
        directionalCountLastFrame = emission.plan().directionalLights();
        pointCountLastFrame = emission.plan().pointLights();
        spotCountLastFrame = emission.plan().spotLights();
        activeCapabilitiesLastFrame = emission.plan().activeCapabilities();
        prunedCapabilitiesLastFrame = emission.plan().prunedCapabilities();
        signalsLastFrame = emission.plan().signals();
        if (warnings != null) {
            warnings.add(emission.warning());
        }
    }

    public LightingCapabilityDiagnostics diagnostics() {
        return new LightingCapabilityDiagnostics(
                !modeLastFrame.isBlank(),
                modeLastFrame,
                directionalCountLastFrame,
                pointCountLastFrame,
                spotCountLastFrame,
                physicallyBasedUnitsEnabled,
                prioritizationEnabled,
                emissiveMeshEnabled,
                activeCapabilitiesLastFrame,
                prunedCapabilitiesLastFrame,
                signalsLastFrame
        );
    }
}
