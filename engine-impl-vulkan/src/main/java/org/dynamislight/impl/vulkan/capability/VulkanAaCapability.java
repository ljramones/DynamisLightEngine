package org.dynamislight.impl.vulkan.capability;

import java.util.List;
import org.dynamislight.spi.render.RenderBindingFrequency;
import org.dynamislight.spi.render.RenderCapabilityDependency;
import org.dynamislight.spi.render.RenderFeatureCapability;
import org.dynamislight.spi.render.RenderFeatureContract;
import org.dynamislight.spi.render.RenderPassContribution;
import org.dynamislight.spi.render.RenderPassPhase;
import org.dynamislight.spi.render.RenderResourceRequirement;
import org.dynamislight.spi.render.RenderResourceType;
import org.dynamislight.spi.render.RenderShaderHookContribution;
import org.dynamislight.spi.render.RenderShaderStage;

/**
 * Metadata-only AA resolve capability declaration for Vulkan.
 */
public final class VulkanAaCapability implements RenderFeatureCapability {
    private final VulkanAaCapabilityMode mode;
    private final RenderFeatureContract contract;

    private VulkanAaCapability(VulkanAaCapabilityMode mode) {
        this.mode = mode == null ? VulkanAaCapabilityMode.TAA : mode;
        this.contract = buildContract(this.mode);
    }

    public static VulkanAaCapability of(VulkanAaCapabilityMode mode) {
        return new VulkanAaCapability(mode);
    }

    public VulkanAaCapabilityMode mode() {
        return mode;
    }

    @Override
    public RenderFeatureContract contract() {
        return contract;
    }

    private static RenderFeatureContract buildContract(VulkanAaCapabilityMode mode) {
        String modeKey = mode.name().toLowerCase();
        return new RenderFeatureContract(
                "vulkan.aa." + modeKey,
                "v1",
                List.of(new RenderPassContribution(
                        "post_composite",
                        RenderPassPhase.POST_MAIN,
                        List.of("scene_color", "velocity", "history_color", "history_velocity"),
                        List.of("resolved_color", "history_color_next", "history_velocity_next"),
                        false
                )),
                List.of(
                        new RenderShaderHookContribution(
                                "post_composite",
                                RenderShaderStage.FRAGMENT,
                                "resolveAntiAliasing",
                                "aa_" + modeKey,
                                false
                        ),
                        new RenderShaderHookContribution(
                                "post_composite",
                                RenderShaderStage.FRAGMENT,
                                "resolveTemporalHistory",
                                requiresTemporalHistory(mode) ? "temporal_history_" + modeKey : "temporal_history_disabled",
                                !requiresTemporalHistory(mode)
                        )
                ),
                List.of(
                        new RenderResourceRequirement("scene_color", RenderResourceType.SAMPLED_IMAGE, RenderBindingFrequency.PER_PASS, 0, 0, true),
                        new RenderResourceRequirement("velocity", RenderResourceType.SAMPLED_IMAGE, RenderBindingFrequency.PER_PASS, 0, 2, true),
                        new RenderResourceRequirement("history_color", RenderResourceType.SAMPLED_IMAGE, RenderBindingFrequency.PER_FRAME, 0, 1, requiresTemporalHistory(mode)),
                        new RenderResourceRequirement("history_velocity", RenderResourceType.SAMPLED_IMAGE, RenderBindingFrequency.PER_FRAME, 0, 3, requiresTemporalHistory(mode)),
                        new RenderResourceRequirement("aa_uniforms", RenderResourceType.PUSH_CONSTANTS, RenderBindingFrequency.PER_PASS, 0, 0, true)
                ),
                List.of(
                        new RenderCapabilityDependency("vulkan.post.smaa", "edge_blend", usesSmaa(mode)),
                        new RenderCapabilityDependency("vulkan.post.tone", "resolved_color", true)
                )
        );
    }

    private static boolean requiresTemporalHistory(VulkanAaCapabilityMode mode) {
        return switch (mode) {
            case TAA, TSR, TUUA, HYBRID_TUUA_MSAA, DLAA -> true;
            case MSAA_SELECTIVE, FXAA_LOW -> false;
        };
    }

    private static boolean usesSmaa(VulkanAaCapabilityMode mode) {
        return switch (mode) {
            case TAA, TSR, TUUA, HYBRID_TUUA_MSAA -> true;
            case MSAA_SELECTIVE, DLAA, FXAA_LOW -> false;
        };
    }
}
