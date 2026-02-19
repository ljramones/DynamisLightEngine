package org.dynamislight.impl.vulkan.shader;

import java.util.List;
import org.dynamislight.spi.render.RenderShaderModuleDeclaration;

public final class VulkanShaderSources {
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
        return VulkanMainShaderSources.mainFragment();
    }

    public static String postVertex() {
        return VulkanPostShaderSources.postVertex();
    }

    public static String postFragment() {
        return VulkanPostShaderSources.postFragment();
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
}
