package org.dynamislight.spi.render;

import java.util.List;

/**
 * Declares concrete shader modules for a capability/mode.
 *
 * Phase C introduces composed shader assembly from these module declarations.
 */
public interface RenderShaderModuleContributor {
    /**
     * Returns shader modules contributed by this capability for the requested mode.
     *
     * @param mode active feature mode
     * @return module declarations (possibly empty)
     */
    default List<RenderShaderModuleDeclaration> shaderModules(RenderFeatureMode mode) {
        return List.of();
    }
}
