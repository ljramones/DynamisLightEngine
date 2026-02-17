package org.dynamislight.impl.common.upscale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExternalUpscalerIntegrationTest {
    @Test
    void evaluateDisablesVendorModeWhenVendorLibraryIsUnconfigured() {
        ExternalUpscalerIntegration integration = ExternalUpscalerIntegration.create(
                "vulkan",
                "vulkan.",
                Map.of("vulkan.upscaler.bridgeClass", StubBridge.class.getName())
        );

        ExternalUpscalerBridge.Decision decision = integration.evaluate(new ExternalUpscalerBridge.DecisionInput(
                "vulkan",
                "tsr",
                "fsr",
                "quality",
                "high",
                0.9f,
                1.0f,
                0.0f,
                0.67f,
                true,
                0.92f,
                0.25f,
                0.35f,
                0.80f,
                0.25f,
                0.30f
        ));

        assertFalse(decision.nativeActive());
        assertTrue(decision.detail().contains("fsr vendor unavailable"));
    }

    @Test
    void evaluateAllowsVendorModeWhenVendorLibraryPathExists() throws IOException {
        java.nio.file.Path tmp = Files.createTempFile("dle-fsr-sdk", ".dylib");
        ExternalUpscalerIntegration integration = ExternalUpscalerIntegration.create(
                "vulkan",
                "vulkan.",
                Map.of(
                        "vulkan.upscaler.bridgeClass", StubBridge.class.getName(),
                        "vulkan.upscaler.vendor.fsr.library", tmp.toAbsolutePath().toString()
                )
        );

        ExternalUpscalerBridge.Decision decision = integration.evaluate(new ExternalUpscalerBridge.DecisionInput(
                "vulkan",
                "tsr",
                "fsr",
                "quality",
                "high",
                0.9f,
                1.0f,
                0.0f,
                0.67f,
                true,
                0.92f,
                0.25f,
                0.35f,
                0.80f,
                0.25f,
                0.30f
        ));

        assertTrue(decision.nativeActive());
    }

    public static final class StubBridge implements ExternalUpscalerBridge {
        @Override
        public String id() {
            return "stub";
        }

        @Override
        public boolean initialize(InitContext context) {
            return true;
        }

        @Override
        public Decision evaluate(DecisionInput input) {
            return new Decision(true, null, null, null, null, null, "stub active");
        }
    }
}
