package org.dynamislight.impl.opengl;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL15.glGetQueryObjecti;
import static org.lwjgl.opengl.GL20.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL20.glAttachShader;
import static org.lwjgl.opengl.GL20.glCompileShader;
import static org.lwjgl.opengl.GL20.glCreateProgram;
import static org.lwjgl.opengl.GL20.glCreateShader;
import static org.lwjgl.opengl.GL20.glDeleteProgram;
import static org.lwjgl.opengl.GL20.glDeleteShader;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20.glGetProgrami;
import static org.lwjgl.opengl.GL20.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20.glGetShaderi;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glLinkProgram;
import static org.lwjgl.opengl.GL20.glShaderSource;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniform3f;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL33.GL_TIME_ELAPSED;
import static org.lwjgl.opengl.GL33.glBeginQuery;
import static org.lwjgl.opengl.GL33.glDeleteQueries;
import static org.lwjgl.opengl.GL33.glEndQuery;
import static org.lwjgl.opengl.GL33.glGenQueries;
import static org.lwjgl.opengl.GL33.glGetQueryObjecti64;
import static org.lwjgl.opengl.GL33.GL_QUERY_RESULT;
import static org.lwjgl.opengl.GL33.GL_QUERY_RESULT_AVAILABLE;

import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

final class OpenGlContext {
    private static final String VERTEX_SHADER = """
            #version 330 core
            layout (location = 0) in vec2 aPos;
            layout (location = 1) in vec3 aColor;
            out vec3 vColor;
            out float vHeight;
            void main() {
                vColor = aColor;
                vHeight = aPos.y;
                gl_Position = vec4(aPos, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 330 core
            in vec3 vColor;
            in float vHeight;
            uniform int uFogEnabled;
            uniform vec3 uFogColor;
            uniform float uFogDensity;
            uniform int uFogSteps;
            uniform int uSmokeEnabled;
            uniform vec3 uSmokeColor;
            uniform float uSmokeIntensity;
            out vec4 FragColor;
            void main() {
                vec3 color = vColor;
                if (uFogEnabled == 1) {
                    float normalizedHeight = clamp((vHeight + 1.0) * 0.5, 0.0, 1.0);
                    float fogFactor = clamp(exp(-uFogDensity * (1.0 - normalizedHeight)), 0.0, 1.0);
                    if (uFogSteps > 0) {
                        fogFactor = floor(fogFactor * float(uFogSteps)) / float(uFogSteps);
                    }
                    color = mix(uFogColor, color, fogFactor);
                }
                if (uSmokeEnabled == 1) {
                    float radial = clamp(1.0 - length(gl_FragCoord.xy / vec2(1920.0, 1080.0) - vec2(0.5)), 0.0, 1.0);
                    float smokeFactor = clamp(uSmokeIntensity * (0.35 + radial * 0.65), 0.0, 0.85);
                    color = mix(color, uSmokeColor, smokeFactor);
                }
                FragColor = vec4(color, 1.0);
            }
            """;

    private long window;
    private int width;
    private int height;
    private int programId;
    private int vaoId;
    private int vboId;
    private int fogEnabledLocation;
    private int fogColorLocation;
    private int fogDensityLocation;
    private int fogStepsLocation;
    private int smokeEnabledLocation;
    private int smokeColorLocation;
    private int smokeIntensityLocation;
    private boolean fogEnabled;
    private float fogR = 0.5f;
    private float fogG = 0.5f;
    private float fogB = 0.5f;
    private float fogDensity;
    private int fogSteps;
    private boolean smokeEnabled;
    private float smokeR = 0.6f;
    private float smokeG = 0.6f;
    private float smokeB = 0.6f;
    private float smokeIntensity;
    private boolean gpuTimerQuerySupported;
    private int gpuTimeQueryId;
    private double lastGpuFrameMs;

