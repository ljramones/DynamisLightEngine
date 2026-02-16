package org.dynamislight.impl.opengl;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
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
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL33.GL_QUERY_RESULT;
import static org.lwjgl.opengl.GL33.GL_QUERY_RESULT_AVAILABLE;
import static org.lwjgl.opengl.GL33.GL_TIME_ELAPSED;
import static org.lwjgl.opengl.GL33.glBeginQuery;
import static org.lwjgl.opengl.GL33.glDeleteQueries;
import static org.lwjgl.opengl.GL33.glEndQuery;
import static org.lwjgl.opengl.GL33.glGenQueries;
import static org.lwjgl.opengl.GL33.glGetQueryObjecti64;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.dynamislight.api.error.EngineErrorCode;
import org.dynamislight.api.error.EngineException;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

final class OpenGlContext {
    static record MeshGeometry(float[] vertices) {
        MeshGeometry {
            if (vertices == null || vertices.length == 0 || vertices.length % 6 != 0) {
                throw new IllegalArgumentException("Mesh vertices must be non-empty and packed as x,y,z,r,g,b");
            }
        }

        int vertexCount() {
            return vertices.length / 6;
        }
    }

    static record SceneMesh(
            MeshGeometry geometry,
            float[] modelMatrix,
            float[] albedoColor,
            float metallic,
            float roughness,
            Path albedoTexturePath,
            Path normalTexturePath
    ) {
        SceneMesh {
            if (geometry == null) {
                throw new IllegalArgumentException("geometry is required");
            }
            if (modelMatrix == null || modelMatrix.length != 16) {
                throw new IllegalArgumentException("modelMatrix must be 16 floats");
            }
            if (albedoColor == null || albedoColor.length != 3) {
                throw new IllegalArgumentException("albedoColor must be 3 floats");
            }
        }
    }

    private static final class MeshBuffer {
        private final int vaoId;
        private final int vboId;
        private final int vertexCount;
        private final float[] modelMatrix;
        private final float[] albedoColor;
        private final float metallic;
        private final float roughness;
        private final int textureId;
        private final int normalTextureId;

        private MeshBuffer(
                int vaoId,
                int vboId,
                int vertexCount,
                float[] modelMatrix,
                float[] albedoColor,
                float metallic,
                float roughness,
                int textureId,
                int normalTextureId
        ) {
            this.vaoId = vaoId;
            this.vboId = vboId;
            this.vertexCount = vertexCount;
            this.modelMatrix = modelMatrix;
            this.albedoColor = albedoColor;
            this.metallic = metallic;
            this.roughness = roughness;
            this.textureId = textureId;
            this.normalTextureId = normalTextureId;
        }
    }

