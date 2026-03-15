package org.dynamisengine.light.impl.vulkan.shader;

public final class VulkanCullingComputeSource {
    private VulkanCullingComputeSource() {
    }

    public static String compute() {
        return """
                #version 450
                layout(local_size_x = 64) in;

                struct MeshBounds {
                    vec4 centerRadius;
                    uvec4 meta;
                };

                struct DrawCmd {
                    uint indexCount;
                    uint instanceCount;
                    uint firstIndex;
                    int vertexOffset;
                    uint firstInstance;
                };

                layout(set = 0, binding = 0) readonly buffer BoundsBuffer {
                    MeshBounds bounds[];
                } meshBounds;

                layout(set = 0, binding = 1) readonly buffer InputCommands {
                    DrawCmd cmds[];
                } inputCmds;

                layout(set = 0, binding = 2) writeonly buffer OutputCommands {
                    DrawCmd cmds[];
                } outputCmds;

                layout(set = 0, binding = 3) buffer DrawCount {
                    uint counts[8];
                } drawCount;

                layout(push_constant) uniform FrustumPush {
                    vec4 planes[6];
                    uvec4 header0; // x=drawCount, y=boundsCount, z=staticBase, w=morphBase
                    uvec4 header1; // x=skinnedBase, y=skinnedMorphBase, z=instancedBase, w=unused
                } pc;

                uint variantBase(uint variant) {
                    if (variant == 0u) {
                        return pc.header0.z;
                    }
                    if (variant == 1u) {
                        return pc.header0.w;
                    }
                    if (variant == 2u) {
                        return pc.header1.x;
                    }
                    if (variant == 3u) {
                        return pc.header1.y;
                    }
                    return pc.header1.z;
                }

                bool sphereVisible(vec3 center, float radius) {
                    for (int i = 0; i < 6; i++) {
                        vec4 p = pc.planes[i];
                        float d = dot(p.xyz, center) + p.w;
                        if (d < -radius) {
                            return false;
                        }
                    }
                    return true;
                }

                void main() {
                    uint id = gl_GlobalInvocationID.x;
                    if (id >= pc.header0.x) {
                        return;
                    }

                    DrawCmd cmd = inputCmds.cmds[id];
                    uint variant = (cmd.firstInstance >> 29u) & 0x7u;
                    cmd.firstInstance = cmd.firstInstance & 0x1FFFFFFFu;
                    if (variant > 4u) {
                        variant = 0u;
                    }

                    bool visible = true;
                    if (id < pc.header0.y) {
                        MeshBounds b = meshBounds.bounds[id];
                        visible = sphereVisible(b.centerRadius.xyz, b.centerRadius.w);
                    }
                    if (!visible) {
                        return;
                    }
                    uint outIndex = variantBase(variant) + atomicAdd(drawCount.counts[variant], 1u);
                    outputCmds.cmds[outIndex] = cmd;
                }
                """;
    }
}
