# GI Phase 2 Execution Checklist

Scope: convert GI phase-1 contract/planner scaffolding into production Vulkan execution paths with promotion gates.

## Phase 2A: SSGI Path

- [x] Add SSGI pass contribution with explicit graph IO contract (`scene_depth`, `scene_normal`, `scene_color` -> `gi_ssgi_buffer`) in GI v2 descriptor declarations.
- [x] Add SSGI shader module realization in post/lighting composition path.
- [x] Add SSGI envelope warnings (quality/perf) + cooldown/streak gating (`GI_SSGI_ENVELOPE`, `GI_SSGI_ENVELOPE_BREACH`, `GI_SSGI_PROMOTION_READY`).
- [x] Add typed SSGI diagnostics accessor fields under `GiPromotionDiagnostics` (expected/active ratio, thresholds, cooldown/streak, promotion state).
- [x] Add dedicated SSGI lockdown runner (`scripts/gi_phase2_ssgi_lockdown.sh`) and CI lane (`gi-phase2-ssgi-lockdown`) for phase-2A gating tests.
- [x] Add integration scenes for disocclusion, thin geometry, and camera-motion stability.
- [x] Add lockdown lane for SSGI promotion (`scripts/gi_phase2_ssgi_lockdown.sh`).

## Phase 2B: Probe Grid Path

- [x] Add probe-grid resource lifecycle (persistent probe volume buffer + runtime update cadence telemetry/envelope policy).
- [x] Add probe-grid shading hook integration for indirect diffuse.
- [x] Add probe-grid streaming/update envelope diagnostics and breach warnings (`GI_PROBE_GRID_ENVELOPE`, `GI_PROBE_GRID_ENVELOPE_BREACH`, `GI_PROBE_GRID_PROMOTION_READY`).
- [x] Add typed probe-grid diagnostics for parser-free CI assertions (extended `GiPromotionDiagnostics` probe-grid fields).
- [x] Add lockdown lane for probe-grid promotion (`scripts/gi_phase2_probe_lockdown.sh`) + CI lane (`gi-phase2-probe-lockdown`).

## Phase 2C: RT Detail Lane

- [x] Add RT GI single-bounce lane policy with explicit fallback signaling (`GI_RT_DETAIL_FALLBACK_CHAIN`) for RT-active vs SSGI-fallback visibility.
- [x] Add RT-detail diagnostics and promotion gates for quality/perf envelope (`GI_RT_DETAIL_POLICY_ACTIVE`, `GI_RT_DETAIL_ENVELOPE`, `GI_RT_DETAIL_ENVELOPE_BREACH`, `GI_RT_DETAIL_PROMOTION_READY`).
- [x] Add hybrid composition lane (probe + SSGI + RT detail) envelope diagnostics (`GI_HYBRID_COMPOSITION`, `GI_HYBRID_COMPOSITION_BREACH`).
- [x] Add lockdown lane for RT/hybrid GI promotion (`scripts/gi_phase2_rt_lockdown.sh`) + CI lane (`gi-phase2-rt-lockdown`).

## Phase 2D: Promotion + CI

- [x] Add consolidated GI phase-2 promotion warning (`GI_PHASE2_PROMOTION_READY`) for SSGI/probe/RT-hybrid.
- [x] Expose typed consolidated GI promotion diagnostics (`phase2PromotionReady` in `GiPromotionDiagnostics`).
- [x] Add full GI phase-2 lockdown bundle (`scripts/gi_phase2_lockdown_full.sh`).
- [x] Add CI lanes for SSGI, probe, RT, and full GI phase-2 bundle.

## Exit Criteria

- [x] All GI phase-2 lockdown scripts pass in CI (lanes wired) and in local replay (`scripts/gi_phase2_lockdown_full.sh`).
- [x] No structure guardrail violations.
- [x] `wish_list.md` GI rows updated with Vulkan-scope status and caveats.
