# Reflections Guide

This guide covers reflection controls in `PostProcessDesc`.

## API Surface

`PostProcessDesc.reflections` uses `ReflectionDesc`:

- `enabled`: master switch
- `mode`: `ibl_only`, `ssr`, `planar`, `hybrid`, `rt_hybrid`
- `ssrStrength`: SSR contribution `[0..1]`
- `ssrMaxRoughness`: SSR roughness cutoff `[0..1]`
- `ssrStepScale`: SSR ray step scale `[0.5..3]`
- `temporalWeight`: history stabilization `[0..0.98]`
- `planarStrength`: planar reflection contribution `[0..1]`

`PostProcessDesc.reflectionAdvanced` uses `ReflectionAdvancedDesc`:

- `hiZEnabled`: enables hierarchical-style SSR stepping
- `hiZMipCount`: SSR mip budget hint `[1..12]`
- `denoisePasses`: SSR denoise pass count `[0..6]`
- `planarClipPlaneEnabled`: enables planar clip-plane weighting
- `planarPlaneHeight`: plane-height hint for clip-plane workflows
- `planarFadeStart` / `planarFadeEnd`: planar fade control
- `probeVolumeEnabled`: enables probe-volume blend path
- `probeBoxProjectionEnabled`: enables probe box-projection shaping
- `probeBlendDistance`: probe blend distance hint
- `probes`: optional per-scene probe descriptors (`ReflectionProbeDesc`) including volume, priority, intensity, and cubemap asset path
- `rtEnabled`: requests hardware RT reflection lane
- `rtMaxRoughness`: roughness ceiling for RT lane
- `rtFallbackMode`: fallback mode when RT lane is unavailable

## Modes

- `ibl_only`: keep environment/IBL only.
- `ssr`: screen-space reflections only.
- `planar`: mirrored screen-space planar sample.
- `hybrid`: SSR + planar blend.
- `rt_hybrid`: requests RT reflection path with fallback stack (current backends execute fallback with RT-oriented tuning).

Advanced reflection flags are packed into runtime mode bits internally, so both OpenGL and Vulkan consume the same high-level API payload without expanding post-pass constant buffers.

Probe descriptors are scene-level data. In Vulkan they are currently uploaded each frame as metadata (SSBO) and consumed in main-fragment reflection weighting.

## Backend Profiles

Reflection quality profiles are available through backend options:

- OpenGL: `opengl.reflectionsProfile`
- Vulkan: `vulkan.reflectionsProfile`

Allowed values:

- `performance`: reduces SSR intensity/history cost.
- `balanced`: default.
- `quality`: increases reflection stability/intensity.
- `stability`: favors temporal stability over aggressive SSR.

## Example

```java
PostProcessDesc post = new PostProcessDesc(
        true, true, 1.05f, 2.2f,
        true, 1.0f, 0.8f,
        true, 0.42f, 1.0f, 0.02f, 1.0f,
        true, 0.52f,
        true, 0.56f, true,
        null,
        new ReflectionDesc(true, "rt_hybrid", 0.72f, 0.80f, 1.2f, 0.82f, 0.42f),
        new ReflectionAdvancedDesc(
                true, 6, 3,
                true, 0.0f, 0.4f, 4.8f,
                true, true, 2.5f,
                List.of(
                        new ReflectionProbeDesc(
                                "room_center",
                                new Vector3f(0.0f, 1.5f, 0.0f),
                                new Vector3f(-8.0f, -1.0f, -8.0f),
                                new Vector3f(8.0f, 6.0f, 8.0f),
                                "assets/probes/room_center.ktx2",
                                100,
                                1.5f,
                                1.0f,
                                true
                        )
                ),
                true, 0.85f, "hybrid"
        )
);
```

## Runtime Notes

- OpenGL and Vulkan both consume the same reflection descriptor.
- Advanced reflection controls (`ReflectionAdvancedDesc`) are consumed by both backends.
- Vulkan probe path currently supports probe metadata cull/sort/upload and main-pass probe-weighted reflection blending.
- Vulkan per-probe cubemap-array texture sampling is still pending (current probe path reuses the baseline radiance sample source).
- LOW quality tier disables reflections; MEDIUM applies conservative scaling.
- Warnings are emitted when reflections are active:
  - `REFLECTIONS_BASELINE_ACTIVE`
  - `REFLECTIONS_QUALITY_DEGRADED` (MEDIUM tier)
