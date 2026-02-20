# VFX / Particles Phase 1 Checklist (Vulkan Scope)

- [x] Add backend-agnostic typed VFX capability diagnostics (`vfxCapabilityDiagnostics()`).
- [x] Add backend-agnostic typed VFX promotion diagnostics (`vfxPromotionDiagnostics()`).
- [x] Add Vulkan runtime VFX capability state:
  - expected/active/pruned feature diagnostics
  - promotion envelope tracking
- [x] Emit per-frame VFX warning contract:
  - `VFX_CAPABILITY_MODE_ACTIVE`
  - `VFX_POLICY_ACTIVE`
  - `VFX_PROMOTION_ENVELOPE`
  - `VFX_PROMOTION_ENVELOPE_BREACH`
  - `VFX_PROMOTION_READY`
- [x] Add integration test coverage (`VulkanVfxCapabilityPromotionIntegrationTest`).
- [x] Add lockdown runner (`scripts/vfx_phase1_lockdown.sh`) and CI lane (`vfx-phase1-lockdown`).

Scope note:
- Current Phase 1 is Vulkan-path scaffold for capability/promotion contracts; production VFX execution paths remain future work.
