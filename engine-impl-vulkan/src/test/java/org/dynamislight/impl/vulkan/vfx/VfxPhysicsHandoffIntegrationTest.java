package org.dynamislight.impl.vulkan.vfx;

import org.dynamisvfx.api.DebrisSpawnEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VfxPhysicsHandoffIntegrationTest {

    @Test
    void onDebrisSpawnCallsCollisionWorldExactlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        VulkanVfxPhysicsHandoffAdapter adapter = new VulkanVfxPhysicsHandoffAdapter(
                config -> calls.incrementAndGet(),
                meshId -> VulkanVfxPhysicsHandoffAdapter.CollisionShape.sphere(0.25f)
        );

        adapter.onDebrisSpawn(event("mesh_a", 2f, new float[]{1f, 2f, 3f}, new float[]{0.1f, 0f, 0f}));
        assertEquals(1, calls.get());
    }

    @Test
    void fallbackSphereUsedWhenMeshIdNotFound() {
        AtomicReference<VulkanVfxPhysicsHandoffAdapter.RigidBodyConfig> captured = new AtomicReference<>();
        VulkanVfxPhysicsHandoffAdapter adapter = new VulkanVfxPhysicsHandoffAdapter(
                captured::set,
                meshId -> null
        );

        adapter.onDebrisSpawn(event("missing_mesh", 1f, new float[]{0f, 0f, 0f}, new float[]{0f, 1f, 0f}));
        assertNotNull(captured.get());
        assertEquals("sphere", captured.get().shape().type());
        assertEquals(0.1f, captured.get().shape().radius());
    }

    @Test
    void rigidBodyConfigHasCorrectMass() {
        AtomicReference<VulkanVfxPhysicsHandoffAdapter.RigidBodyConfig> captured = new AtomicReference<>();
        VulkanVfxPhysicsHandoffAdapter adapter = new VulkanVfxPhysicsHandoffAdapter(
                captured::set,
                meshId -> VulkanVfxPhysicsHandoffAdapter.CollisionShape.sphere(0.2f)
        );

        adapter.onDebrisSpawn(event("mesh_b", 5.5f, new float[]{0f, 0f, 0f}, new float[]{0f, 0f, 0f}));
        assertEquals(5.5f, captured.get().mass());
    }

    @Test
    void rigidBodyConfigHasCorrectVelocity() {
        AtomicReference<VulkanVfxPhysicsHandoffAdapter.RigidBodyConfig> captured = new AtomicReference<>();
        VulkanVfxPhysicsHandoffAdapter adapter = new VulkanVfxPhysicsHandoffAdapter(
                captured::set,
                meshId -> VulkanVfxPhysicsHandoffAdapter.CollisionShape.sphere(0.2f)
        );

        float[] velocity = new float[]{3f, -1f, 2f};
        float[] angular = new float[]{0.25f, 0.5f, 0.75f};
        adapter.onDebrisSpawn(new DebrisSpawnEvent(
                new float[]{1f, 0f, 0f, 1f},
                velocity,
                angular,
                2f,
                "mesh_c",
                "stone",
                42
        ));

        assertArrayEquals(velocity, captured.get().linearVelocity());
        assertArrayEquals(angular, captured.get().angularVelocity());
    }

    private static DebrisSpawnEvent event(String meshId, float mass, float[] worldPos, float[] velocity) {
        return new DebrisSpawnEvent(
                worldPos,
                velocity,
                new float[]{0f, 0f, 0f},
                mass,
                meshId,
                "generic",
                7
        );
    }
}
