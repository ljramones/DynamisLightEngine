package org.dynamislight.spi.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RenderCapabilityContractV2CiGateTest {
    @Test
    void warningsOnlyPassAndProduceGroupedReport() {
        List<RenderCapabilityValidationIssue> issues = List.of(
                new RenderCapabilityValidationIssue(
                        "SHADER_INJECTION_POINT_CONFLICT",
                        "ordering missing",
                        RenderCapabilityValidationIssue.Severity.WARNING,
                        "feature.a",
                        "feature.b",
                        "targetPass=main,injectionPoint=LIGHTING_EVAL"
                ),
                new RenderCapabilityValidationIssue(
                        "SHADER_INJECTION_POINT_CONFLICT",
                        "ordering missing",
                        RenderCapabilityValidationIssue.Severity.WARNING,
                        "feature.c",
                        "feature.d",
                        "targetPass=post,injectionPoint=POST_RESOLVE"
                )
        );

        List<String> logs = new ArrayList<>();
        RenderCapabilityContractV2CiGate.GateResult result = RenderCapabilityContractV2CiGate.enforceIssues(issues, logs::add);

        assertTrue(result.passed());
        assertEquals(0, result.errorCount());
        assertEquals(2, result.warningCount());
        assertEquals(1, logs.size());
        assertTrue(logs.getFirst().contains("PASS"));
        assertTrue(logs.getFirst().contains("SHADER_INJECTION_POINT_CONFLICT x2"));
    }

    @Test
    void errorsFailGateAndStillLogReport() {
        List<RenderCapabilityValidationIssue> issues = List.of(
                new RenderCapabilityValidationIssue(
                        "RESOURCE_NAME_COLLISION",
                        "resource collision",
                        RenderCapabilityValidationIssue.Severity.ERROR,
                        "feature.a",
                        "feature.b",
                        "resourceName=shared"
                )
        );

        List<String> logs = new ArrayList<>();
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> RenderCapabilityContractV2CiGate.enforceIssues(issues, logs::add)
        );

        assertTrue(failure.getMessage().contains("errors=1"));
        assertEquals(1, logs.size());
        assertTrue(logs.getFirst().contains("FAIL"));
        assertTrue(logs.getFirst().contains("RESOURCE_NAME_COLLISION x1"));
    }
}

