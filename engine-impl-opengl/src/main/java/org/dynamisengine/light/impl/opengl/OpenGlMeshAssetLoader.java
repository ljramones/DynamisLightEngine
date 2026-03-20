package org.dynamisengine.light.impl.opengl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.dynamisengine.light.api.scene.MeshDesc;
import org.dynamisengine.light.impl.common.mesh.MeshForgeAssetService;
import org.dynamisengine.light.impl.common.mesh.MeshLoadException;
import org.dynamisengine.light.impl.common.mesh.MeshLoadResult;
import org.dynamisengine.meshforge.gpu.RuntimeGeometryPayload;
import org.dynamisengine.meshforge.pack.buffer.PackedMesh;
import org.dynamisengine.meshforge.pack.layout.VertexLayout;

/**
 * OpenGL mesh asset loader.
 *
 * <p>Primary path: delegates to {@link MeshForgeAssetService} for canonical
 * mesh loading (MGI fast-path or source format import via MeshForge).
 * Converts {@link RuntimeGeometryPayload} to {@link OpenGlContext.MeshGeometry}.</p>
 *
 * <p>glTF scene expansion (multi-primitive with embedded textures/materials)
 * still uses the legacy parser for full scene loads, as that path requires
 * material and texture extraction beyond geometry.</p>
 */
final class OpenGlMeshAssetLoader {
    private final OpenGlGltfMeshParser gltfParser;
    private final MeshForgeAssetService meshForge;

    OpenGlMeshAssetLoader(Path assetRoot) {
        Path root = assetRoot == null ? Path.of(".") : assetRoot;
        this.gltfParser = new OpenGlGltfMeshParser(root);
        this.meshForge = new MeshForgeAssetService(root);
    }

    List<OpenGlContext.MeshGeometry> loadSceneMeshes(List<MeshDesc> meshes) {
        if (meshes == null || meshes.isEmpty()) {
            return List.of(OpenGlContext.defaultTriangleGeometry());
        }
        List<OpenGlContext.MeshGeometry> geometries = new ArrayList<>(meshes.size());
        for (int i = 0; i < meshes.size(); i++) {
            geometries.add(loadMeshGeometry(meshes.get(i), i));
        }
        return geometries;
    }

    OpenGlContext.MeshGeometry loadMeshGeometry(MeshDesc mesh, int index) {
        if (mesh == null || mesh.meshAssetPath() == null || mesh.meshAssetPath().isBlank()) {
            return OpenGlContext.defaultTriangleGeometry();
        }

        // Try MeshForge canonical path first (handles MGI, OBJ, STL, PLY, glTF)
        try {
            MeshLoadResult result = meshForge.load(mesh.meshAssetPath());
            return convertPayload(result.payload());
        } catch (MeshLoadException e) {
            // Fall through to legacy paths
        }

        // Legacy fallback: glTF via hand-rolled parser (for backward compatibility)
        String meshPath = mesh.meshAssetPath().toLowerCase(Locale.ROOT);
        Path resolved = Path.of(mesh.meshAssetPath());
        if (meshPath.endsWith(".glb") || meshPath.endsWith(".gltf")) {
            var parsed = gltfParser.parse(resolved);
            if (parsed.isPresent()) {
                return parsed.get();
            }
        }

        return mapByName(meshPath, index);
    }

    /**
     * Convert MeshForge RuntimeGeometryPayload to OpenGL MeshGeometry.
     *
     * Extracts position + normal + UV from the packed vertex buffer
     * into the POS_NORMAL_UV_8F interleaved format expected by OpenGL.
     */
    private OpenGlContext.MeshGeometry convertPayload(RuntimeGeometryPayload payload) {
        VertexLayout layout = payload.layout();
        ByteBuffer vb = payload.vertexBytes().duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int vertexCount = payload.vertexCount();
        int stride = layout.strideBytes();

        // Find attribute offsets
        int posOffset = -1, normalOffset = -1, uvOffset = -1;
        for (VertexLayout.Entry entry : layout.entries().values()) {
            String semantic = entry.key().semantic().name();
            if ("POSITION".equals(semantic)) posOffset = entry.offsetBytes();
            else if ("NORMAL".equals(semantic)) normalOffset = entry.offsetBytes();
            else if ("UV".equals(semantic) && entry.key().setIndex() == 0) uvOffset = entry.offsetBytes();
        }

        if (posOffset < 0) {
            throw new IllegalStateException("MeshForge payload missing POSITION attribute");
        }

        // Build POS_NORMAL_UV_8F array: px,py,pz, nx,ny,nz, u,v
        float[] vertices = new float[vertexCount * 8];
        for (int v = 0; v < vertexCount; v++) {
            int base = v * stride;
            int out = v * 8;
            // Position (always present)
            vertices[out]     = vb.getFloat(base + posOffset);
            vertices[out + 1] = vb.getFloat(base + posOffset + 4);
            vertices[out + 2] = vb.getFloat(base + posOffset + 8);
            // Normal (may be missing)
            if (normalOffset >= 0) {
                vertices[out + 3] = vb.getFloat(base + normalOffset);
                vertices[out + 4] = vb.getFloat(base + normalOffset + 4);
                vertices[out + 5] = vb.getFloat(base + normalOffset + 8);
            } else {
                vertices[out + 3] = 0; vertices[out + 4] = 1; vertices[out + 5] = 0;
            }
            // UV (may be missing)
            if (uvOffset >= 0) {
                vertices[out + 6] = vb.getFloat(base + uvOffset);
                vertices[out + 7] = vb.getFloat(base + uvOffset + 4);
            }
        }

        return new OpenGlContext.MeshGeometry(vertices, OpenGlContext.VertexFormat.POS_NORMAL_UV_8F);
    }

    // --- glTF scene expansion (legacy, for multi-primitive + texture/material extraction) ---

    record LoadedGltfScene(
            List<LoadedPrimitive> primitives,
            List<OpenGlGltfMeshParser.GltfMaterial> materials,
            List<ByteBuffer> imageBuffers
    ) {}

    record LoadedPrimitive(
            OpenGlContext.MeshGeometry geometry,
            int materialIndex,
            String meshName,
            int meshIndex,
            int primitiveIndex
    ) {}

    LoadedGltfScene loadGltfScene(Path glbPath) {
        if (glbPath == null) return null;
        var sceneOpt = gltfParser.parseScene(glbPath);
        if (sceneOpt.isEmpty()) return null;
        var scene = sceneOpt.get();
        List<LoadedPrimitive> primitives = new ArrayList<>(scene.primitives().size());
        for (var p : scene.primitives()) {
            primitives.add(new LoadedPrimitive(
                    p.geometry(), p.materialIndex(), p.meshName(), p.meshIndex(), p.primitiveIndex()));
        }
        return new LoadedGltfScene(primitives, scene.materials(), scene.imageBuffers());
    }

    private OpenGlContext.MeshGeometry mapByName(String sourceName, int index) {
        float tint = (index % 5) * 0.08f;
        if (sourceName.contains("quad") || sourceName.contains("box")) {
            return OpenGlContext.quadGeometry(0.25f + tint, 0.55f, 0.9f - tint);
        }
        return OpenGlContext.triangleGeometry(0.95f - tint, 0.35f + tint, 0.3f + tint);
    }
}
