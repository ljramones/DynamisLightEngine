package org.dynamislight.impl.vulkan.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.dynamislight.impl.vulkan.command.VulkanRuntimeBarrierTrace;
import org.junit.jupiter.api.Test;

class VulkanRenderGraphBarrierEquivalenceTest {
    @Test
    void reportsEquivalentWhenSignaturesMatch() {
        VulkanRenderGraphBarrierPlan planned = new VulkanRenderGraphBarrierPlan(List.of(
                new VulkanRenderGraphBarrier(
                        "scene_color",
                        "main#0:write",
                        "post#1:read",
                        VulkanRenderGraphBarrierHazardType.READ_AFTER_WRITE,
                        1,
                        2,
                        3,
                        4,
                        5,
                        6,
                        false
                )
        ));
        List<VulkanRuntimeBarrierTrace.ImageBarrierEvent> runtime = List.of(
                new VulkanRuntimeBarrierTrace.ImageBarrierEvent(1, 2, 3, 4, 5, 6, 17L)
        );

        VulkanRenderGraphBarrierEquivalence.Result result = VulkanRenderGraphBarrierEquivalence.compare(planned, runtime);

        assertTrue(result.equivalent());
        assertTrue(result.mismatches().isEmpty());
    }

    @Test
    void reportsMismatchWhenCountsDiffer() {
        VulkanRenderGraphBarrierPlan planned = new VulkanRenderGraphBarrierPlan(List.of(
                new VulkanRenderGraphBarrier(
                        "scene_color",
                        "main#0:write",
                        "post#1:read",
                        VulkanRenderGraphBarrierHazardType.READ_AFTER_WRITE,
                        1,
                        2,
                        3,
                        4,
                        5,
                        6,
                        false
                )
        ));

        VulkanRenderGraphBarrierEquivalence.Result result = VulkanRenderGraphBarrierEquivalence.compare(planned, List.of());

        assertFalse(result.equivalent());
        assertFalse(result.mismatches().isEmpty());
    }

    @Test
    void skipsExecutionOnlyBarriersFromEquivalence() {
        VulkanRenderGraphBarrierPlan planned = new VulkanRenderGraphBarrierPlan(List.of(
                new VulkanRenderGraphBarrier(
                        "history",
                        "a",
                        "b",
                        VulkanRenderGraphBarrierHazardType.WRITE_AFTER_READ,
                        10,
                        11,
                        0,
                        0,
                        -1,
                        -1,
                        true
                )
        ));

        VulkanRenderGraphBarrierEquivalence.Result result = VulkanRenderGraphBarrierEquivalence.compare(planned, List.of());

        assertTrue(result.equivalent());
    }
}
