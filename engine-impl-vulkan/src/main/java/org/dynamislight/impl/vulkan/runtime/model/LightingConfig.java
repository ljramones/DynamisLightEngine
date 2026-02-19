package org.dynamislight.impl.vulkan.runtime.model;

import java.util.Map;

public record LightingConfig(
        float[] directionalDirection,
        float[] directionalColor,
        float directionalIntensity,
        float[] shadowPointPosition,
        float[] shadowPointDirection,
        boolean shadowPointIsSpot,
        float shadowPointOuterCos,
        float shadowPointRange,
        boolean shadowPointCastsShadows,
        int localLightCount,
        float[] localLightPosRange,
        float[] localLightColorIntensity,
        float[] localLightDirInner,
        float[] localLightOuterTypeShadow,
        Map<String, Integer> shadowLayerAssignments,
        int shadowAllocatorAssignedLights,
        int shadowAllocatorReusedAssignments,
        int shadowAllocatorEvictions
) {
}
