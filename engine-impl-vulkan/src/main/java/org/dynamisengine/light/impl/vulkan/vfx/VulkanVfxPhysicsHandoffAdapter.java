package org.dynamisengine.light.impl.vulkan.vfx;

import org.dynamisvfx.api.DebrisSpawnEvent;
import org.dynamisvfx.api.PhysicsHandoff;

import java.util.logging.Logger;

public final class VulkanVfxPhysicsHandoffAdapter implements PhysicsHandoff {
    private static final Logger LOG = Logger.getLogger(VulkanVfxPhysicsHandoffAdapter.class.getName());
    private final CollisionWorldBridge collisionWorld;
    private final MeshShapeResolver shapeResolver;

    public VulkanVfxPhysicsHandoffAdapter() {
        this(config -> {
        }, meshId -> null);
    }

    public VulkanVfxPhysicsHandoffAdapter(
            CollisionWorldBridge collisionWorld,
            MeshShapeResolver shapeResolver
    ) {
        this.collisionWorld = collisionWorld == null ? config -> {
        } : collisionWorld;
        this.shapeResolver = shapeResolver == null ? meshId -> null : shapeResolver;
    }

    @Override
    public void onDebrisSpawn(DebrisSpawnEvent event) {
        if (event == null) {
            return;
        }
        CollisionShape shape = shapeResolver.resolve(event.meshId());
        if (shape == null) {
            shape = CollisionShape.sphere(0.1f);
        }

        RigidBodyConfig config = new RigidBodyConfig(
                shape,
                event.mass(),
                safeArray(event.worldTransform()),
                safeArray(event.velocity()),
                safeArray(event.angularVelocity()),
                event.materialTag()
        );
        collisionWorld.spawnRigidBody(config);
        LOG.fine(() -> "[VFX] Debris spawn emitter=" + event.sourceEmitterId()
                + ", mass=" + event.mass()
                + ", material=" + event.materialTag());
    }

    private static float[] safeArray(float[] value) {
        return value == null ? new float[0] : value.clone();
    }

    @FunctionalInterface
    public interface CollisionWorldBridge {
        void spawnRigidBody(RigidBodyConfig config);
    }

    @FunctionalInterface
    public interface MeshShapeResolver {
        CollisionShape resolve(String meshId);
    }

    public record CollisionShape(String type, float radius) {
        public static CollisionShape sphere(float radius) {
            return new CollisionShape("sphere", radius);
        }
    }

    public record RigidBodyConfig(
            CollisionShape shape,
            float mass,
            float[] worldTransform,
            float[] linearVelocity,
            float[] angularVelocity,
            String materialTag
    ) {
    }
}
