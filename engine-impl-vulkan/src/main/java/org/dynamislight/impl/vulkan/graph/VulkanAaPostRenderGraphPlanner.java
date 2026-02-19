package org.dynamislight.impl.vulkan.graph;

import java.util.Set;
import org.dynamislight.impl.vulkan.capability.VulkanAaPostCapabilityPlan;
import org.dynamislight.impl.vulkan.capability.VulkanAaPostCapabilityPlanner;

/**
 * Phase B bridge: compiles AA/post capability planner output into render-graph metadata.
 *
 * This planner is metadata-only and does not rewire runtime command recording yet.
 */
public final class VulkanAaPostRenderGraphPlanner {
    private static final Set<String> DEFAULT_EXTERNAL_INPUTS = Set.of(
            "scene_color",
            "velocity",
            "depth",
            "history_color",
            "history_velocity"
    );

    private final VulkanRenderGraphCompiler compiler;

    public VulkanAaPostRenderGraphPlanner() {
        this(new VulkanRenderGraphCompiler());
    }

    VulkanAaPostRenderGraphPlanner(VulkanRenderGraphCompiler compiler) {
        this.compiler = compiler == null ? new VulkanRenderGraphCompiler() : compiler;
    }

    public VulkanAaPostRenderGraphCompilation compile(VulkanAaPostCapabilityPlanner.PlanInput input) {
        return compile(input, DEFAULT_EXTERNAL_INPUTS);
    }

    public VulkanAaPostRenderGraphCompilation compile(
            VulkanAaPostCapabilityPlanner.PlanInput input,
            Set<String> externalInputs
    ) {
        VulkanAaPostCapabilityPlan capabilityPlan = VulkanAaPostCapabilityPlanner.plan(input);
        Set<String> externals = externalInputs == null ? DEFAULT_EXTERNAL_INPUTS : Set.copyOf(externalInputs);
        VulkanRenderGraphPlan graphPlan = compiler.compile(capabilityPlan.activeCapabilities(), externals);
        return new VulkanAaPostRenderGraphCompilation(capabilityPlan, graphPlan, externals);
    }

    public static Set<String> defaultExternalInputs() {
        return DEFAULT_EXTERNAL_INPUTS;
    }

    public record VulkanAaPostRenderGraphCompilation(
            VulkanAaPostCapabilityPlan capabilityPlan,
            VulkanRenderGraphPlan graphPlan,
            Set<String> externalInputs
    ) {
        public VulkanAaPostRenderGraphCompilation {
            externalInputs = externalInputs == null ? Set.of() : Set.copyOf(externalInputs);
        }
    }
}
