package org.dynamislight.impl.vulkan.scene;

import org.dynamislight.api.scene.ReflectionProbeDesc;
import org.dynamislight.api.scene.Vec3;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import static org.dynamislight.impl.vulkan.math.VulkanMath.lookAt;
import static org.dynamislight.impl.vulkan.math.VulkanMath.mul;
import static org.dynamislight.impl.vulkan.math.VulkanMath.perspective;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VulkanReflectionProbeCoordinatorTest {
    @Test
    void visibleProbesCullsOutsideFrustumAndSortsByPriority() {
        float[] view = lookAt(0f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f);
        float[] proj = perspective((float) Math.toRadians(60.0), 16.0f / 9.0f, 0.1f, 100.0f);
        float[] vp = mul(proj, view);
        ReflectionProbeDesc visibleLow = probe(1, -2f, -2f, -8f, 2f, 2f, -4f, 1);
        ReflectionProbeDesc visibleHigh = probe(2, -1f, -1f, -6f, 1f, 1f, -3f, 10);
        ReflectionProbeDesc invisibleBehind = probe(3, -1f, -1f, 2f, 1f, 1f, 6f, 99);

        List<ReflectionProbeDesc> visible = VulkanReflectionProbeCoordinator.visibleProbes(
                List.of(visibleLow, visibleHigh, invisibleBehind),
                vp,
                8
        );

        assertEquals(2, visible.size());
        assertEquals(2, visible.get(0).id());
        assertEquals(1, visible.get(1).id());
    }

    @Test
    void packVisibleProbesWritesHeaderAndProbePayload() {
        float[] view = lookAt(0f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f);
        float[] proj = perspective((float) Math.toRadians(70.0), 1.0f, 0.1f, 100.0f);
        float[] vp = mul(proj, view);
        ReflectionProbeDesc probe = new ReflectionProbeDesc(
                41,
                new Vec3(1f, 2f, -3f),
                new Vec3(-2f, -1f, -5f),
                new Vec3(4f, 3f, -1f),
                "assets/probes/room_a.ktx2",
                7,
                0.75f,
                1.25f,
                true
        );

        ByteBuffer packed = VulkanReflectionProbeCoordinator.packVisibleProbes(
                List.of(probe),
                vp,
                4,
                80,
                Map.of("assets/probes/room_a.ktx2", 0),
                1,
                16 + (4 * 80)
        );

        assertEquals(1, packed.getInt(0));
        assertEquals(1, packed.getInt(4));
        assertEquals(1, packed.getInt(8));
        assertEquals(0, packed.getInt(12));
        int base = 16;
        assertEquals(1f, packed.getFloat(base), 1.0e-6f);
        assertEquals(2f, packed.getFloat(base + 4), 1.0e-6f);
        assertEquals(-3f, packed.getFloat(base + 8), 1.0e-6f);
        assertEquals(1.25f, packed.getFloat(base + 12), 1.0e-6f);
        assertEquals(-2f, packed.getFloat(base + 16), 1.0e-6f);
        assertEquals(-1f, packed.getFloat(base + 20), 1.0e-6f);
        assertEquals(-5f, packed.getFloat(base + 24), 1.0e-6f);
        assertEquals(0.75f, packed.getFloat(base + 28), 1.0e-6f);
        assertEquals(4f, packed.getFloat(base + 32), 1.0e-6f);
        assertEquals(3f, packed.getFloat(base + 36), 1.0e-6f);
        assertEquals(-1f, packed.getFloat(base + 40), 1.0e-6f);
        assertEquals(7f, packed.getFloat(base + 44), 1.0e-6f);
        assertEquals(0, packed.getInt(base + 48));
        assertEquals(1, packed.getInt(base + 52));
        assertEquals(41, packed.getInt(base + 56));
    }

    @Test
    void packVisibleProbesAssignsDeterministicCubemapSlotsByAssetPath() {
        float[] view = lookAt(0f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f);
        float[] proj = perspective((float) Math.toRadians(70.0), 1.0f, 0.1f, 100.0f);
        float[] vp = mul(proj, view);
        ReflectionProbeDesc probeHighPriorityB = new ReflectionProbeDesc(
                10,
                new Vec3(0f, 1f, -5f),
                new Vec3(-1f, 0f, -6f),
                new Vec3(1f, 2f, -4f),
                "assets/probes/b.ktx2",
                100,
                1.0f,
                1.0f,
                true
        );
        ReflectionProbeDesc probeLowPriorityA = new ReflectionProbeDesc(
                11,
                new Vec3(0f, 1f, -7f),
                new Vec3(-1f, 0f, -8f),
                new Vec3(1f, 2f, -6f),
                "assets/probes/a.ktx2",
                1,
                1.0f,
                1.0f,
                true
        );

        ByteBuffer packed = VulkanReflectionProbeCoordinator.packVisibleProbes(
                List.of(probeHighPriorityB, probeLowPriorityA),
                vp,
                4,
                80,
                Map.of(
                        "assets/probes/a.ktx2", 0,
                        "assets/probes/b.ktx2", 1
                ),
                2,
                16 + (4 * 80)
        );

        int base0 = 16;
        int base1 = 16 + 80;
        assertEquals(2, packed.getInt(0));
        assertEquals(2, packed.getInt(4));
        assertEquals(2, packed.getInt(8));
        assertEquals(0, packed.getInt(12));
        // Visible probes are sorted by priority (B then A), but cubemap slots are deterministic by path (a=0, b=1).
        assertEquals(1, packed.getInt(base0 + 48));
        assertEquals(0, packed.getInt(base1 + 48));
    }

    private static ReflectionProbeDesc probe(int id, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int priority) {
        return new ReflectionProbeDesc(
                id,
                new Vec3((minX + maxX) * 0.5f, (minY + maxY) * 0.5f, (minZ + maxZ) * 0.5f),
                new Vec3(minX, minY, minZ),
                new Vec3(maxX, maxY, maxZ),
                "assets/probes/p" + id + ".ktx2",
                priority,
                1.0f,
                1.0f,
                true
        );
    }
}
