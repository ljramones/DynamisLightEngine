package org.dynamislight.impl.vulkan.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.impl.vulkan.graph.VulkanExecutableRenderGraphPlan;
import org.dynamislight.impl.vulkan.graph.VulkanRenderGraphBarrier;
import org.dynamislight.impl.vulkan.graph.VulkanRenderGraphBarrierHazardType;
import org.dynamislight.impl.vulkan.graph.VulkanRenderGraphBarrierPlan;
import org.dynamislight.impl.vulkan.graph.VulkanRenderGraphNode;
import org.dynamislight.impl.vulkan.graph.VulkanRenderGraphPlan;
import org.dynamislight.impl.vulkan.graph.VulkanResourceBindingTable;
import org.dynamislight.spi.render.RenderPassPhase;
import org.junit.jupiter.api.Test;

import static org.lwjgl.vulkan.VK10.VK_FORMAT_B8G8R8A8_SRGB;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

class VulkanRenderGraphExecutorTest {
    @Test
    void executesCallbacksInCompiledNodeOrder() throws EngineException {
        List<String> order = new ArrayList<>();
        VulkanExecutableRenderGraphPlan plan = plan(
                List.of(
                        node("feature.shadow:shadow_passes#0", "shadow_passes", RenderPassPhase.PRE_MAIN),
                        node("feature.main:main_geometry#0", "main_geometry", RenderPassPhase.MAIN),
                        node("feature.post:post_composite#0", "post_composite", RenderPassPhase.POST_MAIN)
                ),
                List.of(),
                mapOf(
                        "feature.shadow:shadow_passes#0", () -> order.add("shadow"),
                        "feature.main:main_geometry#0", () -> order.add("main"),
                        "feature.post:post_composite#0", () -> order.add("post")
                )
        );
        VulkanRenderGraphExecutor executor = new VulkanRenderGraphExecutor(false);
        executor.execute(null, null, plan, new VulkanResourceBindingTable());
        assertEquals(List.of("shadow", "main", "post"), order);
    }

    @Test
    void updatesImageLayoutTrackingWhenBarrierTransitionsLayout() throws EngineException {
        String nodeId = "feature.main:main_geometry#0";
        VulkanExecutableRenderGraphPlan plan = plan(
                List.of(node(nodeId, "main_geometry", RenderPassPhase.MAIN)),
                List.of(new VulkanRenderGraphBarrier(
                        "scene_color",
                        "feature.shadow:shadow_passes#0#0:write",
                        nodeId + "#1:read",
                        VulkanRenderGraphBarrierHazardType.READ_AFTER_WRITE,
                        0,
                        0,
                        0,
                        0,
                        VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                        VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                        false
                )),
                mapOf(nodeId, () -> { })
        );

        VulkanResourceBindingTable table = new VulkanResourceBindingTable()
                .bind("scene_color", 21L, VK_FORMAT_B8G8R8A8_SRGB, VK_IMAGE_ASPECT_COLOR_BIT, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

        VulkanRenderGraphExecutor executor = new VulkanRenderGraphExecutor(false);
        executor.execute(null, null, plan, table);

        assertEquals(
                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                table.resolveImage("scene_color").currentLayout()
        );
    }

    @Test
    void throwsOnImageLayoutMismatch() {
        String nodeId = "feature.main:main_geometry#0";
        VulkanExecutableRenderGraphPlan plan = plan(
                List.of(node(nodeId, "main_geometry", RenderPassPhase.MAIN)),
                List.of(new VulkanRenderGraphBarrier(
                        "scene_color",
                        "feature.shadow:shadow_passes#0#0:write",
                        nodeId + "#1:read",
                        VulkanRenderGraphBarrierHazardType.READ_AFTER_WRITE,
                        0,
                        0,
                        0,
                        0,
                        VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                        VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                        false
                )),
                mapOf(nodeId, () -> { })
        );
        VulkanResourceBindingTable table = new VulkanResourceBindingTable()
                .bind("scene_color", 21L, VK_FORMAT_B8G8R8A8_SRGB, VK_IMAGE_ASPECT_COLOR_BIT, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

        VulkanRenderGraphExecutor executor = new VulkanRenderGraphExecutor(false);
        assertThrows(EngineException.class, () -> executor.execute(null, null, plan, table));
    }

    @Test
    void destinationNodeIdParsesPlannerAccessId() {
        assertEquals(
                "feature.main:main_geometry#0",
                VulkanRenderGraphExecutor.destinationNodeId("feature.main:main_geometry#0#12:read")
        );
    }

    private static VulkanRenderGraphNode node(String nodeId, String passId, RenderPassPhase phase) {
        return new VulkanRenderGraphNode(nodeId, "feature", passId, phase, List.of(), List.of(), false);
    }

    private static VulkanExecutableRenderGraphPlan plan(
            List<VulkanRenderGraphNode> nodes,
            List<VulkanRenderGraphBarrier> barriers,
            Map<String, Runnable> callbacks
    ) {
        VulkanRenderGraphPlan metadataPlan = new VulkanRenderGraphPlan(nodes, List.of(), List.of(), List.of());
        VulkanRenderGraphBarrierPlan barrierPlan = new VulkanRenderGraphBarrierPlan(barriers);
        return new VulkanExecutableRenderGraphPlan(metadataPlan, barrierPlan, callbacks);
    }

    private static Map<String, Runnable> mapOf(String nodeId, Runnable callback) {
        Map<String, Runnable> callbacks = new HashMap<>();
        callbacks.put(nodeId, callback);
        return callbacks;
    }

    private static Map<String, Runnable> mapOf(
            String n1, Runnable c1,
            String n2, Runnable c2,
            String n3, Runnable c3
    ) {
        Map<String, Runnable> callbacks = new HashMap<>();
        callbacks.put(n1, c1);
        callbacks.put(n2, c2);
        callbacks.put(n3, c3);
        return callbacks;
    }
}
