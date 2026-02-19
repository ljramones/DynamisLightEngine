package org.dynamislight.spi.render;

/**
 * Shader-level capability contribution declaration.
 *
 * @param passId owning pass identifier where hook is consumed
 * @param stage shader stage of hook implementation
 * @param hookName stable hook name (for example, evaluateShadow)
 * @param implementationKey symbolic key identifying implementation variant
 * @param optional whether hook is optional (profile/config dependent)
 */
public record RenderShaderHookContribution(
        String passId,
        RenderShaderStage stage,
        String hookName,
        String implementationKey,
        boolean optional
) {
    public RenderShaderHookContribution {
        passId = passId == null ? "" : passId.trim();
        stage = stage == null ? RenderShaderStage.FRAGMENT : stage;
        hookName = hookName == null ? "" : hookName.trim();
        implementationKey = implementationKey == null ? "" : implementationKey.trim();
    }
}
