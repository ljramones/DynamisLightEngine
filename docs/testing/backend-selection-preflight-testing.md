# Backend Selection and Preflight Testing Design

Last updated: February 17, 2026

## 1. Goal
Guarantee deterministic backend resolution and actionable preflight diagnostics.

## 2. Coverage
- BackendRegistry resolution paths
- API compatibility checks
- Vulkan preflight pass/fail diagnostics on macOS

## 3. Primary Metrics
- resolve success/failure classification
- preflight status (`OK` vs `FAILED`) and reason quality

## 4. Execution Commands

Preflight only:
```bash
./scripts/aa_rebaseline_real_mac.sh preflight
```

Real backend compare start:
```bash
DLE_COMPARE_VULKAN_MODE=real ./scripts/aa_rebaseline_real_mac.sh
```

Auto mode fallback behavior:
```bash
DLE_COMPARE_VULKAN_MODE=auto ./scripts/aa_rebaseline_real_mac.sh
```

## 5. Pass/Fail Criteria
- Missing/invalid loader state fails early with clear remediation hints.
- Valid real Vulkan setup reaches test execution phase.

## 6. Known Gaps
- Add automated negative preflight fixtures for CI.
