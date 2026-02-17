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
- `fog-smoke-testing.md` - fog/smoke/shadow combined stress validation plan.
- `post-process-testing.md` - post chain regression and stress validation plan.
- `environment-ibl-testing.md` - environment/IBL parity and interaction validation plan.
- `resources-hot-reload-testing.md` - resource lifecycle/hot-reload validation plan.
- `runtime-events-errors-testing.md` - runtime lifecycle/event/error validation plan.
- `input-frame-loop-testing.md` - host input/update/render loop validation plan.
- `feature-testing-template.md` - reusable template for future feature test plans.
