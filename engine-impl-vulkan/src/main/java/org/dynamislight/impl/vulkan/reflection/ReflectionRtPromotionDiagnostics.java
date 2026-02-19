package org.dynamislight.impl.vulkan.reflection;

public record ReflectionRtPromotionDiagnostics(
        boolean readyLastFrame,
        int highStreak,
        int minFrames,
        boolean dedicatedActive,
        boolean perfBreach,
        boolean hybridBreach,
        boolean denoiseBreach,
        boolean asBudgetBreach,
        String transparencyStageGateStatus
) {
}
