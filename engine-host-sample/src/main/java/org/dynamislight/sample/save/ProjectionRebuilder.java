package org.dynamislight.sample.save;

import org.dynamis.core.entity.EntityId;
import org.dynamisecs.api.world.World;
import org.dynamisscenegraph.api.SceneNodeId;
import org.dynamisscenegraph.core.DefaultSceneGraph;
import org.vectrix.affine.Transformf;
import org.vectrix.core.Vector3f;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ProjectionRebuilder {

    public void rebuildIntoSceneGraph(World world, DefaultSceneGraph graph) {
        rebuildIntoSceneGraph(world, graph, new java.util.HashMap<>());
    }

    public void rebuildIntoSceneGraph(World world, DefaultSceneGraph graph, Map<EntityId, SceneNodeId> entityToNode) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(entityToNode, "entityToNode");

        Set<EntityId> seen = new HashSet<>();

        for (EntityId entityId : world.entities()) {
            var translation = world.get(entityId, DemoKeys.TRANSLATION);
            var bounds = world.get(entityId, DemoKeys.BOUNDS);
            var renderable = world.get(entityId, DemoKeys.RENDERABLE);
            if (translation.isEmpty() || bounds.isEmpty() || renderable.isEmpty()) {
                continue;
            }

            SceneNodeId nodeId = entityToNode.computeIfAbsent(entityId, ignored -> graph.createNode());

            var t = translation.get();
            var b = bounds.get();
            var r = renderable.get();

            Transformf transform = new Transformf();
            transform.translation.set(t.x(), t.y(), t.z());
            graph.setLocalTransform(nodeId, transform);
            graph.setLocalBoundsSphere(nodeId, new Vector3f(b.cx(), b.cy(), b.cz()), b.radius());
            graph.bindRenderable(nodeId, Integer.valueOf(r.meshHandle()), r.materialKey());
            seen.add(entityId);
        }

        var it = entityToNode.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (!seen.contains(entry.getKey())) {
                graph.unbindRenderable(entry.getValue());
                it.remove();
            }
        }
    }
}