    private static final String VERTEX_SHADER = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec3 aColor;
            uniform mat4 uModel;
            uniform mat4 uView;
            uniform mat4 uProj;
            out vec3 vColor;
            out vec3 vWorldPos;
            out float vHeight;
            out vec2 vUv;
            void main() {
                vec4 world = uModel * vec4(aPos, 1.0);
                vColor = aColor;
                vWorldPos = world.xyz;
                vHeight = world.y;
                vUv = aPos.xy * 0.5 + vec2(0.5);
                gl_Position = uProj * uView * world;
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 330 core
            in vec3 vColor;
            in vec3 vWorldPos;
            in float vHeight;
            in vec2 vUv;
            uniform vec3 uMaterialAlbedo;
            uniform float uMaterialMetallic;
            uniform float uMaterialRoughness;
            uniform int uUseAlbedoTexture;
            uniform sampler2D uAlbedoTexture;
            uniform int uUseNormalTexture;
            uniform sampler2D uNormalTexture;
            uniform vec3 uDirLightDir;
            uniform vec3 uDirLightColor;
            uniform float uDirLightIntensity;
            uniform vec3 uPointLightPos;
            uniform vec3 uPointLightColor;
            uniform float uPointLightIntensity;
            uniform int uFogEnabled;
            uniform vec3 uFogColor;
            uniform float uFogDensity;
            uniform int uFogSteps;
            uniform int uSmokeEnabled;
            uniform vec3 uSmokeColor;
            uniform float uSmokeIntensity;
            out vec4 FragColor;
            float distributionGGX(float ndh, float roughness) {
                float a = roughness * roughness;
                float a2 = a * a;
                float d = (ndh * ndh) * (a2 - 1.0) + 1.0;
                return a2 / max(3.14159 * d * d, 0.0001);
            }
            float geometrySchlickGGX(float ndv, float roughness) {
                float r = roughness + 1.0;
                float k = (r * r) / 8.0;
                return ndv / max(ndv * (1.0 - k) + k, 0.0001);
            }
            float geometrySmith(float ndv, float ndl, float roughness) {
                return geometrySchlickGGX(ndv, roughness) * geometrySchlickGGX(ndl, roughness);
            }
            vec3 fresnelSchlick(float cosTheta, vec3 f0) {
                return f0 + (1.0 - f0) * pow(1.0 - cosTheta, 5.0);
            }
            void main() {
                vec3 albedo = vColor * uMaterialAlbedo;
                vec3 normal = vec3(0.0, 0.0, 1.0);
                if (uUseAlbedoTexture == 1) {
                    vec3 tex = texture(uAlbedoTexture, vUv).rgb;
                    albedo *= tex;
                }
                if (uUseNormalTexture == 1) {
                    vec3 ntex = texture(uNormalTexture, vUv).rgb * 2.0 - 1.0;
                    normal = normalize(mix(normal, ntex, 0.55));
                }
                float metallic = clamp(uMaterialMetallic, 0.0, 1.0);
                float roughness = clamp(uMaterialRoughness, 0.04, 1.0);
                vec3 lDir = normalize(-uDirLightDir);
                vec3 viewDir = normalize(vec3(0.0, 0.0, 1.0));
                vec3 halfVec = normalize(lDir + viewDir);
                float ndl = max(dot(normal, lDir), 0.0);
                float ndv = max(dot(normal, viewDir), 0.0);
                float ndh = max(dot(normal, halfVec), 0.0);
                float vdh = max(dot(viewDir, halfVec), 0.0);
                vec3 f0 = mix(vec3(0.04), albedo, metallic);
                vec3 f = fresnelSchlick(vdh, f0);
                float d = distributionGGX(ndh, roughness);
                float g = geometrySmith(ndv, ndl, roughness);
                vec3 numerator = d * g * f;
                float denominator = max(4.0 * ndv * ndl, 0.0001);
                vec3 specular = numerator / denominator;
                vec3 kd = (1.0 - f) * (1.0 - metallic);
                vec3 diffuse = kd * albedo / 3.14159;
                vec3 directional = (diffuse + specular) * uDirLightColor * (ndl * uDirLightIntensity);

                vec3 pDir = normalize(uPointLightPos - vWorldPos);
                float pNdl = max(dot(normal, pDir), 0.0);
                float dist = max(length(uPointLightPos - vWorldPos), 0.1);
                float attenuation = 1.0 / (1.0 + 0.35 * dist + 0.1 * dist * dist);
                vec3 pointLit = (kd * albedo / 3.14159) * uPointLightColor * (pNdl * attenuation * uPointLightIntensity);
                vec3 ambient = (0.08 + 0.1 * (1.0 - roughness)) * albedo;
                vec3 color = ambient + directional + pointLit;
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
    private final List<MeshBuffer> sceneMeshes = new ArrayList<>();
    private int modelLocation;
    private int viewLocation;
    private int projLocation;
    private int materialAlbedoLocation;
    private int materialMetallicLocation;
    private int materialRoughnessLocation;
    private int useAlbedoTextureLocation;
    private int albedoTextureLocation;
    private int useNormalTextureLocation;
    private int normalTextureLocation;
    private int dirLightDirLocation;
    private int dirLightColorLocation;
    private int dirLightIntensityLocation;
    private int pointLightPosLocation;
    private int pointLightColorLocation;
    private int pointLightIntensityLocation;
    private int fogEnabledLocation;
    private int fogColorLocation;
    private int fogDensityLocation;
    private int fogStepsLocation;
    private int smokeEnabledLocation;
    private int smokeColorLocation;
    private int smokeIntensityLocation;
    private float[] viewMatrix = identityMatrix();
    private float[] projMatrix = identityMatrix();
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
    private float dirLightDirX = 0.3f;
    private float dirLightDirY = -1.0f;
    private float dirLightDirZ = 0.25f;
    private float dirLightColorR = 1.0f;
    private float dirLightColorG = 0.98f;
    private float dirLightColorB = 0.95f;
    private float dirLightIntensity = 1.0f;
    private float pointLightPosX = 0.0f;
    private float pointLightPosY = 1.2f;
    private float pointLightPosZ = 1.8f;
    private float pointLightColorR = 0.95f;
    private float pointLightColorG = 0.62f;
    private float pointLightColorB = 0.22f;
    private float pointLightIntensity = 1.0f;
    private boolean gpuTimerQuerySupported;
    private int gpuTimeQueryId;
    private double lastGpuFrameMs;
    private long lastDrawCalls;
    private long lastTriangles;
    private long lastVisibleObjects;

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
        glEnable(GL_DEPTH_TEST);

        this.width = width;
        this.height = height;
        glViewport(0, 0, width, height);

        initializeShaderPipeline();
        initializeGpuQuerySupport();
        setSceneMeshes(List.of(new SceneMesh(defaultTriangleGeometry(), identityMatrix(), new float[]{1f, 1f, 1f}, 0.0f, 0.6f, null, null)));
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
        double gpuMs = lastGpuFrameMs > 0.0 ? lastGpuFrameMs : cpuMs;
        return new OpenGlFrameMetrics(cpuMs, gpuMs, lastDrawCalls, lastTriangles, lastVisibleObjects, 0);
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
        glUniformMatrix4fv(viewLocation, false, viewMatrix);
        glUniformMatrix4fv(projLocation, false, projMatrix);
        lastDrawCalls = 0;
        lastTriangles = 0;
        lastVisibleObjects = sceneMeshes.size();
        for (MeshBuffer mesh : sceneMeshes) {
            glUniformMatrix4fv(modelLocation, false, mesh.modelMatrix);
            glUniform3f(materialAlbedoLocation, mesh.albedoColor[0], mesh.albedoColor[1], mesh.albedoColor[2]);
            glUniform1f(materialMetallicLocation, mesh.metallic);
            glUniform1f(materialRoughnessLocation, mesh.roughness);
            glUniform3f(dirLightDirLocation, dirLightDirX, dirLightDirY, dirLightDirZ);
            glUniform3f(dirLightColorLocation, dirLightColorR, dirLightColorG, dirLightColorB);
            glUniform1f(dirLightIntensityLocation, dirLightIntensity);
            glUniform3f(pointLightPosLocation, pointLightPosX, pointLightPosY, pointLightPosZ);
            glUniform3f(pointLightColorLocation, pointLightColorR, pointLightColorG, pointLightColorB);
            glUniform1f(pointLightIntensityLocation, pointLightIntensity);
            if (mesh.textureId != 0) {
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, mesh.textureId);
                glUniform1i(useAlbedoTextureLocation, 1);
            } else {
                glUniform1i(useAlbedoTextureLocation, 0);
            }
            if (mesh.normalTextureId != 0) {
                glActiveTexture(GL_TEXTURE0 + 1);
                glBindTexture(GL_TEXTURE_2D, mesh.normalTextureId);
                glUniform1i(useNormalTextureLocation, 1);
            } else {
                glUniform1i(useNormalTextureLocation, 0);
            }
            glBindVertexArray(mesh.vaoId);
            glDrawArrays(GL_TRIANGLES, 0, mesh.vertexCount);
            lastDrawCalls++;
            lastTriangles += mesh.vertexCount / 3;
        }
        glBindVertexArray(0);
        glActiveTexture(GL_TEXTURE0 + 1);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, 0);
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
        clearSceneMeshes();
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

