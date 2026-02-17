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
  -Dtest=VulkanEngineRuntimeLightingMapperTest,VulkanShadowMatrixBuilderTest \
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
- D16 vs D32 runs show no unacceptable acne/peter-panning divergence for locked profiles.
- OpenGL lifecycle tests stay green with local shadow-atlas loop active:
  - `OpenGlEngineRuntimeLifecycleTest`
- Vulkan integration tests surface explicit baseline warning when multiple local shadow casters are requested but render-side multi-local atlas/cubemap rollout is still pending:
  - `SHADOW_LOCAL_RENDER_BASELINE`

## 6. Known Gaps
- Vulkan per-light shadow atlas/array rendering for multiple simultaneous local shadow casters is still pending.
- Vulkan multi-point cubemap shadow rendering (>1 concurrent point-shadow map) is still pending.
- Need dedicated long-run shimmer/flicker analysis for shadow-only camera sweeps.

## 7. Planned Additions
- Add targeted high-motion shadow sweeps (fast pan + thin-geo + animated casters).
- Add cadence tests (hero lights full-rate vs distant lights throttled update rates).
- Add static-cache correctness tests (no stale shadows after object/light state transitions).
- Add CI matrix axis for shadow depth format (`D16_UNORM`, `D32_SFLOAT`) and publish drift deltas in reports.
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
