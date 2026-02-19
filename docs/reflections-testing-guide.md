# Reflections Testing Guide (Vulkan)

Updated: 2026-02-18

## RT Promotion Thresholds

- `vulkan.reflections.rtHybridProbeShareWarnMax=0.70` (default)
- `vulkan.reflections.rtDenoiseSpatialVarianceWarnMax=0.45` (default)
- `vulkan.reflections.rtDenoiseTemporalLagWarnMax=0.35` (default)
- `vulkan.reflections.rtAsBuildGpuMsWarnMax=1.2` (default)
- `vulkan.reflections.rtAsMemoryBudgetMb=64.0` (default)
- `vulkan.reflections.rtPerfMaxGpuMs{Low,Medium,High,Ultra}` profile-adjusted defaults
- `REFLECTION_RT_PROMOTION_READY` requires sustained stability over `minFrames=3`

## Probe Streaming Thresholds

- `vulkan.reflections.probeStreamingWarnMinFrames=3` (default)
- `vulkan.reflections.probeStreamingWarnCooldownFrames=120` (default)
- `vulkan.reflections.probeStreamingMissRatioWarnMax=0.35` (default)
- `vulkan.reflections.probeStreamingDeferredRatioWarnMax=0.55` (default)
- `vulkan.reflections.probeStreamingLodSkewWarnMax=0.70` (default)
- `vulkan.reflections.probeStreamingMemoryBudgetMb=48.0` (default)

## Probe Quality Thresholds

- `vulkan.reflections.probeQualityOverlapWarnMaxPairs=8` (default)
- `vulkan.reflections.probeQualityBleedRiskWarnMaxPairs=0` (default)
- `vulkan.reflections.probeQualityMinOverlapPairsWhenMultiple=1` (default)
- `vulkan.reflections.probeQualityBoxProjectionMinRatio=0.60` (default)
- `vulkan.reflections.probeQualityInvalidBlendDistanceWarnMax=0` (default)
- `vulkan.reflections.probeQualityOverlapCoverageWarnMin=0.12` (default)

## Required Test Lanes

1. Lockdown contracts:
   - `./scripts/rt_reflections_ci_lockdown_full.sh`
2. Guarded real-Vulkan signoff:
   - `./scripts/rt_reflections_real_gpu_signoff.sh`
3. Long-run real-Vulkan replay:
   - `./scripts/rt_reflections_real_longrun_signoff.sh`
4. Promotion bundle:
   - `./scripts/rt_reflections_in_promotion_bundle.sh`

## CI Assertions

- Presence of:
  - `REFLECTION_RT_PATH_REQUESTED`
  - `REFLECTION_RT_PIPELINE_LIFECYCLE`
  - `REFLECTION_RT_HYBRID_COMPOSITION`
  - `REFLECTION_RT_DENOISE_ENVELOPE`
  - `REFLECTION_RT_AS_BUDGET`
  - `REFLECTION_RT_PROMOTION_STATUS`
  - `REFLECTION_PROBE_STREAMING_DIAGNOSTICS`
  - `REFLECTION_PROBE_STREAMING_ENVELOPE`
  - `REFLECTION_PROBE_QUALITY_SWEEP`
- Fail on any breach warnings in blessed lanes:
  - `REFLECTION_RT_PERF_GATES_BREACH`
  - `REFLECTION_RT_HYBRID_COMPOSITION_BREACH`
  - `REFLECTION_RT_DENOISE_ENVELOPE_BREACH`
  - `REFLECTION_RT_AS_BUDGET_BREACH`
  - `REFLECTION_PROBE_STREAMING_ENVELOPE_BREACH`
  - `REFLECTION_PROBE_QUALITY_ENVELOPE_BREACH`