    void setCameraMatrices(float[] view, float[] proj) {
        if (view != null && view.length == 16) {
            viewMatrix = view.clone();
        }
        if (proj != null && proj.length == 16) {
            projMatrix = proj.clone();
        }
    }

    void setLightingParameters(
            float[] dirDir,
            float[] dirColor,
            float dirIntensity,
            float[] pointPos,
            float[] pointColor,
            float pointIntensity
    ) {
        if (dirDir != null && dirDir.length == 3) {
            dirLightDirX = dirDir[0];
            dirLightDirY = dirDir[1];
            dirLightDirZ = dirDir[2];
        }
        if (dirColor != null && dirColor.length == 3) {
            dirLightColorR = dirColor[0];
            dirLightColorG = dirColor[1];
            dirLightColorB = dirColor[2];
        }
        dirLightIntensity = Math.max(0f, dirIntensity);
        if (pointPos != null && pointPos.length == 3) {
            pointLightPosX = pointPos[0];
            pointLightPosY = pointPos[1];
            pointLightPosZ = pointPos[2];
        }
        if (pointColor != null && pointColor.length == 3) {
            pointLightColorR = pointColor[0];
            pointLightColorG = pointColor[1];
            pointLightColorB = pointColor[2];
        }
        pointLightIntensity = Math.max(0f, pointIntensity);
    }

