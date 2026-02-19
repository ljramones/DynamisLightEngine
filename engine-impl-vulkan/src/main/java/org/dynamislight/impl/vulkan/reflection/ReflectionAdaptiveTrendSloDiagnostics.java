package org.dynamislight.impl.vulkan.reflection;

public record ReflectionAdaptiveTrendSloDiagnostics(
        String status,
        String reason,
        boolean failed,
        int windowSamples,
        double meanSeverity,
        double highRatio,
        double sloMeanSeverityMax,
        double sloHighRatioMax,
        int sloMinSamples
) {
}
