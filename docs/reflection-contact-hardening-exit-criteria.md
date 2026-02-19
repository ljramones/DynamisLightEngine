# Reflection Contact-Hardening Exit Criteria

Target: mark `Contact-hardening reflections (roughness ramp near contact)` as `In` for Vulkan production path.

## Functional

- [x] Contact-hardening roughness-ramp behavior is active in Vulkan reflection resolve path.
- [x] Contact-hardening behavior is applied in reflection-active SSR-capable modes.
- [x] Base shader constants for contact-hardening are stable and documented.

## Contracts + Diagnostics

- [x] `REFLECTION_CONTACT_HARDENING_POLICY` emits per-frame policy diagnostics when reflections are active.
- [x] `REFLECTION_CONTACT_HARDENING_ENVELOPE_BREACH` emits only after streak/cooldown gate conditions are met.
- [x] Typed diagnostics (`debugReflectionContactHardeningDiagnostics`) expose parser-free contract fields.

## Runtime Controls

- [x] Backend options exist for:
  - `contactHardeningMinSsrStrength`
  - `contactHardeningMinSsrMaxRoughness`
  - `contactHardeningWarnMinFrames`
  - `contactHardeningWarnCooldownFrames`
- [x] Runtime options bounds/fallback behavior is covered in tests.
- [x] Reflection profile defaults tune contact-hardening thresholds by profile.

## Validation

- [x] Integration tests cover baseline policy warning and typed diagnostics.
- [x] Integration tests cover strict-threshold breach path.
- [x] Integration tests cover cooldown gating behavior.
- [x] Integration tests cover profile-default and explicit-override threshold paths.

## Documentation + Status

- [x] Reflection docs/testing guides include contact-hardening policy/gate checks.
- [x] Wishlist marks contact-hardening reflections as `In` for Vulkan scope.
