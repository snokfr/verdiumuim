#version 440 core

// Sun beam vertex shader. Beam quads are 2D planes extruded along the sun
// direction from shadow-edge voxels; corner expansion is procedural like the
// terrain path.

layout(std430, binding = 1) restrict readonly buffer BeamBlock {
    uvec4 beams[]; // xy = world origin (x, y), zw = plane extent (w along edge, d along sun dir)
};

layout(location = 0) out vec2 vBeamUv;
layout(location = 1) flat out uint vBeamIndex;

uniform mat4 uViewProj;

void main() {
    uvec4 b = beams[gl_DrawID];
    uint quadIndex = uint(gl_VertexID >> 2);
    uint corner = uint(gl_VertexID & 3u);
    uvec2 co = uvec2(corner & 1u, corner >> 1u);

    vec3 origin = vec3(b.xy);
    vec3 extent = vec3(b.zw & 0xFFFFu, b.zw >> 16u);

    // Plane: width along the shadow edge, depth along the sun direction.
    vec3 local = vec3(co.x * extent.x, co.y * extent.y, 0.0);
    vec3 world = origin + local;

    vBeamUv = vec2(co);
    vBeamIndex = gl_DrawID;

    gl_Position = uViewProj * vec4(world, 1.0);
}
