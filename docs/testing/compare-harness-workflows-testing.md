# Compare Harness Workflows Testing Design

Last updated: February 17, 2026

## 1. Goal
Validate end-to-end compare harness operation, threshold management, and artifact integrity.

## 2. Coverage
- Default/mock and real Vulkan runs
- Preflight + lock-thresholds workflow
- Repo-default threshold loading by mode (`vulkan-real.properties` / `vulkan-mock.properties`)
- Threshold recommendation promotion workflow (`scripts/promote_compare_thresholds.sh`)
- Long-run and longrun-motion sampling workflows
- Upscaler matrix workflow
- Reflection profile and reflection-scene compare sweeps

## 3. Primary Metrics
- compare execution success/failure
- artifact completeness and metadata correctness
- threshold lock output generation

## 4. Execution Commands

Default run:
```bash
./scripts/aa_rebaseline_real_mac.sh
```

Real Vulkan run:
```bash
DLE_COMPARE_VULKAN_MODE=real ./scripts/aa_rebaseline_real_mac.sh
```

Threshold lock:
```bash
./scripts/aa_rebaseline_real_mac.sh lock-thresholds artifacts/compare
```

Promote recommended thresholds into repo defaults:
```bash
./scripts/promote_compare_thresholds.sh artifacts/compare/threshold-lock/recommended-thresholds.properties real
./scripts/promote_compare_thresholds.sh artifacts/compare/threshold-lock/recommended-thresholds.properties mock
```

Long-run motion:
```bash
./scripts/aa_rebaseline_real_mac.sh longrun-motion
```

Upscaler matrix:
```bash
./scripts/aa_rebaseline_real_mac.sh upscaler-matrix
```

Reflection profile override run:
```bash
MAVEN_OPTS="-Ddle.compare.reflections.profile=quality" \
./scripts/aa_rebaseline_real_mac.sh
```

## 5. Pass/Fail Criteria
- Workflows generate expected artifacts and reports.
- Real/mock outputs remain separated.
- Locked thresholds are consumable in follow-up compare runs.

## 6. Known Gaps
- Add CI checks that validate artifact manifests and metadata keys per workflow.
