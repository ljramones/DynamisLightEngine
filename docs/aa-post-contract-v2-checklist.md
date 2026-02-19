# AA/Post Contract V2 Checklist

Date: 2026-02-19  
Scope: harden Vulkan AA + post capability declarations against the extracted v2 contract and cross-capability validator.

## Objectives

- [x] Add Vulkan AA v2 capability descriptor with explicit supported modes and mode sanitization.
- [x] Add Vulkan post v2 capability descriptor with explicit supported modules and mode sanitization.
- [x] Ensure AA temporal modes declare cross-frame temporal resources (`history_color`, `history_velocity`) and non-temporal modes do not require them.
- [x] Ensure descriptor bindings avoid collisions with mature shadow/reflection contracts under validator rules.
- [x] Ensure shader contributions use explicit ordering for shared injection points.
- [x] Validate AA + post + shadow + reflection compositions with zero `ERROR` issues in `RenderCapabilityContractV2Validator`.
- [x] Add lockdown runner for CI/local replay: `scripts/aa_post_contract_v2_lockdown.sh`.
- [x] Add always-on CI lane for the lockdown bundle.

## Verification

- `./scripts/aa_post_contract_v2_lockdown.sh`
