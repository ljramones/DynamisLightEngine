package org.dynamislight.impl.opengl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class OpenGlGltfMeshParser {
    private static final int GLB_HEADER_SIZE = 12;
    private static final int GLB_CHUNK_HEADER_SIZE = 8;
    private static final int GLB_MAGIC = 0x46546C67;
    private static final int GLB_JSON_CHUNK = 0x4E4F534A;
    private static final int GLB_BIN_CHUNK = 0x004E4942;
    private static final ObjectMapper JSON = new ObjectMapper();

    record ParsedPrimitive(
            OpenGlContext.MeshGeometry geometry,
            int materialIndex,
            String meshName,
            int meshIndex,
            int primitiveIndex
    ) {
    }

    record GltfMaterial(
            float[] baseColorFactor,
            float metallicFactor,
            float roughnessFactor,
            int baseColorTextureIndex,
            int metallicRoughnessTextureIndex,
            int normalTextureIndex,
            int occlusionTextureIndex
    ) {
    }

    record GltfScene(
            List<ParsedPrimitive> primitives,
            List<GltfMaterial> materials,
            List<ByteBuffer> imageBuffers
    ) {
    }

    private final Path assetRoot;

    OpenGlGltfMeshParser(Path assetRoot) {
        this.assetRoot = assetRoot == null ? Path.of(".") : assetRoot;
    }

    /**
     * Legacy single-mesh parse for backward compatibility.
     */
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
            GlbData data;
            if (lower.endsWith(".glb")) {
                data = readGlb(resolved);
            } else if (lower.endsWith(".gltf")) {
                data = readGltf(resolved);
            } else {
                return Optional.empty();
            }
            if (data == null) {
                return Optional.empty();
            }
            return parseLegacySingleGeometry(data.root, data.binary);
        } catch (IOException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Full scene parse: all meshes, all primitives, materials, embedded textures.
     */
    Optional<GltfScene> parseScene(Path meshPath) {
        if (meshPath == null) {
            return Optional.empty();
        }
        Path resolved = meshPath.isAbsolute() ? meshPath : assetRoot.resolve(meshPath).normalize();
        if (!Files.isRegularFile(resolved)) {
            return Optional.empty();
        }
        Path baseDir = resolved.getParent() != null ? resolved.getParent() : assetRoot;
        String lower = resolved.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            GlbData data;
            if (lower.endsWith(".glb")) {
                data = readGlb(resolved);
            } else if (lower.endsWith(".gltf")) {
                data = readGltf(resolved);
            } else {
                return Optional.empty();
            }
            if (data == null) {
                return Optional.empty();
            }
            return parseFullScene(data.root, data.binary, baseDir);
        } catch (IOException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private record GlbData(JsonNode root, byte[] binary) {
    }

    private GlbData readGlb(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length < GLB_HEADER_SIZE + GLB_CHUNK_HEADER_SIZE) {
            return null;
        }
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int magic = bb.getInt();
        int version = bb.getInt();
        int length = bb.getInt();
        if (magic != GLB_MAGIC || version < 2 || length > bytes.length) {
            return null;
        }

        String jsonChunk = null;
        byte[] binChunk = new byte[0];
        while (bb.remaining() >= GLB_CHUNK_HEADER_SIZE) {
            int chunkLength = bb.getInt();
            int chunkType = bb.getInt();
            if (chunkLength < 0 || chunkLength > bb.remaining()) {
                return null;
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
            return null;
        }
        return new GlbData(JSON.readTree(jsonChunk), binChunk);
    }

    private GlbData readGltf(Path path) throws IOException {
        JsonNode root = JSON.readTree(Files.readString(path));
        byte[] binary = readPrimaryBuffer(root, path);
        return new GlbData(root, binary);
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

    // ---- Legacy single-mesh parse (backward compat for box.glb etc.) ----

    private Optional<OpenGlContext.MeshGeometry> parseLegacySingleGeometry(JsonNode root, byte[] binary) {
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
        float[] positions = readVec3Accessor(root, binary, positionAccessor);
        if (positions == null || positions.length < 9) {
            return Optional.empty();
        }

        int colorAccessor = primitive.path("attributes").path("COLOR_0").asInt(-1);
        float[] colors = colorAccessor >= 0 ? readVec3Accessor(root, binary, colorAccessor) : null;

        int indexAccessor = primitive.path("indices").asInt(-1);
        int[] indices = indexAccessor >= 0 ? readIndexAccessor(root, binary, indexAccessor) : null;
        if (mode == 5) {
            indices = triangleStripToTriangles(indices, positions.length / 3);
        }

        float[] interleaved = buildLegacyInterleaved(positions, colors, indices);
        if (interleaved.length < 18) {
            return Optional.empty();
        }
        normalizeLegacyPosition(interleaved);
        return Optional.of(new OpenGlContext.MeshGeometry(interleaved));
    }

    private float[] buildLegacyInterleaved(float[] positions, float[] colors, int[] indices) {
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
                float[] color = legacyColorAt(colors, i);
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
            float[] color = legacyColorAt(colors, idx);
            out[outBase + 3] = color[0];
            out[outBase + 4] = color[1];
            out[outBase + 5] = color[2];
        }
        return out;
    }

    private float[] legacyColorAt(float[] colors, int vertexIndex) {
        if (colors == null) {
            return new float[]{0.7f, 0.75f, 0.9f};
        }
        int base = vertexIndex * 3;
        if (base + 2 >= colors.length) {
            return new float[]{0.7f, 0.75f, 0.9f};
        }
        return new float[]{colors[base], colors[base + 1], colors[base + 2]};
    }

    private void normalizeLegacyPosition(float[] interleaved) {
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

    // ---- Full scene parse (all meshes, materials, textures) ----

    private Optional<GltfScene> parseFullScene(JsonNode root, byte[] binary, Path baseDir) {
        JsonNode meshesNode = root.path("meshes");
        if (!meshesNode.isArray() || meshesNode.isEmpty()) {
            return Optional.empty();
        }

        List<ParsedPrimitive> allPrimitives = new ArrayList<>();
        for (int mi = 0; mi < meshesNode.size(); mi++) {
            JsonNode mesh = meshesNode.get(mi);
            String meshName = mesh.path("name").asText("mesh-" + mi);
            JsonNode primitivesNode = mesh.path("primitives");
            if (!primitivesNode.isArray()) {
                continue;
            }
            for (int pi = 0; pi < primitivesNode.size(); pi++) {
                JsonNode prim = primitivesNode.get(pi);
                int mode = prim.path("mode").asInt(4);
                if (mode != 4 && mode != 5) {
                    continue;
                }
                ParsedPrimitive parsed = parsePrimitive(root, binary, prim, mode, meshName, mi, pi);
                if (parsed != null) {
                    allPrimitives.add(parsed);
                }
            }
        }

        if (allPrimitives.isEmpty()) {
            return Optional.empty();
        }

        List<GltfMaterial> materials = parseMaterials(root);
        List<ByteBuffer> imageBuffers = extractImageBuffers(root, binary, baseDir);

        return Optional.of(new GltfScene(allPrimitives, materials, imageBuffers));
    }

    private ParsedPrimitive parsePrimitive(JsonNode root, byte[] binary, JsonNode prim,
                                           int mode, String meshName, int meshIndex, int primIndex) {
        JsonNode attrs = prim.path("attributes");
        int posAccessor = attrs.path("POSITION").asInt(-1);
        if (posAccessor < 0) {
            return null;
        }
        float[] positions = readVec3Accessor(root, binary, posAccessor);
        if (positions == null || positions.length < 9) {
            return null;
        }

        int normalAccessor = attrs.path("NORMAL").asInt(-1);
        float[] normals = normalAccessor >= 0 ? readVec3Accessor(root, binary, normalAccessor) : null;

        int uvAccessor = attrs.path("TEXCOORD_0").asInt(-1);
        float[] uvs = uvAccessor >= 0 ? readVec2Accessor(root, binary, uvAccessor) : null;

        int indexAccessor = prim.path("indices").asInt(-1);
        int[] indices = indexAccessor >= 0 ? readIndexAccessor(root, binary, indexAccessor) : null;
        if (mode == 5) {
            indices = triangleStripToTriangles(indices, positions.length / 3);
        }

        int materialIndex = prim.path("material").asInt(-1);

        float[] interleaved = buildExtendedInterleaved(positions, normals, uvs, indices);
        if (interleaved.length < 24) { // at least 3 vertices * 8 floats
            return null;
        }

        OpenGlContext.MeshGeometry geometry = new OpenGlContext.MeshGeometry(
                interleaved, OpenGlContext.VertexFormat.POS_NORMAL_UV_8F);
        return new ParsedPrimitive(geometry, materialIndex, meshName, meshIndex, primIndex);
    }

    private float[] buildExtendedInterleaved(float[] positions, float[] normals, float[] uvs, int[] indices) {
        int vertexCount = positions.length / 3;

        if (indices == null || indices.length == 0) {
            int usable = vertexCount - (vertexCount % 3);
            float[] out = new float[usable * 8];
            for (int i = 0; i < usable; i++) {
                writeExtendedVertex(out, i, positions, normals, uvs, i, vertexCount);
            }
            if (normals == null) {
                generateFlatNormals(out, usable);
            }
            return out;
        }

        int usable = indices.length - (indices.length % 3);
        float[] out = new float[usable * 8];
        for (int i = 0; i < usable; i++) {
            int idx = indices[i];
            if (idx < 0 || idx >= vertexCount) {
                return new float[0];
            }
            writeExtendedVertex(out, i, positions, normals, uvs, idx, vertexCount);
        }
        if (normals == null) {
            generateFlatNormals(out, usable);
        }
        return out;
    }

    private void writeExtendedVertex(float[] out, int outIndex, float[] positions,
                                     float[] normals, float[] uvs, int srcIndex, int vertexCount) {
        int posBase = srcIndex * 3;
        int outBase = outIndex * 8;
        // position
        out[outBase] = positions[posBase];
        out[outBase + 1] = positions[posBase + 1];
        out[outBase + 2] = positions[posBase + 2];
        // normal
        if (normals != null && posBase + 2 < normals.length) {
            out[outBase + 3] = normals[posBase];
            out[outBase + 4] = normals[posBase + 1];
            out[outBase + 5] = normals[posBase + 2];
        }
        // uv
        if (uvs != null) {
            int uvBase = srcIndex * 2;
            if (uvBase + 1 < uvs.length) {
                out[outBase + 6] = uvs[uvBase];
                out[outBase + 7] = uvs[uvBase + 1];
            }
        } else {
            // planar projection fallback
            out[outBase + 6] = positions[posBase] * 0.1f;
            out[outBase + 7] = positions[posBase + 1] * 0.1f;
        }
    }

    private void generateFlatNormals(float[] interleaved, int vertexCount) {
        for (int i = 0; i < vertexCount; i += 3) {
            int b0 = i * 8;
            int b1 = (i + 1) * 8;
            int b2 = (i + 2) * 8;
            if (b2 + 7 >= interleaved.length) {
                break;
            }
            float ax = interleaved[b1] - interleaved[b0];
            float ay = interleaved[b1 + 1] - interleaved[b0 + 1];
            float az = interleaved[b1 + 2] - interleaved[b0 + 2];
            float bx = interleaved[b2] - interleaved[b0];
            float by = interleaved[b2 + 1] - interleaved[b0 + 1];
            float bz = interleaved[b2 + 2] - interleaved[b0 + 2];
            float nx = ay * bz - az * by;
            float ny = az * bx - ax * bz;
            float nz = ax * by - ay * bx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 0.00001f) {
                nx /= len;
                ny /= len;
                nz /= len;
            } else {
                nx = 0f;
                ny = 1f;
                nz = 0f;
            }
            for (int v = 0; v < 3; v++) {
                int base = (i + v) * 8;
                interleaved[base + 3] = nx;
                interleaved[base + 4] = ny;
                interleaved[base + 5] = nz;
            }
        }
    }

    // ---- Material parsing ----

    private List<GltfMaterial> parseMaterials(JsonNode root) {
        JsonNode materialsNode = root.path("materials");
        if (!materialsNode.isArray()) {
            return List.of();
        }
        JsonNode texturesNode = root.path("textures");
        List<GltfMaterial> result = new ArrayList<>(materialsNode.size());
        for (int i = 0; i < materialsNode.size(); i++) {
            JsonNode mat = materialsNode.get(i);
            JsonNode pbr = mat.path("pbrMetallicRoughness");

            float[] baseColor = new float[]{1f, 1f, 1f, 1f};
            JsonNode bcf = pbr.path("baseColorFactor");
            if (bcf.isArray() && bcf.size() >= 3) {
                baseColor[0] = (float) bcf.get(0).asDouble(1.0);
                baseColor[1] = (float) bcf.get(1).asDouble(1.0);
                baseColor[2] = (float) bcf.get(2).asDouble(1.0);
                if (bcf.size() >= 4) {
                    baseColor[3] = (float) bcf.get(3).asDouble(1.0);
                }
            }

            float metallic = (float) pbr.path("metallicFactor").asDouble(1.0);
            float roughness = (float) pbr.path("roughnessFactor").asDouble(1.0);

            int baseColorTexIdx = resolveTextureImageIndex(texturesNode, pbr.path("baseColorTexture"));
            int mrTexIdx = resolveTextureImageIndex(texturesNode, pbr.path("metallicRoughnessTexture"));
            int normalTexIdx = resolveTextureImageIndex(texturesNode, mat.path("normalTexture"));
            int occlusionTexIdx = resolveTextureImageIndex(texturesNode, mat.path("occlusionTexture"));

            result.add(new GltfMaterial(baseColor, metallic, roughness,
                    baseColorTexIdx, mrTexIdx, normalTexIdx, occlusionTexIdx));
        }
        return result;
    }

    private int resolveTextureImageIndex(JsonNode texturesNode, JsonNode textureInfo) {
        if (textureInfo == null || textureInfo.isMissingNode()) {
            return -1;
        }
        int texIndex = textureInfo.path("index").asInt(-1);
        if (texIndex < 0 || texturesNode == null || !texturesNode.isArray() || texIndex >= texturesNode.size()) {
            return -1;
        }
        return texturesNode.get(texIndex).path("source").asInt(-1);
    }

    // ---- Embedded image extraction ----

    private List<ByteBuffer> extractImageBuffers(JsonNode root, byte[] binary, Path baseDir) {
        JsonNode imagesNode = root.path("images");
        if (!imagesNode.isArray()) {
            return List.of();
        }
        JsonNode bufferViews = root.path("bufferViews");
        List<ByteBuffer> result = new ArrayList<>(imagesNode.size());
        for (int i = 0; i < imagesNode.size(); i++) {
            JsonNode image = imagesNode.get(i);

            // Try bufferView first (GLB embedded)
            int bvIndex = image.path("bufferView").asInt(-1);
            if (bvIndex >= 0 && bufferViews.isArray() && bvIndex < bufferViews.size()) {
                JsonNode bv = bufferViews.get(bvIndex);
                int offset = bv.path("byteOffset").asInt(0);
                int length = bv.path("byteLength").asInt(0);
                if (offset >= 0 && length > 0 && offset + length <= binary.length) {
                    ByteBuffer buf = ByteBuffer.allocateDirect(length).order(ByteOrder.nativeOrder());
                    buf.put(binary, offset, length);
                    buf.flip();
                    result.add(buf);
                    continue;
                }
            }

            // Fall back to URI (external file)
            String uri = image.path("uri").asText("");
            if (!uri.isBlank() && !uri.startsWith("data:") && baseDir != null) {
                try {
                    Path imagePath = baseDir.resolve(uri).normalize();
                    if (Files.isRegularFile(imagePath)) {
                        byte[] imageBytes = Files.readAllBytes(imagePath);
                        ByteBuffer buf = ByteBuffer.allocateDirect(imageBytes.length).order(ByteOrder.nativeOrder());
                        buf.put(imageBytes);
                        buf.flip();
                        result.add(buf);
                        continue;
                    }
                } catch (IOException ignored) {
                    // fall through
                }
            }

            result.add(null);
        }
        return result;
    }

    // ---- Accessor reading utilities ----

    private float[] readVec3Accessor(JsonNode root, byte[] binary, int accessorIndex) {
        AccessorMeta meta = readAccessorMeta(root, accessorIndex);
        if (meta == null || (meta.components != 2 && meta.components != 3) || binary.length == 0) {
            return null;
        }
        float[] out = new float[meta.count * 3];
        ByteBuffer bb = ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < meta.count; i++) {
            int base = meta.offset + i * meta.stride;
            if (base + meta.componentBytes * meta.components > binary.length) {
                return null;
            }
            out[i * 3] = readComponentAsFloat(bb, base, meta.componentType);
            out[i * 3 + 1] = readComponentAsFloat(bb, base + meta.componentBytes, meta.componentType);
            out[i * 3 + 2] = meta.components >= 3
                    ? readComponentAsFloat(bb, base + 2 * meta.componentBytes, meta.componentType)
                    : 0.0f;
        }
        return out;
    }

    private float[] readVec2Accessor(JsonNode root, byte[] binary, int accessorIndex) {
        AccessorMeta meta = readAccessorMeta(root, accessorIndex);
        if (meta == null || meta.components < 2 || binary.length == 0) {
            return null;
        }
        float[] out = new float[meta.count * 2];
        ByteBuffer bb = ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < meta.count; i++) {
            int base = meta.offset + i * meta.stride;
            if (base + meta.componentBytes * 2 > binary.length) {
                return null;
            }
            out[i * 2] = readComponentAsFloat(bb, base, meta.componentType);
            out[i * 2 + 1] = readComponentAsFloat(bb, base + meta.componentBytes, meta.componentType);
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
            case 5120 -> Math.max(-1.0f, bb.get(offset) / 127.0f);
            case 5122 -> Math.max(-1.0f, bb.getShort(offset) / 32767.0f);
            default -> 0.0f;
        };
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
