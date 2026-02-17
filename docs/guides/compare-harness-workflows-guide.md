# Compare Harness Workflows Guide

Last updated: February 17, 2026

## 1. What Compare Workflows Support
- Cross-backend image parity checks
- Real vs mock Vulkan profile separation
- Temporal metric gating for AA scenes
- Reflection scene parity gates (`reflections-ssr`, `reflections-planar`, `reflections-hybrid`)
- Threshold lock workflow from sampled runs
- Vendor upscaler matrix workflow hooks

## 2. Quick Start

### Standard compare run (script)
```bash
./scripts/aa_rebaseline_real_mac.sh
```

### Real Vulkan compare run
```bash
DLE_COMPARE_VULKAN_MODE=real ./scripts/aa_rebaseline_real_mac.sh
```

### Single test class/method run
```bash
DLE_COMPARE_TEST_CLASS=org.dynamislight.sample.BackendParityIntegrationTest#compareHarnessProducesImagesWithBoundedDiff \
DLE_COMPARE_VULKAN_MODE=real \
./scripts/aa_rebaseline_real_mac.sh
```

## 3. Script Commands
- `run` (default)
- `preflight`
- `lock-thresholds <dir>`
- `longrun`
- `longrun-motion`
- `upscaler-matrix`
- `shadow-matrix`

Examples:
```bash
./scripts/aa_rebaseline_real_mac.sh preflight
./scripts/aa_rebaseline_real_mac.sh lock-thresholds artifacts/compare
./scripts/aa_rebaseline_real_mac.sh longrun-motion
./scripts/aa_rebaseline_real_mac.sh upscaler-matrix
./scripts/aa_rebaseline_real_mac.sh shadow-matrix
```

## 4. Key Environment Variables
- `DLE_COMPARE_VULKAN_MODE=mock|auto|real`
- `DLE_COMPARE_OUTPUT_DIR=<path>`
- `DLE_COMPARE_TEST_CLASS=<class-or-class#method>`
- `DLE_COMPARE_THRESHOLDS_FILE=<properties-file>`
- `DLE_COMPARE_EXTRA_MVN_ARGS="<maven -D... overrides>"` (for targeted backend-option sweeps)
- `DLE_COMPARE_TEMPORAL_FRAMES=<n>`
- `DLE_COMPARE_TEMPORAL_WINDOW=<n>`
- `DLE_COMPARE_TSR_FRAME_BOOST=<n>`
- `DLE_COMPARE_UPSCALER_MODE=none|fsr|xess|dlss`
- `DLE_COMPARE_UPSCALER_QUALITY=performance|balanced|quality|ultra_quality`
- `DLE_COMPARE_JVM_STACK_SIZE=<size>`

Optional JVM property for reflection profile during compare:
- `-Ddle.compare.reflections.profile=performance|balanced|quality|stability`

## 5. Artifact Expectations
- Output is mode-separated (`vulkan_real` / `vulkan_mock`).
- Compare metadata and threshold source are recorded.
- Threshold lock outputs:
  - `threshold-lock-report.tsv`
  - `recommended-thresholds.properties`

## 6. Troubleshooting
If script fails in real mode:
- Run `preflight` first.
- Check loader/ICD/MoltenVK diagnostics.
- Fall back to `mock` for CI safety when hardware is unavailable.

If thresholds are not found:
- Ensure lock-thresholds source dir contains real Vulkan compare metadata.

## 7. Validation Commands

Fast parity test via Maven directly:
```bash
mvn -pl engine-host-sample -am test \
  -Ddle.compare.tests=true \
  -Dtest=BackendParityIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Real + threshold override run:
```bash
DLE_COMPARE_THRESHOLDS_FILE=artifacts/compare/threshold-lock/recommended-thresholds.properties \
DLE_COMPARE_VULKAN_MODE=real \
./scripts/aa_rebaseline_real_mac.sh
```

## 8. Known Gaps / Next Steps
- Add per-workflow expected artifact manifest checks in CI.
