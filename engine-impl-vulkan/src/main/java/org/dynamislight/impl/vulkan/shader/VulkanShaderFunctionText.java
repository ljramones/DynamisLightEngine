package org.dynamislight.impl.vulkan.shader;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Function-text helpers for shader source extraction/replacement.
 */
public final class VulkanShaderFunctionText {
    private static final Pattern FUNCTION_NAME_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:[A-Za-z_][A-Za-z0-9_]*\\s+)+([A-Za-z_][A-Za-z0-9_]*)\\s*\\("
    );

    private VulkanShaderFunctionText() {
    }

    public static String extractFunctionDefinition(String source, String functionName) {
        if (source == null || source.isBlank() || functionName == null || functionName.isBlank()) {
            return "";
        }
        String normalized = VulkanShaderModuleAssembler.normalizeLineEndings(source);
        Pattern definitionPattern = Pattern.compile(
                "(?m)^\\s*(?:[A-Za-z_][A-Za-z0-9_]*\\s+)+" + Pattern.quote(functionName) + "\\s*\\("
        );
        Matcher matcher = definitionPattern.matcher(normalized);
        while (matcher.find()) {
            int idx = matcher.start();
            int openParen = normalized.indexOf('(', matcher.end() - 1);
            if (openParen < 0) {
                continue;
            }
            int closeParen = matchForward(normalized, openParen, '(', ')');
            if (closeParen < 0) {
                continue;
            }
            int cursor = closeParen + 1;
            while (cursor < normalized.length() && Character.isWhitespace(normalized.charAt(cursor))) {
                cursor++;
            }
            if (cursor >= normalized.length() || normalized.charAt(cursor) != '{') {
                continue;
            }
            int end = matchForward(normalized, cursor, '{', '}');
            if (end < 0) {
                continue;
            }
            return normalized.substring(idx, end + 1);
        }
        return "";
    }

    public static Set<String> extractFunctionNames(String source) {
        Set<String> names = new LinkedHashSet<>();
        if (source == null || source.isBlank()) {
            return names;
        }
        String normalized = VulkanShaderModuleAssembler.normalizeLineEndings(source);
        Matcher matcher = FUNCTION_NAME_PATTERN.matcher(normalized);
        while (matcher.find()) {
            String name = matcher.group(1);
            int openParen = normalized.indexOf('(', matcher.end() - 1);
            if (openParen < 0) {
                continue;
            }
            int closeParen = matchForward(normalized, openParen, '(', ')');
            if (closeParen < 0) {
                continue;
            }
            int cursor = closeParen + 1;
            while (cursor < normalized.length() && Character.isWhitespace(normalized.charAt(cursor))) {
                cursor++;
            }
            // Require "{ ... }" function definition shape; ignore calls and assignments.
            if (cursor >= normalized.length() || normalized.charAt(cursor) != '{') {
                continue;
            }
            if (name != null && !name.isBlank()
                    && !"if".equals(name)
                    && !"for".equals(name)
                    && !"while".equals(name)
                    && !"switch".equals(name)) {
                names.add(name);
            }
        }
        return names;
    }

    public static String removeFunctionDefinitions(String source, Set<String> functionNames) {
        if (source == null || source.isBlank() || functionNames == null || functionNames.isEmpty()) {
            return source == null ? "" : source;
        }
        String normalized = VulkanShaderModuleAssembler.normalizeLineEndings(source);
        String result = normalized;
        for (String functionName : functionNames) {
            result = removeOne(result, functionName);
        }
        return result;
    }

    private static String removeOne(String source, String functionName) {
        String definition = extractFunctionDefinition(source, functionName);
        if (definition.isBlank()) {
            return source;
        }
        int idx = source.indexOf(definition);
        if (idx < 0) {
            return source;
        }
        int start = idx;
        int end = idx + definition.length();
        while (end < source.length() && (source.charAt(end) == '\n' || source.charAt(end) == '\r')) {
            end++;
        }
        return source.substring(0, start) + source.substring(end);
    }

    private static int matchForward(String source, int start, char open, char close) {
        int depth = 0;
        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
