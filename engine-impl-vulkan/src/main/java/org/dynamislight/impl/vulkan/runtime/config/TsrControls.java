package org.dynamislight.impl.vulkan.runtime.config;

public record TsrControls(
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
