package org.dynamislight.impl.common.upscale;

import java.util.Map;

public interface VendorUpscalerSdkProvider {
    String vendor();

    boolean initialize(Map<String, String> options);

    ExternalUpscalerBridge.Decision evaluate(
            ExternalUpscalerBridge.DecisionInput input,
            float fallbackRenderScale,
            float fallbackSharpen,
            String fallbackDetail
    );

    default String detail() {
        return "provider-ready";
    }
}

