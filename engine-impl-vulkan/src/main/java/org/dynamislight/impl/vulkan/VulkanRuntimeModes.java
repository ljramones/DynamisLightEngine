package org.dynamislight.impl.vulkan;

enum AaPreset {
    PERFORMANCE,
    BALANCED,
    QUALITY,
    STABILITY
}

enum AaMode {
    TAA,
    TSR,
    TUUA,
    MSAA_SELECTIVE,
    HYBRID_TUUA_MSAA,
    DLAA,
    FXAA_LOW
}

enum UpscalerMode {
    NONE,
    FSR,
    XESS,
    DLSS
}

enum UpscalerQuality {
    PERFORMANCE,
    BALANCED,
    QUALITY,
    ULTRA_QUALITY
}

enum ReflectionProfile {
    PERFORMANCE,
    BALANCED,
    QUALITY,
    STABILITY
}

record TsrControls(
        float historyWeight,
        float responsiveMask,
        float neighborhoodClamp,
        float reprojectionConfidence,
        float sharpen,
        float antiRinging,
        float tsrRenderScale,
        float tuuaRenderScale
) {
}
