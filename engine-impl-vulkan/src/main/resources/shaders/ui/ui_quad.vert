#version 450

layout(location = 0) in vec2 inPos;
layout(location = 1) in vec2 inUV;
layout(location = 2) in uint inColor;

layout(push_constant) uniform PushConstants {
    vec2 screenSize;
} pc;

layout(location = 0) out vec2 fragUV;
layout(location = 1) out vec4 fragColor;

void main() {
    // Convert screen-space to NDC: (0,0) top-left -> (-1,-1) to (1,1)
    float x = (inPos.x / pc.screenSize.x) * 2.0 - 1.0;
    float y = (inPos.y / pc.screenSize.y) * 2.0 - 1.0;
    gl_Position = vec4(x, y, 0.0, 1.0);

    fragUV = inUV;

    // Unpack ABGR color
    fragColor = vec4(
        float(inColor & 0xFFu) / 255.0,
        float((inColor >> 8u) & 0xFFu) / 255.0,
        float((inColor >> 16u) & 0xFFu) / 255.0,
        float((inColor >> 24u) & 0xFFu) / 255.0
    );
}
