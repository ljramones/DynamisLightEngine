package org.dynamisengine.light.impl.vulkan.reflection;

public record ReflectionAdaptiveWindowSample(
        double severity,
        double temporalDelta,
        double ssrStrengthDelta,
        double ssrStepScaleDelta
) {
}
