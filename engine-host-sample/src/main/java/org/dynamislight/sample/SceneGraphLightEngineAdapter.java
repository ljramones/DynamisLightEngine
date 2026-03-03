package org.dynamislight.sample;

import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.runtime.EngineRuntime;
import org.dynamislight.api.scene.CameraDesc;
import org.dynamislight.api.scene.EnvironmentDesc;
import org.dynamislight.api.scene.FogDesc;
import org.dynamislight.api.scene.FogMode;
import org.dynamislight.api.scene.LightDesc;
import org.dynamislight.api.scene.MaterialDesc;
import org.dynamislight.api.scene.MeshDesc;
import org.dynamislight.api.scene.SceneDescriptor;
import org.dynamislight.api.scene.ShadowDesc;
import org.dynamislight.api.scene.SmokeEmitterDesc;
import org.dynamislight.api.scene.TransformDesc;
import org.dynamislight.api.scene.Vec3;
import org.dynamisscenegraph.api.SceneNodeId;
import org.dynamisscenegraph.api.extract.BatchedRenderScene;
import org.dynamisscenegraph.api.extract.InstanceBatch;
import org.dynamisscenegraph.api.extract.RenderKey;
import org.dynamisscenegraph.core.DefaultSceneGraph;
import org.vectrix.affine.Transformf;
import org.vectrix.core.Matrix4f;
import org.vectrix.core.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class SceneGraphLightEngineAdapter {
    private static final String SPIKE_CAMERA_ID = "sg-spike-camera";
    private static final String SPIKE_TRANSFORM_ID = "sg-spike-root";
    private static final String SPIKE_MESH_ID = "sg-spike-mesh";
    private static final String SPIKE_MATERIAL_ID = "sg-spike-material";

    private final DefaultSceneGraph sceneGraph = new DefaultSceneGraph();
    private final Map<RenderKey, Integer> batchHandlesByKey = new HashMap<>();
    private final List<SceneNodeId> animatedNodes = new ArrayList<>();
    private final Map<SceneNodeId, Vector3f> basePositions = new HashMap<>();

    DefaultSceneGraph sceneGraph() {
        return sceneGraph;
    }

    void loadMinimalScene(EngineRuntime engine) throws EngineException {
        engine.loadScene(minimalSceneDescriptor());
    }

    void seedDemoGrid(int width, int depth, float spacing, int meshHandle, Object materialKey) {
        animatedNodes.clear();
        basePositions.clear();

        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                SceneNodeId nodeId = sceneGraph.createNode();
                float px = (x - ((width - 1) / 2f)) * spacing;
                float pz = (z - ((depth - 1) / 2f)) * spacing;

                Transformf transform = new Transformf();
                transform.translation.set(px, 0f, pz);
                sceneGraph.setLocalTransform(nodeId, transform);
                sceneGraph.setLocalBoundsSphere(nodeId, new Vector3f(0f, 0f, 0f), 1f);
                sceneGraph.bindRenderable(nodeId, meshHandle, materialKey);

                animatedNodes.add(nodeId);
                basePositions.put(nodeId, new Vector3f(px, 0f, pz));
            }
        }
    }

    void animateDemoGrid(double timeSeconds) {
        float time = (float) timeSeconds;
        for (SceneNodeId nodeId : animatedNodes) {
            Vector3f base = basePositions.get(nodeId);
            if (base == null) {
                continue;
            }
            Transformf transform = new Transformf();
            float y = (float) Math.sin(time + (base.x * 0.25f) + (base.z * 0.2f)) * 0.35f;
            transform.translation.set(base.x, y, base.z);
            sceneGraph.setLocalTransform(nodeId, transform);
        }
    }

    void syncBatches(EngineRuntime engine, BatchedRenderScene scene) throws EngineException {
        Map<RenderKey, Boolean> seen = new HashMap<>();

        for (InstanceBatch batch : scene.batches()) {
            RenderKey key = batch.key();
            int meshHandle = requireMeshHandle(key);
            float[][] matrices = toModelMatrices(batch.worldMatrices());
            seen.put(key, Boolean.TRUE);

            Integer existing = batchHandlesByKey.get(key);
            if (existing == null) {
                int handle = engine.registerInstanceBatch(meshHandle, matrices);
                batchHandlesByKey.put(key, handle);
            } else {
                engine.updateInstanceBatch(existing, matrices);
            }
        }

        Iterator<Map.Entry<RenderKey, Integer>> it = batchHandlesByKey.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<RenderKey, Integer> entry = it.next();
            if (!seen.containsKey(entry.getKey())) {
                engine.removeInstanceBatch(entry.getValue());
                it.remove();
            }
        }
    }

    void removeAllBatches(EngineRuntime engine) throws EngineException {
        for (Integer handle : batchHandlesByKey.values()) {
            engine.removeInstanceBatch(handle);
        }
        batchHandlesByKey.clear();
    }

    static float[][] toModelMatrices(List<Matrix4f> worldMatrices) {
        float[][] matrices = new float[worldMatrices.size()][16];
        for (int i = 0; i < worldMatrices.size(); i++) {
            matrices[i] = toRowMajor16(worldMatrices.get(i));
        }
        return matrices;
    }

    private static int requireMeshHandle(RenderKey key) {
        if (!(key.meshHandle() instanceof Integer meshHandle)) {
            throw new IllegalArgumentException("RenderKey.meshHandle must be Integer for LightEngine spike: " + key.meshHandle());
        }
        return meshHandle;
    }

    private static float[] toRowMajor16(Matrix4f matrix) {
        return new float[] {
                matrix.m00(), matrix.m01(), matrix.m02(), matrix.m03(),
                matrix.m10(), matrix.m11(), matrix.m12(), matrix.m13(),
                matrix.m20(), matrix.m21(), matrix.m22(), matrix.m23(),
                matrix.m30(), matrix.m31(), matrix.m32(), matrix.m33()
        };
    }

    private static SceneDescriptor minimalSceneDescriptor() {
        CameraDesc camera = new CameraDesc(
                SPIKE_CAMERA_ID,
                new Vec3(0f, 12f, 20f),
                new Vec3(0f, 0f, 0f),
                60f,
                0.1f,
                500f
        );
        TransformDesc transform = new TransformDesc(
                SPIKE_TRANSFORM_ID,
                new Vec3(0f, 0f, 0f),
                new Vec3(0f, 0f, 0f),
                new Vec3(1f, 1f, 1f)
        );
        MeshDesc mesh = new MeshDesc(
                SPIKE_MESH_ID,
                SPIKE_TRANSFORM_ID,
                SPIKE_MATERIAL_ID,
                "meshes/box.glb"
        );
        MaterialDesc material = new MaterialDesc(
                SPIKE_MATERIAL_ID,
                new Vec3(1f, 1f, 1f),
                0.1f,
                0.7f,
                null,
                null,
                null,
                null,
                0.82f,
                true,
                true
        );
        ShadowDesc shadow = new ShadowDesc(2048, 0.005f, 2, 3);
        LightDesc light = new LightDesc("sg-spike-sun", new Vec3(0f, 20f, 0f), new Vec3(1f, 1f, 1f), 1.0f, 250f, true, shadow);
        EnvironmentDesc environment = new EnvironmentDesc(new Vec3(0.1f, 0.1f, 0.12f), 0.25f, null);
        FogDesc fog = new FogDesc(false, FogMode.NONE, new Vec3(0.5f, 0.5f, 0.5f), 0f, 0f, 0f, 0f, 0f, 0f);

        return new SceneDescriptor(
                "scenegraph-spike",
                List.of(camera),
                camera.id(),
                List.of(transform),
                List.of(mesh),
                List.of(material),
                List.of(light),
                environment,
                fog,
                List.<SmokeEmitterDesc>of(),
                null
        );
    }
}
