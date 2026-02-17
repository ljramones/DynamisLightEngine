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

    @Test
    void evaluateUsesConfiguredVendorProviderClass() {
        VendorSdkUpscalerBridge bridge = new VendorSdkUpscalerBridge();
        assertTrue(bridge.initialize(new ExternalUpscalerBridge.InitContext("vulkan", Map.of(
                "dle.upscaler.vendor.fsr.providerClass", StubFsrProvider.class.getName()
        ))));

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
        assertTrue(decision.detail().contains("stub-fsr-provider"));
    }

    public static final class StubFsrProvider implements VendorUpscalerSdkProvider {
        @Override
        public String vendor() {
            return "fsr";
        }

        @Override
        public boolean initialize(Map<String, String> options) {
            return true;
        }

        @Override
        public ExternalUpscalerBridge.Decision evaluate(
                ExternalUpscalerBridge.DecisionInput input,
                float fallbackRenderScale,
                float fallbackSharpen,
                String fallbackDetail
        ) {
            return new ExternalUpscalerBridge.Decision(
                    true,
                    null,
                    null,
                    fallbackSharpen,
                    Math.min(1.0f, fallbackRenderScale + 0.05f),
                    null,
                    "stub-fsr-provider active"
            );
        }
    }
}
