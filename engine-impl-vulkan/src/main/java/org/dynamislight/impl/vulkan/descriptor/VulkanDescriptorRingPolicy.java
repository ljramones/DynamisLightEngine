package org.dynamislight.impl.vulkan.descriptor;

public final class VulkanDescriptorRingPolicy {
    private VulkanDescriptorRingPolicy() {
    }

    public static Decision decide(int currentCapacity, int requiredSetCount, int maxSetCapacity) {
        int growthBase = currentCapacity <= 0 ? 64 : currentCapacity;
        int grown = Math.max(requiredSetCount, growthBase + Math.max(16, growthBase / 2));
        int rounded = roundUpToPowerOfTwo(grown);
        int capped = Math.min(rounded, maxSetCapacity);
        if (capped < requiredSetCount) {
            return new Decision(requiredSetCount, true);
        }
        return new Decision(capped, false);
    }

    private static int roundUpToPowerOfTwo(int value) {
        int x = Math.max(1, value - 1);
        x |= x >> 1;
        x |= x >> 2;
        x |= x >> 4;
        x |= x >> 8;
        x |= x >> 16;
        if (x == Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return x + 1;
    }

    public record Decision(int targetCapacity, boolean capBypass) {
    }
}
