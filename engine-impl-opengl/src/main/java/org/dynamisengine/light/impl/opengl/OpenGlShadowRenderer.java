package org.dynamisengine.light.impl.opengl;

import static org.dynamisengine.light.impl.opengl.GlMathUtil.*;
import static org.dynamisengine.light.impl.opengl.GlShaderSources.*;

import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_NONE;
import static org.lwjgl.opengl.GL11.GL_POLYGON_OFFSET_FILL;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_BORDER_COLOR;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glDrawBuffer;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glPolygonOffset;
import static org.lwjgl.opengl.GL11.glReadBuffer;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameterfv;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE_CUBE_MAP;
import static org.lwjgl.opengl.GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X;
import static org.lwjgl.opengl.GL14.GL_TEXTURE_COMPARE_MODE;
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
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glCheckFramebufferStatus;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL30.glGenFramebuffers;

import java.util.List;
import org.dynamisengine.light.api.error.EngineErrorCode;
import org.dynamisengine.light.api.error.EngineException;

/**
 * Manages shadow map rendering: directional, point (cubemap), and local spot shadow atlas.
 * Extracted from OpenGlContext (step 4 decomposition).
 */
final class OpenGlShadowRenderer {

    private static final int MAX_LOCAL_SHADOWS = OpenGlContext.MAX_LOCAL_SHADOWS;

    // Shadow shader program state
    private int shadowProgramId;
    private int shadowModelLocation;
    private int shadowLightViewProjLocation;

    // Directional shadow resources
    private int shadowFramebufferId;
    private int shadowDepthTextureId;

    // Point shadow resources
    private int pointShadowFramebufferId;
    private int pointShadowDepthTextureId;

    // Local shadow atlas resources
    private int localShadowFramebufferId;
    private int localShadowDepthTextureId;

    // Light view-projection matrix for directional shadows
    private float[] lightViewProjMatrix = identityMatrix();

    // Shadow matrix cache key
    private long shadowMatrixStateKey = Long.MIN_VALUE;

    // Local shadow budget and tracking
    private int localShadowBudget;
    private int localShadowCount;
    private final float[] localShadowMatrices = new float[MAX_LOCAL_SHADOWS * 16];
    private final float[] localShadowAtlasRects = new float[MAX_LOCAL_SHADOWS * 4];
    private final float[] localShadowMeta = new float[MAX_LOCAL_SHADOWS * 4];
    private final int[] localShadowSlotLightIndex = new int[MAX_LOCAL_SHADOWS];
    private final int[] localShadowSlotLastUpdateFrame = new int[MAX_LOCAL_SHADOWS];

    OpenGlShadowRenderer() {
        for (int i = 0; i < localShadowSlotLightIndex.length; i++) {
            localShadowSlotLightIndex[i] = -1;
            localShadowSlotLastUpdateFrame[i] = Integer.MIN_VALUE / 2;
        }
    }

