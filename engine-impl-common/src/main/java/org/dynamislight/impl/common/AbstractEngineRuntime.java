package org.dynamislight.impl.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.dynamislight.api.runtime.EngineApiVersion;
import org.dynamislight.api.runtime.EngineCapabilities;
import org.dynamislight.api.config.EngineConfig;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.runtime.EngineFrameResult;
import org.dynamislight.api.runtime.EngineHostCallbacks;
import org.dynamislight.api.input.EngineInput;
import org.dynamislight.api.error.EngineErrorReport;
import org.dynamislight.api.resource.EngineResourceService;
import org.dynamislight.api.runtime.EngineRuntime;
import org.dynamislight.api.runtime.EngineStats;
import org.dynamislight.api.runtime.ReflectionAdaptiveTrendSloDiagnostics;
import org.dynamislight.api.runtime.ShadowCapabilityDiagnostics;
import org.dynamislight.api.runtime.ShadowCacheDiagnostics;
import org.dynamislight.api.runtime.ShadowCadenceDiagnostics;
import org.dynamislight.api.runtime.ShadowPointBudgetDiagnostics;
import org.dynamislight.api.runtime.ShadowRtDiagnostics;
import org.dynamislight.api.runtime.ShadowHybridDiagnostics;
import org.dynamislight.api.runtime.ShadowSpotProjectedDiagnostics;
import org.dynamislight.api.runtime.ShadowTransparentReceiverDiagnostics;
import org.dynamislight.api.runtime.ShadowExtendedModeDiagnostics;
import org.dynamislight.api.runtime.ShadowTopologyDiagnostics;
import org.dynamislight.api.runtime.ShadowPhaseAPromotionDiagnostics;
import org.dynamislight.api.runtime.ShadowPhaseDPromotionDiagnostics;
import org.dynamislight.api.event.AaTelemetryEvent;
import org.dynamislight.api.event.DeviceLostEvent;
import org.dynamislight.api.event.EngineEvent;
import org.dynamislight.api.event.EngineWarning;
import org.dynamislight.api.event.PerformanceWarningEvent;
import org.dynamislight.api.runtime.FrameHandle;
import org.dynamislight.api.logging.LogLevel;
import org.dynamislight.api.logging.LogMessage;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.resource.ResourceDescriptor;
import org.dynamislight.api.resource.ResourceCacheStats;
import org.dynamislight.api.event.ResourceHotReloadedEvent;
import org.dynamislight.api.resource.ResourceId;
import org.dynamislight.api.resource.ResourceInfo;
import org.dynamislight.api.resource.ResourceState;
import org.dynamislight.api.resource.ResourceType;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.event.SceneLoadFailedEvent;
import org.dynamislight.api.event.SceneLoadedEvent;

public abstract class AbstractEngineRuntime implements EngineRuntime {
    protected record RenderMetrics(
            double cpuFrameMs,
            double gpuFrameMs,
            long drawCalls,
            long triangles,
            long visibleObjects,
            long gpuMemoryBytes
    ) {
    }

    private enum State {
        NEW,
        INITIALIZED,
        SHUTDOWN
    }

    private final String backendName;
    private final EngineCapabilities capabilities;
    private final double renderCpuFrameMs;
    private final double renderGpuFrameMs;

    private State state = State.NEW;
    private EngineHostCallbacks host;
    private Path assetRoot = Path.of(".");
    private long frameIndex;
    private EngineStats stats = new EngineStats(0.0, 0.0, 0.0, 0, 0, 0, 0, 0.0, 1.0, 0);
    private final Map<ResourceId, ResourceInfo> resourceCache = new LinkedHashMap<>();
    private final Map<ResourceId, String> resourceChecksums = new LinkedHashMap<>();
    private List<ResourceId> activeSceneResourceIds = new ArrayList<>();
    private final EngineResourceService resourceService = new RuntimeResourceService();
    private int resourceCacheMaxEntries = 256;
    private int resourceReloadMaxRetries = 2;
    private boolean resourceWatchEnabled;
    private long resourceWatchDebounceMs = 200L;
    private WatchService resourceWatchService;
    private Thread resourceWatcherThread;
    private final Map<Path, Long> watchedPathLastReloadMs = new ConcurrentHashMap<>();
    private long cacheHits;
    private long cacheMisses;
    private long reloadRequests;
    private long reloadFailures;
    private long evictions;
    private long watcherEvents;

