# Reflection Per-Material Override Checklist (To `In`)

Scope date: 2026-02-19  
Scope target: Vulkan production path (`engine-impl-vulkan`)

## 0. Scope Lock

- [x] Promotion scope locked to Vulkan path for this cycle.
- [x] OpenGL parity remains a separate follow-up track.

## 1. Functional Override Path

- [x] Support per-material override lanes in Vulkan (`AUTO`, `PROBE_ONLY`, `SSR_ONLY`).
- [x] Preserve deterministic reflection behavior across scene reloads and runtime mode transitions.
- [x] Maintain baseline warning visibility of override distribution counts.

## 2. Policy + Envelope Gates

- [x] Add configurable override envelope thresholds:
  - probe-only ratio max
  - SSR-only ratio max
  - other-mode count max
  - warn min frames / cooldown
- [x] Emit override envelope diagnostics every reflection-active frame.
- [x] Emit cooldown-gated breach warning under sustained threshold violations.

## 3. Typed Diagnostics

- [x] Expose typed override-policy diagnostics for:
  - counts and ratios
  - threshold values
  - streak/cooldown/breach state
  - selective planar excludes policy string

## 4. Profile + Runtime Option Integration

- [x] Add runtime options parsing + bounds fallback tests for new override envelope controls.
- [x] Add profile-default thresholds (`performance`, `quality`, `stability`) for override envelope.
- [x] Include override thresholds in profile telemetry warning payload.
- [x] Validate explicit backend options override profile defaults.

## 5. Tests

- [x] Integration test for mixed override distribution + typed diagnostics parity.
- [x] Integration test forcing envelope breach under strict thresholds.
- [x] Integration test for cooldown behavior (no immediate repeat breach).
- [x] Integration test for profile defaults + explicit option override path.

## 6. Docs + Wishlist

- [x] Add dedicated completion checklist + exit criteria docs.
- [x] Update reflection testing docs with override envelope checks and warning expectations.
- [x] Promote wishlist status `Per-material reflection override` from `Partial` to `In` (Vulkan scoped).
