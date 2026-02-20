package org.dynamislight.impl.vulkan.profile;

import java.util.ArrayList;
import java.util.List;
import org.dynamislight.impl.vulkan.capability.VulkanAaCapabilityDescriptorV2;
import org.dynamislight.impl.vulkan.capability.VulkanCoreCapabilityDescriptorV2;
import org.dynamislight.impl.vulkan.capability.VulkanPostCapabilityDescriptorV2;
import org.dynamislight.impl.vulkan.capability.VulkanReflectionCapabilityDescriptorV2;
import org.dynamislight.impl.vulkan.capability.VulkanShadowCapabilityDescriptorV2;
import org.dynamislight.impl.vulkan.descriptor.VulkanComposedDescriptorLayoutPlan;
import org.dynamislight.impl.vulkan.descriptor.VulkanDescriptorLayoutComposer;
import org.dynamislight.impl.vulkan.shader.VulkanShaderProfileAssembler;
import org.dynamislight.spi.render.RenderFeatureCapabilityV2;
import org.dynamislight.spi.render.RenderShaderModuleDeclaration;

/**
 * Phase C profile compiler (C.3.2): builds composed shader sources and descriptor plans.
 */
public final class VulkanPipelineProfileCompiler {
    private VulkanPipelineProfileCompiler() {
    }

    public static VulkanPipelineProfileCompilation compile(VulkanPipelineProfileKey key) {
        VulkanPipelineProfileKey effective = key == null ? VulkanPipelineProfileKey.defaults() : key;

        RenderFeatureCapabilityV2 core = new VulkanCoreCapabilityDescriptorV2();
        RenderFeatureCapabilityV2 shadow = VulkanShadowCapabilityDescriptorV2.withMode(effective.shadowMode());
        RenderFeatureCapabilityV2 reflection = VulkanReflectionCapabilityDescriptorV2.withMode(effective.reflectionMode());
        RenderFeatureCapabilityV2 aa = VulkanAaCapabilityDescriptorV2.withMode(effective.aaMode());
        RenderFeatureCapabilityV2 post = VulkanPostCapabilityDescriptorV2.withMode(effective.postMode());

        List<RenderFeatureCapabilityV2> capabilities = List.of(core, shadow, reflection, aa, post);
        VulkanComposedDescriptorLayoutPlan mainPlan = VulkanDescriptorLayoutComposer.composeForPass(
                "main_geometry",
                effective.tier(),
                capabilities
        );
        VulkanComposedDescriptorLayoutPlan postPlan = VulkanDescriptorLayoutComposer.composeForPass(
                "post_composite",
                effective.tier(),
                capabilities
        );

        List<RenderShaderModuleDeclaration> mainModules = new ArrayList<>();
        mainModules.addAll(shadow.shaderModules(effective.shadowMode()));
        mainModules.addAll(reflection.shaderModules(effective.reflectionMode()));

        List<RenderShaderModuleDeclaration> postModules = new ArrayList<>();
        postModules.addAll(reflection.shaderModules(effective.reflectionMode()));
        postModules.addAll(aa.shaderModules(effective.aaMode()));

        String mainFragmentSource = VulkanShaderProfileAssembler.assembleMainFragmentCanonical(mainModules).source();
        String postFragmentSource = VulkanShaderProfileAssembler.assemblePostFragmentCanonical(postModules).source();

        return new VulkanPipelineProfileCompilation(
                effective,
                capabilities,
                mainFragmentSource,
                postFragmentSource,
                mainPlan,
                postPlan
        );
    }
}
