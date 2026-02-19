package org.dynamislight.impl.vulkan.shader;

import java.util.List;
import org.dynamislight.impl.vulkan.capability.VulkanAaCapabilityDescriptorV2;
import org.dynamislight.impl.vulkan.capability.VulkanReflectionCapabilityDescriptorV2;
import org.dynamislight.impl.vulkan.capability.VulkanShadowCapabilityDescriptorV2;
import org.dynamislight.spi.render.RenderShaderModuleDeclaration;

public final class VulkanShaderSources {
    private static final List<RenderShaderModuleDeclaration> RUNTIME_MAIN_MODULES = runtimeMainModules();
    private static final List<RenderShaderModuleDeclaration> RUNTIME_POST_MODULES = runtimePostModules();
    private static final String MAIN_FRAGMENT_ASSEMBLED = VulkanShaderProfileAssembler
            .assembleMainFragmentCanonical(RUNTIME_MAIN_MODULES)
            .source();
    private static final String POST_FRAGMENT_ASSEMBLED = VulkanShaderProfileAssembler
            .assemblePostFragmentCanonical(RUNTIME_POST_MODULES)
            .source();

    private VulkanShaderSources() {
    }

    public static String shadowVertex() {
        return VulkanShadowShaderSources.shadowVertex();
    }

    public static String shadowFragment() {
        return VulkanShadowShaderSources.shadowFragment();
    }

    public static String shadowFragmentMoments() {
        return VulkanShadowShaderSources.shadowFragmentMoments();
    }

    public static String mainVertex() {
        return VulkanMainShaderSources.mainVertex();
    }

    public static String mainFragment() {
        return MAIN_FRAGMENT_ASSEMBLED;
    }

    public static String postVertex() {
        return VulkanPostShaderSources.postVertex();
    }

    public static String postFragment() {
        return POST_FRAGMENT_ASSEMBLED;
    }

    /**
     * Phase C non-cutover helper: assemble a main-fragment host template from module declarations.
     */
    public static VulkanShaderAssemblyResult assembleMainFragmentModules(List<RenderShaderModuleDeclaration> modules) {
        return VulkanShaderModuleAssembler.assemble(
                VulkanShaderModuleAssembler.defaultMainHostTemplate(),
                "main_geometry",
                org.dynamislight.spi.render.RenderShaderStage.FRAGMENT,
                modules
        );
    }

    /**
     * Phase C non-cutover helper: assemble a post-fragment host template from module declarations.
     */
    public static VulkanShaderAssemblyResult assemblePostFragmentModules(List<RenderShaderModuleDeclaration> modules) {
        return VulkanShaderModuleAssembler.assemble(
                VulkanShaderModuleAssembler.defaultPostHostTemplate(),
                "post_composite",
                org.dynamislight.spi.render.RenderShaderStage.FRAGMENT,
                modules
        );
    }

    public static String mainFragmentMonolithic() {
        return VulkanMainShaderSources.mainFragment();
    }

    public static String postFragmentMonolithic() {
        return VulkanPostShaderSources.postFragment();
    }

    private static List<RenderShaderModuleDeclaration> runtimeMainModules() {
        java.util.ArrayList<RenderShaderModuleDeclaration> modules = new java.util.ArrayList<>();
        modules.addAll(VulkanShadowCapabilityDescriptorV2.withMode(VulkanShadowCapabilityDescriptorV2.MODE_PCF)
                .shaderModules(VulkanShadowCapabilityDescriptorV2.MODE_PCF));
        modules.addAll(VulkanShadowCapabilityDescriptorV2.withMode(VulkanShadowCapabilityDescriptorV2.MODE_EVSM)
                .shaderModules(VulkanShadowCapabilityDescriptorV2.MODE_EVSM));
        modules.addAll(VulkanReflectionCapabilityDescriptorV2.withMode(VulkanReflectionCapabilityDescriptorV2.MODE_HYBRID)
                .shaderModules(VulkanReflectionCapabilityDescriptorV2.MODE_HYBRID));
        return List.copyOf(modules);
    }

    private static List<RenderShaderModuleDeclaration> runtimePostModules() {
        java.util.ArrayList<RenderShaderModuleDeclaration> modules = new java.util.ArrayList<>();
        modules.addAll(VulkanReflectionCapabilityDescriptorV2.withMode(VulkanReflectionCapabilityDescriptorV2.MODE_HYBRID)
                .shaderModules(VulkanReflectionCapabilityDescriptorV2.MODE_HYBRID));
        modules.addAll(VulkanAaCapabilityDescriptorV2.withMode(VulkanAaCapabilityDescriptorV2.MODE_TAA)
                .shaderModules(VulkanAaCapabilityDescriptorV2.MODE_TAA));
        return List.copyOf(modules);
    }
}
