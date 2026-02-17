# Shadows Testing Design

Last updated: February 17, 2026

## 1. Goal
Validate shadow stability, policy selection, and quality-tier fallback behavior across OpenGL and Vulkan.

## 2. Coverage
- Directional/spot/point shadow selection policy.
- Tier budget behavior (`maxShadowedLocalLights`).
- Bias scaling behavior (normal/slope scales per light type).
- Shadow matrix determinism for repeated identical inputs.
- Integration parity in compare harness shadow-heavy scenes.

## 3. Primary Metrics
- `compare.diffMetric`
- Shadow-related warning stream:
  - `SHADOW_POLICY_ACTIVE`
  - `SHADOW_QUALITY_DEGRADED`
  - `SHADOW_LOCAL_RENDER_BASELINE`
  - `SHADOW_RT_PATH_REQUESTED`
  - `SHADOW_RT_PATH_FALLBACK_ACTIVE`
  - `SHADOW_RT_BVH_PIPELINE_PENDING`
- Shadow matrix deterministic equality in unit tests.
- Shadow memory telemetry:
  - atlas allocation bytes
  - per-frame shadow update/upload bytes
- Depth-format divergence:
  - compare `D16_UNORM` and `D32_SFLOAT` shadow runs for acne/peter-panning drift.

## 4. Execution Commands

End-to-end shadow CI matrix automation:
```bash
./scripts/shadow_ci_matrix.sh
```

CI always-on rollout:
- GitHub Actions `shadow-matrix` runs on `push`/`pull_request` with mock Vulkan safety.
- Weekly scheduled run (`schedule`) also executes long-run AA/shadow motion sampling.
- GitHub Actions `shadow-real-longrun-guarded` now runs on `push`/`pull_request`/`schedule` and emits guarded threshold-lock recommendations when real Vulkan is available.
- GitHub Actions `shadow-production-quality-sweeps` now runs on `push`/`pull_request`/`schedule` and executes production profile sweeps (`pcf`, `pcss/contact`, `vsm`, `evsm`, `rt optional`, `rt bvh`, `rt bvh_dedicated`) with guarded threshold-lock output.
- Manual `workflow_dispatch` toggles:
  - `run_shadow_real_matrix=true`
  - `run_shadow_longrun=true`
  - `run_shadow_quality_sweeps=true`
  - `run_shadow_quality_sweeps_strict_bvh=true`

Optional real Vulkan depth-format matrix + long-run:
```bash
DLE_SHADOW_CI_REAL_MATRIX=1 \
DLE_SHADOW_CI_LONGRUN=1 \
DLE_COMPARE_VULKAN_MODE=real \
./scripts/shadow_ci_matrix.sh
```

Unit policy + matrix checks:
```bash
mvn -pl engine-impl-vulkan -am test \
  -Dtest=VulkanEngineRuntimeLightingMapperTest,VulkanRuntimeOptionsTest,VulkanShadowMatrixBuilderTest,VulkanEngineRuntimeIntegrationTest#shadowAllocatorTelemetryShowsReuseAcrossFrames+shadowQualityPathRequestsEmitTrackingWarnings+bvhShadowModeRequestEmitsExplicitFallbackContext+pcssShadowQualityRequestTracksActivePathWithoutMomentWarning \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Shadow-atlas planner checks:
```bash
mvn -pl engine-impl-common -am test \
  -Dtest=ShadowAtlasPlannerTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Shadow parity/stress compare:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessShadowCascadeStressHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Fog/smoke/shadow integration stress:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessFogSmokeShadowPostStressHasBoundedDiff \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Depth-format divergence check (real Vulkan):
```bash
DLE_COMPARE_VULKAN_MODE=real \
DLE_COMPARE_VULKAN_SHADOW_DEPTH_FORMAT=d16 \
./scripts/aa_rebaseline_real_mac.sh

DLE_COMPARE_VULKAN_MODE=real \
DLE_COMPARE_VULKAN_SHADOW_DEPTH_FORMAT=d32 \
./scripts/aa_rebaseline_real_mac.sh
```

