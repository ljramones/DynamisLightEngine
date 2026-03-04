package org.dynamislight.sample;

import org.dynamis.core.entity.EntityId;
import org.dynamisecs.core.DefaultWorld;
import org.dynamislight.api.runtime.EngineRuntime;
import org.dynamislight.sample.save.BoundsSphereComponent;
import org.dynamislight.sample.save.DemoCodecRegistry;
import org.dynamislight.sample.save.DemoKeys;
import org.dynamislight.sample.save.RenderableComponent;
import org.dynamislight.sample.save.TranslationComponent;
import org.dynamissession.api.model.EcsSnapshot;
import org.dynamissession.api.model.SaveGame;
import org.dynamissession.api.model.SaveMetadata;
import org.dynamissession.runtime.DefaultSessionManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SessionToLightEngineSpikeTest {

    @Test
    void loadProjectAndSyncShouldRegisterThenUpdateBatches() throws Exception {
        Path slot = Files.createTempFile("session-lightengine-spike-", ".dses");
        var registry = DemoCodecRegistry.build();

        DefaultWorld world = new DefaultWorld();
        seed(world);

        SaveGame save = new SaveGame(
                new SaveMetadata(1, "1.0.0-SNAPSHOT", System.currentTimeMillis(), 7L, "host-sample-slot"),
                new EcsSnapshot(List.of()));
        new DefaultSessionManager().save(slot, world, save, registry);

        SceneGraphLightEngineAdapter adapter = new SceneGraphLightEngineAdapter();
        var loaded = adapter.loadWorldFromSlot(slot);
        adapter.projectWorld(loaded);

        var batched = adapter.sceneGraph().extractBatched();
        assertEquals(1, batched.batches().size());
        assertEquals(3, batched.batches().getFirst().instanceCount());

        AtomicInteger registerCalls = new AtomicInteger();
        AtomicInteger updateCalls = new AtomicInteger();
        AtomicInteger nextHandle = new AtomicInteger(300);
        Map<Integer, float[][]> uploads = new HashMap<>();

        EngineRuntime runtime = runtimeProxy(registerCalls, updateCalls, nextHandle, uploads);

        adapter.syncFromProjectedWorld(runtime);
        assertEquals(1, registerCalls.get());
        assertEquals(0, updateCalls.get());

        float[][] firstUpload = uploads.values().iterator().next();
        assertEquals(3, firstUpload.length);
        assertEquals(16, firstUpload[0].length);

        adapter.syncFromProjectedWorld(runtime);
        assertEquals(1, registerCalls.get());
        assertEquals(1, updateCalls.get());
    }

    @Test
    void cullingExcludesFarInstances() throws Exception {
        Path slot = Files.createTempFile("session-lightengine-cull-", ".dses");
        var registry = DemoCodecRegistry.build();

        DefaultWorld world = new DefaultWorld();

        EntityId near = world.createEntity();
        world.add(near, DemoKeys.TRANSLATION, new TranslationComponent(0f, 0f, 0f));
        world.add(near, DemoKeys.BOUNDS, new BoundsSphereComponent(0f, 0f, 0f, 0.5f));
        world.add(near, DemoKeys.RENDERABLE, new RenderableComponent(1, "mat.default"));

        EntityId far = world.createEntity();
        world.add(far, DemoKeys.TRANSLATION, new TranslationComponent(0f, 0f, -10_000f));
        world.add(far, DemoKeys.BOUNDS, new BoundsSphereComponent(0f, 0f, 0f, 0.5f));
        world.add(far, DemoKeys.RENDERABLE, new RenderableComponent(1, "mat.default"));

        SaveGame save = new SaveGame(
                new SaveMetadata(1, "1.0.0-SNAPSHOT", System.currentTimeMillis(), 8L, "host-sample-cull"),
                new EcsSnapshot(List.of()));
        new DefaultSessionManager().save(slot, world, save, registry);

        SceneGraphLightEngineAdapter adapter = new SceneGraphLightEngineAdapter();
        var loaded = adapter.loadWorldFromSlot(slot);
        adapter.projectWorld(loaded);

        var culled = adapter.extractProjectedBatches(true);
        assertEquals(1, culled.batches().size());
        assertEquals(1, culled.batches().getFirst().instanceCount());

        AtomicInteger registerCalls = new AtomicInteger();
        AtomicInteger updateCalls = new AtomicInteger();
        AtomicInteger nextHandle = new AtomicInteger(500);
        Map<Integer, float[][]> uploads = new HashMap<>();

        EngineRuntime runtime = runtimeProxy(registerCalls, updateCalls, nextHandle, uploads);

        adapter.syncFromProjectedWorld(runtime, true);
        assertEquals(1, registerCalls.get());
        assertEquals(0, updateCalls.get());

        float[][] upload = uploads.values().iterator().next();
        assertEquals(1, upload.length);
        assertEquals(16, upload[0].length);
    }

    private static EngineRuntime runtimeProxy(
            AtomicInteger registerCalls,
            AtomicInteger updateCalls,
            AtomicInteger nextHandle,
            Map<Integer, float[][]> uploads
    ) {
        return (EngineRuntime) Proxy.newProxyInstance(
                EngineRuntime.class.getClassLoader(),
                new Class<?>[]{EngineRuntime.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "registerInstanceBatch" -> {
                        registerCalls.incrementAndGet();
                        int handle = nextHandle.getAndIncrement();
                        uploads.put(handle, (float[][]) args[1]);
                        yield handle;
                    }
                    case "updateInstanceBatch" -> {
                        updateCalls.incrementAndGet();
                        uploads.put((Integer) args[0], (float[][]) args[1]);
                        yield null;
                    }
                    case "removeInstanceBatch" -> {
                        uploads.remove((Integer) args[0]);
                        yield null;
                    }
                    default -> null;
                }
        );
    }

    private static void seed(DefaultWorld world) {
        for (int i = 0; i < 3; i++) {
            EntityId entity = world.createEntity();
            world.add(entity, DemoKeys.TRANSLATION, new TranslationComponent(i, 0f, 0f));
            world.add(entity, DemoKeys.BOUNDS, new BoundsSphereComponent(0f, 0f, 0f, 0.5f));
            world.add(entity, DemoKeys.RENDERABLE, new RenderableComponent(7, "mat.default"));
        }
    }
}
