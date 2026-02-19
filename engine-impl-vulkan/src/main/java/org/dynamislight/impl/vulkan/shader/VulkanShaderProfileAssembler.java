package org.dynamislight.impl.vulkan.shader;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.dynamislight.spi.render.RenderShaderModuleDeclaration;
import org.dynamislight.spi.render.RenderShaderStage;

/**
 * Canonical host-source assembly for phase-C parallel validation.
 */
public final class VulkanShaderProfileAssembler {
    private VulkanShaderProfileAssembler() {
    }

    public static VulkanShaderAssemblyResult assembleMainFragmentCanonical(List<RenderShaderModuleDeclaration> modules) {
        return assembleCanonical(
                VulkanMainShaderSources.mainFragment(),
                "main_geometry",
                RenderShaderStage.FRAGMENT,
                modules
        );
    }

    public static VulkanShaderAssemblyResult assemblePostFragmentCanonical(List<RenderShaderModuleDeclaration> modules) {
        return assembleCanonical(
                VulkanPostShaderSources.postFragment(),
                "post_composite",
                RenderShaderStage.FRAGMENT,
                modules
        );
    }

    public static VulkanShaderAssemblyResult assembleCanonical(
            String hostSource,
            String targetPassId,
            RenderShaderStage stage,
            List<RenderShaderModuleDeclaration> modules
    ) {
        Set<String> namesToReplace = new LinkedHashSet<>();
        for (RenderShaderModuleDeclaration module : modules == null ? List.<RenderShaderModuleDeclaration>of() : modules) {
            if (module == null) {
                continue;
            }
            if (!targetPassId.equalsIgnoreCase(module.targetPassId()) || module.stage() != stage) {
                continue;
            }
            namesToReplace.addAll(VulkanShaderFunctionText.extractFunctionNames(module.glslBody()));
        }
        String hostWithToken = removeAndInsertDeclarationToken(hostSource, namesToReplace);
        return VulkanShaderModuleAssembler.assemble(hostWithToken, targetPassId, stage, modules);
    }

    private static String removeAndInsertDeclarationToken(String hostSource, Set<String> functionNames) {
        String source = VulkanShaderModuleAssembler.normalizeLineEndings(hostSource);
        if (functionNames == null || functionNames.isEmpty()) {
            return source;
        }
        String working = source;
        boolean tokenInserted = false;
        for (String functionName : functionNames) {
            String definition = VulkanShaderFunctionText.extractFunctionDefinition(working, functionName);
            if (definition.isBlank()) {
                continue;
            }
            int idx = working.indexOf(definition);
            if (idx < 0) {
                continue;
            }
            int end = idx + definition.length();
            while (end < working.length() && (working.charAt(end) == '\n' || working.charAt(end) == '\r')) {
                end++;
            }
            String replacement = tokenInserted
                    ? ""
                    : VulkanShaderModuleAssembler.TOKEN_MODULE_DECLARATIONS + "\n";
            tokenInserted = true;
            working = working.substring(0, idx) + replacement + working.substring(end);
        }
        if (!tokenInserted && !working.contains(VulkanShaderModuleAssembler.TOKEN_MODULE_DECLARATIONS)) {
            int versionIdx = working.indexOf("#version");
            int lineEnd = versionIdx >= 0 ? working.indexOf('\n', versionIdx) : -1;
            if (lineEnd >= 0) {
                working = working.substring(0, lineEnd + 1)
                        + VulkanShaderModuleAssembler.TOKEN_MODULE_DECLARATIONS + "\n"
                        + working.substring(lineEnd + 1);
            } else {
                working = VulkanShaderModuleAssembler.TOKEN_MODULE_DECLARATIONS + "\n" + working;
            }
        }
        return working;
    }
}
