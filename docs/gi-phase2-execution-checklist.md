# GI Phase 2 Execution Checklist

Scope: convert GI phase-1 contract/planner scaffolding into production Vulkan execution paths with promotion gates.

## Phase 2A: SSGI Path

- [x] Add SSGI pass contribution with explicit graph IO contract (`scene_depth`, `scene_normal`, `scene_color` -> `gi_ssgi_buffer`) in GI v2 descriptor declarations.
- [ ] Add SSGI shader module realization in post/lighting composition path.
- [x] Add SSGI envelope warnings (quality/perf) + cooldown/streak gating (`GI_SSGI_ENVELOPE`, `GI_SSGI_ENVELOPE_BREACH`, `GI_SSGI_PROMOTION_READY`).
- [x] Add typed SSGI diagnostics accessor fields under `GiPromotionDiagnostics` (expected/active ratio, thresholds, cooldown/streak, promotion state).
- [ ] Add integration scenes for disocclusion, thin geometry, and camera-motion stability.
- [ ] Add lockdown lane for SSGI promotion (`scripts/gi_phase2_ssgi_lockdown.sh`).

## Phase 2B: Probe Grid Path

- [ ] Add probe-grid resource lifecycle (persistent probe volume texture/buffer + update cadence).
- [ ] Add probe-grid shading hook integration for indirect diffuse.
- [ ] Add probe-grid streaming/update envelope diagnostics and breach warnings.
- [ ] Add typed probe-grid diagnostics for parser-free CI assertions.
- [ ] Add lockdown lane for probe-grid promotion (`scripts/gi_phase2_probe_lockdown.sh`).

## Phase 2C: RT Detail Lane

- [ ] Add RT GI single-bounce execution lane with explicit fallback signaling.
- [ ] Add denoise-stage diagnostics and promotion gates for RT GI quality/perf.
- [ ] Add hybrid composition lane (probe + SSGI + RT detail) envelope diagnostics.
- [ ] Add lockdown lane for RT/hybrid GI promotion (`scripts/gi_phase2_rt_lockdown.sh`).

## Phase 2D: Promotion + CI

- [ ] Add consolidated GI phase-2 promotion warning (`GI_PHASE2_PROMOTION_READY`) for SSGI/probe/RT-hybrid.
- [ ] Expose typed consolidated GI promotion diagnostics.
- [ ] Add full GI phase-2 lockdown bundle (`scripts/gi_phase2_lockdown_full.sh`).
- [ ] Add CI lanes for SSGI, probe, RT, and full GI phase-2 bundle.

## Exit Criteria

- [ ] All GI phase-2 lockdown scripts pass in CI.
- [ ] No structure guardrail violations.
- [ ] `wish_list.md` GI rows updated with Vulkan-scope status and caveats.
