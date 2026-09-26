#version 440 core

// Terrain vertex shader. Each packed quad (one 32-bit int) is expanded into a
// 4-vertex triangle strip procedurally: gl_VertexID picks the corner, so the
// VBO stores one int per face and the SSBO carries section origins + quads.

layout(std430, binding = 0) restrict readonly buffer SectionBlock {
    uvec4 sections[];   // [0..N): xy = world origin, z = unused, w = floatBits(sink offset)
    uint  quadData[];   // packed quads, section-local (see PackedQuad bit layout)
};

layout(location = 0) out vec2 vUv;
layout(location = 1) out vec3 vNormal;
layout(location = 2) out vec3 vWorldPos;

uniform mat4 uViewProj;
uniform vec3 uCamPos;

const vec3 FACE_NORMALS[6] = vec3[6](
    vec3(-1.0, 0.0, 0.0), vec3(1.0, 0.0, 0.0),
    vec3(0.0, -1.0, 0.0), vec3(0.0, 1.0, 0.0),
    vec3(0.0, 0.0, -1.0), vec3(0.0, 0.0, 1.0)
);

void main() {
    uint drawId = uint(gl_DrawID);
    uvec4 sec = sections[drawId];

    uint quadIndex = uint(gl_VertexID >> 2);   // quad this vertex belongs to
    uint corner = uint(gl_VertexID & 3u);      // 0..3 strip corner order

    uint packed = quadData[quadIndex];

    int x  = int(packed & 0x3FFu);
    int y  = int((packed >> 10u) & 0x3FFu);
    int z  = int((packed >> 20u) & 0x3Fu);
    uint dir = (packed >> 26u) & 0x7u;
    int w  = int((packed >> 28u) & 0x3u) + 1; // merged width in blocks
    int h  = int((packed >> 30u) & 0x3u) + 1; // merged height in blocks

    // Corner order for a triangle strip: (0,0) (1,0) (0,1) (1,1).
    uvec2 co = uvec2(corner & 1u, corner >> 1u);

    // Expand the width/height span in the face's plane.
    vec3 local;
    if (dir <= 1u) {      local = vec3(x, y + co.y * h, z + co.x * w); }
    else if (dir <= 3u) { local = vec3(x + co.x * w, y, z + co.y * h); }
    else {                local = vec3(x + co.x * w, y + co.y * h, z); }

    vec3 world = vec3(sec.xy) + local;
    world.y -= sec.w; // LOD sink: drop terrain underground at the HQ boundary

    vNormal = FACE_NORMALS[dir];
    vWorldPos = world;
    vUv = vec2(co) * vec2(w, h); // world-space UV so textures tile across merged faces

    gl_Position = uViewProj * vec4(world, 1.0);
}
