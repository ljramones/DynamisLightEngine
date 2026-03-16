package org.dynamisengine.light.impl.opengl;

import static org.dynamisengine.light.impl.opengl.GlShaderSources.*;

import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
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
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT1;
import static org.lwjgl.opengl.GL30.GL_DEPTH24_STENCIL8;
import static org.lwjgl.opengl.GL30.GL_DEPTH_STENCIL_ATTACHMENT;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30.GL_RENDERBUFFER;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glBindRenderbuffer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glCheckFramebufferStatus;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30.glDeleteRenderbuffers;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glFramebufferRenderbuffer;
import static org.lwjgl.opengl.GL30.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30.glGenFramebuffers;
import static org.lwjgl.opengl.GL30.glGenRenderbuffers;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL30.glRenderbufferStorage;

import org.dynamisengine.light.api.error.EngineErrorCode;
import org.dynamisengine.light.api.error.EngineException;

/**
 * Manages the post-processing pipeline: FBO, textures, shader program, and the
 * full-screen post-process render pass (tone mapping, bloom, SSAO, SMAA, TAA,
 * reflections).
 *
 * <p>Package-private helper extracted from {@code OpenGlContext} (step 5 decomposition).
 */
final class OpenGlPostProcessor {

    // --- shader program + uniform locations ---
    private int postProgramId;
    private int postSceneColorLocation;
    private int postSceneVelocityLocation;
    private int postTonemapEnabledLocation;
    private int postTonemapExposureLocation;
    private int postTonemapGammaLocation;
    private int postBloomEnabledLocation;
    private int postBloomThresholdLocation;
    private int postBloomStrengthLocation;
    private int postSsaoEnabledLocation;
    private int postSsaoStrengthLocation;
    private int postSsaoRadiusLocation;
    private int postSsaoBiasLocation;
    private int postSsaoPowerLocation;
    private int postSmaaEnabledLocation;
    private int postSmaaStrengthLocation;
    private int postTaaEnabledLocation;
    private int postTaaBlendLocation;
    private int postTaaHistoryValidLocation;
    private int postTaaJitterDeltaLocation;
    private int postTaaMotionUvLocation;
    private int postTaaDebugViewLocation;
    private int postTaaClipScaleLocation;
    private int postTaaLumaClipEnabledLocation;
    private int postTaaSharpenStrengthLocation;
    private int postTaaUpsampleScaleLocation;
    private int postReflectionsEnabledLocation;
    private int postReflectionsModeLocation;
    private int postReflectionsSsrStrengthLocation;
    private int postReflectionsSsrMaxRoughnessLocation;
    private int postReflectionsSsrStepScaleLocation;
    private int postReflectionsTemporalWeightLocation;
    private int postReflectionsPlanarStrengthLocation;
    private int postTaaHistoryLocation;
    private int postTaaHistoryVelocityLocation;

    // --- full-screen triangle VAO ---
    private int postVaoId;

