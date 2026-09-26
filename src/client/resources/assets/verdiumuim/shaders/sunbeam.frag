#version 440 core

// Sun beam fragment shader. Per-beam opacity arrives from the scanner's
// 16-sample estimate, temporally smoothed via the accumulator SSBO (binding 2)
// so foliage shadow flicker does not flicker the beams.

layout(location = 0) in vec2 vBeamUv;
layout(location = 1) flat in uint vBeamIndex;

layout(location = 0) out vec4 fragColor;

layout(std430, binding = 2) restrict readonly buffer AccumBlock {
    float beamAccum[];
};

uniform float uIntensity;

const float PI = 3.14159265;

void main() {
    float opacity = beamAccum[vBeamIndex];

    // Soft falloff on both axes so beams fade at edges and tops.
    float radial = sin(vBeamUv.x * PI);
    float vertical = 1.0 - vBeamUv.y;
    float a = opacity * radial * vertical * uIntensity;

    fragColor = vec4(vec3(1.0, 0.95, 0.78) * a, a);
}
