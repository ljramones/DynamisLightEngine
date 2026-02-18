package org.dynamislight.impl.vulkan.scene;

import org.dynamislight.api.scene.ReflectionProbeDesc;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public final class VulkanReflectionProbeCoordinator {
    private VulkanReflectionProbeCoordinator() {
    }

    public static int uploadVisibleProbes(
            List<ReflectionProbeDesc> probes,
            float[] viewProjMatrix,
            int maxProbeCount,
            int probeStrideBytes,
            Map<String, Integer> cubemapSlots,
            int cubemapSlotCount,
            long mappedAddress,
            int bufferBytes,
            long frameTick,
            int updateCadenceFrames,
            int maxVisibleProbes,
            float lodDepthScale
    ) {
        if (mappedAddress == 0L || bufferBytes <= 0) {
            return 0;
        }
        ByteBuffer packed = packVisibleProbes(
                probes,
                viewProjMatrix,
                maxProbeCount,
                probeStrideBytes,
                cubemapSlots,
                cubemapSlotCount,
                bufferBytes,
                frameTick,
                updateCadenceFrames,
                maxVisibleProbes,
                lodDepthScale
        );
        ByteBuffer mapped = MemoryUtil.memByteBuffer(mappedAddress, packed.capacity());
        packed.clear();
        mapped.position(0);
        mapped.put(packed);
        mapped.position(0);
        return packed.getInt(0);
    }

    static ByteBuffer packVisibleProbes(
            List<ReflectionProbeDesc> probes,
            float[] viewProjMatrix,
            int maxProbeCount,
            int probeStrideBytes,
            Map<String, Integer> cubemapSlots,
            int cubemapSlotCount,
            int bufferBytes,
            long frameTick,
            int updateCadenceFrames,
            int maxVisibleProbes,
            float lodDepthScale
    ) {
        int clampedMax = Math.max(1, maxProbeCount);
        int clampedStride = Math.max(80, probeStrideBytes);
        int minBytes = 16 + (clampedMax * clampedStride);
        int outBytes = Math.max(minBytes, bufferBytes);
        ByteBuffer packed = ByteBuffer.allocate(outBytes);
        List<ReflectionProbeDesc> visible = visibleProbes(
                probes,
                viewProjMatrix,
                clampedMax,
                frameTick,
                updateCadenceFrames,
                maxVisibleProbes
        );
        Set<String> visibleUniquePaths = new HashSet<>();
        int visibleAssigned = 0;
        Map<String, Integer> safeSlots = cubemapSlots == null ? Map.of() : cubemapSlots;
        for (ReflectionProbeDesc probe : visible) {
            if (probe == null) {
                continue;
            }
            String path = probe.cubemapAssetPath();
            if (path == null || path.isBlank()) {
                continue;
            }
            if (visibleUniquePaths.add(path) && safeSlots.containsKey(path)) {
                visibleAssigned++;
            }
        }
        packed.putInt(0, visible.size());
        packed.putInt(4, Math.max(0, cubemapSlotCount));
        packed.putInt(8, visibleUniquePaths.size());
        packed.putInt(12, Math.max(0, visibleUniquePaths.size() - visibleAssigned));

        for (int i = 0; i < visible.size(); i++) {
            ReflectionProbeDesc probe = visible.get(i);
            Integer existingSlot = safeSlots.get(probe.cubemapAssetPath());
            int cubemapSlot = existingSlot != null ? existingSlot : -1;
            int base = 16 + (i * clampedStride);
            int lodTier = estimateProbeLodTier(probe, viewProjMatrix, lodDepthScale);
            putProbe(packed, base, probe, cubemapSlot, lodTier);
        }
        return packed;
    }

    static List<ReflectionProbeDesc> visibleProbes(
            List<ReflectionProbeDesc> probes,
            float[] viewProjMatrix,
            int maxProbeCount,
            long frameTick,
            int updateCadenceFrames,
            int maxVisibleProbes
    ) {
        if (probes == null || probes.isEmpty()) {
            return List.of();
        }
        Plane[] planes = extractFrustumPlanes(viewProjMatrix);
        List<ReflectionProbeDesc> visible = new ArrayList<>();
        for (ReflectionProbeDesc probe : probes) {
            if (probe == null) {
                continue;
            }
            if (aabbIntersectsFrustum(probe, planes)) {
                visible.add(probe);
            }
        }
        visible.sort(Comparator.comparingInt(ReflectionProbeDesc::priority).reversed());
        int cappedMax = Math.max(1, Math.min(maxProbeCount, maxVisibleProbes));
        if (visible.size() <= cappedMax && updateCadenceFrames <= 1) {
            return visible;
        }
        int cadence = Math.max(1, updateCadenceFrames);
        List<ReflectionProbeDesc> selected = new ArrayList<>(Math.min(cappedMax, visible.size()));
        int guaranteed = Math.min(Math.min(4, cappedMax), visible.size());
        for (int i = 0; i < guaranteed; i++) {
            selected.add(visible.get(i));
        }
        if (selected.size() < cappedMax) {
            for (int i = guaranteed; i < visible.size() && selected.size() < cappedMax; i++) {
                ReflectionProbeDesc probe = visible.get(i);
                if (cadence == 1 || Math.floorMod((long) probe.id() + frameTick, cadence) == 0) {
                    selected.add(probe);
                }
            }
        }
        if (selected.size() < cappedMax) {
            for (int i = guaranteed; i < visible.size() && selected.size() < cappedMax; i++) {
                ReflectionProbeDesc probe = visible.get(i);
                if (!selected.contains(probe)) {
                    selected.add(probe);
                }
            }
        }
        return selected;
    }

    private static void putProbe(ByteBuffer out, int base, ReflectionProbeDesc probe, int cubemapSlot, int lodTier) {
        // vec4 positionAndIntensity
        out.putFloat(base, probe.position().x());
        out.putFloat(base + 4, probe.position().y());
        out.putFloat(base + 8, probe.position().z());
        out.putFloat(base + 12, probe.intensity());
        // vec4 extentsMin (w = blendDistance)
        out.putFloat(base + 16, probe.extentsMin().x());
        out.putFloat(base + 20, probe.extentsMin().y());
        out.putFloat(base + 24, probe.extentsMin().z());
        out.putFloat(base + 28, probe.blendDistance());
        // vec4 extentsMax (w = priority as float)
        out.putFloat(base + 32, probe.extentsMax().x());
        out.putFloat(base + 36, probe.extentsMax().y());
        out.putFloat(base + 40, probe.extentsMax().z());
        out.putFloat(base + 44, probe.priority());
        // ivec4 cubemapIndexAndFlags
        out.putInt(base + 48, cubemapSlot);
        out.putInt(base + 52, probe.boxProjection() ? 1 : 0);
        out.putInt(base + 56, probe.id());
        out.putInt(base + 60, lodTier);
        // trailing padding (64..79) left zeroed by calloc.
    }

    private static int estimateProbeLodTier(ReflectionProbeDesc probe, float[] viewProjMatrix, float lodDepthScale) {
        if (probe == null || viewProjMatrix == null || viewProjMatrix.length < 16) {
            return 0;
        }
        float x = probe.position().x();
        float y = probe.position().y();
        float z = probe.position().z();
        float cx = (viewProjMatrix[0] * x) + (viewProjMatrix[4] * y) + (viewProjMatrix[8] * z) + viewProjMatrix[12];
        float cy = (viewProjMatrix[1] * x) + (viewProjMatrix[5] * y) + (viewProjMatrix[9] * z) + viewProjMatrix[13];
        float cz = (viewProjMatrix[2] * x) + (viewProjMatrix[6] * y) + (viewProjMatrix[10] * z) + viewProjMatrix[14];
        float cw = (viewProjMatrix[3] * x) + (viewProjMatrix[7] * y) + (viewProjMatrix[11] * z) + viewProjMatrix[15];
        float safeW = Math.abs(cw) < 1.0e-6f ? 1.0f : cw;
        float ndcZ = cz / safeW;
        float depth01 = Math.max(0.0f, Math.min(1.0f, ndcZ * 0.5f + 0.5f));
        float scaled = depth01 * Math.max(0.25f, Math.min(4.0f, lodDepthScale));
        return Math.max(0, Math.min(3, (int) Math.floor(scaled * 4.0f)));
    }

    private static boolean aabbIntersectsFrustum(ReflectionProbeDesc probe, Plane[] planes) {
        float minX = probe.extentsMin().x();
        float minY = probe.extentsMin().y();
        float minZ = probe.extentsMin().z();
        float maxX = probe.extentsMax().x();
        float maxY = probe.extentsMax().y();
        float maxZ = probe.extentsMax().z();
        for (Plane plane : planes) {
            float px = plane.nx >= 0f ? maxX : minX;
            float py = plane.ny >= 0f ? maxY : minY;
            float pz = plane.nz >= 0f ? maxZ : minZ;
            if ((plane.nx * px) + (plane.ny * py) + (plane.nz * pz) + plane.d < 0f) {
                return false;
            }
        }
        return true;
    }

    private static Plane[] extractFrustumPlanes(float[] m) {
        float m00 = m[0], m01 = m[4], m02 = m[8], m03 = m[12];
        float m10 = m[1], m11 = m[5], m12 = m[9], m13 = m[13];
        float m20 = m[2], m21 = m[6], m22 = m[10], m23 = m[14];
        float m30 = m[3], m31 = m[7], m32 = m[11], m33 = m[15];
        return new Plane[]{
                normalize(new Plane(m30 + m00, m31 + m01, m32 + m02, m33 + m03)), // left
                normalize(new Plane(m30 - m00, m31 - m01, m32 - m02, m33 - m03)), // right
                normalize(new Plane(m30 + m10, m31 + m11, m32 + m12, m33 + m13)), // bottom
                normalize(new Plane(m30 - m10, m31 - m11, m32 - m12, m33 - m13)), // top
                normalize(new Plane(m30 + m20, m31 + m21, m32 + m22, m33 + m23)), // near
                normalize(new Plane(m30 - m20, m31 - m21, m32 - m22, m33 - m23))  // far
        };
    }

    private static Plane normalize(Plane plane) {
        float length = (float) Math.sqrt((plane.nx * plane.nx) + (plane.ny * plane.ny) + (plane.nz * plane.nz));
        if (length <= 1.0e-6f) {
            return new Plane(plane.nx, plane.ny, plane.nz, plane.d);
        }
        return new Plane(plane.nx / length, plane.ny / length, plane.nz / length, plane.d / length);
    }

    private record Plane(float nx, float ny, float nz, float d) {
    }
}