    // --- FBO + textures ---
    private int sceneFramebufferId;
    private int sceneColorTextureId;
    private int sceneVelocityTextureId;
    private int sceneDepthRenderbufferId;
    private int taaHistoryTextureId;
    private int taaHistoryVelocityTextureId;
    private boolean taaHistoryValid;
    private boolean postProcessPipelineAvailable;

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    void initializePipeline() throws EngineException {
        int vertexShaderId = compileShader(GL_VERTEX_SHADER, POST_VERTEX_SHADER);
        int fragmentShaderId = compileShader(GL_FRAGMENT_SHADER, POST_FRAGMENT_SHADER);
        postProgramId = glCreateProgram();
        glAttachShader(postProgramId, vertexShaderId);
        glAttachShader(postProgramId, fragmentShaderId);
        glLinkProgram(postProgramId);

        if (glGetProgrami(postProgramId, GL_LINK_STATUS) == 0) {
            String info = glGetProgramInfoLog(postProgramId);
            glDeleteShader(vertexShaderId);
            glDeleteShader(fragmentShaderId);
            throw new EngineException(EngineErrorCode.SHADER_COMPILATION_FAILED, "Post shader link failed: " + info, false);
        }

        glDeleteShader(vertexShaderId);
        glDeleteShader(fragmentShaderId);
        postSceneColorLocation = glGetUniformLocation(postProgramId, "uSceneColor");
        postSceneVelocityLocation = glGetUniformLocation(postProgramId, "uSceneVelocity");
        postTonemapEnabledLocation = glGetUniformLocation(postProgramId, "uTonemapEnabled");
        postTonemapExposureLocation = glGetUniformLocation(postProgramId, "uTonemapExposure");
        postTonemapGammaLocation = glGetUniformLocation(postProgramId, "uTonemapGamma");
        postBloomEnabledLocation = glGetUniformLocation(postProgramId, "uBloomEnabled");
        postBloomThresholdLocation = glGetUniformLocation(postProgramId, "uBloomThreshold");
        postBloomStrengthLocation = glGetUniformLocation(postProgramId, "uBloomStrength");
        postSsaoEnabledLocation = glGetUniformLocation(postProgramId, "uSsaoEnabled");
        postSsaoStrengthLocation = glGetUniformLocation(postProgramId, "uSsaoStrength");
        postSsaoRadiusLocation = glGetUniformLocation(postProgramId, "uSsaoRadius");
        postSsaoBiasLocation = glGetUniformLocation(postProgramId, "uSsaoBias");
        postSsaoPowerLocation = glGetUniformLocation(postProgramId, "uSsaoPower");
        postSmaaEnabledLocation = glGetUniformLocation(postProgramId, "uSmaaEnabled");
        postSmaaStrengthLocation = glGetUniformLocation(postProgramId, "uSmaaStrength");
        postTaaEnabledLocation = glGetUniformLocation(postProgramId, "uTaaEnabled");
        postTaaBlendLocation = glGetUniformLocation(postProgramId, "uTaaBlend");
        postTaaHistoryValidLocation = glGetUniformLocation(postProgramId, "uTaaHistoryValid");
        postTaaJitterDeltaLocation = glGetUniformLocation(postProgramId, "uTaaJitterDelta");
        postTaaMotionUvLocation = glGetUniformLocation(postProgramId, "uTaaMotionUv");
        postTaaDebugViewLocation = glGetUniformLocation(postProgramId, "uTaaDebugView");
        postTaaClipScaleLocation = glGetUniformLocation(postProgramId, "uTaaClipScale");
        postTaaLumaClipEnabledLocation = glGetUniformLocation(postProgramId, "uTaaLumaClipEnabled");
        postTaaSharpenStrengthLocation = glGetUniformLocation(postProgramId, "uTaaSharpenStrength");
        postTaaUpsampleScaleLocation = glGetUniformLocation(postProgramId, "uTaaUpsampleScale");
        postReflectionsEnabledLocation = glGetUniformLocation(postProgramId, "uReflectionsEnabled");
        postReflectionsModeLocation = glGetUniformLocation(postProgramId, "uReflectionsMode");
        postReflectionsSsrStrengthLocation = glGetUniformLocation(postProgramId, "uReflectionsSsrStrength");
        postReflectionsSsrMaxRoughnessLocation = glGetUniformLocation(postProgramId, "uReflectionsSsrMaxRoughness");
        postReflectionsSsrStepScaleLocation = glGetUniformLocation(postProgramId, "uReflectionsSsrStepScale");
        postReflectionsTemporalWeightLocation = glGetUniformLocation(postProgramId, "uReflectionsTemporalWeight");
        postReflectionsPlanarStrengthLocation = glGetUniformLocation(postProgramId, "uReflectionsPlanarStrength");
        postTaaHistoryLocation = glGetUniformLocation(postProgramId, "uTaaHistory");
        postTaaHistoryVelocityLocation = glGetUniformLocation(postProgramId, "uTaaHistoryVelocity");
        postVaoId = glGenVertexArrays();
        glUseProgram(postProgramId);
        glUniform1i(postSceneColorLocation, 0);
        glUniform1i(postTaaHistoryLocation, 1);
        glUniform1i(postSceneVelocityLocation, 2);
        glUniform1i(postTaaHistoryVelocityLocation, 3);
        glUseProgram(0);
    }

    void recreateTargets(int sceneRenderWidth, int sceneRenderHeight) {
        destroyResources();
        try {
            int rw = Math.max(1, sceneRenderWidth);
            int rh = Math.max(1, sceneRenderHeight);
            sceneColorTextureId = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, sceneColorTextureId);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, rw, rh, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
            glBindTexture(GL_TEXTURE_2D, 0);

            sceneVelocityTextureId = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, sceneVelocityTextureId);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, rw, rh, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
            glBindTexture(GL_TEXTURE_2D, 0);