    double lastGpuFrameMs() {
        return lastGpuFrameMs;
    }

    long lastDrawCalls() {
        return lastDrawCalls;
    }

    long lastTriangles() {
        return lastTriangles;
    }

    long lastVisibleObjects() {
        return lastVisibleObjects;
    }

    void setSceneMeshes(List<SceneMesh> meshes) {
        clearSceneMeshes();
        List<SceneMesh> effectiveMeshes = meshes == null || meshes.isEmpty()
                ? List.of(new SceneMesh(defaultTriangleGeometry(), identityMatrix(), new float[]{1f, 1f, 1f}, 0.0f, 0.6f, null, null))
                : meshes;
        for (SceneMesh mesh : effectiveMeshes) {
            sceneMeshes.add(uploadMesh(mesh));
        }
    }

    private void initializeShaderPipeline() throws EngineException {
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
        modelLocation = glGetUniformLocation(programId, "uModel");
        viewLocation = glGetUniformLocation(programId, "uView");
        projLocation = glGetUniformLocation(programId, "uProj");
        materialAlbedoLocation = glGetUniformLocation(programId, "uMaterialAlbedo");
        materialMetallicLocation = glGetUniformLocation(programId, "uMaterialMetallic");
        materialRoughnessLocation = glGetUniformLocation(programId, "uMaterialRoughness");
        useAlbedoTextureLocation = glGetUniformLocation(programId, "uUseAlbedoTexture");
        albedoTextureLocation = glGetUniformLocation(programId, "uAlbedoTexture");
        useNormalTextureLocation = glGetUniformLocation(programId, "uUseNormalTexture");
        normalTextureLocation = glGetUniformLocation(programId, "uNormalTexture");
        dirLightDirLocation = glGetUniformLocation(programId, "uDirLightDir");
        dirLightColorLocation = glGetUniformLocation(programId, "uDirLightColor");
        dirLightIntensityLocation = glGetUniformLocation(programId, "uDirLightIntensity");
        pointLightPosLocation = glGetUniformLocation(programId, "uPointLightPos");
        pointLightColorLocation = glGetUniformLocation(programId, "uPointLightColor");
        pointLightIntensityLocation = glGetUniformLocation(programId, "uPointLightIntensity");
        fogEnabledLocation = glGetUniformLocation(programId, "uFogEnabled");
        fogColorLocation = glGetUniformLocation(programId, "uFogColor");
        fogDensityLocation = glGetUniformLocation(programId, "uFogDensity");
        fogStepsLocation = glGetUniformLocation(programId, "uFogSteps");
        smokeEnabledLocation = glGetUniformLocation(programId, "uSmokeEnabled");
        smokeColorLocation = glGetUniformLocation(programId, "uSmokeColor");
        smokeIntensityLocation = glGetUniformLocation(programId, "uSmokeIntensity");

        glUseProgram(programId);
        glUniform1i(albedoTextureLocation, 0);
        glUniform1i(normalTextureLocation, 1);
        glUseProgram(0);
    }

