# Planar Reflections Exit Criteria (Partial -> In)

Scope date: 2026-02-18  
Primary scope: Vulkan production path (`engine-impl-vulkan`)

## Definition of `In` (Vulkan-scoped)

Planar reflections are `In` when all items below are green in CI and documented as stable contracts:

1. End-to-end planar capture path is active and validated under representative content.
2. Artifact gates are active and fail CI on sustained breach.
3. Performance gates are active and fail CI on sustained breach.
4. Selective rerender policy is deterministic and validated.
5. Pass/resource contracts are enforced and validated every frame.
6. Scene/stress coverage is broad enough for production confidence.
7. Defaults are tuned/locked per profile and documented.
8. Typed diagnostics exist for parser-free host/CI assertions.

## Exit Checklist

- [x] Planar scope/order contract warning and typed diagnostics are emitted.
  Evidence: `REFLECTION_PLANAR_SCOPE_CONTRACT`, `debugReflectionPlanarContractDiagnostics`.
- [x] Planar resource contract warning is emitted for active and fallback paths.
  Evidence: `REFLECTION_PLANAR_RESOURCE_CONTRACT`.
- [x] Planar stability envelope warning and breach gate are emitted.
  Evidence: `REFLECTION_PLANAR_STABILITY_ENVELOPE`, `REFLECTION_PLANAR_STABILITY_ENVELOPE_BREACH`.
- [x] Planar perf gate warning and breach gate are emitted.
  Evidence: `REFLECTION_PLANAR_PERF_GATES`, `REFLECTION_PLANAR_PERF_GATES_BREACH`.
- [x] Typed planar perf diagnostics are exposed.
  Evidence: `debugReflectionPlanarPerfDiagnostics`.
- [x] Strict timestamp-required perf mode is available for promotion gating.
  Evidence: `vulkan.reflections.planarPerfRequireGpuTimestamp=true` forces perf-gate breach when timestamp timing is unavailable.
- [x] Selective scope include/exclude policy is runtime-configurable and tested.
  Evidence: `vulkan.reflections.planarScopeInclude*` + integration tests.
- [x] Coverage matrix tests exist (interior/outdoor/multi-plane/dynamic-crossing).
  Evidence: `VulkanEngineRuntimeIntegrationTest#planarSceneCoverageMatrixEmitsContractsForInteriorOutdoorMultiAndDynamic`.
- [x] CI lockdown lane exists for planar contract/perf/stability checks.
  Evidence: `scripts/planar_ci_lockdown_full.sh`, `.github/workflows/ci.yml`.
- [x] Real-GPU planar pass timing path is wired (timestamp query when supported, estimate fallback otherwise).
  Evidence: Vulkan planar capture timestamps are recorded via query pool and surfaced as `timingSource=gpu_timestamp` when available.
- [ ] Timestamp caps/thresholds calibrated and locked from guarded real-Vulkan content runs.
  Remaining: promote per-profile/tier planar GPU-ms envelopes from real content baselines.
- [ ] Artifact gates are locked from real-content baseline and enforced fail-on-breach.
  Remaining: promote/lock thresholds from real runs for planar-specific stress scenes.
- [ ] Production signoff across real content set (long-run + camera/plane stress).
  Remaining: capture and lock approved goldens for reflection-planar/hybrid stress variants.
- [ ] OpenGL parity decision finalized for wishlist semantics.
  Remaining: either implement parity or keep explicit Vulkan-only `In` semantics.

## Execution Commands

Planar lockdown (mock Vulkan safe CI lane + compare harness):

```bash
./scripts/planar_ci_lockdown_full.sh
```

Manual targeted local verification:

```bash
mvn -pl engine-impl-vulkan -am test \
  -DskipITs \
  -Dtest=VulkanEngineRuntimeIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false

mvn -pl engine-host-sample -am test \
  -Ddle.compare.tests=true \
  -Ddle.compare.outputDir=artifacts/compare/planar-lockdown \
  -Dtest=BackendParityIntegrationTest#compareHarnessReflectionsPlanarSceneHasBoundedDiff+compareHarnessReflectionsHybridSceneHasBoundedDiff+compareHarnessReflectionsHiZProbeSceneHasBoundedDiff+compareHarnessReflectionsRtFallbackSceneHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## Status Note

As of 2026-02-18, Vulkan planar is advanced `Partial` with strong contracts/gates, CI coverage, and wired timestamp timing path, but not yet `In` due to threshold calibration and full production-content artifact lock-down.
