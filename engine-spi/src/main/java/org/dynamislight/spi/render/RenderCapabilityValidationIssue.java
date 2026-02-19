package org.dynamislight.spi.render;

/**
 * Validation issue emitted by capability/graph validation.
 *
 * @param code stable issue code
 * @param message human-readable issue details
 * @param severity issue severity
 * @param primaryFeatureId primary feature involved in issue
 * @param secondaryFeatureId secondary/conflicting feature involved in issue
 * @param details structured details payload
 */
public record RenderCapabilityValidationIssue(
        String code,
        String message,
        Severity severity,
        String primaryFeatureId,
        String secondaryFeatureId,
        String details
) {
    public RenderCapabilityValidationIssue(String code, String message, Severity severity) {
        this(code, message, severity, "", "", "");
    }

    public RenderCapabilityValidationIssue {
        code = code == null ? "" : code.trim();
        message = message == null ? "" : message.trim();
        severity = severity == null ? Severity.ERROR : severity;
        primaryFeatureId = primaryFeatureId == null ? "" : primaryFeatureId.trim();
        secondaryFeatureId = secondaryFeatureId == null ? "" : secondaryFeatureId.trim();
        details = details == null ? "" : details.trim();
    }

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }
}
