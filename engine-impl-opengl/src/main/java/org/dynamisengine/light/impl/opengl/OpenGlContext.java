package org.dynamisengine.light.impl.opengl;

import static org.dynamisengine.light.impl.opengl.GlMathUtil.*;
import static org.dynamisengine.light.impl.opengl.GlShaderSources.*;

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
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glDrawBuffer;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.GL_POLYGON_OFFSET_FILL;
import static org.lwjgl.opengl.GL11.glPolygonOffset;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glReadBuffer;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameterf;
import static org.lwjgl.opengl.GL11.glTexParameterfv;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE_CUBE_MAP;
import static org.lwjgl.opengl.GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X;
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
import static org.lwjgl.opengl.GL20.glUniform2f;
import static org.lwjgl.opengl.GL20.glUniform3f;
import static org.lwjgl.opengl.GL20.glUniform4f;
import static org.lwjgl.opengl.GL20.glUniform4fv;
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
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT1;
import static org.lwjgl.opengl.GL30.GL_DEPTH24_STENCIL8;
import static org.lwjgl.opengl.GL30.GL_DEPTH_STENCIL_ATTACHMENT;
import static org.lwjgl.opengl.GL30.GL_RENDERBUFFER;
import static org.lwjgl.opengl.GL30.glBindRenderbuffer;
import static org.lwjgl.opengl.GL30.glDeleteRenderbuffers;
import static org.lwjgl.opengl.GL30.glFramebufferRenderbuffer;
import static org.lwjgl.opengl.GL30.glGenRenderbuffers;
import static org.lwjgl.opengl.GL30.glRenderbufferStorage;
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dynamisengine.light.api.error.EngineErrorCode;
import org.dynamisengine.light.api.error.EngineException;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

final class OpenGlContext {
    static final int MAX_LOCAL_LIGHTS = 8;
    static final int MAX_LOCAL_SHADOWS = 4;

    enum VertexFormat {
        POS_COLOR_6F(6),
        POS_NORMAL_UV_8F(8);
        final int stride;
        VertexFormat(int stride) { this.stride = stride; }
    }

    static record MeshGeometry(float[] vertices, VertexFormat format) {
        MeshGeometry(float[] vertices) {
            this(vertices, VertexFormat.POS_COLOR_6F);
        }

        MeshGeometry {
            if (vertices == null || vertices.length == 0 || vertices.length % format.stride != 0) {
                throw new IllegalArgumentException(
                        "Mesh vertices must be non-empty and divisible by stride " + format.stride);
            }
        }

        int vertexCount() {
            return vertices.length / format.stride;
        }
    }

