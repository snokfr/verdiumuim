#version 440 core

// Terrain fragment shader. Face direction drives the light term; texture
// lookup uses world-space tiling so greedy-merged quads keep detail.

layout(location = 0) in vec2 vUv;
layout(location = 1) in vec3 vNormal;
layout(location = 2) in vec3 vWorldPos;

layout(location = 0) out vec4 fragColor;

uniform sampler2D uAtlas;
uniform vec3 uLightDir;   // normalized sun direction
uniform vec3 uCamPos;
uniform float uTime;

void main() {
    // Face shading: bake a simple directional + ambient term per normal.
    float ndl = max(dot(normalize(vNormal), normalize(uLightDir)), 0.0);
    float light = 0.35 + 0.65 * ndl;

    // World-space tiled checker placeholder until the atlas hookup lands;
    // texture ID extraction from the packed quad is a follow-up (v1.1).
    vec2 tile = floor(vWorldPos.xz) * 0.25;
    vec4 texel = vec4(0.45 + 0.1 * fract(tile.x) + 0.1 * fract(tile.y),
                      0.55, 0.40, 1.0);

    fragColor = vec4(texel.rgb * light, 1.0);
}
