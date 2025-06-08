#version 430 core

in vec2 fragCoord_v; // Normalized screen coordinates [0,1]
out vec4 FragColor;

uniform vec2 iResolution;   // Viewport resolution
uniform float iTime;        // Time
uniform vec3 cameraPos_world;   // Camera position in world space
uniform mat3 cameraToWorldMatrix; // Matrix to transform ray direction from camera to world space

// Voxel data structure from SSBO
struct Voxel {
    // Matches the packing in Voxel.java:
    // Bits 0-3: localX
    // Bits 4-7: localY
    // Bits 8-11: localZ
    // Bits 12-21: textureId (10 bits)
    // Bit 22: isSolid (1 bit)
    // Bits 23-31: Unused (9 bits)
    uint data;
};

layout(std430, binding = 0) buffer VoxelBuffer {
    Voxel voxels[]; // Array of all voxels from all chunks
};

// --- Constants for Voxel Data Unpacking ---
const uint LOCAL_X_MASK = 0x000Fu;
const uint LOCAL_Y_MASK = 0x00F0u;
const uint LOCAL_Z_MASK = 0x0F00u;
const uint TEXTURE_ID_MASK = 0x3FF000u; // 10 bits starting at bit 12
const uint IS_SOLID_MASK = 0x400000u; // Bit 22

const int CHUNK_SIZE_X = 16;
const int CHUNK_SIZE_Y = 16;
const int CHUNK_SIZE_Z = 16;
const int CHUNK_TOTAL_VOXELS = CHUNK_SIZE_X * CHUNK_SIZE_Y * CHUNK_SIZE_Z; // 4096

// --- Uniforms for SSBO indexing ---
uniform ivec3 worldMinChunkCoords; // e.g., (-2, 0, -2) from TestScene
uniform ivec3 worldNumChunks;      // e.g., (5, 1, 5) from TestScene (num chunks in X, Y, Z)

Voxel getVoxelFromSSBO(ivec3 worldVoxelCoord) {
    // 1. Convert worldVoxelCoord to chunk coordinates and local voxel coordinates
    ivec3 chunkCoord;
    // Correctly compute chunkCoord for negative worldVoxelCoord values
    chunkCoord.x = (worldVoxelCoord.x < 0) ? (worldVoxelCoord.x - CHUNK_SIZE_X + 1) / CHUNK_SIZE_X : worldVoxelCoord.x / CHUNK_SIZE_X;
    chunkCoord.y = (worldVoxelCoord.y < 0) ? (worldVoxelCoord.y - CHUNK_SIZE_Y + 1) / CHUNK_SIZE_Y : worldVoxelCoord.y / CHUNK_SIZE_Y;
    chunkCoord.z = (worldVoxelCoord.z < 0) ? (worldVoxelCoord.z - CHUNK_SIZE_Z + 1) / CHUNK_SIZE_Z : worldVoxelCoord.z / CHUNK_SIZE_Z;

    ivec3 localCoord = ivec3(
        worldVoxelCoord.x % CHUNK_SIZE_X,
        worldVoxelCoord.y % CHUNK_SIZE_Y,
        worldVoxelCoord.z % CHUNK_SIZE_Z
    );
    // Correct modulo for negative numbers
    localCoord.x = (localCoord.x + CHUNK_SIZE_X) % CHUNK_SIZE_X;
    localCoord.y = (localCoord.y + CHUNK_SIZE_Y) % CHUNK_SIZE_Y;
    localCoord.z = (localCoord.z + CHUNK_SIZE_Z) % CHUNK_SIZE_Z;

    // 2. Convert chunkCoord to a linear chunk index based on worldMinChunkCoords and worldNumChunks
    ivec3 relativeChunkCoord = chunkCoord - worldMinChunkCoords;

    // Check bounds: if outside the chunks we've sent to GPU, return air
    if (relativeChunkCoord.x < 0 || relativeChunkCoord.x >= worldNumChunks.x ||
        relativeChunkCoord.y < 0 || relativeChunkCoord.y >= worldNumChunks.y ||
        relativeChunkCoord.z < 0 || relativeChunkCoord.z >= worldNumChunks.z) {
        Voxel airVoxel; airVoxel.data = 0u; return airVoxel; // Not solid, texture 0
    }

    int chunkIndex = relativeChunkCoord.z * worldNumChunks.x * worldNumChunks.y +
                     relativeChunkCoord.y * worldNumChunks.x +
                     relativeChunkCoord.x;

    int localVoxelIndex = localCoord.z * CHUNK_SIZE_X * CHUNK_SIZE_Y +
                          localCoord.y * CHUNK_SIZE_X +
                          localCoord.x;
    int globalVoxelIndex = chunkIndex * CHUNK_TOTAL_VOXELS + localVoxelIndex;

    if (globalVoxelIndex >= 0 && globalVoxelIndex < voxels.length()) {
        return voxels[globalVoxelIndex];
    }
    Voxel airVoxel; airVoxel.data = 0u; return airVoxel; // Out of bounds
}

