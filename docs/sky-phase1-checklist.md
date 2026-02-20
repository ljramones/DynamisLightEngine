# Sky / Atmosphere Phase 1 Checklist (Vulkan Scope)

- [x] Add backend-agnostic typed sky capability diagnostics (`skyCapabilityDiagnostics()`).
- [x] Add backend-agnostic typed sky promotion diagnostics (`skyPromotionDiagnostics()`).
- [x] Add Vulkan runtime sky capability/promotion state:
  - mode + requested feature policy ingestion
  - expected/active/pruned feature diagnostics
  - promotion envelope tracking
- [x] Emit per-frame sky warning contract:
  - `SKY_CAPABILITY_MODE_ACTIVE`
  - `SKY_POLICY_ACTIVE`
  - `SKY_PROMOTION_ENVELOPE`
  - `SKY_PROMOTION_ENVELOPE_BREACH`
  - `SKY_PROMOTION_READY`
- [x] Add integration test coverage (`VulkanSkyCapabilityPromotionIntegrationTest`).
- [x] Add lockdown runner (`scripts/sky_phase1_lockdown.sh`) and CI lane (`sky-phase1-lockdown`).

Scope note:
- Current Phase 1 is Vulkan-path scaffold for capability/promotion contracts; advanced sky execution modes remain future work.
