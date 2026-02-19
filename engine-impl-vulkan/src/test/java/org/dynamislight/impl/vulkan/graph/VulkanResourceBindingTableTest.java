package org.dynamislight.impl.vulkan.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.dynamislight.spi.render.RenderFeatureCapability;
import org.dynamislight.spi.render.RenderFeatureContract;
import org.dynamislight.spi.render.RenderPassContribution;
import org.dynamislight.spi.render.RenderPassPhase;
import org.dynamislight.spi.render.RenderResourceType;
import org.junit.jupiter.api.Test;

import static org.lwjgl.vulkan.VK10.VK_FORMAT_B8G8R8A8_SRGB;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

class VulkanResourceBindingTableTest {
    private final VulkanRenderGraphCompiler compiler = new VulkanRenderGraphCompiler();

    @Test
    void resolvesAllGraphResourcesAfterPopulation() {
        VulkanRenderGraphPlan graphPlan = compile(
                List.of(
                        capability("shadow", pass("shadow", RenderPassPhase.PRE_MAIN, List.of(), List.of("shadow_depth"))),
                        capability("main", pass("main", RenderPassPhase.MAIN, List.of("shadow_depth"), List.of("scene_color", "depth"))),
                        capability("post", pass("post", RenderPassPhase.POST_MAIN, List.of("scene_color", "depth"), List.of("resolved_color")))
                ),
                List.of(
                        new VulkanImportedResource(
                                "history_color",
                                RenderResourceType.SAMPLED_IMAGE,
                                VulkanImportedResource.ResourceLifetime.PERSISTENT,
                                VulkanImportedResource.ResourceProvider.PREVIOUS_FRAME
                        )
                )
        );

        VulkanResourceBindingTable table = new VulkanResourceBindingTable()
                .bind("shadow_depth", 11L, VK_FORMAT_D32_SFLOAT, VK_IMAGE_ASPECT_DEPTH_BIT, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .bind("scene_color", 12L, VK_FORMAT_B8G8R8A8_SRGB, VK_IMAGE_ASPECT_COLOR_BIT, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .bind("depth", 13L, VK_FORMAT_D32_SFLOAT, VK_IMAGE_ASPECT_DEPTH_BIT, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .bind("resolved_color", 14L, VK_FORMAT_B8G8R8A8_SRGB, VK_IMAGE_ASPECT_COLOR_BIT, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .bind("history_color", 15L, VK_FORMAT_B8G8R8A8_SRGB, VK_IMAGE_ASPECT_COLOR_BIT, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

        assertTrue(table.unboundResources(graphPlan).isEmpty());
        assertTrue(table.invalidBindings(graphPlan).isEmpty());
    }

    @Test
    void reportsMissingBindings() {
        VulkanRenderGraphPlan graphPlan = compile(
                List.of(capability("main", pass("main", RenderPassPhase.MAIN, List.of("foo"), List.of("scene_color")))),
                List.of()
        );
        VulkanResourceBindingTable table = new VulkanResourceBindingTable()
                .bind("scene_color", 12L, VK_FORMAT_B8G8R8A8_SRGB, VK_IMAGE_ASPECT_COLOR_BIT, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

        assertEquals(List.of("foo"), table.unboundResources(graphPlan).stream().toList());
    }

    @Test
    void tracksImageLayoutUpdates() {
        VulkanResourceBindingTable table = new VulkanResourceBindingTable()
                .bind("scene_color", 12L, VK_FORMAT_B8G8R8A8_SRGB, VK_IMAGE_ASPECT_COLOR_BIT, 0);

        table.updateLayout("scene_color", VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        assertEquals(
                VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                table.resolveImage("scene_color").currentLayout()
        );
        table.updateLayout("scene_color", VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        assertEquals(
                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                table.resolveImage("scene_color").currentLayout()
        );
    }

    @Test
    void enforcesImageBufferTypeSafety() {
        VulkanResourceBindingTable table = new VulkanResourceBindingTable()
                .bindBuffer("probe_metadata", RenderResourceType.STORAGE_BUFFER, 21L, 1024L);

        assertThrows(IllegalStateException.class, () -> table.resolveImage("probe_metadata"));
        assertEquals(21L, table.resolveBuffer("probe_metadata").vkBufferHandle());
    }

    @Test
    void flagsInvalidHandleBinding() {
        VulkanRenderGraphPlan graphPlan = compile(
                List.of(capability("main", pass("main", RenderPassPhase.MAIN, List.of(), List.of("scene_color")))),
                List.of()
        );
        VulkanResourceBindingTable table = new VulkanResourceBindingTable()
                .bind("scene_color", VK_NULL_HANDLE, VK_FORMAT_B8G8R8A8_SRGB, VK_IMAGE_ASPECT_COLOR_BIT, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

        assertFalse(table.invalidBindings(graphPlan).isEmpty());
    }

    private VulkanRenderGraphPlan compile(List<RenderFeatureCapability> capabilities, List<VulkanImportedResource> imports) {
        return compiler.compile(capabilities, imports);
    }

    private static RenderFeatureCapability capability(String featureId, RenderPassContribution pass) {
        RenderFeatureContract contract = new RenderFeatureContract(featureId, "v1", List.of(pass), List.of(), List.of(), List.of());
        return () -> contract;
    }

    private static RenderPassContribution pass(String passId, RenderPassPhase phase, List<String> reads, List<String> writes) {
        return new RenderPassContribution(passId, phase, reads, writes, false);
    }
}
