package org.dynamislight.impl.vulkan.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.dynamislight.impl.vulkan.command.VulkanRuntimeBarrierTrace;

/**
 * Semantic comparison helper between graph-derived and runtime-recorded barriers.
 */
public final class VulkanRenderGraphBarrierEquivalence {
    private VulkanRenderGraphBarrierEquivalence() {
    }

    public static Result compare(
            VulkanRenderGraphBarrierPlan planned,
            List<VulkanRuntimeBarrierTrace.ImageBarrierEvent> runtimeEvents
    ) {
        Map<String, Integer> plannedCounts = new TreeMap<>();
        Map<String, Integer> runtimeCounts = new TreeMap<>();

        if (planned != null) {
            for (VulkanRenderGraphBarrier barrier : planned.barriers()) {
                if (barrier == null || barrier.executionDependencyOnly()) {
                    continue;
                }
                if (barrier.oldLayout() < 0 && barrier.newLayout() < 0) {
                    continue;
                }
                plannedCounts.merge(barrier.signature(), 1, Integer::sum);
            }
        }

        if (runtimeEvents != null) {
            for (VulkanRuntimeBarrierTrace.ImageBarrierEvent event : runtimeEvents) {
                if (event == null) {
                    continue;
                }
                String signature = event.srcStageMask() + ":"
                        + event.dstStageMask() + ":"
                        + event.srcAccessMask() + ":"
                        + event.dstAccessMask() + ":"
                        + event.oldLayout() + ":"
                        + event.newLayout();
                runtimeCounts.merge(signature, 1, Integer::sum);
            }
        }

        List<String> mismatches = new ArrayList<>();
        for (String signature : union(plannedCounts, runtimeCounts)) {
            int plannedCount = plannedCounts.getOrDefault(signature, 0);
            int runtimeCount = runtimeCounts.getOrDefault(signature, 0);
            if (plannedCount != runtimeCount) {
                mismatches.add(signature + " planned=" + plannedCount + " runtime=" + runtimeCount);
            }
        }

        return new Result(mismatches.isEmpty(), mismatches);
    }

    private static List<String> union(Map<String, Integer> a, Map<String, Integer> b) {
        java.util.Set<String> keys = new java.util.TreeSet<>();
        keys.addAll(a.keySet());
        keys.addAll(b.keySet());
        return List.copyOf(keys);
    }

    public record Result(boolean equivalent, List<String> mismatches) {
        public Result {
            mismatches = mismatches == null ? List.of() : List.copyOf(mismatches);
        }
    }
}
