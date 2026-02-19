package org.dynamislight.impl.vulkan.shader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.dynamislight.spi.render.RenderShaderInjectionPoint;
import org.dynamislight.spi.render.RenderShaderModuleDeclaration;
import org.dynamislight.spi.render.RenderShaderStage;

/**
 * Phase C module assembler for host GLSL templates.
 *
 * This utility performs metadata-driven module assembly only. Runtime pipeline cutover is handled in later sub-phases.
 */
public final class VulkanShaderModuleAssembler {
    public static final String TOKEN_MODULE_DECLARATIONS = "/*__DYNAMIS_MODULE_DECLARATIONS__*/";
    public static final String TOKEN_LIGHTING_EVAL = "/*__DYNAMIS_INJECT_LIGHTING_EVAL__*/";
    public static final String TOKEN_POST_RESOLVE = "/*__DYNAMIS_INJECT_POST_RESOLVE__*/";
    public static final String TOKEN_AUXILIARY = "/*__DYNAMIS_INJECT_AUXILIARY__*/";

    private static final Comparator<RenderShaderModuleDeclaration> MODULE_ORDERING = Comparator
            .comparingInt((RenderShaderModuleDeclaration module) -> module.ordering() < 0 ? Integer.MAX_VALUE : module.ordering())
            .thenComparing(RenderShaderModuleDeclaration::moduleId, String.CASE_INSENSITIVE_ORDER);

    private VulkanShaderModuleAssembler() {
    }

    public static VulkanShaderAssemblyResult assemble(
            String hostSource,
            String targetPassId,
            RenderShaderStage stage,
            List<RenderShaderModuleDeclaration> moduleDeclarations
    ) {
        String host = hostSource == null ? "" : hostSource;
        String passId = targetPassId == null ? "" : targetPassId.trim();
        RenderShaderStage shaderStage = stage == null ? RenderShaderStage.FRAGMENT : stage;

        List<RenderShaderModuleDeclaration> selected = moduleDeclarations == null
                ? List.of()
                : moduleDeclarations.stream()
                        .filter(module -> module != null
                                && module.targetPassId().equalsIgnoreCase(passId)
                                && module.stage() == shaderStage)
                        .sorted(MODULE_ORDERING)
                        .toList();

        Map<RenderShaderInjectionPoint, List<RenderShaderModuleDeclaration>> byPoint = selected.stream()
                .collect(Collectors.groupingBy(
                        RenderShaderModuleDeclaration::injectionPoint,
                        () -> new EnumMap<>(RenderShaderInjectionPoint.class),
                        Collectors.toList()
                ));

        String moduleBodies = selected.stream()
                .map(RenderShaderModuleDeclaration::glslBody)
                .filter(body -> body != null && !body.isBlank())
                .collect(Collectors.joining("\n\n"));

        String assembled = injectModuleDeclarations(host, moduleBodies);

        List<RenderShaderInjectionPoint> unresolved = new ArrayList<>();
        assembled = injectHookList(
                assembled,
                TOKEN_LIGHTING_EVAL,
                RenderShaderInjectionPoint.LIGHTING_EVAL,
                byPoint,
                unresolved
        );
        assembled = injectHookList(
                assembled,
                TOKEN_POST_RESOLVE,
                RenderShaderInjectionPoint.POST_RESOLVE,
                byPoint,
                unresolved
        );
        assembled = injectHookList(
                assembled,
                TOKEN_AUXILIARY,
                RenderShaderInjectionPoint.AUXILIARY,
                byPoint,
                unresolved
        );

        return new VulkanShaderAssemblyResult(
                assembled,
                selected,
                List.copyOf(unresolved)
        );
    }

    private static String injectModuleDeclarations(String host, String moduleBodies) {
        if (host.contains(TOKEN_MODULE_DECLARATIONS)) {
            return host.replace(TOKEN_MODULE_DECLARATIONS, moduleBodies);
        }
        if (moduleBodies.isBlank()) {
            return host;
        }
        int versionIdx = host.indexOf("#version");
        if (versionIdx < 0) {
            return moduleBodies + "\n\n" + host;
        }
        int lineEnd = host.indexOf('\n', versionIdx);
        if (lineEnd < 0) {
            return host + "\n\n" + moduleBodies + "\n";
        }
        String prefix = host.substring(0, lineEnd + 1);
        String suffix = host.substring(lineEnd + 1);
        return prefix + "\n" + moduleBodies + "\n\n" + suffix;
    }

    private static String injectHookList(
            String source,
            String token,
            RenderShaderInjectionPoint point,
            Map<RenderShaderInjectionPoint, List<RenderShaderModuleDeclaration>> byPoint,
            List<RenderShaderInjectionPoint> unresolved
    ) {
        List<RenderShaderModuleDeclaration> modules = byPoint.getOrDefault(point, List.of());
        String hookComment = modules.isEmpty()
                ? ""
                : modules.stream()
                        .map(module -> "/* hook:" + module.hookFunction() + " mode-module:" + module.moduleId() + " */")
                        .collect(Collectors.joining("\n"));
        if (source.contains(token)) {
            return source.replace(token, hookComment);
        }
        if (!modules.isEmpty()) {
            unresolved.add(point);
        }
        return source;
    }

    public static String defaultMainHostTemplate() {
        return """
                #version 450
                /*__DYNAMIS_MODULE_DECLARATIONS__*/
                layout(location = 0) out vec4 outColor;
                void main() {
                    vec3 worldPos = vec3(0.0, 0.0, 0.0);
                    vec3 normal = vec3(0.0, 1.0, 0.0);
                    vec3 viewDir = vec3(0.0, 0.0, 1.0);
                    float roughness = 0.5;
                    /*__DYNAMIS_INJECT_AUXILIARY__*/
                    /*__DYNAMIS_INJECT_LIGHTING_EVAL__*/
                    outColor = vec4(worldPos + normal + viewDir + vec3(roughness), 1.0);
                }
                """;
    }

    public static String defaultPostHostTemplate() {
        return """
                #version 450
                /*__DYNAMIS_MODULE_DECLARATIONS__*/
                layout(location = 0) out vec4 outColor;
                void main() {
                    vec2 uv = vec2(0.5, 0.5);
                    vec4 baseColor = vec4(0.0, 0.0, 0.0, 1.0);
                    /*__DYNAMIS_INJECT_AUXILIARY__*/
                    /*__DYNAMIS_INJECT_POST_RESOLVE__*/
                    outColor = baseColor;
                }
                """;
    }

    public static String normalizeLineEndings(String source) {
        if (source == null) {
            return "";
        }
        return source.replace("\r\n", "\n").replace('\r', '\n');
    }

    public static String stageLabel(RenderShaderStage stage) {
        RenderShaderStage effective = stage == null ? RenderShaderStage.FRAGMENT : stage;
        return effective.name().toLowerCase(Locale.ROOT);
    }
}

