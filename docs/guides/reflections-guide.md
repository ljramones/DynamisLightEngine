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
- `rt_hybrid`: requests RT reflection path with SSR/probe fallback stack.

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
- Vulkan probe path now uses a dedicated probe-radiance sampler lane with per-scene 2D-array texture generation and per-probe slot sampling in main fragment shading.
- Vulkan native cubemap-array probe sampling is still pending; current per-probe selection uses the 2D-array slot path.
- LOW quality tier disables reflections; MEDIUM applies conservative scaling.
- Vulkan adaptive reflection trend diagnostics are exposed through runtime API (`EngineRuntime#reflectionAdaptiveTrendSloDiagnostics`) and backend debug accessors.
- Vulkan now emits an explicit SSR/TAA history policy warning (`REFLECTION_SSR_TAA_HISTORY_POLICY`) describing active reflected-region history strategy (`surface_motion_vectors`, `reflection_region_decay`, `reflection_region_reject`) with thresholds and active reject/decay bias.
- Vulkan now emits a formal SSR/TAA reprojection policy signal (`surface_motion_vectors`, `reflection_space_bias`, `reflection_space_reject`) in history-policy diagnostics, including disocclusion-driven rejection gates.
- Vulkan now emits probe quality sweep diagnostics (`REFLECTION_PROBE_QUALITY_SWEEP`) and envelope breach warnings (`REFLECTION_PROBE_QUALITY_ENVELOPE_BREACH`) based on overlap/priority analysis of configured probe volumes.
- Vulkan now emits planar scope/order contracts (`REFLECTION_PLANAR_SCOPE_CONTRACT`) including selective mesh eligibility and required pass order contract.
- Vulkan now executes runtime-composed reflection mode bits for reflection-space reprojection/reject policy, selective planar execution, RT lane activation, and transparent/refraction integration.
- Vulkan RT hybrid now executes an active RT-oriented reflection trace + denoise path in post shader, with fallback diagnostics only when the lane is disabled.
- Vulkan now emits transparency/refraction stage-gate diagnostics (`REFLECTION_TRANSPARENCY_STAGE_GATE`, `REFLECTION_TRANSPARENCY_REFRACTION_PENDING`) and activates `preview_enabled` integration when RT lane is active.
- Vulkan now runs an explicit planar selective geometry capture pre-pass before main scene pass, then copies that capture into the planar history source prior to post composite (`planar_capture_before_main_sample_before_post` contract is now backed by execution).
- Vulkan planar capture now applies shader-level mirror transform and clip-plane culling during the selective pre-main capture pass (push-constant driven, plane height sourced from `reflectionAdvanced.planarPlaneHeight` when planar clip-plane mode is enabled).
- Planar capture currently uses selective pre-main rerender scope; true mirrored clip-plane camera rerender is still pending.
- Vulkan now emits SSR reprojection envelope diagnostics (`REFLECTION_SSR_REPROJECTION_ENVELOPE`) and breach warnings (`REFLECTION_SSR_REPROJECTION_ENVELOPE_BREACH`) with threshold/cooldown gating for ghosting/disocclusion risk.
- Vulkan RT lane now supports dedicated denoise staging (spatial + temporal) behind runtime mode bit activation.
- Vulkan probe metadata upload now supports streaming cadence + max-visible budget + LOD depth-tier tagging for probe resolve.
- Warnings are emitted when reflections are active:
  - `REFLECTIONS_BASELINE_ACTIVE`
  - `REFLECTIONS_QUALITY_DEGRADED` (MEDIUM tier)
  - `REFLECTION_SSR_TAA_ADAPTIVE_TREND_REPORT` (window metrics)
  - `REFLECTION_SSR_TAA_ADAPTIVE_TREND_SLO_AUDIT` (`status=pass|pending|fail`)
  - `REFLECTION_SSR_TAA_ADAPTIVE_TREND_SLO_FAILED` (fail-only)
  - `REFLECTION_SSR_TAA_ADAPTIVE_TREND_HIGH_RISK` (threshold/cooldown gate)
  - `REFLECTION_SSR_TAA_HISTORY_POLICY` (history-policy mode + bias diagnostics)
  - `REFLECTION_PROBE_QUALITY_SWEEP` / `REFLECTION_PROBE_QUALITY_ENVELOPE_BREACH`
  - `REFLECTION_PLANAR_SCOPE_CONTRACT`
  - `REFLECTION_RT_PATH_REQUESTED` / `REFLECTION_RT_PATH_FALLBACK_ACTIVE` (only when lane unavailable)
  - `REFLECTION_TRANSPARENCY_STAGE_GATE` / `REFLECTION_TRANSPARENCY_REFRACTION_PENDING`
- High-risk/fail adaptive trend warnings now also emit `PerformanceWarningEvent` callbacks in Vulkan for parser-free host/CI alerting.
