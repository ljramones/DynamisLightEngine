package org.dynamisengine.light.impl.vulkan.asset;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.dynamisengine.light.api.scene.MeshDesc;
import org.dynamisengine.light.impl.common.mesh.MeshForgeAssetService;
import org.dynamisengine.light.impl.common.mesh.MeshLoadException;
import org.dynamisengine.light.impl.common.mesh.MeshLoadResult;
import org.dynamisengine.meshforge.gpu.RuntimeGeometryPayload;
import org.dynamisengine.meshforge.pack.layout.VertexLayout;

/**
 * Vulkan mesh asset loader.
 *
 * <p>Primary path: delegates to {@link MeshForgeAssetService} for canonical
 * mesh loading (MGI fast-path or source format import via MeshForge).
 * Falls back to legacy glTF parser for backward compatibility.</p>
 */
public final class VulkanMeshAssetLoader {
    private static final int DEFAULT_MAX_GEOMETRY_CACHE_ENTRIES = 256;
    private final Path assetRoot;
    private final VulkanGltfMeshParser gltfParser;
    private final MeshForgeAssetService meshForge;
    private final int maxGeometryCacheEntries;
    private final LinkedHashMap<String, VulkanGltfMeshParser.MeshGeometry> geometryCache =
            new LinkedHashMap<>(32, 0.75f, true);
    private long geometryCacheHits;
    private long geometryCacheMisses;
    private long geometryCacheEvictions;

    public VulkanMeshAssetLoader(Path assetRoot) {
        this(assetRoot, DEFAULT_MAX_GEOMETRY_CACHE_ENTRIES);
    }

    public VulkanMeshAssetLoader(Path assetRoot, int maxGeometryCacheEntries) {
        this.assetRoot = assetRoot == null ? Path.of(".") : assetRoot;
        this.gltfParser = new VulkanGltfMeshParser(this.assetRoot);
        this.meshForge = new MeshForgeAssetService(this.assetRoot);
        this.maxGeometryCacheEntries = Math.max(1, maxGeometryCacheEntries);
    }

    public VulkanGltfMeshParser.MeshGeometry loadMeshGeometry(MeshDesc mesh, int meshIndex) {
        if (mesh == null) {
            return cloneGeometry(triangleGeometry());
        }

        String meshPath = mesh.meshAssetPath() == null ? "" : mesh.meshAssetPath().toLowerCase(Locale.ROOT);
        String cacheKey = "meshforge:" + meshPath + "#" + meshIndex;
        VulkanGltfMeshParser.MeshGeometry cached = geometryCache.get(cacheKey);
        if (cached != null) {
            geometryCacheHits++;
            return cloneGeometry(cached);
        }

        // Try MeshForge canonical path first (handles MGI, OBJ, STL, PLY, glTF)
        if (mesh.meshAssetPath() != null && !mesh.meshAssetPath().isBlank()) {
            try {
                MeshLoadResult result = meshForge.load(mesh.meshAssetPath());
                VulkanGltfMeshParser.MeshGeometry converted = convertPayload(result.payload());
                geometryCacheMisses++;
                geometryCache.put(cacheKey, converted);
                evictGeometryCacheIfNeeded();
                return cloneGeometry(converted);
            } catch (MeshLoadException e) {
                // Fall through to legacy
            }
        }

        // Legacy fallback: glTF via MeshForge-based parser
        Path resolved = resolve(mesh.meshAssetPath());
        VulkanGltfMeshParser.MeshGeometry resolvedGeometry = null;
        if (resolved != null && (meshPath.endsWith(".glb") || meshPath.endsWith(".gltf"))) {
            var parsed = gltfParser.parse(resolved);
            if (parsed.isPresent()) {
                resolvedGeometry = parsed.get();
            }
        }
        if (resolvedGeometry == null) {
            resolvedGeometry = fallbackByName(meshPath, meshIndex);
        }
        geometryCacheMisses++;
        geometryCache.put(cacheKey, resolvedGeometry);
        evictGeometryCacheIfNeeded();
        return cloneGeometry(resolvedGeometry);
    }

