package org.dynamisengine.light.impl.vulkan.reflection;

public record ReflectionAdaptivePolicyDiagnostics(
        boolean enabled,
        float baseTemporalWeight,
        float activeTemporalWeight,
        float baseSsrStrength,
        float activeSsrStrength,
        float baseSsrStepScale,
        float activeSsrStepScale,
        double temporalBoostMax,
        double ssrStrengthScaleMin,
        double stepScaleBoostMax
) {
}
