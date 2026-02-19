# AA/Post Contract V2 Checklist

Date: 2026-02-19  
Scope: harden Vulkan AA + post capability declarations against the extracted v2 contract and cross-capability validator.

## Objectives

- [x] Add Vulkan AA v2 capability descriptor with explicit supported modes and mode sanitization.
- [x] Add Vulkan post v2 capability descriptor with explicit supported modules and mode sanitization.
- [x] Ensure AA temporal modes declare cross-frame temporal resources (`history_color`, `history_velocity`) and non-temporal modes do not require them.
- [x] Ensure descriptor bindings avoid collisions with mature shadow/reflection contracts under validator rules.
- [x] Ensure shader contributions use explicit ordering for shared injection points.
- [x] Emit runtime AA/post capability-plan warning (`AA_POST_CAPABILITY_PLAN_ACTIVE`) and typed backend-agnostic diagnostics (`aaPostCapabilityDiagnostics()`) for parser-free host/CI assertions.
- [x] Validate AA + post + shadow + reflection compositions with zero `ERROR` issues in `RenderCapabilityContractV2Validator`.
- [x] Add lockdown runner for CI/local replay: `scripts/aa_post_contract_v2_lockdown.sh`.
- [x] Add always-on CI lane for the lockdown bundle.
- [x] Add AA temporal hardening envelope + promotion warnings (`AA_TEMPORAL_ENVELOPE`, `AA_TEMPORAL_ENVELOPE_BREACH`, `AA_TEMPORAL_PROMOTION_READY`) with typed diagnostics (`aaTemporalPromotionDiagnostics()`).
- [x] Add material-policy temporal hardening gates (`AA_REACTIVE_MASK_POLICY`, `AA_REACTIVE_MASK_ENVELOPE_BREACH`, `AA_HISTORY_CLAMP_POLICY`, `AA_HISTORY_CLAMP_ENVELOPE_BREACH`) and consolidated readiness signal (`AA_TEMPORAL_CORE_PROMOTION_READY`).
- [x] Add TUUA/TSR upscale envelope + promotion warnings (`AA_UPSCALE_POLICY_ACTIVE`, `AA_UPSCALE_ENVELOPE`, `AA_UPSCALE_ENVELOPE_BREACH`, `AA_UPSCALE_PROMOTION_READY`) with typed diagnostics (`aaUpscalePromotionDiagnostics()`).
- [x] Add MSAA-selective/hybrid envelope + promotion warnings (`AA_MSAA_POLICY_ACTIVE`, `AA_MSAA_ENVELOPE`, `AA_MSAA_ENVELOPE_BREACH`, `AA_MSAA_PROMOTION_READY`) with typed diagnostics (`aaMsaaPromotionDiagnostics()`).
- [x] Add DLAA/specular-AA quality envelope + promotion warnings (`AA_DLAA_*`, `AA_SPECULAR_*`) with typed diagnostics (`aaQualityPromotionDiagnostics()`).
- [x] Add geometric AA + alpha-to-coverage envelope + promotion warnings (`AA_GEOMETRIC_*`, `AA_A2C_*`) under quality diagnostics (`aaQualityPromotionDiagnostics()`).

## Verification

- `./scripts/aa_post_contract_v2_lockdown.sh`
