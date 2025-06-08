package com.raytracer;

public class Chunk {
    public static final int CHUNK_SIZE_X = 16;
    public static final int CHUNK_SIZE_Y = 16;
    public static final int CHUNK_SIZE_Z = 16;

    private Voxel[][][] voxels; // [x][y][z]
    public int worldX, worldY, worldZ; // Chunk's position in world units (multiples of CHUNK_SIZE)
                                     // These are the 8-bit components from the spec.

    public Chunk(int worldX, int worldY, int worldZ) {
        this.worldX = worldX; // This is the chunk's grid coordinate
        this.worldY = worldY;
        this.worldZ = worldZ;
        this.voxels = new Voxel[CHUNK_SIZE_X][CHUNK_SIZE_Y][CHUNK_SIZE_Z];
        // Initialize with air or default voxels
        int currentChunkId = packChunkId(worldX, worldY, worldZ);
        for (byte x = 0; x < CHUNK_SIZE_X; x++) {
            for (byte y = 0; y < CHUNK_SIZE_Y; y++) {
                for (byte z = 0; z < CHUNK_SIZE_Z; z++) {
                    // Default to air (textureId 0)
                    voxels[x][y][z] = new Voxel(x, y, z, 0, currentChunkId);
                }
            }
        }
    }

    public static int packChunkId(int cx, int cy, int cz) {
        // Mask to ensure they are within 8-bit range if not already
        return ((cx & 0xFF) << 16) | ((cy & 0xFF) << 8) | (cz & 0xFF);
    }

    public static int[] unpackChunkId(int chunkId) {
        int cx = (chunkId >> 16) & 0xFF;
        int cy = (chunkId >> 8) & 0xFF;
        int cz = chunkId & 0xFF;
        return new int[]{cx, cy, cz};
    }


    public Voxel getVoxel(int localX, int localY, int localZ) {
        if (localX < 0 || localX >= CHUNK_SIZE_X ||
            localY < 0 || localY >= CHUNK_SIZE_Y ||
            localZ < 0 || localZ >= CHUNK_SIZE_Z) {
            return null; // Or throw exception
        }
        return voxels[localX][localY][localZ];
    }

    public void setVoxel(int localX, int localY, int localZ, int textureId) {
        if (localX < 0 || localX >= CHUNK_SIZE_X ||
            localY < 0 || localY >= CHUNK_SIZE_Y ||
            localZ < 0 || localZ >= CHUNK_SIZE_Z) {
            return; // Or throw exception
        }
        // The chunkId for the voxel should be this chunk's ID
        voxels[localX][localY][localZ] = new Voxel((byte)localX, (byte)localY, (byte)localZ, textureId, packChunkId(this.worldX, this.worldY, this.worldZ));
    }

    public AABB getAABB() {
        // Calculate world coordinates of the chunk's corners
        float minX = worldX * CHUNK_SIZE_X;
        float minY = worldY * CHUNK_SIZE_Y;
        float minZ = worldZ * CHUNK_SIZE_Z;
        // Max corner is exclusive for typical iteration, but inclusive for AABB bounds
        float maxX = minX + CHUNK_SIZE_X;
        float maxY = minY + CHUNK_SIZE_Y;
        float maxZ = minZ + CHUNK_SIZE_Z;
        return new AABB(new Vec3(minX, minY, minZ), new Vec3(maxX, maxY, maxZ));
    }
}