            taaHistoryTextureId = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, taaHistoryTextureId);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, rw, rh, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
            glBindTexture(GL_TEXTURE_2D, 0);

            taaHistoryVelocityTextureId = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, taaHistoryVelocityTextureId);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, rw, rh, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
            glBindTexture(GL_TEXTURE_2D, 0);
            taaHistoryValid = false;

            sceneDepthRenderbufferId = glGenRenderbuffers();
            glBindRenderbuffer(GL_RENDERBUFFER, sceneDepthRenderbufferId);
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, rw, rh);
            glBindRenderbuffer(GL_RENDERBUFFER, 0);

            sceneFramebufferId = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, sceneFramebufferId);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, sceneColorTextureId, 0);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, sceneVelocityTextureId, 0);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, sceneDepthRenderbufferId);
            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                org.lwjgl.opengl.GL20.glDrawBuffers(stack.ints(GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1));
            }
            int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            postProcessPipelineAvailable = status == GL_FRAMEBUFFER_COMPLETE;
        } catch (Throwable ignored) {
            postProcessPipelineAvailable = false;
        }
        if (!postProcessPipelineAvailable) {
            destroyResources();
        }
    }

    void destroyResources() {
        if (sceneFramebufferId != 0) {
            glDeleteFramebuffers(sceneFramebufferId);
            sceneFramebufferId = 0;
        }
        if (sceneColorTextureId != 0) {
            glDeleteTextures(sceneColorTextureId);
            sceneColorTextureId = 0;
        }
        if (sceneVelocityTextureId != 0) {
            glDeleteTextures(sceneVelocityTextureId);
            sceneVelocityTextureId = 0;
        }
        if (sceneDepthRenderbufferId != 0) {
            glDeleteRenderbuffers(sceneDepthRenderbufferId);
            sceneDepthRenderbufferId = 0;
        }
        if (taaHistoryTextureId != 0) {
            glDeleteTextures(taaHistoryTextureId);
            taaHistoryTextureId = 0;
        }
        if (taaHistoryVelocityTextureId != 0) {
            glDeleteTextures(taaHistoryVelocityTextureId);
            taaHistoryVelocityTextureId = 0;
        }
        taaHistoryValid = false;
        postProcessPipelineAvailable = false;
    }

    void destroyPipeline() {
        destroyResources();
        if (postVaoId != 0) {
            glDeleteVertexArrays(postVaoId);
            postVaoId = 0;
        }
        if (postProgramId != 0) {
            glDeleteProgram(postProgramId);
            postProgramId = 0;
        }
    }

    // -----------------------------------------------------------------------
    // State queries
    // -----------------------------------------------------------------------

    boolean useDedicatedPostPass(
            boolean tonemapEnabled,
            boolean bloomEnabled,
            boolean ssaoEnabled,
            boolean smaaEnabled,
            boolean taaEnabled,
            boolean reflectionsEnabled
    ) {
        return postProcessPipelineAvailable
                && postProgramId != 0
                && (tonemapEnabled || bloomEnabled || ssaoEnabled || smaaEnabled || taaEnabled || reflectionsEnabled);
    }

    int sceneFramebufferId() {
        return sceneFramebufferId;
    }

    void invalidateHistory() {
        taaHistoryValid = false;
    }

    // -----------------------------------------------------------------------
    // Render
    // -----------------------------------------------------------------------

    void renderPostProcessPass(
            int displayWidth,
            int displayHeight,
            int sceneRenderWidth,
            int sceneRenderHeight,
            boolean tonemapEnabled,
            float tonemapExposure,
            float tonemapGamma,
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
            float taaJitterUvDeltaX,
            float taaJitterUvDeltaY,
            float taaMotionUvX,
            float taaMotionUvY,
            int taaDebugView,
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
        if (!useDedicatedPostPass(tonemapEnabled, bloomEnabled, ssaoEnabled, smaaEnabled, taaEnabled, reflectionsEnabled)) {
            return;
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        glViewport(0, 0, displayWidth, displayHeight);
        glClear(GL_COLOR_BUFFER_BIT);
        glUseProgram(postProgramId);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sceneColorTextureId);
        glActiveTexture(GL_TEXTURE0 + 1);
        glBindTexture(GL_TEXTURE_2D, taaHistoryTextureId);
        glActiveTexture(GL_TEXTURE0 + 2);
        glBindTexture(GL_TEXTURE_2D, sceneVelocityTextureId);
        glActiveTexture(GL_TEXTURE0 + 3);
        glBindTexture(GL_TEXTURE_2D, taaHistoryVelocityTextureId);
        glUniform1i(postTonemapEnabledLocation, tonemapEnabled ? 1 : 0);
        glUniform1f(postTonemapExposureLocation, tonemapExposure);
        glUniform1f(postTonemapGammaLocation, tonemapGamma);
        glUniform1i(postBloomEnabledLocation, bloomEnabled ? 1 : 0);
        glUniform1f(postBloomThresholdLocation, bloomThreshold);
        glUniform1f(postBloomStrengthLocation, bloomStrength);
        glUniform1i(postSsaoEnabledLocation, ssaoEnabled ? 1 : 0);
        glUniform1f(postSsaoStrengthLocation, ssaoStrength);
        glUniform1f(postSsaoRadiusLocation, ssaoRadius);
        glUniform1f(postSsaoBiasLocation, ssaoBias);
        glUniform1f(postSsaoPowerLocation, ssaoPower);
        glUniform1i(postSmaaEnabledLocation, smaaEnabled ? 1 : 0);
        glUniform1f(postSmaaStrengthLocation, smaaStrength);
        glUniform1i(postTaaEnabledLocation, taaEnabled ? 1 : 0);
        glUniform1f(postTaaBlendLocation, taaBlend);
        glUniform1i(postTaaHistoryValidLocation, taaHistoryValid ? 1 : 0);
        glUniform2f(postTaaJitterDeltaLocation, taaJitterUvDeltaX, taaJitterUvDeltaY);
        glUniform2f(postTaaMotionUvLocation, taaMotionUvX, taaMotionUvY);
        glUniform1i(postTaaDebugViewLocation, taaDebugView);
        glUniform1f(postTaaClipScaleLocation, taaClipScale);
        glUniform1i(postTaaLumaClipEnabledLocation, taaLumaClipEnabled ? 1 : 0);
        glUniform1f(postTaaSharpenStrengthLocation, taaSharpenStrength);
        glUniform1f(postTaaUpsampleScaleLocation, taaRenderScale);
        glUniform1i(postReflectionsEnabledLocation, reflectionsEnabled ? 1 : 0);
        glUniform1i(postReflectionsModeLocation, reflectionsMode);
        glUniform1f(postReflectionsSsrStrengthLocation, reflectionsSsrStrength);
        glUniform1f(postReflectionsSsrMaxRoughnessLocation, reflectionsSsrMaxRoughness);
        glUniform1f(postReflectionsSsrStepScaleLocation, reflectionsSsrStepScale);
        glUniform1f(postReflectionsTemporalWeightLocation, reflectionsTemporalWeight);
        glUniform1f(postReflectionsPlanarStrengthLocation, reflectionsPlanarStrength);
        glDisable(GL_DEPTH_TEST);
        glBindVertexArray(postVaoId);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);
        glEnable(GL_DEPTH_TEST);
        if (taaEnabled && taaHistoryTextureId != 0 && taaHistoryVelocityTextureId != 0) {
            glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, sceneFramebufferId);
            org.lwjgl.opengl.GL11.glReadBuffer(GL_COLOR_ATTACHMENT0);
            glBindTexture(GL_TEXTURE_2D, taaHistoryTextureId);
            org.lwjgl.opengl.GL11.glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, sceneRenderWidth, sceneRenderHeight);
            glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, sceneFramebufferId);
            org.lwjgl.opengl.GL11.glReadBuffer(GL_COLOR_ATTACHMENT1);
            glBindTexture(GL_TEXTURE_2D, taaHistoryVelocityTextureId);
            org.lwjgl.opengl.GL11.glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 0, 0, sceneRenderWidth, sceneRenderHeight);
            glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, 0);
            org.lwjgl.opengl.GL11.glReadBuffer(org.lwjgl.opengl.GL11.GL_BACK);
            taaHistoryValid = true;
        }
        glActiveTexture(GL_TEXTURE0 + 1);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0 + 2);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0 + 3);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0);
        glUseProgram(0);
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

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
}
