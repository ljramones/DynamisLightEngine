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
- [~] Physically-based units realization and promotion gate (runtime policy warning + typed diagnostics integration landed; promotion gate pending).
- [~] Emissive mesh lights realization and promotion gate (policy/envelope warning + typed diagnostics landed; promotion gate pending).

## Phase 3: Advanced Lighting Modes (Backlog)
- [ ] Area lights (approximate/sampled).
- [ ] IES profiles.
- [ ] Cookies/projectors.
- [ ] Volumetric shafts.
- [ ] Clustering.
- [ ] Light layers/channels.
