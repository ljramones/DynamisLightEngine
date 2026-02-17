package org.dynamislight.impl.common.upscale;

import java.util.Map;

public interface ExternalUpscalerBridge {
    String id();

    boolean initialize(InitContext context);

    Decision evaluate(DecisionInput input);

    default void shutdown() {
    }

    record InitContext(
            String backend,
            Map<String, String> backendOptions
    ) {
    }

    record DecisionInput(
            String backend,
            String aaMode,
            String upscalerMode,
            String upscalerQuality,
            String qualityTier,
            float taaBlend,
            float taaClipScale,
            float taaSharpenStrength,
            float taaRenderScale,
            boolean taaLumaClipEnabled,
            float tsrHistoryWeight,
            float tsrResponsiveMask,
            float tsrNeighborhoodClamp,
            float tsrReprojectionConfidence,
            float tsrSharpen,
            float tsrAntiRinging
    ) {
    }

    record Decision(
            boolean nativeActive,
            Float taaBlendOverride,
            Float taaClipScaleOverride,
            Float taaSharpenStrengthOverride,
            Float taaRenderScaleOverride,
            Boolean taaLumaClipEnabledOverride,
            String detail
    ) {
        public static Decision inactive(String detail) {
            return new Decision(false, null, null, null, null, null, detail);
        }
    }
}
