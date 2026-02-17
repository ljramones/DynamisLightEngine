# Config and Capabilities Guide

Last updated: February 17, 2026

## 1. What Config/Capabilities Support
- Engine startup/runtime configuration via `EngineConfig`
- Validation via `EngineConfigValidator`
- Runtime feature introspection via `EngineCapabilities`

## 2. Quick Start

### Run host with explicit backend and tier
```bash
mvn -f engine-host-sample/pom.xml exec:java \
  -Dexec.args="vulkan --tier=high"
```

### Run config/API validation tests
```bash
mvn -pl engine-api -am test
```

## 2.1 Programmatic Setup (Java API)

```java
EngineConfig config = new EngineConfig(
    "vulkan",
    "DLE Host",
    1280,
    720,
    1.0f,
    true,
    60,
    QualityTier.HIGH,
    Path.of("assets"),
    Map.of(
        "vulkan.mockContext", "false",
        "vulkan.aaPreset", "quality"
    )
);

EngineConfigValidator.validate(config);

EngineCapabilities caps = runtime.getCapabilities();
boolean temporal = caps.temporalReprojection();
Set<QualityTier> tiers = caps.supportedQualityTiers();
```

## 3. Validation Rules and Constraints
`EngineConfigValidator` enforces:
- non-blank `backendId`
- non-blank `appName`
- dimensions > 0
- `dpiScale > 0`
- `targetFps > 0`
- non-null `qualityTier`
- non-null `assetRoot`

## 4. Backend Notes
- `backendOptions` is immutable (`Map.copyOf`).
- Capability sets are immutable (`Set.copyOf`).
- Use `getCapabilities()` after initialize to gate optional features.

## 5. Troubleshooting
If init fails with `INVALID_ARGUMENT`:
- Re-check config validation rules.
- Validate before `runtime.initialize()`.

If a feature is unexpectedly disabled:
- Inspect `EngineCapabilities` and backend selection.
- Confirm runtime actually loaded expected backend.

## 6. Validation Commands

Config/API tests:
```bash
mvn -pl engine-api -am test
```

Runtime initialization check:
```bash
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args="opengl"
```

## 7. Known Gaps / Next Steps
- Add host helper for capability-based auto-tuning recommendations.
