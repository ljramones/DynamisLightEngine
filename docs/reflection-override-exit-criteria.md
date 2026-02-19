# Reflection Per-Material Override Exit Criteria

Target: mark `Per-material reflection override (force probe-only for specific surfaces)` as `In` for Vulkan production path.

## Functional

- [x] Per-material override path is active and stable in Vulkan for `AUTO`, `PROBE_ONLY`, and `SSR_ONLY`.
- [x] Override mode encoding/consumption remains stable across scene reload and frame execution.
- [x] Reflection baseline warning includes override class counts.

## Contracts + Diagnostics

- [x] `REFLECTION_OVERRIDE_POLICY` emits count/scope diagnostics each reflection-active frame.
- [x] `REFLECTION_OVERRIDE_POLICY_ENVELOPE` emits ratio/threshold/gate diagnostics each reflection-active frame.
- [x] `REFLECTION_OVERRIDE_POLICY_ENVELOPE_BREACH` emits only after streak + cooldown conditions are satisfied.
- [x] Typed diagnostics (`debugReflectionOverridePolicyDiagnostics`) expose parser-free contract fields for CI.

## Runtime Controls

- [x] Backend options exist for override envelope thresholds and cooldown controls.
- [x] Bounds/fallback behavior is covered in runtime options tests.
- [x] Reflection profile defaults tune override envelope thresholds by profile.

## Validation

- [x] Integration tests cover mixed override counts and typed diagnostics parity.
- [x] Integration tests cover strict-threshold breach path.
- [x] Integration tests cover cooldown gating behavior.
- [x] Integration tests cover profile-default envelopes and explicit option override behavior.

## Documentation + Status

- [x] Reflection testing docs include override envelope checks and expected warnings.
- [x] Wishlist marks per-material reflection override as `In` for Vulkan path.
