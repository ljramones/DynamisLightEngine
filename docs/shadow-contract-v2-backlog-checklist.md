# Shadow Contract V2 Backlog Checklist

Date: 2026-02-19  
Scope: represent remaining shadow wishlist items as explicit `RenderFeatureMode` entries in `VulkanShadowCapabilityDescriptorV2` and validate composition safety with reflections.

## Target

Add shadow backlog capability modes to the v2 contract so each remaining shadow area can be tracked/validated as a mode-scoped contract surface before deeper implementation hardening.

## Mode Coverage

- [x] `pcf`
- [x] `pcss`
- [x] `vsm`
- [x] `evsm`
- [x] `rt`
- [x] `local_atlas_cadence`
- [x] `point_cubemap_budget`
- [x] `spot_projected`
- [x] `area_approx`
- [x] `rt_denoised`
- [x] `hybrid_cascade_contact_rt`
- [x] `transparent_receivers`
- [x] `cached_static_dynamic`
- [x] `distance_field_soft`

## Contract Coverage Checks

- [x] Pass declarations are present for every mode.
- [x] Shader contribution is present for every mode.
- [x] Descriptor requirements are present for every mode.
- [x] Resource declarations are present for every mode.
- [x] Scheduler declarations cover cadence/face/cache policy modes.
- [x] Telemetry declarations include mode-specific warning/diagnostic/gate surface.
- [x] Deterministic mode-resolution planner maps runtime policy signals to one active contract mode (`VulkanShadowCapabilityPlanner`).

## Cross-Capability Validation Checks

- [x] Shadow + reflection descriptors produce zero `ERROR` issues through `RenderCapabilityContractV2Validator` for baseline (`evsm` + `hybrid`).
- [x] Shadow + reflection descriptors produce zero `ERROR` issues for every shadow supported mode against reflection `hybrid`.

## Notes

- This checklist validates contract expressiveness and composition safety only; it does not mark each shadow wishlist item as runtime-production complete.
- Runtime hardening/promotion remains tracked in `docs/shadow-production-execution-plan-2026.md` and `wish_list.md`.
- Runtime-production promotion proof points now exist and are linked:
  - Phase A sustained promotion gate: `SHADOW_PHASEA_PROMOTION_READY`, runner `scripts/shadow_phasea_promotion_lockdown.sh`, CI lane `shadow-phasea-lockdown`.
  - Phase C sustained stability gates: `SHADOW_CACHE_CHURN_HIGH`, `SHADOW_RT_DENOISE_ENVELOPE_BREACH`, `SHADOW_HYBRID_COMPOSITION_BREACH`, runner `scripts/shadow_phasec_lockdown.sh`, CI lane `shadow-phasec-lockdown`.
  - Transparent receiver policy/stability gates: `SHADOW_TRANSPARENT_RECEIVER_POLICY`, `SHADOW_TRANSPARENT_RECEIVER_ENVELOPE_BREACH`, runner `scripts/shadow_transparent_receivers_lockdown.sh`, CI lane `shadow-transparent-lockdown`.
  - Phase D consolidated promotion gate: `SHADOW_PHASED_PROMOTION_READY`, runner `scripts/shadow_phased_lockdown.sh`, CI lane `shadow-phased-lockdown`.
