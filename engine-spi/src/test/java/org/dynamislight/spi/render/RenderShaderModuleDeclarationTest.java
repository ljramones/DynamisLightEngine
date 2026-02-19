package org.dynamislight.spi.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RenderShaderModuleDeclarationTest {
    @Test
    void normalizesNullsAndCopiesCollections() {
        List<RenderShaderModuleBinding> bindings = new ArrayList<>();
        bindings.add(new RenderShaderModuleBinding(
                " uShadowMap ",
                new RenderDescriptorRequirement(
                        "main",
                        1,
                        4,
                        RenderDescriptorType.COMBINED_IMAGE_SAMPLER,
                        RenderBindingFrequency.PER_FRAME,
                        false
                )
        ));

        RenderShaderModuleDeclaration declaration = new RenderShaderModuleDeclaration(
                " shadow.main ",
                " vulkan.shadow ",
                " main ",
                null,
                null,
                " evaluateShadow ",
                " float evaluateShadow(vec3 p, vec3 n) ",
                " { return 1.0; } ",
                bindings,
                null,
                null,
                -99,
                true
        );

        bindings.clear();

        assertEquals("shadow.main", declaration.moduleId());
        assertEquals("vulkan.shadow", declaration.providerFeatureId());
        assertEquals("main", declaration.targetPassId());
        assertEquals(RenderShaderInjectionPoint.AUXILIARY, declaration.injectionPoint());
        assertEquals(RenderShaderStage.FRAGMENT, declaration.stage());
        assertEquals("evaluateShadow", declaration.hookFunction());
        assertEquals("float evaluateShadow(vec3 p, vec3 n)", declaration.functionSignature());
        assertEquals("{ return 1.0; }", declaration.glslBody());
        assertEquals(1, declaration.bindings().size());
        assertEquals("uShadowMap", declaration.bindings().getFirst().symbolName());
        assertEquals(-1, declaration.ordering());
        assertTrue(declaration.uniformRequirements().isEmpty());
        assertTrue(declaration.pushConstantRequirements().isEmpty());
    }

    @Test
    void bindingDefaultsAreDefensive() {
        RenderShaderModuleBinding binding = new RenderShaderModuleBinding(null, null);
        assertEquals("", binding.symbolName());
        assertEquals(RenderDescriptorType.UNIFORM_BUFFER, binding.descriptor().type());
        assertEquals(RenderBindingFrequency.PER_FRAME, binding.descriptor().frequency());
        assertTrue(binding.descriptor().conditional());
    }
}
