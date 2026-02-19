package org.dynamislight.impl.vulkan.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.dynamislight.spi.render.RenderCapabilityValidationIssue;
import org.dynamislight.spi.render.RenderFeatureCapability;
import org.dynamislight.spi.render.RenderFeatureContract;
import org.dynamislight.spi.render.RenderPassContribution;
import org.dynamislight.spi.render.RenderPassPhase;
import org.junit.jupiter.api.Test;

class VulkanRenderGraphCompilerTest {
    private final VulkanRenderGraphCompiler compiler = new VulkanRenderGraphCompiler();

    @Test
    void ordersNodesByPhasePassAndFeatureDeterministically() {
        RenderFeatureCapability preA = capability("feature.pre.a", pass("pre_a", RenderPassPhase.PRE_MAIN, List.of(), List.of("pre_a")));
        RenderFeatureCapability mainB = capability("feature.main.b", pass("main", RenderPassPhase.MAIN, List.of("pre_a"), List.of("main_b")));
        RenderFeatureCapability mainA = capability("feature.main.a", pass("main", RenderPassPhase.MAIN, List.of("pre_a"), List.of("main_a")));
        RenderFeatureCapability post = capability("feature.post", pass("post", RenderPassPhase.POST_MAIN, List.of("main_b"), List.of("resolved")));

        VulkanRenderGraphPlan plan = compiler.compile(List.of(post, mainB, preA, mainA), Set.of());

        List<String> ordered = plan.orderedNodes().stream().map(VulkanRenderGraphNode::nodeId).toList();
        assertEquals(List.of(
                "feature.pre.a:pre_a#0",
                "feature.main.a:main#0",
                "feature.main.b:main#0",
                "feature.post:post#0"
        ), ordered);
    }

    @Test
    void reportsMissingProducerWhenResourceNotExternalOrWritten() {
        RenderFeatureCapability post = capability(
                "feature.post",
                pass("post", RenderPassPhase.POST_MAIN, List.of("scene_color", "missing_buffer"), List.of("resolved_color"))
        );

        VulkanRenderGraphPlan plan = compiler.compile(List.of(post), Set.of("scene_color"));

        assertTrue(plan.hasErrors());
        assertTrue(plan.validationIssues().stream().anyMatch(i ->
                i.code().equals("MISSING_PRODUCER") && i.message().contains("missing_buffer")));
    }

    @Test
    void allowsDuplicateWritersInsideSamePassGroup() {
        RenderFeatureCapability tonemap = capability(
                "vulkan.post.tonemap",
                pass("post_composite", RenderPassPhase.POST_MAIN, List.of("scene_color"), List.of("resolved_color"))
        );
        RenderFeatureCapability bloom = capability(
                "vulkan.post.bloom",
                pass("post_composite", RenderPassPhase.POST_MAIN, List.of("scene_color"), List.of("resolved_color"))
        );

        VulkanRenderGraphPlan plan = compiler.compile(List.of(tonemap, bloom), Set.of("scene_color"));

        assertFalse(plan.hasErrors());
        assertTrue(plan.validationIssues().stream().noneMatch(i -> i.code().equals("DUPLICATE_WRITER")));
    }

    @Test
    void reportsDuplicateWritersAcrossDifferentPassGroups() {
        RenderFeatureCapability first = capability(
                "feature.first",
                pass("main_a", RenderPassPhase.MAIN, List.of("scene_color"), List.of("resolved_color"))
        );
        RenderFeatureCapability second = capability(
                "feature.second",
                pass("main_b", RenderPassPhase.MAIN, List.of("scene_color"), List.of("resolved_color"))
        );

        VulkanRenderGraphPlan plan = compiler.compile(List.of(first, second), Set.of("scene_color"));

        assertTrue(plan.hasErrors());
        assertTrue(plan.validationIssues().stream().anyMatch(i -> i.code().equals("DUPLICATE_WRITER")));
    }

    @Test
    void computesResourceLifetimesAcrossOrderedNodes() {
        RenderFeatureCapability pre = capability(
                "feature.pre",
                pass("depth_pre", RenderPassPhase.PRE_MAIN, List.of(), List.of("depth"))
        );
        RenderFeatureCapability main = capability(
                "feature.main",
                pass("main", RenderPassPhase.MAIN, List.of("depth"), List.of("scene_color"))
        );
        RenderFeatureCapability post = capability(
                "feature.post",
                pass("post", RenderPassPhase.POST_MAIN, List.of("scene_color"), List.of("resolved_color"))
        );

        VulkanRenderGraphPlan plan = compiler.compile(List.of(post, main, pre), Set.of());

        var byName = plan.resourceLifetimes().stream().collect(java.util.stream.Collectors.toMap(
                VulkanRenderGraphResourceLifetime::resourceName,
                lifetime -> lifetime
        ));

        assertEquals(0, byName.get("depth").firstNodeIndex());
        assertEquals(1, byName.get("depth").lastNodeIndex());
        assertEquals(1, byName.get("scene_color").firstNodeIndex());
        assertEquals(2, byName.get("scene_color").lastNodeIndex());
        assertEquals(2, byName.get("resolved_color").firstNodeIndex());
        assertEquals(2, byName.get("resolved_color").lastNodeIndex());
    }

    private static RenderFeatureCapability capability(String featureId, RenderPassContribution pass) {
        RenderFeatureContract contract = new RenderFeatureContract(
                featureId,
                "v1",
                List.of(pass),
                List.of(),
                List.of(),
                List.of()
        );
        return () -> contract;
    }

    private static RenderPassContribution pass(String passId, RenderPassPhase phase, List<String> reads, List<String> writes) {
        return new RenderPassContribution(passId, phase, reads, writes, false);
    }
}
