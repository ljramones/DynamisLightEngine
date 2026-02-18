# Reflections Testing

This document defines reflection validation for OpenGL and Vulkan.

## Scope

- `ReflectionDesc` validation in API layer.
- `ReflectionAdvancedDesc` validation in API layer.
- Runtime parity of reflection mode wiring across backends.
- Compare-harness coverage for reflection-heavy scenes.

## Required Checks

1. API validation
- Reject invalid `mode`.
- Enforce numeric ranges for SSR/temporal/planar controls.
- Enforce advanced ranges (`hiZMipCount`, `denoisePasses`, fade ranges, probe blend, RT limits/fallback mode).

2. Runtime warnings
- Reflection-enabled scenes emit `REFLECTIONS_BASELINE_ACTIVE`.
- MEDIUM-tier reflection scenes emit `REFLECTIONS_QUALITY_DEGRADED`.
- Mixed override scenes emit `REFLECTION_OVERRIDE_POLICY` and expose typed override-policy diagnostics for counts/scope assertions.

3. Profile validation
- Run each reflection mode with `performance`, `balanced`, `quality`, `stability`.
- Confirm profile changes are reflected in metadata/warnings and bounded diffs.
- Validate blessed profile trend envelopes (`performance`, `quality`, `stability`) for:
  - `ssrTaaAdaptiveTrendWindowFrames`
  - `ssrTaaAdaptiveTrendHighRatioWarnMin`
  - `ssrTaaAdaptiveTrendSloMeanSeverityMax`
  - `ssrTaaAdaptiveTrendSloHighRatioMax`
  - `ssrTaaAdaptiveTrendSloMinSamples`

3. Scene-level rendering checks
- `ssr` scene: confirm visible reflected highlights from on-screen content.
- `planar` scene: confirm vertical mirror sample behavior.
- `hybrid` scene: confirm stable blend under camera motion.
- `hybrid_hiz_probe` scene: confirm Hi-Z SSR stepping + denoise + probe blending remains bounded.
- `rt_hybrid` scene: confirm RT lane activates and denoise path is applied with bounded parity diff.

## Commands

```bash
# API validation tests
mvn -pl engine-api -am -Dtest=EngineApiContractTest test

# OpenGL reflections runtime tests
mvn -pl engine-impl-opengl -am -Dtest=OpenGlEngineRuntimeLifecycleTest -Dsurefire.failIfNoSpecifiedTests=false test

# Vulkan reflections runtime tests
mvn -pl engine-impl-vulkan -am -Dtest=VulkanEngineRuntimeIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test

# Full planar lockdown sequence (contracts + planar compare scenes)
./scripts/planar_ci_lockdown_full.sh

# Guarded real-Vulkan planar signoff bundle
./scripts/planar_real_gpu_signoff.sh

# RT reflections lockdown (contracts + compare harness)
./scripts/rt_reflections_ci_lockdown_full.sh

# Guarded real-Vulkan RT reflections signoff
./scripts/rt_reflections_real_gpu_signoff.sh
```

See `docs/planar-in-exit-criteria.md` for explicit `Partial -> In` exit criteria and current checklist status.

## Compare Harness Additions

- Add reflection-tagged scenes:
  - glossy SSR stress
  - planar mirror stress
  - hybrid motion stress
- Track per-scene diff and temporal metrics, same as AA gates.

Current profile tags in parity tests:

- `reflections-ssr`
- `reflections-planar`
- `reflections-hybrid`
- `reflections-hiz-probe`
- `reflections-rt-fallback`

## Adaptive Trend CI Checks

1. High-risk gate checks
- Force instability and assert `REFLECTION_SSR_TAA_ADAPTIVE_TREND_HIGH_RISK` respects configured min frames, min samples, and cooldown.

