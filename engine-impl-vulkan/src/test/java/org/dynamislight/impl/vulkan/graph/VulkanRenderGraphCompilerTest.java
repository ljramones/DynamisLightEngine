package org.dynamislight.impl.vulkan.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.dynamislight.spi.render.RenderFeatureCapability;
import org.dynamislight.spi.render.RenderFeatureContract;
import org.dynamislight.spi.render.RenderPassContribution;
import org.dynamislight.spi.render.RenderPassPhase;
import org.dynamislight.spi.render.RenderResourceType;
import org.junit.jupiter.api.Test;

class VulkanRenderGraphCompilerTest {
    private final VulkanRenderGraphCompiler compiler = new VulkanRenderGraphCompiler();

    @Test
    void linearChainProducesExpectedOrder() {
        RenderFeatureCapability shadow = capability("feature.shadow", pass("shadow", RenderPassPhase.PRE_MAIN, List.of(), List.of("shadow_map")));
        RenderFeatureCapability main = capability("feature.main", pass("main", RenderPassPhase.MAIN, List.of("shadow_map"), List.of("scene_color", "depth")));
        RenderFeatureCapability post = capability("feature.post", pass("post", RenderPassPhase.POST_MAIN, List.of("scene_color"), List.of("resolved_color")));

        VulkanRenderGraphPlan plan = compiler.compile(List.of(post, main, shadow), List.of());

        assertFalse(plan.hasErrors());
        assertEquals(List.of(
                "feature.shadow:shadow#0",
                "feature.main:main#0",
                "feature.post:post#0"
        ), plan.orderedNodes().stream().map(VulkanRenderGraphNode::nodeId).toList());
    }

    @Test
    void importedResourceDoesNotRequirePassProducer() {
        RenderFeatureCapability main = capability(
                "feature.main",
                pass("main", RenderPassPhase.MAIN, List.of("probe_metadata", "scene_color"), List.of("resolved_color"))
        );
        List<VulkanImportedResource> imports = List.of(
                new VulkanImportedResource(
                        "probe_metadata",
                        RenderResourceType.STORAGE_BUFFER,
                        VulkanImportedResource.ResourceLifetime.PER_FRAME,
                        VulkanImportedResource.ResourceProvider.CPU_UPLOAD
                ),
                new VulkanImportedResource(
                        "scene_color",
                        RenderResourceType.SAMPLED_IMAGE,
                        VulkanImportedResource.ResourceLifetime.PER_FRAME,
                        VulkanImportedResource.ResourceProvider.EXTERNAL_SYSTEM
                )
        );

        VulkanRenderGraphPlan plan = compiler.compile(List.of(main), imports);

        assertFalse(plan.hasErrors());
        assertEquals(2, plan.importedResources().size());
    }

    @Test
    void reportsMissingProducerWhenResourceNotImportedOrWritten() {
        RenderFeatureCapability main = capability(
                "feature.main",
                pass("main", RenderPassPhase.MAIN, List.of("missing_buffer"), List.of("resolved_color"))
        );

        VulkanRenderGraphPlan plan = compiler.compile(List.of(main), List.of());

        assertTrue(plan.hasErrors());
        assertTrue(plan.validationIssues().stream().anyMatch(i ->
                i.code().equals("MISSING_PRODUCER") && i.message().contains("missing_buffer")));
    }

    @Test
    void reportsCycleForMutualDependency() {
        RenderFeatureCapability a = capability(
                "feature.a",
                pass("pass_a", RenderPassPhase.MAIN, List.of("y"), List.of("x"))
        );
        RenderFeatureCapability b = capability(
                "feature.b",
                pass("pass_b", RenderPassPhase.POST_MAIN, List.of("x"), List.of("y"))
        );

        VulkanRenderGraphPlan plan = compiler.compile(List.of(a, b), List.of());

        assertTrue(plan.hasErrors());
        assertTrue(plan.validationIssues().stream().anyMatch(i -> i.code().equals("CYCLE_DETECTED")));
    }

