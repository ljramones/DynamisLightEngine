package org.dynamisengine.light.impl.opengl;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dynamisengine.light.api.scene.MaterialDesc;
import org.dynamisengine.light.api.scene.MeshDesc;
import org.dynamisengine.light.api.scene.ReactivePreset;
import org.dynamisengine.light.api.scene.SceneDescriptor;
import org.dynamisengine.light.api.scene.TransformDesc;
import org.dynamisengine.light.api.scene.Vec3;

/**
 * Converts scene mesh descriptors into {@link OpenGlContext.SceneMesh} instances,
 * including glTF scene expansion and material property resolution.
 */
final class OpenGlSceneMeshMapper {

    private OpenGlSceneMeshMapper() {
    }

    static List<OpenGlContext.SceneMesh> mapSceneMeshes(
            SceneDescriptor scene,
            OpenGlMeshAssetLoader meshLoader,
            OpenGlContext context,
            Path assetRoot,
            boolean mockContext) {
        if (scene.meshes() == null || scene.meshes().isEmpty()) {
            return List.of(new OpenGlContext.SceneMesh(
                    "default-triangle",
                    OpenGlContext.defaultTriangleGeometry(),
                    identityMatrix(),
                    new float[]{1f, 1f, 1f},
                    0.0f,
                    0.6f,
                    0f,
                    false,
                    false,
                    1.0f,
                    1.0f,
                    1.0f,
                    0f,
                    null,
                    null,
                    null,
                    null
            ));
        }

        Map<String, TransformDesc> transforms = new HashMap<>();
        for (TransformDesc transform : scene.transforms()) {
            transforms.put(transform.id(), transform);
        }

        Map<String, MaterialDesc> materials = new HashMap<>();
        for (MaterialDesc material : scene.materials()) {
            materials.put(material.id(), material);
        }

        List<OpenGlContext.SceneMesh> sceneMeshes = new ArrayList<>(scene.meshes().size());
        for (int i = 0; i < scene.meshes().size(); i++) {
            MeshDesc mesh = scene.meshes().get(i);

            if (mesh.id() != null && mesh.id().startsWith("gltf-scene:")) {
                List<OpenGlContext.SceneMesh> expanded = expandGltfScene(mesh, transforms, materials, meshLoader, context, mockContext);
                sceneMeshes.addAll(expanded);
                continue;
            }

            OpenGlContext.MeshGeometry geometry = meshLoader.loadMeshGeometry(mesh, i);
            TransformDesc transform = transforms.get(mesh.transformId());
            MaterialDesc material = materials.get(mesh.materialId());

            float[] model = modelMatrixOf(transform);
            float[] albedo = albedoOf(material);
            Path albedoTexturePath = resolveTexturePath(material == null ? null : material.albedoTexturePath(), assetRoot);
            Path normalTexturePath = resolveTexturePath(material == null ? null : material.normalTexturePath(), assetRoot);
            Path metallicRoughnessTexturePath =
                    resolveTexturePath(material == null ? null : material.metallicRoughnessTexturePath(), assetRoot);
            Path occlusionTexturePath = resolveTexturePath(material == null ? null : material.occlusionTexturePath(), assetRoot);
            float metallic = material == null ? 0.0f : clamp01(material.metallic());
            float roughness = material == null ? 0.6f : clamp01(material.roughness());
            float reactiveStrength = material == null ? 0f : clamp01(material.reactiveStrength());
            boolean alphaTested = material != null && material.alphaTested();
            boolean foliage = material != null && material.foliage();
            float reactiveBoost = material == null ? 1.0f : Math.max(0f, Math.min(2.0f, material.reactiveBoost()));
            float taaHistoryClamp = material == null ? 1.0f : clamp01(material.taaHistoryClamp());
            float emissiveReactiveBoost = material == null ? 1.0f : Math.max(0f, Math.min(3.0f, material.emissiveReactiveBoost()));
            float reactivePreset = material == null ? 0f : toReactivePresetValue(material.reactivePreset());
            sceneMeshes.add(new OpenGlContext.SceneMesh(
                    mesh.id() == null || mesh.id().isBlank() ? ("mesh-index-" + i) : mesh.id(),
                    geometry,
                    model,
                    albedo,
                    metallic,
                    roughness,
                    reactiveStrength,
                    alphaTested,
                    foliage,
                    reactiveBoost,
                    taaHistoryClamp,
                    emissiveReactiveBoost,
                    reactivePreset,
                    albedoTexturePath,
                    normalTexturePath,
                    metallicRoughnessTexturePath,
                    occlusionTexturePath
            ));
        }
        return sceneMeshes;
    }

