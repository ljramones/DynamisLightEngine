#version 450

layout(location = 0) in vec2 fragUV;
layout(location = 1) in vec4 fragColor;

layout(set = 0, binding = 0) uniform sampler2D fontAtlas;

layout(location = 0) out vec4 outColor;

void main() {
    // If UV is (0,0)-(0,0) this is a solid quad; otherwise sample font atlas
    float alpha;
    if (fragUV.x == 0.0 && fragUV.y == 0.0) {
        alpha = 1.0; // solid color
    } else {
        alpha = texture(fontAtlas, fragUV).r; // R8 font atlas
    }
    outColor = vec4(fragColor.rgb, fragColor.a * alpha);
}
