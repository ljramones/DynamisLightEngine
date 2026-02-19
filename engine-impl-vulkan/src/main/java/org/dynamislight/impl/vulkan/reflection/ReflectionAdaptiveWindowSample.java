package org.dynamislight.impl.vulkan.reflection;

public record ReflectionAdaptiveWindowSample(
        double severity,
        double temporalDelta,
        double ssrStrengthDelta,
        double ssrStepScaleDelta
) {
}
