package org.dynamislight.impl.opengl;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_NONE;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_BORDER_COLOR;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glCullFace;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glDrawBuffer;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glReadBuffer;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameterfv;
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
import static org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glCheckFramebufferStatus;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30.glGenFramebuffers;
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
import static org.lwjgl.opengl.GL14.GL_TEXTURE_COMPARE_MODE;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE;

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
        private final long vertexBytes;
        private final long textureBytes;
        private final long normalTextureBytes;

        private MeshBuffer(
                int vaoId,
                int vboId,
                int vertexCount,
                float[] modelMatrix,
                float[] albedoColor,
                float metallic,
                float roughness,
                int textureId,
                int normalTextureId,
                long vertexBytes,
                long textureBytes,
                long normalTextureBytes
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
            this.vertexBytes = vertexBytes;
            this.textureBytes = textureBytes;
            this.normalTextureBytes = normalTextureBytes;
        }
    }

    private record TextureData(int id, long bytes) {
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
            out vec4 vLightSpacePos;
            uniform mat4 uLightViewProj;
            void main() {
                vec4 world = uModel * vec4(aPos, 1.0);
                vColor = aColor;
                vWorldPos = world.xyz;
                vHeight = world.y;
                vUv = aPos.xy * 0.5 + vec2(0.5);
                vLightSpacePos = uLightViewProj * world;
                gl_Position = uProj * uView * world;
            }
            """;

    private static final String SHADOW_VERTEX_SHADER = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            uniform mat4 uModel;
            uniform mat4 uLightViewProj;
            void main() {
                gl_Position = uLightViewProj * uModel * vec4(aPos, 1.0);
            }
            """;

    private static final String SHADOW_FRAGMENT_SHADER = """
            #version 330 core
            void main() { }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 330 core
            in vec3 vColor;
            in vec3 vWorldPos;
            in float vHeight;
            in vec2 vUv;
            in vec4 vLightSpacePos;
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
            uniform int uShadowEnabled;
            uniform float uShadowStrength;
            uniform float uShadowBias;
            uniform int uShadowPcfRadius;
            uniform int uShadowCascadeCount;
            uniform sampler2D uShadowMap;
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
            float shadowTerm(vec3 normal, float ndl) {
                vec3 projCoords = vLightSpacePos.xyz / max(vLightSpacePos.w, 0.0001);
                projCoords = projCoords * 0.5 + 0.5;
                if (projCoords.z > 1.0 || projCoords.x < 0.0 || projCoords.x > 1.0 || projCoords.y < 0.0 || projCoords.y > 1.0) {
                    return 0.0;
                }
                float cascadeBiasScale = 1.0 + float(max(uShadowCascadeCount - 1, 0)) * 0.12;
                float bias = max(uShadowBias, (1.0 - ndl) * uShadowBias * 2.0) * cascadeBiasScale;
                int radius = max(uShadowPcfRadius, 0);
                vec2 texel = 1.0 / vec2(textureSize(uShadowMap, 0));
                float occlusion = 0.0;
                int taps = 0;
                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        float depth = texture(uShadowMap, projCoords.xy + vec2(x, y) * texel).r;
                        occlusion += (projCoords.z - bias) > depth ? 1.0 : 0.0;
                        taps++;
                    }
                }
                return taps > 0 ? (occlusion / float(taps)) : 0.0;
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
                if (uShadowEnabled == 1) {
                    float shadowFactor = clamp(shadowTerm(normal, ndl) * uShadowStrength, 0.0, 0.9);
                    color *= (1.0 - shadowFactor);
                }
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
    private int lightViewProjLocation;
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
    private int shadowEnabledLocation;
    private int shadowStrengthLocation;
    private int shadowBiasLocation;
    private int shadowPcfRadiusLocation;
    private int shadowCascadeCountLocation;
    private int shadowMapLocation;
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
    private boolean shadowEnabled;
    private float shadowStrength = 0.45f;
    private float shadowBias = 0.0015f;
    private int shadowPcfRadius = 1;
    private int shadowCascadeCount = 1;
    private int shadowMapResolution = 1024;
    private int shadowProgramId;
    private int shadowModelLocation;
    private int shadowLightViewProjLocation;
    private int shadowFramebufferId;
    private int shadowDepthTextureId;
    private float[] lightViewProjMatrix = identityMatrix();
    private boolean gpuTimerQuerySupported;
    private int gpuTimeQueryId;
    private double lastGpuFrameMs;
    private long lastDrawCalls;
    private long lastTriangles;
    private long lastVisibleObjects;
    private long estimatedGpuMemoryBytes;

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
        initializeShadowPipeline();
        recreateShadowResources();
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
        renderShadowPass();
        renderGeometryPass();
        renderFogPass();
        renderSmokePass();
        endFrame();

        double cpuMs = (System.nanoTime() - startNs) / 1_000_000.0;
        double gpuMs = lastGpuFrameMs > 0.0 ? lastGpuFrameMs : cpuMs;
        return new OpenGlFrameMetrics(cpuMs, gpuMs, lastDrawCalls, lastTriangles, lastVisibleObjects, estimatedGpuMemoryBytes);
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
        glViewport(0, 0, width, height);
        glUseProgram(programId);
        applyFogUniforms();
        applySmokeUniforms();
        glUniformMatrix4fv(viewLocation, false, viewMatrix);
        glUniformMatrix4fv(projLocation, false, projMatrix);
        glUniformMatrix4fv(lightViewProjLocation, false, lightViewProjMatrix);
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
            glUniform1i(shadowEnabledLocation, shadowEnabled ? 1 : 0);
            glUniform1f(shadowStrengthLocation, shadowStrength);
            glUniform1f(shadowBiasLocation, shadowBias);
            glUniform1i(shadowPcfRadiusLocation, shadowPcfRadius);
            glUniform1i(shadowCascadeCountLocation, shadowCascadeCount);
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
            glActiveTexture(GL_TEXTURE0 + 2);
            glBindTexture(GL_TEXTURE_2D, shadowDepthTextureId);
            glBindVertexArray(mesh.vaoId);
            glDrawArrays(GL_TRIANGLES, 0, mesh.vertexCount);
            lastDrawCalls++;
            lastTriangles += mesh.vertexCount / 3;
        }
        glBindVertexArray(0);
        glActiveTexture(GL_TEXTURE0 + 1);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0 + 2);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, 0);
        glUseProgram(0);
    }

    void renderShadowPass() {
        if (!shadowEnabled || shadowFramebufferId == 0 || shadowProgramId == 0) {
            return;
        }
        updateLightViewProjMatrix();
        glViewport(0, 0, shadowMapResolution, shadowMapResolution);
        glBindFramebuffer(GL_FRAMEBUFFER, shadowFramebufferId);
        glClear(GL_DEPTH_BUFFER_BIT);
        glUseProgram(shadowProgramId);
        glUniformMatrix4fv(shadowLightViewProjLocation, false, lightViewProjMatrix);
        for (MeshBuffer mesh : sceneMeshes) {
            glUniformMatrix4fv(shadowModelLocation, false, mesh.modelMatrix);
            glBindVertexArray(mesh.vaoId);
            glDrawArrays(GL_TRIANGLES, 0, mesh.vertexCount);
        }
        glBindVertexArray(0);
        glUseProgram(0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
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
        destroyShadowResources();
        if (shadowProgramId != 0) {
            glDeleteProgram(shadowProgramId);
            shadowProgramId = 0;
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

    void setShadowParameters(boolean enabled, float strength, float bias, int pcfRadius, int cascadeCount, int mapResolution) {
        shadowEnabled = enabled;
        shadowStrength = Math.max(0f, Math.min(1f, strength));
        shadowBias = Math.max(0.00001f, bias);
        shadowPcfRadius = Math.max(0, pcfRadius);
        shadowCascadeCount = Math.max(1, cascadeCount);
        int clampedResolution = Math.max(256, Math.min(4096, mapResolution));
        if (shadowMapResolution != clampedResolution) {
            shadowMapResolution = clampedResolution;
            recreateShadowResources();
        }
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

    long estimatedGpuMemoryBytes() {
        return estimatedGpuMemoryBytes;
    }

    void setSceneMeshes(List<SceneMesh> meshes) {
        clearSceneMeshes();
        List<SceneMesh> effectiveMeshes = meshes == null || meshes.isEmpty()
                ? List.of(new SceneMesh(defaultTriangleGeometry(), identityMatrix(), new float[]{1f, 1f, 1f}, 0.0f, 0.6f, null, null))
                : meshes;
        for (SceneMesh mesh : effectiveMeshes) {
            sceneMeshes.add(uploadMesh(mesh));
        }
        estimatedGpuMemoryBytes = estimateGpuMemoryBytes();
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
        lightViewProjLocation = glGetUniformLocation(programId, "uLightViewProj");
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
        shadowEnabledLocation = glGetUniformLocation(programId, "uShadowEnabled");
        shadowStrengthLocation = glGetUniformLocation(programId, "uShadowStrength");
        shadowBiasLocation = glGetUniformLocation(programId, "uShadowBias");
        shadowPcfRadiusLocation = glGetUniformLocation(programId, "uShadowPcfRadius");
        shadowCascadeCountLocation = glGetUniformLocation(programId, "uShadowCascadeCount");
        shadowMapLocation = glGetUniformLocation(programId, "uShadowMap");
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
        glUniform1i(shadowMapLocation, 2);
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

        TextureData albedoTexture = loadTexture(mesh.albedoTexturePath());
        TextureData normalTexture = loadTexture(mesh.normalTexturePath());
        long vertexBytes = (long) mesh.geometry().vertices().length * Float.BYTES;
        return new MeshBuffer(
                vaoId,
                vboId,
                mesh.geometry().vertexCount(),
                mesh.modelMatrix().clone(),
                mesh.albedoColor().clone(),
                clamp01(mesh.metallic()),
                clamp01(mesh.roughness()),
                albedoTexture.id(),
                normalTexture.id(),
                vertexBytes,
                albedoTexture.bytes(),
                normalTexture.bytes()
        );
    }

    private TextureData loadTexture(Path texturePath) {
        if (texturePath == null || !Files.isRegularFile(texturePath)) {
            return new TextureData(0, 0);
        }
        try {
            BufferedImage image = ImageIO.read(texturePath.toFile());
            if (image == null) {
                return new TextureData(0, 0);
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
            return new TextureData(textureId, (long) width * height * 4L);
        } catch (IOException ignored) {
            return new TextureData(0, 0);
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
        estimatedGpuMemoryBytes = 0;
    }

    private long estimateGpuMemoryBytes() {
        long bytes = 0;
        for (MeshBuffer mesh : sceneMeshes) {
            bytes += mesh.vertexBytes;
            bytes += mesh.textureBytes;
            bytes += mesh.normalTextureBytes;
        }
        return bytes;
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

    private void initializeShadowPipeline() throws EngineException {
        int vertexShaderId = compileShader(GL_VERTEX_SHADER, SHADOW_VERTEX_SHADER);
        int fragmentShaderId = compileShader(GL_FRAGMENT_SHADER, SHADOW_FRAGMENT_SHADER);
        shadowProgramId = glCreateProgram();
        glAttachShader(shadowProgramId, vertexShaderId);
        glAttachShader(shadowProgramId, fragmentShaderId);
        glLinkProgram(shadowProgramId);
        if (glGetProgrami(shadowProgramId, GL_LINK_STATUS) == 0) {
            String info = glGetProgramInfoLog(shadowProgramId);
            glDeleteShader(vertexShaderId);
            glDeleteShader(fragmentShaderId);
            throw new EngineException(EngineErrorCode.SHADER_COMPILATION_FAILED, "Shadow shader link failed: " + info, false);
        }
        glDeleteShader(vertexShaderId);
        glDeleteShader(fragmentShaderId);
        shadowModelLocation = glGetUniformLocation(shadowProgramId, "uModel");
        shadowLightViewProjLocation = glGetUniformLocation(shadowProgramId, "uLightViewProj");
    }

    private void recreateShadowResources() {
        destroyShadowResources();
        shadowDepthTextureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, shadowDepthTextureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT, shadowMapResolution, shadowMapResolution, 0, GL_DEPTH_COMPONENT, GL_FLOAT, 0L);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_NONE);
        glTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR, new float[]{1f, 1f, 1f, 1f});
        glBindTexture(GL_TEXTURE_2D, 0);

        shadowFramebufferId = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, shadowFramebufferId);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, shadowDepthTextureId, 0);
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Shadow framebuffer incomplete: status=" + status);
        }
    }

    private void destroyShadowResources() {
        if (shadowFramebufferId != 0) {
            glDeleteFramebuffers(shadowFramebufferId);
            shadowFramebufferId = 0;
        }
        if (shadowDepthTextureId != 0) {
            glDeleteTextures(shadowDepthTextureId);
            shadowDepthTextureId = 0;
        }
    }

    private void updateLightViewProjMatrix() {
        float len = (float) Math.sqrt(dirLightDirX * dirLightDirX + dirLightDirY * dirLightDirY + dirLightDirZ * dirLightDirZ);
        if (len < 0.0001f) {
            len = 1f;
        }
        float lx = dirLightDirX / len;
        float ly = dirLightDirY / len;
        float lz = dirLightDirZ / len;
        float eyeX = -lx * 8.0f;
        float eyeY = -ly * 8.0f;
        float eyeZ = -lz * 8.0f;
        float[] lightView = lookAt(eyeX, eyeY, eyeZ, 0f, 0f, 0f, 0f, 1f, 0f);
        float[] lightProj = ortho(-8f, 8f, -8f, 8f, 0.1f, 32f);
        lightViewProjMatrix = mul(lightProj, lightView);
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

    private static float[] lookAt(float eyeX, float eyeY, float eyeZ, float targetX, float targetY, float targetZ,
                                  float upX, float upY, float upZ) {
        float fx = targetX - eyeX;
        float fy = targetY - eyeY;
        float fz = targetZ - eyeZ;
        float fLen = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (fLen < 0.00001f) {
            return identityMatrix();
        }
        fx /= fLen;
        fy /= fLen;
        fz /= fLen;

        float sx = fy * upZ - fz * upY;
        float sy = fz * upX - fx * upZ;
        float sz = fx * upY - fy * upX;
        float sLen = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sLen < 0.00001f) {
            return identityMatrix();
        }
        sx /= sLen;
        sy /= sLen;
        sz /= sLen;

        float ux = sy * fz - sz * fy;
        float uy = sz * fx - sx * fz;
        float uz = sx * fy - sy * fx;

        return new float[]{
                sx, ux, -fx, 0f,
                sy, uy, -fy, 0f,
                sz, uz, -fz, 0f,
                -(sx * eyeX + sy * eyeY + sz * eyeZ),
                -(ux * eyeX + uy * eyeY + uz * eyeZ),
                (fx * eyeX + fy * eyeY + fz * eyeZ),
                1f
        };
    }

    private static float[] ortho(float left, float right, float bottom, float top, float near, float far) {
        float rl = right - left;
        float tb = top - bottom;
        float fn = far - near;
        return new float[]{
                2f / rl, 0f, 0f, 0f,
                0f, 2f / tb, 0f, 0f,
                0f, 0f, -2f / fn, 0f,
                -(right + left) / rl, -(top + bottom) / tb, -(far + near) / fn, 1f
        };
    }

    private static float[] mul(float[] a, float[] b) {
        float[] out = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                out[c * 4 + r] = a[r] * b[c * 4]
                        + a[4 + r] * b[c * 4 + 1]
                        + a[8 + r] * b[c * 4 + 2]
                        + a[12 + r] * b[c * 4 + 3];
            }
        }
        return out;
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
