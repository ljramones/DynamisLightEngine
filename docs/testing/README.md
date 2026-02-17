# Testing Docs

This folder tracks feature-specific test design documents.

Guidelines:
- Create one document per major feature area.
- Keep each doc focused on test strategy, coverage, execution commands, pass/fail criteria, and known gaps.
- Update docs as implementation changes, thresholds change, or new regressions are found.

Current documents:
- `aa-testing.md` - anti-aliasing test design and execution workflow.
- `scene-assembly-testing.md` - scene graph validity and baseline load/parity checks.
- `camera-testing.md` - camera motion/disocclusion and animated-motion validation.
- `materials-lighting-testing.md` - material/reactive/lighting stress and parity plan.
- `shadows-testing.md` - shadow policy, stability, and tier-fallback validation plan.
- `fog-smoke-testing.md` - fog/smoke/shadow combined stress validation plan.
- `post-process-testing.md` - post chain regression and stress validation plan.
- `environment-ibl-testing.md` - environment/IBL parity and interaction validation plan.
- `reflections-testing.md` - reflection mode validation and parity test plan.
- `resources-hot-reload-testing.md` - resource lifecycle/hot-reload validation plan.
- `runtime-events-errors-testing.md` - runtime lifecycle/event/error validation plan.
- `input-frame-loop-testing.md` - host input/update/render loop validation plan.
- `config-capabilities-testing.md` - config validation and capability reporting plan.
- `backend-selection-preflight-testing.md` - backend resolution and Vulkan preflight validation plan.
- `quality-tiers-testing.md` - tiered envelope and stress validation plan.
- `compare-harness-workflows-testing.md` - compare workflow artifact/threshold validation plan.
- `feature-testing-template.md` - reusable template for future feature test plans.

Automation shortcuts:
- `./scripts/shadow_ci_matrix.sh` - shadow policy/stress/depth-format matrix checks.
