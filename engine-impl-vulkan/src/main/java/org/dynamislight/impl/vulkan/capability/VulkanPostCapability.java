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
 * Metadata-only post-stack capability declaration for Vulkan.
 */
public final class VulkanPostCapability implements RenderFeatureCapability {
    private final VulkanPostCapabilityId capabilityId;
    private final RenderFeatureContract contract;

    private VulkanPostCapability(VulkanPostCapabilityId capabilityId) {
        this.capabilityId = capabilityId == null ? VulkanPostCapabilityId.TONEMAP : capabilityId;
        this.contract = buildContract(this.capabilityId);
    }

    public static VulkanPostCapability of(VulkanPostCapabilityId capabilityId) {
        return new VulkanPostCapability(capabilityId);
    }

    public VulkanPostCapabilityId capabilityId() {
        return capabilityId;
    }

    @Override
    public RenderFeatureContract contract() {
        return contract;
    }

    private static RenderFeatureContract buildContract(VulkanPostCapabilityId capabilityId) {
        String key = capabilityId.name().toLowerCase();
        return new RenderFeatureContract(
                "vulkan.post." + key,
                "v1",
                List.of(new RenderPassContribution(
                        "post_composite",
                        RenderPassPhase.POST_MAIN,
                        readsFor(capabilityId),
                        writesFor(capabilityId),
                        false
                )),
                List.of(new RenderShaderHookContribution(
                        "post_composite",
                        RenderShaderStage.FRAGMENT,
                        hookNameFor(capabilityId),
                        implementationKeyFor(capabilityId),
                        false
                )),
                resourcesFor(capabilityId),
                dependenciesFor(capabilityId)
        );
    }

    private static List<String> readsFor(VulkanPostCapabilityId capabilityId) {
        return switch (capabilityId) {
            case TONEMAP -> List.of("scene_color");
            case BLOOM -> List.of("scene_color");
            case SSAO -> List.of("scene_color", "velocity");
            case SMAA -> List.of("scene_color");
            case TAA_RESOLVE -> List.of("scene_color", "history_color", "velocity", "history_velocity");
            case FOG_COMPOSITE -> List.of("scene_color", "depth");
        };
    }

    private static List<String> writesFor(VulkanPostCapabilityId capabilityId) {
        return switch (capabilityId) {
            case TAA_RESOLVE -> List.of("resolved_color", "history_color_next", "history_velocity_next");
            default -> List.of("resolved_color");
        };
    }

    private static String hookNameFor(VulkanPostCapabilityId capabilityId) {
        return switch (capabilityId) {
            case TONEMAP -> "applyTonemap";
            case BLOOM -> "applyBloom";
            case SSAO -> "applySsao";
            case SMAA -> "resolveSmaa";
            case TAA_RESOLVE -> "resolveTaa";
            case FOG_COMPOSITE -> "applyFogComposite";
        };
    }

    private static String implementationKeyFor(VulkanPostCapabilityId capabilityId) {
        return switch (capabilityId) {
            case TONEMAP -> "tonemap_reinhard_gamma";
            case BLOOM -> "bloom_threshold_inline";
            case SSAO -> "ssao_depth_gradient";
            case SMAA -> "smaa_single_pass";
            case TAA_RESOLVE -> "taa_history_reproject";
            case FOG_COMPOSITE -> "fog_post_composite";
        };
    }

    private static List<RenderResourceRequirement> resourcesFor(VulkanPostCapabilityId capabilityId) {
        return switch (capabilityId) {
            case TONEMAP -> List.of(
                    new RenderResourceRequirement("scene_color", RenderResourceType.SAMPLED_IMAGE, RenderBindingFrequency.PER_PASS, 0, 0, true),
                    new RenderResourceRequirement("tonemap_uniforms", RenderResourceType.PUSH_CONSTANTS, RenderBindingFrequency.PER_PASS, 0, 0, true)
            );
            case BLOOM -> List.of(
                    new RenderResourceRequirement("scene_color", RenderResourceType.SAMPLED_IMAGE, RenderBindingFrequency.PER_PASS, 0, 0, true),
                    new RenderResourceRequirement("bloom_uniforms", RenderResourceType.PUSH_CONSTANTS, RenderBindingFrequency.PER_PASS, 0, 0, true)
            );
            case SSAO -> List.of(
                    new RenderResourceRequirement("scene_color", RenderResourceType.SAMPLED_IMAGE, RenderBindingFrequency.PER_PASS, 0, 0, true),
                    new RenderResourceRequirement("velocity", RenderResourceType.SAMPLED_IMAGE, RenderBindingFrequency.PER_PASS, 0, 2, true),
                    new RenderResourceRequirement("ssao_uniforms", RenderResourceType.PUSH_CONSTANTS, RenderBindingFrequency.PER_PASS, 0, 0, true)
            );
            case SMAA -> List.of(
                    new RenderResourceRequirement("scene_color", RenderResourceType.SAMPLED_IMAGE, RenderBindingFrequency.PER_PASS, 0, 0, true),
                    new RenderResourceRequirement("smaa_uniforms", RenderResourceType.PUSH_CONSTANTS, RenderBindingFrequency.PER_PASS, 0, 0, true)
            );
            case TAA_RESOLVE -> List.of(
                    new RenderResourceRequirement("scene_color", RenderResourceType.SAMPLED_IMAGE, RenderBindingFrequency.PER_PASS, 0, 0, true),
                    new RenderResourceRequirement("history_color", RenderResourceType.SAMPLED_IMAGE, RenderBindingFrequency.PER_FRAME, 0, 1, true),
                    new RenderResourceRequirement("velocity", RenderResourceType.SAMPLED_IMAGE, RenderBindingFrequency.PER_PASS, 0, 2, true),
                    new RenderResourceRequirement("history_velocity", RenderResourceType.SAMPLED_IMAGE, RenderBindingFrequency.PER_FRAME, 0, 3, true),
                    new RenderResourceRequirement("taa_uniforms", RenderResourceType.PUSH_CONSTANTS, RenderBindingFrequency.PER_PASS, 0, 0, true)
            );
            case FOG_COMPOSITE -> List.of(
                    new RenderResourceRequirement("scene_color", RenderResourceType.SAMPLED_IMAGE, RenderBindingFrequency.PER_PASS, 0, 0, true),
                    new RenderResourceRequirement("depth", RenderResourceType.SAMPLED_IMAGE, RenderBindingFrequency.PER_PASS, 0, 2, true),
                    new RenderResourceRequirement("fog_uniforms", RenderResourceType.PUSH_CONSTANTS, RenderBindingFrequency.PER_PASS, 0, 0, true)
            );
        };
    }

    private static List<RenderCapabilityDependency> dependenciesFor(VulkanPostCapabilityId capabilityId) {
        return switch (capabilityId) {
            case TONEMAP -> List.of();
            case BLOOM -> List.of(new RenderCapabilityDependency("vulkan.post.tonemap", "resolved_color", false));
            case SSAO -> List.of(new RenderCapabilityDependency("vulkan.main", "velocity", true));
            case SMAA -> List.of(new RenderCapabilityDependency("vulkan.main", "scene_color", true));
            case TAA_RESOLVE -> List.of(
                    new RenderCapabilityDependency("vulkan.main", "velocity", true),
                    new RenderCapabilityDependency("vulkan.post.smaa", "edge_blend", false)
            );
            case FOG_COMPOSITE -> List.of(new RenderCapabilityDependency("vulkan.main", "depth", true));
        };
    }
}
