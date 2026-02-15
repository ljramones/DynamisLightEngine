package org.dynamislight.impl.common;

import java.util.List;
import org.dynamislight.api.EngineApiVersion;
import org.dynamislight.api.EngineCapabilities;
import org.dynamislight.api.EngineConfig;
import org.dynamislight.api.EngineErrorCode;
import org.dynamislight.api.EngineException;
import org.dynamislight.api.EngineFrameResult;
import org.dynamislight.api.EngineHostCallbacks;
import org.dynamislight.api.EngineInput;
import org.dynamislight.api.EngineRuntime;
import org.dynamislight.api.EngineStats;
import org.dynamislight.api.EngineWarning;
import org.dynamislight.api.FrameHandle;
import org.dynamislight.api.LogLevel;
import org.dynamislight.api.LogMessage;
import org.dynamislight.api.SceneDescriptor;

public abstract class AbstractEngineRuntime implements EngineRuntime {
    private enum State {
        NEW,
        INITIALIZED,
        SHUTDOWN
    }

    private final String backendName;
    private final EngineCapabilities capabilities;
    private final double renderCpuFrameMs;
    private final double renderGpuFrameMs;
    private final EngineWarning stubWarning;

    private State state = State.NEW;
    private EngineHostCallbacks host;
    private long frameIndex;
    private EngineStats stats = new EngineStats(0.0, 0.0, 0.0, 0, 0, 0, 0);

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
        this.stubWarning = new EngineWarning("STUB_BACKEND", backendName + " runtime is currently a skeleton");
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
        state = State.INITIALIZED;
        this.host.onLog(new LogMessage(LogLevel.INFO, "LIFECYCLE", backendName + " runtime initialized", System.currentTimeMillis()));
    }

    @Override
    public final void loadScene(SceneDescriptor scene) throws EngineException {
        ensureInitialized();
        if (scene == null) {
            throw new EngineException(EngineErrorCode.INVALID_ARGUMENT, "scene is required", true);
        }
        host.onLog(new LogMessage(LogLevel.INFO, "SCENE", "Loaded scene: " + scene.sceneName(), System.currentTimeMillis()));
    }

    @Override
    public final EngineFrameResult update(double dtSeconds, EngineInput input) throws EngineException {
        ensureInitialized();
        if (dtSeconds < 0.0) {
            throw new EngineException(EngineErrorCode.INVALID_ARGUMENT, "dtSeconds cannot be negative", true);
        }
        return new EngineFrameResult(frameIndex, dtSeconds * 1000.0, 0.0, new FrameHandle(frameIndex, false), List.of());
    }

    @Override
    public final EngineFrameResult render() throws EngineException {
        ensureInitialized();
        frameIndex++;
        stats = new EngineStats(60.0, renderCpuFrameMs, renderGpuFrameMs, 1, 3, 1, 0);
        return new EngineFrameResult(frameIndex, stats.cpuFrameMs(), stats.gpuFrameMs(), new FrameHandle(frameIndex, false),
                List.of(stubWarning));
    }

    @Override
    public final void resize(int widthPx, int heightPx, float dpiScale) throws EngineException {
        ensureInitialized();
        if (widthPx <= 0 || heightPx <= 0 || dpiScale <= 0f) {
            throw new EngineException(EngineErrorCode.INVALID_ARGUMENT, "Invalid resize dimensions", true);
        }
        host.onLog(new LogMessage(LogLevel.INFO, "RENDER",
                "Resize to " + widthPx + "x" + heightPx + " @ " + dpiScale, System.currentTimeMillis()));
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
    public final void shutdown() {
        if (state == State.SHUTDOWN) {
            return;
        }
        state = State.SHUTDOWN;
        if (host != null) {
            host.onLog(new LogMessage(LogLevel.INFO, "LIFECYCLE", backendName + " runtime shut down", System.currentTimeMillis()));
        }
    }

    private void ensureInitialized() throws EngineException {
        if (state != State.INITIALIZED) {
            throw new EngineException(EngineErrorCode.INVALID_STATE, "Runtime is not initialized", false);
        }
    }
}
