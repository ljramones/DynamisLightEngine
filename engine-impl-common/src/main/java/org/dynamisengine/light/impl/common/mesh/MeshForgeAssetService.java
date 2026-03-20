package org.dynamisengine.light.impl.common.mesh;

import org.dynamisengine.meshforge.api.Ops;
import org.dynamisengine.meshforge.api.Packers;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.gpu.GpuGeometryUploadPlan;
import org.dynamisengine.meshforge.gpu.MeshForgeGpuBridge;
import org.dynamisengine.meshforge.gpu.RuntimeGeometryPayload;
import org.dynamisengine.meshforge.loader.MeshLoaders;
import org.dynamisengine.meshforge.mgi.MgiMeshDataCodec;
import org.dynamisengine.meshforge.mgi.MgiStaticMesh;
import org.dynamisengine.meshforge.mgi.MgiStaticMeshCodec;
import org.dynamisengine.meshforge.pack.packer.MeshPacker;
import org.dynamisengine.meshforge.ops.pipeline.MeshPipeline;
import org.dynamisengine.meshforge.pack.buffer.PackedMesh;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Shared mesh asset service for LightEngine backends.
 *
 * <p>Provides a single canonical path for mesh loading:
 * <ol>
 *   <li>If path is {@code .mgi} - fast binary deserialize via MeshForge MGI codec</li>
 *   <li>Otherwise - load via MeshForge loaders (OBJ/glTF/STL/PLY), process, pack</li>
 *   <li>Output: {@link RuntimeGeometryPayload} + {@link GpuGeometryUploadPlan}</li>
 * </ol>
 *
 * <p>Backends should NOT implement their own source-format parsers.
 * This service owns mesh truth; backends own only GPU upload.</p>
 *
 * <p>Doctrine: interchange formats (glTF, OBJ) are authoring/import formats.
 * MGI is the preferred runtime geometry format for LightEngine.</p>
 */
public final class MeshForgeAssetService {

    private final Path assetRoot;
    private final int maxCacheEntries;
    private final LinkedHashMap<String, MeshLoadResult> cache;
    private final MgiStaticMeshCodec mgiCodec = new MgiStaticMeshCodec();

    public MeshForgeAssetService(Path assetRoot) {
        this(assetRoot, 256);
    }

    public MeshForgeAssetService(Path assetRoot, int maxCacheEntries) {
        this.assetRoot = assetRoot != null ? assetRoot : Path.of(".");
        this.maxCacheEntries = Math.max(1, maxCacheEntries);
        this.cache = new LinkedHashMap<>(32, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, MeshLoadResult> eldest) {
                return size() > MeshForgeAssetService.this.maxCacheEntries;
            }
        };
    }

    /**
     * Load a mesh asset. Returns cached result if available.
     *
     * @param meshAssetPath relative path from asset root
     * @return loaded mesh ready for GPU upload
     * @throws MeshLoadException if loading fails
     */
    public MeshLoadResult load(String meshAssetPath) throws MeshLoadException {
        if (meshAssetPath == null || meshAssetPath.isBlank()) {
            throw new MeshLoadException("Null or empty mesh asset path");
        }

        MeshLoadResult cached = cache.get(meshAssetPath);
        if (cached != null) return cached;

        Path resolved = assetRoot.resolve(meshAssetPath);
        if (!Files.exists(resolved)) {
            throw new MeshLoadException("Mesh asset not found: " + resolved);
        }

        try {
            MeshLoadResult result;
            String lower = meshAssetPath.toLowerCase(Locale.ROOT);

            if (lower.endsWith(".mgi")) {
                result = loadMgi(resolved, meshAssetPath);
            } else {
                result = loadAndProcess(resolved, meshAssetPath);
            }

            cache.put(meshAssetPath, result);
            return result;
        } catch (MeshLoadException e) {
            throw e;
        } catch (Exception e) {
            throw new MeshLoadException("Failed to load mesh: " + meshAssetPath, e);
        }
    }

    /** Fast path: load pre-packed MGI binary. */
    private MeshLoadResult loadMgi(Path path, String assetPath) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        MgiStaticMesh mgi = mgiCodec.read(bytes);
        MeshData meshData = MgiMeshDataCodec.toMeshData(mgi);

        PackedMesh packed = MeshPacker.pack(meshData, Packers.realtimeFast());
        RuntimeGeometryPayload payload = MeshForgeGpuBridge.payloadFromPackedMesh(packed);
        GpuGeometryUploadPlan plan = MeshForgeGpuBridge.buildUploadPlan(payload);

        return new MeshLoadResult(payload, plan, assetPath, true);
    }

    /** Import path: load source format, process, pack. */
    private MeshLoadResult loadAndProcess(Path path, String assetPath) throws Exception {
        MeshData meshData = MeshLoaders.defaults().load(path);

        meshData = MeshPipeline.run(meshData,
                Ops.validate(),
                Ops.removeDegenerates(),
                Ops.weld(1e-6f),
                Ops.normals(60f),
                Ops.tangents(),
                Ops.optimizeVertexCache(),
                Ops.bounds()
        );

        PackedMesh packed = MeshPacker.pack(meshData, Packers.realtimeFast());
        RuntimeGeometryPayload payload = MeshForgeGpuBridge.payloadFromPackedMesh(packed);
        GpuGeometryUploadPlan plan = MeshForgeGpuBridge.buildUploadPlan(payload);

        return new MeshLoadResult(payload, plan, assetPath, false);
    }

    /** Clear the geometry cache. */
    public void clearCache() { cache.clear(); }

    /** Current cache size. */
    public int cacheSize() { return cache.size(); }
}
