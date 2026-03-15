package org.dynamisengine.light.impl.vulkan.runtime.config;

/**
 * Vulkan GI planning mode (Phase 1 scaffold).
 */
public enum GiMode {
    SSGI,
    PROBE_GRID,
    RTGI_SINGLE,
    RTGI_MULTI,
    HYBRID_PROBE_SSGI_RT,
    EMISSIVE_GI,
    DYNAMIC_SKY_GI,
    INDIRECT_SPECULAR_GI,
    STATIC_LIGHTMAPS,
    LIGHT_PROBES_SH,
    IRRADIANCE_VOLUMES,
    VOXEL_GI,
    SDF_GI
}
