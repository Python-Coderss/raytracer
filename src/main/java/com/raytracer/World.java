package com.raytracer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.lwjgl.system.MemoryUtil;

public class World {
    // Using ConcurrentHashMap if we plan for multi-threaded chunk access/generation later
    private Map<Integer, Chunk> chunks; // Key is packed chunk ID
    private ByteBuffer packedVoxelDataBufferCache = null; // Cache for the SSBO data
    private Vec3i worldMinChunkCoordsActual = new Vec3i(0,0,0);
    private Vec3i worldNumChunksActual = new Vec3i(0,0,0);


    public World() {
        this.chunks = new ConcurrentHashMap<>();
    }

    public Chunk getChunk(int chunkX, int chunkY, int chunkZ) {
        int chunkId = Chunk.packChunkId(chunkX, chunkY, chunkZ);
        return chunks.get(chunkId);
    }

    public Chunk getOrCreateChunk(int chunkX, int chunkY, int chunkZ) {
        int chunkId = Chunk.packChunkId(chunkX, chunkY, chunkZ);
        return chunks.computeIfAbsent(chunkId, id -> new Chunk(chunkX, chunkY, chunkZ));
    }

    public void addChunk(Chunk chunk) {
        int chunkId = Chunk.packChunkId(chunk.worldX, chunk.worldY, chunk.worldZ);
        chunks.put(chunkId, chunk);
    }

    public void removeChunk(int chunkX, int chunkY, int chunkZ) {
        int chunkId = Chunk.packChunkId(chunkX, chunkY, chunkZ);
        chunks.remove(chunkId);
    }

    public Map<Integer, Chunk> getAllChunks() {
        return new HashMap<>(chunks); // Return a copy to avoid concurrent modification issues if iterating elsewhere
    }

    public Voxel getVoxelAtWorld(float worldX, float worldY, float worldZ) {
        // Determine which chunk the world coordinates fall into
        int chunkX = (int) Math.floor(worldX / Chunk.CHUNK_SIZE_X);
        int chunkY = (int) Math.floor(worldY / Chunk.CHUNK_SIZE_Y);
        int chunkZ = (int) Math.floor(worldZ / Chunk.CHUNK_SIZE_Z);

        Chunk chunk = getChunk(chunkX, chunkY, chunkZ);
        if (chunk == null) {
            return null; // No chunk at this location
        }

        // Determine local voxel coordinates within that chunk
        int localX = (int) Math.floor(worldX) % Chunk.CHUNK_SIZE_X;
        if (localX < 0) localX += Chunk.CHUNK_SIZE_X; // Ensure positive modulo result

        int localY = (int) Math.floor(worldY) % Chunk.CHUNK_SIZE_Y;
        if (localY < 0) localY += Chunk.CHUNK_SIZE_Y;

        int localZ = (int) Math.floor(worldZ) % Chunk.CHUNK_SIZE_Z;
        if (localZ < 0) localZ += Chunk.CHUNK_SIZE_Z;

        return chunk.getVoxel(localX, localY, localZ);
    }

    public void setVoxelAtWorld(float worldX, float worldY, float worldZ, int textureId) {
        int chunkX = (int) Math.floor(worldX / Chunk.CHUNK_SIZE_X);
        int chunkY = (int) Math.floor(worldY / Chunk.CHUNK_SIZE_Y);
        int chunkZ = (int) Math.floor(worldZ / Chunk.CHUNK_SIZE_Z);

        Chunk chunk = getOrCreateChunk(chunkX, chunkY, chunkZ); // Ensure chunk exists

        int localX = (int) Math.floor(worldX) % Chunk.CHUNK_SIZE_X;
        if (localX < 0) localX += Chunk.CHUNK_SIZE_X;

        int localY = (int) Math.floor(worldY) % Chunk.CHUNK_SIZE_Y;
        if (localY < 0) localY += Chunk.CHUNK_SIZE_Y;

        int localZ = (int) Math.floor(worldZ) % Chunk.CHUNK_SIZE_Z;
        if (localZ < 0) localZ += Chunk.CHUNK_SIZE_Z;

        chunk.setVoxel(localX, localY, localZ, textureId);
    }


