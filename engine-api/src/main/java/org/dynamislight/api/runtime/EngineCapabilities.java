package org.dynamislight.api.runtime;

import java.util.Set;
import org.dynamislight.api.config.QualityTier;

/**
 * Represents the supported rendering and engine capabilities of a runtime.
 *
 * Instances of this record provide details about the engine's feature set,
 * hardware limits, and supported rendering configurations.
 *
 * This information is useful for querying runtime constraints, determining
 * supported backends, and adapting host behavior or engine configuration
 * to utilize the engine optimally.
 *
 * @param backends              Set of backend rendering technologies available in the runtime.
 *                              Examples of backends include "Vulkan," "DirectX," or "Metal."
 * @param volumetricFog         Indicates whether volumetric fog rendering is supported.
 * @param volumetricSmoke       Indicates whether volumetric smoke rendering is supported.
 * @param shadowedVolumetrics   Indicates whether volumetrics support shadow rendering.
 *                              This often applies to features like volumetric fog or smoke.
 * @param temporalReprojection  Indicates whether the runtime supports temporal reprojection,
 *                              a technique often used for anti-aliasing or upscaling.
 * @param maxRenderWidth        Maximum render width supported by the runtime in pixels.
 * @param maxRenderHeight       Maximum render height supported by the runtime in pixels.
 * @param supportedQualityTiers Set of quality tiers supported by the runtime for rendering.
 *                              Quality tiers define the levels of rendering fidelity available.
 */
public record EngineCapabilities(
        Set<String> backends,
        boolean volumetricFog,
        boolean volumetricSmoke,
        boolean shadowedVolumetrics,
        boolean temporalReprojection,
        int maxRenderWidth,
        int maxRenderHeight,
        Set<QualityTier> supportedQualityTiers
) {
    /**
     * Constructs an instance of EngineCapabilities with provided or default values for backends
     * and supported quality tiers. If null values are passed for these fields, they will
     * be replaced with empty immutable sets.
     *
     * @param backends              Set of backend rendering technologies available in the runtime.
     *                              If null, an empty immutable set will be assigned.
     * @param volumetricFog         Indicates whether volumetric fog rendering is supported.
     * @param volumetricSmoke       Indicates whether volumetric smoke rendering is supported.
     * @param shadowedVolumetrics   Indicates whether volumetrics support shadow rendering.
     *                              This often applies to features like volumetric fog or smoke.
     * @param temporalReprojection  Indicates whether the runtime supports temporal reprojection,
     *                              a technique often used for anti-aliasing or upscaling.
     * @param maxRenderWidth        Maximum render width supported by the runtime in pixels.
     * @param maxRenderHeight       Maximum render height supported by the runtime in pixels.
     * @param supportedQualityTiers Set of quality tiers supported by the runtime for rendering.
     *                              If null, an empty immutable set will be assigned.
     */
    public EngineCapabilities {
        backends = backends == null ? Set.of() : Set.copyOf(backends);
        supportedQualityTiers = supportedQualityTiers == null ? Set.of() : Set.copyOf(supportedQualityTiers);
    }
}
