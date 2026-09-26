#version 330 core

// Sun beam fragment shader (GL 3.3). Opacity is computed per-instance from a
// temporally smoothed scan estimate; no SSBO accumulator required on the
// legacy path (the temporal smoothing already happened CPU-side).

layout(location = 0) in vec2 vBeamUv;

layout(location = 0) out vec4 fragColor;

uniform float uIntensity;
uniform float uOpacity;

void main() {
    float radial = sin(vBeamUv.x * 3.14159265);
    float vertical = 1.0 - vBeamUv.y;
    float a = uOpacity * radial * vertical * uIntensity;

    fragColor = vec4(vec3(1.0, 0.95, 0.78) * a, a);
}