    private MeshBuffer uploadMesh(SceneMesh mesh) {
        int vaoId = glGenVertexArrays();
        int vboId = glGenBuffers();

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, mesh.geometry().vertices(), GL_STATIC_DRAW);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.BYTES, 0L);
        glEnableVertexAttribArray(0);

        glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * Float.BYTES, 3L * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        int textureId = loadTexture(mesh.albedoTexturePath());
        int normalTextureId = loadTexture(mesh.normalTexturePath());
        return new MeshBuffer(
                vaoId,
                vboId,
                mesh.geometry().vertexCount(),
                mesh.modelMatrix().clone(),
                mesh.albedoColor().clone(),
                clamp01(mesh.metallic()),
                clamp01(mesh.roughness()),
                textureId,
                normalTextureId
        );
    }

    private int loadTexture(Path texturePath) {
        if (texturePath == null || !Files.isRegularFile(texturePath)) {
            return 0;
        }
        try {
            BufferedImage image = ImageIO.read(texturePath.toFile());
            if (image == null) {
                return 0;
            }
            int width = image.getWidth();
            int height = image.getHeight();
            ByteBuffer rgba = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getRGB(x, y);
                    rgba.put((byte) ((argb >> 16) & 0xFF));
                    rgba.put((byte) ((argb >> 8) & 0xFF));
                    rgba.put((byte) (argb & 0xFF));
                    rgba.put((byte) ((argb >> 24) & 0xFF));
                }
            }
            rgba.flip();

            int textureId = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, textureId);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
            glBindTexture(GL_TEXTURE_2D, 0);
            return textureId;
        } catch (IOException ignored) {
            return 0;
        }
    }

    private void clearSceneMeshes() {
        for (MeshBuffer mesh : sceneMeshes) {
            if (mesh.textureId != 0) {
                glDeleteTextures(mesh.textureId);
            }
            if (mesh.normalTextureId != 0) {
                glDeleteTextures(mesh.normalTextureId);
            }
            glDeleteBuffers(mesh.vboId);
            glDeleteVertexArrays(mesh.vaoId);
        }
        sceneMeshes.clear();
    }

    private float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
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

    static MeshGeometry defaultTriangleGeometry() {
        return triangleGeometry(1.0f, 0.2f, 0.2f);
    }

    static MeshGeometry triangleGeometry(float r, float g, float b) {
        return new MeshGeometry(new float[]{
                -0.6f, -0.4f, 0.0f, r, g, b,
                0.6f, -0.4f, 0.0f, r * 0.2f + 0.2f, g * 0.9f + 0.1f, b * 0.2f + 0.2f,
                0.0f, 0.6f, 0.0f, r * 0.2f + 0.2f, g * 0.3f + 0.3f, b * 0.9f + 0.1f
        });
    }

    static MeshGeometry quadGeometry(float r, float g, float b) {
        return new MeshGeometry(new float[]{
                -0.55f, -0.55f, 0.0f, r, g, b,
                0.55f, -0.55f, 0.0f, r, g, b,
                0.55f, 0.55f, 0.0f, r, g, b,
                -0.55f, -0.55f, 0.0f, r, g, b,
                0.55f, 0.55f, 0.0f, r, g, b,
                -0.55f, 0.55f, 0.0f, r, g, b
        });
    }

    private static float[] identityMatrix() {
        return new float[]{
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
        };
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
