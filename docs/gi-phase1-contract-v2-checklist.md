# GI Phase 1 Contract V2 Checklist

Scope: establish GI composition contract/planner scaffolding using the existing v2 + lockdown pattern.

## Goals

- Add Vulkan GI v2 descriptor modes for Phase 1 planning.
- Add deterministic GI planner with explicit active/pruned outputs.
- Expose typed runtime GI diagnostics without warning-string parsing.
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
- [x] Add backend-agnostic typed diagnostics:
  - `EngineRuntime.giCapabilityDiagnostics()`
  - `GiCapabilityDiagnostics`

## Validation

- [x] Descriptor-level validator coverage includes GI modes with shadow/reflection/AA/post.
- [x] Planner unit tests cover disabled + fallback pruning paths.
- [x] Integration tests validate warning + typed diagnostics contract.
- [x] Add lockdown runner: `scripts/gi_phase1_contract_v2_lockdown.sh`.

## Verification Commands

```bash
./scripts/gi_phase1_contract_v2_lockdown.sh
```
