# Quality Tiers Testing Design

Last updated: February 17, 2026

## 1. Goal
Keep tier-specific behavior bounded and prevent regressions in tiered envelopes.

## 2. Coverage
- Tiered golden profile checks
- Stress golden profile checks
- Tier-related warnings and degradations

## 3. Primary Metrics
- per-tier scene diff thresholds
- warning code incidence by tier

## 4. Execution Commands

Tiered golden envelopes:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessTieredGoldenProfilesStayBounded \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Tiered stress envelopes:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessStressGoldenProfilesStayBounded \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 5. Pass/Fail Criteria
- All tiers satisfy their defined bounds.
- ULTRA threshold lock drift remains controlled under repeated runs.

## 6. Known Gaps
- Add per-tier perf budget assertions once standardized.
