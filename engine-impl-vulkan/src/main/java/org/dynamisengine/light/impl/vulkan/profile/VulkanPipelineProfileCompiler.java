package org.dynamisengine.light.impl.vulkan.profile;

import java.util.ArrayList;
import java.util.List;
import org.dynamisengine.light.impl.vulkan.capability.VulkanAaCapabilityDescriptorV2;
import org.dynamisengine.light.impl.vulkan.capability.VulkanCoreCapabilityDescriptorV2;
import org.dynamisengine.light.impl.vulkan.capability.VulkanGiCapabilityDescriptorV2;
import org.dynamisengine.light.impl.vulkan.capability.VulkanLightingCapabilityDescriptorV2;
import org.dynamisengine.light.impl.vulkan.capability.VulkanPbrCapabilityDescriptorV2;
import org.dynamisengine.light.impl.vulkan.capability.VulkanPostCapabilityDescriptorV2;
import org.dynamisengine.light.impl.vulkan.capability.VulkanReflectionCapabilityDescriptorV2;
import org.dynamisengine.light.impl.vulkan.capability.VulkanRtCapabilityDescriptorV2;
import org.dynamisengine.light.impl.vulkan.capability.VulkanSkyCapabilityDescriptorV2;
import org.dynamisengine.light.impl.vulkan.capability.VulkanShadowCapabilityDescriptorV2;
import org.dynamisengine.light.impl.vulkan.descriptor.VulkanComposedDescriptorLayoutPlan;
import org.dynamisengine.light.impl.vulkan.descriptor.VulkanDescriptorLayoutComposer;
import org.dynamisengine.light.impl.vulkan.shader.VulkanShaderProfileAssembler;
import org.dynamisengine.light.spi.render.RenderFeatureCapabilityV2;
import org.dynamisengine.light.spi.render.RenderShaderModuleDeclaration;

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
        RenderFeatureCapabilityV2 lighting = VulkanLightingCapabilityDescriptorV2.withMode(effective.lightingMode());
        RenderFeatureCapabilityV2 pbr = VulkanPbrCapabilityDescriptorV2.withMode(effective.pbrMode());
        RenderFeatureCapabilityV2 gi = VulkanGiCapabilityDescriptorV2.withMode(effective.giMode());
        RenderFeatureCapabilityV2 sky = VulkanSkyCapabilityDescriptorV2.withMode(effective.skyMode());
        RenderFeatureCapabilityV2 rt = VulkanRtCapabilityDescriptorV2.withMode(effective.rtMode());

        List<RenderFeatureCapabilityV2> capabilities = List.of(core, shadow, reflection, lighting, pbr, gi, sky, rt, aa, post);
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
        mainModules.addAll(lighting.shaderModules(effective.lightingMode()));
        mainModules.addAll(pbr.shaderModules(effective.pbrMode()));

        List<RenderShaderModuleDeclaration> postModules = new ArrayList<>();
        postModules.addAll(reflection.shaderModules(effective.reflectionMode()));
        postModules.addAll(gi.shaderModules(effective.giMode()));
        postModules.addAll(sky.shaderModules(effective.skyMode()));
        postModules.addAll(rt.shaderModules(effective.rtMode()));
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
