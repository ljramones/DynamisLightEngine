package org.dynamislight.impl.vulkan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

final class VulkanGltfMeshParser {
    private static final int GLB_HEADER_SIZE = 12;
    private static final int GLB_CHUNK_HEADER_SIZE = 8;
    private static final int GLB_MAGIC = 0x46546C67;
    private static final int GLB_JSON_CHUNK = 0x4E4F534A;
    private static final int GLB_BIN_CHUNK = 0x004E4942;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path assetRoot;

    VulkanGltfMeshParser(Path assetRoot) {
        this.assetRoot = assetRoot == null ? Path.of(".") : assetRoot;
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
        try {
            if (lower.endsWith(".glb")) {
                return parseGlb(resolved);
            }
            if (lower.endsWith(".gltf")) {
                return parseGltf(resolved);
            }
            return Optional.empty();
        } catch (IOException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private Optional<MeshGeometry> parseGlb(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length < GLB_HEADER_SIZE + GLB_CHUNK_HEADER_SIZE) {
            return Optional.empty();
        }
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int magic = bb.getInt();
        int version = bb.getInt();
        int length = bb.getInt();
        if (magic != GLB_MAGIC || version < 2 || length > bytes.length) {
            return Optional.empty();
        }

        String jsonChunk = null;
        byte[] binChunk = new byte[0];
        while (bb.remaining() >= GLB_CHUNK_HEADER_SIZE) {
            int chunkLength = bb.getInt();
            int chunkType = bb.getInt();
            if (chunkLength < 0 || chunkLength > bb.remaining()) {
                return Optional.empty();
            }
            byte[] chunkData = new byte[chunkLength];
            bb.get(chunkData);
            if (chunkType == GLB_JSON_CHUNK) {
                jsonChunk = new String(chunkData, StandardCharsets.UTF_8).trim();
            } else if (chunkType == GLB_BIN_CHUNK) {
                binChunk = chunkData;
            }
        }
        if (jsonChunk == null) {
            return Optional.empty();
        }
        JsonNode root = JSON.readTree(jsonChunk);
        return parseGeometry(root, binChunk);
    }

    private Optional<MeshGeometry> parseGltf(Path path) throws IOException {
        JsonNode root = JSON.readTree(Files.readString(path));
        byte[] binary = readPrimaryBuffer(root, path);
        return parseGeometry(root, binary);
    }

    private byte[] readPrimaryBuffer(JsonNode root, Path gltfPath) throws IOException {
        JsonNode buffers = root.path("buffers");
        if (!buffers.isArray() || buffers.isEmpty()) {
            return new byte[0];
        }
        String uri = buffers.get(0).path("uri").asText("");
        if (uri.isBlank()) {
            return new byte[0];
        }
        if (uri.startsWith("data:")) {
            int comma = uri.indexOf(',');
            if (comma < 0) {
                return new byte[0];
            }
            String data = uri.substring(comma + 1);
            return Base64.getDecoder().decode(data);
        }
        Path resolved = gltfPath.getParent() == null ? assetRoot.resolve(uri) : gltfPath.getParent().resolve(uri);
        if (!Files.isRegularFile(resolved)) {
            return new byte[0];
        }
        return Files.readAllBytes(resolved);
    }

    private Optional<MeshGeometry> parseGeometry(JsonNode root, byte[] binary) {
        JsonNode meshes = root.path("meshes");
        if (!meshes.isArray() || meshes.isEmpty()) {
            return Optional.empty();
        }
        JsonNode primitives = meshes.get(0).path("primitives");
        if (!primitives.isArray() || primitives.isEmpty()) {
            return Optional.empty();
        }
        JsonNode primitive = primitives.get(0);
        int mode = primitive.path("mode").asInt(4);
        if (mode != 4 && mode != 5) {
            return Optional.empty();
        }

        int positionAccessor = primitive.path("attributes").path("POSITION").asInt(-1);
        if (positionAccessor < 0) {
            return Optional.empty();
        }

        float[] positions = readAccessorAsFloatArray(root, binary, positionAccessor, true);
        if (positions == null || positions.length < 9) {
            return Optional.empty();
        }

        int vertexCount = positions.length / 3;

        int normalAccessor = primitive.path("attributes").path("NORMAL").asInt(-1);
        float[] normals = normalAccessor >= 0 ? readAccessorAsFloatArray(root, binary, normalAccessor, true) : null;

        int uvAccessor = primitive.path("attributes").path("TEXCOORD_0").asInt(-1);
        float[] uvs = uvAccessor >= 0 ? readAccessorAsFloatArray(root, binary, uvAccessor, false) : null;

        int tangentAccessor = primitive.path("attributes").path("TANGENT").asInt(-1);
        float[] tangents = tangentAccessor >= 0 ? readAccessorAsFloatArray(root, binary, tangentAccessor, true) : null;

        int indexAccessor = primitive.path("indices").asInt(-1);
        int[] indices = indexAccessor >= 0 ? readIndexAccessor(root, binary, indexAccessor) : null;

        if (mode == 5) {
            indices = triangleStripToTriangles(indices, vertexCount);
        } else if (indices == null || indices.length == 0) {
            indices = sequentialTriangles(vertexCount);
        }

        if (indices.length < 3) {
            return Optional.empty();
        }

        normalizePositions(positions);
        float[] interleaved = interleaveVertexAttributes(vertexCount, positions, normals, uvs, tangents);
        return Optional.of(new MeshGeometry(interleaved, indices));
    }

    private float[] interleaveVertexAttributes(
            int vertexCount,
            float[] positions,
            float[] normals,
            float[] uvs,
            float[] tangents
    ) {
        float[] out = new float[vertexCount * 11];
        for (int i = 0; i < vertexCount; i++) {
            int outBase = i * 11;
            int pBase = i * 3;
            out[outBase] = positions[pBase];
            out[outBase + 1] = positions[pBase + 1];
            out[outBase + 2] = positions[pBase + 2];

            float nx = 0f;
            float ny = 0f;
            float nz = 1f;
            if (normals != null && normals.length >= pBase + 3) {
                nx = normals[pBase];
                ny = normals[pBase + 1];
                nz = normals[pBase + 2];
            }
            out[outBase + 3] = nx;
            out[outBase + 4] = ny;
            out[outBase + 5] = nz;

            int uvBase = i * 2;
            float u = (positions[pBase] * 0.5f) + 0.5f;
            float v = (positions[pBase + 1] * 0.5f) + 0.5f;
            if (uvs != null && uvs.length >= uvBase + 2) {
                u = uvs[uvBase];
                v = uvs[uvBase + 1];
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

    private float[] readAccessorAsFloatArray(JsonNode root, byte[] binary, int accessorIndex, boolean expectVec3OrVec4) {
        AccessorMeta meta = readAccessorMeta(root, accessorIndex);
        if (meta == null || binary.length == 0) {
            return null;
        }
        if (expectVec3OrVec4) {
            if (meta.components < 3 || meta.components > 4) {
                return null;
            }
        } else if (meta.components != 2) {
            return null;
        }

        int componentsOut = expectVec3OrVec4 ? 3 : 2;
        float[] out = new float[meta.count * componentsOut];
        ByteBuffer bb = ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < meta.count; i++) {
            int base = meta.offset + i * meta.stride;
            if (base + meta.componentBytes * meta.components > binary.length) {
                return null;
            }
            out[i * componentsOut] = readComponentAsFloat(bb, base, meta.componentType);
            out[i * componentsOut + 1] = readComponentAsFloat(bb, base + meta.componentBytes, meta.componentType);
            if (componentsOut == 3) {
                out[i * componentsOut + 2] = readComponentAsFloat(bb, base + 2 * meta.componentBytes, meta.componentType);
            }
        }
        return out;
    }

    private int[] readIndexAccessor(JsonNode root, byte[] binary, int accessorIndex) {
        AccessorMeta meta = readAccessorMeta(root, accessorIndex);
        if (meta == null || meta.components != 1 || binary.length == 0) {
            return null;
        }
        int[] out = new int[meta.count];
        ByteBuffer bb = ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < meta.count; i++) {
            int base = meta.offset + i * meta.stride;
            if (base + meta.componentBytes > binary.length) {
                return null;
            }
            out[i] = switch (meta.componentType) {
                case 5121 -> bb.get(base) & 0xFF;
                case 5123 -> bb.getShort(base) & 0xFFFF;
                case 5125 -> bb.getInt(base);
                default -> -1;
            };
            if (out[i] < 0) {
                return null;
            }
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

    private int[] triangleStripToTriangles(int[] indices, int vertexCount) {
        if (indices == null || indices.length < 3) {
            int count = Math.max(0, vertexCount - 2);
            int[] generated = new int[count * 3];
            int out = 0;
            for (int i = 0; i < count; i++) {
                if ((i & 1) == 0) {
                    generated[out++] = i;
                    generated[out++] = i + 1;
                    generated[out++] = i + 2;
                } else {
                    generated[out++] = i + 1;
                    generated[out++] = i;
                    generated[out++] = i + 2;
                }
            }
            return generated;
        }

        int triCount = Math.max(0, indices.length - 2);
        int[] out = new int[triCount * 3];
        int outIdx = 0;
        for (int i = 0; i < triCount; i++) {
            int a = indices[i];
            int b = indices[i + 1];
            int c = indices[i + 2];
            if ((i & 1) == 1) {
                int t = a;
                a = b;
                b = t;
            }
            out[outIdx++] = a;
            out[outIdx++] = b;
            out[outIdx++] = c;
        }
        return out;
    }

    private float readComponentAsFloat(ByteBuffer bb, int offset, int componentType) {
        return switch (componentType) {
            case 5126 -> bb.getFloat(offset);
            case 5121 -> (bb.get(offset) & 0xFF) / 255.0f;
            case 5123 -> (bb.getShort(offset) & 0xFFFF) / 65535.0f;
            case 5120 -> Math.max(-1.0f, bb.get(offset) / 127.0f);
            case 5122 -> Math.max(-1.0f, bb.getShort(offset) / 32767.0f);
            default -> 0.0f;
        };
    }

    private AccessorMeta readAccessorMeta(JsonNode root, int accessorIndex) {
        JsonNode accessors = root.path("accessors");
        JsonNode bufferViews = root.path("bufferViews");
        if (!accessors.isArray() || accessorIndex < 0 || accessorIndex >= accessors.size()) {
            return null;
        }
        JsonNode accessor = accessors.get(accessorIndex);
        int viewIndex = accessor.path("bufferView").asInt(-1);
        if (!bufferViews.isArray() || viewIndex < 0 || viewIndex >= bufferViews.size()) {
            return null;
        }
        JsonNode view = bufferViews.get(viewIndex);

        int componentType = accessor.path("componentType").asInt(-1);
        int componentBytes = switch (componentType) {
            case 5120, 5121 -> 1;
            case 5122, 5123 -> 2;
            case 5125, 5126 -> 4;
            default -> -1;
        };
        if (componentBytes < 0) {
            return null;
        }

        int components = componentCount(accessor.path("type").asText(""));
        if (components <= 0) {
            return null;
        }

        int count = accessor.path("count").asInt(0);
        if (count <= 0) {
            return null;
        }

        int accessorOffset = accessor.path("byteOffset").asInt(0);
        int viewOffset = view.path("byteOffset").asInt(0);
        int stride = view.path("byteStride").asInt(components * componentBytes);

        return new AccessorMeta(componentType, componentBytes, components, count, accessorOffset + viewOffset, stride);
    }

    private int componentCount(String type) {
        return switch (type) {
            case "SCALAR" -> 1;
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4" -> 4;
            default -> -1;
        };
    }

    private void normalizePositions(float[] interleavedOrPositions) {
        float maxAbs = 0f;
        for (int i = 0; i < interleavedOrPositions.length; i += 3) {
            maxAbs = Math.max(maxAbs, Math.abs(interleavedOrPositions[i]));
            maxAbs = Math.max(maxAbs, Math.abs(interleavedOrPositions[i + 1]));
            maxAbs = Math.max(maxAbs, Math.abs(interleavedOrPositions[i + 2]));
        }
        if (maxAbs < 0.00001f) {
            return;
        }
        float scale = 0.8f / maxAbs;
        for (int i = 0; i < interleavedOrPositions.length; i++) {
            interleavedOrPositions[i] *= scale;
        }
    }

    record MeshGeometry(float[] vertices, int[] indices) {
    }

    private record AccessorMeta(
            int componentType,
            int componentBytes,
            int components,
            int count,
            int offset,
            int stride
    ) {
    }
}