Scheduler override check (real Vulkan):
```bash
DLE_COMPARE_VULKAN_MODE=real \
DLE_COMPARE_EXTRA_MVN_ARGS="-Dvulkan.shadow.maxLocalShadowLayers=24 -Dvulkan.shadow.maxShadowFacesPerFrame=6" \
./scripts/aa_rebaseline_real_mac.sh
```

Shadow quality profile sweep using compare harness:
```bash
DLE_COMPARE_VULKAN_MODE=mock \
DLE_COMPARE_EXTRA_MVN_ARGS="-Dvulkan.shadow.filterPath=evsm -Dvulkan.shadow.momentBlend=1.2 -Dvulkan.shadow.momentBleedReduction=1.2" \
./scripts/aa_rebaseline_real_mac.sh
```

Production quality parity sweeps + threshold lock (guarded real Vulkan by default):
```bash
./scripts/shadow_production_quality_sweeps.sh
```

Optional strict BVH enforcement during production sweeps (capability must exist):
```bash
DLE_SHADOW_PROD_SWEEP_RT_BVH_STRICT=1 \
./scripts/shadow_production_quality_sweeps.sh
```

Optional one-shot promotion into repo defaults after sweep lock:
```bash
DLE_SHADOW_PROD_SWEEP_PROMOTE_MODE=real \
./scripts/shadow_production_quality_sweeps.sh
```

Cadence scheduler check (real Vulkan):
```bash
DLE_COMPARE_VULKAN_MODE=real \
DLE_COMPARE_EXTRA_MVN_ARGS="-Dvulkan.shadow.scheduler.enabled=true -Dvulkan.shadow.scheduler.heroPeriod=1 -Dvulkan.shadow.scheduler.midPeriod=2 -Dvulkan.shadow.scheduler.distantPeriod=6" \
./scripts/aa_rebaseline_real_mac.sh
```

Long-run motion/shimmer sweep (real Vulkan):
```bash
./scripts/aa_longrun_motion_sampling_mac.sh
```

## 5. Pass/Fail Criteria
- Unit tests pass for selection policy and matrix stability.
- Compare scenes pass configured thresholds for target profile.
- No unexpected shadow warning regressions for selected quality tier.
- `SHADOW_POLICY_ACTIVE` contains atlas telemetry fields:
  - `atlasMemoryD16Bytes`
  - `atlasMemoryD32Bytes`
  - `shadowUpdateBytesEstimate`
  - `shadowMomentAtlasBytesEstimate`
- D16 vs D32 runs show no unacceptable acne/peter-panning divergence for locked profiles.
- OpenGL lifecycle tests stay green with local shadow-atlas loop active:
  - `OpenGlEngineRuntimeLifecycleTest`
- Vulkan integration tests surface explicit rollout warning context when local shadow requests exceed currently guaranteed render parity paths:
  - `SHADOW_LOCAL_RENDER_BASELINE`
- Vulkan integration tests now also validate multi-spot render policy reporting (`renderedLocalShadows`, `renderedSpotShadows`) and quality-path request tracking fields (`filterPath`, `contactShadows`, `rtMode`).
- Vulkan mapper tests now validate capability-gated RT active state (`rtShadowActive`) when traversal extensions are available.
- Vulkan integration tests now validate strict BVH request behavior:
  - `VulkanEngineRuntimeIntegrationTest#strictBvhModeFailsFastWhenBvhCapabilityIsUnavailable`
  - `VulkanEngineRuntimeIntegrationTest#strictDedicatedBvhModeFailsFast`
- Vulkan shader/uniform tests now validate packed RT sample count metadata and runtime RT tuning decode (`shadowRtDenoiseStrength`, `shadowRtRayLength`, `shadowRtSampleCount`) feeding traversal/denoise shaping.
- Vulkan runtime options tests also validate shadow quality tuning clamps:
  - `vulkan.shadow.pcssSoftness`
  - `vulkan.shadow.momentBlend`
  - `vulkan.shadow.momentBleedReduction`
  - `vulkan.shadow.contactStrength`
  - `vulkan.shadow.contactTemporalMotionScale`
  - `vulkan.shadow.contactTemporalMinStability`
  - `vulkan.shadow.rtDenoiseStrength`
  - `vulkan.shadow.rtRayLength`
  - `vulkan.shadow.rtSampleCount`
