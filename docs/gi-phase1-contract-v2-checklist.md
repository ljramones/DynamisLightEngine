# GI Phase 1 Contract V2 Checklist

Scope: establish GI composition contract/planner scaffolding using the existing v2 + lockdown pattern.

## Goals

- Add Vulkan GI v2 descriptor modes for Phase 1 planning.
- Add deterministic GI planner with explicit active/pruned outputs.
- Expose typed runtime GI diagnostics without warning-string parsing.
- Add GI promotion policy + promotion-ready warning gates with tier/default + backend override thresholds.
- Expose typed GI promotion diagnostics without warning-string parsing.
- Add lockdown script for local/CI contract + integration replay.

## Contract Surface

- [x] Add `VulkanGiCapabilityDescriptorV2` with modes:
  - `ssgi`
  - `probe_grid`
  - `rtgi_single`
  - `hybrid_probe_ssgi_rt`
- [x] Add deterministic planner:
  - `VulkanGiCapabilityPlanner`
  - `VulkanGiCapabilityPlan`
- [x] Add runtime warning emission:
  - `GI_CAPABILITY_PLAN_ACTIVE`
- [x] Add runtime promotion warning emission:
  - `GI_PROMOTION_POLICY_ACTIVE`
  - `GI_SSGI_POLICY_ACTIVE`
  - `GI_SSGI_ENVELOPE`
  - `GI_SSGI_ENVELOPE_BREACH`
  - `GI_SSGI_PROMOTION_READY`
  - `GI_PROMOTION_READY`
- [x] Add backend-agnostic typed diagnostics:
  - `EngineRuntime.giCapabilityDiagnostics()`
  - `GiCapabilityDiagnostics`
- [x] Add backend-agnostic typed promotion diagnostics:
  - `EngineRuntime.giPromotionDiagnostics()`
  - `GiPromotionDiagnostics`
  - includes active path flags: `ssgiActive`, `probeGridActive`, `rtDetailActive`
  - includes SSGI envelope + streak/cooldown/promotion fields for parser-free gating

## Validation

- [x] Descriptor-level validator coverage includes GI modes with shadow/reflection/AA/post.
- [x] Planner unit tests cover disabled + fallback pruning paths.
- [x] Integration tests validate capability + promotion warnings and typed diagnostics contracts.
- [x] Add lockdown runner: `scripts/gi_phase1_contract_v2_lockdown.sh`.

## Verification Commands

```bash
./scripts/gi_phase1_contract_v2_lockdown.sh
```
