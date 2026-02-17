# Anti-Aliasing Testing Design

Last updated: February 17, 2026

## 1. Goal
Validate that AA modes remain visually stable and cross-backend consistent while preserving performance and avoiding regressions in temporal behavior.

## 2. In-Scope Modes
- `taa`
- `tuua`
- `tsr`
- `msaa_selective`
- `hybrid_tuua_msaa`
- profile hooks: `dlaa`, `fxaa_low`

## 3. Coverage Areas
- Cross-backend parity (`opengl` vs `vulkan`)
- Temporal stability (shimmer/reject/confidence drift)
- Scene-class stress behavior:
  - subpixel alpha foliage
  - specular micro-highlights
  - thin-geometry motion
  - disocclusion + rapid-pan
  - animated fast-motion suites
- Upscaler hook/vendor matrix behavior (`fsr`, `xess`, `dlss`) and native-state reporting
- Real-vs-mock Vulkan profile separation and threshold application

## 4. Primary Metrics
- PNG diff metric (`compare.diffMetric`)
- `shimmerIndex`
- `taaHistoryRejectRate`
- `taaConfidenceMean`
- `taaConfidenceDropCount`
- rolling-window drift:
  - `taaRejectTrendWindow`
  - `taaConfidenceTrendWindow`
  - `taaConfidenceDropWindow`

## 5. Test Layers

### 5.1 Fast CI-Safe Parity (mock Vulkan)
Run in PR/CI for deterministic guardrails.

```bash
mvn -pl engine-host-sample -am test \
  -Ddle.compare.tests=true \
  -Ddle.compare.opengl.mockContext=true \
  -Ddle.compare.vulkan.mockContext=true \
  -Dtest=BackendParityIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

### 5.2 Real Vulkan Rebaseline
Run on macOS Vulkan-capable host for strict thresholds.

```bash
DLE_COMPARE_VULKAN_MODE=real ./scripts/aa_rebaseline_real_mac.sh
```

### 5.3 Motion-Focused Long-Run Sampling
Run repeated stress suites, then lock thresholds.

```bash
./scripts/aa_rebaseline_real_mac.sh longrun-motion
```

Environment knobs:
- `DLE_COMPARE_LONGRUN_MOTION_RUNS`
- `DLE_COMPARE_TEMPORAL_FRAMES`
- `DLE_COMPARE_TEMPORAL_WINDOW`
- `DLE_COMPARE_TSR_FRAME_BOOST`
- `DLE_COMPARE_THRESHOLD_LOCK_MIN_RUNS`

### 5.4 Upscaler Vendor Matrix
Validate hook/native state + diff behavior for `fsr/xess/dlss`.

```bash
./scripts/aa_rebaseline_real_mac.sh upscaler-matrix
```

Output report:
- `artifacts/compare/.../vendor-matrix/upscaler-vendor-matrix.tsv`

## 6. Artifact Validation Checklist
Per run, verify:
- `compare-metadata.properties` exists in artifact directory.
- Correct mode metadata:
  - `compare.vulkan.mode` (`vulkan_real` vs `vulkan_mock`)
  - `compare.aa.acceptanceProfile` (`strict` vs `fallback`)
  - `compare.aa.mode`
- Temporal metrics are present and within profile thresholds.
- Warning-code snapshots are present:
  - `compare.opengl.warningCodes`
  - `compare.vulkan.warningCodes`
- For upscaler tests:
  - hook active: `UPSCALER_HOOK_ACTIVE`
  - native state reported: `UPSCALER_NATIVE_ACTIVE` or `UPSCALER_NATIVE_INACTIVE`

## 7. Manual Debug/Tuning Workflow
Use runtime debug views to inspect temporal internals.

Values:
- `0` off
- `1` reactive
- `2` disocclusion
- `3` historyWeight
- `4` velocity
- `5` compositeOverlay

Example:

```bash
mvn -f engine-host-sample/pom.xml exec:java \
  -Dexec.args="vulkan --interactive --taa-debug=overlay"
```

## 8. Pass/Fail Rules
- AA profile tests fail on any threshold breach (diff or temporal drift).
- Motion long-run runs are used to tighten thresholds only when repeated real Vulkan samples remain stable.
- Mock Vulkan thresholds are fallback-only for CI safety and must not replace strict real Vulkan baselines.

## 9. Known Gaps / Next Additions
- Add perf-budget assertions per AA mode at target tiers.
- Add explicit scene-level golden image review checklist (human signoff) for major threshold shifts.
- Add optional hardware-tier matrix (Apple Silicon classes / driver versions) for strict profile qualification.