2. SLO audit checks
- Assert `REFLECTION_SSR_TAA_ADAPTIVE_TREND_SLO_AUDIT` emits structured status (`pass|pending|fail`) and fail-only warning when thresholds are violated.
- Assert typed diagnostics via `EngineRuntime#reflectionAdaptiveTrendSloDiagnostics` mirror backend diagnostics.

3. Callback checks
- Assert adaptive trend `HIGH_RISK` / `SLO_FAILED` warnings can be consumed through `PerformanceWarningEvent` callbacks for parser-free host alerting.

4. SSR/TAA history policy checks
- Assert `REFLECTION_SSR_TAA_HISTORY_POLICY` emits deterministic mode transitions under controlled thresholds.
- Validate typed diagnostics (`debugReflectionSsrTaaHistoryPolicyDiagnostics` in Vulkan tests) agree with warning payload fields.
- Assert disocclusion-driven rejection thresholds trigger `reflection_disocclusion_reject` / `reflection_space_reject` policy when configured.

5. Probe quality sweep checks
- Assert `REFLECTION_PROBE_QUALITY_SWEEP` always reports overlap/bleed/transition counts for probe-enabled scenes.
- Assert `REFLECTION_PROBE_QUALITY_ENVELOPE_BREACH` under intentionally strict overlap/bleed thresholds.
- Validate typed diagnostics (`debugReflectionProbeQualityDiagnostics`) against warning payload.

6. Planar contract checks
- Assert `REFLECTION_PLANAR_SCOPE_CONTRACT` for planar/hybrid modes reports required ordering contract and selective scope counts.
- Validate typed diagnostics (`debugReflectionPlanarContractDiagnostics`) match warning payload.
- Assert planar contract/typed diagnostics include `mirrorCameraActive=true` when planar path is active.
- Assert planar contract/typed diagnostics include `dedicatedCaptureLaneActive=true` when dedicated planar capture resource is bound.
- Assert runtime-composed reflection mode carries planar selective/capture execution bits when eligible scope exists, including planar geometry capture execution bit (`1 << 20`).
- Validate planar clip-plane height from `ReflectionAdvancedDesc.planarPlaneHeight` is consumed by Vulkan planar capture path (mirrored/clip behavior follows configured plane).
- Add multi-frame plane-height stability assertions to ensure planar contract (`planeHeight`, `mirrorCameraActive`) remains consistent across consecutive renders.
- Validate swapchain/post resource wiring includes dedicated planar capture image/view/sampler lane (no coupling to TAA history-velocity texture).
- Include planar contract/stability coverage checks across `planar`, `hybrid`, and `rt_hybrid` reflection modes.
- Assert `REFLECTION_PLANAR_STABILITY_ENVELOPE` emits for planar-active modes and assert cooldown-gated `REFLECTION_PLANAR_STABILITY_ENVELOPE_BREACH` under strict envelope thresholds.
- Validate typed diagnostics (`debugReflectionPlanarStabilityDiagnostics`) for parser-free CI gating.
- Assert `REFLECTION_PLANAR_RESOURCE_CONTRACT` reports `capture_available_before_post_sample` for planar-active paths and `fallback_scene_color` otherwise.
- Assert planar perf gates emit `REFLECTION_PLANAR_PERF_GATES` every planar-active frame and `REFLECTION_PLANAR_PERF_GATES_BREACH` under strict thresholds.
- Validate typed diagnostics (`debugReflectionPlanarPerfDiagnostics`) against warning payload fields (`gpuMsEstimate`, draw inflation, memory estimate, caps, cooldown/high-streak state).
- Add strict timing-source gate check: with `vulkan.reflections.planarPerfRequireGpuTimestamp=true`, assert breach when timing source is not `gpu_timestamp`.
- For guarded real-Vulkan runs, assert planar perf diagnostics report `timingSource=gpu_timestamp` when timestamp queries are supported and enabled.
  - Current test: `VulkanEngineRuntimeIntegrationTest#guardedRealVulkanPlanarPerfTimingSourceFollowsTimestampAvailability`.
