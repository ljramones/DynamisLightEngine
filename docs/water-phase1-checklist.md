# Water / Ocean Phase 1 Checklist (Vulkan Scope)

- [x] Add backend-agnostic typed water capability diagnostics (`waterCapabilityDiagnostics()`).
- [x] Add backend-agnostic typed water promotion diagnostics (`waterPromotionDiagnostics()`).
- [x] Add Vulkan runtime water capability state:
  - expected/active/pruned feature diagnostics
  - promotion envelope tracking
- [x] Emit per-frame water warning contract:
  - `WATER_CAPABILITY_MODE_ACTIVE`
  - `WATER_POLICY_ACTIVE`
  - `WATER_PROMOTION_ENVELOPE`
  - `WATER_PROMOTION_ENVELOPE_BREACH`
  - `WATER_PROMOTION_READY`
- [x] Add integration test coverage (`VulkanWaterCapabilityPromotionIntegrationTest`).
- [x] Add lockdown runner (`scripts/water_phase1_lockdown.sh`) and CI lane (`water-phase1-lockdown`).

Scope note:
- Current Phase 1 is Vulkan-path scaffold for capability/promotion contracts; production water/ocean execution paths remain future work.
