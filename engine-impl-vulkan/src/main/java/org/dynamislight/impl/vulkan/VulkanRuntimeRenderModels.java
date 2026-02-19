package org.dynamislight.impl.vulkan;

import java.nio.file.Path;
import java.util.Map;

record FrameResourceConfig(
        int framesInFlight,
        int maxDynamicSceneObjects,
        int maxPendingUploadRanges,
        int maxTextureDescriptorSets,
        int meshGeometryCacheEntries
) {
}

record FogRenderConfig(boolean enabled, float r, float g, float b, float density, int steps, boolean degraded) {
}

record SmokeRenderConfig(boolean enabled, float r, float g, float b, float intensity, boolean degraded) {
}

record PostProcessRenderConfig(
        boolean tonemapEnabled,
        float exposure,
        float gamma,
        boolean bloomEnabled,
        float bloomThreshold,
        float bloomStrength,
        boolean ssaoEnabled,
        float ssaoStrength,
        float ssaoRadius,
        float ssaoBias,
        float ssaoPower,
        boolean smaaEnabled,
        float smaaStrength,
        boolean taaEnabled,
        float taaBlend,
        float taaClipScale,
        boolean taaLumaClipEnabled,
        float taaSharpenStrength,
        float taaRenderScale,
        boolean reflectionsEnabled,
        int reflectionsMode,
        float reflectionsSsrStrength,
        float reflectionsSsrMaxRoughness,
        float reflectionsSsrStepScale,
        float reflectionsTemporalWeight,
        float reflectionsPlanarStrength,
        float reflectionsPlanarPlaneHeight
) {
}

record IblRenderConfig(
        boolean enabled,
        float diffuseStrength,
        float specularStrength,
        boolean textureDriven,
        boolean skyboxDerived,
        boolean ktxContainerRequested,
        boolean ktxSkyboxFallback,
        int ktxDecodeUnavailableCount,
        int ktxTranscodeRequiredCount,
        int ktxUnsupportedVariantCount,
        float prefilterStrength,
        boolean degraded,
        int missingAssetCount,
        Path irradiancePath,
        Path radiancePath,
        Path brdfLutPath
) {
}

record ShadowRenderConfig(
        boolean enabled,
        float strength,
        float bias,
        float normalBiasScale,
        float slopeBiasScale,
        int pcfRadius,
        int cascadeCount,
        int mapResolution,
        int maxShadowedLocalLights,
        int selectedLocalShadowLights,
        String primaryShadowType,
        String primaryShadowLightId,
        int atlasCapacityTiles,
        int atlasAllocatedTiles,
        float atlasUtilization,
        int atlasEvictions,
        long atlasMemoryBytesD16,
        long atlasMemoryBytesD32,
        long shadowUpdateBytesEstimate,
        long shadowMomentAtlasBytesEstimate,
        int renderedLocalShadowLights,
        int renderedSpotShadowLights,
        int renderedPointShadowCubemaps,
        String renderedShadowLightIdsCsv,
        int deferredShadowLightCount,
        String deferredShadowLightIdsCsv,
        int staleBypassShadowLightCount,
        String filterPath,
        String runtimeFilterPath,
        boolean momentFilterEstimateOnly,
        boolean momentPipelineRequested,
        boolean momentPipelineActive,
        boolean contactShadowsRequested,
        String rtShadowMode,
        boolean rtShadowActive,
        boolean degraded
) {
}

record CameraMatrices(float[] view, float[] proj) {
}

record LightingConfig(
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