- Shader regression checks now include:
  - neighborhood-weighted + edge-aware moment denoise path (`wideMoments` sampling + `denoiseEdgeFactor`)
  - deep wide bilateral moment consistency pass (`deepMoments` + `consistency` blend)
  - ultra-wide bilateral refinement + moment-neighborhood bounds clamp (`ultraMoments`, `momentBounds`, `clampMomentsToBounds`)
  - leak-risk adaptive anti-bleed shaping (`leakRisk`, `antiBleedMix`)
  - PCSS secondary blocker-ring refinement and penumbra neighborhood balancing (`refineRadius`, `refinedBlockerDepth`, `neighDiag`)
  - motion-adaptive contact temporal stabilization shaping (`contactTemporalStability`)
- Vulkan integration tests validate multi-point cubemap concurrency policy under ULTRA and override budgets:
  - `VulkanEngineRuntimeIntegrationTest#multiPointLocalShadowSceneUsesConcurrentPointCubemapBudgetAtUltra`
  - `VulkanEngineRuntimeIntegrationTest#multiPointLocalShadowSceneHonorsOverrideForThreeConcurrentCubemaps`
- Vulkan unit coverage now includes local point-light cubemap matrix generation from per-light layer assignments:
  - `VulkanShadowMatrixBuilderTest#localPointShadowAssignmentBuildsCubemapFaceMatrices`
- For `vsm|evsm` requests, verify warning stream includes:
  - `SHADOW_POLICY_ACTIVE` fields: `runtimeFilterPath` matches requested `vsm|evsm`
  - `SHADOW_POLICY_ACTIVE` field: `momentFilterEstimateOnly=false`
  - `SHADOW_POLICY_ACTIVE` field: `momentPipelineRequested=true` (request-state must be explicit in both mock and real Vulkan runs)
  - `SHADOW_POLICY_ACTIVE` fields: `momentPipelineActive=true` on real Vulkan
  - `SHADOW_MOMENT_PIPELINE_PENDING` expected in mock mode when requested path is unavailable
  - `SHADOW_POLICY_ACTIVE` fields: `momentResourceAllocated`, `momentResourceFormat`
  - `SHADOW_POLICY_ACTIVE` fields: `momentInitialized=true`, `momentPhase=active`
  - `SHADOW_POLICY_ACTIVE` field: `momentPhase` should not remain `pending` for real Vulkan `vsm|evsm`.
  - As hardware RT path evolves, keep current moment-pipeline regression checks plus scene diff/temporal metrics.
- Vulkan policy checks now include concurrent point-cubemap scheduling counters (`renderedPointShadowCubemaps`) for tier-bounded multi-point coverage.
- Verify current tier cap behavior explicitly:
  - `HIGH` should cap to `1` rendered point cubemap (`6` shadow passes).
  - `ULTRA` should cap to `2` rendered point cubemaps (`12` shadow passes).
- Verify scheduler override behavior:
  - `vulkan.shadow.maxShadowedLocalLights` increases local shadow-light budget above tier default when set.
  - `HIGH` with `vulkan.shadow.maxLocalShadowLayers=12` can render `2` point cubemaps.
  - `vulkan.shadow.maxShadowFacesPerFrame=6` caps point shadow passes to one cubemap.
  - `ULTRA` with `vulkan.shadow.maxLocalShadowLayers=24` can schedule up to `4` point cubemaps (subject to local-light budget).
