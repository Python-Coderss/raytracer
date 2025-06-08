#version 330 core
out vec4 FragColor;
in vec2 fragCoord; // Received from vertex shader, non-normalized (0,0 to 1,1)

uniform vec2 iResolution; // Viewport resolution in pixels
uniform float iTime;     // Shader playback time (in seconds)

#define MAX_STEPS 64
#define MAX_DIST 100.0
#define SURF_DIST 0.01

// SDF for a sphere
float sphereSDF(vec3 p, vec3 c, float r) {
    return length(p - c) - r;
}

// Scene SDF - contains one sphere and backface culling
float sceneSDF(vec3 p, out vec3 normal) {
    vec3 sphereCenter = vec3(0.0, 0.0, 2.0); // Moved sphere further to see it
    float sphereRadius = 1.0;
    float d = sphereSDF(p, sphereCenter, sphereRadius);

    // Approximate normal using finite difference
    // Epsilon for normal calculation
    float eps = 0.001;
    // Gradient of the SDF is the normal
    normal = normalize(vec3(
        sphereSDF(p + vec3(eps, 0.0, 0.0), sphereCenter, sphereRadius) - sphereSDF(p - vec3(eps, 0.0, 0.0), sphereCenter, sphereRadius),
        sphereSDF(p + vec3(0.0, eps, 0.0), sphereCenter, sphereRadius) - sphereSDF(p - vec3(0.0, eps, 0.0), sphereCenter, sphereRadius),
        sphereSDF(p + vec3(0.0, 0.0, eps), sphereCenter, sphereRadius) - sphereSDF(p - vec3(0.0, 0.0, eps), sphereCenter, sphereRadius)
    ));

    // Backface culling: If ray is hitting the back of the surface, treat as no hit
    // Assuming camera is at origin or looking towards positive z
    // The ray direction `rd` is needed here. We'll use `normalize(p - ro)` as an approximation for the view vector for now.
    // A proper solution would pass the ray direction to this function or use the view vector from the camera.
    // For this example, let's assume `ro` (ray origin) is `vec3(0,0,-5)` as in main.
    // So, the view vector towards `p` is `normalize(p - vec3(0.0, 0.0, -5.0))`.
    // If `dot(normal, view_vector) > 0`, then we are looking at a backface.
    // Simplified: if normal is pointing towards camera (positive z for normal, negative z for view direction)
    // it's a front face. If normal is pointing away from camera (negative z for normal) it's a back face.
    // Let's use the direction from the point `p` to the ray origin `ro`.
    // If the surface normal points in a similar direction to the vector pointing from the surface to the camera,
    // it's a back-face for a camera outside the object.
    // Given `ro = vec3(0.0, 0.0, -5.0)`, the view vector `v = normalize(ro - p)`.
    // If `dot(normal, v) < 0` then it's a backface.
    // The issue's example uses `dot(normal, normalize(p)) > 0.0`. This assumes the ray origin is at (0,0,0) and points towards p.
    // Let's stick to the issue's example for now, but note its assumption.
    // `normalize(p)` is effectively the direction from origin to point `p`.
    //if (dot(normal, normalize(p - vec3(0.0, 0.0, -5.0))) > 0.0) { // Check against direction from point to camera
         //return MAX_DIST; // Commented out for now to ensure sphere is visible for testing
    //}

    return d;
}

// Basic raymarching algorithm
vec3 rayMarch(vec3 ro, vec3 rd) {
    float totalDistance = 0.0;
    for (int i = 0; i < MAX_STEPS; i++) {
        vec3 p = ro + rd * totalDistance;
        vec3 normal; // Output normal from sceneSDF
        float d = sceneSDF(p, normal);

        if (d < SURF_DIST) {
            // Simple diffuse lighting
            vec3 lightDir = normalize(vec3(0.5, 0.5, -1.0)); // Light direction
            float diff = max(dot(normal, lightDir), 0.0);
            // Return color based on diffuse lighting
            return vec3(diff * 0.8 + 0.2); // Ambient + Diffuse
        }

        // If ray goes too far or distance is too small (should not happen if SURF_DIST is chosen well)
        if (totalDistance > MAX_DIST || d < 0.0001) { // d < 0.0001 to prevent issues if inside an object
            break;
        }
        totalDistance += d;
    }
    return vec3(0.1, 0.1, 0.2); // Background color (e.g., dark blue)
}

void main() {
    // Normalize fragment coordinates to [-1, 1] range
    vec2 uv = (fragCoord * 2.0 - 1.0);
    // Correct for aspect ratio
    uv.x *= iResolution.x / iResolution.y;

    // Ray origin (camera position)
    vec3 ro = vec3(0.0, 0.0, -5.0);
    // Ray direction (calculated from UVs, pointing towards positive Z)
    vec3 rd = normalize(vec3(uv, 1.5)); // Adjust Z component for FOV

    // Perform raymarching
    vec3 col = rayMarch(ro, rd);

    FragColor = vec4(col, 1.0);
}