    void initializeShadowPipeline() throws EngineException {
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

    void recreateShadowResources(int shadowMapResolution) {
        destroyShadowResources();
        invalidateShadowCaches();
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

        pointShadowDepthTextureId = glGenTextures();
        glBindTexture(GL_TEXTURE_CUBE_MAP, pointShadowDepthTextureId);
        for (int i = 0; i < 6; i++) {
            glTexImage2D(
                    GL_TEXTURE_CUBE_MAP_POSITIVE_X + i,
                    0,
                    GL_DEPTH_COMPONENT,
                    shadowMapResolution,
                    shadowMapResolution,
                    0,
                    GL_DEPTH_COMPONENT,
                    GL_FLOAT,
                    0L
            );
        }
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, org.lwjgl.opengl.GL12.GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_CUBE_MAP, 0);

        pointShadowFramebufferId = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, pointShadowFramebufferId);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_CUBE_MAP_POSITIVE_X, pointShadowDepthTextureId, 0);
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        int pointStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        if (pointStatus != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Point shadow framebuffer incomplete: status=" + pointStatus);
        }

        localShadowDepthTextureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, localShadowDepthTextureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT, shadowMapResolution, shadowMapResolution, 0, GL_DEPTH_COMPONENT, GL_FLOAT, 0L);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_NONE);
        glTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);

        localShadowFramebufferId = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, localShadowFramebufferId);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, localShadowDepthTextureId, 0);
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        int localStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        if (localStatus != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Local shadow framebuffer incomplete: status=" + localStatus);
        }
    }

    void destroyShadowResources() {
        if (shadowFramebufferId != 0) {
            glDeleteFramebuffers(shadowFramebufferId);
            shadowFramebufferId = 0;
        }
        if (shadowDepthTextureId != 0) {
            glDeleteTextures(shadowDepthTextureId);
            shadowDepthTextureId = 0;
        }
        if (pointShadowFramebufferId != 0) {
            glDeleteFramebuffers(pointShadowFramebufferId);
            pointShadowFramebufferId = 0;
        }
        if (pointShadowDepthTextureId != 0) {
            glDeleteTextures(pointShadowDepthTextureId);
            pointShadowDepthTextureId = 0;
        }
        if (localShadowFramebufferId != 0) {
            glDeleteFramebuffers(localShadowFramebufferId);
            localShadowFramebufferId = 0;
        }
        if (localShadowDepthTextureId != 0) {
            glDeleteTextures(localShadowDepthTextureId);
            localShadowDepthTextureId = 0;
        }
    }

    void invalidateShadowCaches() {
        shadowMatrixStateKey = Long.MIN_VALUE;
        localShadowCount = 0;
        for (int i = 0; i < localShadowSlotLightIndex.length; i++) {
            localShadowSlotLightIndex[i] = -1;
            localShadowSlotLastUpdateFrame[i] = Integer.MIN_VALUE / 2;
        }
        java.util.Arrays.fill(localShadowMatrices, 0f);
        java.util.Arrays.fill(localShadowAtlasRects, 0f);
        java.util.Arrays.fill(localShadowMeta, 0f);
    }

    void renderShadowPass(
            boolean shadowEnabled,
            int shadowMapResolution,
            List<OpenGlContext.MeshBuffer> sceneMeshes,
            float pointLightIsSpot,
            float pointLightPosX, float pointLightPosY, float pointLightPosZ,
            float pointLightDirX, float pointLightDirY, float pointLightDirZ,
            float pointLightOuterCos,
            float dirLightDirX, float dirLightDirY, float dirLightDirZ,
            int shadowCascadeCount,
            float shadowOrthoSize, float shadowFarPlane
    ) {
        if (!shadowEnabled || shadowFramebufferId == 0 || shadowProgramId == 0) {
            return;
        }
        updateLightViewProjMatrix(
                pointLightIsSpot,
                pointLightPosX, pointLightPosY, pointLightPosZ,
                pointLightDirX, pointLightDirY, pointLightDirZ,
                pointLightOuterCos,
                dirLightDirX, dirLightDirY, dirLightDirZ,
                shadowCascadeCount,
                shadowOrthoSize, shadowFarPlane, shadowMapResolution
        );
        glViewport(0, 0, shadowMapResolution, shadowMapResolution);
        glBindFramebuffer(GL_FRAMEBUFFER, shadowFramebufferId);
        glClear(GL_DEPTH_BUFFER_BIT);
        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(2.0f, 4.0f);
        glUseProgram(shadowProgramId);
        glUniformMatrix4fv(shadowLightViewProjLocation, false, lightViewProjMatrix);
        for (OpenGlContext.MeshBuffer mesh : sceneMeshes) {
            glUniformMatrix4fv(shadowModelLocation, false, mesh.modelMatrix);
            glBindVertexArray(mesh.vaoId);
            glDrawArrays(GL_TRIANGLES, 0, mesh.vertexCount);
        }
        glBindVertexArray(0);
        glUseProgram(0);
        glDisable(GL_POLYGON_OFFSET_FILL);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /**
     * Renders the local spot shadow atlas pass.
     *
     * @return the updated pointShadowLightIndex value (set to -1 at start of this method in original code,
     *         but that was actually done in renderPointShadowPass; here we just handle local shadows).
     */
    void renderLocalShadowAtlasPass(
            boolean shadowEnabled,
            int shadowMapResolution,
            List<OpenGlContext.MeshBuffer> sceneMeshes,
            int localLightCount,
            float[] localLightPosRange,
            float[] localLightDirInner,
            float[] localLightOuterTypeShadow,
            int frameCounter
    ) {
        localShadowCount = 0;
        for (int i = 0; i < localShadowSlotLightIndex.length; i++) {
            localShadowSlotLightIndex[i] = -1;
            localShadowMeta[(i * 4)] = -1f;
            localShadowMeta[(i * 4) + 1] = 0f;
            localShadowMeta[(i * 4) + 2] = 0f;
            localShadowMeta[(i * 4) + 3] = 0f;
        }
        if (!shadowEnabled || localShadowFramebufferId == 0 || shadowProgramId == 0 || localShadowBudget <= 0) {
            return;
        }

        int selected = 0;
        for (int i = 0; i < localLightCount && selected < Math.min(localShadowBudget, MAX_LOCAL_SHADOWS); i++) {
            int offset = i * 4;
            float isSpot = localLightOuterTypeShadow[offset + 1];
            float castsShadows = localLightOuterTypeShadow[offset + 2];
            if (isSpot <= 0.5f || castsShadows <= 0.5f) {
                continue;
            }
            localShadowSlotLightIndex[selected] = i;
            selected++;
        }
        localShadowCount = selected;
        if (localShadowCount == 0) {
            return;
        }

        glBindFramebuffer(GL_FRAMEBUFFER, localShadowFramebufferId);
        glUseProgram(shadowProgramId);
        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(2.0f, 4.0f);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        int cols = localShadowCount <= 1 ? 1 : 2;
        int rows = (int) Math.ceil(localShadowCount / (double) cols);
        int tileW = shadowMapResolution / cols;
        int tileH = shadowMapResolution / rows;

        for (int slot = 0; slot < localShadowCount; slot++) {
            int lightIndex = localShadowSlotLightIndex[slot];
            if (lightIndex < 0) {
                continue;
            }
            int interval = slot == 0 ? 1 : (slot == 1 ? 2 : 4);
            boolean shouldUpdate = (frameCounter - localShadowSlotLastUpdateFrame[slot]) >= interval;
            int col = slot % cols;
            int row = slot / cols;
            int x = col * tileW;
            int y = row * tileH;
            int offset = lightIndex * 4;

            localShadowAtlasRects[(slot * 4)] = x / (float) shadowMapResolution;
            localShadowAtlasRects[(slot * 4) + 1] = y / (float) shadowMapResolution;
            localShadowAtlasRects[(slot * 4) + 2] = tileW / (float) shadowMapResolution;
            localShadowAtlasRects[(slot * 4) + 3] = tileH / (float) shadowMapResolution;
            localShadowMeta[(slot * 4)] = lightIndex;
            localShadowMeta[(slot * 4) + 1] = 1f;
            localShadowMeta[(slot * 4) + 2] = interval;
            localShadowMeta[(slot * 4) + 3] = 0f;

            if (!shouldUpdate) {
                continue;
            }

            float posX = localLightPosRange[offset];
            float posY = localLightPosRange[offset + 1];
            float posZ = localLightPosRange[offset + 2];
            float range = Math.max(localLightPosRange[offset + 3], 1.0f);
            float dirX = localLightDirInner[offset];
            float dirY = localLightDirInner[offset + 1];
            float dirZ = localLightDirInner[offset + 2];
            float outerCos = clamp01(localLightOuterTypeShadow[offset]);
            float outerAngle = (float) Math.toDegrees(Math.acos(Math.max(0.001f, outerCos)));
            float fov = Math.max(20f, Math.min(120f, outerAngle * 2.0f));
            float[] lightProj = perspective((float) Math.toRadians(fov), 1f, 0.1f, range);
            float upX = Math.abs(dirY) > 0.95f ? 1f : 0f;
            float upY = Math.abs(dirY) > 0.95f ? 0f : 1f;
            float upZ = 0f;
            float[] lightView = lookAt(
                    posX, posY, posZ,
                    posX + dirX, posY + dirY, posZ + dirZ,
                    upX, upY, upZ
            );
            float[] lightVp = mul(lightProj, lightView);
            System.arraycopy(lightVp, 0, localShadowMatrices, slot * 16, 16);

            glViewport(x, y, tileW, tileH);
            org.lwjgl.opengl.GL11.glScissor(x, y, tileW, tileH);
            glClear(GL_DEPTH_BUFFER_BIT);
            glUniformMatrix4fv(shadowLightViewProjLocation, false, lightVp);
            for (OpenGlContext.MeshBuffer mesh : sceneMeshes) {
                glUniformMatrix4fv(shadowModelLocation, false, mesh.modelMatrix);
                glBindVertexArray(mesh.vaoId);
                glDrawArrays(GL_TRIANGLES, 0, mesh.vertexCount);
            }
            localShadowSlotLastUpdateFrame[slot] = frameCounter;
        }

        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        glDisable(GL_POLYGON_OFFSET_FILL);
        glBindVertexArray(0);
        glUseProgram(0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /**
     * Renders point light cubemap shadow pass.
     *
     * @return a {@link PointShadowResult} containing the updated point shadow state fields.
     */
    PointShadowResult renderPointShadowPass(
            boolean shadowEnabled,
            int shadowMapResolution,
            List<OpenGlContext.MeshBuffer> sceneMeshes,
            int localLightCount,
            float[] localLightPosRange,
            float[] localLightOuterTypeShadow,
            int frameCounter
    ) {
        if (!shadowEnabled || pointShadowFramebufferId == 0 || pointShadowDepthTextureId == 0 || shadowProgramId == 0 || localShadowBudget <= 0) {
            return new PointShadowResult(false, -1, 0f, 0f, 0f, 15f);
        }
        int[] pointCandidates = new int[OpenGlContext.MAX_LOCAL_LIGHTS];
        int pointCandidateCount = 0;
        for (int i = 0; i < localLightCount; i++) {
            int offset = i * 4;
            float isSpot = localLightOuterTypeShadow[offset + 1];
            float castsShadows = localLightOuterTypeShadow[offset + 2];
            if (isSpot > 0.5f || castsShadows <= 0.5f) {
                continue;
            }
            pointCandidates[pointCandidateCount++] = i;
        }
        if (pointCandidateCount == 0) {
            return new PointShadowResult(false, -1, 0f, 0f, 0f, 15f);
        }
        int cadenceDivisor = Math.max(1, localShadowBudget);
        int candidateOffset = (frameCounter / cadenceDivisor) % pointCandidateCount;
        int selectedLight = pointCandidates[candidateOffset];
        int selectedOffset = selectedLight * 4;
        float posX = localLightPosRange[selectedOffset];
        float posY = localLightPosRange[selectedOffset + 1];
        float posZ = localLightPosRange[selectedOffset + 2];
        float farPlane = Math.max(1.0f, localLightPosRange[selectedOffset + 3]);

        float[] lightProj = perspective((float) Math.toRadians(90.0), 1f, 0.1f, farPlane);
        float[][] directions = new float[][]{
                {1f, 0f, 0f}, {-1f, 0f, 0f},
                {0f, 1f, 0f}, {0f, -1f, 0f},
                {0f, 0f, 1f}, {0f, 0f, -1f}
        };
        float[][] ups = new float[][]{
                {0f, -1f, 0f}, {0f, -1f, 0f},
                {0f, 0f, 1f}, {0f, 0f, -1f},
                {0f, -1f, 0f}, {0f, -1f, 0f}
        };
        glViewport(0, 0, shadowMapResolution, shadowMapResolution);
        glBindFramebuffer(GL_FRAMEBUFFER, pointShadowFramebufferId);
        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(2.0f, 4.0f);
        glUseProgram(shadowProgramId);
        for (int face = 0; face < 6; face++) {
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_CUBE_MAP_POSITIVE_X + face, pointShadowDepthTextureId, 0);
            glClear(GL_DEPTH_BUFFER_BIT);
            float[] dir = directions[face];
            float[] up = ups[face];
            float[] lightView = lookAt(
                    posX, posY, posZ,
                    posX + dir[0], posY + dir[1], posZ + dir[2],
                    up[0], up[1], up[2]
            );
            float[] lightVp = mul(lightProj, lightView);
            glUniformMatrix4fv(shadowLightViewProjLocation, false, lightVp);
            for (OpenGlContext.MeshBuffer mesh : sceneMeshes) {
                glUniformMatrix4fv(shadowModelLocation, false, mesh.modelMatrix);
                glBindVertexArray(mesh.vaoId);
                glDrawArrays(GL_TRIANGLES, 0, mesh.vertexCount);
            }
        }
        glBindVertexArray(0);
        glUseProgram(0);
        glDisable(GL_POLYGON_OFFSET_FILL);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        return new PointShadowResult(true, selectedLight, posX, posY, posZ, farPlane);
    }

    private void updateLightViewProjMatrix(
            float pointLightIsSpot,
            float pointLightPosX, float pointLightPosY, float pointLightPosZ,
            float pointLightDirX, float pointLightDirY, float pointLightDirZ,
            float pointLightOuterCos,
            float dirLightDirX, float dirLightDirY, float dirLightDirZ,
            int shadowCascadeCount,
            float shadowOrthoSize, float shadowFarPlane, int shadowMapResolution
    ) {
        long key = 17L;
        key = 31L * key + Float.floatToIntBits(pointLightIsSpot);
        key = 31L * key + Float.floatToIntBits(pointLightPosX);
        key = 31L * key + Float.floatToIntBits(pointLightPosY);
        key = 31L * key + Float.floatToIntBits(pointLightPosZ);
        key = 31L * key + Float.floatToIntBits(pointLightDirX);
        key = 31L * key + Float.floatToIntBits(pointLightDirY);
        key = 31L * key + Float.floatToIntBits(pointLightDirZ);
        key = 31L * key + Float.floatToIntBits(pointLightOuterCos);
        key = 31L * key + Float.floatToIntBits(dirLightDirX);
        key = 31L * key + Float.floatToIntBits(dirLightDirY);
        key = 31L * key + Float.floatToIntBits(dirLightDirZ);
        key = 31L * key + shadowCascadeCount;
        if (key == shadowMatrixStateKey) {
            return;
        }
        shadowMatrixStateKey = key;
        if (pointLightIsSpot > 0.5f) {
            float[] spotDir = normalize3(pointLightDirX, pointLightDirY, pointLightDirZ);
            float targetX = pointLightPosX + spotDir[0];
            float targetY = pointLightPosY + spotDir[1];
            float targetZ = pointLightPosZ + spotDir[2];
            float upX = 0f;
            float upY = 1f;
            float upZ = 0f;
            if (Math.abs(spotDir[1]) > 0.95f) {
                upX = 0f;
                upY = 0f;
                upZ = 1f;
            }
            float[] lightView = lookAt(pointLightPosX, pointLightPosY, pointLightPosZ, targetX, targetY, targetZ, upX, upY, upZ);
            float outerCos = Math.max(0.0001f, Math.min(1f, pointLightOuterCos));
            float coneHalfAngle = (float) Math.acos(outerCos);
            float fov = Math.max((float) Math.toRadians(20.0), Math.min((float) Math.toRadians(120.0), coneHalfAngle * 2.0f));
            float[] lightProj = perspective(fov, 1f, 0.1f, 30f);
            lightViewProjMatrix = mul(lightProj, lightView);
            return;
        }
        float len = (float) Math.sqrt(dirLightDirX * dirLightDirX + dirLightDirY * dirLightDirY + dirLightDirZ * dirLightDirZ);
        if (len < 0.0001f) {
            len = 1f;
        }
        float lx = dirLightDirX / len;
        float ly = dirLightDirY / len;
        float lz = dirLightDirZ / len;
        float halfExt = shadowOrthoSize;
        float eyeX = -lx * halfExt;
        float eyeY = -ly * halfExt;
        float eyeZ = -lz * halfExt;
        float texelWorld = (2f * halfExt) / Math.max(1.0f, shadowMapResolution);
        eyeX = snapToTexel(eyeX, texelWorld);
        eyeY = snapToTexel(eyeY, texelWorld);
        eyeZ = snapToTexel(eyeZ, texelWorld);
        float[] lightView = lookAt(eyeX, eyeY, eyeZ, 0f, 0f, 0f, 0f, 1f, 0f);
        float[] lightProj = ortho(-halfExt, halfExt, -halfExt, halfExt, 1f, shadowFarPlane);
        lightViewProjMatrix = mul(lightProj, lightView);
    }

    void destroyShadowProgram() {
        if (shadowProgramId != 0) {
            glDeleteProgram(shadowProgramId);
            shadowProgramId = 0;
        }
    }

    // --- Accessors for state that OpenGlContext needs to read ---

    float[] lightViewProjMatrix() {
        return lightViewProjMatrix;
    }

    int shadowDepthTextureId() {
        return shadowDepthTextureId;
    }

    int pointShadowDepthTextureId() {
        return pointShadowDepthTextureId;
    }

    int localShadowDepthTextureId() {
        return localShadowDepthTextureId;
    }

    int localShadowCount() {
        return localShadowCount;
    }

    float[] localShadowMatrices() {
        return localShadowMatrices;
    }

    float[] localShadowAtlasRects() {
        return localShadowAtlasRects;
    }

    float[] localShadowMeta() {
        return localShadowMeta;
    }

    int localShadowBudget() {
        return localShadowBudget;
    }

    void setLocalShadowBudget(int budget) {
        if (localShadowBudget != budget) {
            localShadowBudget = budget;
            invalidateShadowCaches();
        }
    }

    /**
     * Result of point shadow pass, carrying back updated point-light state to OpenGlContext.
     */
    record PointShadowResult(
            boolean pointShadowEnabled,
            int pointShadowLightIndex,
            float pointLightPosX,
            float pointLightPosY,
            float pointLightPosZ,
            float pointShadowFarPlane
    ) {
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
}