    static List<OpenGlContext.SceneMesh> expandGltfScene(
            MeshDesc mesh, Map<String, TransformDesc> transforms, Map<String, MaterialDesc> materials,
            OpenGlMeshAssetLoader meshLoader, OpenGlContext context, boolean mockContext) {
        Path glbPath = mesh.meshAssetPath() == null ? null : Path.of(mesh.meshAssetPath());
        if (glbPath == null) {
            return List.of();
        }
        OpenGlMeshAssetLoader.LoadedGltfScene loaded = meshLoader.loadGltfScene(glbPath);
        if (loaded == null) {
            return List.of();
        }
        TransformDesc sceneTransform = transforms.get(mesh.transformId());
        float[] sceneModel = modelMatrixOf(sceneTransform);

        // Identify which images are used as color (sRGB) textures
        java.util.Set<Integer> sRgbImages = new java.util.HashSet<>();
        for (var mat : loaded.materials()) {
            if (mat.baseColorTextureIndex() >= 0) {
                sRgbImages.add(mat.baseColorTextureIndex());
            }
        }

        // Upload embedded textures
        int[] textureIds = new int[loaded.imageBuffers().size()];
        if (!mockContext) {
            for (int ti = 0; ti < loaded.imageBuffers().size(); ti++) {
                java.nio.ByteBuffer imgBuf = loaded.imageBuffers().get(ti);
                if (imgBuf != null) {
                    textureIds[ti] = context.loadTextureFromMemory(imgBuf, sRgbImages.contains(ti));
                }
            }
        }

        List<OpenGlContext.SceneMesh> result = new ArrayList<>(loaded.primitives().size());
        for (var prim : loaded.primitives()) {
            float[] albedoColor = new float[]{1f, 1f, 1f};
            float metallic = 0.0f;
            float roughness = 1.0f;
            int albedoTexId = 0;
            int normalTexId = 0;
            int mrTexId = 0;
            int occlusionTexId = 0;

            boolean alphaTested = false;
            float alphaCutoff = 0f;

            if (prim.materialIndex() >= 0 && prim.materialIndex() < loaded.materials().size()) {
                var gltfMat = loaded.materials().get(prim.materialIndex());
                albedoColor = new float[]{gltfMat.baseColorFactor()[0], gltfMat.baseColorFactor()[1], gltfMat.baseColorFactor()[2]};
                metallic = gltfMat.metallicFactor();
                roughness = gltfMat.roughnessFactor();
                alphaTested = "MASK".equals(gltfMat.alphaMode());
                alphaCutoff = alphaTested ? gltfMat.alphaCutoff() : 0f;
                if (gltfMat.baseColorTextureIndex() >= 0 && gltfMat.baseColorTextureIndex() < textureIds.length) {
                    albedoTexId = textureIds[gltfMat.baseColorTextureIndex()];
                }
                if (gltfMat.normalTextureIndex() >= 0 && gltfMat.normalTextureIndex() < textureIds.length) {
                    normalTexId = textureIds[gltfMat.normalTextureIndex()];
                }
                if (gltfMat.metallicRoughnessTextureIndex() >= 0 && gltfMat.metallicRoughnessTextureIndex() < textureIds.length) {
                    mrTexId = textureIds[gltfMat.metallicRoughnessTextureIndex()];
                }
                if (gltfMat.occlusionTextureIndex() >= 0 && gltfMat.occlusionTextureIndex() < textureIds.length) {
                    occlusionTexId = textureIds[gltfMat.occlusionTextureIndex()];
                }
            }

            String primId = prim.meshName() + "-p" + prim.primitiveIndex();
            result.add(new OpenGlContext.SceneMesh(
                    primId,
                    prim.geometry(),
                    sceneModel.clone(),
                    albedoColor,
                    metallic,
                    roughness,
                    0f,     // reactiveStrength
                    alphaTested,
                    alphaCutoff,
                    false,  // foliage
                    1.0f,   // reactiveBoost
                    1.0f,   // taaHistoryClamp
                    1.0f,   // emissiveReactiveBoost
                    0f,     // reactivePreset
                    null,   // albedoTexturePath
                    null,   // normalTexturePath
                    null,   // metallicRoughnessTexturePath
                    null,   // occlusionTexturePath
                    albedoTexId,
                    normalTexId,
                    mrTexId,
                    occlusionTexId
            ));
        }
        return result;
    }

    // ── helpers ──

