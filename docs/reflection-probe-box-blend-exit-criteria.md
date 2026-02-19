# Reflection Probe Box Projection + Blending Exit Criteria

Target: mark both items `In` for Vulkan production path:

- `Box-projected parallax-corrected probes`
- `Probe blending (distance, priority, volume-weighted)`

## Functional

- [x] Box-projected sampling path executes in Vulkan main-fragment reflection path.
- [x] Non-box projection fallback remains valid and stable.
- [x] Probe blending includes volume weighting and priority-aware overlap handling.
- [x] Distance shaping is active to reduce overlap bleed in shared volumes.

## Quality + Diagnostics

- [x] `REFLECTION_PROBE_QUALITY_SWEEP` includes:
  - box projection coverage ratio
  - invalid blend-distance/extents counts
  - overlap coverage metric
- [x] `REFLECTION_PROBE_QUALITY_ENVELOPE_BREACH` triggers for projection/blend envelope failures.
- [x] Typed runtime diagnostics expose these metrics for parser-free assertions.

## Policy + Tuning

- [x] Backend options include projection/blend quality thresholds.
- [x] Reflection profile defaults provide per-profile threshold baselines.
- [x] Telemetry profile warning payload includes new threshold values.

## Validation

- [x] Shader source contract test coverage updated.
- [x] Runtime options parse/bounds coverage updated.
- [x] Integration coverage includes forced projection/blend envelope breach case.
- [x] Vulkan targeted test suite passes for updated reflection probe stack.

## Documentation + Status

- [x] Checklist and exit criteria docs committed in repo.
- [x] Reflection docs/testing guide updated with new probe quality controls.
- [x] Wishlist marks both items `In` for Vulkan path.