    protected AbstractEngineRuntime(
            String backendName,
            EngineCapabilities capabilities,
            double renderCpuFrameMs,
            double renderGpuFrameMs
    ) {
        this.backendName = backendName;
        this.capabilities = capabilities;
        this.renderCpuFrameMs = renderCpuFrameMs;
        this.renderGpuFrameMs = renderGpuFrameMs;
    }

    @Override
    public EngineApiVersion apiVersion() {
        return new EngineApiVersion(1, 0, 0);
    }

    @Override
    public final void initialize(EngineConfig config, EngineHostCallbacks host) throws EngineException {
        if (state != State.NEW) {
            throw new EngineException(EngineErrorCode.INVALID_STATE, "initialize() must be called exactly once", false);
        }
        if (config == null || host == null) {
            throw new EngineException(EngineErrorCode.INVALID_ARGUMENT, "config and host are required", true);
        }

        this.host = host;
        this.assetRoot = config.assetRoot() == null ? Path.of(".") : config.assetRoot();
        this.resourceCacheMaxEntries = parseIntOption(config, "resource.cache.maxEntries", 256);
        this.resourceReloadMaxRetries = parseIntOption(config, "resource.reload.maxRetries", 2);
        this.resourceWatchDebounceMs = parseIntOption(config, "resource.watch.debounceMs", 200);
        this.resourceWatchEnabled = Boolean.parseBoolean(config.backendOptions().getOrDefault("resource.watch.enabled", "false"));
        try {
            onInitialize(config);
            startResourceWatcherIfEnabled();
            state = State.INITIALIZED;
            log(LogLevel.INFO, "LIFECYCLE", backendName + " runtime initialized");
            log(LogLevel.INFO, "SHADER", backendName + " shader subsystem initialized");
        } catch (EngineException e) {
            throw reportAndReturn(e);
        } catch (RuntimeException e) {
            throw reportAndReturn(new EngineException(EngineErrorCode.INTERNAL_ERROR, "Unexpected initialize failure: " + e.getMessage(), false));
        }
    }

    @Override
    public final void loadScene(SceneDescriptor scene) throws EngineException {
        try {
            ensureInitialized();
            if (scene == null) {
                throw new EngineException(EngineErrorCode.INVALID_ARGUMENT, "scene is required", true);
            }
            List<String> resourceFailures = acquireSceneResources(scene);
            onLoadScene(scene);
            if (!resourceFailures.isEmpty()) {
                String reason = "Resource scan failures (" + resourceFailures.size() + "): " + String.join("; ", resourceFailures);
                log(LogLevel.ERROR, "ERROR", "Scene load had resource failures: " + scene.sceneName());
                if (host != null) {
                    host.onEvent(new SceneLoadFailedEvent(scene.sceneName(), reason));
                }
                reportError(new EngineException(EngineErrorCode.SCENE_VALIDATION_FAILED, reason, true));
            } else {
                log(LogLevel.INFO, "SCENE", "Loaded scene: " + scene.sceneName());
                host.onEvent(new SceneLoadedEvent(scene.sceneName()));
            }
        } catch (EngineException e) {
            throw reportAndReturn(e);
        } catch (RuntimeException e) {
            throw reportAndReturn(new EngineException(EngineErrorCode.INTERNAL_ERROR, "Unexpected scene load failure: " + e.getMessage(), false));
        }
    }

