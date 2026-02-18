# DynamicLightEngine Demos Module

This module is the home for first-party demo content:
- demo scene descriptors
- material definitions
- reusable demo assets/manifests
- runnable command-line demos with telemetry output

## Layout

- `src/main/resources/demos/materials`:
  material definitions and material manifests.
- `src/main/resources/demos/scenes`:
  scene descriptors and scene manifests.
- `src/main/java/org/dynamislight/demos`:
  demo runner, registry, and demo implementations.

## Conventions

- Keep paths relative to `src/main/resources/demos`.
- Use stable IDs in manifests to avoid breaking references between scenes and materials.
- Add new demos under this module instead of `engine-host-sample` so sample host code stays runtime-focused.

## CLI Usage

List demos:
```bash
mvn -f engine-demos/pom.xml exec:java -Dexec.args="--list"
```

Run a demo:
```bash
mvn -f engine-demos/pom.xml exec:java \
  -Dexec.args="--demo=hello-triangle --backend=vulkan --seconds=10 --quality=high"
```

Run a demo on macOS with Vulkan real/mock wiring (recommended):
```bash
./scripts/run_demo_mac.sh --demo=hello-triangle --backend=vulkan --mock=false --seconds=10 --quality=high
```
If you update backend modules, refresh local snapshots first with `mvn -DskipTests install`.

Run AA demo with explicit mode and telemetry file:
```bash
mvn -f engine-demos/pom.xml exec:java \
  -Dexec.args="--demo=aa-matrix --backend=vulkan --aa-mode=tsr --aa-render-scale=0.64 --seconds=12 --telemetry=artifacts/demos/aa-tsr.jsonl"
```

Run material baseline demo:
```bash
./scripts/run_demo_mac.sh --demo=material-baseline --backend=vulkan --mock=false --seconds=10 --quality=high
```

Run local lights array demo:
```bash
./scripts/run_demo_mac.sh --demo=lights-local-array --backend=vulkan --mock=false --seconds=10 --quality=high
```

Run shadow cascade debug demo:
```bash
./scripts/run_demo_mac.sh --demo=shadow-cascade-debug --backend=vulkan --mock=false --seconds=10 --quality=high
```

Run shadow cascade baseline demo:
```bash
./scripts/run_demo_mac.sh --demo=shadow-cascade-baseline --backend=vulkan --mock=false --seconds=10 --quality=high
```

Run shadow local atlas pressure demo:
```bash
./scripts/run_demo_mac.sh --demo=shadow-local-atlas --backend=vulkan --mock=false --seconds=10 --quality=high
```

Run shadow quality matrix demo:
```bash
./scripts/run_demo_mac.sh --demo=shadow-quality-matrix --backend=vulkan --mock=false --seconds=10 --quality=high
```

Run AA motion stress demo:
```bash
./scripts/run_demo_mac.sh --demo=aa-motion-stress --backend=vulkan --mock=false --seconds=10 --quality=high --aa-mode=taa
```

Run SSR + Hi-Z reflections demo:
```bash
./scripts/run_demo_mac.sh --demo=reflections-ssr-hiz --backend=vulkan --mock=false --seconds=10 --quality=high
```

Run planar reflections demo:
```bash
./scripts/run_demo_mac.sh --demo=reflections-planar --backend=vulkan --mock=false --seconds=10 --quality=high
```

Run hybrid reflections demo:
```bash
./scripts/run_demo_mac.sh --demo=reflections-hybrid --backend=vulkan --mock=false --seconds=10 --quality=high
```

Telemetry outputs:
- frame-by-frame JSONL (default under `artifacts/demos/`)
- summary JSON (`*.summary.json`) with averages/p95/event/log counts

## Demo Planning

- Roadmap and target demo catalog:
  `docs/guides/demos-roadmap.md`
