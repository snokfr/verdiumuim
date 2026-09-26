#version 330 core

// Legacy (GL 3.3) terrain fragment shader. Same shading as the modern path.

layout(location = 0) in vec2 vUv;
layout(location = 1) in vec3 vNormal;
layout(location = 2) in vec3 vWorldPos;

layout(location = 0) out vec4 fragColor;

uniform vec3 uLightDir;

void main() {
    float ndl = max(dot(normalize(vNormal), normalize(uLightDir)), 0.0);
    float light = 0.35 + 0.65 * ndl;

    // Subtle world-space variation so merged greedy quads read as terrain.
    vec2 tile = floor(vWorldPos.xz * 0.5) + floor(vWorldPos.y * 0.5);
    float variation = 0.9 + 0.1 * fract(tile.x * 0.25 + tile.y * 0.5);

    fragColor = vec4(vec3(0.45, 0.55, 0.40) * light * variation, 1.0);
}
