package org.dynamislight.impl.vulkan.asset;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;
import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.attr.VertexAttributeView;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.core.mesh.MorphTarget;
import org.meshforge.core.topology.Topology;
import org.meshforge.loader.MeshLoaders;

public final class VulkanGltfMeshParser {
    private static final Logger LOG = Logger.getLogger(VulkanGltfMeshParser.class.getName());
    private static final int STATIC_VERTEX_STRIDE_FLOATS = 11;
    private static final int SKINNED_VERTEX_STRIDE_FLOATS = 16;

    private final Path assetRoot;
    private final MeshLoaders meshLoaders;

    public VulkanGltfMeshParser(Path assetRoot) {
        this.assetRoot = assetRoot == null ? Path.of(".") : assetRoot;
        this.meshLoaders = MeshLoaders.planned();
    }

    Optional<MeshGeometry> parse(Path meshPath) {
        if (meshPath == null) {
            return Optional.empty();
        }
        Path resolved = meshPath.isAbsolute() ? meshPath : assetRoot.resolve(meshPath).normalize();
        if (!Files.isRegularFile(resolved)) {
            return Optional.empty();
        }
        String lower = resolved.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".glb") && !lower.endsWith(".gltf")) {
            return Optional.empty();
        }

        MeshGeometry geometry = parseWithMeshForge(resolved);
        String msg = "MeshForge parser handled " + resolved + ": "
                + vertexCount(geometry) + " vertices, "
                + geometry.indices().length + " indices";
        LOG.info(msg);
        emitParserTelemetry(msg);
        return Optional.of(geometry);
    }

    private MeshGeometry parseWithMeshForge(Path path) {
        try {
            MeshData mesh = meshLoaders.load(path);
            if (mesh == null) {
                throw new IllegalStateException("MeshForge returned null MeshData");
            }
            if (mesh.vertexCount() < 3) {
                throw new IllegalStateException("MeshForge returned vertexCount<3: " + mesh.vertexCount());
            }
            if (mesh.topology() != Topology.TRIANGLES) {
                throw new IllegalStateException("MeshForge returned unsupported topology: " + mesh.topology());
            }

            int vertexCount = mesh.vertexCount();
            float[] positions = readMeshForgeFloatAttribute(mesh, AttributeSemantic.POSITION, 0, 3);
            if (positions == null || positions.length < 9) {
                throw new IllegalStateException("MeshForge missing/invalid POSITION attribute");
            }
            normalizePositions(positions);

            float[] normals = readMeshForgeFloatAttribute(mesh, AttributeSemantic.NORMAL, 0, 3);
            float[] uvs = readMeshForgeFloatAttribute(mesh, AttributeSemantic.UV, 0, 2);
            float[] tangents = readMeshForgeFloatAttribute(mesh, AttributeSemantic.TANGENT, 0, 3);
            float[] weights = readMeshForgeFloatAttribute(mesh, AttributeSemantic.WEIGHTS, 0, 4);
            int[] joints = readMeshForgeJointAttribute(mesh, 0);

            int[] indices = mesh.indicesOrNull();
            if (indices == null || indices.length == 0) {
                indices = sequentialTriangles(vertexCount);
            }
            if (indices.length < 3 || indices.length % 3 != 0) {
                throw new IllegalStateException("MeshForge produced invalid index count: " + indices.length);
            }

            boolean hasSkinAttributes = weights != null && joints != null
                    && weights.length >= vertexCount * 4
                    && joints.length >= vertexCount * 4;
            int jointCount = hasSkinAttributes ? inferJointCount(joints) : 0;

            MorphTargetData morph = readMorphTargetsFromMeshForge(mesh, vertexCount);
            float[] interleaved = hasSkinAttributes
                    ? interleaveSkinnedVertexAttributes(vertexCount, positions, normals, uvs, tangents, weights, joints)
                    : interleaveVertexAttributes(vertexCount, positions, normals, uvs, tangents);

            return new MeshGeometry(interleaved, indices, hasSkinAttributes, jointCount, morph.packedDeltas(), morph.targetCount());
        } catch (Exception ex) {
            String msg = "MeshForge threw exception for " + path + ": "
                    + ex.getClass().getSimpleName() + " - " + ex.getMessage();
            LOG.warning(msg);
            emitParserTelemetry(msg);
            throw new IllegalStateException(msg, ex);
        }
    }

    private static int vertexCount(MeshGeometry geometry) {
        int strideFloats = geometry.skinned() ? SKINNED_VERTEX_STRIDE_FLOATS : STATIC_VERTEX_STRIDE_FLOATS;
        return geometry.vertices().length / strideFloats;
    }

    private static void emitParserTelemetry(String message) {
        System.out.println("[PARSER] " + message);
    }

    private MorphTargetData readMorphTargetsFromMeshForge(MeshData mesh, int vertexCount) {
        var targets = mesh.morphTargets();
        if (targets == null || targets.isEmpty()) {
            return MorphTargetData.empty();
        }
        int targetCount = targets.size();
        float[] packed = new float[targetCount * vertexCount * 6];

        for (int targetIndex = 0; targetIndex < targetCount; targetIndex++) {
            MorphTarget target = targets.get(targetIndex);
            float[] posDelta = target == null ? null : target.positionDeltas();
            float[] normalDelta = target == null ? null : target.normalDeltas();

            for (int vertexIndex = 0; vertexIndex < vertexCount; vertexIndex++) {
                int outBase = (targetIndex * vertexCount + vertexIndex) * 6;
                if (posDelta != null) {
                    int base = vertexIndex * 3;
                    if (base + 2 < posDelta.length) {
                        packed[outBase] = posDelta[base];
                        packed[outBase + 1] = posDelta[base + 1];
                        packed[outBase + 2] = posDelta[base + 2];
                    }
                }
                if (normalDelta != null) {
                    int base = vertexIndex * 3;
                    if (base + 2 < normalDelta.length) {
                        packed[outBase + 3] = normalDelta[base];
                        packed[outBase + 4] = normalDelta[base + 1];
                        packed[outBase + 5] = normalDelta[base + 2];
                    }
                }
            }
        }

        return new MorphTargetData(packed, targetCount);
    }

    private float[] readMeshForgeFloatAttribute(MeshData mesh, AttributeSemantic semantic, int setIndex, int components) {
        if (mesh == null || semantic == null || components <= 0 || !mesh.has(semantic, setIndex)) {
            return null;
        }
        VertexAttributeView view = mesh.attribute(semantic, setIndex);
        if (view == null || view.vertexCount() != mesh.vertexCount()) {
            return null;
        }
        int vertexCount = mesh.vertexCount();
        float[] out = new float[vertexCount * components];
        for (int i = 0; i < vertexCount; i++) {
            int base = i * components;
            for (int c = 0; c < components; c++) {
                out[base + c] = view.getFloat(i, c);
            }
        }
        return out;
    }

    private int[] readMeshForgeJointAttribute(MeshData mesh, int setIndex) {
        if (mesh == null || !mesh.has(AttributeSemantic.JOINTS, setIndex)) {
            return null;
        }
        VertexAttributeView view = mesh.attribute(AttributeSemantic.JOINTS, setIndex);
        if (view == null || view.vertexCount() != mesh.vertexCount()) {
            return null;
        }
        int vertexCount = mesh.vertexCount();
        int[] out = new int[vertexCount * 4];
        for (int i = 0; i < vertexCount; i++) {
            for (int c = 0; c < 4; c++) {
                int value = view.getInt(i, c);
                out[i * 4 + c] = Math.max(0, Math.min(255, value));
            }
        }
        return out;
    }

    private int inferJointCount(int[] joints) {
        int max = 0;
        for (int joint : joints) {
            if (joint > max) {
                max = joint;
            }
        }
        return max + 1;
    }

    private float[] interleaveVertexAttributes(
            int vertexCount,
            float[] positions,
            float[] normals,
            float[] uvs,
            float[] tangents
    ) {
        float[] out = new float[vertexCount * STATIC_VERTEX_STRIDE_FLOATS];
        for (int i = 0; i < vertexCount; i++) {
            int outBase = i * STATIC_VERTEX_STRIDE_FLOATS;
            int posBase = i * 3;
            out[outBase] = positions[posBase];
            out[outBase + 1] = positions[posBase + 1];
            out[outBase + 2] = positions[posBase + 2];

            float nx = 0f;
            float ny = 0f;
            float nz = 1f;
            if (normals != null) {
                int normalStride = normals.length / vertexCount;
                int nBase = i * normalStride;
                if (normals.length >= nBase + 3) {
                    nx = normals[nBase];
                    ny = normals[nBase + 1];
                    nz = normals[nBase + 2];
                }
            }
            out[outBase + 3] = nx;
            out[outBase + 4] = ny;
            out[outBase + 5] = nz;

            float u = 0f;
            float v = 0f;
            if (uvs != null) {
                int uvBase = i * 2;
                if (uvs.length >= uvBase + 2) {
                    u = uvs[uvBase];
                    v = uvs[uvBase + 1];
                }
            }
            out[outBase + 6] = u;
            out[outBase + 7] = v;

            float tx = 1f;
            float ty = 0f;
            float tz = 0f;
            if (tangents != null) {
                int tangentStride = tangents.length / vertexCount;
                int tBase = i * tangentStride;
                if (tangents.length >= tBase + 3) {
                    tx = tangents[tBase];
                    ty = tangents[tBase + 1];
                    tz = tangents[tBase + 2];
                }
            }
            out[outBase + 8] = tx;
            out[outBase + 9] = ty;
            out[outBase + 10] = tz;
        }
        return out;
    }

    private float[] interleaveSkinnedVertexAttributes(
            int vertexCount,
            float[] positions,
            float[] normals,
            float[] uvs,
            float[] tangents,
            float[] weights,
            int[] joints
    ) {
        float[] out = new float[vertexCount * SKINNED_VERTEX_STRIDE_FLOATS];
        for (int i = 0; i < vertexCount; i++) {
            int outBase = i * SKINNED_VERTEX_STRIDE_FLOATS;
            int posBase = i * 3;
            out[outBase] = positions[posBase];
            out[outBase + 1] = positions[posBase + 1];
            out[outBase + 2] = positions[posBase + 2];

            float nx = 0f;
            float ny = 0f;
            float nz = 1f;
            if (normals != null) {
                int normalStride = normals.length / vertexCount;
                int nBase = i * normalStride;
                if (normals.length >= nBase + 3) {
                    nx = normals[nBase];
                    ny = normals[nBase + 1];
                    nz = normals[nBase + 2];
                }
            }
            out[outBase + 3] = nx;
            out[outBase + 4] = ny;
            out[outBase + 5] = nz;

            float u = 0f;
            float v = 0f;
            if (uvs != null) {
                int uvBase = i * 2;
                if (uvs.length >= uvBase + 2) {
                    u = uvs[uvBase];
                    v = uvs[uvBase + 1];
                }
            }
            out[outBase + 6] = u;
            out[outBase + 7] = v;

            float tx = 1f;
            float ty = 0f;
            float tz = 0f;
            if (tangents != null) {
                int tangentStride = tangents.length / vertexCount;
                int tBase = i * tangentStride;
                if (tangents.length >= tBase + 3) {
                    tx = tangents[tBase];
                    ty = tangents[tBase + 1];
                    tz = tangents[tBase + 2];
                }
            }
            out[outBase + 8] = tx;
            out[outBase + 9] = ty;
            out[outBase + 10] = tz;

            int wBase = i * 4;
            float wx = weights[wBase];
            float wy = weights[wBase + 1];
            float wz = weights[wBase + 2];
            float ww = weights[wBase + 3];
            float sum = wx + wy + wz + ww;
            if (sum > 0.000001f) {
                float inv = 1.0f / sum;
                wx *= inv;
                wy *= inv;
                wz *= inv;
                ww *= inv;
            } else {
                wx = 1f;
                wy = 0f;
                wz = 0f;
                ww = 0f;
            }
            out[outBase + 11] = wx;
            out[outBase + 12] = wy;
            out[outBase + 13] = wz;
            out[outBase + 14] = ww;

            int jBase = i * 4;
            int packed = (joints[jBase] & 0xFF)
                    | ((joints[jBase + 1] & 0xFF) << 8)
                    | ((joints[jBase + 2] & 0xFF) << 16)
                    | ((joints[jBase + 3] & 0xFF) << 24);
            // Keep the interleaved upload path float-based; loc5 reinterprets bits as R8G8B8A8_UINT.
            out[outBase + 15] = Float.intBitsToFloat(packed);
        }
        return out;
    }

    private int[] sequentialTriangles(int vertexCount) {
        int usable = vertexCount - (vertexCount % 3);
        int[] out = new int[usable];
        for (int i = 0; i < usable; i++) {
            out[i] = i;
        }
        return out;
    }

    private void normalizePositions(float[] positions) {
        float maxAbs = 0f;
        for (int i = 0; i < positions.length; i += 3) {
            maxAbs = Math.max(maxAbs, Math.abs(positions[i]));
            maxAbs = Math.max(maxAbs, Math.abs(positions[i + 1]));
            maxAbs = Math.max(maxAbs, Math.abs(positions[i + 2]));
        }
        if (maxAbs < 0.00001f) {
            return;
        }
        float scale = 0.8f / maxAbs;
        for (int i = 0; i < positions.length; i++) {
            positions[i] *= scale;
        }
    }

    public record MeshGeometry(
            float[] vertices,
            int[] indices,
            boolean skinned,
            int jointCount,
            float[] morphTargetDeltas,
            int morphTargetCount
    ) {
    }

    private record MorphTargetData(float[] packedDeltas, int targetCount) {
        static MorphTargetData empty() {
            return new MorphTargetData(null, 0);
        }
    }
}
