package org.dynamislight.impl.vulkan.shader;

import java.util.ArrayList;
import java.util.List;
import org.dynamislight.impl.vulkan.capability.VulkanAaCapabilityDescriptorV2;
import org.dynamislight.impl.vulkan.capability.VulkanReflectionCapabilityDescriptorV2;
import org.dynamislight.impl.vulkan.capability.VulkanShadowCapabilityDescriptorV2;
import org.dynamislight.spi.render.RenderFeatureMode;

/**
 * Canonical module-body extraction from existing Vulkan monolithic shader sources.
 */
public final class VulkanCanonicalShaderModuleBodies {
    private VulkanCanonicalShaderModuleBodies() {
    }

    public static String shadowMainBody(RenderFeatureMode mode) {
        String source = VulkanMainFragmentShaderSource.mainFragment();
        boolean moment = mode != null
                && (VulkanShadowCapabilityDescriptorV2.MODE_VSM.id().equalsIgnoreCase(mode.id())
                || VulkanShadowCapabilityDescriptorV2.MODE_EVSM.id().equalsIgnoreCase(mode.id()));
        List<String> names = moment
                ? new ArrayList<>(List.of(
                        "reduceLightBleed",
                        "sampleMomentsWeighted",
                        "sampleMomentsWideBilateral",
                        "sampleMomentsRingBilateral",
                        "sampleMomentBounds",
                        "clampMomentsToBounds",
                        "momentVarianceConfidence",
                        "momentVisibilityApprox",
                        "evsmVisibilityApprox"
                ))
                : new ArrayList<>(List.of("finalizeShadowVisibility"));
        return extractFunctions(source, names);
    }

    public static String reflectionMainBody(RenderFeatureMode mode) {
        String source = VulkanMainFragmentShaderSource.mainFragment();
        return extractFunctions(source, List.of(
                "probeAxisWeight",
                "probeWeightAtWorldPos",
                "probeSampleDirection",
                "sampleProbeRadiance"
        ));
    }

    public static String reflectionPostBody(RenderFeatureMode mode) {
        String source = VulkanPostShaderSources.postFragment();
        if (mode != null && VulkanReflectionCapabilityDescriptorV2.MODE_IBL_ONLY.id().equalsIgnoreCase(mode.id())) {
            return "";
        }
        return extractFunctions(source, List.of("applyReflections"));
    }

    public static String aaPostBody(RenderFeatureMode mode) {
        String source = VulkanPostShaderSources.postFragment();
        List<String> names = new ArrayList<>(List.of(
                "smaaLuma",
                "smaaEdge",
                "smaaBlendWeights",
                "smaaNeighborhoodResolve",
                "smaaFull"
        ));
        if (mode != null && requiresTemporalAa(mode)) {
            names.add("taaSharpen");
        }
        return extractFunctions(source, names);
    }

    private static boolean requiresTemporalAa(RenderFeatureMode mode) {
        String id = mode == null ? "" : mode.id();
        return VulkanAaCapabilityDescriptorV2.MODE_TAA.id().equalsIgnoreCase(id)
                || VulkanAaCapabilityDescriptorV2.MODE_TSR.id().equalsIgnoreCase(id)
                || VulkanAaCapabilityDescriptorV2.MODE_TUUA.id().equalsIgnoreCase(id)
                || VulkanAaCapabilityDescriptorV2.MODE_HYBRID_TUUA_MSAA.id().equalsIgnoreCase(id)
                || VulkanAaCapabilityDescriptorV2.MODE_DLAA.id().equalsIgnoreCase(id);
    }

    private static String extractFunctions(String source, List<String> functionNames) {
        String normalized = VulkanShaderModuleAssembler.normalizeLineEndings(source);
        StringBuilder out = new StringBuilder();
        for (String name : functionNames) {
            String fn = extractFunctionDefinition(normalized, name);
            if (!fn.isBlank()) {
                if (!out.isEmpty()) {
                    out.append("\n\n");
                }
                out.append(fn.trim());
            }
        }
        return out.toString();
    }

    private static String extractFunctionDefinition(String source, String functionName) {
        return VulkanShaderFunctionText.extractFunctionDefinition(source, functionName);
    }
}