    @Override
    public final EngineFrameResult update(double dtSeconds, EngineInput input) throws EngineException {
        try {
            ensureInitialized();
            if (dtSeconds < 0.0) {
                throw new EngineException(EngineErrorCode.INVALID_ARGUMENT, "dtSeconds cannot be negative", true);
            }
            return new EngineFrameResult(frameIndex, dtSeconds * 1000.0, 0.0, new FrameHandle(frameIndex, false), List.of());
        } catch (EngineException e) {
            throw reportAndReturn(e);
        } catch (RuntimeException e) {
            throw reportAndReturn(new EngineException(EngineErrorCode.INTERNAL_ERROR, "Unexpected update failure: " + e.getMessage(), false));
        }
    }

    @Override
    public final EngineFrameResult render() throws EngineException {
        try {
            ensureInitialized();
            RenderMetrics renderMetrics = onRender();
            frameIndex++;
            if (renderMetrics == null) {
                renderMetrics = new RenderMetrics(renderCpuFrameMs, renderGpuFrameMs, 1, 3, 1, 0);
            }
            double frameMs = Math.max(renderMetrics.cpuFrameMs(), 0.0001);
            stats = new EngineStats(
                    1000.0 / frameMs,
                    renderMetrics.cpuFrameMs(),
                    renderMetrics.gpuFrameMs(),
                    renderMetrics.drawCalls(),
                    renderMetrics.triangles(),
                    renderMetrics.visibleObjects(),
                    renderMetrics.gpuMemoryBytes(),
                    clamp01(aaHistoryRejectRate()),
                    clamp01(aaConfidenceMean()),
                    Math.max(0L, aaConfidenceDropEvents())
            );
        log(LogLevel.DEBUG, "RENDER", "Rendered frame " + frameIndex);
            log(LogLevel.DEBUG, "PERF",
                    "frame=" + frameIndex + " cpuMs=" + String.format("%.3f", stats.cpuFrameMs())
                            + " gpuMs=" + String.format("%.3f", stats.gpuFrameMs())
                            + " fps=" + String.format("%.1f", stats.fps()));
            List<EngineWarning> warnings = new ArrayList<>();
            warnings.addAll(baselineWarnings());
            warnings.addAll(frameWarnings());
            if (host != null) {
                for (EngineWarning warning : warnings) {
                    if (shouldEmitPerformanceWarningEvent(warning)) {
                        host.onEvent(new PerformanceWarningEvent(warning.code(), warning.message()));
                    }
                }
                host.onEvent(new AaTelemetryEvent(
                        frameIndex,
                        stats.taaHistoryRejectRate(),
                        stats.taaConfidenceMean(),
                        stats.taaConfidenceDropEvents()
                ));
                EngineEvent extraTelemetry = additionalTelemetryEvent(frameIndex);
                if (extraTelemetry != null) {
                    host.onEvent(extraTelemetry);
                }
            }
            return new EngineFrameResult(frameIndex, stats.cpuFrameMs(), stats.gpuFrameMs(), new FrameHandle(frameIndex, false),
                    warnings);
        } catch (EngineException e) {
            throw reportAndReturn(e);
        } catch (RuntimeException e) {
            throw reportAndReturn(new EngineException(EngineErrorCode.INTERNAL_ERROR, "Unexpected render failure: " + e.getMessage(), false));
        }
    }

    @Override
    public final void resize(int widthPx, int heightPx, float dpiScale) throws EngineException {
        try {
            ensureInitialized();
            if (widthPx <= 0 || heightPx <= 0 || dpiScale <= 0f) {
                throw new EngineException(EngineErrorCode.INVALID_ARGUMENT, "Invalid resize dimensions", true);
            }
            onResize(widthPx, heightPx, dpiScale);
            log(LogLevel.INFO, "RENDER", "Resize to " + widthPx + "x" + heightPx + " @ " + dpiScale);
        } catch (EngineException e) {
            throw reportAndReturn(e);
        } catch (RuntimeException e) {
            throw reportAndReturn(new EngineException(EngineErrorCode.INTERNAL_ERROR, "Unexpected resize failure: " + e.getMessage(), false));
        }
    }

    @Override
    public final EngineStats getStats() {
        return stats;
    }