    // Method to generate a simple test scene
    public void generateTestScene() {
        // Create a flat plane of chunks
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                Chunk chunk = getOrCreateChunk(cx, 0, cz); // Create chunks at y=0
                for (int x = 0; x < Chunk.CHUNK_SIZE_X; x++) {
                    for (int z = 0; z < Chunk.CHUNK_SIZE_Z; z++) {
                        // Make the top layer of these chunks solid (e.g., grass textureId 1)
                        chunk.setVoxel(x, Chunk.CHUNK_SIZE_Y - 1, z, 1);
                        // Maybe some other layers below it (e.g., dirt textureId 2)
                        if (Chunk.CHUNK_SIZE_Y > 1) {
                           chunk.setVoxel(x, Chunk.CHUNK_SIZE_Y - 2, z, 2);
                        }
                         if (Chunk.CHUNK_SIZE_Y > 2) {
                           chunk.setVoxel(x, Chunk.CHUNK_SIZE_Y - 3, z, 2);
                        }
                    }
                }
            }
        }

        // Create a small structure in one chunk
        Chunk centerChunk = getOrCreateChunk(0, 1, 0); // Place it above the plane
        if (centerChunk != null) {
            centerChunk.setVoxel(7, 0, 7, 3); // A single block (e.g., stone textureId 3)
            centerChunk.setVoxel(8, 0, 7, 3);
            centerChunk.setVoxel(7, 1, 7, 3);
            centerChunk.setVoxel(8, 1, 7, 3);
        }
    }

    public ByteBuffer getPackedVoxelDataBuffer() {
        if (packedVoxelDataBufferCache != null) {
            // Important: rewind buffer if it's going to be re-read by glBufferData or similar
            packedVoxelDataBufferCache.rewind();
            return packedVoxelDataBufferCache;
        }

        // Invalidate cache and free old buffer if it exists
        invalidateVoxelDataCache();

        if (this.chunks.isEmpty()) {
            this.worldMinChunkCoordsActual = new Vec3i(0,0,0);
            this.worldNumChunksActual = new Vec3i(0,0,0);
            ByteBuffer emptyBuffer = MemoryUtil.memAlloc(Integer.BYTES); // Min buffer size
            emptyBuffer.putInt(0); // Dummy value
            emptyBuffer.flip();
            this.packedVoxelDataBufferCache = emptyBuffer;
            return emptyBuffer;
        }

        int minCx = Integer.MAX_VALUE, minCy = Integer.MAX_VALUE, minCz = Integer.MAX_VALUE;
        int maxCx = Integer.MIN_VALUE, maxCy = Integer.MIN_VALUE, maxCz = Integer.MIN_VALUE;

        for (int packedId : this.chunks.keySet()) {
            int[] coords = Chunk.unpackChunkId(packedId); // cx, cy, cz
            minCx = Math.min(minCx, coords[0]);
            minCy = Math.min(minCy, coords[1]);
            minCz = Math.min(minCz, coords[2]);
            maxCx = Math.max(maxCx, coords[0]);
            maxCy = Math.max(maxCy, coords[1]);
            maxCz = Math.max(maxCz, coords[2]);
        }

        this.worldMinChunkCoordsActual = new Vec3i(minCx, minCy, minCz);
        this.worldNumChunksActual = new Vec3i(maxCx - minCx + 1, maxCy - minCy + 1, maxCz - minCz + 1);

        List<Integer> packedVoxelDataList = new ArrayList<>();
        // Iterate in ZYX order for chunks, and then ZYX for voxels within chunks
        // This matches the shader's indexing: chunkIndex = z * numX * numY + y * numX + x
        // and localVoxelIndex = lz * sizeX * sizeY + ly * sizeX + lx
        for (int cz = minCz; cz <= maxCz; cz++) {
            for (int cy = minCy; cy <= maxCy; cy++) {
                for (int cx = minCx; cx <= maxCx; cx++) {
                    Chunk chunk = getChunk(cx, cy, cz); // Uses packed ID internally
                    if (chunk != null) {
                        for (int lz = 0; lz < Chunk.CHUNK_SIZE_Z; lz++) {
                            for (int ly = 0; ly < Chunk.CHUNK_SIZE_Y; ly++) {
                                for (int lx = 0; lx < Chunk.CHUNK_SIZE_X; lx++) {
                                    Voxel voxel = chunk.getVoxel(lx, ly, lz);
                                    packedVoxelDataList.add(voxel.getPackedData());
                                }
                            }
                        }
                    } else {
                        // Chunk is missing in the contiguous range, fill with air
                        int airVoxelPacked = 0; // Assuming textureId 0 and isSolid false
                        for (int i = 0; i < Chunk.CHUNK_SIZE_X * Chunk.CHUNK_SIZE_Y * Chunk.CHUNK_SIZE_Z; i++) {
                            packedVoxelDataList.add(airVoxelPacked);
                        }
                    }
                }
            }
        }

        ByteBuffer buffer = MemoryUtil.memAlloc(packedVoxelDataList.size() * Integer.BYTES);
        for (int packedValue : packedVoxelDataList) {
            buffer.putInt(packedValue);
        }
        buffer.flip(); // Prepare for reading by OpenGL

        this.packedVoxelDataBufferCache = buffer;
        return buffer;
    }

    public Vec3i getActualMinChunkCoords() {
        // Ensure getPackedVoxelDataBuffer has been called at least once to calculate this
        // Or, force a calculation if cache is null, but that might have side effects.
        // For now, assume it's valid after getPackedVoxelDataBuffer.
        return worldMinChunkCoordsActual;
    }

    public Vec3i getActualNumChunks() {
        return worldNumChunksActual;
    }

    // Call this if world structure changes to invalidate the cache AND recalculate bounds on next get
    public void invalidateVoxelDataCache() {
        if (this.packedVoxelDataBufferCache != null) {
            MemoryUtil.memFree(this.packedVoxelDataBufferCache);
            this.packedVoxelDataBufferCache = null;
        }
        // Resetting actuals means they will be recalculated by getPackedVoxelDataBuffer
        // Or, we could leave them stale and document that they reflect the last generated buffer.
        // For safety, let's clear them, so a subsequent getActual without getBuffer doesn't give old values.
        // However, this might not be ideal if getActual is called frequently without buffer regen.
        // A better approach might be to make getPackedVoxelDataBuffer the SOLE source of these
        // by returning a wrapper object. For now, this will do.
        // worldMinChunkCoordsActual = new Vec3i(0,0,0);
        // worldNumChunksActual = new Vec3i(0,0,0);
    }

    // Ensure to free the cache when the world is no longer needed
    public void cleanup() {
        invalidateVoxelDataCache();
    }
}
