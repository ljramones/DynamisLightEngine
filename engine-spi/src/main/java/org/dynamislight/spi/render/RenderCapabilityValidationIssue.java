package org.dynamislight.spi.render;

/**
 * Validation issue emitted by capability/graph validation.
 *
 * @param code stable issue code
 * @param message human-readable issue details
 * @param severity issue severity
 */
public record RenderCapabilityValidationIssue(
        String code,
        String message,
        Severity severity
) {
    public RenderCapabilityValidationIssue {
        code = code == null ? "" : code.trim();
        message = message == null ? "" : message.trim();
        severity = severity == null ? Severity.ERROR : severity;
    }

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }
}
