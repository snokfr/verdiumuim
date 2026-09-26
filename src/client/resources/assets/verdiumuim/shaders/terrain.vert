#version 440 core

// Terrain vertex shader. Vertex data lives entirely in SSBO 0; the EBO is an
// identity passthrough so gl_VertexID == index, and corner order for the
// 4-vertex triangle strip is generated procedurally.
//
// SSBO 0 layout (written by ChunkBatchRenderer):
//   [0]                uvec4 header = (sectionCount, 0, 0, 0)
//   [1 .. N]           uvec4 section records: xyz = world origin,
//                      w = floatBits(sinkOffset)
//   [N+1 ..]           uvec4 quads: x = sectionId, y = packed quad int
//                        (PackedQuad layout: 10b x, 10b y, 6b z, 3b dir,
//                         2b w-1, 2b h-1)
//
// Each section draws quadCount*4 vertices as one TRIANGLE_STRIP
// via glMultiDrawElementsIndirect; baseVertex offsets gl_VertexID to the
// section's first quad.

layout(std430, binding = 0) restrict readonly buffer SectionBlock {
    uvec4 header;    // x = section count
    uvec4 sections[]; // [0..N-1] sections, [N..] quads
};

layout(location = 0) out vec2 vUv;
layout(location = 1) out vec3 vNormal;
layout(location = 2) out vec3 vWorldPos;

uniform mat4 uView;
uniform mat4 uProj;
uniform float uSinkScale; // global LOD sink multiplier (0 = off)

const vec3 FACE_NORMALS[6] = vec3[6](
    vec3(-1.0, 0.0, 0.0), vec3(1.0, 0.0, 0.0),
    vec3(0.0, -1.0, 0.0), vec3(0.0, 1.0, 0.0),
    vec3(0.0, 0.0, -1.0), vec3(0.0, 0.0, 1.0)
);

void main() {
    uint n = header.x;
    uint quadBase = uint(gl_VertexID) >> 2u;   // quad index within this section
    uint corner = uint(gl_VertexID) & 3u;      // 0..3 strip corner
    uvec2 co = uvec2(corner & 1u, corner >> 1u);

    // baseVertex = firstQuad * 4, so absolute quad index = baseVertex/4 + quadBase.
    // We encode the section id directly in the quad stream instead: the
    // renderer writes (sectionId, packed) pairs, and each section's command
    // uses baseVertex = its firstQuad*4. Recover the section id from the
    // first vertex of the command via a small search over the command-free
    // path: the section record index equals gl_DrawID by construction.
    uint sid = uint(gl_DrawID);

    uvec4 quadRec = sections[n + quadBase];
    uint packed = quadRec.y;

    int x = int(packed & 0x3FFu);
    int y = int((packed >> 10u) & 0x3FFu);
    int z = int((packed >> 20u) & 0x3Fu);
    uint dir = (packed >> 26u) & 0x7u;
    int w = int((packed >> 28u) & 0x3u) + 1;
    int h = int((packed >> 30u) & 0x3u) + 1;

    // Expand the merged span in the face's plane.
    vec3 local;
    if (dir <= 1u) {      local = vec3(x, y + co.y * h, z + co.x * w); }
    else if (dir <= 3u) { local = vec3(x + co.x * w, y, z + co.y * h); }
    else {                local = vec3(x + co.x * w, y + co.y * h, z); }

    uvec4 sec = sections[sid];
    vec3 world = vec3(sec.xy) + local;
    world.y -= float(sec.w) * uSinkScale; // LOD sink: push terrain underground

    vNormal = FACE_NORMALS[dir & 7u];
    vWorldPos = world;
    vUv = vec2(co) * vec2(w, h); // world-space UV tiling across merged faces

    gl_Position = uProj * uView * vec4(world, 1.0);
}
