package org.dynamislight.spi.render;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.dynamislight.api.config.QualityTier;

/**
 * Minimal CI gate utility for v2 capability contract validation.
 *
 * <p>Behavior:
 * <ul>
 *   <li>Groups and formats issues for logging.</li>
 *   <li>Passes when issues contain warnings/info only.</li>
 *   <li>Fails fast when any error issues are present.</li>
 * </ul>
 */
public final class RenderCapabilityContractV2CiGate {
    private RenderCapabilityContractV2CiGate() {
    }

    public static GateResult evaluateIssues(List<RenderCapabilityValidationIssue> issues) {
        List<RenderCapabilityValidationIssue> safeIssues = issues == null ? List.of() : List.copyOf(issues);
        long errorCount = safeIssues.stream()
                .filter(issue -> issue.severity() == RenderCapabilityValidationIssue.Severity.ERROR)
                .count();
        long warningCount = safeIssues.stream()
                .filter(issue -> issue.severity() == RenderCapabilityValidationIssue.Severity.WARNING)
                .count();
        long infoCount = safeIssues.stream()
                .filter(issue -> issue.severity() == RenderCapabilityValidationIssue.Severity.INFO)
                .count();
        String report = formatGroupedReport(safeIssues, errorCount, warningCount, infoCount);
        return new GateResult(errorCount == 0, (int) errorCount, (int) warningCount, (int) infoCount, report, safeIssues);
    }

    public static GateResult evaluateCapabilities(List<RenderFeatureCapabilityV2> capabilities, QualityTier tier) {
        List<RenderCapabilityValidationIssue> issues = RenderCapabilityContractV2Validator.validate(capabilities, tier);
        return evaluateIssues(issues);
    }

    public static GateResult enforceCapabilities(
            List<RenderFeatureCapabilityV2> capabilities,
            QualityTier tier,
            Consumer<String> logger
    ) {
        GateResult result = evaluateCapabilities(capabilities, tier);
        log(logger, result.report());
        if (!result.passed()) {
            throw new IllegalStateException("Capability contract CI gate failed (errors=" + result.errorCount() + ")");
        }
        return result;
    }

    public static GateResult enforceIssues(List<RenderCapabilityValidationIssue> issues, Consumer<String> logger) {
        GateResult result = evaluateIssues(issues);
        log(logger, result.report());
        if (!result.passed()) {
            throw new IllegalStateException("Capability contract CI gate failed (errors=" + result.errorCount() + ")");
        }
        return result;
    }

    private static void log(Consumer<String> logger, String report) {
        if (logger != null) {
            logger.accept(report);
        }
    }

    private static String formatGroupedReport(
            List<RenderCapabilityValidationIssue> issues,
            long errorCount,
            long warningCount,
            long infoCount
    ) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("Render capability contract CI gate: ")
                .append(errorCount == 0 ? "PASS" : "FAIL")
                .append(" (errors=").append(errorCount)
                .append(", warnings=").append(warningCount)
                .append(", info=").append(infoCount)
                .append(")");

        if (issues.isEmpty()) {
            return sb.toString();
        }

        Map<String, List<RenderCapabilityValidationIssue>> grouped = new LinkedHashMap<>();
        for (RenderCapabilityValidationIssue issue : issues) {
            String key = issue.severity().name() + "|" + issue.code();
            grouped.computeIfAbsent(key, __ -> new java.util.ArrayList<>()).add(issue);
        }

        for (Map.Entry<String, List<RenderCapabilityValidationIssue>> entry : grouped.entrySet()) {
            List<RenderCapabilityValidationIssue> bucket = entry.getValue();
            RenderCapabilityValidationIssue sample = bucket.getFirst();
            sb.append('\n')
                    .append("- ")
                    .append(sample.severity())
                    .append(' ')
                    .append(sample.code())
                    .append(" x")
                    .append(bucket.size());
            if (!sample.primaryFeatureId().isBlank() || !sample.secondaryFeatureId().isBlank()) {
                sb.append(" [")
                        .append(sample.primaryFeatureId().isBlank() ? "n/a" : sample.primaryFeatureId())
                        .append(" vs ")
                        .append(sample.secondaryFeatureId().isBlank() ? "n/a" : sample.secondaryFeatureId())
                        .append(']');
            }
            if (!sample.details().isBlank()) {
                sb.append(" {").append(sample.details()).append('}');
            }
            if (!sample.message().isBlank()) {
                sb.append(" -> ").append(sample.message());
            }
        }

        return sb.toString();
    }

    public record GateResult(
            boolean passed,
            int errorCount,
            int warningCount,
            int infoCount,
            String report,
            List<RenderCapabilityValidationIssue> issues
    ) {
        public GateResult {
            report = report == null ? "" : report;
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }
}

