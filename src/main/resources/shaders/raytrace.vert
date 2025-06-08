#version 330 core
out vec2 fragCoord; // Output to fragment shader

void main() {
    // Generate a fullscreen triangle using gl_VertexID
    // The vertices are (-1,-1), (3,-1), (-1,3) in clip space
    // This covers the entire screen.
    vec2 pos = vec2(0.0);
    if (gl_VertexID == 0) {
        pos = vec2(-1.0, -1.0);
    } else if (gl_VertexID == 1) {
        pos = vec2(3.0, -1.0);
    } else if (gl_VertexID == 2) {
        pos = vec2(-1.0, 3.0);
    }

    // Pass texture coordinates (0,0) to (1,1) for the fragment shader
    // The calculation (pos * 0.5 + 0.5) maps clip space coordinates to [0,1] range.
    fragCoord = pos * 0.5 + 0.5;

    gl_Position = vec4(pos, 0.0, 1.0);
}
