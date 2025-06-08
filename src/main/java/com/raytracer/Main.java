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
// GL11 will be imported via static import
//import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;


public class Main {

    private long window;
    private int windowWidth = 1280; // Updated width
    private int windowHeight = 720; // Updated height

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
    }

    private void loop() {
        // Load and compile shaders
        // Shader paths are relative to the resources directory, ensure they start with /
        Shader shader = Shader.load("/shaders/raytrace.vert", "/shaders/raytrace.frag");

        shader.bind();
        shader.setUniform2f("iResolution", (float)windowWidth, (float)windowHeight);
        shader.unbind();

        while (!glfwWindowShouldClose(window)) {
            // Clear the framebuffer
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            shader.bind();

            // Set time uniform for animations
            shader.setUniform1f("iTime", (float)glfwGetTime());
            // If window were resizable and you wanted to update resolution dynamically:
            // int[] w = new int[1], h = new int[1];
            // glfwGetFramebufferSize(window, w, h);
            // shader.setUniform2f("iResolution", (float)w[0], (float)h[0]);

            // Draw a fullscreen triangle
            glDrawArrays(GL_TRIANGLES, 0, 3);

            shader.unbind();

            glfwSwapBuffers(window); // Swap the color buffers
            glfwPollEvents(); // Poll for window events, keyboard and mouse input
        }

        // Cleanup shader
        if (shader != null) {
            shader.cleanup();
        }
    }

    private void cleanup() {
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
