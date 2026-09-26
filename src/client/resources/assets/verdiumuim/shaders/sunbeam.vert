#version 330 core

// Sun beam vertex shader (GL 3.3 compatible). Each beam is a 4-vertex
// triangle strip billboarded around its column. gl_InstanceID selects the
// beam; the beam's world position comes in as a per-instance float attribute
// (integers uploaded as floats) because this Intel driver rejects
// gl_DrawID-based SSBO indexing even when the extension flag is advertised.

layout(location = 0) in vec3 iBeamPos;   // world-space beam column base (per instance)
layout(location = 1) in float iOpacity;  // temporally-smoothed opacity (per instance)

layout(location = 0) out vec2 vBeamUv;

uniform mat4 uView;
uniform mat4 uProj;
uniform vec3 uCamPos;

void main() {
    vec3 origin = iBeamPos;
    uint corner = uint(gl_VertexID) & 3u;
    vec2 co = vec2(float(corner & 1u), float(corner >> 1u));

    // Vertical plane billboarded toward the camera.
    vec3 toCam = normalize(uCamPos - origin);
    vec3 side = normalize(cross(vec3(0.0, 1.0, 0.0), toCam));

    float w = 0.9;
    float h = 6.0;
    vec3 world = origin + side * (co.x - 0.5) * w + vec3(0.0, co.y * h, 0.0);

    vBeamUv = co;

    gl_Position = uProj * uView * vec4(world, 1.0);
}
