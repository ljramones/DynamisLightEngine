package org.dynamisengine.light.sample;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Deterministic generator for mesh fixtures used by parser validation.
 */
public final class HostSampleMeshFixtureGenerator {
    private static final int GLB_MAGIC = 0x46546C67;
    private static final int GLB_VERSION = 2;
    private static final int GLB_JSON_CHUNK = 0x4E4F534A;
    private static final int GLB_BIN_CHUNK = 0x004E4942;

    private HostSampleMeshFixtureGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path repoRoot = resolveRepoRoot();
        Path outDir = repoRoot.resolve("engine-host-sample/src/test/resources/assets/meshes");
        Files.createDirectories(outDir);

        write(outDir.resolve("fixture-box.glb"), buildBoxFixtureGlb());
        write(outDir.resolve("fixture-triangle.glb"), buildTriangleFixtureGlb());
        write(outDir.resolve("fixture-skinned-cylinder.gltf"), buildSkinnedFixture());
        write(outDir.resolve("fixture-morph-quad.gltf"), buildMorphFixture());
        write(outDir.resolve("fixture-multi-primitive.gltf"), buildMultiPrimitiveFixture());
        write(outDir.resolve("fixture-tangent-quad.gltf"), buildTangentFixture());

        System.out.println("Generated fixtures in " + outDir.toAbsolutePath().normalize());
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static void write(Path path, byte[] content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, content);
    }

    private static Path resolveRepoRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(cwd.resolve("pom.xml")) && Files.isDirectory(cwd.resolve("engine-host-sample"))) {
            return cwd;
        }
        Path parent = cwd.getParent();
        if (parent != null && Files.isRegularFile(parent.resolve("pom.xml")) && Files.isDirectory(parent.resolve("engine-host-sample"))) {
            return parent;
        }
        return cwd;
    }

    private static String buildSkinnedFixture() {
        float[] positions = new float[] {
                -0.5f, 0.0f, -0.5f,
                 0.5f, 0.0f, -0.5f,
                 0.5f, 0.0f,  0.5f,
                -0.5f, 0.0f,  0.5f,
                -0.5f, 1.0f, -0.5f,
                 0.5f, 1.0f, -0.5f,
                 0.5f, 1.0f,  0.5f,
                -0.5f, 1.0f,  0.5f
        };
        float inv = 0.70710677f;
        float[] normals = new float[] {
                -inv, 0.0f, -inv,
                 inv, 0.0f, -inv,
                 inv, 0.0f,  inv,
                -inv, 0.0f,  inv,
                -inv, 0.0f, -inv,
                 inv, 0.0f, -inv,
                 inv, 0.0f,  inv,
                -inv, 0.0f,  inv
        };
        float[] uvs = new float[] {
                0.0f, 0.0f,
                0.33f, 0.0f,
                0.66f, 0.0f,
                1.0f, 0.0f,
                0.0f, 1.0f,
                0.33f, 1.0f,
                0.66f, 1.0f,
                1.0f, 1.0f
        };
        float[] tangents = repeatVec3(8, 1.0f, 0.0f, 0.0f);
        float[] weights = new float[] {
                1.0f, 0.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f
        };
        byte[] joints = repeatJoints(8, (byte) 0, (byte) 1, (byte) 0, (byte) 0);

        short[] indices = new short[] {
                0, 1, 4, 1, 5, 4,
                1, 2, 5, 2, 6, 5,
                2, 3, 6, 3, 7, 6,
                3, 0, 7, 0, 4, 7
        };

        float[] ibm = new float[] {
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1,

                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0,
                0, -1, 0, 1
        };

        Builder b = new Builder();
        int pos = b.addFloats(positions);
        int nrm = b.addFloats(normals);
        int uv = b.addFloats(uvs);
        int tan = b.addFloats(tangents);
        int wgt = b.addFloats(weights);
        int jnt = b.addBytes(joints);
        int idx = b.addShorts(indices);
        int ibmView = b.addFloats(ibm);

        int aPos = b.addAccessor(pos, 5126, 8, "VEC3");
        int aNrm = b.addAccessor(nrm, 5126, 8, "VEC3");
        int aUv = b.addAccessor(uv, 5126, 8, "VEC2");
        int aTan = b.addAccessor(tan, 5126, 8, "VEC3");
        int aWgt = b.addAccessor(wgt, 5126, 8, "VEC4");
        int aJnt = b.addAccessor(jnt, 5121, 8, "VEC4");
        int aIdx = b.addAccessor(idx, 5123, indices.length, "SCALAR");
        int aIbm = b.addAccessor(ibmView, 5126, 2, "MAT4");

        return b.toGltf("""
                "nodes": [
                  {"name": "root", "children": [1, 3]},
                  {"name": "joint0", "children": [2], "translation": [0.0, 0.0, 0.0]},
                  {"name": "joint1", "translation": [0.0, 1.0, 0.0]},
                  {"name": "meshNode", "mesh": 0, "skin": 0}
                ],
                "skins": [
                  {"joints": [1, 2], "skeleton": 1, "inverseBindMatrices": %d}
                ],
                "meshes": [
                  {
                    "primitives": [
                      {
                        "attributes": {
                          "POSITION": %d,
                          "NORMAL": %d,
                          "TEXCOORD_0": %d,
                          "TANGENT": %d,
                          "WEIGHTS_0": %d,
                          "JOINTS_0": %d
                        },
                        "indices": %d,
                        "mode": 4
                      }
                    ]
                  }
                ],
                "scenes": [{"nodes": [0]}],
                "scene": 0
                """.formatted(aIbm, aPos, aNrm, aUv, aTan, aWgt, aJnt, aIdx));
    }

    private static byte[] buildBoxFixtureGlb() {
        float[] positions = new float[] {
                // +X
                0.5f, -0.5f, -0.5f,
                0.5f, -0.5f, 0.5f,
                0.5f, 0.5f, 0.5f,
                0.5f, 0.5f, -0.5f,
                // -X
                -0.5f, -0.5f, 0.5f,
                -0.5f, -0.5f, -0.5f,
                -0.5f, 0.5f, -0.5f,
                -0.5f, 0.5f, 0.5f,
                // +Y
                -0.5f, 0.5f, -0.5f,
                0.5f, 0.5f, -0.5f,
                0.5f, 0.5f, 0.5f,
                -0.5f, 0.5f, 0.5f,
                // -Y
                -0.5f, -0.5f, 0.5f,
                0.5f, -0.5f, 0.5f,
                0.5f, -0.5f, -0.5f,
                -0.5f, -0.5f, -0.5f,
                // +Z
                -0.5f, -0.5f, 0.5f,
                -0.5f, 0.5f, 0.5f,
                0.5f, 0.5f, 0.5f,
                0.5f, -0.5f, 0.5f,
                // -Z
                0.5f, -0.5f, -0.5f,
                0.5f, 0.5f, -0.5f,
                -0.5f, 0.5f, -0.5f,
                -0.5f, -0.5f, -0.5f
        };
        float[] normals = new float[] {
                1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0,
                -1, 0, 0, -1, 0, 0, -1, 0, 0, -1, 0, 0,
                0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0,
                0, -1, 0, 0, -1, 0, 0, -1, 0, 0, -1, 0,
                0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1,
                0, 0, -1, 0, 0, -1, 0, 0, -1, 0, 0, -1
        };
        float[] uvs = repeatFaceUv6Faces();
        float[] tangents = new float[] {
                0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1,
                0, 0, -1, 1, 0, 0, -1, 1, 0, 0, -1, 1, 0, 0, -1, 1,
                1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1,
                1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1,
                1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1,
                -1, 0, 0, 1, -1, 0, 0, 1, -1, 0, 0, 1, -1, 0, 0, 1
        };
        short[] indices = new short[] {
                0, 1, 2, 0, 2, 3,
                4, 5, 6, 4, 6, 7,
                8, 9, 10, 8, 10, 11,
                12, 13, 14, 12, 14, 15,
                16, 17, 18, 16, 18, 19,
                20, 21, 22, 20, 22, 23
        };

        Builder b = new Builder();
        int pos = b.addFloats(positions);
        int nrm = b.addFloats(normals);
        int uv = b.addFloats(uvs);
        int tan = b.addFloats(tangents);
        int idx = b.addShorts(indices);

        int aPos = b.addAccessor(pos, 5126, 24, "VEC3");
        int aNrm = b.addAccessor(nrm, 5126, 24, "VEC3");
        int aUv = b.addAccessor(uv, 5126, 24, "VEC2");
        int aTan = b.addAccessor(tan, 5126, 24, "VEC4");
        int aIdx = b.addAccessor(idx, 5123, indices.length, "SCALAR");

        String body = """
                "meshes": [
                  {
                    "primitives": [
                      {
                        "attributes": {
                          "POSITION": %d,
                          "NORMAL": %d,
                          "TEXCOORD_0": %d,
                          "TANGENT": %d
                        },
                        "indices": %d,
                        "mode": 4
                      }
                    ]
                  }
                ],
                "nodes": [{"mesh": 0}],
                "scenes": [{"nodes": [0]}],
                "scene": 0
                """.formatted(aPos, aNrm, aUv, aTan, aIdx);
        return b.toGlb(body);
    }

    private static byte[] buildTriangleFixtureGlb() {
        float[] positions = new float[] {
                -0.5f, -0.5f, 0.0f,
                0.5f, -0.5f, 0.0f,
                0.0f, 0.5f, 0.0f
        };
        Builder b = new Builder();
        int pos = b.addFloats(positions);
        int aPos = b.addAccessor(pos, 5126, 3, "VEC3");
        String body = """
                "meshes": [
                  {
                    "primitives": [
                      {
                        "attributes": {
                          "POSITION": %d
                        },
                        "mode": 4
                      }
                    ]
                  }
                ],
                "nodes": [{"mesh": 0}],
                "scenes": [{"nodes": [0]}],
                "scene": 0
                """.formatted(aPos);
        return b.toGlb(body);
    }

    private static String buildMorphFixture() {
        float[] positions = new float[] {
                -0.5f, 0.0f, -0.5f,
                 0.5f, 0.0f, -0.5f,
                 0.5f, 0.0f,  0.5f,
                -0.5f, 0.0f,  0.5f
        };
        float[] normals = repeatVec3(4, 0.0f, 1.0f, 0.0f);
        float[] uvs = new float[] {
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f,
                0.0f, 1.0f
        };
        float[] tangents = repeatVec3(4, 1.0f, 0.0f, 0.0f);
        short[] indices = new short[] {0, 1, 2, 0, 2, 3};

        float[] morphPos = repeatVec3(4, 0.0f, 0.5f, 0.0f);
        float[] morphNrm = repeatVec3(4, 0.0f, 0.0f, 0.0f);

        Builder b = new Builder();
        int pos = b.addFloats(positions);
        int nrm = b.addFloats(normals);
        int uv = b.addFloats(uvs);
        int tan = b.addFloats(tangents);
        int idx = b.addShorts(indices);
        int mPos = b.addFloats(morphPos);
        int mNrm = b.addFloats(morphNrm);

        int aPos = b.addAccessor(pos, 5126, 4, "VEC3");
        int aNrm = b.addAccessor(nrm, 5126, 4, "VEC3");
        int aUv = b.addAccessor(uv, 5126, 4, "VEC2");
        int aTan = b.addAccessor(tan, 5126, 4, "VEC3");
        int aIdx = b.addAccessor(idx, 5123, indices.length, "SCALAR");
        int aMorphPos = b.addAccessor(mPos, 5126, 4, "VEC3");
        int aMorphNrm = b.addAccessor(mNrm, 5126, 4, "VEC3");

        return b.toGltf("""
                "meshes": [
                  {
                    "weights": [0.0],
                    "primitives": [
                      {
                        "attributes": {
                          "POSITION": %d,
                          "NORMAL": %d,
                          "TEXCOORD_0": %d,
                          "TANGENT": %d
                        },
                        "indices": %d,
                        "targets": [
                          {
                            "POSITION": %d,
                            "NORMAL": %d
                          }
                        ],
                        "mode": 4
                      }
                    ]
                  }
                ],
                "nodes": [{"mesh": 0}],
                "scenes": [{"nodes": [0]}],
                "scene": 0
                """.formatted(aPos, aNrm, aUv, aTan, aIdx, aMorphPos, aMorphNrm));
    }

    private static String buildMultiPrimitiveFixture() {
        float[] positions = new float[] {
                -1.0f, 0.0f, 0.0f,
                -0.2f, 0.0f, 0.0f,
                -0.6f, 0.8f, 0.0f,
                 0.2f, 0.0f, 0.0f,
                 1.0f, 0.0f, 0.0f,
                 0.6f, 0.8f, 0.0f
        };
        float[] normals = repeatVec3(6, 0.0f, 0.0f, 1.0f);
        float[] uvs = new float[] {
                0.0f, 0.0f,
                0.4f, 0.0f,
                0.2f, 1.0f,
                0.6f, 0.0f,
                1.0f, 0.0f,
                0.8f, 1.0f
        };
        float[] tangents = repeatVec3(6, 1.0f, 0.0f, 0.0f);
        short[] indicesA = new short[] {0, 1, 2};
        short[] indicesB = new short[] {3, 4, 5};

        Builder b = new Builder();
        int pos = b.addFloats(positions);
        int nrm = b.addFloats(normals);
        int uv = b.addFloats(uvs);
        int tan = b.addFloats(tangents);
        int idxA = b.addShorts(indicesA);
        int idxB = b.addShorts(indicesB);

        int aPos = b.addAccessor(pos, 5126, 6, "VEC3");
        int aNrm = b.addAccessor(nrm, 5126, 6, "VEC3");
        int aUv = b.addAccessor(uv, 5126, 6, "VEC2");
        int aTan = b.addAccessor(tan, 5126, 6, "VEC3");
        int aIdxA = b.addAccessor(idxA, 5123, indicesA.length, "SCALAR");
        int aIdxB = b.addAccessor(idxB, 5123, indicesB.length, "SCALAR");

        return b.toGltf("""
                "meshes": [
                  {
                    "primitives": [
                      {
                        "attributes": {
                          "POSITION": %d,
                          "NORMAL": %d,
                          "TEXCOORD_0": %d,
                          "TANGENT": %d
                        },
                        "indices": %d,
                        "mode": 4
                      },
                      {
                        "attributes": {
                          "POSITION": %d,
                          "NORMAL": %d,
                          "TEXCOORD_0": %d,
                          "TANGENT": %d
                        },
                        "indices": %d,
                        "mode": 4
                      }
                    ]
                  }
                ],
                "nodes": [{"mesh": 0}],
                "scenes": [{"nodes": [0]}],
                "scene": 0
                """.formatted(aPos, aNrm, aUv, aTan, aIdxA, aPos, aNrm, aUv, aTan, aIdxB));
    }

    private static String buildTangentFixture() {
        float[] positions = new float[] {
                -0.5f, 0.0f, -0.5f,
                 0.5f, 0.0f, -0.5f,
                 0.5f, 0.0f,  0.5f,
                -0.5f, 0.0f,  0.5f
        };
        float[] normals = repeatVec3(4, 0.0f, 1.0f, 0.0f);
        float[] uvs = new float[] {
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f,
                0.0f, 1.0f
        };
        float[] tangents = new float[] {
                1.0f, 0.0f, 0.0f, 1.0f,
                1.0f, 0.0f, 0.0f, 1.0f,
                1.0f, 0.0f, 0.0f, 1.0f,
                1.0f, 0.0f, 0.0f, 1.0f
        };
        short[] indices = new short[] {0, 1, 2, 0, 2, 3};

        Builder b = new Builder();
        int pos = b.addFloats(positions);
        int nrm = b.addFloats(normals);
        int uv = b.addFloats(uvs);
        int tan = b.addFloats(tangents);
        int idx = b.addShorts(indices);

        int aPos = b.addAccessor(pos, 5126, 4, "VEC3");
        int aNrm = b.addAccessor(nrm, 5126, 4, "VEC3");
        int aUv = b.addAccessor(uv, 5126, 4, "VEC2");
        int aTan = b.addAccessor(tan, 5126, 4, "VEC4");
        int aIdx = b.addAccessor(idx, 5123, indices.length, "SCALAR");

        return b.toGltf("""
                "meshes": [
                  {
                    "primitives": [
                      {
                        "attributes": {
                          "POSITION": %d,
                          "NORMAL": %d,
                          "TEXCOORD_0": %d,
                          "TANGENT": %d
                        },
                        "indices": %d,
                        "mode": 4
                      }
                    ]
                  }
                ],
                "nodes": [{"mesh": 0}],
                "scenes": [{"nodes": [0]}],
                "scene": 0
                """.formatted(aPos, aNrm, aUv, aTan, aIdx));
    }

    private static float[] repeatVec3(int count, float x, float y, float z) {
        float[] out = new float[count * 3];
        for (int i = 0; i < count; i++) {
            int base = i * 3;
            out[base] = x;
            out[base + 1] = y;
            out[base + 2] = z;
        }
        return out;
    }

    private static byte[] repeatJoints(int count, byte j0, byte j1, byte j2, byte j3) {
        byte[] out = new byte[count * 4];
        for (int i = 0; i < count; i++) {
            int base = i * 4;
            out[base] = j0;
            out[base + 1] = j1;
            out[base + 2] = j2;
            out[base + 3] = j3;
        }
        return out;
    }

    private static float[] repeatFaceUv6Faces() {
        float[] out = new float[24 * 2];
        for (int face = 0; face < 6; face++) {
            int base = face * 8;
            out[base] = 0.0f;
            out[base + 1] = 0.0f;
            out[base + 2] = 1.0f;
            out[base + 3] = 0.0f;
            out[base + 4] = 1.0f;
            out[base + 5] = 1.0f;
            out[base + 6] = 0.0f;
            out[base + 7] = 1.0f;
        }
        return out;
    }

    private record View(int byteOffset, int byteLength) {
    }

    private record Accessor(int viewIndex, int componentType, int count, String type) {
    }

    private static final class Builder {
        private final List<Byte> bytes = new ArrayList<>();
        private final List<View> views = new ArrayList<>();
        private final List<Accessor> accessors = new ArrayList<>();

        int addFloats(float[] values) {
            ByteBuffer bb = ByteBuffer.allocate(values.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            for (float value : values) {
                bb.putFloat(value);
            }
            return addBytes(bb.array());
        }

        int addShorts(short[] values) {
            ByteBuffer bb = ByteBuffer.allocate(values.length * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            for (short value : values) {
                bb.putShort(value);
            }
            return addBytes(bb.array());
        }

        int addBytes(byte[] values) {
            align4();
            int offset = bytes.size();
            for (byte value : values) {
                bytes.add(value);
            }
            int viewIndex = views.size();
            views.add(new View(offset, values.length));
            return viewIndex;
        }

        int addAccessor(int viewIndex, int componentType, int count, String type) {
            int accessorIndex = accessors.size();
            accessors.add(new Accessor(viewIndex, componentType, count, type));
            return accessorIndex;
        }

        String toGltf(String bodyJson) {
            byte[] raw = binaryBlob();
            String encoded = Base64.getEncoder().encodeToString(raw);

            String bufferDecl = "\"buffers\": [{ \"uri\": \"data:application/octet-stream;base64,"
                    + encoded
                    + "\", \"byteLength\": " + raw.length + " }],";
            return toGltfDocument(bufferDecl, bodyJson);
        }

        byte[] toGlb(String bodyJson) {
            byte[] bin = binaryBlob();
            String bufferDecl = "\"buffers\": [{ \"byteLength\": " + bin.length + " }],";
            String json = toGltfDocument(bufferDecl, bodyJson);
            return packGlb(json, bin);
        }

        private byte[] binaryBlob() {
            byte[] raw = new byte[bytes.size()];
            for (int i = 0; i < bytes.size(); i++) {
                raw[i] = bytes.get(i);
            }
            return raw;
        }

        private String toGltfDocument(String bufferDecl, String bodyJson) {
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("  \"asset\": { \"version\": \"2.0\" },\n");
            json.append("  ").append(bufferDecl).append("\n");
            json.append("  \"bufferViews\": [\n");
            for (int i = 0; i < views.size(); i++) {
                View v = views.get(i);
                json.append("    { \"buffer\": 0, \"byteOffset\": ")
                        .append(v.byteOffset)
                        .append(", \"byteLength\": ")
                        .append(v.byteLength)
                        .append(" }");
                json.append(i + 1 == views.size() ? "\n" : ",\n");
            }
            json.append("  ],\n");

            json.append("  \"accessors\": [\n");
            for (int i = 0; i < accessors.size(); i++) {
                Accessor a = accessors.get(i);
                json.append("    { \"bufferView\": ")
                        .append(a.viewIndex)
                        .append(", \"componentType\": ")
                        .append(a.componentType)
                        .append(", \"count\": ")
                        .append(a.count)
                        .append(", \"type\": \"")
                        .append(a.type)
                        .append("\" }");
                json.append(i + 1 == accessors.size() ? "\n" : ",\n");
            }
            json.append("  ],\n");

            String normalized = bodyJson == null ? "" : bodyJson.strip();
            if (!normalized.isEmpty()) {
                json.append("  ").append(normalized.replace("\n", "\n  ")).append('\n');
            }
            json.append("}\n");
            return json.toString();
        }

        private byte[] packGlb(String jsonText, byte[] binData) {
            byte[] jsonRaw = jsonText.getBytes(StandardCharsets.UTF_8);
            int jsonPaddedLen = align4(jsonRaw.length);
            int binPaddedLen = align4(binData.length);
            int totalLen = 12 + 8 + jsonPaddedLen + 8 + binPaddedLen;

            ByteBuffer out = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN);
            out.putInt(GLB_MAGIC);
            out.putInt(GLB_VERSION);
            out.putInt(totalLen);

            out.putInt(jsonPaddedLen);
            out.putInt(GLB_JSON_CHUNK);
            out.put(jsonRaw);
            for (int i = jsonRaw.length; i < jsonPaddedLen; i++) {
                out.put((byte) 0x20);
            }

            out.putInt(binPaddedLen);
            out.putInt(GLB_BIN_CHUNK);
            out.put(binData);
            for (int i = binData.length; i < binPaddedLen; i++) {
                out.put((byte) 0);
            }
            return out.array();
        }

        private void align4() {
            while (bytes.size() % 4 != 0) {
                bytes.add((byte) 0);
            }
        }

        private static int align4(int value) {
            return (value + 3) & ~3;
        }
    }
}