    static record SceneMesh(
            String meshId,
            MeshGeometry geometry,
            float[] modelMatrix,
            float[] albedoColor,
            float metallic,
            float roughness,
            float reactiveStrength,
            boolean alphaTested,
            float alphaCutoff,
            boolean foliage,
            float reactiveBoost,
            float taaHistoryClamp,
            float emissiveReactiveBoost,
            float reactivePreset,
            Path albedoTexturePath,
            Path normalTexturePath,
            Path metallicRoughnessTexturePath,
            Path occlusionTexturePath,
            int preloadedAlbedoTextureId,
            int preloadedNormalTextureId,
            int preloadedMetallicRoughnessTextureId,
            int preloadedOcclusionTextureId
    ) {
        SceneMesh(
                String meshId,
                MeshGeometry geometry,
                float[] modelMatrix,
                float[] albedoColor,
                float metallic,
                float roughness,
                float reactiveStrength,
                boolean alphaTested,
                boolean foliage,
                float reactiveBoost,
                float taaHistoryClamp,
                float emissiveReactiveBoost,
                float reactivePreset,
                Path albedoTexturePath,
                Path normalTexturePath,
                Path metallicRoughnessTexturePath,
                Path occlusionTexturePath
        ) {
            this(meshId, geometry, modelMatrix, albedoColor, metallic, roughness,
                    reactiveStrength, alphaTested, alphaTested ? 0.5f : 0f, foliage, reactiveBoost, taaHistoryClamp,
                    emissiveReactiveBoost, reactivePreset, albedoTexturePath, normalTexturePath,
                    metallicRoughnessTexturePath, occlusionTexturePath, 0, 0, 0, 0);
        }
        SceneMesh {
            if (meshId == null || meshId.isBlank()) {
                throw new IllegalArgumentException("meshId is required");
            }
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

    static final class MeshBuffer {
        final int vaoId;
        private final int vboId;
        final int vertexCount;
        private final int vertexFormat; // 0 = POS_COLOR_6F, 1 = POS_NORMAL_UV_8F
        private final String meshId;
        final float[] modelMatrix;
        private final float[] prevModelMatrix;
        private final float[] albedoColor;
        private final float metallic;
        private final float roughness;
        private final float reactiveStrength;
        private final boolean alphaTested;
        private final float alphaCutoff;
        private final boolean foliage;
        private final float reactiveBoost;
        private final float taaHistoryClamp;
        private final float emissiveReactiveBoost;
        private final float reactivePreset;
        private final int textureId;
        private final int normalTextureId;
        private final int metallicRoughnessTextureId;
        private final int occlusionTextureId;
        private final long vertexBytes;
        private final long textureBytes;
        private final long normalTextureBytes;
        private final long metallicRoughnessTextureBytes;
        private final long occlusionTextureBytes;

        private MeshBuffer(
                int vaoId,
                int vboId,
                int vertexCount,
                int vertexFormat,
                String meshId,
                float[] modelMatrix,
                float[] prevModelMatrix,
                float[] albedoColor,
                float metallic,
                float roughness,
                float reactiveStrength,
                boolean alphaTested,
                float alphaCutoff,
                boolean foliage,
                float reactiveBoost,
                float taaHistoryClamp,
                float emissiveReactiveBoost,
                float reactivePreset,
                int textureId,
                int normalTextureId,
                int metallicRoughnessTextureId,
                int occlusionTextureId,
                long vertexBytes,
                long textureBytes,
                long normalTextureBytes,
                long metallicRoughnessTextureBytes,
                long occlusionTextureBytes
        ) {
            this.vaoId = vaoId;
            this.vboId = vboId;
            this.vertexCount = vertexCount;
            this.vertexFormat = vertexFormat;
            this.meshId = meshId;
            this.modelMatrix = modelMatrix;
            this.prevModelMatrix = prevModelMatrix;
            this.albedoColor = albedoColor;
            this.metallic = metallic;
            this.roughness = roughness;
            this.reactiveStrength = reactiveStrength;
            this.alphaTested = alphaTested;
            this.alphaCutoff = alphaCutoff;
            this.foliage = foliage;
            this.reactiveBoost = reactiveBoost;
            this.taaHistoryClamp = taaHistoryClamp;
            this.emissiveReactiveBoost = emissiveReactiveBoost;
            this.reactivePreset = reactivePreset;
            this.textureId = textureId;
            this.normalTextureId = normalTextureId;
            this.metallicRoughnessTextureId = metallicRoughnessTextureId;
            this.occlusionTextureId = occlusionTextureId;
            this.vertexBytes = vertexBytes;
            this.textureBytes = textureBytes;
            this.normalTextureBytes = normalTextureBytes;
            this.metallicRoughnessTextureBytes = metallicRoughnessTextureBytes;
            this.occlusionTextureBytes = occlusionTextureBytes;
        }
    }

    private OpenGlTextureLoader textureLoader;

    // Shader source constants moved to GlShaderSources (static-imported above).
    // VERTEX_SHADER, SHADOW_VERTEX_SHADER, SHADOW_FRAGMENT_SHADER,
    // FRAGMENT_SHADER, POST_VERTEX_SHADER, POST_FRAGMENT_SHADER

    private long window;
    private int width;
    private int height;
    private volatile int pendingFramebufferWidth;
    private volatile int pendingFramebufferHeight;
    private int sceneRenderWidth = 1;
    private int sceneRenderHeight = 1;
    private int programId;
    private final List<MeshBuffer> sceneMeshes = new ArrayList<>();
    private int vertexFormatLocation;
    private int modelLocation;
    private int prevModelLocation;
    private int viewLocation;
    private int projLocation;
    private int currentViewProjLocation;
    private int prevViewProjLocation;
    private int lightViewProjLocation;
    private int materialAlbedoLocation;
    private int materialMetallicLocation;
    private int materialRoughnessLocation;
    private int materialReactiveLocation;
    private int materialReactiveTuningLocation;
    private int useAlbedoTextureLocation;
    private int albedoTextureLocation;
    private int useNormalTextureLocation;
    private int normalTextureLocation;
    private int useMetallicRoughnessTextureLocation;
    private int metallicRoughnessTextureLocation;
    private int useOcclusionTextureLocation;
    private int occlusionTextureLocation;
    private int iblIrradianceTextureLocation;
    private int iblRadianceTextureLocation;
    private int iblBrdfLutTextureLocation;
    private int iblRadianceMaxLodLocation;
    private int dirLightDirLocation;
    private int dirLightColorLocation;
    private int dirLightIntensityLocation;
    private int localLightCountLocation;
    private int localLightPosRangeLocation;
    private int localLightColorIntensityLocation;
    private int localLightDirInnerLocation;
    private int localLightOuterTypeShadowLocation;
    private int pointLightPosLocation;
    private int pointLightDirLocation;
    private int pointShadowEnabledLocation;
    private int pointShadowMapLocation;
    private int pointShadowFarPlaneLocation;
    private int pointShadowLightIndexLocation;
    private int shadowEnabledLocation;
    private int shadowStrengthLocation;
    private int shadowBiasLocation;
    private int shadowNormalBiasScaleLocation;
    private int shadowSlopeBiasScaleLocation;
    private int shadowPcfRadiusLocation;
    private int shadowCascadeCountLocation;
    private int shadowMapLocation;
    private int localShadowCountLocation;
    private int localShadowMapLocation;
    private int localShadowMatrixLocation;
    private int localShadowAtlasRectLocation;
    private int localShadowMetaLocation;
    private int fogEnabledLocation;
    private int fogColorLocation;
    private int fogDensityLocation;
    private int fogStepsLocation;
    private int smokeEnabledLocation;
    private int smokeColorLocation;
    private int smokeIntensityLocation;
    private int viewportSizeLocation;
    private int iblParamsLocation;
    private int tonemapEnabledLocation;
    private int tonemapExposureLocation;
    private int tonemapGammaLocation;
    private int bloomEnabledLocation;
    private int bloomThresholdLocation;
    private int bloomStrengthLocation;
    private int ssaoEnabledLocation;
    private int ssaoStrengthLocation;
    private int ssaoRadiusLocation;
    private int ssaoBiasLocation;
    private int ssaoPowerLocation;
    private int smaaEnabledLocation;
    private int smaaStrengthLocation;
    private int taaEnabledLocation;
    private int taaBlendLocation;
    private int ambientColorLocation;
    private int ambientIntensityLocation;
    private int alphaCutoffLocation;
    private OpenGlPostProcessor postProcessor;
    private final OpenGlTemporalAA temporalAA = new OpenGlTemporalAA();
    private float[] viewMatrix = identityMatrix();
    private float[] projMatrix = identityMatrix();
    private float[] projBaseMatrix = identityMatrix();
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
    private boolean iblEnabled;
    private float iblDiffuseStrength;
    private float iblSpecularStrength;
    private float iblPrefilterStrength;
    private float iblRadianceMaxLod;
    private boolean tonemapEnabled = true;
    private float tonemapExposure = 1.0f;
    private float tonemapGamma = 2.2f;
    private boolean bloomEnabled;
    private float bloomThreshold = 1.0f;
    private float bloomStrength = 0.8f;
    private boolean ssaoEnabled;
    private float ssaoStrength = 0f;
    private float ssaoRadius = 1.0f;
    private float ssaoBias = 0.02f;
    private float ssaoPower = 1.0f;
    private boolean smaaEnabled;
    private float smaaStrength;
    private boolean taaEnabled;
    private float taaBlend;
    private int taaDebugView;
    private float taaClipScale = 1.0f;
    private boolean taaLumaClipEnabled;
    private float taaSharpenStrength = 0.16f;
    private float taaRenderScale = 1.0f;
    private boolean reflectionsEnabled;
    private int reflectionsMode;
    private float reflectionsSsrStrength = 0.6f;
    private float reflectionsSsrMaxRoughness = 0.78f;
    private float reflectionsSsrStepScale = 1.0f;
    private float reflectionsTemporalWeight = 0.80f;
    private float reflectionsPlanarStrength = 0.35f;
    private float dirLightDirX = 0.3f;
    private float dirLightDirY = -1.0f;
    private float dirLightDirZ = 0.25f;
    private float dirLightColorR = 1.0f;
    private float dirLightColorG = 0.98f;
    private float dirLightColorB = 0.95f;
    private float dirLightIntensity = 1.0f;
    private int localLightCount;
    private final float[] localLightPosRange = new float[MAX_LOCAL_LIGHTS * 4];
    private final float[] localLightColorIntensity = new float[MAX_LOCAL_LIGHTS * 4];
    private final float[] localLightDirInner = new float[MAX_LOCAL_LIGHTS * 4];
    private final float[] localLightOuterTypeShadow = new float[MAX_LOCAL_LIGHTS * 4];
    private float pointLightPosX = 0.0f;
    private float pointLightPosY = 1.2f;
    private float pointLightPosZ = 1.8f;
    private float pointLightDirX = 0.0f;
    private float pointLightDirY = -1.0f;
    private float pointLightDirZ = 0.0f;
    private float pointLightOuterCos = 1.0f;
    private float pointLightIsSpot;
    private boolean pointShadowEnabled;
    private int pointShadowLightIndex = -1;
    private float pointShadowFarPlane = 15f;
    private boolean shadowEnabled;
    private float shadowStrength = 0.45f;
    private float shadowBias = 0.0015f;
    private float shadowNormalBiasScale = 1.0f;
    private float shadowSlopeBiasScale = 1.0f;
    private int shadowPcfRadius = 1;
    private int shadowCascadeCount = 1;
    private int shadowMapResolution = 1024;
    private OpenGlShadowRenderer shadowRenderer;
    private int iblIrradianceTextureId;
    private int iblRadianceTextureId;
    private int iblBrdfLutTextureId;
    private float clearColorR = 0.08f;
    private float clearColorG = 0.09f;
    private float clearColorB = 0.12f;
    private float ambientColorR = 0.15f;
    private float ambientColorG = 0.15f;
    private float ambientColorB = 0.15f;
    private float ambientIntensity = 0.3f;
    private float shadowOrthoSize = 8f;
    private float shadowFarPlane = 32f;
    private float maxAnisotropy;
    private boolean gpuTimerQuerySupported;
    private int gpuTimeQueryId;
    private double lastGpuFrameMs;
    private long lastDrawCalls;
    private long lastTriangles;
    private long lastVisibleObjects;
    private long estimatedGpuMemoryBytes;
    private int frameCounter;

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
        GLFW.glfwWindowHint(GLFW.GLFW_COCOA_RETINA_FRAMEBUFFER, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_SCALE_FRAMEBUFFER, GLFW.GLFW_TRUE);

        window = GLFW.glfwCreateWindow(width, height, appName, 0, 0);
        if (window == 0) {
            GLFW.glfwTerminate();
            throw new EngineException(EngineErrorCode.BACKEND_INIT_FAILED, "Failed to create OpenGL window/context", false);
        }
        if (windowVisible) {
            // Make visibility explicit on macOS instead of relying solely on GLFW hints.
            GLFW.glfwRestoreWindow(window);
            GLFW.glfwSetWindowPos(window, 120, 120);
            GLFW.glfwShowWindow(window);
            GLFW.glfwFocusWindow(window);
        }

        GLFW.glfwSetFramebufferSizeCallback(window, (win, fbW, fbH) -> {
            if (fbW > 0 && fbH > 0) {
                pendingFramebufferWidth = fbW;
                pendingFramebufferHeight = fbH;
            }
        });

        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwSwapInterval(vsyncEnabled ? 1 : 0);
        GL.createCapabilities();
        glEnable(GL_DEPTH_TEST);

        // Poll events so macOS delivers the Retina framebuffer size callback.
        GLFW.glfwPollEvents();

        // Use the callback-reported size if available, otherwise query directly.
        int framebufferWidth;
        int framebufferHeight;
        if (pendingFramebufferWidth > 0 && pendingFramebufferHeight > 0) {
            framebufferWidth = pendingFramebufferWidth;
            framebufferHeight = pendingFramebufferHeight;
            pendingFramebufferWidth = 0;
            pendingFramebufferHeight = 0;
        } else {
            int[] drawable = queryDrawableSize(width, height);
            framebufferWidth = drawable[0];
            framebufferHeight = drawable[1];
        }
        this.width = framebufferWidth;
        this.height = framebufferHeight;
        updateSceneRenderResolution();
        glViewport(0, 0, this.width, this.height);

        initializeShaderPipeline();
        shadowRenderer = new OpenGlShadowRenderer();
        shadowRenderer.initializeShadowPipeline();
        postProcessor = new OpenGlPostProcessor();
        postProcessor.initializePipeline();
        postProcessor.recreateTargets(sceneRenderWidth, sceneRenderHeight);
        shadowRenderer.recreateShadowResources(shadowMapResolution);
        initializeGpuQuerySupport();
        setSceneMeshes(List.of(new SceneMesh(
                "default-triangle",
                defaultTriangleGeometry(),
                identityMatrix(),
                new float[]{1f, 1f, 1f},
                0.0f,
                0.6f,
                0f,
                false,
                false,
                1.0f,
                1.0f,
                1.0f,
                0f,
                null,
                null,
                null,
                null
        )));
    }

    void resize(int width, int height) {
        int[] drawable = queryDrawableSize(width, height);
        this.width = drawable[0];
        this.height = drawable[1];
        updateSceneRenderResolution();
        glViewport(0, 0, this.width, this.height);
        postProcessor.recreateTargets(sceneRenderWidth, sceneRenderHeight);
    }

    OpenGlFrameMetrics renderFrame() {
        if (window == 0) {
            return new OpenGlFrameMetrics(0.0, 0.0, 0, 0, 0, 0);
        }

        long startNs = System.nanoTime();

        beginFrame();
        renderClearPass();
        renderShadowPass();
        renderLocalShadowAtlasPass();
        renderPointShadowPass();
        renderGeometryPass();
        renderFogPass();
        renderSmokePass();
        renderPostProcessPass();
        endFrame();

        double cpuMs = (System.nanoTime() - startNs) / 1_000_000.0;
        double gpuMs = lastGpuFrameMs > 0.0 ? lastGpuFrameMs : cpuMs;
        return new OpenGlFrameMetrics(cpuMs, gpuMs, lastDrawCalls, lastTriangles, lastVisibleObjects, estimatedGpuMemoryBytes);
    }

    void beginFrame() {
        syncFramebufferSizeFromWindow();
        frameCounter++;
        projMatrix = temporalAA.updateTemporalJitterState(
                taaEnabled, sceneRenderWidth, sceneRenderHeight,
                projBaseMatrix, viewMatrix);
        glViewport(0, 0, activeRenderWidth(), activeRenderHeight());
        if (gpuTimerQuerySupported) {
            glBeginQuery(GL_TIME_ELAPSED, gpuTimeQueryId);
        }
    }

    void renderClearPass() {
        if (useDedicatedPostPass()) {
            glBindFramebuffer(GL_FRAMEBUFFER, postProcessor.sceneFramebufferId());
        } else {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
        }
        glClearColor(clearColorR, clearColorG, clearColorB, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    void renderGeometryPass() {
        glViewport(0, 0, activeRenderWidth(), activeRenderHeight());
        glUseProgram(programId);
        applyFogUniforms();
        applySmokeUniforms();
        applyPostProcessUniforms(useShaderDrivenPost());
        glUniformMatrix4fv(viewLocation, false, viewMatrix);
        glUniformMatrix4fv(projLocation, false, projMatrix);
        glUniformMatrix4fv(currentViewProjLocation, false, mul(projMatrix, viewMatrix));
        glUniformMatrix4fv(prevViewProjLocation, false, temporalAA.isPrevViewProjValid() ? temporalAA.prevViewProj() : mul(projMatrix, viewMatrix));
        glUniformMatrix4fv(lightViewProjLocation, false, shadowRenderer.lightViewProjMatrix());
        glUniform3f(ambientColorLocation, ambientColorR, ambientColorG, ambientColorB);
        glUniform1f(ambientIntensityLocation, ambientIntensity);
        lastDrawCalls = 0;
        lastTriangles = 0;
        lastVisibleObjects = sceneMeshes.size();
        for (MeshBuffer mesh : sceneMeshes) {
            glUniform1i(vertexFormatLocation, mesh.vertexFormat);
            glUniformMatrix4fv(modelLocation, false, mesh.modelMatrix);
            glUniformMatrix4fv(prevModelLocation, false, mesh.prevModelMatrix);
            glUniform3f(materialAlbedoLocation, mesh.albedoColor[0], mesh.albedoColor[1], mesh.albedoColor[2]);
            glUniform1f(materialMetallicLocation, mesh.metallic);
            glUniform1f(materialRoughnessLocation, mesh.roughness);
            glUniform4f(
                    materialReactiveLocation,
                    mesh.reactiveStrength,
                    mesh.alphaTested ? 1f : 0f,
                    mesh.foliage ? 1f : 0f,
                    0f
            );
            glUniform4f(
                    materialReactiveTuningLocation,
                    mesh.reactiveBoost,
                    mesh.taaHistoryClamp,
                    mesh.emissiveReactiveBoost,
                    mesh.reactivePreset
            );
            glUniform1f(alphaCutoffLocation, mesh.alphaCutoff);
            glUniform3f(dirLightDirLocation, dirLightDirX, dirLightDirY, dirLightDirZ);
            glUniform3f(dirLightColorLocation, dirLightColorR, dirLightColorG, dirLightColorB);
            glUniform1f(dirLightIntensityLocation, dirLightIntensity);
            glUniform1i(localLightCountLocation, localLightCount);
            glUniform4fv(localLightPosRangeLocation, localLightPosRange);
            glUniform4fv(localLightColorIntensityLocation, localLightColorIntensity);
            glUniform4fv(localLightDirInnerLocation, localLightDirInner);
            glUniform4fv(localLightOuterTypeShadowLocation, localLightOuterTypeShadow);
            glUniform3f(pointLightPosLocation, pointLightPosX, pointLightPosY, pointLightPosZ);
            glUniform3f(pointLightDirLocation, pointLightDirX, pointLightDirY, pointLightDirZ);
            glUniform1i(pointShadowEnabledLocation, pointShadowEnabled ? 1 : 0);
            glUniform1f(pointShadowFarPlaneLocation, pointShadowFarPlane);
            glUniform1i(pointShadowLightIndexLocation, pointShadowLightIndex);
            glUniform1i(shadowEnabledLocation, shadowEnabled ? 1 : 0);
            glUniform1f(shadowStrengthLocation, shadowStrength);
            glUniform1f(shadowBiasLocation, shadowBias);
            glUniform1f(shadowNormalBiasScaleLocation, shadowNormalBiasScale);
            glUniform1f(shadowSlopeBiasScaleLocation, shadowSlopeBiasScale);
            glUniform1i(shadowPcfRadiusLocation, shadowPcfRadius);
            glUniform1i(shadowCascadeCountLocation, shadowCascadeCount);
            glUniform1i(localShadowCountLocation, shadowRenderer.localShadowCount());
            glUniformMatrix4fv(localShadowMatrixLocation, false, shadowRenderer.localShadowMatrices());
            glUniform4fv(localShadowAtlasRectLocation, shadowRenderer.localShadowAtlasRects());
            glUniform4fv(localShadowMetaLocation, shadowRenderer.localShadowMeta());
            glUniform4f(
                    iblParamsLocation,
                    iblEnabled ? 1f : 0f,
                    iblDiffuseStrength,
                    iblSpecularStrength,
                    iblPrefilterStrength
            );
            glUniform1f(iblRadianceMaxLodLocation, iblRadianceMaxLod);
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
            if (mesh.metallicRoughnessTextureId != 0) {
                glActiveTexture(GL_TEXTURE0 + 2);
                glBindTexture(GL_TEXTURE_2D, mesh.metallicRoughnessTextureId);
                glUniform1i(useMetallicRoughnessTextureLocation, 1);
            } else {
                glUniform1i(useMetallicRoughnessTextureLocation, 0);
            }
            if (mesh.occlusionTextureId != 0) {
                glActiveTexture(GL_TEXTURE0 + 3);
                glBindTexture(GL_TEXTURE_2D, mesh.occlusionTextureId);
                glUniform1i(useOcclusionTextureLocation, 1);
            } else {
                glUniform1i(useOcclusionTextureLocation, 0);
            }
            glActiveTexture(GL_TEXTURE0 + 4);
            glBindTexture(GL_TEXTURE_2D, shadowRenderer.shadowDepthTextureId());
            glActiveTexture(GL_TEXTURE0 + 5);
            glBindTexture(GL_TEXTURE_2D, iblIrradianceTextureId);
            glActiveTexture(GL_TEXTURE0 + 6);
            glBindTexture(GL_TEXTURE_2D, iblRadianceTextureId);
            glActiveTexture(GL_TEXTURE0 + 7);
            glBindTexture(GL_TEXTURE_2D, iblBrdfLutTextureId);
            glActiveTexture(GL_TEXTURE0 + 8);
            glBindTexture(GL_TEXTURE_CUBE_MAP, shadowRenderer.pointShadowDepthTextureId());
            glActiveTexture(GL_TEXTURE0 + 9);
            glBindTexture(GL_TEXTURE_2D, shadowRenderer.localShadowDepthTextureId());
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
        glActiveTexture(GL_TEXTURE0 + 3);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0 + 4);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0 + 5);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0 + 6);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0 + 7);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0 + 8);
        glBindTexture(GL_TEXTURE_CUBE_MAP, 0);
        glActiveTexture(GL_TEXTURE0 + 9);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, 0);
        glUseProgram(0);
    }

    void renderShadowPass() {
        shadowRenderer.renderShadowPass(
                shadowEnabled, shadowMapResolution, sceneMeshes,
                pointLightIsSpot,
                pointLightPosX, pointLightPosY, pointLightPosZ,
                pointLightDirX, pointLightDirY, pointLightDirZ,
                pointLightOuterCos,
                dirLightDirX, dirLightDirY, dirLightDirZ,
                shadowCascadeCount,
                shadowOrthoSize, shadowFarPlane
        );
    }

    void renderLocalShadowAtlasPass() {
        shadowRenderer.renderLocalShadowAtlasPass(
                shadowEnabled, shadowMapResolution, sceneMeshes,
                localLightCount, localLightPosRange, localLightDirInner,
                localLightOuterTypeShadow, frameCounter
        );
    }

    void renderPointShadowPass() {
        pointShadowLightIndex = -1;
        OpenGlShadowRenderer.PointShadowResult result = shadowRenderer.renderPointShadowPass(
                shadowEnabled, shadowMapResolution, sceneMeshes,
                localLightCount, localLightPosRange, localLightOuterTypeShadow,
                frameCounter
        );
        pointShadowEnabled = result.pointShadowEnabled();
        pointShadowLightIndex = result.pointShadowLightIndex();
        if (result.pointShadowEnabled()) {
            pointLightPosX = result.pointLightPosX();
            pointLightPosY = result.pointLightPosY();
            pointLightPosZ = result.pointLightPosZ();
            pointShadowFarPlane = result.pointShadowFarPlane();
        }
    }

    void renderFogPass() {
        // Fog is currently applied in the fragment shader during geometry pass.
    }

    void renderSmokePass() {
        // Smoke is currently applied in the fragment shader during geometry pass.
    }

    void renderPostProcessPass() {
        postProcessor.renderPostProcessPass(
                width, height, sceneRenderWidth, sceneRenderHeight,
                tonemapEnabled, tonemapExposure, tonemapGamma,
                bloomEnabled, bloomThreshold, bloomStrength,
                ssaoEnabled, ssaoStrength, ssaoRadius, ssaoBias, ssaoPower,
                smaaEnabled, smaaStrength,
                taaEnabled, taaBlend,
                temporalAA.taaJitterUvDeltaX(), temporalAA.taaJitterUvDeltaY(),
                temporalAA.motionUvX(), temporalAA.motionUvY(),
                taaDebugView, taaClipScale, taaLumaClipEnabled, taaSharpenStrength, taaRenderScale,
                reflectionsEnabled, reflectionsMode,
                reflectionsSsrStrength, reflectionsSsrMaxRoughness, reflectionsSsrStepScale,
                reflectionsTemporalWeight, reflectionsPlanarStrength
        );
    }

    void endFrame() {
        promotePreviousModelMatrices();
        temporalAA.updateTemporalHistoryCameraState(taaEnabled, projMatrix, viewMatrix);
        temporalAA.updateAaTelemetry(taaEnabled, taaBlend, taaClipScale, taaLumaClipEnabled, taaRenderScale);
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

    private void promotePreviousModelMatrices() {
        for (MeshBuffer mesh : sceneMeshes) {
            System.arraycopy(mesh.modelMatrix, 0, mesh.prevModelMatrix, 0, 16);
        }
    }

    double taaHistoryRejectRate() {
        return temporalAA.taaHistoryRejectRate();
    }

    double taaConfidenceMean() {
        return temporalAA.taaConfidenceMean();
    }

    long taaConfidenceDropEvents() {
        return temporalAA.taaConfidenceDropEvents();
    }

    void shutdown() {
        clearSceneMeshes();
        shadowRenderer.destroyShadowResources();
        postProcessor.destroyPipeline();
        shadowRenderer.destroyShadowProgram();
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

    void setIblParameters(boolean enabled, float diffuseStrength, float specularStrength, float prefilterStrength) {
        iblEnabled = enabled;
        iblDiffuseStrength = Math.max(0f, Math.min(2.0f, diffuseStrength));
        iblSpecularStrength = Math.max(0f, Math.min(2.0f, specularStrength));
        iblPrefilterStrength = Math.max(0f, Math.min(1.0f, prefilterStrength));
    }

    void setIblTexturePaths(Path irradiancePath, Path radiancePath, Path brdfLutPath) {
        if (iblIrradianceTextureId != 0) {
            glDeleteTextures(iblIrradianceTextureId);
            iblIrradianceTextureId = 0;
        }
        if (iblRadianceTextureId != 0) {
            glDeleteTextures(iblRadianceTextureId);
            iblRadianceTextureId = 0;
        }
        if (iblBrdfLutTextureId != 0) {
            glDeleteTextures(iblBrdfLutTextureId);
            iblBrdfLutTextureId = 0;
        }
        OpenGlTextureLoader.TextureData irradiance = textureLoader.loadTexture(irradiancePath);
        OpenGlTextureLoader.TextureData radiance = textureLoader.loadTexture(radiancePath);
        OpenGlTextureLoader.TextureData brdfLut = textureLoader.loadTexture(brdfLutPath);
        iblIrradianceTextureId = irradiance.id();
        iblRadianceTextureId = radiance.id();
        iblBrdfLutTextureId = brdfLut.id();
        iblRadianceMaxLod = radiance.maxLod();
    }

    void setPostProcessParameters(
            boolean tonemapEnabled,
            float exposure,
            float gamma,
            boolean bloomEnabled,
            float bloomThreshold,
            float bloomStrength,
            boolean ssaoEnabled,
            float ssaoStrength,
            float ssaoRadius,
            float ssaoBias,
            float ssaoPower,
            boolean smaaEnabled,
            float smaaStrength,
            boolean taaEnabled,
            float taaBlend,
            float taaClipScale,
            boolean taaLumaClipEnabled,
            float taaSharpenStrength,
            float taaRenderScale,
            boolean reflectionsEnabled,
            int reflectionsMode,
            float reflectionsSsrStrength,
            float reflectionsSsrMaxRoughness,
            float reflectionsSsrStepScale,
            float reflectionsTemporalWeight,
            float reflectionsPlanarStrength
    ) {
        boolean previousTaaEnabled = this.taaEnabled;
        this.tonemapEnabled = tonemapEnabled;
        tonemapExposure = Math.max(0.05f, Math.min(8.0f, exposure));
        tonemapGamma = Math.max(0.8f, Math.min(3.2f, gamma));
        this.bloomEnabled = bloomEnabled;
        this.bloomThreshold = Math.max(0f, Math.min(4.0f, bloomThreshold));
        this.bloomStrength = Math.max(0f, Math.min(2.0f, bloomStrength));
        this.ssaoEnabled = ssaoEnabled;
        this.ssaoStrength = Math.max(0f, Math.min(1.0f, ssaoStrength));
        this.ssaoRadius = Math.max(0.2f, Math.min(3.0f, ssaoRadius));
        this.ssaoBias = Math.max(0f, Math.min(0.2f, ssaoBias));
        this.ssaoPower = Math.max(0.5f, Math.min(4.0f, ssaoPower));
        this.smaaEnabled = smaaEnabled;
        this.smaaStrength = Math.max(0f, Math.min(1.0f, smaaStrength));
        this.taaEnabled = taaEnabled;
        this.taaBlend = Math.max(0f, Math.min(0.95f, taaBlend));
        this.taaClipScale = Math.max(0.5f, Math.min(1.6f, taaClipScale));
        this.taaLumaClipEnabled = taaLumaClipEnabled;
        this.taaSharpenStrength = Math.max(0f, Math.min(0.35f, taaSharpenStrength));
        this.reflectionsEnabled = reflectionsEnabled;
        this.reflectionsMode = Math.max(0, Math.min(2047, reflectionsMode));
        this.reflectionsSsrStrength = Math.max(0f, Math.min(1f, reflectionsSsrStrength));
        this.reflectionsSsrMaxRoughness = Math.max(0f, Math.min(1f, reflectionsSsrMaxRoughness));
        this.reflectionsSsrStepScale = Math.max(0.5f, Math.min(3f, reflectionsSsrStepScale));
        this.reflectionsTemporalWeight = Math.max(0f, Math.min(0.98f, reflectionsTemporalWeight));
        this.reflectionsPlanarStrength = Math.max(0f, Math.min(1f, reflectionsPlanarStrength));
        float clampedRenderScale = Math.max(0.5f, Math.min(1.0f, taaRenderScale));
        boolean renderScaleChanged = Math.abs(this.taaRenderScale - clampedRenderScale) > 0.000001f;
        this.taaRenderScale = clampedRenderScale;
        boolean taaModeChanged = previousTaaEnabled != this.taaEnabled;
        if (renderScaleChanged || taaModeChanged) {
            updateSceneRenderResolution();
            postProcessor.recreateTargets(sceneRenderWidth, sceneRenderHeight);
            temporalAA.resetTemporalJitterState();
            temporalAA.resetTemporalMotionState();
        }
        if (!this.taaEnabled) {
            temporalAA.resetTemporalJitterState();
            projMatrix = projBaseMatrix.clone();
            temporalAA.resetTemporalMotionState();
            temporalAA.resetTelemetry();
        }
    }

    void setCameraMatrices(float[] view, float[] proj) {
        if (view != null && view.length == 16) {
            viewMatrix = view.clone();
        }
        if (proj != null && proj.length == 16) {
            projBaseMatrix = proj.clone();
            projMatrix = applyProjectionJitter(projBaseMatrix, temporalAA.taaJitterNdcX(), temporalAA.taaJitterNdcY());
            postProcessor.invalidateHistory();
            temporalAA.invalidatePrevViewProj();
        }
    }

    void setTaaDebugView(int debugView) {
        taaDebugView = Math.max(0, Math.min(5, debugView));
    }

    void setLightingParameters(
            float[] dirDir,
            float[] dirColor,
            float dirIntensity,
            float[] shadowPointPos,
            float[] shadowPointDirection,
            boolean shadowPointIsSpot,
            float shadowPointOuterCos,
            float shadowPointRange,
            boolean shadowPointCastsShadows,
            int localCount,
            float[] localPosRange,
            float[] localColorIntensity,
            float[] localDirInner,
            float[] localOuterTypeShadow
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
        if (shadowPointPos != null && shadowPointPos.length == 3) {
            pointLightPosX = shadowPointPos[0];
            pointLightPosY = shadowPointPos[1];
            pointLightPosZ = shadowPointPos[2];
        }
        if (shadowPointDirection != null && shadowPointDirection.length == 3) {
            float[] normalized = normalize3(shadowPointDirection[0], shadowPointDirection[1], shadowPointDirection[2]);
            pointLightDirX = normalized[0];
            pointLightDirY = normalized[1];
            pointLightDirZ = normalized[2];
        }
        pointLightOuterCos = clamp01(shadowPointOuterCos);
        pointLightIsSpot = shadowPointIsSpot ? 1f : 0f;
        pointShadowFarPlane = Math.max(1.0f, shadowPointRange);
        pointShadowEnabled = shadowPointCastsShadows;
        localLightCount = Math.max(0, Math.min(MAX_LOCAL_LIGHTS, localCount));
        for (int i = 0; i < localLightPosRange.length; i++) {
            localLightPosRange[i] = 0f;
            localLightColorIntensity[i] = 0f;
            localLightDirInner[i] = 0f;
            localLightOuterTypeShadow[i] = 0f;
        }
        if (localPosRange != null) {
            System.arraycopy(localPosRange, 0, localLightPosRange, 0, Math.min(localPosRange.length, localLightPosRange.length));
        }
        if (localColorIntensity != null) {
            System.arraycopy(localColorIntensity, 0, localLightColorIntensity, 0, Math.min(localColorIntensity.length, localLightColorIntensity.length));
        }
        if (localDirInner != null) {
            System.arraycopy(localDirInner, 0, localLightDirInner, 0, Math.min(localDirInner.length, localLightDirInner.length));
        }
        if (localOuterTypeShadow != null) {
            System.arraycopy(localOuterTypeShadow, 0, localLightOuterTypeShadow, 0, Math.min(localOuterTypeShadow.length, localLightOuterTypeShadow.length));
        }
    }

    void setShadowParameters(
            boolean enabled,
            float strength,
            float bias,
            float normalBiasScale,
            float slopeBiasScale,
            int pcfRadius,
            int cascadeCount,
            int mapResolution,
            int selectedLocalShadowLights
    ) {
        shadowEnabled = enabled;
        shadowStrength = Math.max(0f, Math.min(1f, strength));
        shadowBias = Math.max(0.00001f, bias);
        shadowNormalBiasScale = Math.max(0.25f, Math.min(4.0f, normalBiasScale));
        shadowSlopeBiasScale = Math.max(0.25f, Math.min(4.0f, slopeBiasScale));
        shadowPcfRadius = Math.max(0, pcfRadius);
        shadowCascadeCount = Math.max(1, cascadeCount);
        int clampedLocalBudget = Math.max(0, Math.min(MAX_LOCAL_SHADOWS, selectedLocalShadowLights));
        shadowRenderer.setLocalShadowBudget(clampedLocalBudget);
        int clampedResolution = Math.max(256, Math.min(4096, mapResolution));
        if (shadowMapResolution != clampedResolution) {
            shadowMapResolution = clampedResolution;
            shadowRenderer.recreateShadowResources(shadowMapResolution);
        }
    }

    void setClearColor(float r, float g, float b) {
        clearColorR = r;
        clearColorG = g;
        clearColorB = b;
    }

    void setAmbientLight(float r, float g, float b, float intensity) {
        ambientColorR = r;
        ambientColorG = g;
        ambientColorB = b;
        ambientIntensity = Math.max(0f, intensity);
    }

    void setShadowOrthoSize(float halfExtent, float farPlane) {
        shadowOrthoSize = Math.max(1f, halfExtent);
        shadowFarPlane = Math.max(1f, farPlane);
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
        Map<String, float[]> previousModelByMeshId = new HashMap<>();
        for (MeshBuffer mesh : sceneMeshes) {
            previousModelByMeshId.put(mesh.meshId, mesh.modelMatrix.clone());
        }
        clearSceneMeshes();
        List<SceneMesh> effectiveMeshes = meshes == null || meshes.isEmpty()
                ? List.of(new SceneMesh(
                "default-triangle",
                defaultTriangleGeometry(),
                identityMatrix(),
                new float[]{1f, 1f, 1f},
                0.0f,
                0.6f,
                0f,
                false,
                false,
                1.0f,
                1.0f,
                1.0f,
                0f,
                null,
                null,
                null,
                null
        ))
                : meshes;
        for (SceneMesh mesh : effectiveMeshes) {
            float[] prevModel = previousModelByMeshId.get(mesh.meshId());
            sceneMeshes.add(uploadMesh(mesh, prevModel == null ? mesh.modelMatrix().clone() : prevModel));
        }
        shadowRenderer.invalidateShadowCaches();
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
        vertexFormatLocation = glGetUniformLocation(programId, "uVertexFormat");
        modelLocation = glGetUniformLocation(programId, "uModel");
        prevModelLocation = glGetUniformLocation(programId, "uPrevModel");
        viewLocation = glGetUniformLocation(programId, "uView");
        projLocation = glGetUniformLocation(programId, "uProj");
        currentViewProjLocation = glGetUniformLocation(programId, "uCurrentViewProj");
        prevViewProjLocation = glGetUniformLocation(programId, "uPrevViewProj");
        lightViewProjLocation = glGetUniformLocation(programId, "uLightViewProj");
        materialAlbedoLocation = glGetUniformLocation(programId, "uMaterialAlbedo");
        materialMetallicLocation = glGetUniformLocation(programId, "uMaterialMetallic");
        materialRoughnessLocation = glGetUniformLocation(programId, "uMaterialRoughness");
        materialReactiveLocation = glGetUniformLocation(programId, "uMaterialReactive");
        materialReactiveTuningLocation = glGetUniformLocation(programId, "uMaterialReactiveTuning");
        useAlbedoTextureLocation = glGetUniformLocation(programId, "uUseAlbedoTexture");
        albedoTextureLocation = glGetUniformLocation(programId, "uAlbedoTexture");
        useNormalTextureLocation = glGetUniformLocation(programId, "uUseNormalTexture");
        normalTextureLocation = glGetUniformLocation(programId, "uNormalTexture");
        useMetallicRoughnessTextureLocation = glGetUniformLocation(programId, "uUseMetallicRoughnessTexture");
        metallicRoughnessTextureLocation = glGetUniformLocation(programId, "uMetallicRoughnessTexture");
        useOcclusionTextureLocation = glGetUniformLocation(programId, "uUseOcclusionTexture");
        occlusionTextureLocation = glGetUniformLocation(programId, "uOcclusionTexture");
        iblIrradianceTextureLocation = glGetUniformLocation(programId, "uIblIrradiance");
        iblRadianceTextureLocation = glGetUniformLocation(programId, "uIblRadiance");
        iblBrdfLutTextureLocation = glGetUniformLocation(programId, "uIblBrdfLut");
        iblRadianceMaxLodLocation = glGetUniformLocation(programId, "uIblRadianceMaxLod");
        dirLightDirLocation = glGetUniformLocation(programId, "uDirLightDir");
        dirLightColorLocation = glGetUniformLocation(programId, "uDirLightColor");
        dirLightIntensityLocation = glGetUniformLocation(programId, "uDirLightIntensity");
        localLightCountLocation = glGetUniformLocation(programId, "uLocalLightCount");
        localLightPosRangeLocation = glGetUniformLocation(programId, "uLocalLightPosRange");
        localLightColorIntensityLocation = glGetUniformLocation(programId, "uLocalLightColorIntensity");
        localLightDirInnerLocation = glGetUniformLocation(programId, "uLocalLightDirInner");
        localLightOuterTypeShadowLocation = glGetUniformLocation(programId, "uLocalLightOuterTypeShadow");
        pointLightPosLocation = glGetUniformLocation(programId, "uPointLightPos");
        pointLightDirLocation = glGetUniformLocation(programId, "uPointLightDir");
        pointShadowEnabledLocation = glGetUniformLocation(programId, "uPointShadowEnabled");
        pointShadowMapLocation = glGetUniformLocation(programId, "uPointShadowMap");
        pointShadowFarPlaneLocation = glGetUniformLocation(programId, "uPointShadowFarPlane");
        pointShadowLightIndexLocation = glGetUniformLocation(programId, "uPointShadowLightIndex");
        shadowEnabledLocation = glGetUniformLocation(programId, "uShadowEnabled");
        shadowStrengthLocation = glGetUniformLocation(programId, "uShadowStrength");
        shadowBiasLocation = glGetUniformLocation(programId, "uShadowBias");
        shadowNormalBiasScaleLocation = glGetUniformLocation(programId, "uShadowNormalBiasScale");
        shadowSlopeBiasScaleLocation = glGetUniformLocation(programId, "uShadowSlopeBiasScale");
        shadowPcfRadiusLocation = glGetUniformLocation(programId, "uShadowPcfRadius");
        shadowCascadeCountLocation = glGetUniformLocation(programId, "uShadowCascadeCount");
        shadowMapLocation = glGetUniformLocation(programId, "uShadowMap");
        localShadowCountLocation = glGetUniformLocation(programId, "uLocalShadowCount");
        localShadowMapLocation = glGetUniformLocation(programId, "uLocalShadowMap");
        localShadowMatrixLocation = glGetUniformLocation(programId, "uLocalShadowMatrix");
        localShadowAtlasRectLocation = glGetUniformLocation(programId, "uLocalShadowAtlasRect");
        localShadowMetaLocation = glGetUniformLocation(programId, "uLocalShadowMeta");
        fogEnabledLocation = glGetUniformLocation(programId, "uFogEnabled");
        fogColorLocation = glGetUniformLocation(programId, "uFogColor");
        fogDensityLocation = glGetUniformLocation(programId, "uFogDensity");
        fogStepsLocation = glGetUniformLocation(programId, "uFogSteps");
        smokeEnabledLocation = glGetUniformLocation(programId, "uSmokeEnabled");
        smokeColorLocation = glGetUniformLocation(programId, "uSmokeColor");
        smokeIntensityLocation = glGetUniformLocation(programId, "uSmokeIntensity");
        viewportSizeLocation = glGetUniformLocation(programId, "uViewportSize");
        iblParamsLocation = glGetUniformLocation(programId, "uIblParams");
        tonemapEnabledLocation = glGetUniformLocation(programId, "uTonemapEnabled");
        tonemapExposureLocation = glGetUniformLocation(programId, "uTonemapExposure");
        tonemapGammaLocation = glGetUniformLocation(programId, "uTonemapGamma");
        bloomEnabledLocation = glGetUniformLocation(programId, "uBloomEnabled");
        bloomThresholdLocation = glGetUniformLocation(programId, "uBloomThreshold");
        bloomStrengthLocation = glGetUniformLocation(programId, "uBloomStrength");
        ssaoEnabledLocation = glGetUniformLocation(programId, "uSsaoEnabled");
        ssaoStrengthLocation = glGetUniformLocation(programId, "uSsaoStrength");
        ssaoRadiusLocation = glGetUniformLocation(programId, "uSsaoRadius");
        ssaoBiasLocation = glGetUniformLocation(programId, "uSsaoBias");
        ssaoPowerLocation = glGetUniformLocation(programId, "uSsaoPower");
        smaaEnabledLocation = glGetUniformLocation(programId, "uSmaaEnabled");
        smaaStrengthLocation = glGetUniformLocation(programId, "uSmaaStrength");
        taaEnabledLocation = glGetUniformLocation(programId, "uTaaEnabled");
        taaBlendLocation = glGetUniformLocation(programId, "uTaaBlend");
        ambientColorLocation = glGetUniformLocation(programId, "uAmbientColor");
        ambientIntensityLocation = glGetUniformLocation(programId, "uAmbientIntensity");
        alphaCutoffLocation = glGetUniformLocation(programId, "uAlphaCutoff");

        glUseProgram(programId);
        glUniform1i(albedoTextureLocation, 0);
        glUniform1i(normalTextureLocation, 1);
        glUniform1i(metallicRoughnessTextureLocation, 2);
        glUniform1i(occlusionTextureLocation, 3);
        glUniform1i(shadowMapLocation, 4);
        glUniform1i(iblIrradianceTextureLocation, 5);
        glUniform1i(iblRadianceTextureLocation, 6);
        glUniform1i(iblBrdfLutTextureLocation, 7);
        glUniform1i(pointShadowMapLocation, 8);
        glUniform1i(localShadowMapLocation, 9);
        glUseProgram(0);
    }

    // Post-processing pipeline methods extracted to OpenGlPostProcessor (step 5 decomposition).

    private MeshBuffer uploadMesh(SceneMesh mesh, float[] prevModelMatrix) {
        int vaoId = glGenVertexArrays();
        int vboId = glGenBuffers();
        VertexFormat fmt = mesh.geometry().format();
        int formatInt = fmt == VertexFormat.POS_NORMAL_UV_8F ? 1 : 0;

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, mesh.geometry().vertices(), GL_STATIC_DRAW);

        if (fmt == VertexFormat.POS_NORMAL_UV_8F) {
            int stride = 8 * Float.BYTES;
            glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 6L * Float.BYTES);
            glEnableVertexAttribArray(2);
        } else {
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.BYTES, 0L);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * Float.BYTES, 3L * Float.BYTES);
            glEnableVertexAttribArray(1);
        }

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        OpenGlTextureLoader.TextureData albedoTexture = mesh.preloadedAlbedoTextureId() != 0
                ? new OpenGlTextureLoader.TextureData(mesh.preloadedAlbedoTextureId(), 0, 0)
                : textureLoader.loadTexture(mesh.albedoTexturePath(), true);
        OpenGlTextureLoader.TextureData normalTexture = mesh.preloadedNormalTextureId() != 0
                ? new OpenGlTextureLoader.TextureData(mesh.preloadedNormalTextureId(), 0, 0)
                : textureLoader.loadTexture(mesh.normalTexturePath());
        OpenGlTextureLoader.TextureData metallicRoughnessTexture = mesh.preloadedMetallicRoughnessTextureId() != 0
                ? new OpenGlTextureLoader.TextureData(mesh.preloadedMetallicRoughnessTextureId(), 0, 0)
                : textureLoader.loadTexture(mesh.metallicRoughnessTexturePath());
        OpenGlTextureLoader.TextureData occlusionTexture = mesh.preloadedOcclusionTextureId() != 0
                ? new OpenGlTextureLoader.TextureData(mesh.preloadedOcclusionTextureId(), 0, 0)
                : textureLoader.loadTexture(mesh.occlusionTexturePath());
        long vertexBytes = (long) mesh.geometry().vertices().length * Float.BYTES;
        return new MeshBuffer(
                vaoId,
                vboId,
                mesh.geometry().vertexCount(),
                formatInt,
                mesh.meshId(),
                mesh.modelMatrix().clone(),
                prevModelMatrix.clone(),
                mesh.albedoColor().clone(),
                clamp01(mesh.metallic()),
                clamp01(mesh.roughness()),
                clamp01(mesh.reactiveStrength()),
                mesh.alphaTested(),
                mesh.alphaCutoff(),
                mesh.foliage(),
                Math.max(0f, Math.min(2.0f, mesh.reactiveBoost())),
                clamp01(mesh.taaHistoryClamp()),
                Math.max(0f, Math.min(3.0f, mesh.emissiveReactiveBoost())),
                Math.max(0f, Math.min(3.0f, mesh.reactivePreset())),
                albedoTexture.id(),
                normalTexture.id(),
                metallicRoughnessTexture.id(),
                occlusionTexture.id(),
                vertexBytes,
                albedoTexture.bytes(),
                normalTexture.bytes(),
                metallicRoughnessTexture.bytes(),
                occlusionTexture.bytes()
        );
    }

    // Texture loading methods extracted to OpenGlTextureLoader (step 3 decomposition).

    int loadTextureFromMemory(ByteBuffer imageData) {
        return textureLoader.loadTextureFromMemory(imageData);
    }

    int loadTextureFromMemory(ByteBuffer imageData, boolean sRgb) {
        return textureLoader.loadTextureFromMemory(imageData, sRgb);
    }

    private void clearSceneMeshes() {
        for (MeshBuffer mesh : sceneMeshes) {
            if (mesh.textureId != 0) {
                glDeleteTextures(mesh.textureId);
            }
            if (mesh.normalTextureId != 0) {
                glDeleteTextures(mesh.normalTextureId);
            }
            if (mesh.metallicRoughnessTextureId != 0) {
                glDeleteTextures(mesh.metallicRoughnessTextureId);
            }
            if (mesh.occlusionTextureId != 0) {
                glDeleteTextures(mesh.occlusionTextureId);
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
            bytes += mesh.metallicRoughnessTextureBytes;
            bytes += mesh.occlusionTextureBytes;
        }
        if (shadowRenderer.shadowDepthTextureId() != 0) {
            bytes += (long) shadowMapResolution * (long) shadowMapResolution * 4L;
        }
        if (shadowRenderer.localShadowDepthTextureId() != 0) {
            bytes += (long) shadowMapResolution * (long) shadowMapResolution * 4L;
        }
        if (shadowRenderer.pointShadowDepthTextureId() != 0) {
            bytes += (long) shadowMapResolution * (long) shadowMapResolution * 4L * 6L;
        }
        return bytes;
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
        glUniform2f(viewportSizeLocation, Math.max(1, sceneRenderWidth), Math.max(1, sceneRenderHeight));
    }

    private void applyPostProcessUniforms(boolean shaderDrivenEnabled) {
        glUniform1i(tonemapEnabledLocation, shaderDrivenEnabled && tonemapEnabled ? 1 : 0);
        glUniform1f(tonemapExposureLocation, tonemapExposure);
        glUniform1f(tonemapGammaLocation, tonemapGamma);
        glUniform1i(bloomEnabledLocation, shaderDrivenEnabled && bloomEnabled ? 1 : 0);
        glUniform1f(bloomThresholdLocation, bloomThreshold);
        glUniform1f(bloomStrengthLocation, bloomStrength);
        glUniform1i(ssaoEnabledLocation, shaderDrivenEnabled && ssaoEnabled ? 1 : 0);
        glUniform1f(ssaoStrengthLocation, ssaoStrength);
        glUniform1f(ssaoRadiusLocation, ssaoRadius);
        glUniform1f(ssaoBiasLocation, ssaoBias);
        glUniform1f(ssaoPowerLocation, ssaoPower);
        glUniform1i(smaaEnabledLocation, shaderDrivenEnabled && smaaEnabled ? 1 : 0);
        glUniform1f(smaaStrengthLocation, smaaStrength);
        glUniform1i(taaEnabledLocation, shaderDrivenEnabled && taaEnabled ? 1 : 0);
        glUniform1f(taaBlendLocation, taaBlend);
    }

    private boolean useDedicatedPostPass() {
        return postProcessor.useDedicatedPostPass(
                tonemapEnabled, bloomEnabled, ssaoEnabled, smaaEnabled, taaEnabled, reflectionsEnabled);
    }

    private void updateSceneRenderResolution() {
        if (!useDedicatedPostPass()) {
            sceneRenderWidth = Math.max(1, width);
            sceneRenderHeight = Math.max(1, height);
            return;
        }
        float scale = taaEnabled ? taaRenderScale : 1.0f;
        sceneRenderWidth = Math.max(1, Math.round(Math.max(1, width) * scale));
        sceneRenderHeight = Math.max(1, Math.round(Math.max(1, height) * scale));
    }

    private int activeRenderWidth() {
        return useDedicatedPostPass() ? sceneRenderWidth : width;
    }

    private int activeRenderHeight() {
        return useDedicatedPostPass() ? sceneRenderHeight : height;
    }

    // TAA jitter/motion/history methods extracted to OpenGlTemporalAA (step 6 decomposition).

    private void syncFramebufferSizeFromWindow() {
        if (window == 0) {
            return;
        }
        int fbWidth;
        int fbHeight;
        int pw = pendingFramebufferWidth;
        int ph = pendingFramebufferHeight;
        if (pw > 0 && ph > 0) {
            fbWidth = pw;
            fbHeight = ph;
            pendingFramebufferWidth = 0;
            pendingFramebufferHeight = 0;
        } else {
            int[] drawable = queryDrawableSize(width, height);
            fbWidth = drawable[0];
            fbHeight = drawable[1];
        }
        if (fbWidth != width || fbHeight != height) {
            resize(fbWidth, fbHeight);
        }
    }

    private int[] queryDrawableSize(int fallbackWidth, int fallbackHeight) {
        int targetWidth = Math.max(1, fallbackWidth);
        int targetHeight = Math.max(1, fallbackHeight);
        if (window == 0) {
            return new int[]{targetWidth, targetHeight};
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var pFbWidth = stack.mallocInt(1);
            var pFbHeight = stack.mallocInt(1);
            GLFW.glfwGetFramebufferSize(window, pFbWidth, pFbHeight);

            var pWinWidth = stack.mallocInt(1);
            var pWinHeight = stack.mallocInt(1);
            GLFW.glfwGetWindowSize(window, pWinWidth, pWinHeight);

            var pScaleX = stack.mallocFloat(1);
            var pScaleY = stack.mallocFloat(1);
            GLFW.glfwGetWindowContentScale(window, pScaleX, pScaleY);

            int fbWidth = Math.max(1, pFbWidth.get(0));
            int fbHeight = Math.max(1, pFbHeight.get(0));
            int scaledWinWidth = Math.max(1, Math.round(Math.max(1, pWinWidth.get(0)) * Math.max(1.0f, pScaleX.get(0))));
            int scaledWinHeight = Math.max(1, Math.round(Math.max(1, pWinHeight.get(0)) * Math.max(1.0f, pScaleY.get(0))));

            targetWidth = Math.max(targetWidth, Math.max(fbWidth, scaledWinWidth));
            targetHeight = Math.max(targetHeight, Math.max(fbHeight, scaledWinHeight));
        }
        return new int[]{targetWidth, targetHeight};
    }


    private boolean useShaderDrivenPost() {
        return !useDedicatedPostPass();
    }

    // Shadow rendering methods extracted to OpenGlShadowRenderer (step 4 decomposition).

    private void initializeGpuQuerySupport() {
        var caps = GL.getCapabilities();
        gpuTimerQuerySupported = caps.OpenGL33 || caps.GL_ARB_timer_query;
        if (gpuTimerQuerySupported) {
            gpuTimeQueryId = glGenQueries();
        }
        if (caps.GL_EXT_texture_filter_anisotropic) {
            maxAnisotropy = org.lwjgl.opengl.GL11.glGetFloat(
                    org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
        }
        textureLoader = new OpenGlTextureLoader(maxAnisotropy);
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