bool isVoxelSolid(Voxel v) {
    return (v.data & IS_SOLID_MASK) != 0u;
}

uint getTextureId(Voxel v) {
    return (v.data & TEXTURE_ID_MASK) >> 12;
}

// Amanatides & Woo Voxel Traversal
vec3 raycast(vec3 rayOrigin_world, vec3 rayDir_world, out vec3 hitNormal) {
    ivec3 currentVoxel_world = ivec3(floor(rayOrigin_world));
    vec3 step = sign(rayDir_world);

    vec3 tMax;
    tMax.x = (rayDir_world.x == 0.0) ? 1.0/0.0 : ((floor(rayOrigin_world.x) + max(0.0, step.x)) - rayOrigin_world.x) / rayDir_world.x;
    tMax.y = (rayDir_world.y == 0.0) ? 1.0/0.0 : ((floor(rayOrigin_world.y) + max(0.0, step.y)) - rayOrigin_world.y) / rayDir_world.y;
    tMax.z = (rayDir_world.z == 0.0) ? 1.0/0.0 : ((floor(rayOrigin_world.z) + max(0.0, step.z)) - rayOrigin_world.z) / rayDir_world.z;

    vec3 tDelta = abs(vec3(1.0) / rayDir_world);

    hitNormal = vec3(0.0);
    float maxDist = 200.0; // Max ray travel distance in voxel units
    float currentDist = 0.0;

    for (int i = 0; i < 300; i++) { // Max steps
        Voxel voxel = getVoxelFromSSBO(currentVoxel_world);
        if (isVoxelSolid(voxel)) {
            uint tex_id = getTextureId(voxel);
            if (tex_id == 1u) return vec3(0.0, 0.8, 0.0); // Green
            if (tex_id == 2u) return vec3(0.6, 0.4, 0.2); // Brown
            if (tex_id == 3u) return vec3(0.5, 0.5, 0.5); // Grey
            return vec3(0.8, 0.2, 0.8); // Default solid color (magenta)
        }

        if (tMax.x < tMax.y) {
            if (tMax.x < tMax.z) {
                currentDist = tMax.x;
                currentVoxel_world.x += int(step.x);
                tMax.x += tDelta.x;
                hitNormal = vec3(-step.x, 0.0, 0.0);
            } else {
                currentDist = tMax.z;
                currentVoxel_world.z += int(step.z);
                tMax.z += tDelta.z;
                hitNormal = vec3(0.0, 0.0, -step.z);
            }
        } else {
            if (tMax.y < tMax.z) {
                currentDist = tMax.y;
                currentVoxel_world.y += int(step.y);
                tMax.y += tDelta.y;
                hitNormal = vec3(0.0, -step.y, 0.0);
            } else {
                currentDist = tMax.z;
                currentVoxel_world.z += int(step.z);
                tMax.z += tDelta.z;
                hitNormal = vec3(0.0, 0.0, -step.z);
            }
        }
        if(currentDist > maxDist) break;
    }
    return vec3(0.7, 0.8, 1.0); // Sky color
}

void main() {
    vec2 uv = (fragCoord_v * 2.0 - 1.0);
    uv.x *= iResolution.x / iResolution.y;

    vec3 rayDir_camera = normalize(vec3(uv, 1.5)); // FOV, Z=1.5
    vec3 rayDir_world = cameraToWorldMatrix * rayDir_camera;
    vec3 rayOrigin_world = cameraPos_world;

    vec3 hitNormal;
    vec3 color = raycast(rayOrigin_world, rayDir_world, hitNormal);

    // Optional: Basic lighting
    // if(length(hitNormal) > 0.01) { // Check if normal was set (hit occurred)
    //    vec3 lightDir = normalize(vec3(0.7, 0.7, -0.3)); // Adjusted light direction
    //    float diffuse = max(dot(hitNormal, lightDir), 0.15); // Ambient 0.15
    //    color *= diffuse;
    // }

    FragColor = vec4(color, 1.0);
}