    void initialize(String appName, int width, int height, boolean vsyncEnabled, boolean windowVisible) throws EngineException {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!GLFW.glfwInit()) {
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "GLFW initialization failed", false);
        }

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, windowVisible ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);

        window = GLFW.glfwCreateWindow(width, height, appName, 0, 0);
        if (window == 0) {
            GLFW.glfwTerminate();
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "Failed to create OpenGL window/context", false);
        }

        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwSwapInterval(vsyncEnabled ? 1 : 0);
        GL.createCapabilities();

        this.width = width;
        this.height = height;
        glViewport(0, 0, width, height);

        initializeTrianglePipeline();
        initializeGpuQuerySupport();
    }

    void resize(int width, int height) {
        this.width = width;
        this.height = height;
        glViewport(0, 0, width, height);
    }

    OpenGlFrameMetrics renderFrame() {
        if (window == 0) {
            return new OpenGlFrameMetrics(0.0, 0.0, 0, 0, 0, 0);
        }

        long startNs = System.nanoTime();

        beginFrame();
        renderClearPass();
        renderGeometryPass();
        renderFogPass();
        renderSmokePass();
        endFrame();

        double cpuMs = (System.nanoTime() - startNs) / 1_000_000.0;
        return new OpenGlFrameMetrics(cpuMs, cpuMs, 2, 1, 1, 0);
    }

    void beginFrame() {
        glViewport(0, 0, width, height);
        if (gpuTimerQuerySupported) {
            glBeginQuery(GL_TIME_ELAPSED, gpuTimeQueryId);
        }
    }

    void renderClearPass() {
        glClearColor(0.08f, 0.09f, 0.12f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    void renderGeometryPass() {
        glUseProgram(programId);
        applyFogUniforms();
        applySmokeUniforms();
        glBindVertexArray(vaoId);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);
        glUseProgram(0);
    }

    void renderFogPass() {
        // Fog is currently applied in the fragment shader during geometry pass.
    }

    void renderSmokePass() {
        // Smoke is currently applied in the fragment shader during geometry pass.
    }

    void endFrame() {
        if (gpuTimerQuerySupported) {
            glEndQuery(GL_TIME_ELAPSED);
            if (glGetQueryObjecti(gpuTimeQueryId, GL_QUERY_RESULT_AVAILABLE) == 1) {
                long ns = glGetQueryObjecti64(gpuTimeQueryId, GL_QUERY_RESULT);
                lastGpuFrameMs = ns / 1_000_000.0;
            }
        }
        GLFW.glfwSwapBuffers(window);
        GLFW.glfwPollEvents();
    }

    void shutdown() {
        if (vboId != 0) {
            glDeleteBuffers(vboId);
            vboId = 0;
        }
        if (vaoId != 0) {
            glDeleteVertexArrays(vaoId);
            vaoId = 0;
        }
        if (programId != 0) {
            glDeleteProgram(programId);
            programId = 0;
        }
        if (gpuTimeQueryId != 0) {
            glDeleteQueries(gpuTimeQueryId);
            gpuTimeQueryId = 0;
        }

        if (window != 0) {
            GLFW.glfwDestroyWindow(window);
            window = 0;
        }
        GLFW.glfwTerminate();

        GLFWErrorCallback callback = GLFW.glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }
    }

    void setFogParameters(boolean enabled, float r, float g, float b, float density, int steps) {
        fogEnabled = enabled;
        fogR = r;
        fogG = g;
        fogB = b;
        fogDensity = Math.max(0f, density);
        fogSteps = Math.max(0, steps);
    }

    void setSmokeParameters(boolean enabled, float r, float g, float b, float intensity) {
        smokeEnabled = enabled;
        smokeR = r;
        smokeG = g;
        smokeB = b;
        smokeIntensity = Math.max(0f, Math.min(1f, intensity));
    }

    double lastGpuFrameMs() {
        return lastGpuFrameMs;
    }

    private void initializeTrianglePipeline() throws EngineException {
        int vertexShaderId = compileShader(GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShaderId = compileShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);

        programId = glCreateProgram();
        glAttachShader(programId, vertexShaderId);
        glAttachShader(programId, fragmentShaderId);
        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == 0) {
            String info = glGetProgramInfoLog(programId);
            glDeleteShader(vertexShaderId);
            glDeleteShader(fragmentShaderId);
            throw new EngineException(EngineErrorCode.SHADER_COMPILATION_FAILED, "Shader link failed: " + info, false);
        }

        glDeleteShader(vertexShaderId);
        glDeleteShader(fragmentShaderId);
        fogEnabledLocation = glGetUniformLocation(programId, "uFogEnabled");
        fogColorLocation = glGetUniformLocation(programId, "uFogColor");
        fogDensityLocation = glGetUniformLocation(programId, "uFogDensity");
        fogStepsLocation = glGetUniformLocation(programId, "uFogSteps");
        smokeEnabledLocation = glGetUniformLocation(programId, "uSmokeEnabled");
        smokeColorLocation = glGetUniformLocation(programId, "uSmokeColor");
        smokeIntensityLocation = glGetUniformLocation(programId, "uSmokeIntensity");

        float[] vertices = {
                // x, y, r, g, b
                -0.6f, -0.4f, 1.0f, 0.2f, 0.2f,
                0.6f, -0.4f, 0.2f, 1.0f, 0.2f,
                0.0f, 0.6f, 0.2f, 0.3f, 1.0f
        };

        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 2, GL_FLOAT, false, 5 * Float.BYTES, 0L);
        glEnableVertexAttribArray(0);

        glVertexAttribPointer(1, 3, GL_FLOAT, false, 5 * Float.BYTES, 2L * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    private static int compileShader(int type, String source) throws EngineException {
        int shaderId = glCreateShader(type);
        glShaderSource(shaderId, source);
        glCompileShader(shaderId);

        if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == 0) {
            String info = glGetShaderInfoLog(shaderId);
            glDeleteShader(shaderId);
            throw new EngineException(EngineErrorCode.SHADER_COMPILATION_FAILED, "Shader compilation failed: " + info, false);
        }

        return shaderId;
    }

    private void applyFogUniforms() {
        glUniform1i(fogEnabledLocation, fogEnabled ? 1 : 0);
        glUniform3f(fogColorLocation, fogR, fogG, fogB);
        glUniform1f(fogDensityLocation, fogDensity);
        glUniform1i(fogStepsLocation, fogSteps);
    }

    private void applySmokeUniforms() {
        glUniform1i(smokeEnabledLocation, smokeEnabled ? 1 : 0);
        glUniform3f(smokeColorLocation, smokeR, smokeG, smokeB);
        glUniform1f(smokeIntensityLocation, smokeIntensity);
    }

    private void initializeGpuQuerySupport() {
        var caps = GL.getCapabilities();
        gpuTimerQuerySupported = caps.OpenGL33 || caps.GL_ARB_timer_query;
        if (gpuTimerQuerySupported) {
            gpuTimeQueryId = glGenQueries();
        }
    }

    record OpenGlFrameMetrics(
            double cpuFrameMs,
            double gpuFrameMs,
            long drawCalls,
            long triangles,
            long visibleObjects,
            long gpuMemoryBytes
    ) {
    }
}
