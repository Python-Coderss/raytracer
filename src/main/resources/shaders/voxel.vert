#version 430 core // Use 430 for SSBOs and other potential features

out vec2 fragCoord_v; // Pass normalized screen coordinates to fragment shader

void main() {
    // Fullscreen triangle
    vec2 pos = vec2(0.0);
    if (gl_VertexID == 0) pos = vec2(-1.0, -1.0);
    else if (gl_VertexID == 1) pos = vec2(3.0, -1.0);
    else if (gl_VertexID == 2) pos = vec2(-1.0, 3.0);

    fragCoord_v = pos * 0.5 + 0.5; // Map to [0,1]
    gl_Position = vec4(pos, 0.0, 1.0);
}
