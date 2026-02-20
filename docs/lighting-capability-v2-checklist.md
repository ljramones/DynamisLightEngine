# Lighting Capability V2 Checklist

Status: Phase 2 Complete; Phase 3 In Progress (Vulkan-first)

## Phase 1: Contract + Planner + Telemetry
- [x] Add `vulkan.lighting` v2 descriptor with baseline and expansion modes.
- [x] Add deterministic lighting planner with parser-friendly signals.
- [x] Emit per-frame `LIGHTING_CAPABILITY_MODE_ACTIVE` warning payload.
- [x] Expose backend-agnostic typed runtime diagnostics: `lightingCapabilityDiagnostics()`.
- [x] Add planner unit tests.
- [x] Add cross-capability contract validation coverage with shadow/reflection/aa/post/gi.
- [x] Add contract lockdown runner (`scripts/lighting_contract_v2_lockdown.sh`) and CI lane (`lighting-contract-v2-lockdown`).

## Phase 2: Capability Realization (Next)
- [x] Directional/point/spot baseline hardening and promotion gate (`LIGHTING_BASELINE_PROMOTION_READY` + typed promotion diagnostics fields + default/override thresholds).
- [x] Light prioritization/budget realization and promotion gate (envelope + typed diagnostics + cooldown/streak/promotion-ready warnings + tier-profile defaults + lockdown lanes).
- [x] Profile-default override precedence is covered in integration tests (backend options override tier defaults).
- [x] Physically-based units realization and promotion gate (`LIGHTING_PHYS_UNITS_PROMOTION_READY` + typed promotion diagnostics fields + default/override thresholds).
- [x] Emissive mesh lights realization and promotion gate (`LIGHTING_EMISSIVE_PROMOTION_READY` + typed promotion diagnostics fields + default/override thresholds).
- [x] Consolidated phase-2 promotion gate (`LIGHTING_PHASE2_PROMOTION_READY`) when budget + phys-units + emissive (if enabled) are jointly stable.
- [x] Add strict phase-2 lockdown runner (`scripts/lighting_phase2_lockdown.sh`) and CI lane (`lighting-phase2-lockdown`).
- [x] Add strict advanced-stack lockdown runner (`scripts/lighting_advanced_lockdown.sh`) and CI lane (`lighting-advanced-lockdown`).
- [x] Add full lighting lockdown bundle runner (`scripts/lighting_lockdown_full.sh`) and CI lane (`lighting-lockdown-full`).
- [x] Add advanced-stack promotion gate (`LIGHTING_ADVANCED_PROMOTION_READY`) with tier-profile defaults, override precedence, and integration coverage.
- [x] Add typed advanced-policy diagnostics fields (`areaApproxEnabled`, `iesProfilesEnabled`, `cookiesEnabled`, `volumetricShaftsEnabled`, `clusteringEnabled`, `lightLayersEnabled`) to backend-agnostic lighting capability diagnostics.
- [x] Wire planner-resolved lighting mode into Phase-C profile resolution (`VulkanPipelineProfileResolver`) via runtime override so compiled profile keys follow active lighting capability mode.
- [x] Expose typed advanced diagnostics accessor (`lightingAdvancedDiagnostics()`) for parser-free CI assertions on advanced expected/active capability coverage.

## Phase 3: Advanced Lighting Modes (Backlog)
- [~] Area lights (approximate/sampled) planner/tier-gating telemetry + contract resources landed (full production shader realization pending).
- [~] IES profiles planner/tier-gating telemetry + contract resources landed (full production shader realization pending).
- [~] Cookies/projectors planner/tier-gating telemetry + contract resources landed (full production shader realization pending).
- [~] Volumetric shafts planner/tier-gating telemetry + contract resources landed (full production shader realization pending).
- [~] Clustering planner/tier-gating telemetry + contract resources landed (full production shader realization pending).
- [~] Light layers/channels planner/tier-gating telemetry + contract resources landed (full production shader realization pending).
- [x] Advanced-stack contract realization now declares concrete descriptor/uniform/resource requirements for area/IES/cookies/volumetric/clustering/layers modes.
- [x] Add strict advanced required-path policy + breach gate (`LIGHTING_ADVANCED_REQUIRED_PATH_POLICY`, `LIGHTING_ADVANCED_REQUIRED_UNAVAILABLE_BREACH`) with cooldown/streak controls and typed diagnostics fields.
