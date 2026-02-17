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

## 5. Pass/Fail Criteria
- Unit tests pass for selection policy and matrix stability.
- Compare scenes pass configured thresholds for target profile.
- No unexpected shadow warning regressions for selected quality tier.

## 6. Known Gaps
- Per-light shadow atlas/array rendering for multiple simultaneous local shadow casters is still pending.
- Need dedicated long-run shimmer/flicker analysis for shadow-only camera sweeps.

## 7. Planned Additions
- Add targeted high-motion shadow sweeps (fast pan + thin-geo + animated casters).
- Add cadence tests (hero lights full-rate vs distant lights throttled update rates).
- Add static-cache correctness tests (no stale shadows after object/light state transitions).
- Add CI matrix axis for shadow depth format (`D16_UNORM`, `D32_SFLOAT`) and publish drift deltas in reports.
