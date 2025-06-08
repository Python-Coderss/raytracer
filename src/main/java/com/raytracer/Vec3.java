package com.raytracer;

public class Vec3 {
    public float x, y, z;

    // Constructors
    public Vec3() {
        this.x = 0;
        this.y = 0;
        this.z = 0;
    }

    public Vec3(float scalar) {
        this.x = scalar;
        this.y = scalar;
        this.z = scalar;
    }

    public Vec3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    // Vector operations
    public Vec3 add(Vec3 other) {
        return new Vec3(this.x + other.x, this.y + other.y, this.z + other.z);
    }

    public Vec3 sub(Vec3 other) {
        return new Vec3(this.x - other.x, this.y - other.y, this.z - other.z);
    }

    public Vec3 mul(float scalar) {
        return new Vec3(this.x * scalar, this.y * scalar, this.z * scalar);
    }

    public Vec3 mul(Vec3 other) { // Component-wise multiplication
        return new Vec3(this.x * other.x, this.y * other.y, this.z * other.z);
    }

    public float dot(Vec3 other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }

    public float lengthSquared() {
        return this.x * this.x + this.y * this.y + this.z * this.z;
    }

    public float length() {
        return (float) Math.sqrt(lengthSquared());
    }

    public Vec3 normalize() {
        float len = length();
        if (len > 0.00001f) { // Avoid division by zero
            return new Vec3(this.x / len, this.y / len, this.z / len);
        }
        return new Vec3(0, 0, 0); // Or throw an exception, or return this
    }

    // Static utility methods
    public static Vec3 min(Vec3 a, Vec3 b) {
        return new Vec3(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z));
    }

    public static Vec3 max(Vec3 a, Vec3 b) {
        return new Vec3(Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z));
    }

    @Override
    public String toString() {
        return "Vec3(" + x + ", " + y + ", " + z + ")";
    }
}
