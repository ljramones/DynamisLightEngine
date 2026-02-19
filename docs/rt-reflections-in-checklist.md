# RT Reflections Checklist (To `In`)

Scope date: 2026-02-18  
Scope target: Vulkan production path (`engine-impl-vulkan`)

This checklist tracks all work required to promote RT reflections from `Partial` to `In`.

## 0. Scope Lock

- [x] Lock `In` scope statement (Vulkan-only vs engine-wide).
- [x] If Vulkan-only: update docs/wishlist with explicit scope note.
- [x] If engine-wide: define OpenGL status policy for RT reflections.
  Current policy: RT reflections `In` is Vulkan-scoped; OpenGL RT parity is out of scope for this promotion.

## 1. Dedicated RT Execution Path

- [x] Add strict dedicated-path policy gate (`rtRequireDedicatedPipeline`).
- [x] Add dedicated-path diagnostics and breach warnings.
- [x] Add mock preview activation switch (`rtDedicatedPipelineEnabled`) for contract exercise.
- [x] Make dedicated preview activation capability-conditioned in real Vulkan signoff lanes.
- [x] Add RT BLAS/TLAS/SBT lifecycle telemetry scaffolding (`REFLECTION_RT_PIPELINE_LIFECYCLE`, typed runtime diagnostics).
- [x] Implement true dedicated hardware RT path activation (non-mock).
- [x] Wire BLAS/TLAS lifecycle for reflection path (build/refit/compaction).
- [x] Wire SBT lifecycle (create/update/rebuild on scene/material changes).
- [x] Validate dedicated path over approved real-content scenes.

## 2. Single-Bounce Production Correctness

- [x] Validate hit-shading parity vs PBR expectations (Fresnel/roughness/metallic).
- [x] Validate fallback behavior for misses/disocclusion (`rt -> ssr -> probe`).
- [x] Add scene gates for specular edge cases (grazing, thin geometry, off-screen misses).

## 3. Multi-Bounce Hardening

- [x] Lock per-tier bounce budgets and scheduling policy.
- [x] Add stability controls (termination/firefly clamp/energy bound).
- [x] Lock multi-bounce perf envelopes for all blessed profiles.

## 4. Denoise Hardening

- [x] Lock spatial denoise envelope gates.
- [x] Lock temporal denoise envelope gates for camera/disocclusion stress.
- [x] Add typed denoise diagnostics for CI assertions.

## 5. Hybrid Composition Lockdown

- [x] Add hybrid composition telemetry diagnostics/warnings (`REFLECTION_RT_HYBRID_COMPOSITION`, typed runtime accessor).
- [x] Lock roughness split policy (`RT` vs `SSR`) and thresholds.
- [x] Lock miss/fallback blend policy (`SSR`/`probe`) and envelope gates.
- [x] Add hybrid artifact gates for seams and temporal instability.

## 6. Transparent/Refractive Integration

- [x] Finalize transparent/refractive material classes on top of RT path.
- [x] Add ordering/sorting edge-case coverage scenes.
- [x] Lock fallback policy (`rt_or_probe`) and known unsupported cases.

## 7. Performance Gates

- [x] Add RT perf warning/breach gates and typed diagnostics.
- [x] Lock per-tier RT GPU-ms caps on real hardware.
- [x] Add AS update/build budget gates.
- [x] Add RT resource memory budget gates.

## 8. Artifact/Stability Gates

- [x] Add temporal shimmer/ghosting/disocclusion gates for RT content.
- [x] Add long-run stability drift/noise accumulation gates.
- [x] Add per-profile envelope defaults for RT stability.

## 9. CI and Signoff

- [x] Maintain RT lockdown lane (`scripts/rt_reflections_ci_lockdown_full.sh`).
- [x] Maintain guarded real-Vulkan signoff lane (`scripts/rt_reflections_real_gpu_signoff.sh`).
- [x] Add long-run real-Vulkan RT signoff lane (duration stress).
- [x] Add promotion command bundle for final `In` signoff replay.

## 10. Final Promotion

- [x] Update `docs/rt-reflections-in-exit-criteria.md` all remaining items to `[x]`.
- [x] Update reflections testing guide with final thresholds.
- [x] Update `wish_list.md` RT reflections status from `Partial` to `In`.
