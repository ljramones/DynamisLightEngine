package org.dynamislight.impl.vulkan.graph;

import java.util.List;
import java.util.Set;
import org.dynamislight.impl.vulkan.capability.VulkanAaPostCapabilityPlan;
import org.dynamislight.impl.vulkan.capability.VulkanAaPostCapabilityPlanner;
import org.dynamislight.spi.render.RenderResourceType;

/**
 * Phase B bridge: compiles AA/post capability planner output into render-graph metadata.
 *
 * This planner is metadata-only and does not rewire runtime command recording yet.
 */
public final class VulkanAaPostRenderGraphPlanner {
    private static final List<VulkanImportedResource> DEFAULT_IMPORTS = List.of(
            new VulkanImportedResource(
                    "scene_color",
                    RenderResourceType.SAMPLED_IMAGE,
                    VulkanImportedResource.ResourceLifetime.PER_FRAME,
                    VulkanImportedResource.ResourceProvider.EXTERNAL_SYSTEM
            ),
            new VulkanImportedResource(
                    "velocity",
                    RenderResourceType.SAMPLED_IMAGE,
                    VulkanImportedResource.ResourceLifetime.PER_FRAME,
                    VulkanImportedResource.ResourceProvider.EXTERNAL_SYSTEM
            ),
            new VulkanImportedResource(
                    "depth",
                    RenderResourceType.SAMPLED_IMAGE,
                    VulkanImportedResource.ResourceLifetime.PER_FRAME,
                    VulkanImportedResource.ResourceProvider.EXTERNAL_SYSTEM
            ),
            new VulkanImportedResource(
                    "history_color",
                    RenderResourceType.SAMPLED_IMAGE,
                    VulkanImportedResource.ResourceLifetime.PERSISTENT,
                    VulkanImportedResource.ResourceProvider.PREVIOUS_FRAME
            ),
            new VulkanImportedResource(
                    "history_velocity",
                    RenderResourceType.SAMPLED_IMAGE,
                    VulkanImportedResource.ResourceLifetime.PERSISTENT,
                    VulkanImportedResource.ResourceProvider.PREVIOUS_FRAME
            )
    );

    private final VulkanRenderGraphCompiler compiler;

    public VulkanAaPostRenderGraphPlanner() {
        this(new VulkanRenderGraphCompiler());
    }

    VulkanAaPostRenderGraphPlanner(VulkanRenderGraphCompiler compiler) {
        this.compiler = compiler == null ? new VulkanRenderGraphCompiler() : compiler;
    }

    public VulkanAaPostRenderGraphCompilation compile(VulkanAaPostCapabilityPlanner.PlanInput input) {
        return compile(input, DEFAULT_IMPORTS);
    }

    public VulkanAaPostRenderGraphCompilation compile(
            VulkanAaPostCapabilityPlanner.PlanInput input,
            Set<String> externalInputs
    ) {
        List<VulkanImportedResource> imports = externalInputs == null
                ? DEFAULT_IMPORTS
                : externalInputs.stream()
                .map(name -> new VulkanImportedResource(
                        name,
                        RenderResourceType.ATTACHMENT,
                        VulkanImportedResource.ResourceLifetime.PER_FRAME,
                        VulkanImportedResource.ResourceProvider.EXTERNAL_SYSTEM
                ))
                .toList();
        return compile(input, imports);
    }

    public VulkanAaPostRenderGraphCompilation compile(
            VulkanAaPostCapabilityPlanner.PlanInput input,
            List<VulkanImportedResource> importedResources
    ) {
        VulkanAaPostCapabilityPlan capabilityPlan = VulkanAaPostCapabilityPlanner.plan(input);
        List<VulkanImportedResource> imports = importedResources == null ? DEFAULT_IMPORTS : List.copyOf(importedResources);
        VulkanRenderGraphPlan graphPlan = compiler.compile(capabilityPlan.activeCapabilities(), imports);
        return new VulkanAaPostRenderGraphCompilation(capabilityPlan, graphPlan, imports);
    }

    public static List<VulkanImportedResource> defaultImportedResources() {
        return DEFAULT_IMPORTS;
    }

    public static Set<String> defaultExternalInputs() {
        return DEFAULT_IMPORTS.stream().map(VulkanImportedResource::resourceName).collect(java.util.stream.Collectors.toSet());
    }

    public record VulkanAaPostRenderGraphCompilation(
            VulkanAaPostCapabilityPlan capabilityPlan,
            VulkanRenderGraphPlan graphPlan,
            List<VulkanImportedResource> importedResources
    ) {
        public VulkanAaPostRenderGraphCompilation {
            importedResources = importedResources == null ? List.of() : List.copyOf(importedResources);
        }

        public Set<String> externalInputs() {
            return importedResources.stream().map(VulkanImportedResource::resourceName).collect(java.util.stream.Collectors.toSet());
        }
    }
}
