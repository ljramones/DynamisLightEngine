package org.dynamislight.impl.vulkan.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.dynamislight.spi.render.RenderFeatureCapability;
import org.dynamislight.spi.render.RenderFeatureContract;
import org.dynamislight.spi.render.RenderPassContribution;

/**
 * Compiles Vulkan-local executable pass declarations into metadata + callback plan.
 *
 * Execution remains external in B.3.2; callbacks are attached but not graph-dispatched yet.
 */
public final class VulkanExecutableRenderGraphPlanner {
    private final VulkanRenderGraphCompiler compiler;
    private final VulkanRenderGraphBarrierPlanner barrierPlanner;

    public VulkanExecutableRenderGraphPlanner() {
        this(new VulkanRenderGraphCompiler(), new VulkanRenderGraphBarrierPlanner());
    }

    VulkanExecutableRenderGraphPlanner(
            VulkanRenderGraphCompiler compiler,
            VulkanRenderGraphBarrierPlanner barrierPlanner
    ) {
        this.compiler = compiler == null ? new VulkanRenderGraphCompiler() : compiler;
        this.barrierPlanner = barrierPlanner == null ? new VulkanRenderGraphBarrierPlanner() : barrierPlanner;
    }

    public VulkanExecutableRenderGraphPlan compile(
            List<VulkanExecutablePassDeclaration> declarations,
            List<VulkanImportedResource> imports
    ) {
        Map<String, List<RenderPassContribution>> passByFeature = new LinkedHashMap<>();
        Map<String, List<Runnable>> callbackByFeature = new LinkedHashMap<>();

        if (declarations != null) {
            for (VulkanExecutablePassDeclaration declaration : declarations) {
                if (declaration == null || declaration.featureId().isBlank()) {
                    continue;
                }
                passByFeature.computeIfAbsent(declaration.featureId(), ignored -> new ArrayList<>())
                        .add(declaration.contribution());
                callbackByFeature.computeIfAbsent(declaration.featureId(), ignored -> new ArrayList<>())
                        .add(declaration.executeCallback());
            }
        }

        List<RenderFeatureCapability> capabilities = new ArrayList<>();
        Map<String, Runnable> callbackByNode = new LinkedHashMap<>();

        for (Map.Entry<String, List<RenderPassContribution>> entry : passByFeature.entrySet()) {
            String featureId = entry.getKey();
            List<RenderPassContribution> contributions = List.copyOf(entry.getValue());
            List<Runnable> callbacks = callbackByFeature.getOrDefault(featureId, List.of());

            for (int i = 0; i < contributions.size() && i < callbacks.size(); i++) {
                String nodeId = featureId + ":" + contributions.get(i).passId() + "#" + i;
                callbackByNode.put(nodeId, callbacks.get(i));
            }

            RenderFeatureContract contract = new RenderFeatureContract(
                    featureId,
                    "v1",
                    contributions,
                    List.of(),
                    List.of(),
                    List.of()
            );
            capabilities.add(() -> contract);
        }

        VulkanRenderGraphPlan metadataPlan = compiler.compile(capabilities, imports == null ? List.of() : imports);
        VulkanRenderGraphBarrierPlan barrierPlan = barrierPlanner.plan(metadataPlan);
        return new VulkanExecutableRenderGraphPlan(metadataPlan, barrierPlan, callbackByNode);
    }
}
