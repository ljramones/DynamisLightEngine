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

## 4. Execution Commands

Unit policy + matrix checks:
```bash
mvn -pl engine-impl-vulkan -am test \
  -Dtest=VulkanEngineRuntimeLightingMapperTest,VulkanShadowMatrixBuilderTest \
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
