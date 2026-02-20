# Terrain Phase 1 Checklist (Vulkan Scope)

- [x] Add backend-agnostic typed terrain capability diagnostics (`terrainCapabilityDiagnostics()`).
- [x] Add backend-agnostic typed terrain promotion diagnostics (`terrainPromotionDiagnostics()`).
- [x] Add Vulkan runtime terrain capability state:
  - expected/active/pruned feature diagnostics
  - promotion envelope tracking
- [x] Emit per-frame terrain warning contract:
  - `TERRAIN_CAPABILITY_MODE_ACTIVE`
  - `TERRAIN_POLICY_ACTIVE`
  - `TERRAIN_PROMOTION_ENVELOPE`
  - `TERRAIN_PROMOTION_ENVELOPE_BREACH`
  - `TERRAIN_PROMOTION_READY`
- [x] Add integration test coverage (`VulkanTerrainCapabilityPromotionIntegrationTest`).
- [x] Add lockdown runner (`scripts/terrain_phase1_lockdown.sh`) and CI lane (`terrain-phase1-lockdown`).

Scope note:
- Current Phase 1 is Vulkan-path scaffold for capability/promotion contracts; production terrain execution paths remain future work.
