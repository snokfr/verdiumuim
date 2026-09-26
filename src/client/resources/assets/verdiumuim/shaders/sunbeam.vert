#version 440 core

// Sun beam vertex shader. Each beam is a 4-vertex triangle strip: a vertical
// 2D plane billboarded around its column, expanded procedurally from
// gl_VertexID. Beam data (world xyz + opacity) arrives via SSBO 1 indexed
// by gl_DrawID (one instance per beam).

layout(std430, binding = 1) restrict readonly buffer BeamBlock {
    uvec4 beams[]; // x = worldX, y = worldY, z = worldZ, w = floatBits(opacity)
};

layout(location = 0) out vec2 vBeamUv;
layout(location = 1) flat out uint vBeamIndex;

uniform mat4 uView;
uniform mat4 uProj;
uniform vec3 uCamPos;
uniform float uTime;

void main() {
    uvec4 b = beams[uint(gl_DrawID)];
    // Beam coords are integers, not float bits.
    vec3 origin = vec3(float(b.x), float(b.y), float(b.z));

    uint corner = uint(gl_VertexID) & 3u;
    uvec2 co = uvec2(corner & 1u, corner >> 1u);

    // Vertical plane facing the camera: width across, height up.
    vec3 toCam = normalize(uCamPos - origin);
    vec3 side = normalize(cross(vec3(0.0, 1.0, 0.0), toCam));

    float w = 0.9;
    float h = 6.0;
    vec3 world = origin + side * (float(co.x) - 0.5) * w + vec3(0.0, float(co.y) * h, 0.0);

    vBeamUv = vec2(co);
    vBeamIndex = uint(gl_DrawID);

    gl_Position = uProj * uView * vec4(world, 1.0);
}
