package com.raytracer;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL43; // For SSBO
// GL11 will be imported via static import
//import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil; // For memFree

import java.nio.ByteBuffer; // For ByteBuffer


public class Main {

    private long window;
    private int windowWidth = 1280; // Updated width
    private int windowHeight = 720; // Updated height
    private World world; // World instance
    private int voxelSsboId; // SSBO ID for voxel data
    private Shader shader; // Shader program

    // Camera settings
    private Vec3 cameraPos = new Vec3(2.5f * 16, 2.5f * 16, 2.5f * 16 - 80.0f); // Approx (40, 40, -40)
    private float cameraYaw = 0.0f;   // Rotation around Y axis (degrees)
    private float cameraPitch = -20.0f; // Rotation around X axis (degrees)

    // Mouse control
    private double lastMouseX = windowWidth / 2.0;
    private double lastMouseY = windowHeight / 2.0;
    private boolean firstMouse = true;
    private float mouseSensitivity = 0.1f;

    // Movement speed
    private float cameraSpeed = 10.0f; // World units per second
    private float deltaTime = 0.0f;    // Time between current frame and last frame
    private float lastFrameTime = 0.0f;


    public void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        // Initialize GLFW
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Configure GLFW
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // The window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // The window will be resizable

        // Set OpenGL version to 3.3 Core Profile
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE); // Required for macOS

        // Create the window
        window = glfwCreateWindow(windowWidth, windowHeight, "RayTracer", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        // Setup a key callback. It will be called every time a key is pressed, repeated or released.
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(win, true); // We will detect this in the rendering loop
            }
        });

        // Get the thread stack and push a new frame
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.IntBuffer pWidth = stack.mallocInt(1); // int*
            java.nio.IntBuffer pHeight = stack.mallocInt(1); // int*

            // Get the window size passed to glfwCreateWindow
            glfwGetWindowSize(window, pWidth, pHeight);

            // Get the resolution of the primary monitor
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            // Center the window
            if (vidmode != null) {
                glfwSetWindowPos(
                    window,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2
                );
            }
        } // the stack frame is popped automatically

        // Make the OpenGL context current
        glfwMakeContextCurrent(window);

        // Disable VSync
        glfwSwapInterval(0);

        // Make the window visible
        glfwShowWindow(window);

        // This line is critical for LWJGL's interoperation with GLFW's
        // OpenGL context, or any context that is managed externally.
        // LWJGL detects the context that is current in the current thread,
        // creates the GLCapabilities instance and makes the OpenGL
        // bindings available for use.
        GL.createCapabilities();

        // Set the clear color (black)
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        // Initialize World and generate test scene
        world = new World();
        world.generateTestScene(); // Populate with some data

        // Get packed voxel data
        ByteBuffer voxelDataBuffer = world.getPackedVoxelDataBuffer();

        // Create and populate SSBO for voxel data
        voxelSsboId = GL43.glGenBuffers();
        GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, voxelSsboId);
        // Upload data - GL_STATIC_DRAW if data rarely changes, GL_DYNAMIC_DRAW if it changes often
        GL43.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, voxelDataBuffer, GL43.GL_STATIC_DRAW);
        GL43.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0); // Unbind

        // The world object now owns the buffer if it's cached, so we don't free it here.
        // If the world didn't cache it, or if we created a copy for GL, we would free it:
        // MemoryUtil.memFree(voxelDataBuffer);
        // World.java now handles caching and freeing of this specific buffer via world.cleanup().

        // Set up input mode and callbacks
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            if (firstMouse) {
                lastMouseX = xpos;
                lastMouseY = ypos;
                firstMouse = false;
            }

            float xoffset = (float)(xpos - lastMouseX);
            float yoffset = (float)(lastMouseY - ypos); // Reversed since y-coordinates go from top to bottom
            lastMouseX = xpos;
            lastMouseY = ypos;

            xoffset *= mouseSensitivity;
            yoffset *= mouseSensitivity;

            cameraYaw += xoffset;
            cameraPitch += yoffset;

            // Clamp pitch
            if (cameraPitch > 89.0f) cameraPitch = 89.0f;
            if (cameraPitch < -89.0f) cameraPitch = -89.0f;
        });

        // Load shaders and set static uniforms
        shader = Shader.load("/shaders/voxel.vert", "/shaders/voxel.frag");
        shader.bind();
        shader.setUniform2f("iResolution", (float)windowWidth, (float)windowHeight);

        // Dynamic uniforms for voxel SSBO indexing based on actual world content
        // These are now determined by getPackedVoxelDataBuffer() which should have been called
        // during SSBO creation.
        Vec3i minCoords = world.getActualMinChunkCoords();
        Vec3i numChunks = world.getActualNumChunks();
        shader.setUniform3i("worldMinChunkCoords", minCoords.x(), minCoords.y(), minCoords.z());
        shader.setUniform3i("worldNumChunks", numChunks.x(), numChunks.y(), numChunks.z());

        shader.unbind();
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            // Calculate deltaTime
            float currentFrameTime = (float)glfwGetTime();
            deltaTime = currentFrameTime - lastFrameTime;
            lastFrameTime = currentFrameTime;

            // Process Keyboard Input
            float actualSpeed = cameraSpeed * deltaTime;
            Vec3 forward = new Vec3(
                (float)(Math.cos(Math.toRadians(cameraYaw)) * Math.cos(Math.toRadians(cameraPitch))),
                (float)(Math.sin(Math.toRadians(cameraPitch))),
                (float)(Math.sin(Math.toRadians(cameraYaw)) * Math.cos(Math.toRadians(cameraPitch)))
            ).normalize();
            Vec3 worldUp = new Vec3(0,1,0);
            Vec3 right = forward.cross(worldUp).normalize();
            // Vec3 up = right.cross(forward).normalize(); // For camera-local up movement if needed

            if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
                cameraPos = cameraPos.add(forward.mul(actualSpeed));
            }
            if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
                cameraPos = cameraPos.sub(forward.mul(actualSpeed));
            }
            if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
                cameraPos = cameraPos.sub(right.mul(actualSpeed));
            }
            if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
                cameraPos = cameraPos.add(right.mul(actualSpeed));
            }
            if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) {
                cameraPos = cameraPos.add(worldUp.mul(actualSpeed)); // Fly up (world Y)
            }
            if (glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) {
                cameraPos = cameraPos.sub(worldUp.mul(actualSpeed)); // Fly down (world Y)
            }


            // Bind the SSBO for voxel data to binding point 0
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, voxelSsboId);

            // Clear the framebuffer
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            shader.bind();

            // Set per-frame uniforms
            shader.setUniform1f("iTime", currentFrameTime); // Use currentFrameTime for iTime
            shader.setUniform3f("cameraPos_world", cameraPos.x, cameraPos.y, cameraPos.z);

            // Calculate cameraToWorldMatrix (based on yaw and pitch)
            float cosYaw = (float)Math.cos(Math.toRadians(cameraYaw));
            float sinYaw = (float)Math.sin(Math.toRadians(cameraYaw));
            float cosPitch = (float)Math.cos(Math.toRadians(cameraPitch));
            float sinPitch = (float)Math.sin(Math.toRadians(cameraPitch));

            // Column-Major order for glUniformMatrix3fv
            float[] cameraMatrixCM = new float[]{
                cosYaw,                               0.0f,    -sinYaw,                               // Column 0
                sinYaw * sinPitch,                    cosPitch, cosYaw * sinPitch,                    // Column 1
                sinYaw * cosPitch,                   -sinPitch, cosYaw * cosPitch                     // Column 2
            };
            shader.setUniformMatrix3fv("cameraToWorldMatrix", cameraMatrixCM);

            // Draw a fullscreen triangle (which triggers fragment shader for each pixel)
            glDrawArrays(GL_TRIANGLES, 0, 3);

            shader.unbind();

            glfwSwapBuffers(window); // Swap the color buffers
            glfwPollEvents(); // Poll for window events, keyboard and mouse input
        }

        // Cleanup shader
        if (shader != null) {
            shader.cleanup();
        }

        // Unbind SSBO (optional, good practice)
        GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, 0);
    }

    private void cleanup() {
        // Delete SSBO
        if (voxelSsboId != 0) {
            GL43.glDeleteBuffers(voxelSsboId);
        }

        // Cleanup world resources (like cached ByteBuffer)
        if (world != null) {
            world.cleanup();
        }

        // Free window callbacks and destroy the window
        if (window != NULL) {
            Callbacks.glfwFreeCallbacks(window);
            glfwDestroyWindow(window);
            window = NULL;
        }

        // Terminate GLFW and free the error callback
        glfwTerminate();
        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }
    }

    public static void main(String[] args) {
        new Main().run();
    }
}
