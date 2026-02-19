package org.dynamislight.impl.vulkan.shader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.dynamislight.impl.vulkan.capability.VulkanAaCapabilityDescriptorV2;
import org.dynamislight.impl.vulkan.capability.VulkanReflectionCapabilityDescriptorV2;
import org.dynamislight.impl.vulkan.capability.VulkanShadowCapabilityDescriptorV2;
import org.dynamislight.spi.render.RenderFeatureMode;
import org.dynamislight.spi.render.RenderShaderModuleDeclaration;
import org.dynamislight.spi.render.RenderShaderStage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.lwjgl.util.shaderc.Shaderc.shaderc_fragment_shader;

class VulkanShaderPhaseCParallelValidationTest {
    @ParameterizedTest(name = "phase-c c1.6 compile parity: {0}")
    @MethodSource("blessedProfiles")
    void assembledShadersCompileAndMatchCanonicalFunctionBodies(
            String profile,
            RenderFeatureMode shadowMode,
            RenderFeatureMode reflectionMode,
            RenderFeatureMode aaMode
    ) throws Exception {
        List<RenderShaderModuleDeclaration> modules = gatherModules(shadowMode, reflectionMode, aaMode);

        String monolithicMain = VulkanShaderSources.mainFragmentMonolithic();
        String monolithicPost = VulkanShaderSources.postFragmentMonolithic();
        VulkanShaderAssemblyResult assembledMainResult = VulkanShaderProfileAssembler.assembleMainFragmentCanonical(modules);
        VulkanShaderAssemblyResult assembledPostResult = VulkanShaderProfileAssembler.assemblePostFragmentCanonical(modules);
        String assembledMain = assembledMainResult.source();
        String assembledPost = assembledPostResult.source();

        ByteBuffer monoMainSpv = VulkanShaderCompiler.compileGlslToSpv(monolithicMain, shaderc_fragment_shader, profile + ".mono.main.frag");
        ByteBuffer monoPostSpv = VulkanShaderCompiler.compileGlslToSpv(monolithicPost, shaderc_fragment_shader, profile + ".mono.post.frag");
        ByteBuffer asmMainSpv = VulkanShaderCompiler.compileGlslToSpv(assembledMain, shaderc_fragment_shader, profile + ".asm.main.frag");
        ByteBuffer asmPostSpv = VulkanShaderCompiler.compileGlslToSpv(assembledPost, shaderc_fragment_shader, profile + ".asm.post.frag");
        assertTrue(monoMainSpv.remaining() > 0);
        assertTrue(monoPostSpv.remaining() > 0);
        assertTrue(asmMainSpv.remaining() > 0);
        assertTrue(asmPostSpv.remaining() > 0);

        assertFunctionBodiesEquivalent(
                moduleFunctionNamesFor(modules, "main_geometry", RenderShaderStage.FRAGMENT),
                monolithicMain,
                assembledMain
        );
        assertFunctionBodiesEquivalent(
                moduleFunctionNamesFor(modules, "post_composite", RenderShaderStage.FRAGMENT),
                monolithicPost,
                assembledPost
        );

        assertFalse(assembledMainResult.injectedModules().isEmpty(), "main assembled modules should be non-empty for " + profile);
        assertFalse(assembledPostResult.injectedModules().isEmpty(), "post assembled modules should be non-empty for " + profile);
    }

    private static List<RenderShaderModuleDeclaration> gatherModules(
            RenderFeatureMode shadowMode,
            RenderFeatureMode reflectionMode,
            RenderFeatureMode aaMode
    ) {
        List<RenderShaderModuleDeclaration> shadowModules =
                VulkanShadowCapabilityDescriptorV2.withMode(shadowMode).shaderModules(shadowMode);
        List<RenderShaderModuleDeclaration> reflectionModules =
                VulkanReflectionCapabilityDescriptorV2.withMode(reflectionMode).shaderModules(reflectionMode);
        List<RenderShaderModuleDeclaration> aaModules =
                VulkanAaCapabilityDescriptorV2.withMode(aaMode).shaderModules(aaMode);
        java.util.ArrayList<RenderShaderModuleDeclaration> all = new java.util.ArrayList<>();
        all.addAll(shadowModules);
        all.addAll(reflectionModules);
        all.addAll(aaModules);
        return List.copyOf(all);
    }

    private static Set<String> moduleFunctionNamesFor(
            List<RenderShaderModuleDeclaration> modules,
            String targetPassId,
            RenderShaderStage stage
    ) {
        Set<String> names = new LinkedHashSet<>();
        for (RenderShaderModuleDeclaration module : modules) {
            if (module.targetPassId().equals(targetPassId) && module.stage() == stage) {
                names.addAll(VulkanShaderFunctionText.extractFunctionNames(module.glslBody()));
            }
        }
        return names;
    }

    private static void assertFunctionBodiesEquivalent(Set<String> functionNames, String monolithic, String assembled) {
        for (String functionName : functionNames) {
            String mono = VulkanShaderFunctionText.extractFunctionDefinition(monolithic, functionName).trim();
            String asm = VulkanShaderFunctionText.extractFunctionDefinition(assembled, functionName).trim();
            assertFalse(mono.isBlank(), "missing monolithic function: " + functionName);
            assertFalse(asm.isBlank(), "missing assembled function: " + functionName);
            assertEquals(mono, asm, "function body mismatch: " + functionName);
        }
    }

    private static Stream<Arguments> blessedProfiles() {
        return Stream.of(
                Arguments.of(
                        "performance",
                        VulkanShadowCapabilityDescriptorV2.MODE_PCF,
                        VulkanReflectionCapabilityDescriptorV2.MODE_SSR,
                        VulkanAaCapabilityDescriptorV2.MODE_FXAA_LOW
                ),
                Arguments.of(
                        "balanced",
                        VulkanShadowCapabilityDescriptorV2.MODE_PCSS,
                        VulkanReflectionCapabilityDescriptorV2.MODE_HYBRID,
                        VulkanAaCapabilityDescriptorV2.MODE_TAA
                ),
                Arguments.of(
                        "quality",
                        VulkanShadowCapabilityDescriptorV2.MODE_EVSM,
                        VulkanReflectionCapabilityDescriptorV2.MODE_RT_HYBRID,
                        VulkanAaCapabilityDescriptorV2.MODE_TSR
                ),
                Arguments.of(
                        "stability",
                        VulkanShadowCapabilityDescriptorV2.MODE_VSM,
                        VulkanReflectionCapabilityDescriptorV2.MODE_HYBRID,
                        VulkanAaCapabilityDescriptorV2.MODE_TUUA
                )
        );
    }
}
