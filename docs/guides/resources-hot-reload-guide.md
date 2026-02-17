# Resources and Hot-Reload Guide

Last updated: February 17, 2026

## 1. What Resource Management Supports
- Runtime resource lifecycle through `EngineResourceService`
- Acquire/release/reload flows
- Resource telemetry via `loadedResources()` and `stats()`
- Hot-reload events via `ResourceHotReloadedEvent`

## 2. Quick Start

### Run sample host with resource probe
```bash
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args="vulkan --resources"
```

### Run host sample tests touching resource paths
```bash
mvn -pl engine-host-sample -am test
```

## 2.1 Programmatic Setup (Java API)

```java
EngineResourceService resources = runtime.resources();

ResourceDescriptor desc = new ResourceDescriptor(
    new ResourceId("mat-albedo"),
    ResourceType.TEXTURE,
    "textures/albedo.png",
    true
);

ResourceInfo acquired = resources.acquire(desc);
ResourceInfo reloaded = resources.reload(desc.id());
resources.release(desc.id());

ResourceCacheStats stats = resources.stats();
List<ResourceInfo> loaded = resources.loadedResources();
```

## 3. Recommended Usage Pattern
- Acquire resources during scene setup/load.
- Reload only for hot-reloadable descriptors.
- Release resources when no longer needed.
- Track cache health over time (`hits`, `misses`, `reloadFailures`, `evictions`).

## 4. Validation Rules and Constraints
- Resource service may be unavailable on runtimes that do not implement it (`UnsupportedOperationException`).
- Resource load/reload failures surface as `EngineException`.

## 5. Troubleshooting
If acquire/reload fails:
- Verify `assetRoot` and resource path.
- Check resource type/path alignment.
- Inspect callback `onError` and runtime logs.

If cache churn is too high:
- Watch `evictions` and `cacheMisses` in `ResourceCacheStats`.
- Consolidate repeated acquire/release thrashing patterns.

## 6. Validation Commands

API tests:
```bash
mvn -pl engine-api -am test
```

Sample resource probe run:
```bash
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args="vulkan --resources"
```

## 7. Known Gaps / Next Steps
- Add a dedicated resource hot-reload stress harness with repeatable file-change simulation.
