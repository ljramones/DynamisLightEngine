# Lighting Capability V2 Checklist

Status: In Progress (Vulkan-first)

## Phase 1: Contract + Planner + Telemetry
- [x] Add `vulkan.lighting` v2 descriptor with baseline and expansion modes.
- [x] Add deterministic lighting planner with parser-friendly signals.
- [x] Emit per-frame `LIGHTING_CAPABILITY_MODE_ACTIVE` warning payload.
- [x] Expose backend-agnostic typed runtime diagnostics: `lightingCapabilityDiagnostics()`.
- [x] Add planner unit tests.
- [x] Add cross-capability contract validation coverage with shadow/reflection/aa/post/gi.
- [x] Add contract lockdown runner (`scripts/lighting_contract_v2_lockdown.sh`) and CI lane (`lighting-contract-v2-lockdown`).

## Phase 2: Capability Realization (Next)
- [ ] Directional/point/spot baseline hardening and promotion gate.
- [~] Light prioritization/budget realization and promotion gate (envelope + typed diagnostics + cooldown/streak/promotion-ready warnings landed, lockdown + tier envelope tuning pending).
- [~] Light prioritization/budget realization and promotion gate (tier-profile defaults + profile-active telemetry landed; lockdown/tier tuning hardening pending).
- [x] Profile-default override precedence is covered in integration tests (backend options override tier defaults).
- [x] Physically-based units realization and promotion gate (`LIGHTING_PHYS_UNITS_PROMOTION_READY` + typed promotion diagnostics fields + default/override thresholds).
- [x] Emissive mesh lights realization and promotion gate (`LIGHTING_EMISSIVE_PROMOTION_READY` + typed promotion diagnostics fields + default/override thresholds).
- [x] Consolidated phase-2 promotion gate (`LIGHTING_PHASE2_PROMOTION_READY`) when budget + phys-units + emissive (if enabled) are jointly stable.
- [x] Add strict phase-2 lockdown runner (`scripts/lighting_phase2_lockdown.sh`) and CI lane (`lighting-phase2-lockdown`).

## Phase 3: Advanced Lighting Modes (Backlog)
- [~] Area lights (approximate/sampled) planner/tier-gating telemetry scaffold landed (runtime realization pending).
- [~] IES profiles planner/tier-gating telemetry scaffold landed (runtime realization pending).
- [~] Cookies/projectors planner/tier-gating telemetry scaffold landed (runtime realization pending).
- [~] Volumetric shafts planner/tier-gating telemetry scaffold landed (runtime realization pending).
- [~] Clustering planner/tier-gating telemetry scaffold landed (runtime realization pending).
- [~] Light layers/channels planner/tier-gating telemetry scaffold landed (runtime realization pending).