    @Override
    public final EngineCapabilities getCapabilities() {
        return capabilities;
    }

    @Override
    public EngineResourceService resources() {
        return resourceService;
    }

    @Override
    public ReflectionAdaptiveTrendSloDiagnostics reflectionAdaptiveTrendSloDiagnostics() {
        return backendReflectionAdaptiveTrendSloDiagnostics();
    }

    @Override
    public ShadowCapabilityDiagnostics shadowCapabilityDiagnostics() {
        return backendShadowCapabilityDiagnostics();
    }

    @Override
    public ShadowCadenceDiagnostics shadowCadenceDiagnostics() {
        return backendShadowCadenceDiagnostics();
    }

    @Override
    public ShadowPointBudgetDiagnostics shadowPointBudgetDiagnostics() {
        return backendShadowPointBudgetDiagnostics();
    }

    @Override
    public ShadowSpotProjectedDiagnostics shadowSpotProjectedDiagnostics() {
        return backendShadowSpotProjectedDiagnostics();
    }

    @Override
    public ShadowCacheDiagnostics shadowCacheDiagnostics() {
        return backendShadowCacheDiagnostics();
    }

    @Override
    public ShadowRtDiagnostics shadowRtDiagnostics() {
        return backendShadowRtDiagnostics();
    }

    @Override
    public ShadowHybridDiagnostics shadowHybridDiagnostics() {
        return backendShadowHybridDiagnostics();
    }

    @Override
    public ShadowTransparentReceiverDiagnostics shadowTransparentReceiverDiagnostics() {
        return backendShadowTransparentReceiverDiagnostics();
    }

    @Override
    public ShadowExtendedModeDiagnostics shadowExtendedModeDiagnostics() {
        return backendShadowExtendedModeDiagnostics();
    }

    @Override
    public ShadowTopologyDiagnostics shadowTopologyDiagnostics() {
        return backendShadowTopologyDiagnostics();
    }

    @Override
    public ShadowPhaseAPromotionDiagnostics shadowPhaseAPromotionDiagnostics() {
        return backendShadowPhaseAPromotionDiagnostics();
    }

    @Override
    public ShadowPhaseDPromotionDiagnostics shadowPhaseDPromotionDiagnostics() {
        return backendShadowPhaseDPromotionDiagnostics();
    }

    @Override
    public final void shutdown() {
        if (state == State.SHUTDOWN) {
            return;
        }
        stopResourceWatcher();
        try {
            onShutdown();
        } catch (RuntimeException e) {
            reportError(new EngineException(EngineErrorCode.INTERNAL_ERROR, "Unexpected shutdown failure: " + e.getMessage(), false));
        }
        state = State.SHUTDOWN;
        releaseResources(activeSceneResourceIds);
        activeSceneResourceIds = new ArrayList<>();
        resourceCache.clear();
        resourceChecksums.clear();
        watchedPathLastReloadMs.clear();
        if (host != null) {
            log(LogLevel.INFO, "LIFECYCLE", backendName + " runtime shut down");
        }
    }

    protected void onInitialize(EngineConfig config) throws EngineException {
    }

    protected void onLoadScene(SceneDescriptor scene) throws EngineException {
    }

    protected RenderMetrics onRender() throws EngineException { return null; }

    protected void onResize(int widthPx, int heightPx, float dpiScale) throws EngineException { }

    protected List<EngineWarning> frameWarnings() {
        return List.of();
    }

    protected double aaHistoryRejectRate() {
        return 0.0;
    }

    protected double aaConfidenceMean() {
        return 1.0;
    }

    protected long aaConfidenceDropEvents() {
        return 0L;
    }

    protected List<EngineWarning> baselineWarnings() {
        return List.of();
    }

    protected EngineEvent additionalTelemetryEvent(long frameIndex) {
        return null;
    }

    protected boolean shouldEmitPerformanceWarningEvent(EngineWarning warning) {
        return false;
    }

    protected ReflectionAdaptiveTrendSloDiagnostics backendReflectionAdaptiveTrendSloDiagnostics() {
        return ReflectionAdaptiveTrendSloDiagnostics.unavailable();
    }

