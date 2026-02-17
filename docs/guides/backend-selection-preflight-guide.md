# Backend Selection and Preflight Guide

Last updated: February 17, 2026

## 1. What Backend Selection Supports
- Backend provider discovery/resolution via `BackendRegistry`
- API compatibility checks via `EngineApiVersions`
- macOS Vulkan preflight workflow via `aa_rebaseline_real_mac.sh preflight`

## 2. Quick Start

### Select backend in sample host
```bash
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args="vulkan"
```

### Run real Vulkan preflight only
```bash
./scripts/aa_rebaseline_real_mac.sh preflight
```

## 2.1 Programmatic Setup (Java API)

```java
EngineApiVersion hostRequired = new EngineApiVersion(1, 0, 0);
EngineBackendProvider provider = BackendRegistry.discover().resolve("vulkan", hostRequired);

if (!EngineApiVersions.isRuntimeCompatible(hostRequired, provider.supportedApiVersion())) {
    throw new IllegalStateException("Incompatible runtime API version");
}
```

## 3. Preflight Commands (macOS Vulkan)

Preflight and real run:
```bash
./scripts/aa_rebaseline_real_mac.sh preflight
DLE_COMPARE_VULKAN_MODE=real ./scripts/aa_rebaseline_real_mac.sh
```

Useful env knobs:
- `DLE_COMPARE_VULKAN_MODE=mock|auto|real`
- `DLE_COMPARE_REQUIRE_MOLTENVK_VERSION=<version>`
- `DLE_VULKAN_LOADER_DIR=<path>`
- `DLE_VULKAN_ICD_JSON=<path>`

## 4. Validation Rules and Constraints
- Unknown backend ID -> `BACKEND_NOT_FOUND`.
- Multiple providers with same ID -> `INTERNAL_ERROR`.
- API mismatch -> `INVALID_ARGUMENT`.
- Real Vulkan run fails early if preflight checks fail.

## 5. Troubleshooting
If real Vulkan fails:
- Run `preflight` command first.
- Verify loader (`libvulkan.1.dylib`) and ICD/MoltenVK presence.
- Confirm `vulkaninfo` exposes required surface extensions.

If backend resolve fails:
- Check backend ID string.
- Verify runtime/backend jars on classpath.

## 6. Validation Commands

Backend selection smoke tests:
```bash
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args="opengl"
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args="vulkan"
```

Vulkan preflight:
```bash
./scripts/aa_rebaseline_real_mac.sh preflight
```

## 7. Known Gaps / Next Steps
- Add explicit backend discovery listing command in sample host.
