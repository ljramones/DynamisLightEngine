# Shadow Deep-Dive Implementation Checklist

Date: 2026-02-19  
Scope: execute remaining shadow capabilities from `Partial`/`Not In Yet` to production-ready `In` (Vulkan path first), with strict CI/telemetry gates.

## Sequencing

1. Per-light atlas + cadence scheduling hardening (`local_atlas_cadence`)
2. Point cubemap + face-budget control hardening (`point_cubemap_budget`)
3. Spot projected shadows hardening (`spot_projected`)
4. Shadow caching static/dynamic overlay hardening (`cached_static_dynamic`)
5. RT shadows denoised path hardening (`rt_denoised`)
6. Hybrid cascade + contact + RT composition hardening (`hybrid_cascade_contact_rt`)
7. Transparent shadow receivers implementation/hardening (`transparent_receivers`)
8. Area light shadow approximation path (`area_approx`)
9. Distance-field soft shadows path (`distance_field_soft`)

## Phase Gates

- Phase A (1-3): atlas topology parity + scheduling/budget stability + no-regression on current shadow matrix.
- Phase B (4): cache invalidation correctness + churn/perf envelope gates.
- Phase C (5-6): RT availability/perf/denoise/hybrid envelope gates.
- Phase D (7-9): material correctness + scene coverage + stress/perf lock.

## Work Items

### 1) Per-light atlas + cadence scheduling

- [x] Planner/runtime mode diagnostics use rendered topology signals (selected/deferred/spot/point), not only static config hints.
- [x] Add typed atlas/cadence diagnostics accessor (selected/deferred/stale-bypass + envelope state) via `EngineRuntime.shadowCadenceDiagnostics()`.
- [x] Add cadence envelope warning + cooldown gate (deferred ratio / streak/cooldown) with runtime options:
  - `vulkan.shadow.cadenceWarnDeferredRatioMax`
  - `vulkan.shadow.cadenceWarnMinFrames`
  - `vulkan.shadow.cadenceWarnCooldownFrames`
- [x] Add cadence promotion-ready gate (`SHADOW_CADENCE_PROMOTION_READY`) with typed stability fields and profile-default window (`vulkan.shadow.cadencePromotionReadyMinFrames`).
- [x] Add CI assertions for cadence envelope stability on blessed tiers (`VulkanShadowCapabilityWarningIntegrationTest#cadenceEnvelopeStaysStableAcrossBlessedTiers`).

### 2) Point cubemap + face-budget

- [x] Add dedicated point-face budget diagnostics + envelope gates (`SHADOW_POINT_FACE_BUDGET_ENVELOPE`, `SHADOW_POINT_FACE_BUDGET_ENVELOPE_BREACH`) + typed runtime accessor `shadowPointBudgetDiagnostics()`.
- [x] Add scene coverage for multi-point budget saturation/breach behavior (`VulkanShadowCapabilityWarningIntegrationTest#pointFaceBudgetBreachGateTriggersOnSaturatedDeferredPointWork`).
- [x] Lock point face-budget thresholds per tier profile in runtime defaults with explicit override precedence and warning telemetry (`SHADOW_TELEMETRY_PROFILE_ACTIVE`).
- [x] Add point face-budget promotion-ready gate (`SHADOW_POINT_FACE_BUDGET_PROMOTION_READY`) with typed stability fields and profile-default window (`vulkan.shadow.pointFaceBudgetPromotionReadyMinFrames`).

### 3) Spot projected

- [x] Add explicit projected-spot contract diagnostics (active/inactive/reason) via warning codes:
  - `SHADOW_SPOT_PROJECTED_CONTRACT`
  - `SHADOW_SPOT_PROJECTED_CONTRACT_BREACH`
  and typed runtime accessor `shadowSpotProjectedDiagnostics()`.