    protected ShadowCapabilityDiagnostics backendShadowCapabilityDiagnostics() {
        return ShadowCapabilityDiagnostics.unavailable();
    }

    protected ShadowCadenceDiagnostics backendShadowCadenceDiagnostics() {
        return ShadowCadenceDiagnostics.unavailable();
    }

    protected ShadowPointBudgetDiagnostics backendShadowPointBudgetDiagnostics() {
        return ShadowPointBudgetDiagnostics.unavailable();
    }

    protected ShadowSpotProjectedDiagnostics backendShadowSpotProjectedDiagnostics() {
        return ShadowSpotProjectedDiagnostics.unavailable();
    }

    protected ShadowCacheDiagnostics backendShadowCacheDiagnostics() {
        return ShadowCacheDiagnostics.unavailable();
    }

    protected ShadowRtDiagnostics backendShadowRtDiagnostics() {
        return ShadowRtDiagnostics.unavailable();
    }

    protected ShadowHybridDiagnostics backendShadowHybridDiagnostics() {
        return ShadowHybridDiagnostics.unavailable();
    }

    protected ShadowTransparentReceiverDiagnostics backendShadowTransparentReceiverDiagnostics() {
        return ShadowTransparentReceiverDiagnostics.unavailable();
    }

    protected ShadowExtendedModeDiagnostics backendShadowExtendedModeDiagnostics() {
        return ShadowExtendedModeDiagnostics.unavailable();
    }

    protected ShadowTopologyDiagnostics backendShadowTopologyDiagnostics() {
        return ShadowTopologyDiagnostics.unavailable();
    }

    protected ShadowPhaseAPromotionDiagnostics backendShadowPhaseAPromotionDiagnostics() {
        return ShadowPhaseAPromotionDiagnostics.unavailable();
    }

    protected ShadowPhaseDPromotionDiagnostics backendShadowPhaseDPromotionDiagnostics() {
        return ShadowPhaseDPromotionDiagnostics.unavailable();
    }

    protected final RenderMetrics renderMetrics(
            double cpuFrameMs,
            double gpuFrameMs,
            long drawCalls,
            long triangles,
            long visibleObjects,
            long gpuMemoryBytes
    ) {
        return new RenderMetrics(cpuFrameMs, gpuFrameMs, drawCalls, triangles, visibleObjects, gpuMemoryBytes);
    }

    protected void onShutdown() {
    }

    private void ensureInitialized() throws EngineException {
        if (state != State.INITIALIZED) {
            throw new EngineException(EngineErrorCode.INVALID_STATE, "Runtime is not initialized", false);
        }
    }

    private EngineException reportAndReturn(EngineException error) {
        reportError(error);
        return error;
    }

    private void reportError(EngineException error) {
        if (host == null) {
            return;
        }
        log(LogLevel.ERROR, "ERROR", error.getMessage());
        if (error.code() == EngineErrorCode.DEVICE_LOST) {
            host.onEvent(new DeviceLostEvent(backendName.toLowerCase(Locale.ROOT)));
        }
        host.onError(new EngineErrorReport(error.code(), error.getMessage(), error.recoverable(), error));
    }

    private void log(LogLevel level, String category, String message) {
        if (host == null) {
            return;
        }
        host.onLog(new LogMessage(level, category, message, System.currentTimeMillis()));
    }

    private void startResourceWatcherIfEnabled() throws EngineException {
        if (!resourceWatchEnabled) {
            return;
        }
        try {
            resourceWatchService = java.nio.file.FileSystems.getDefault().newWatchService();
            registerWatchTree(assetRoot);
            resourceWatcherThread = Thread.ofVirtual().name("dle-resource-watcher").start(this::watchLoop);
            log(LogLevel.INFO, "SCENE", "Resource watcher enabled at " + assetRoot);
        } catch (IOException e) {
            throw new EngineException(EngineErrorCode.RESOURCE_CREATION_FAILED,
                    "Failed to start resource watcher at " + assetRoot + ": " + e.getMessage(), true);
        }
    }

