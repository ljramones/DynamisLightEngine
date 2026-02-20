# RT Capability Checklist (Vulkan Scope)

- [x] Add backend-agnostic typed RT capability diagnostics (`rtCapabilityDiagnostics()`).
- [x] Add backend-agnostic typed RT capability promotion diagnostics (`rtCapabilityPromotionDiagnostics()`).
- [x] Add Vulkan runtime RT capability state with expected/active/pruned coverage for:
  - RT AO
  - RT translucency/caustics
  - BVH compaction
  - denoiser framework
  - hybrid RT+raster composition
  - RT quality tiers
  - inline ray query
  - dedicated ray generation path
- [x] Emit per-frame RT capability warning contract:
  - `RT_CAPABILITY_MODE_ACTIVE`
  - `RT_CAPABILITY_POLICY_ACTIVE`
  - `RT_CAPABILITY_ENVELOPE`
  - `RT_CAPABILITY_ENVELOPE_BREACH`
  - `RT_CAPABILITY_PROMOTION_READY`
- [x] Add integration test coverage (`VulkanRtCapabilityPromotionIntegrationTest`).
- [x] Add lockdown runner (`scripts/rt_capability_lockdown.sh`) and CI lane (`rt-capability-lockdown`).

Scope note:
- This checklist is Vulkan-path scoped and establishes capability/promotion contracts for remaining RT rows; full dedicated execution hardening remains the next phase.