    @Test
    void allowsSelfDependencyReadWriteSameResourceInPassGroup() {
        RenderFeatureCapability moment = capability(
                "feature.moment",
                pass("moment_blit", RenderPassPhase.PRE_MAIN, List.of("moment_atlas"), List.of("moment_atlas"))
        );

        VulkanRenderGraphPlan plan = compiler.compile(List.of(moment), List.of());

        assertFalse(plan.hasErrors());
        var access = plan.resourceAccessOrder().get("moment_atlas");
        assertEquals(1, access.size());
        assertEquals(VulkanRenderGraphResourceAccessType.READ_WRITE, access.getFirst().accessType());
    }

    @Test
    void persistentPreviousFrameImportDoesNotCreateCycle() {
        RenderFeatureCapability resolve = capability(
                "feature.taa",
                pass("taa_resolve", RenderPassPhase.POST_MAIN, List.of("taa_history_prev", "scene_color"), List.of("taa_history_next", "resolved_color"))
        );

        List<VulkanImportedResource> imports = List.of(
                new VulkanImportedResource(
                        "taa_history_prev",
                        RenderResourceType.SAMPLED_IMAGE,
                        VulkanImportedResource.ResourceLifetime.PERSISTENT,
                        VulkanImportedResource.ResourceProvider.PREVIOUS_FRAME
                ),
                new VulkanImportedResource(
                        "scene_color",
                        RenderResourceType.SAMPLED_IMAGE,
                        VulkanImportedResource.ResourceLifetime.PER_FRAME,
                        VulkanImportedResource.ResourceProvider.EXTERNAL_SYSTEM
                )
        );

        VulkanRenderGraphPlan plan = compiler.compile(List.of(resolve), imports);

        assertFalse(plan.hasErrors());
        assertTrue(plan.importedResources().stream().anyMatch(r ->
                r.resourceName().equals("taa_history_prev") && r.provider() == VulkanImportedResource.ResourceProvider.PREVIOUS_FRAME));
    }

    @Test
    void tieBreakUsesPhaseAndInsertionOrderForIndependentNodes() {
        RenderFeatureCapability shadowA = capability("feature.shadow.a", pass("shadow_a", RenderPassPhase.PRE_MAIN, List.of(), List.of("cascade_a")));
        RenderFeatureCapability shadowB = capability("feature.shadow.b", pass("shadow_b", RenderPassPhase.PRE_MAIN, List.of(), List.of("cascade_b")));
        RenderFeatureCapability main = capability("feature.main", pass("main", RenderPassPhase.MAIN, List.of("cascade_a", "cascade_b"), List.of("scene_color")));

        VulkanRenderGraphPlan plan = compiler.compile(List.of(shadowA, shadowB, main), List.of());

        assertFalse(plan.hasErrors());
        assertEquals(List.of(
                "feature.shadow.a:shadow_a#0",
                "feature.shadow.b:shadow_b#0",
                "feature.main:main#0"
        ), plan.orderedNodes().stream().map(VulkanRenderGraphNode::nodeId).toList());
    }

    @Test
    void dependencyOverridesPhaseHintOrdering() {
        RenderFeatureCapability postProducer = capability(
                "feature.post_producer",
                pass("late_source", RenderPassPhase.POST_MAIN, List.of(), List.of("intermediate_x"))
        );
        RenderFeatureCapability mainConsumer = capability(
                "feature.main_consumer",
                pass("main_consumer", RenderPassPhase.MAIN, List.of("intermediate_x"), List.of("scene_color"))
        );

        VulkanRenderGraphPlan plan = compiler.compile(List.of(mainConsumer, postProducer), List.of());

        assertFalse(plan.hasErrors());
        assertEquals(List.of(
                "feature.post_producer:late_source#0",
                "feature.main_consumer:main_consumer#0"
        ), plan.orderedNodes().stream().map(VulkanRenderGraphNode::nodeId).toList());
    }

    @Test
    void duplicateWriterAcrossDifferentPassGroupsIsError() {
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
    void resourceAccessOrderDiagnosticsAreResourceCentric() {
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

        VulkanRenderGraphPlan plan = compiler.compile(List.of(post, main, pre), List.of());

        var depthSeq = plan.resourceAccessOrder().get("depth").stream()
                .map(e -> e.nodeId() + ":" + e.accessType().name())
                .collect(Collectors.toList());
        assertEquals(List.of(
                "feature.pre:depth_pre#0:WRITE",
                "feature.main:main#0:READ"
        ), depthSeq);
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