    private void stopResourceWatcher() {
        if (resourceWatcherThread != null) {
            resourceWatcherThread.interrupt();
            resourceWatcherThread = null;
        }
        if (resourceWatchService != null) {
            try {
                resourceWatchService.close();
            } catch (IOException ignored) {
            }
            resourceWatchService = null;
        }
    }

    private void registerWatchTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        Files.walk(root)
                .filter(Files::isDirectory)
                .forEach(path -> {
                    try {
                        path.register(resourceWatchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
                    } catch (IOException ignored) {
                    }
                });
    }

    private void watchLoop() {
        while (resourceWatchService != null && !Thread.currentThread().isInterrupted()) {
            try {
                WatchKey key = resourceWatchService.poll(300, TimeUnit.MILLISECONDS);
                if (key == null) {
                    continue;
                }
                Path dir = (Path) key.watchable();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (!(event.context() instanceof Path changedRel)) {
                        continue;
                    }
                    Path changed = dir.resolve(changedRel).normalize();
                    if (Files.isDirectory(changed)) {
                        registerWatchTree(changed);
                        continue;
                    }
                    watcherEvents++;
                    reloadResourcesForPath(changed);
                }
                key.reset();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ignored) {
            }
        }
    }

    private void reloadResourcesForPath(Path changed) {
        long now = System.currentTimeMillis();
        long lastReload = watchedPathLastReloadMs.getOrDefault(changed, 0L);
        if (now - lastReload < resourceWatchDebounceMs) {
            return;
        }
        watchedPathLastReloadMs.put(changed, now);
        List<ResourceId> matching = resourceCache.values().stream()
                .filter(info -> info.resolvedPath() != null && Path.of(info.resolvedPath()).normalize().equals(changed))
                .map(info -> info.descriptor().id())
                .toList();
        for (ResourceId id : matching) {
            try {
                resourceService.reload(id);
            } catch (EngineException ignored) {
            }
        }
    }

    private int parseIntOption(EngineConfig config, String key, int defaultValue) {
        String raw = config.backendOptions().get(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private List<String> acquireSceneResources(SceneDescriptor scene) throws EngineException {
        List<ResourceId> nextIds = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (MeshDesc mesh : scene.meshes()) {
            String path = mesh.meshAssetPath();
            if (isBlank(path)) {
                continue;
            }
            ResourceDescriptor descriptor = descriptor(ResourceType.MESH, path, true);
            ResourceInfo info = resourceService.acquire(descriptor);
            nextIds.add(descriptor.id());
            if (info.state() == ResourceState.FAILED) {
                failures.add(info.errorMessage());
            }
        }
        for (MaterialDesc material : scene.materials()) {
            if (!isBlank(material.albedoTexturePath())) {
                ResourceDescriptor descriptor = descriptor(ResourceType.TEXTURE, material.albedoTexturePath(), true);
                ResourceInfo info = resourceService.acquire(descriptor);
                nextIds.add(descriptor.id());
                if (info.state() == ResourceState.FAILED) {
                    failures.add(info.errorMessage());
                }
            }
            if (!isBlank(material.normalTexturePath())) {
                ResourceDescriptor descriptor = descriptor(ResourceType.TEXTURE, material.normalTexturePath(), true);
                ResourceInfo info = resourceService.acquire(descriptor);
                nextIds.add(descriptor.id());
                if (info.state() == ResourceState.FAILED) {
                    failures.add(info.errorMessage());
                }
            }
        }
        if (scene.environment() != null && !isBlank(scene.environment().skyboxAssetPath())) {
            ResourceDescriptor descriptor = descriptor(ResourceType.TEXTURE, scene.environment().skyboxAssetPath(), true);
            ResourceInfo info = resourceService.acquire(descriptor);
            nextIds.add(descriptor.id());
            if (info.state() == ResourceState.FAILED) {
                failures.add(info.errorMessage());
            }
        }
        releaseResources(activeSceneResourceIds);
        activeSceneResourceIds = nextIds;
        return failures;
    }

    private static ResourceDescriptor descriptor(ResourceType type, String sourcePath, boolean hotReloadable) {
        return new ResourceDescriptor(new ResourceId(type.name().toLowerCase() + ":" + sourcePath), type, sourcePath, hotReloadable);
    }

    private void releaseResources(List<ResourceId> ids) {
        for (ResourceId id : ids) {
            resourceService.release(id);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private final class RuntimeResourceService implements EngineResourceService {
        @Override
        public ResourceInfo acquire(ResourceDescriptor descriptor) throws EngineException {
            if (descriptor == null || descriptor.id() == null || descriptor.type() == null || isBlank(descriptor.sourcePath())) {
                throw reportAndReturn(new EngineException(EngineErrorCode.INVALID_ARGUMENT, "Invalid resource descriptor", true));
            }

            ResourceInfo existing = resourceCache.get(descriptor.id());
            if (existing != null) {
                cacheHits++;
                long now = System.currentTimeMillis();
                ResourceInfo next = new ResourceInfo(
                        existing.descriptor(),
                        existing.state(),
                        existing.refCount() + 1,
                        now,
                        existing.errorMessage(),
                        existing.resolvedPath(),
                        existing.lastChecksum()
                );
                resourceCache.put(descriptor.id(), next);
                return next;
            }
            cacheMisses++;

            ResourceInfo created = scanResource(descriptor, 1);
            resourceCache.put(descriptor.id(), created);
            if (created.state() == ResourceState.LOADED) {
                log(LogLevel.DEBUG, "SCENE", "Acquired resource " + descriptor.id().value());
            } else {
                log(LogLevel.ERROR, "ERROR", "Resource acquire failed " + descriptor.id().value() + ": " + created.errorMessage());
            }
            enforceCacheEviction();
            return created;
        }

        @Override
        public void release(ResourceId id) {
            if (id == null) {
                return;
            }
            ResourceInfo existing = resourceCache.get(id);
            if (existing == null) {
                return;
            }
            int nextCount = existing.refCount() - 1;
            if (nextCount <= 0) {
                resourceCache.put(id, new ResourceInfo(
                        existing.descriptor(),
                        existing.state(),
                        0,
                        System.currentTimeMillis(),
                        existing.errorMessage(),
                        existing.resolvedPath(),
                        existing.lastChecksum()
                ));
                log(LogLevel.DEBUG, "SCENE", "Released resource " + id.value() + " (cached)");
                enforceCacheEviction();
                return;
            }
            resourceCache.put(id, new ResourceInfo(
                    existing.descriptor(),
                    existing.state(),
                    nextCount,
                    existing.lastLoadedEpochMs(),
                    existing.errorMessage(),
                    existing.resolvedPath(),
                    existing.lastChecksum()
            ));
        }

        @Override
        public ResourceInfo reload(ResourceId id) throws EngineException {
            reloadRequests++;
            if (id == null) {
                reloadFailures++;
                throw reportAndReturn(new EngineException(EngineErrorCode.INVALID_ARGUMENT, "resource id is required", true));
            }
            ResourceInfo existing = resourceCache.get(id);
            if (existing == null) {
                reloadFailures++;
                throw reportAndReturn(new EngineException(EngineErrorCode.RESOURCE_CREATION_FAILED, "resource not loaded: " + id.value(), true));
            }
            if (!existing.descriptor().hotReloadable()) {
                reloadFailures++;
                throw reportAndReturn(new EngineException(EngineErrorCode.INVALID_ARGUMENT, "resource is not hot-reloadable: " + id.value(), true));
            }

            ResourceInfo reloaded = scanWithRetry(existing.descriptor(), existing.refCount(), resourceReloadMaxRetries);
            String previousChecksum = resourceChecksums.get(id);
            String nextChecksum = checksumOf(reloaded);
            resourceCache.put(id, reloaded);
            if (reloaded.state() == ResourceState.LOADED && nextChecksum != null) {
                resourceChecksums.put(id, nextChecksum);
            } else {
                resourceChecksums.remove(id);
            }

            if (reloaded.state() == ResourceState.LOADED) {
                if (host != null) {
                    host.onEvent(new ResourceHotReloadedEvent(id.value()));
                }
                if (!Objects.equals(previousChecksum, nextChecksum)) {
                    log(LogLevel.INFO, "SCENE", "Hot reloaded resource " + id.value());
                } else {
                    log(LogLevel.DEBUG, "SCENE", "Resource unchanged on reload " + id.value());
                }
            } else {
                reloadFailures++;
                log(LogLevel.ERROR, "ERROR", "Resource reload failed " + id.value() + ": " + reloaded.errorMessage());
            }
            return reloaded;
        }

        @Override
        public List<ResourceInfo> loadedResources() {
            return List.copyOf(resourceCache.values());
        }

        @Override
        public ResourceCacheStats stats() {
            return new ResourceCacheStats(
                    cacheHits,
                    cacheMisses,
                    reloadRequests,
                    reloadFailures,
                    evictions,
                    watcherEvents
            );
        }

        private ResourceInfo scanResource(ResourceDescriptor descriptor, int refCount) {
            Path path = resolveResourcePath(descriptor.sourcePath());
            long now = System.currentTimeMillis();
            try {
                if (!Files.isRegularFile(path)) {
                    return new ResourceInfo(
                            descriptor,
                            ResourceState.FAILED,
                            refCount,
                            now,
                            "Resource path not found: " + path,
                            path.toString(),
                            null
                    );
                }
                String checksum = checksum(path);
                resourceChecksums.put(descriptor.id(), checksum);
                return new ResourceInfo(
                        descriptor,
                        ResourceState.LOADED,
                        refCount,
                        now,
                        null,
                        path.toString(),
                        checksum
                );
            } catch (IOException e) {
                return new ResourceInfo(
                        descriptor,
                        ResourceState.FAILED,
                        refCount,
                        now,
                        "Failed to read resource " + path + ": " + e.getMessage(),
                        path.toString(),
                        null
                );
            }
        }

        private ResourceInfo scanWithRetry(ResourceDescriptor descriptor, int refCount, int maxRetries) {
            ResourceInfo result = scanResource(descriptor, refCount);
            int attempts = Math.max(0, maxRetries);
            int retryIndex = 0;
            while (attempts > 0 && result.state() == ResourceState.FAILED) {
                attempts--;
                retryIndex++;
                try {
                    Thread.sleep(Math.min(250L, 50L * retryIndex));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return result;
                }
                result = scanResource(descriptor, refCount);
            }
            return result;
        }

        private String checksumOf(ResourceInfo info) {
            return info.lastChecksum();
        }

        private String checksum(Path path) throws IOException {
            byte[] bytes = Files.readAllBytes(path);
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return HexFormat.of().formatHex(digest.digest(bytes));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 not available", e);
            }
        }

        private Path resolveResourcePath(String sourcePath) {
            Path raw = Path.of(sourcePath);
            if (raw.isAbsolute()) {
                return raw.normalize();
            }
            return assetRoot.resolve(raw).normalize();
        }

        private void enforceCacheEviction() {
            if (resourceCache.size() <= resourceCacheMaxEntries) {
                return;
            }
            List<ResourceInfo> evictable = resourceCache.values().stream()
                    .filter(info -> info.refCount() == 0)
                    .sorted(Comparator.comparingLong(ResourceInfo::lastLoadedEpochMs))
                    .toList();
            int toEvict = resourceCache.size() - resourceCacheMaxEntries;
            for (int i = 0; i < toEvict && i < evictable.size(); i++) {
                ResourceId id = evictable.get(i).descriptor().id();
                resourceCache.remove(id);
                resourceChecksums.remove(id);
                evictions++;
                log(LogLevel.DEBUG, "SCENE", "Evicted cached resource " + id.value());
            }
        }
    }
}
