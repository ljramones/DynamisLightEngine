# Reflection Probe Box Projection + Blending Checklist (To `In`)

Scope date: 2026-02-19  
Scope target: Vulkan production path (`engine-impl-vulkan`)

## 0. Scope Lock

- [x] Promotion scope locked to Vulkan path for this cycle.
- [x] OpenGL parity remains tracked separately.

## 1. Shader Hardening

- [x] Harden box-projection ray/AABB intersection for invalid/edge ray directions.
- [x] Keep non-box fallback path stable when projection is disabled or invalid.
- [x] Add distance-weighted contribution shaping inside probe volumes.
- [x] Preserve priority-aware blend contribution behavior under overlap.

## 2. Probe Quality Envelope

- [x] Add quality diagnostics for box-projection coverage ratio.
- [x] Add quality diagnostics for invalid blend distance/extents.
- [x] Add overlap coverage ratio metric for transition/bleed risk.
- [x] Extend envelope breach reasons to include projection/blend validity failures.

## 3. Runtime Policy + Thresholds

- [x] Add backend options for projection/blend quality thresholds.
- [x] Add profile defaults for new projection/blend thresholds.
- [x] Include new thresholds in `REFLECTION_TELEMETRY_PROFILE_ACTIVE` summary.

## 4. Tests

- [x] Update shader source contract tests for box-projection/blending helpers.
- [x] Extend runtime options parsing/bounds tests for new thresholds.
- [x] Add integration test that forces projection/blend envelope breach and validates typed diagnostics.

## 5. Docs + Wishlist

- [x] Add dedicated completion checklist + exit criteria docs.
- [x] Update reflections guide/runtime notes for hardened box-projection + blending envelopes.
- [x] Update reflections testing guide with new probe quality knobs and CI assertions.
- [x] Promote wishlist statuses for box-projected probes and probe blending from `Partial` to `In` (Vulkan scoped).
