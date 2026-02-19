# Reflection Contact-Hardening Checklist (To `In`)

Scope date: 2026-02-19  
Scope target: Vulkan production path (`engine-impl-vulkan`)

## 0. Scope Lock

- [x] Promotion scope locked to Vulkan path for this cycle.
- [x] OpenGL parity remains a separate follow-up track.

## 1. Functional Path

- [x] Contact-hardening roughness-ramp logic is active in Vulkan post reflection resolve.
- [x] SSR/planar contact hardening remains active for reflection modes with SSR lane (`ssr`, `planar`, `hybrid`, `rt_hybrid`).
- [x] Existing shader constants remain stable:
  - depth window: `0.01..0.16`
  - roughness ramp min: `0.58`
  - SSR boost max: `1.22`
  - planar boost max: `1.10`

## 2. Policy + Envelope Gates

- [x] Add configurable policy envelope for expected contact-hardening viability:
  - min SSR strength
  - min SSR max roughness
  - warn min frames / cooldown
- [x] Emit policy diagnostics every reflection-active frame.
- [x] Emit cooldown-gated breach warning under sustained policy violations.

## 3. Typed Diagnostics

- [x] Expose typed runtime diagnostics for contact-hardening policy:
  - active flag
  - estimated strength
  - policy thresholds
  - streak/cooldown/breach state

## 4. Runtime Options + Profiles

- [x] Add backend options parsing/bounds tests for contact-hardening policy thresholds.
- [x] Add profile defaults (`performance`, `quality`, `stability`) for policy thresholds.
- [x] Include thresholds in telemetry profile warning payload.
- [x] Validate explicit backend options override profile defaults.

## 5. Tests

- [x] Integration test for policy warning + typed diagnostics presence.
- [x] Integration test forcing strict-threshold breach.
- [x] Integration test for cooldown behavior (no immediate repeat breach).
- [x] Integration tests for profile defaults and explicit override behavior.

## 6. Docs + Wishlist

- [x] Add dedicated checklist/exit-criteria docs.
- [x] Update reflections guide/testing docs with contact-hardening policy warnings and thresholds.
- [x] Promote wishlist status `Contact-hardening reflections` from `Partial` to `In` (Vulkan scoped).
