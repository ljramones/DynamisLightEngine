# Demo Roadmap

This document defines the planned demo set for `engine-demos` with emphasis on graphical capability coverage.

## Current

- `hello-triangle` (graphical baseline)
- `material-baseline` (graphical PBR sweep)
- `lights-local-array` (graphical local-light validation)
- `shadow-cascade-baseline` (graphical clean shadow lane)
- `shadow-cascade-debug` (graphical shadow stress lane)
- `shadow-local-atlas` (graphical local shadow atlas pressure)
- `shadow-quality-matrix` (graphical shadow quality comparison lane)
- `aa-motion-stress` (graphical AA stress lane)
- `reflections-ssr-hiz` (graphical SSR + Hi-Z lane)
- `reflections-planar` (graphical planar reflection lane)
- `reflections-hybrid` (graphical hybrid reflection lane)
- `fog-smoke-post` (graphical fog/smoke/post interaction lane)
- `telemetry-export` (programmatic telemetry capture lane)
- `threshold-replay` (programmatic threshold replay lane)
- `aa-matrix` (graphical AA configuration/telemetry)

Current count: `15` demos (`13` graphical, `2` programmatic)

## Target Demo Set

Target count: `17` demos (`13` graphical, `4` programmatic/CLI-focused)

### Graphical Demos (Priority)

1. `hello-triangle`
2. `material-baseline` (PBR roughness/metalness sweep)
3. `lights-local-array` (multi point/spot budgets and attenuation)
4. `shadow-cascade-baseline` (clean cascade baseline lane)
5. `shadow-cascade-debug` (cascade splits, bias tuning lanes)
6. `shadow-local-atlas` (local shadow budget and atlas pressure)
7. `shadow-quality-matrix` (PCF/quality-tier comparison)
8. `aa-matrix` (TAA/TUAA/TSR/SMAA/FXAA profiles)
9. `aa-motion-stress` (fast pan, thin geo, alpha foliage)
10. `reflections-ssr-hiz` (SSR + Hi-Z behavior)
11. `reflections-planar` (planar reflection path)
12. `reflections-hybrid` (hybrid fallback chain)
13. `fog-smoke-post` (fog/smoke/post-process interaction)

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
