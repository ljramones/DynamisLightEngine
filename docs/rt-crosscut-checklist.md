# RT Cross-Cut Checklist (Vulkan Scope)

- [x] Add backend-agnostic typed diagnostics surface (`rtCrossCutDiagnostics()`).
- [x] Add Vulkan runtime RT cross-cut policy/envelope/promotion state.
- [x] Emit RT cross-cut warning contract each frame:
  - `RT_CROSSCUT_POLICY_ACTIVE`
  - `RT_CROSSCUT_ENVELOPE`
  - `RT_CROSSCUT_ENVELOPE_BREACH`
  - `RT_CROSSCUT_PROMOTION_READY`
- [x] Add integration coverage for promotion/breach paths (`VulkanRtCrossCutPromotionIntegrationTest`).
- [x] Add lockdown runner (`scripts/rt_crosscut_lockdown.sh`).
- [x] Add CI lane (`rt-crosscut-lockdown`).
- [x] Add full RT lockdown bundle runner/lane across reflections + GI RT lanes + cross-cut + capability gates (`scripts/rt_lockdown_full.sh`, `rt-lockdown-full`).

Scope note:
- This checklist is Vulkan-path scoped for current promotion and does not imply OpenGL RT parity.
