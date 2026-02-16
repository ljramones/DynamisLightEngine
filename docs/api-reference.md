# DynamicLightEngine API Reference

This document describes the public Java API in `engine-api` (`org.dynamislight.api.*`).

## 1) Design rules
- API boundary is Java-first and JavaFX-agnostic.
- DTOs are immutable records/enums.
- No backend-native handles/objects cross the API boundary.
- Runtime interaction is single-threaded and non-reentrant.

## 2) Package index
- `org.dynamislight.api.runtime`: runtime lifecycle, stats, capabilities, frames, host callbacks.
- `org.dynamislight.api.config`: runtime config and quality tier.
- `org.dynamislight.api.scene`: scene graph DTOs (camera/transform/mesh/material/light/fog/smoke).
- `org.dynamislight.api.input`: input DTOs and key codes.
- `org.dynamislight.api.event`: event hierarchy and per-frame warnings.
- `org.dynamislight.api.error`: error model (`EngineException`, `EngineErrorCode`, reports).
- `org.dynamislight.api.logging`: structured log message DTOs.
- `org.dynamislight.api.resource`: runtime resource service and cache telemetry.
- `org.dynamislight.api.validation`: validators for config and scene contracts.

## 3) Core runtime contract
`EngineRuntime` is the host entry point:
- `initialize(EngineConfig, EngineHostCallbacks)`
- `loadScene(SceneDescriptor)`
- `update(double dtSeconds, EngineInput)`
- `render()`
- `resize(int widthPx, int heightPx, float dpiScale)`
- `getStats()`, `getCapabilities()`, `resources()`, `shutdown()`

Lifecycle order is strict: initialize once -> load/update/render/resize loop -> shutdown.

## 4) Host callbacks
`EngineHostCallbacks` exposes:
- `onEvent(EngineEvent)`
- `onLog(LogMessage)`
- `onError(EngineErrorReport)`
- optional `onFrameReady(FrameHandle)`

Callbacks must be non-blocking and must not call runtime methods synchronously.

## 5) Version compatibility
- `EngineApiVersion(major, minor, patch)` represents API versions.
- `EngineApiVersions.isRuntimeCompatible(hostRequired, runtimeVersion)` rules:
  - major must match
  - runtime minor must be >= host required minor
  - patch is not used for incompatibility checks

## 6) Configuration and quality
`EngineConfig` contains backend/app/runtime settings:
- backend selection (`backendId`)
- startup dimensions/dpi/vsync/fps
- `qualityTier` (`LOW`, `MEDIUM`, `HIGH`, `ULTRA`)
- `assetRoot`
- `backendOptions` (immutable copy)

## 7) Scene model
`SceneDescriptor` contains:
- cameras (`CameraDesc`) and active camera id
- transforms (`TransformDesc`)
- meshes (`MeshDesc`) referencing transform/material ids
- materials (`MaterialDesc`) with albedo/metallic/roughness + texture paths
- lights (`LightDesc`)
- environment (`EnvironmentDesc`)
- fog (`FogDesc`, `FogMode`)
- smoke emitters (`SmokeEmitterDesc`)

Collection fields are defensively copied to immutable lists.

## 8) Input model
`EngineInput` carries per-frame mouse/buttons/scroll + `Set<KeyCode>`.
`keysDown` is defensively copied to an immutable set.

## 9) Errors, events, and logs
### Errors
- `EngineException` includes `EngineErrorCode` and `recoverable` flag.
- `EngineErrorCode` includes lifecycle/argument/backend/shader/resource/scene/device/internal failures.

### Events
`EngineEvent` sealed hierarchy:
- `SceneLoadedEvent`
- `SceneLoadFailedEvent`
- `ResourceHotReloadedEvent`
- `DeviceLostEvent`
- `PerformanceWarningEvent`

`EngineFrameResult` also contains `List<EngineWarning>` for per-frame degradations.

### Logs
`LogMessage(level, category, message, epochMillis)` with `LogLevel` (`TRACE`..`ERROR`).

## 10) Resource API
`EngineResourceService`:
- `acquire(ResourceDescriptor)`
- `release(ResourceId)`
- `reload(ResourceId)`
- `loadedResources()`
- `stats()`

Key DTOs:
- `ResourceDescriptor(id, type, sourcePath, hotReloadable)`
- `ResourceInfo(...state/refCount/path/checksum/error...)`
- `ResourceCacheStats(cacheHits, cacheMisses, reloadRequests, reloadFailures, evictions, watcherEvents)`

## 11) Validation API
- `EngineConfigValidator.validate(EngineConfig)` throws `INVALID_ARGUMENT` on bad config.
- `SceneValidator.validate(SceneDescriptor)` throws `SCENE_VALIDATION_FAILED` on invalid graph/linking.

## 12) Minimal host example
```java
EngineConfig config = ...;
SceneDescriptor scene = ...;

try (EngineRuntime runtime = provider.createRuntime()) {
    runtime.initialize(config, callbacks);
    runtime.loadScene(scene);

    while (running) {
        runtime.update(dtSeconds, input);
        runtime.render();
    }
}
```
