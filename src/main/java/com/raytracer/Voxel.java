package com.raytracer;

public class Voxel {
    public byte localX, localY, localZ; // Position within the chunk (0-15)
    public int textureId; // Texture ID for this voxel
    public int chunkId;   // ID of the chunk this voxel belongs to (could be an index or a packed world pos)

    public Voxel(byte localX, byte localY, byte localZ, int textureId, int chunkId) {
        this.localX = localX;
        this.localY = localY;
        this.localZ = localZ;
        this.textureId = textureId;
        this.chunkId = chunkId;
    }

    // Getters might be useful later
    public int getTextureId() {
        return textureId;
    }

    public boolean isSolid() {
        // Define what makes a voxel solid, e.g., textureId 0 is air
        return textureId != 0;
    }

    public int getPackedData() {
        int packed = 0;
        packed |= (this.localX & 0xF);          // Bits 0-3: Local X (0-15)
        packed |= ((this.localY & 0xF) << 4);   // Bits 4-7: Local Y (0-15)
        packed |= ((this.localZ & 0xF) << 8);   // Bits 8-11: Local Z (0-15)
        // For textureId, we need to decide how many bits. If it's an index into a texture array/atlas.
        // The spec says "10 bits for texture ID (0-1023)".
        packed |= ((this.textureId & 0x3FF) << 12); // Bits 12-21: Texture ID (0-1023)

        // Bit 22: isSolid flag. Could be derived from textureId (e.g., textureId 0 is air)
        // or be an explicit flag. If derived, this bit is redundant but can be a shortcut in shader.
        // Let's make it explicit based on isSolid() for clarity in shader.
        if (this.isSolid()) {
            packed |= (1 << 22); // Bit 22: isSolid flag
        }

        // Bits 23-31 are currently unused. Could be used for other material properties, light levels, etc.
        // Or, the chunk ID could be implicitly known by the shader if we send one SSBO per chunk,
        // or if the shader calculates which chunk a ray is in.
        // The spec mentions "8 bits for chunk X, 8 bits for chunk Y, 8 bits for chunk Z (world position of chunk)".
        // This implies that the chunk coordinates should also be part of the voxel data if sent in one large buffer.
        // However, the current getPackedData is on Voxel.java which already has a chunkId field.
        // The current task is to pack *voxel* data, the SSBO might contain an array of these.
        // The shader would then also need chunk data (e.g., an array of chunk AABBs and offsets into the voxel SSBO).
        // For now, let's stick to the spec for Voxel's own data, assuming chunk context is handled separately or implicitly.
        // The prompt's Voxel.getPackedData() does not include chunkId, this seems correct for a "Voxel" structure.
        // The `chunkId` field in `Voxel.java` is for CPU-side logic if needed.
        return packed;
    }
}
