#version 440 core

// Sun beam fragment shader. Opacity is accumulated from 16 sunlight samples
// per beam column; a temporal accumulator SSBO blends the running average so
// foliage shadow flicker does not translate into beam flicker.

layout(location = 0) in vec2 vBeamUv;
layout(location = 1) flat in uint vBeamIndex;

layout(location = 0) out vec4 fragColor;

// Temporal accumulator: running average opacity per beam, blended each frame.
layout(std430, binding = 2) restrict buffer AccumBlock {
    float beamAccum[]; // one entry per beam
};

uniform sampler2D uShadowMap;
uniform vec3 uSunDir;
uniform float uIntensity;   // config: sunBeamIntensity
uniform float uBlendFactor; // temporal smoothing, e.g. 0.1
uniform uint uSampleCount;  // 16

const float PI = 3.14159265;

float sampleOpacity(vec2 uv, float depthRef) {
    float shadow = texture(uShadowMap, uv).r;
    return clamp(depthRef - shadow, 0.0, 1.0);
}

void main() {
    // 16 sun samples swept across the beam width (dithered golden-ratio path).
    float acc = 0.0;
    float golden = 0.6180339887;
    float phase = fract(float(gl_FragCoord.x) * golden);
    for (uint i = 0u; i < uSampleCount; i++) {
        float t = (float(i) + phase) / float(uSampleCount);
        vec2 uv = vec2(mix(0.0, 1.0, t), vBeamUv.y);
        acc += sampleOpacity(uv, 1.0);
    }
    float instant = acc / float(uSampleCount);

    // Temporal average via SSBO accumulator: kills foliage flicker.
    float prev = beamAccum[vBeamIndex];
    float blended = mix(prev, instant, uBlendFactor);
    beamAccum[vBeamIndex] = blended;

    // Soft vertical falloff so beams fade before the shadow caster.
    float falloff = sin(vBeamUv.y * PI);

    fragColor = vec4(vec3(1.0, 0.95, 0.8) * instant * falloff * uIntensity, instant * falloff * uIntensity);
}