    /**
     * Convert MeshForge RuntimeGeometryPayload to Vulkan MeshGeometry.
     *
     * Extracts position(3) + normal(3) + uv(2) + tangent(3) = 11 floats per vertex.
     */
    private VulkanGltfMeshParser.MeshGeometry convertPayload(RuntimeGeometryPayload payload) {
        VertexLayout layout = payload.layout();
        ByteBuffer vb = payload.vertexBytes().duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int vertexCount = payload.vertexCount();
        int stride = layout.strideBytes();

        int posOff = -1, normOff = -1, uvOff = -1, tanOff = -1;
        for (VertexLayout.Entry entry : layout.entries().values()) {
            String semantic = entry.key().semantic().name();
            if ("POSITION".equals(semantic)) posOff = entry.offsetBytes();
            else if ("NORMAL".equals(semantic)) normOff = entry.offsetBytes();
            else if ("UV".equals(semantic) && entry.key().setIndex() == 0) uvOff = entry.offsetBytes();
            else if ("TANGENT".equals(semantic)) tanOff = entry.offsetBytes();
        }

        // Vulkan expects 11 floats: pos(3) + normal(3) + uv(2) + tangent(3)
        float[] vertices = new float[vertexCount * 11];
        for (int v = 0; v < vertexCount; v++) {
            int base = v * stride;
            int out = v * 11;
            // Position
            if (posOff >= 0) {
                vertices[out] = vb.getFloat(base + posOff);
                vertices[out+1] = vb.getFloat(base + posOff + 4);
                vertices[out+2] = vb.getFloat(base + posOff + 8);
            }
            // Normal
            if (normOff >= 0) {
                vertices[out+3] = vb.getFloat(base + normOff);
                vertices[out+4] = vb.getFloat(base + normOff + 4);
                vertices[out+5] = vb.getFloat(base + normOff + 8);
            } else {
                vertices[out+4] = 1f; // default up normal
            }
            // UV
            if (uvOff >= 0) {
                vertices[out+6] = vb.getFloat(base + uvOff);
                vertices[out+7] = vb.getFloat(base + uvOff + 4);
            }
            // Tangent
            if (tanOff >= 0) {
                vertices[out+8] = vb.getFloat(base + tanOff);
                vertices[out+9] = vb.getFloat(base + tanOff + 4);
                vertices[out+10] = vb.getFloat(base + tanOff + 8);
            } else {
                vertices[out+8] = 1f; // default tangent
            }
        }

        // Extract indices
        int[] indices;
        if (payload.indexBytes() != null && payload.indexCount() > 0) {
            ByteBuffer ib = payload.indexBytes().duplicate().order(ByteOrder.LITTLE_ENDIAN);
            indices = new int[payload.indexCount()];
            if (payload.indexType() == org.dynamisengine.meshforge.pack.buffer.PackedMesh.IndexType.UINT16) {
                for (int i = 0; i < indices.length; i++) indices[i] = Short.toUnsignedInt(ib.getShort(i * 2));
            } else {
                for (int i = 0; i < indices.length; i++) indices[i] = ib.getInt(i * 4);
            }
        } else {
            indices = new int[vertexCount];
            for (int i = 0; i < vertexCount; i++) indices[i] = i;
        }

        return new VulkanGltfMeshParser.MeshGeometry(vertices, indices, false, 0, null, 0);
    }

    public CacheProfile cacheProfile() {
        return new CacheProfile(geometryCacheHits, geometryCacheMisses, geometryCacheEvictions, geometryCache.size(), maxGeometryCacheEntries);
    }

    private Path resolve(String meshAssetPath) {
        if (meshAssetPath == null || meshAssetPath.isBlank()) return null;
        Path path = Path.of(meshAssetPath);
        if (path.isAbsolute()) return path.normalize();
        return assetRoot.resolve(path).normalize();
    }

    private VulkanGltfMeshParser.MeshGeometry fallbackByName(String sourceName, int meshIndex) {
        if (sourceName.contains("quad") || sourceName.contains("box") || sourceName.contains("plane")) {
            return quadGeometry();
        }
        return triangleGeometry();
    }

    private VulkanGltfMeshParser.MeshGeometry cloneGeometry(VulkanGltfMeshParser.MeshGeometry geometry) {
        return new VulkanGltfMeshParser.MeshGeometry(
                geometry.vertices().clone(),
                geometry.indices().clone(),
                geometry.skinned(),
                geometry.jointCount(),
                geometry.morphTargetDeltas() == null ? null : geometry.morphTargetDeltas().clone(),
                geometry.morphTargetCount()
        );
    }

    private void evictGeometryCacheIfNeeded() {
        while (geometryCache.size() > maxGeometryCacheEntries) {
            Iterator<Map.Entry<String, VulkanGltfMeshParser.MeshGeometry>> it = geometryCache.entrySet().iterator();
            if (!it.hasNext()) return;
            it.next(); it.remove();
            geometryCacheEvictions++;
        }
    }

    public record CacheProfile(long hits, long misses, long evictions, int entries, int maxEntries) {}

    private VulkanGltfMeshParser.MeshGeometry triangleGeometry() {
        return new VulkanGltfMeshParser.MeshGeometry(
                new float[]{0.0f,-0.6f,0.0f, 0f,0f,1f, 0.5f,0.0f, 1f,0f,0f,
                        0.6f,0.6f,0.0f, 0f,0f,1f, 1.0f,1.0f, 1f,0f,0f,
                        -0.6f,0.6f,0.0f, 0f,0f,1f, 0.0f,1.0f, 1f,0f,0f},
                new int[]{0, 1, 2}, false, 0, null, 0);
    }

    private VulkanGltfMeshParser.MeshGeometry quadGeometry() {
        return new VulkanGltfMeshParser.MeshGeometry(
                new float[]{-0.6f,-0.6f,0.0f, 0f,0f,1f, 0f,0f, 1f,0f,0f,
                        0.6f,-0.6f,0.0f, 0f,0f,1f, 1f,0f, 1f,0f,0f,
                        0.6f,0.6f,0.0f, 0f,0f,1f, 1f,1f, 1f,0f,0f,
                        -0.6f,0.6f,0.0f, 0f,0f,1f, 0f,1f, 1f,0f,0f},
                new int[]{0, 1, 2, 2, 3, 0}, false, 0, null, 0);
    }
}