- Verify cadence scheduler behavior:
  - policy warning includes scheduler fields (`schedulerEnabled`, `schedulerPeriodHero`, `schedulerPeriodMid`, `schedulerPeriodDistant`, `shadowSchedulerFrameTick`).
  - at fixed face budget, rendered local shadow counts stay within cadence-gated budget envelope.
  - warning telemetry emits `renderedShadowLightIds`; verify scheduler continues to render at least one local light while deferring overflow work under throttled cadence.
  - integration check validates defer behavior under point-cubemap face budget pressure:
    - `VulkanEngineRuntimeIntegrationTest#shadowSchedulerCadenceDefersPointWorkUnderFaceBudget`
  - backlog telemetry is present: `deferredShadowLightCount`, `deferredShadowLightIds`.
  - allocator telemetry is present: `shadowAllocatorAssignedLights`, `shadowAllocatorReusedAssignments`, `shadowAllocatorEvictions`.
  - integration check validates reuse remains active across reordered light lists:
    - `VulkanEngineRuntimeIntegrationTest#shadowAllocatorTelemetryShowsReuseAcrossFrames`
- Verify directional texel-snap controls:
  - `vulkan.shadow.directionalTexelSnapEnabled` toggles runtime snapping path.
  - `vulkan.shadow.directionalTexelSnapScale` is clamped to configured bounds and reported in `SHADOW_POLICY_ACTIVE`.

## 6. Known Gaps
- Vulkan multi-local spot shadow rendering is live within current layer budget; full per-light atlas/cubemap parity for all local types is still pending.
- Vulkan tier/override-bounded multi-point cubemap concurrency is now covered (`HIGH`: 1, `ULTRA`: 2, overrides up to scheduler/local-light limits); further scalability beyond current scheduler/budget caps remains pending.
- Dedicated moment-atlas write/prefilter VSM/EVSM is active in Vulkan; hardware RT traversal/denoise is still pending.
- Current RT path is capability-gated hybrid traversal with tunable denoise/ray-length/sample-count shaping; dedicated BVH traversal + full denoiser chain is still pending.
- Need dedicated long-run shimmer/flicker analysis for shadow-only camera sweeps.

## 7. Planned Additions
- Add targeted high-motion shadow sweeps (fast pan + thin-geo + animated casters).
- Expand cadence tests (hero lights full-rate vs distant lights throttled update rates) from current baseline checks into longer frame windows.
- Expand static-cache correctness tests (beyond current allocator reuse/eviction checks) to include stale-shadow scene transitions.
- Add CI matrix axis for shadow depth format (`D16_UNORM`, `D32_SFLOAT`) and publish drift deltas in reports.
- Keep guarded real-Vulkan shadow long-run sweep enabled in CI (`scripts/shadow_real_longrun_guarded.sh`) so hosts with real Vulkan support continuously sample shadow stress scenes.
- Keep guarded threshold-lock generation enabled for real Vulkan outputs (`scripts/shadow_lock_thresholds_guarded.sh`) so repeated stable runs produce updated lock recommendations.
- Keep production profile parity sweeps enabled in CI (`scripts/shadow_production_quality_sweeps.sh`) so `pcss/contact` and `vsm/evsm` lanes are repeatedly sampled and re-lock recommendations are generated from stable real-Vulkan datasets.
- Add CI gate that verifies `SHADOW_LOCAL_RENDER_BASELINE` clears once Vulkan multi-local render parity lands.

Cadence/static-cache planned test shape:
- Cadence behavior:
  - OpenGL local-shadow atlas currently uses tiered cadence policy (`hero:1`, `mid:2`, `distant:4`) with slot-cache reuse.
  - verify hero slots update every frame and distant slots reuse cached pages between updates.
- Static-cache correctness:
  - OpenGL local-shadow slot cache is invalidated on mesh/reallocation/budget changes.
  - verify unchanged static scene reuses cached slot data.
  - verify cache invalidates on static transform/material/light changes.
  - verify no stale silhouettes after invalidation.

Long-run motion/shimmer expansion notes:
- Add camera-rail shadow sweeps (slow pan, fast pan, roll+pitch combinations).
- Add animated caster set (thin props, alpha foliage, emissive movers) for shimmer sensitivity.
- Track drift windows, not only single-frame peaks, when deciding regressions.
