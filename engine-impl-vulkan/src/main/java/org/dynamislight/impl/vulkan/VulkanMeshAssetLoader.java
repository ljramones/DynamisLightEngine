package org.dynamislight.impl.vulkan;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.dynamislight.api.scene.MeshDesc;

final class VulkanMeshAssetLoader {
    private static final int DEFAULT_MAX_GEOMETRY_CACHE_ENTRIES = 256;
    private final Path assetRoot;
    private final VulkanGltfMeshParser gltfParser;
    private final int maxGeometryCacheEntries;
    private final LinkedHashMap<String, VulkanGltfMeshParser.MeshGeometry> geometryCache =
            new LinkedHashMap<>(32, 0.75f, true);
    private long geometryCacheHits;
    private long geometryCacheMisses;
    private long geometryCacheEvictions;

    VulkanMeshAssetLoader(Path assetRoot) {
        this(assetRoot, DEFAULT_MAX_GEOMETRY_CACHE_ENTRIES);
    }

    VulkanMeshAssetLoader(Path assetRoot, int maxGeometryCacheEntries) {
        this.assetRoot = assetRoot == null ? Path.of(".") : assetRoot;
        this.gltfParser = new VulkanGltfMeshParser(this.assetRoot);
        this.maxGeometryCacheEntries = Math.max(1, maxGeometryCacheEntries);
    }

    VulkanGltfMeshParser.MeshGeometry loadMeshGeometry(MeshDesc mesh, int meshIndex) {
        if (mesh == null) {
            return cloneGeometry(triangleGeometry());
        }

        String meshPath = mesh.meshAssetPath() == null ? "" : mesh.meshAssetPath().toLowerCase(Locale.ROOT);
        Path resolved = resolve(mesh.meshAssetPath());
        String cacheKey = cacheKeyFor(meshPath, resolved, meshIndex);
        VulkanGltfMeshParser.MeshGeometry cached = geometryCache.get(cacheKey);
        if (cached != null) {
            geometryCacheHits++;
            return cloneGeometry(cached);
        }

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

    CacheProfile cacheProfile() {
        return new CacheProfile(geometryCacheHits, geometryCacheMisses, geometryCacheEvictions, geometryCache.size(), maxGeometryCacheEntries);
    }

    private Path resolve(String meshAssetPath) {
        if (meshAssetPath == null || meshAssetPath.isBlank()) {
            return null;
        }
        Path path = Path.of(meshAssetPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return assetRoot.resolve(path).normalize();
    }

    private VulkanGltfMeshParser.MeshGeometry fallbackByName(String sourceName, int meshIndex) {
        if (sourceName.contains("quad") || sourceName.contains("box") || sourceName.contains("plane")) {
            return quadGeometry();
        }
        return triangleGeometry();
    }

    private String cacheKeyFor(String meshPath, Path resolved, int meshIndex) {
        if (resolved != null && (meshPath.endsWith(".glb") || meshPath.endsWith(".gltf"))) {
            return "asset:" + resolved.toAbsolutePath().normalize();
        }
        return "fallback:" + meshPath + "#" + meshIndex;
    }

    private VulkanGltfMeshParser.MeshGeometry cloneGeometry(VulkanGltfMeshParser.MeshGeometry geometry) {
        return new VulkanGltfMeshParser.MeshGeometry(
                geometry.vertices().clone(),
                geometry.indices().clone()
        );
    }

    private void evictGeometryCacheIfNeeded() {
        while (geometryCache.size() > maxGeometryCacheEntries) {
            Iterator<Map.Entry<String, VulkanGltfMeshParser.MeshGeometry>> it = geometryCache.entrySet().iterator();
            if (!it.hasNext()) {
                return;
            }
            it.next();
            it.remove();
            geometryCacheEvictions++;
        }
    }

    record CacheProfile(long hits, long misses, long evictions, int entries, int maxEntries) {
    }

    private VulkanGltfMeshParser.MeshGeometry triangleGeometry() {
        return new VulkanGltfMeshParser.MeshGeometry(
                new float[]{
                        // pos                 // normal      // uv      // tangent
                        0.0f, -0.6f, 0.0f,     0f, 0f, 1f,    0.5f, 0.0f, 1f, 0f, 0f,
                        0.6f, 0.6f, 0.0f,      0f, 0f, 1f,    1.0f, 1.0f, 1f, 0f, 0f,
                        -0.6f, 0.6f, 0.0f,     0f, 0f, 1f,    0.0f, 1.0f, 1f, 0f, 0f
                },
                new int[]{0, 1, 2}
        );
    }

    private VulkanGltfMeshParser.MeshGeometry quadGeometry() {
        return new VulkanGltfMeshParser.MeshGeometry(
                new float[]{
                        -0.6f, -0.6f, 0.0f,    0f, 0f, 1f,    0f, 0f,    1f, 0f, 0f,
                        0.6f, -0.6f, 0.0f,     0f, 0f, 1f,    1f, 0f,    1f, 0f, 0f,
                        0.6f, 0.6f, 0.0f,      0f, 0f, 1f,    1f, 1f,    1f, 0f, 0f,
                        -0.6f, 0.6f, 0.0f,     0f, 0f, 1f,    0f, 1f,    1f, 0f, 0f
                },
                new int[]{0, 1, 2, 2, 3, 0}
        );
    }
}
