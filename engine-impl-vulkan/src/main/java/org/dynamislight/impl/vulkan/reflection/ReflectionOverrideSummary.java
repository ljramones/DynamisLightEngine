package org.dynamislight.impl.vulkan.reflection;

public record ReflectionOverrideSummary(int autoCount, int probeOnlyCount, int ssrOnlyCount, int otherCount) {
    int totalCount() {
        return Math.max(0, autoCount + probeOnlyCount + ssrOnlyCount + otherCount);
    }
}