- [x] Add projected-spot scene coverage for contract activation/breach state (`VulkanShadowCapabilityWarningIntegrationTest#emitsCadenceEnvelopeWarningAndTypedDiagnostics`).
- [x] Add projected-spot promotion-ready gate (`SHADOW_SPOT_PROJECTED_PROMOTION_READY`) with typed stability fields and profile-default window (`vulkan.shadow.spotProjectedPromotionReadyMinFrames`).
- [x] Add strict topology contract diagnostics/gates across local/spot/point coverage (`shadowTopologyDiagnostics()`, `SHADOW_TOPOLOGY_CONTRACT`, `SHADOW_TOPOLOGY_CONTRACT_BREACH`).
- [x] Add topology promotion-ready gate diagnostics (`stableStreak`, `topologyPromotionReadyMinFrames`) with explicit readiness warning (`SHADOW_TOPOLOGY_PROMOTION_READY`) and typed runtime fields.
- [x] Add consolidated Phase A promotion-ready gate (`SHADOW_PHASEA_PROMOTION_READY`) requiring cadence/point/spot promotion readiness for a configured stability window (`vulkan.shadow.phaseAPromotionReadyMinFrames`) with typed diagnostics (`shadowPhaseAPromotionDiagnostics()`).
- [x] Add strict Phase A lockdown runner (`scripts/shadow_phasea_promotion_lockdown.sh`) and always-on CI lane (`shadow-phasea-lockdown`) for sustained-window tier assertions.

### 4) Shadow caching

- [x] Implement/enable explicit static cache + dynamic overlay contract signaling in Vulkan runtime (`SHADOW_CACHE_POLICY_ACTIVE`).
- [x] Add cache churn/miss diagnostics and invalidation reason telemetry via typed runtime accessor `shadowCacheDiagnostics()`.
- [x] Add cache stability CI gate (`SHADOW_CACHE_CHURN_HIGH`) with streak/cooldown thresholds and integration coverage (`VulkanShadowCapabilityWarningIntegrationTest#shadowCacheBreachGateTriggersWithAggressiveThresholds`).

### 5) RT shadows denoised

- [x] Activate strict denoise/perf envelope gates with typed runtime diagnostics parity (`shadowRtDiagnostics()`, `SHADOW_RT_DENOISE_ENVELOPE`, `SHADOW_RT_DENOISE_ENVELOPE_BREACH`).
- [x] Lock RT perf + denoise thresholds by tier profile with explicit override precedence (`SHADOW_TELEMETRY_PROFILE_ACTIVE`).

### 6) Hybrid cascade + contact + RT

- [x] Add composition-share diagnostics and breach gates (`shadowHybridDiagnostics()`, `SHADOW_HYBRID_COMPOSITION`, `SHADOW_HYBRID_COMPOSITION_BREACH`).
- [x] Add hybrid stability CI envelope assertions (`VulkanShadowCapabilityWarningIntegrationTest#shadowHybridCompositionBreachGateTriggersWithAggressiveThresholds`).

### 7) Transparent shadow receivers

- [x] Implement transparent-receiver production policy path with explicit fallback behavior (`fallback_opaque_only` when requested but unsupported in current Vulkan path).
- [x] Add transparent receiver envelope warnings + typed diagnostics (`shadowTransparentReceiverDiagnostics()`, `SHADOW_TRANSPARENT_RECEIVER_POLICY`, `SHADOW_TRANSPARENT_RECEIVER_ENVELOPE_BREACH`).
- [x] Add strict transparent receiver lockdown runner (`scripts/shadow_transparent_receivers_lockdown.sh`) with cooldown and blessed-tier stability assertions.

### 8) Area light shadows

- [x] Implement Vulkan area-light shadow capability policy stage with explicit fallback contract and strict required-path breach signaling.
- [x] Add area-light contract diagnostics (`SHADOW_AREA_APPROX_POLICY`, `SHADOW_AREA_APPROX_REQUIRED_UNAVAILABLE_BREACH`) via typed extended-mode diagnostics.

### 9) Distance-field soft shadows

- [x] Implement Vulkan distance-field shadow capability policy stage with explicit fallback contract and strict required-path breach signaling.
- [x] Add DF contract diagnostics (`SHADOW_DISTANCE_FIELD_SOFT_POLICY`, `SHADOW_DISTANCE_FIELD_REQUIRED_UNAVAILABLE_BREACH`) via typed extended-mode diagnostics.

## Notes

- Contract-mode coverage is already tracked in `docs/shadow-contract-v2-backlog-checklist.md`.
- This checklist is execution-focused (runtime implementation + CI gates), not only contract metadata.
