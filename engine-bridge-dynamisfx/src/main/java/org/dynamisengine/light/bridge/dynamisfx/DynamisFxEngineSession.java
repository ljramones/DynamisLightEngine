package org.dynamisengine.light.bridge.dynamisfx;

import org.dynamisengine.light.api.config.EngineConfig;
import org.dynamisengine.light.api.error.EngineException;
import org.dynamisengine.light.api.runtime.EngineFrameResult;
import org.dynamisengine.light.api.runtime.EngineHostCallbacks;
import org.dynamisengine.light.api.runtime.EngineRuntime;
import org.dynamisengine.light.api.resource.ResourceId;
import org.dynamisengine.light.api.resource.ResourceInfo;
import org.dynamisengine.light.bridge.dynamisfx.model.FxInputSnapshot;
import org.dynamisengine.light.bridge.dynamisfx.model.FxSceneSnapshot;

/**
 * Convenience host adapter that owns runtime lifecycle for DynamisFX embedding.
 */
public final class DynamisFxEngineSession implements AutoCloseable {
    private final DynamisFxEngineBridge bridge = new DynamisFxEngineBridge();
    private final InputMapper inputMapper = new InputMapper();
    private final SceneMapper sceneMapper = new SceneMapper();
    private EngineRuntime runtime;

    public void start(String backendId, EngineConfig config, EngineHostCallbacks callbacks, FxSceneSnapshot initialScene)
            throws EngineException {
        runtime = bridge.createRuntime(backendId);
        runtime.initialize(config, callbacks);
        runtime.loadScene(sceneMapper.mapScene(initialScene));
    }

    public EngineFrameResult tick(double dtSeconds, FxInputSnapshot input) throws EngineException {
        ensureStarted();
        runtime.update(dtSeconds, inputMapper.mapInput(input));
        return runtime.render();
    }

    public void resize(int widthPx, int heightPx, float dpiScale) throws EngineException {
        ensureStarted();
        runtime.resize(widthPx, heightPx, dpiScale);
    }

    public void reloadScene(FxSceneSnapshot sceneSnapshot) throws EngineException {
        ensureStarted();
        runtime.loadScene(sceneMapper.mapScene(sceneSnapshot));
    }

    public java.util.List<ResourceInfo> resources() throws EngineException {
        ensureStarted();
        return runtime.resources().loadedResources();
    }

    public ResourceInfo reloadResource(String id) throws EngineException {
        ensureStarted();
        return runtime.resources().reload(new ResourceId(id));
    }

    @Override
    public void close() {
        if (runtime != null) {
            runtime.shutdown();
            runtime = null;
        }
    }

    private void ensureStarted() throws EngineException {
        if (runtime == null) {
            throw new EngineException(org.dynamisengine.light.api.error.EngineErrorCode.INVALID_STATE, "Session not started", true);
        }
    }
}
