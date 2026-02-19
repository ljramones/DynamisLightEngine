# Reflection Transparency/Refractive Checklist (To `In`)

Scope date: 2026-02-19  
Scope target: Vulkan production path (`engine-impl-vulkan`)

## 0. Scope Lock

- [x] Promotion scope locked to Vulkan path for this cycle.
- [x] OpenGL parity remains a separate follow-up track.

## 1. Runtime Policy + Fallback

- [x] Convert transparency stage gate from preview-only to active fallback policy.
- [x] Enable in-frame transparency integration when transparent candidates exist (not RT-only).
- [x] Keep explicit fallback contract:
  - RT lane active: `rt_or_probe`
  - RT lane inactive: `probe_only`
- [x] Preserve deterministic behavior for no-candidate scenes.

## 2. Shader Integration Hardening

- [x] Ensure transparent/refractive composition runs under fallback policy.
- [x] Prevent non-transparent surfaces from receiving refractive compositing by threshold gating.
- [x] Keep override policy compatibility while allowing transparent probe-fallback behavior.

## 3. Diagnostics + Envelope Gates

- [x] Add typed transparency diagnostics for candidate class counts and gate state.
- [x] Add transparency policy warning payload with candidate composition metrics.
- [x] Add transparency envelope warning + breach gate with streak/cooldown:
  - probe-only override ratio on transparent candidates
  - fallback chain pressure state

## 4. Runtime Options + Profiles

- [x] Add backend options for transparency candidate threshold and envelope thresholds.
- [x] Add profile-default tuning for new transparency thresholds.
- [x] Include transparency thresholds in telemetry profile warning payload.

## 5. Tests

- [x] Update/extend integration tests for active fallback behavior when RT is unavailable.
- [x] Validate RT-active path remains `rt_or_probe`.
- [x] Add envelope breach test under strict transparency thresholds.

## 6. Docs + Wishlist

- [x] Add checklist/exit-criteria docs in repo.
- [x] Update reflections guide/testing guide for transparency production behavior.
- [x] Promote wishlist status `Transparent/refractive surface reflections` from `Partial` to `In` (Vulkan scoped).
