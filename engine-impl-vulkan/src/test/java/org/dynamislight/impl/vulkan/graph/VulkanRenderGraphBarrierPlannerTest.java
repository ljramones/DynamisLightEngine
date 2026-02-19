package org.dynamislight.impl.vulkan.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.dynamislight.spi.render.RenderFeatureCapability;
import org.dynamislight.spi.render.RenderFeatureContract;
import org.dynamislight.spi.render.RenderPassContribution;
import org.dynamislight.spi.render.RenderPassPhase;
import org.dynamislight.spi.render.RenderResourceType;
import org.junit.jupiter.api.Test;

import static org.lwjgl.vulkan.VK10.VK_ACCESS_HOST_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_HOST_BIT;

class VulkanRenderGraphBarrierPlannerTest {
    private final VulkanRenderGraphCompiler compiler = new VulkanRenderGraphCompiler();
    private final VulkanRenderGraphBarrierPlanner planner = new VulkanRenderGraphBarrierPlanner();

    @Test
    void generatesRawBarrierForWriteThenRead() {
        RenderFeatureCapability main = capability("feature.main", pass("main", RenderPassPhase.MAIN, List.of(), List.of("scene_color")));
        RenderFeatureCapability post = capability("feature.post", pass("post", RenderPassPhase.POST_MAIN, List.of("scene_color"), List.of("resolved")));

        VulkanRenderGraphPlan graphPlan = compiler.compile(List.of(main, post), List.of());
        VulkanRenderGraphBarrierPlan barrierPlan = planner.plan(graphPlan);

        assertTrue(barrierPlan.barriers().stream().anyMatch(b ->
                b.resourceName().equals("scene_color")
                        && b.hazardType() == VulkanRenderGraphBarrierHazardType.READ_AFTER_WRITE
                        && !b.executionDependencyOnly()
                        && b.oldLayout() == VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL
                        && b.newLayout() == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL));
    }

    @Test
    void skipsBarrierForReadAfterRead() {
        RenderFeatureCapability a = capability("feature.a", pass("a", RenderPassPhase.MAIN, List.of("probe_ssbo"), List.of()));
        RenderFeatureCapability b = capability("feature.b", pass("b", RenderPassPhase.POST_MAIN, List.of("probe_ssbo"), List.of("resolved")));
        List<VulkanImportedResource> imports = List.of(new VulkanImportedResource(
                "probe_ssbo",
                RenderResourceType.STORAGE_BUFFER,
                VulkanImportedResource.ResourceLifetime.PER_FRAME,
                VulkanImportedResource.ResourceProvider.CPU_UPLOAD
        ));

        VulkanRenderGraphPlan graphPlan = compiler.compile(List.of(a, b), imports);
        VulkanRenderGraphBarrierPlan barrierPlan = planner.plan(graphPlan);

        long importRead = barrierPlan.barriers().stream()
                .filter(it -> it.resourceName().equals("probe_ssbo") && it.hazardType() == VulkanRenderGraphBarrierHazardType.IMPORT_TO_READ)
                .count();
        // only import->first-read barrier should exist; no read->read barrier
        assertEquals(1, importRead);
    }

    @Test
    void importedCpuUploadGeneratesHostToShaderBarrier() {
        RenderFeatureCapability main = capability("feature.main", pass("main", RenderPassPhase.MAIN, List.of("probe_ssbo"), List.of("resolved")));
        List<VulkanImportedResource> imports = List.of(new VulkanImportedResource(
                "probe_ssbo",
                RenderResourceType.STORAGE_BUFFER,
                VulkanImportedResource.ResourceLifetime.PER_FRAME,
                VulkanImportedResource.ResourceProvider.CPU_UPLOAD
        ));

        VulkanRenderGraphPlan graphPlan = compiler.compile(List.of(main), imports);
        VulkanRenderGraphBarrierPlan barrierPlan = planner.plan(graphPlan);

        assertTrue(barrierPlan.barriers().stream().anyMatch(b ->
                b.resourceName().equals("probe_ssbo")
                        && b.hazardType() == VulkanRenderGraphBarrierHazardType.IMPORT_TO_READ
                        && b.srcStageMask() == VK_PIPELINE_STAGE_HOST_BIT
                        && b.srcAccessMask() == VK_ACCESS_HOST_WRITE_BIT
                        && b.dstStageMask() == VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
                        && b.dstAccessMask() == VK_ACCESS_SHADER_READ_BIT));
    }

    @Test
    void bufferResourceHasNoLayoutTransition() {
        RenderFeatureCapability main = capability("feature.main", pass("main", RenderPassPhase.MAIN, List.of("scene_ubo"), List.of("resolved")));
        List<VulkanImportedResource> imports = List.of(new VulkanImportedResource(
                "scene_ubo",
                RenderResourceType.UNIFORM_BUFFER,
                VulkanImportedResource.ResourceLifetime.PER_FRAME,
                VulkanImportedResource.ResourceProvider.CPU_UPLOAD
        ));

        VulkanRenderGraphPlan graphPlan = compiler.compile(List.of(main), imports);
        VulkanRenderGraphBarrierPlan barrierPlan = planner.plan(graphPlan);

        VulkanRenderGraphBarrier barrier = barrierPlan.barriers().stream()
                .filter(b -> b.resourceName().equals("scene_ubo"))
                .findFirst()
                .orElseThrow();

        assertEquals(-1, barrier.oldLayout());
        assertEquals(-1, barrier.newLayout());
    }

    @Test
    void emitsHumanReadableDebugDump() {
        RenderFeatureCapability main = capability("feature.main", pass("main", RenderPassPhase.MAIN, List.of(), List.of("scene_color")));
        RenderFeatureCapability post = capability("feature.post", pass("post", RenderPassPhase.POST_MAIN, List.of("scene_color"), List.of("resolved")));

        VulkanRenderGraphBarrierPlan barrierPlan = planner.plan(compiler.compile(List.of(main, post), List.of()));
        String dump = barrierPlan.debugDump();

        assertFalse(dump.isBlank());
        assertTrue(dump.contains("scene_color"));
    }

    private static RenderFeatureCapability capability(String featureId, RenderPassContribution pass) {
        RenderFeatureContract contract = new RenderFeatureContract(featureId, "v1", List.of(pass), List.of(), List.of(), List.of());
        return () -> contract;
    }

    private static RenderPassContribution pass(String passId, RenderPassPhase phase, List<String> reads, List<String> writes) {
        return new RenderPassContribution(passId, phase, reads, writes, false);
    }
}
