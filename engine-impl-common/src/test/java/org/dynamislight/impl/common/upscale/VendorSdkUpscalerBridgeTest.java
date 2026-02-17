package org.dynamislight.impl.common.upscale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class VendorSdkUpscalerBridgeTest {
    @Test
    void evaluateFsrQualityProducesNativeDecision() {
        VendorSdkUpscalerBridge bridge = new VendorSdkUpscalerBridge();
        assertTrue(bridge.initialize(new ExternalUpscalerBridge.InitContext("vulkan", Map.of())));

        ExternalUpscalerBridge.Decision decision = bridge.evaluate(new ExternalUpscalerBridge.DecisionInput(
                "vulkan",
                "tsr",
                "fsr",
                "quality",
                "high",
                0.9f, 1.0f, 0.2f, 0.67f, true,
                0.92f, 0.25f, 0.35f, 0.80f, 0.25f, 0.30f
        ));

        assertTrue(decision.nativeActive());
        assertNotNull(decision.taaRenderScaleOverride());
        assertTrue(decision.taaRenderScaleOverride() <= 1.0f);
    }

    @Test
    void evaluateUnsupportedModeIsInactive() {
        VendorSdkUpscalerBridge bridge = new VendorSdkUpscalerBridge();
        assertTrue(bridge.initialize(new ExternalUpscalerBridge.InitContext("vulkan", Map.of())));

        ExternalUpscalerBridge.Decision decision = bridge.evaluate(new ExternalUpscalerBridge.DecisionInput(
                "vulkan",
                "tsr",
                "none",
                "quality",
                "high",
                0.9f, 1.0f, 0.2f, 0.67f, true,
                0.92f, 0.25f, 0.35f, 0.80f, 0.25f, 0.30f
        ));

        assertFalse(decision.nativeActive());
    }
}
