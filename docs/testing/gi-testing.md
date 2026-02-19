# GI Testing

## Scope

GI Phase 1 currently validates contract/planner/diagnostic behavior (not full GI rendering output).

## Coverage

- Capability descriptor v2 mode completeness:
  - `ssgi`
  - `probe_grid`
  - `rtgi_single`
  - `hybrid_probe_ssgi_rt`
- Cross-capability validator compatibility with shadow/reflection/AA/post.
- Planner fallback behavior when GI is disabled or RT path is unavailable.
- Runtime warning + typed diagnostics:
  - `GI_CAPABILITY_PLAN_ACTIVE`
  - `EngineRuntime.giCapabilityDiagnostics()`

## Commands

```bash
./scripts/gi_phase1_contract_v2_lockdown.sh
```

## Pass Criteria

- Lockdown command completes successfully.
- No `ERROR` issues from `RenderCapabilityContractV2Validator` for GI + mature domains.
- Integration tests confirm warning emission and typed diagnostics payload consistency.
