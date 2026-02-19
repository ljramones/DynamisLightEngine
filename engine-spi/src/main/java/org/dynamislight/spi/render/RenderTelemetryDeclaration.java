package org.dynamislight.spi.render;

import java.util.List;

/**
 * Telemetry declaration surface for capability diagnostics and CI gates.
 *
 * @param warningTypes warning/event codes emitted by capability
 * @param diagnosticAccessors typed diagnostics accessors exposed by runtime
 * @param eventCallbackTypes callback event types emitted by runtime
 * @param ciGateAssertions declarative CI gate identifiers
 */
public record RenderTelemetryDeclaration(
        List<String> warningTypes,
        List<String> diagnosticAccessors,
        List<String> eventCallbackTypes,
        List<String> ciGateAssertions
) {
    public RenderTelemetryDeclaration {
        warningTypes = warningTypes == null ? List.of() : List.copyOf(warningTypes);
        diagnosticAccessors = diagnosticAccessors == null ? List.of() : List.copyOf(diagnosticAccessors);
        eventCallbackTypes = eventCallbackTypes == null ? List.of() : List.copyOf(eventCallbackTypes);
        ciGateAssertions = ciGateAssertions == null ? List.of() : List.copyOf(ciGateAssertions);
    }
}
