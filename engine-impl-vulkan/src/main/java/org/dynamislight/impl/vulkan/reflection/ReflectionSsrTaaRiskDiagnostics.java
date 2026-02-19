package org.dynamislight.impl.vulkan.reflection;

public record ReflectionSsrTaaRiskDiagnostics(
        boolean instantRisk,
        int highStreak,
        int warnCooldownRemaining,
        double emaReject,
        double emaConfidence,
        boolean warningTriggered
) {
}
