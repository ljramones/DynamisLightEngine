# RT Reflections Exit Criteria (Partial -> In)

Scope date: 2026-02-18  
Primary scope: Vulkan production path (`engine-impl-vulkan`)

Detailed execution checklist: `docs/rt-reflections-in-checklist.md`.

## Definition of `In` (Vulkan-scoped)

RT reflections are `In` when the RT lane is truly executable (not fallback-only), denoise/hybrid composition are stable, and perf/artifact gates are locked from real-content runs.

## Exit Checklist

- [x] RT lane request/active diagnostics are emitted.
  Evidence: `REFLECTION_RT_PATH_REQUESTED`, `debugReflectionRtPathDiagnostics`.
- [x] RT fallback diagnostics are emitted only when lane inactive.
  Evidence: `REFLECTION_RT_PATH_FALLBACK_ACTIVE`.
- [x] Strict RT availability policy exists for promotion gating.
  Evidence: `vulkan.reflections.rtRequireActive=true` + `REFLECTION_RT_PATH_REQUIRED_UNAVAILABLE_BREACH`.
- [x] Strict multi-bounce availability policy exists for promotion gating.
  Evidence: `vulkan.reflections.rtRequireMultiBounce=true` + `REFLECTION_RT_MULTI_BOUNCE_REQUIRED_UNAVAILABLE_BREACH`.
- [x] Strict dedicated RT pipeline availability policy exists for promotion gating.
  Evidence: `vulkan.reflections.rtRequireDedicatedPipeline=true` + `REFLECTION_RT_DEDICATED_PIPELINE_REQUIRED_UNAVAILABLE_BREACH`.
- [x] RT perf envelope warning and breach gate are emitted with typed diagnostics.
  Evidence: `REFLECTION_RT_PERF_GATES`, `REFLECTION_RT_PERF_GATES_BREACH`, `debugReflectionRtPerfDiagnostics`.
- [x] RT pipeline lifecycle scaffolding telemetry is emitted for BLAS/TLAS/SBT progression.
  Evidence: `REFLECTION_RT_PIPELINE_LIFECYCLE`, `debugReflectionRtPipelineDiagnostics`.
- [x] Runtime-composed mode bits expose RT active/multi-bounce/denoise state.
  Evidence: `debugReflectionRuntimeMode`, `debugReflectionRuntimeRtDenoiseStrength`.
- [x] Transparency stage gate is integrated with RT lane status.
  Evidence: `REFLECTION_TRANSPARENCY_STAGE_GATE`, `REFLECTION_TRANSPARENCY_REFRACTION_PENDING`.
- [x] CI lockdown lane exists for RT contracts and parity scenes.
  Evidence: `scripts/rt_reflections_ci_lockdown_full.sh`.
- [x] Guarded real-Vulkan signoff runner exists for RT contract replay.
  Evidence: `scripts/rt_reflections_real_gpu_signoff.sh`.
- [ ] Dedicated hardware RT pipeline path + SBT/AS lifecycle validated on real content.
- [ ] Multi-bounce quality/perf envelopes calibrated and locked per profile.
- [ ] RT denoise temporal/spatial envelopes locked under camera/disocclusion stress.
- [ ] Hybrid policy (`rt -> ssr -> probe`) tuned/locked for roughness split and misses.
- [ ] Real-Vulkan long-run signoff across approved RT reflection scenes.

## Execution Commands

RT lockdown (safe CI lane + compare harness):

```bash
./scripts/rt_reflections_ci_lockdown_full.sh
```

Guarded real-Vulkan signoff slice:

```bash
mvn -pl engine-impl-vulkan -am test \
  -DskipITs \
  -Ddle.test.vulkan.real=true \
  -Dtest=VulkanEngineRuntimeIntegrationTest#guardedRealVulkanInitPath+guardedRealVulkanRtReflectionContractEmitsPathDiagnostics+guardedRealVulkanRtRequireActiveBehaviorMatchesLaneAvailability+guardedRealVulkanRtRequireDedicatedPipelineFollowsCapabilityAndEnableState \
  -Dsurefire.failIfNoSpecifiedTests=false

# or use the packaged runner:
./scripts/rt_reflections_real_gpu_signoff.sh
```

## Status Note

As of 2026-02-18, RT reflections remain `Partial` with strong runtime diagnostics and gating contracts, but not yet `In` until dedicated hardware path validation and real-content envelope lock-down are complete.
