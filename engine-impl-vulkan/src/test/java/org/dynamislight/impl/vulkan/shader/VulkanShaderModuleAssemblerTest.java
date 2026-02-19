package org.dynamislight.impl.vulkan.shader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.dynamislight.spi.render.RenderBindingFrequency;
import org.dynamislight.spi.render.RenderDescriptorRequirement;
import org.dynamislight.spi.render.RenderDescriptorType;
import org.dynamislight.spi.render.RenderPushConstantRequirement;
import org.dynamislight.spi.render.RenderShaderInjectionPoint;
import org.dynamislight.spi.render.RenderShaderModuleBinding;
import org.dynamislight.spi.render.RenderShaderModuleDeclaration;
import org.dynamislight.spi.render.RenderShaderStage;
import org.dynamislight.spi.render.RenderUniformRequirement;
import org.junit.jupiter.api.Test;

class VulkanShaderModuleAssemblerTest {
    @Test
    void assembleFiltersByTargetPassAndStageAndOrdersByOrderingThenModuleId() {
        String host = """
                #version 450
                /*__DYNAMIS_MODULE_DECLARATIONS__*/
                void main() {
                    /*__DYNAMIS_INJECT_LIGHTING_EVAL__*/
                }
                """;
        List<RenderShaderModuleDeclaration> modules = List.of(
                module("b", "main_geometry", RenderShaderInjectionPoint.LIGHTING_EVAL, RenderShaderStage.FRAGMENT, 20),
                module("a", "main_geometry", RenderShaderInjectionPoint.LIGHTING_EVAL, RenderShaderStage.FRAGMENT, 20),
                module("c", "main_geometry", RenderShaderInjectionPoint.AUXILIARY, RenderShaderStage.FRAGMENT, 5),
                module("d", "post_composite", RenderShaderInjectionPoint.POST_RESOLVE, RenderShaderStage.FRAGMENT, 1),
                module("e", "main_geometry", RenderShaderInjectionPoint.LIGHTING_EVAL, RenderShaderStage.VERTEX, 1)
        );

        VulkanShaderAssemblyResult result = VulkanShaderModuleAssembler.assemble(
                host,
                "main_geometry",
                RenderShaderStage.FRAGMENT,
                modules
        );

        assertEquals(3, result.injectedModules().size());
        String source = VulkanShaderModuleAssembler.normalizeLineEndings(result.source());
        int idxAux = source.indexOf("module-c");
        int idxA = source.indexOf("module-a");
        int idxB = source.indexOf("module-b");
        assertTrue(idxAux >= 0);
        assertTrue(idxA >= 0);
        assertTrue(idxB >= 0);
        assertTrue(idxAux < idxA);
        assertTrue(idxA < idxB);
        assertFalse(source.contains("module-d"));
        assertFalse(source.contains("module-e"));
    }

    @Test
    void assembleReportsUnresolvedInjectionPointWhenTokenMissing() {
        String host = """
                #version 450
                void main() { }
                """;
        List<RenderShaderModuleDeclaration> modules = List.of(
                module("a", "main_geometry", RenderShaderInjectionPoint.LIGHTING_EVAL, RenderShaderStage.FRAGMENT, 5)
        );

        VulkanShaderAssemblyResult result = VulkanShaderModuleAssembler.assemble(
                host,
                "main_geometry",
                RenderShaderStage.FRAGMENT,
                modules
        );
        assertTrue(result.unresolvedInjectionPoints().contains(RenderShaderInjectionPoint.LIGHTING_EVAL));
    }

    @Test
    void assembleInsertsModuleBodiesAfterVersionWhenDeclarationTokenMissing() {
        String host = """
                #version 450
                void main() {
                    gl_Position = vec4(0.0);
                }
                """;
        List<RenderShaderModuleDeclaration> modules = List.of(
                module("a", "main_geometry", RenderShaderInjectionPoint.AUXILIARY, RenderShaderStage.FRAGMENT, 5)
        );

        VulkanShaderAssemblyResult result = VulkanShaderModuleAssembler.assemble(
                host,
                "main_geometry",
                RenderShaderStage.FRAGMENT,
                modules
        );
        String source = VulkanShaderModuleAssembler.normalizeLineEndings(result.source());
        int version = source.indexOf("#version 450");
        int body = source.indexOf("module-a");
        int main = source.indexOf("void main()");
        assertTrue(version >= 0);
        assertTrue(body > version);
        assertTrue(main > body);
    }

    @Test
    void shaderSourcesExposePhaseCAssemblyHelpers() {
        List<RenderShaderModuleDeclaration> mainModules = List.of(
                module("mainmod", "main_geometry", RenderShaderInjectionPoint.LIGHTING_EVAL, RenderShaderStage.FRAGMENT, 1)
        );
        VulkanShaderAssemblyResult mainResult = VulkanShaderSources.assembleMainFragmentModules(mainModules);
        assertTrue(mainResult.source().contains("module-mainmod"));

        List<RenderShaderModuleDeclaration> postModules = List.of(
                module("postmod", "post_composite", RenderShaderInjectionPoint.POST_RESOLVE, RenderShaderStage.FRAGMENT, 1)
        );
        VulkanShaderAssemblyResult postResult = VulkanShaderSources.assemblePostFragmentModules(postModules);
        assertTrue(postResult.source().contains("module-postmod"));
    }

    private static RenderShaderModuleDeclaration module(
            String id,
            String targetPass,
            RenderShaderInjectionPoint point,
            RenderShaderStage stage,
            int ordering
    ) {
        RenderDescriptorRequirement req = new RenderDescriptorRequirement(
                targetPass,
                0,
                0,
                RenderDescriptorType.UNIFORM_BUFFER,
                RenderBindingFrequency.PER_FRAME,
                false
        );
        return new RenderShaderModuleDeclaration(
                "module-" + id,
                "feature-" + id,
                targetPass,
                point,
                stage,
                "hook" + id,
                "float hook" + id + "(void)",
                "float hook" + id + "(void) { return 1.0; } // module-" + id,
                List.of(new RenderShaderModuleBinding("uX" + id, req)),
                List.of(new RenderUniformRequirement("global_scene", "u" + id, 0, 0)),
                List.of(new RenderPushConstantRequirement(targetPass, List.of(stage), 0, 16)),
                ordering,
                false
        );
    }
}