- Include planar scene-matrix coverage checks:
  - interior mirror-like scene
  - outdoor plane scene
  - multi-plane scene
  - dynamic crossing scene (object crossing plane height)
- Include stress checks:
  - rapid camera movement
  - frequent plane-height changes
  - high selective-scope mesh counts

7. RT execution-lane checks
- Assert `REFLECTION_RT_PATH_REQUESTED` and typed diagnostics (`debugReflectionRtPathDiagnostics`) expose lane request/active state and fallback chain.
- Assert RT diagnostics include dedicated denoise pipeline activation flag for request and typed snapshot parity.
- Assert runtime-composed reflection mode bits expose RT active/multi-bounce flags (`debugReflectionRuntimeMode`) and denoise strength (`debugReflectionRuntimeRtDenoiseStrength`).
- Assert fallback warning (`REFLECTION_RT_PATH_FALLBACK_ACTIVE`) only when RT lane is explicitly unavailable.
- Assert strict-required RT mode emits breach warning (`REFLECTION_RT_PATH_REQUIRED_UNAVAILABLE_BREACH`) when `vulkan.reflections.rtRequireActive=true` and lane cannot activate.
- Assert strict-required RT multi-bounce emits breach warning (`REFLECTION_RT_MULTI_BOUNCE_REQUIRED_UNAVAILABLE_BREACH`) when `vulkan.reflections.rtRequireMultiBounce=true` and multi-bounce cannot activate.
- Assert strict-required dedicated RT pipeline emits breach warning (`REFLECTION_RT_DEDICATED_PIPELINE_REQUIRED_UNAVAILABLE_BREACH`) when `vulkan.reflections.rtRequireDedicatedPipeline=true` and dedicated hardware pipeline is unavailable.
- Assert RT perf gates emit `REFLECTION_RT_PERF_GATES` and breach under strict caps via `REFLECTION_RT_PERF_GATES_BREACH`.
- Validate typed RT perf diagnostics (`debugReflectionRtPerfDiagnostics`) against warning payload (`gpuMsEstimate`, `gpuMsCap`, streak/cooldown state).
- Assert dedicated RT denoise stage bit is present when dedicated RT denoise pipeline is enabled.

8. Transparency/refraction stage-gate checks
- For alpha-tested/transparent candidates, assert `REFLECTION_TRANSPARENCY_STAGE_GATE` is emitted.
- Assert pending warning (`REFLECTION_TRANSPARENCY_REFRACTION_PENDING`) when RT lane is not active, and `preview_enabled` + `rt_or_probe` fallback when RT lane is active.
- Validate typed diagnostics (`debugReflectionTransparencyDiagnostics`) for candidate count/status/fallback path.

9. SSR calibration envelope checks
- Assert `REFLECTION_SSR_REPROJECTION_ENVELOPE` always emits under SSR/TAA path.
- Under strict thresholds, assert cooldown-gated `REFLECTION_SSR_REPROJECTION_ENVELOPE_BREACH`.

10. Probe streaming/LOD checks
- Validate `VulkanReflectionProbeCoordinator` cadence rotation keeps top-priority probes and rotates lower-priority probes.
- Validate probe metadata payload includes bounded LOD tier in probe flags (`cubemapIndexAndFlags.w` in `[0..3]`).
- Assert streaming diagnostics warning (`REFLECTION_PROBE_STREAMING_DIAGNOSTICS`) and budget-pressure warning (`REFLECTION_PROBE_STREAMING_BUDGET_PRESSURE`) under tight `probeMaxVisible` settings.
- Validate typed diagnostics (`debugReflectionProbeStreamingDiagnostics`) mirror warning payload fields (`effectiveBudget`, `cadenceFrames`, `budgetPressure`).

## Regression Triggers

- New reflection mode added.
- Reflection mode-bit packing layout changed.
- SSR step logic/temporal blend tuning changed.
- Post shader push-constant layout changed (Vulkan).
