package com.raytracer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import org.lwjgl.system.MemoryStack; // Added for Matrix3fv

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer; // Added for Matrix3fv
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.lwjgl.opengl.GL20.*;

public class Shader {

    private int programId;
    private int vertexShaderId;
    private int fragmentShaderId;
    private final Map<String, Integer> uniformLocations;

    private Shader(int programId, int vertexShaderId, int fragmentShaderId) {
        this.programId = programId;
        this.vertexShaderId = vertexShaderId;
        this.fragmentShaderId = fragmentShaderId;
        this.uniformLocations = new HashMap<>();
    }

    private static String loadShaderResource(String resourcePath) {
        try (InputStream is = Shader.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Shader resource not found: " + resourcePath);
            }
            try (InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                 BufferedReader reader = new BufferedReader(isr)) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load shader resource: " + resourcePath, e);
        }
    }

    private static int compileShader(String source, int type) {
        int shaderId = glCreateShader(type);
        if (shaderId == 0) {
            throw new RuntimeException("Error creating shader. Type: " + type);
        }

        glShaderSource(shaderId, source);
        glCompileShader(shaderId);

        if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = glGetShaderInfoLog(shaderId, 512);
            glDeleteShader(shaderId);
            throw new RuntimeException("Error compiling shader. Type: " + type + "\nLog: " + log);
        }
        return shaderId;
    }

    public static Shader load(String vertResourcePath, String fragResourcePath) {
        String vertSource = loadShaderResource(vertResourcePath);
        String fragSource = loadShaderResource(fragResourcePath);

        int vertShaderId = compileShader(vertSource, GL_VERTEX_SHADER);
        int fragShaderId = compileShader(fragSource, GL_FRAGMENT_SHADER);

        int programId = glCreateProgram();
        if (programId == 0) {
            throw new RuntimeException("Could not create shader program");
        }

        glAttachShader(programId, vertShaderId);
        glAttachShader(programId, fragShaderId);
        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = glGetProgramInfoLog(programId, 512);
            glDeleteProgram(programId);
            throw new RuntimeException("Error linking shader program.\nLog: " + log);
        }

        // Detach shaders after successful link, they are no longer needed for the program
        // glDetachShader(programId, vertShaderId);
        // glDetachShader(programId, fragShaderId);
        // Note: Some argue to keep them attached for debugging, others to detach.
        // For now, we'll store them to delete them explicitly in cleanup.

        System.out.println("Shader loaded and compiled successfully. Program ID: " + programId);
        return new Shader(programId, vertShaderId, fragShaderId);
    }

    public void bind() {
        glUseProgram(programId);
    }

    public void unbind() {
        glUseProgram(0);
    }

    public void cleanup() {
        unbind();
        if (programId != 0) {
            if (vertexShaderId != 0) {
                glDetachShader(programId, vertexShaderId);
                glDeleteShader(vertexShaderId);
                vertexShaderId = 0;
            }
            if (fragmentShaderId != 0) {
                glDetachShader(programId, fragmentShaderId);
                glDeleteShader(fragmentShaderId);
                fragmentShaderId = 0;
            }
            glDeleteProgram(programId);
            programId = 0;
        }
    }

    private int getUniformLocation(String name) {
        return uniformLocations.computeIfAbsent(name, n -> {
            int location = glGetUniformLocation(programId, n);
            if (location == -1) {
                System.err.println("Warning: Uniform '" + n + "' not found in shader program " + programId);
            }
            return location;
        });
    }

    public void setUniform1f(String name, float value) {
        int location = getUniformLocation(name);
        if (location != -1) {
            glUniform1f(location, value);
        }
    }

    public void setUniform2f(String name, float v0, float v1) {
        int location = getUniformLocation(name);
        if (location != -1) {
            glUniform2f(location, v0, v1);
        }
    }

    public void setUniform3f(String name, float v0, float v1, float v2) {
        int location = getUniformLocation(name);
        if (location != -1) {
            glUniform3f(location, v0, v1, v2);
        }
    }

    public void setUniform3i(String name, int v0, int v1, int v2) {
        int location = getUniformLocation(name);
        if (location != -1) {
            glUniform3i(location, v0, v1, v2);
        }
    }

    public void setUniformMatrix3fv(String name, float[] value) {
        int location = getUniformLocation(name);
        if (location != -1) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                java.nio.FloatBuffer buffer = stack.mallocFloat(9);
                buffer.put(value).flip();
                // false for 'transpose' means the matrix is supplied in column-major order
                glUniformMatrix3fv(location, false, buffer);
            }
        }
    }

    public void setUniformMatrix4fv(String name, FloatBuffer buffer) {
        int location = getUniformLocation(name);
        if (location != -1) {
            // false for 'transpose' means the matrix is supplied in column-major order
            glUniformMatrix4fv(location, false, buffer);
        }
    }
}
