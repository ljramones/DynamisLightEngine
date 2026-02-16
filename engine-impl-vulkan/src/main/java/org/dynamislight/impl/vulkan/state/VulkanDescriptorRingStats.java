package org.dynamislight.impl.vulkan.state;

public final class VulkanDescriptorRingStats {
    public long descriptorPoolBuildCount;
    public long descriptorPoolRebuildCount;
    public long descriptorRingReuseHitCount;
    public long descriptorRingGrowthRebuildCount;
    public long descriptorRingSteadyRebuildCount;
    public long descriptorRingPoolReuseCount;
    public long descriptorRingPoolResetFailureCount;
    public long descriptorRingCapBypassCount;
    public int descriptorRingSetCapacity;
    public int descriptorRingPeakSetCapacity;
    public int descriptorRingActiveSetCount;
    public int descriptorRingWasteSetCount;
    public int descriptorRingPeakWasteSetCount;
    public int descriptorRingMaxSetCapacity = 4096;
}
