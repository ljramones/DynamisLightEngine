# Reflections Vulkan Closeout

Closeout date: 2026-02-19  
Scope: Vulkan production path (`engine-impl-vulkan`)

This document is the single reference for the completed reflections promotion wave.  
All reflection wishlist items are now marked `In` for Vulkan scope.

## Scope Notes

- Promotion scope is Vulkan-only for this cycle.
- OpenGL parity remains tracked separately.

## Capability Checklist Index

1. Planar reflections
- Checklist/criteria: `docs/planar-in-exit-criteria.md`

2. RT reflections + RT/SSR hybrid
- Checklist: `docs/rt-reflections-in-checklist.md`
- Exit criteria: `docs/rt-reflections-in-exit-criteria.md`

3. Probe box projection + blending
- Checklist: `docs/reflection-probe-box-blend-in-checklist.md`
- Exit criteria: `docs/reflection-probe-box-blend-exit-criteria.md`

4. Probe streaming
- Checklist: `docs/reflection-probe-streaming-in-checklist.md`
- Exit criteria: `docs/reflection-probe-streaming-exit-criteria.md`

5. Transparent/refractive reflections
- Checklist: `docs/reflection-transparency-in-checklist.md`
- Exit criteria: `docs/reflection-transparency-exit-criteria.md`

6. Per-material reflection override
- Checklist: `docs/reflection-override-in-checklist.md`
- Exit criteria: `docs/reflection-override-exit-criteria.md`

7. Contact-hardening reflections
- Checklist: `docs/reflection-contact-hardening-in-checklist.md`
- Exit criteria: `docs/reflection-contact-hardening-exit-criteria.md`

## Promotion Commit Ledger (Key Milestones)

- `9fa1b50` planar promotion finalized (Vulkan-scoped `In`)
- `7a433e3` RT reflections promotion-ready gate + `In`
- `2736ffa` probe streaming envelope gates
- `2099078` probe box projection/blend hardening
- `88590b0` transparency reflection fallback/gates finalized
- `7230abd` per-material override promoted to `In`
- `7fcea30` contact-hardening promoted to `In`

## Validation Baseline

- Primary reflection runtime/tests: `VulkanEngineRuntimeIntegrationTest`
- Runtime options contracts: `VulkanRuntimeOptionsTest`
- Shader contract checks: `VulkanShaderSourcesTest`
- Optional signoff runners:
  - `scripts/planar_real_gpu_signoff.sh`
  - `scripts/rt_reflections_real_gpu_signoff.sh`
  - `scripts/rt_reflections_real_longrun_signoff.sh`
  - `scripts/rt_reflections_in_promotion_bundle.sh`

## Wishlist Sync

- Source of truth: `wish_list.md`
- Reflections entries are all `In` (Vulkan-scoped).
