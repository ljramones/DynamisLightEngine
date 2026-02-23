package org.dynamislight.impl.vulkan.shader;

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
                    uint count;
                } drawCount;

                layout(push_constant) uniform FrustumPush {
                    vec4 planes[6];
                    uint drawCount;
                    uint boundsCount;
                    uint _pad0;
                    uint _pad1;
                } pc;

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
                    if (id >= pc.drawCount) {
                        return;
                    }

                    DrawCmd cmd = inputCmds.cmds[id];

                    // Instance draws are appended after per-mesh draws in current submission ordering.
                    // Those slots currently bypass culling because bounds are tracked per registered mesh.
                    if (id >= pc.boundsCount) {
                        outputCmds.cmds[id] = cmd;
                        atomicAdd(drawCount.count, 1u);
                        return;
                    }

                    MeshBounds b = meshBounds.bounds[id];
                    bool visible = sphereVisible(b.centerRadius.xyz, b.centerRadius.w);
                    if (visible) {
                        outputCmds.cmds[id] = cmd;
                        atomicAdd(drawCount.count, 1u);
                    } else {
                        outputCmds.cmds[id].indexCount = 0u;
                        outputCmds.cmds[id].instanceCount = 0u;
                        outputCmds.cmds[id].firstIndex = 0u;
                        outputCmds.cmds[id].vertexOffset = 0;
                        outputCmds.cmds[id].firstInstance = 0u;
                    }
                }
                """;
    }
}
