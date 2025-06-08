package com.raytracer;

public class AABB {
    public Vec3 min;
    public Vec3 max;

    public AABB(Vec3 min, Vec3 max) {
        this.min = min;
        this.max = max;
    }

    // Ray-AABB intersection test (Slab method)
    public boolean intersectRay(Vec3 rayOrigin, Vec3 invRayDirection, float tMinGlobal, float tMaxGlobal) {
        // invRayDirection is 1.0f / rayDirection.x, 1.0f / rayDirection.y, etc.
        // This pre-computation is often done by the caller.

        float tx1 = (this.min.x - rayOrigin.x) * invRayDirection.x;
        float tx2 = (this.max.x - rayOrigin.x) * invRayDirection.x;

        float tmin = Math.min(tx1, tx2);
        float tmax = Math.max(tx1, tx2);

        float ty1 = (this.min.y - rayOrigin.y) * invRayDirection.y;
        float ty2 = (this.max.y - rayOrigin.y) * invRayDirection.y;

        tmin = Math.max(tmin, Math.min(ty1, ty2));
        tmax = Math.min(tmax, Math.max(ty1, ty2));

        float tz1 = (this.min.z - rayOrigin.z) * invRayDirection.z;
        float tz2 = (this.max.z - rayOrigin.z) * invRayDirection.z;

        tmin = Math.max(tmin, Math.min(tz1, tz2));
        tmax = Math.min(tmax, Math.max(tz1, tz2));

        // Intersection if tmax >= tmin and the intersection interval overlaps [tMinGlobal, tMaxGlobal]
        return tmax >= tmin && tmax >= tMinGlobal && tmin <= tMaxGlobal;
    }

    // Overload for convenience if inverse direction is not precomputed
    public boolean intersectRay(Vec3 rayOrigin, Vec3 rayDirection, float tMinGlobal, float tMaxGlobal) {
        // Handle cases where rayDirection components are zero to avoid division by zero.
        // A small epsilon can be used, or logic to handle axis-parallel rays.
        // For simplicity here, we assume non-zero components or that the caller handles it.
        // A more robust implementation would add epsilon checks or use a different formulation for axis-parallel rays.
        Vec3 invRayDirection = new Vec3(
            1.0f / rayDirection.x,
            1.0f / rayDirection.y,
            1.0f / rayDirection.z
        );
        // Check for NaN or Infinity if rayDirection components are zero
        if (Float.isInfinite(invRayDirection.x) || Float.isNaN(invRayDirection.x) ||
            Float.isInfinite(invRayDirection.y) || Float.isNaN(invRayDirection.y) ||
            Float.isInfinite(invRayDirection.z) || Float.isNaN(invRayDirection.z)) {
            // This simplistic check might need refinement for specific edge cases of axis-aligned rays
            // For a truly robust solution with axis-parallel rays, one might check if the origin is within the slab for that axis.
            // For now, this version relies on the fact that if a component of rayDirection is 0,
            // the corresponding invRayDirection component will be Infinity.
            // The slab test should still mostly work due to how min/max with Infinity behaves,
            // but careful consideration of edge cases (e.g. ray origin inside the box on an axis-aligned path) is needed for full robustness.
        }

        return intersectRay(rayOrigin, invRayDirection, tMinGlobal, tMaxGlobal);
    }


    public static AABB fromSphere(Vec3 center, float radius) {
        Vec3 radiusVec = new Vec3(radius, radius, radius);
        return new AABB(center.sub(radiusVec), center.add(radiusVec));
    }

    // Method to combine two AABBs (useful for BVH construction)
    public static AABB combine(AABB box1, AABB box2) {
        Vec3 minCombined = Vec3.min(box1.min, box2.min);
        Vec3 maxCombined = Vec3.max(box1.max, box2.max);
        return new AABB(minCombined, maxCombined);
    }

    // Method to calculate the surface area of the AABB (useful for SAH in BVH construction)
    public float surfaceArea() {
        Vec3 d = max.sub(min);
        return 2.0f * (d.x * d.y + d.x * d.z + d.y * d.z);
    }

    @Override
    public String toString() {
        return "AABB(min=" + min + ", max=" + max + ")";
    }
}
