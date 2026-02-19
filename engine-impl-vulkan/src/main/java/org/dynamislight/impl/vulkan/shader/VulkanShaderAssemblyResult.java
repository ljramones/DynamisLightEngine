package org.dynamislight.impl.vulkan.shader;

import java.util.List;
import org.dynamislight.spi.render.RenderShaderInjectionPoint;
import org.dynamislight.spi.render.RenderShaderModuleDeclaration;

/**
 * Result of module-based shader assembly.
 *
 * @param source assembled GLSL source
 * @param injectedModules modules injected into this source
 * @param unresolvedInjectionPoints injection points with no matching token in host template
 */
public record VulkanShaderAssemblyResult(
        String source,
        List<RenderShaderModuleDeclaration> injectedModules,
        List<RenderShaderInjectionPoint> unresolvedInjectionPoints
) {
    public VulkanShaderAssemblyResult {
        source = source == null ? "" : source;
        injectedModules = injectedModules == null ? List.of() : List.copyOf(injectedModules);
        unresolvedInjectionPoints = unresolvedInjectionPoints == null ? List.of() : List.copyOf(unresolvedInjectionPoints);
    }
}