    static float[] modelMatrixOf(TransformDesc transform) {
        if (transform == null) {
            return identityMatrix();
        }
        Vec3 pos = transform.position() == null ? new Vec3(0f, 0f, 0f) : transform.position();
        Vec3 rot = transform.rotationEulerDeg() == null ? new Vec3(0f, 0f, 0f) : transform.rotationEulerDeg();
        Vec3 scl = transform.scale() == null ? new Vec3(1f, 1f, 1f) : transform.scale();

        float[] translation = translationMatrix(pos.x(), pos.y(), pos.z());
        float[] rotation = mul(mul(rotationZ(radians(rot.z())), rotationY(radians(rot.y()))), rotationX(radians(rot.x())));
        float[] scale = scaleMatrix(scl.x(), scl.y(), scl.z());
        return mul(translation, mul(rotation, scale));
    }

    static float[] albedoOf(MaterialDesc material) {
        if (material == null || material.albedo() == null) {
            return new float[]{1f, 1f, 1f};
        }
        return new float[]{material.albedo().x(), material.albedo().y(), material.albedo().z()};
    }

    static Path resolveTexturePath(String texturePath, Path assetRoot) {
        if (texturePath == null || texturePath.isBlank()) {
            return null;
        }
        Path path = Path.of(texturePath);
        return path.isAbsolute() ? path : assetRoot.resolve(path).normalize();
    }

    private static float toReactivePresetValue(ReactivePreset preset) {
        if (preset == null) {
            return 0f;
        }
        return switch (preset) {
            case AUTO -> 0f;
            case STABLE -> 1f;
            case BALANCED -> 2f;
            case AGGRESSIVE -> 3f;
        };
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    // ── matrix math (shared with OpenGlEngineRuntime for camera) ──

    static float[] identityMatrix() {
        return new float[]{
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
        };
    }

    static float[] translationMatrix(float x, float y, float z) {
        return new float[]{
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                x, y, z, 1f
        };
    }

    static float[] scaleMatrix(float x, float y, float z) {
        return new float[]{
                x, 0f, 0f, 0f,
                0f, y, 0f, 0f,
                0f, 0f, z, 0f,
                0f, 0f, 0f, 1f
        };
    }

    static float[] rotationX(float radians) {
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        return new float[]{
                1f, 0f, 0f, 0f,
                0f, c, s, 0f,
                0f, -s, c, 0f,
                0f, 0f, 0f, 1f
        };
    }

    static float[] rotationY(float radians) {
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        return new float[]{
                c, 0f, -s, 0f,
                0f, 1f, 0f, 0f,
                s, 0f, c, 0f,
                0f, 0f, 0f, 1f
        };
    }

    static float[] rotationZ(float radians) {
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        return new float[]{
                c, s, 0f, 0f,
                -s, c, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
        };
    }

    static float[] lookAt(float eyeX, float eyeY, float eyeZ, float targetX, float targetY, float targetZ,
                           float upX, float upY, float upZ) {
        float fx = targetX - eyeX;
        float fy = targetY - eyeY;
        float fz = targetZ - eyeZ;
        float fLen = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (fLen < 0.00001f) {
            return identityMatrix();
        }
        fx /= fLen;
        fy /= fLen;
        fz /= fLen;

        float sx = fy * upZ - fz * upY;
        float sy = fz * upX - fx * upZ;
        float sz = fx * upY - fy * upX;
        float sLen = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sLen < 0.00001f) {
            return identityMatrix();
        }
        sx /= sLen;
        sy /= sLen;
        sz /= sLen;

        float ux = sy * fz - sz * fy;
        float uy = sz * fx - sx * fz;
        float uz = sx * fy - sy * fx;

        return new float[]{
                sx, ux, -fx, 0f,
                sy, uy, -fy, 0f,
                sz, uz, -fz, 0f,
                -(sx * eyeX + sy * eyeY + sz * eyeZ),
                -(ux * eyeX + uy * eyeY + uz * eyeZ),
                (fx * eyeX + fy * eyeY + fz * eyeZ),
                1f
        };
    }

    static float[] perspective(float fovRad, float aspect, float near, float far) {
        float f = 1.0f / (float) Math.tan(fovRad * 0.5f);
        float nf = 1.0f / (near - far);
        return new float[]{
                f / aspect, 0f, 0f, 0f,
                0f, f, 0f, 0f,
                0f, 0f, (far + near) * nf, -1f,
                0f, 0f, (2f * far * near) * nf, 0f
        };
    }

    static float[] mul(float[] a, float[] b) {
        float[] out = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                out[c * 4 + r] = a[r] * b[c * 4]
                        + a[4 + r] * b[c * 4 + 1]
                        + a[8 + r] * b[c * 4 + 2]
                        + a[12 + r] * b[c * 4 + 3];
            }
        }
        return out;
    }

    static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }
}
