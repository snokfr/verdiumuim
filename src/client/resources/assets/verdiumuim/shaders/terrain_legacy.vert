#version 330 core

// Legacy (GL 3.3) terrain vertex shader. Same greedy quad expansion as the
// modern path, but per-instance data arrives as vertex attributes instead of
// an SSBO, and the 4-corner triangle strip is generated from gl_VertexID.

layout(location = 1) in vec4 aOriginDir; // quad origin xyz + face dir
layout(location = 2) in vec4 aDims;      // width, height, world origin x, world origin y
layout(location = 3) in vec4 aMisc;      // world origin z, sink offset, -, -

layout(location = 0) out vec2 vUv;
layout(location = 1) out vec3 vNormal;
layout(location = 2) out vec3 vWorldPos;

uniform mat4 uView;
uniform mat4 uProj;
uniform float uSinkScale;

const vec3 FACE_NORMALS[6] = vec3[6](
    vec3(-1.0, 0.0, 0.0), vec3(1.0, 0.0, 0.0),
    vec3(0.0, -1.0, 0.0), vec3(0.0, 1.0, 0.0),
    vec3(0.0, 0.0, -1.0), vec3(0.0, 0.0, 1.0)
);

void main() {
    vec3 origin = aOriginDir.xyz;
    uint dir = uint(aOriginDir.w + 0.5);
    float w = aDims.x;
    float h = aDims.y;
    vec3 worldOrigin = vec3(aDims.z, aDims.w, aMisc.x);
    float sink = aMisc.y * uSinkScale;

    // 4-corner strip expansion, procedural from gl_VertexID.
    uint corner = uint(gl_VertexID) & 3u;
    vec2 co = vec2(float(corner & 1u), float(corner >> 1u));

    vec3 local;
    if (dir <= 1u) {      local = vec3(origin.x, origin.y + co.y * h, origin.z + co.x * w); }
    else if (dir <= 3u) { local = vec3(origin.x + co.x * w, origin.y, origin.z + co.y * h); }
    else {                local = vec3(origin.x + co.x * w, origin.y + co.y * h, origin.z); }

    vec3 world = worldOrigin + local;
    world.y -= sink; // LOD sink

    vNormal = FACE_NORMALS[dir];
    vWorldPos = world;
    vUv = co * vec2(w, h);

    gl_Position = uProj * uView * vec4(world, 1.0);
}
