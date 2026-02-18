# Demo Roadmap

This document defines the planned demo set for `engine-demos` with emphasis on graphical capability coverage.

## Current

- `hello-triangle` (graphical baseline)
- `aa-matrix` (graphical AA configuration/telemetry)

Current count: `2` demos (`2` graphical)

## Target Demo Set

Target count: `16` demos (`12` graphical, `4` programmatic/CLI-focused)

### Graphical Demos (Priority)

1. `hello-triangle`
2. `material-baseline` (PBR roughness/metalness sweep)
3. `lights-local-array` (multi point/spot budgets and attenuation)
4. `shadow-cascade-debug` (cascade splits, bias tuning lanes)
5. `shadow-local-atlas` (local shadow budget and atlas pressure)
6. `shadow-quality-matrix` (PCF/quality-tier comparison)
7. `aa-matrix` (TAA/TUAA/TSR/SMAA/FXAA profiles)
8. `aa-motion-stress` (fast pan, thin geo, alpha foliage)
9. `reflections-ssr-hiz` (SSR + Hi-Z behavior)
10. `reflections-planar` (planar reflection path)
11. `reflections-hybrid` (hybrid fallback chain)
12. `fog-smoke-post` (fog/smoke/post-process interaction)

### Programmatic/CLI Demos

1. `telemetry-export` (headless-ish telemetry stress lane)
2. `threshold-replay` (load stored thresholds and emit verdicts)
3. `backend-compare-smoke` (OpenGL vs Vulkan quick pass)
4. `capability-probe` (runtime capability and feature flags report)

## Implementation Order

1. `material-baseline`
2. `lights-local-array`
3. `shadow-cascade-debug`
4. `shadow-local-atlas`
5. `aa-motion-stress`
6. `reflections-ssr-hiz`
7. Remaining demos in category order

This order front-loads the highest visual impact and highest regression value content.

## Definition of Done Per Demo

- Registered in `DemoRegistry`
- Runnable from CLI (`--demo=<id>`)
- Emits JSONL + summary telemetry
- Has a short section in `engine-demos/README.md`
- Has one smoke command recorded in this roadmap

## Standard Smoke Command

```bash
./scripts/run_demo_mac.sh --demo=<id> --backend=vulkan --mock=false --seconds=10 --quality=high
```

