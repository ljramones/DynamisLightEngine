package org.dynamislight.sample;

import org.dynamislight.api.runtime.EngineRuntime;
import org.dynamisscenegraph.api.extract.BatchedRenderScene;
import org.junit.jupiter.api.Test;
import org.vectrix.affine.Transformf;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SceneGraphAdapterTest {

    @Test
    void sceneGraphBatchedExtractionProducesSingleBatchWithExpectedSize() {
        SceneGraphLightEngineAdapter adapter = new SceneGraphLightEngineAdapter();
        adapter.seedDemoGrid(3, 1, 1.5f, 1, "mat");

        BatchedRenderScene scene = adapter.sceneGraph().extractBatched();
        assertEquals(1, scene.batches().size());
        assertEquals(3, scene.batches().getFirst().instanceCount());

        float[][] matrices = SceneGraphLightEngineAdapter.toModelMatrices(scene.batches().getFirst().worldMatrices());
        assertEquals(3, matrices.length);
        assertEquals(16, matrices[0].length);
    }

    @Test
    void syncBatchesRegistersThenUpdates() throws Exception {
        SceneGraphLightEngineAdapter adapter = new SceneGraphLightEngineAdapter();
        adapter.seedDemoGrid(3, 1, 1.0f, 1, "mat");

        AtomicInteger registerCalls = new AtomicInteger();
        AtomicInteger updateCalls = new AtomicInteger();
        AtomicInteger removeCalls = new AtomicInteger();
        AtomicInteger nextHandle = new AtomicInteger(100);
        Map<Integer, float[][]> lastUploadByHandle = new HashMap<>();

        EngineRuntime runtime = (EngineRuntime) Proxy.newProxyInstance(
                EngineRuntime.class.getClassLoader(),
                new Class<?>[] {EngineRuntime.class},
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "registerInstanceBatch" -> {
                            registerCalls.incrementAndGet();
                            int handle = nextHandle.getAndIncrement();
                            lastUploadByHandle.put(handle, (float[][]) args[1]);
                            yield handle;
                        }
                        case "updateInstanceBatch" -> {
                            updateCalls.incrementAndGet();
                            lastUploadByHandle.put((Integer) args[0], (float[][]) args[1]);
                            yield null;
                        }
                        case "removeInstanceBatch" -> {
                            removeCalls.incrementAndGet();
                            lastUploadByHandle.remove((Integer) args[0]);
                            yield null;
                        }
                        default -> null;
                    };
                }
        );

        adapter.syncBatches(runtime, adapter.sceneGraph().extractBatched());
        assertEquals(1, registerCalls.get());
        assertEquals(0, updateCalls.get());

        adapter.animateDemoGrid(1.0);
        adapter.syncBatches(runtime, adapter.sceneGraph().extractBatched());
        assertEquals(1, registerCalls.get());
        assertEquals(1, updateCalls.get());

        adapter.removeAllBatches(runtime);
        assertEquals(1, removeCalls.get());
        assertTrue(lastUploadByHandle.isEmpty());
    }
}
