#version 450

layout(location = 0) in vec2 inPos;
layout(location = 1) in uint inColor;

layout(push_constant) uniform PushConstants {
    vec2 screenSize;
} pc;

layout(location = 0) out vec4 fragColor;

void main() {
    float x = (inPos.x / pc.screenSize.x) * 2.0 - 1.0;
    float y = (inPos.y / pc.screenSize.y) * 2.0 - 1.0;
    gl_Position = vec4(x, y, 0.0, 1.0);

    fragColor = vec4(
        float(inColor & 0xFFu) / 255.0,
        float((inColor >> 8u) & 0xFFu) / 255.0,
        float((inColor >> 16u) & 0xFFu) / 255.0,
        float((inColor >> 24u) & 0xFFu) / 255.0
    );
}
