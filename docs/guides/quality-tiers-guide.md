# Quality Tiers Guide

Last updated: February 17, 2026

## 1. What Quality Tiers Support
- Runtime quality intent via `QualityTier` (`LOW`, `MEDIUM`, `HIGH`, `ULTRA`)
- Tier-aware feature behavior and warnings
- Tiered compare envelopes in parity/stress tests

## 2. Quick Start

### Run sample at each tier
```bash
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args="vulkan --tier=low"
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args="vulkan --tier=medium"
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args="vulkan --tier=high"
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args="vulkan --tier=ultra"
```

### Run tiered golden profile test
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessTieredGoldenProfilesStayBounded \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 2.1 Programmatic Setup (Java API)

```java
EngineConfig config = new EngineConfig(
    "vulkan",
    "DLE Tiered Host",
    1280,
    720,
    1.0f,
    true,
    60,
    QualityTier.ULTRA,
    Path.of("assets"),
    Map.of()
);
```

## 3. Recommended Tier Usage
- `LOW`: bring-up/CI/limited GPU budgets
- `MEDIUM`: default balanced profile
- `HIGH`: quality-focused development target
- `ULTRA`: strict visual qualification and envelope locking

## 4. Validation Rules and Constraints
- `qualityTier` must be non-null.
- Runtime may emit performance/quality degradation warnings by tier.

## 5. Troubleshooting
If tier behavior looks identical:
- Check backend options and scene options are actually tier-linked.
- Verify compare/test command includes intended tier profile.

If ULTRA fails frequently:
- Re-run with stable real Vulkan baseline and review threshold lock report.

## 6. Validation Commands

Tiered envelopes:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessTieredGoldenProfilesStayBounded \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Stress tier envelopes:
```bash
mvn -pl engine-host-sample -am test \
  -Dtest=BackendParityIntegrationTest#compareHarnessStressGoldenProfilesStayBounded \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 7. Known Gaps / Next Steps
- Add explicit tier-to-feature matrix in this guide once exposed as stable API metadata.
