# Geometry / Detail Phase 1 Checklist (Vulkan Scope)

- [x] Add backend-agnostic typed geometry capability diagnostics (`geometryCapabilityDiagnostics()`).
- [x] Add backend-agnostic typed geometry promotion diagnostics (`geometryPromotionDiagnostics()`).
- [x] Add Vulkan runtime geometry/detail capability state:
  - expected/active/pruned feature diagnostics
  - mesh geometry cache telemetry exposure in typed diagnostics
  - promotion envelope tracking
- [x] Emit per-frame geometry warning contract:
  - `GEOMETRY_CAPABILITY_MODE_ACTIVE`
  - `GEOMETRY_POLICY_ACTIVE`
  - `GEOMETRY_PROMOTION_ENVELOPE`
  - `GEOMETRY_PROMOTION_ENVELOPE_BREACH`
  - `GEOMETRY_PROMOTION_READY`
- [x] Add integration test coverage (`VulkanGeometryCapabilityPromotionIntegrationTest`).
- [x] Add lockdown runner (`scripts/geometry_phase1_lockdown.sh`) and CI lane (`geometry-phase1-lockdown`).

Scope note:
- Current Phase 1 is Vulkan-path scaffold for capability/promotion contracts; advanced geometry execution modes remain future work.
