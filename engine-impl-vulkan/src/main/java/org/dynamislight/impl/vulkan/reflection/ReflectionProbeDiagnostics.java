package org.dynamislight.impl.vulkan.reflection;

public record ReflectionProbeDiagnostics(
        int configuredProbeCount,
        int activeProbeCount,
        int slotCount,
        int metadataCapacity,
        int frustumVisibleCount,
        int deferredProbeCount,
        int visibleUniquePathCount,
        int missingSlotPathCount,
        int lodTier0Count,
        int lodTier1Count,
        int lodTier2Count,
        int lodTier3Count
) {
}
