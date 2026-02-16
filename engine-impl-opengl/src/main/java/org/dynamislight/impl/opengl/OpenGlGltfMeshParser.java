package org.dynamislight.impl.opengl;

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

final class OpenGlGltfMeshParser {
    private static final int GLB_HEADER_SIZE = 12;
    private static final int GLB_CHUNK_HEADER_SIZE = 8;
    private static final int GLB_MAGIC = 0x46546C67;
    private static final int GLB_JSON_CHUNK = 0x4E4F534A;
    private static final int GLB_BIN_CHUNK = 0x004E4942;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path assetRoot;

    OpenGlGltfMeshParser(Path assetRoot) {
        this.assetRoot = assetRoot == null ? Path.of(".") : assetRoot;
    }

    Optional<OpenGlContext.MeshGeometry> parse(Path meshPath) {
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

    private Optional<OpenGlContext.MeshGeometry> parseGlb(Path path) throws IOException {
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

    private Optional<OpenGlContext.MeshGeometry> parseGltf(Path path) throws IOException {
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

    private Optional<OpenGlContext.MeshGeometry> parseGeometry(JsonNode root, byte[] binary) {
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
        if (mode != 4) {
            return Optional.empty();
        }
        int positionAccessor = primitive.path("attributes").path("POSITION").asInt(-1);
        if (positionAccessor < 0) {
            return Optional.empty();
        }
        float[] positions = readVec3FloatAccessor(root, binary, positionAccessor);
        if (positions == null || positions.length < 9) {
            return Optional.empty();
        }

        int colorAccessor = primitive.path("attributes").path("COLOR_0").asInt(-1);
        float[] colors = colorAccessor >= 0 ? readVec3ColorAccessor(root, binary, colorAccessor) : null;

        int indexAccessor = primitive.path("indices").asInt(-1);
        int[] indices = indexAccessor >= 0 ? readIndexAccessor(root, binary, indexAccessor) : null;

        float[] interleaved = buildInterleaved(positions, colors, indices);
        if (interleaved.length < 18) {
            return Optional.empty();
        }
        normalizePosition(interleaved);
        return Optional.of(new OpenGlContext.MeshGeometry(interleaved));
    }

    private float[] buildInterleaved(float[] positions, float[] colors, int[] indices) {
        int vertexCount = positions.length / 3;
        if (indices == null || indices.length == 0) {
            int usable = vertexCount - (vertexCount % 3);
            float[] out = new float[usable * 6];
            for (int i = 0; i < usable; i++) {
                int posBase = i * 3;
                int outBase = i * 6;
                out[outBase] = positions[posBase];
                out[outBase + 1] = positions[posBase + 1];
                out[outBase + 2] = positions[posBase + 2];
                float[] color = colorAt(colors, i);
                out[outBase + 3] = color[0];
                out[outBase + 4] = color[1];
                out[outBase + 5] = color[2];
            }
            return out;
        }

        int usable = indices.length - (indices.length % 3);
        float[] out = new float[usable * 6];
        for (int i = 0; i < usable; i++) {
            int idx = indices[i];
            if (idx < 0 || idx >= vertexCount) {
                return new float[0];
            }
            int posBase = idx * 3;
            int outBase = i * 6;
            out[outBase] = positions[posBase];
            out[outBase + 1] = positions[posBase + 1];
            out[outBase + 2] = positions[posBase + 2];
            float[] color = colorAt(colors, idx);
            out[outBase + 3] = color[0];
            out[outBase + 4] = color[1];
            out[outBase + 5] = color[2];
        }
        return out;
    }

    private float[] colorAt(float[] colors, int vertexIndex) {
        if (colors == null) {
            return new float[]{0.7f, 0.75f, 0.9f};
        }
        int base = vertexIndex * 3;
        if (base + 2 >= colors.length) {
            return new float[]{0.7f, 0.75f, 0.9f};
        }
        return new float[]{colors[base], colors[base + 1], colors[base + 2]};
    }

    private void normalizePosition(float[] interleaved) {
        float maxAbs = 0f;
        for (int i = 0; i < interleaved.length; i += 6) {
            maxAbs = Math.max(maxAbs, Math.abs(interleaved[i]));
            maxAbs = Math.max(maxAbs, Math.abs(interleaved[i + 1]));
            maxAbs = Math.max(maxAbs, Math.abs(interleaved[i + 2]));
        }
        if (maxAbs < 0.00001f) {
            return;
        }
        float scale = 0.8f / maxAbs;
        for (int i = 0; i < interleaved.length; i += 6) {
            interleaved[i] *= scale;
            interleaved[i + 1] *= scale;
            interleaved[i + 2] *= scale;
        }
    }

    private float[] readVec3FloatAccessor(JsonNode root, byte[] binary, int accessorIndex) {
        AccessorMeta meta = readAccessorMeta(root, accessorIndex);
        if (meta == null || meta.componentType != 5126 || meta.components != 3 || binary.length == 0) {
            return null;
        }
        float[] out = new float[meta.count * 3];
        ByteBuffer bb = ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < meta.count; i++) {
            int base = meta.offset + i * meta.stride;
            if (base + 12 > binary.length) {
                return null;
            }
            out[i * 3] = bb.getFloat(base);
            out[i * 3 + 1] = bb.getFloat(base + 4);
            out[i * 3 + 2] = bb.getFloat(base + 8);
        }
        return out;
    }

    private float[] readVec3ColorAccessor(JsonNode root, byte[] binary, int accessorIndex) {
        AccessorMeta meta = readAccessorMeta(root, accessorIndex);
        if (meta == null || meta.components < 3 || binary.length == 0) {
            return null;
        }
        float[] out = new float[meta.count * 3];
        ByteBuffer bb = ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < meta.count; i++) {
            int base = meta.offset + i * meta.stride;
            if (base + meta.componentBytes * meta.components > binary.length) {
                return null;
            }
            for (int c = 0; c < 3; c++) {
                out[i * 3 + c] = readComponentAsFloat(bb, base + c * meta.componentBytes, meta.componentType);
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

    private float readComponentAsFloat(ByteBuffer bb, int offset, int componentType) {
        return switch (componentType) {
            case 5126 -> bb.getFloat(offset);
            case 5121 -> (bb.get(offset) & 0xFF) / 255.0f;
            case 5123 -> (bb.getShort(offset) & 0xFFFF) / 65535.0f;
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
            case 5121 -> 1;
            case 5123 -> 2;
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
