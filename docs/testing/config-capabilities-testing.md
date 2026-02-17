# Config and Capabilities Testing Design

Last updated: February 17, 2026

## 1. Goal
Ensure runtime config validation and capability reporting remain correct and stable.

## 2. Coverage
- `EngineConfigValidator` rule coverage
- Capability object integrity and immutability
- Runtime initialization with valid/invalid configs

## 3. Primary Metrics
- expected error-code mapping for invalid configs
- capability field presence/consistency

## 4. Execution Commands

API config tests:
```bash
mvn -pl engine-api -am test
```

Runtime smoke checks:
```bash
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args="opengl"
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args="vulkan"
```

## 5. Pass/Fail Criteria
- Invalid configs fail with `INVALID_ARGUMENT`.
- Valid configs initialize successfully for available backends.

## 6. Known Gaps
- Add explicit capability snapshot regression tests by backend.
