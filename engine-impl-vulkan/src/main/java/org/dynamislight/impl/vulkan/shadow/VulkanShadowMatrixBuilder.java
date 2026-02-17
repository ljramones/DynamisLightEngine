package org.dynamislight.impl.vulkan.shadow;

import static org.dynamislight.impl.vulkan.math.VulkanMath.identityMatrix;
import static org.dynamislight.impl.vulkan.math.VulkanMath.invert;
import static org.dynamislight.impl.vulkan.math.VulkanMath.lookAt;
import static org.dynamislight.impl.vulkan.math.VulkanMath.mul;
import static org.dynamislight.impl.vulkan.math.VulkanMath.normalize3;
import static org.dynamislight.impl.vulkan.math.VulkanMath.ortho;
import static org.dynamislight.impl.vulkan.math.VulkanMath.perspective;
import static org.dynamislight.impl.vulkan.math.VulkanMath.projectionFar;
import static org.dynamislight.impl.vulkan.math.VulkanMath.projectionNear;
import static org.dynamislight.impl.vulkan.math.VulkanMath.transformPoint;
import static org.dynamislight.impl.vulkan.math.VulkanMath.unproject;
import static org.dynamislight.impl.vulkan.math.VulkanMath.viewDistanceToNdcDepth;

/**
 * A utility class for constructing shadow matrices used in Vulkan-based rendering.
 * This class handles the computation of shadow view-projection matrices for point lights,
 * spotlights, and directional light cascades to support shadow mapping in a variety
 * of lighting scenarios.
 *
 * The {@code VulkanShadowMatrixBuilder} is designed to work with light inputs such as
 * position, direction, and projection parameters. Based on the type of light and its
 * shadowing requirements, it computes appropriate transformation matrices.
 */
public final class VulkanShadowMatrixBuilder {
    private VulkanShadowMatrixBuilder() {
    }

    /**
     * Updates shadow light view projection matrices and shadow cascade split NDC based on the provided shadow inputs.
     * This method handles spotlights, point lights, and directional lights, setting up the appropriate transformations
     * for shadow mapping.
     *
     * @param inputs The input parameters related to the lighting and shadow configuration, such as light position,
     *               direction, and shadow settings.
     * @param shadowLightViewProjMatrices A 2D array to store the calculated light view projection matrices for shadows.
     *                                    Each row corresponds to a cascade or face of the shadow map.
     * @param shadowCascadeSplitNdc An array to hold the computed cascaded shadow split distances in normalized device coordinates.
     */
    public static void updateMatrices(
            ShadowInputs inputs,
            float[][] shadowLightViewProjMatrices,
            float[] shadowCascadeSplitNdc
    ) {
        if (inputs.pointLightIsSpot() > 0.5f) {
            float[] spotDir = normalize3(inputs.pointLightDirX(), inputs.pointLightDirY(), inputs.pointLightDirZ());
            float targetX = inputs.pointLightPosX() + spotDir[0];
            float targetY = inputs.pointLightPosY() + spotDir[1];
            float targetZ = inputs.pointLightPosZ() + spotDir[2];
            float upX = 0f;
            float upY = 1f;
            float upZ = 0f;
            if (Math.abs(spotDir[1]) > 0.95f) {
                upX = 0f;
                upY = 0f;
                upZ = 1f;
            }
            float[] lightView = lookAt(
                    inputs.pointLightPosX(), inputs.pointLightPosY(), inputs.pointLightPosZ(),
                    targetX, targetY, targetZ,
                    upX, upY, upZ
            );
            float outerCos = Math.max(0.0001f, Math.min(1f, inputs.pointLightOuterCos()));
            float coneHalfAngle = (float) Math.acos(outerCos);
            float fov = Math.max((float) Math.toRadians(20.0), Math.min((float) Math.toRadians(120.0), coneHalfAngle * 2.0f));
            float[] lightProj = perspective(fov, 1f, 0.1f, 30f);
            shadowLightViewProjMatrices[0] = mul(lightProj, lightView);
            for (int i = 1; i < inputs.maxShadowMatrices(); i++) {
                shadowLightViewProjMatrices[i] = shadowLightViewProjMatrices[0];
            }
            setPointSplitDefaults(shadowCascadeSplitNdc);
            return;
        }
        if (!inputs.pointShadowEnabled() && inputs.localLightCount() > 0
                && inputs.localLightPosRange() != null
                && inputs.localLightDirInner() != null
                && inputs.localLightOuterTypeShadow() != null) {
            int localShadowLayers = 0;
            float[][] pointDirs = new float[][]{
                    {1f, 0f, 0f},
                    {-1f, 0f, 0f},
                    {0f, 1f, 0f},
                    {0f, -1f, 0f},
                    {0f, 0f, 1f},
                    {0f, 0f, -1f}
            };
            float[][] pointUp = new float[][]{
                    {0f, -1f, 0f},
                    {0f, -1f, 0f},
                    {0f, 0f, 1f},
                    {0f, 0f, -1f},
                    {0f, -1f, 0f},
                    {0f, -1f, 0f}
            };
            for (int i = 0; i < Math.min(inputs.localLightCount(), 8); i++) {
                int offset = i * 4;
                if (offset + 3 >= inputs.localLightOuterTypeShadow().length) {
                    continue;
                }
                float isSpot = inputs.localLightOuterTypeShadow()[offset + 1];
                float castsShadows = inputs.localLightOuterTypeShadow()[offset + 2];
                int layerIndex = Math.round(inputs.localLightOuterTypeShadow()[offset + 3]) - 1;
                if (castsShadows <= 0.5f || layerIndex < 0 || layerIndex >= inputs.maxShadowMatrices()) {
                    continue;
                }
                float px = inputs.localLightPosRange()[offset];
                float py = inputs.localLightPosRange()[offset + 1];
                float pz = inputs.localLightPosRange()[offset + 2];
                float range = Math.max(1.0f, inputs.localLightPosRange()[offset + 3]);
                if (isSpot > 0.5f) {
                    float[] spotDir = normalize3(
                            inputs.localLightDirInner()[offset],
                            inputs.localLightDirInner()[offset + 1],
                            inputs.localLightDirInner()[offset + 2]
                    );
                    float outerCos = Math.max(0.0001f, Math.min(1f, inputs.localLightOuterTypeShadow()[offset]));
                    float coneHalfAngle = (float) Math.acos(outerCos);
                    float fov = Math.max((float) Math.toRadians(20.0), Math.min((float) Math.toRadians(120.0), coneHalfAngle * 2.0f));
                    float[] lightView = lookAt(
                            px, py, pz,
                            px + spotDir[0], py + spotDir[1], pz + spotDir[2],
                            0f, Math.abs(spotDir[1]) > 0.95f ? 0f : 1f, Math.abs(spotDir[1]) > 0.95f ? 1f : 0f
                    );
                    float[] lightProj = perspective(fov, 1f, 0.1f, range);
                    shadowLightViewProjMatrices[layerIndex] = mul(lightProj, lightView);
                    localShadowLayers = Math.max(localShadowLayers, layerIndex + 1);
                } else {
                    float[] pointProj = perspective((float) Math.toRadians(90.0), 1f, 0.1f, range);
                    for (int face = 0; face < 6; face++) {
                        int layer = layerIndex + face;
                        if (layer >= inputs.maxShadowMatrices()) {
                            break;
                        }
                        float[] dir = pointDirs[face];
                        float[] lightView = lookAt(
                                px,
                                py,
                                pz,
                                px + dir[0],
                                py + dir[1],
                                pz + dir[2],
                                pointUp[face][0], pointUp[face][1], pointUp[face][2]
                        );
                        shadowLightViewProjMatrices[layer] = mul(pointProj, lightView);
                        localShadowLayers = Math.max(localShadowLayers, layer + 1);
                    }
                }
            }
            if (localShadowLayers > 0) {
                for (int i = 0; i < inputs.maxShadowMatrices(); i++) {
                    if (shadowLightViewProjMatrices[i] == null) {
                        shadowLightViewProjMatrices[i] = identityMatrix();
                    }
                }
                setPointSplitDefaults(shadowCascadeSplitNdc);
                return;
            }
        }
        if (inputs.pointShadowEnabled()) {
            float[][] pointDirs = new float[][]{
                    {1f, 0f, 0f},
                    {-1f, 0f, 0f},
                    {0f, 1f, 0f},
                    {0f, -1f, 0f},
                    {0f, 0f, 1f},
                    {0f, 0f, -1f}
            };
            float[][] pointUp = new float[][]{
                    {0f, -1f, 0f},
                    {0f, -1f, 0f},
                    {0f, 0f, 1f},
                    {0f, 0f, -1f},
                    {0f, -1f, 0f},
                    {0f, -1f, 0f}
            };
            float[] lightProj = perspective((float) Math.toRadians(90.0), 1f, 0.1f, inputs.pointShadowFarPlane());
            int availableLayers = Math.max(1, Math.min(inputs.pointShadowFaces(), inputs.shadowCascadeCount()));
            for (int i = 0; i < inputs.maxShadowMatrices(); i++) {
                int dirIndex = Math.min(i, pointDirs.length - 1);
                if (i >= availableLayers) {
                    shadowLightViewProjMatrices[i] = shadowLightViewProjMatrices[availableLayers - 1];
                    continue;
                }
                float[] dir = pointDirs[dirIndex];
                float[] lightView = lookAt(
                        inputs.pointLightPosX(),
                        inputs.pointLightPosY(),
                        inputs.pointLightPosZ(),
                        inputs.pointLightPosX() + dir[0],
                        inputs.pointLightPosY() + dir[1],
                        inputs.pointLightPosZ() + dir[2],
                        pointUp[dirIndex][0], pointUp[dirIndex][1], pointUp[dirIndex][2]
                );
                shadowLightViewProjMatrices[i] = mul(lightProj, lightView);
            }
            setPointSplitDefaults(shadowCascadeSplitNdc);
            return;
        }

        float[] viewProj = mul(inputs.projMatrix(), inputs.viewMatrix());
        float[] invViewProj = invert(viewProj);
        if (invViewProj == null) {
            for (int i = 0; i < inputs.maxShadowMatrices(); i++) {
                shadowLightViewProjMatrices[i] = identityMatrix();
            }
            setPointSplitDefaults(shadowCascadeSplitNdc);
            return;
        }

        float near = projectionNear(inputs.projMatrix());
        float far = projectionFar(inputs.projMatrix());
        if (!(near > 0f) || !(far > near)) {
            near = 0.1f;
            far = 100f;
        }

        int cascades = Math.max(1, Math.min(inputs.maxShadowCascades(), inputs.shadowCascadeCount()));
        float lambda = 0.7f;
        float prevSplitDist = near;
        float[] splitDist = new float[inputs.maxShadowCascades()];
        for (int i = 0; i < cascades; i++) {
            float p = (i + 1f) / cascades;
            float log = near * (float) Math.pow(far / near, p);
            float lin = near + (far - near) * p;
            splitDist[i] = log * lambda + lin * (1f - lambda);
        }

        float len = (float) Math.sqrt(
                inputs.dirLightDirX() * inputs.dirLightDirX()
                        + inputs.dirLightDirY() * inputs.dirLightDirY()
                        + inputs.dirLightDirZ() * inputs.dirLightDirZ()
        );
        if (len < 0.0001f) {
            len = 1f;
        }
        float lx = inputs.dirLightDirX() / len;
        float ly = inputs.dirLightDirY() / len;
        float lz = inputs.dirLightDirZ() / len;
        float upX = 0f;
        float upY = 1f;
        float upZ = 0f;
        if (Math.abs(ly) > 0.95f) {
            upX = 0f;
            upY = 0f;
            upZ = 1f;
        }

        setPointSplitDefaults(shadowCascadeSplitNdc);
        for (int cascade = 0; cascade < inputs.maxShadowCascades(); cascade++) {
            if (cascade >= cascades) {
                shadowLightViewProjMatrices[cascade] = shadowLightViewProjMatrices[Math.max(0, cascades - 1)];
                continue;
            }
            float nearDist = prevSplitDist;
            float farDist = splitDist[cascade];
            prevSplitDist = farDist;
            float nearNdc = viewDistanceToNdcDepth(inputs.projMatrix(), nearDist);
            float farNdc = viewDistanceToNdcDepth(inputs.projMatrix(), farDist);
            if (cascade < 3) {
                shadowCascadeSplitNdc[cascade] = farNdc * 0.5f + 0.5f;
            }

            float[][] corners = new float[8][3];
            int idx = 0;
            for (int z = 0; z < 2; z++) {
                float ndcZ = z == 0 ? nearNdc : farNdc;
                for (int y = 0; y < 2; y++) {
                    float ndcY = y == 0 ? -1f : 1f;
                    for (int x = 0; x < 2; x++) {
                        float ndcX = x == 0 ? -1f : 1f;
                        float[] world = unproject(invViewProj, ndcX, ndcY, ndcZ);
                        corners[idx][0] = world[0];
                        corners[idx][1] = world[1];
                        corners[idx][2] = world[2];
                        idx++;
                    }
                }
            }

            float centerX = 0f;
            float centerY = 0f;
            float centerZ = 0f;
            for (float[] c : corners) {
                centerX += c[0];
                centerY += c[1];
                centerZ += c[2];
            }
            centerX /= 8f;
            centerY /= 8f;
            centerZ /= 8f;

            float radius = 0f;
            for (float[] c : corners) {
                float dx = c[0] - centerX;
                float dy = c[1] - centerY;
                float dz = c[2] - centerZ;
                radius = Math.max(radius, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
            radius = Math.max(radius, 1f);
            float eyeX = centerX - lx * (radius * 2.0f);
            float eyeY = centerY - ly * (radius * 2.0f);
            float eyeZ = centerZ - lz * (radius * 2.0f);
            float[] lightView = lookAt(eyeX, eyeY, eyeZ, centerX, centerY, centerZ, upX, upY, upZ);

            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            for (float[] c : corners) {
                float[] l = transformPoint(lightView, c[0], c[1], c[2]);
                minX = Math.min(minX, l[0]);
                minY = Math.min(minY, l[1]);
                minZ = Math.min(minZ, l[2]);
                maxX = Math.max(maxX, l[0]);
                maxY = Math.max(maxY, l[1]);
                maxZ = Math.max(maxZ, l[2]);
            }
            if (inputs.directionalTexelSnapEnabled()) {
                float extentX = Math.max(0.001f, maxX - minX);
                float extentY = Math.max(0.001f, maxY - minY);
                float maxExtent = Math.max(extentX, extentY);
                float texelSize = (maxExtent / Math.max(1, inputs.shadowMapResolution()))
                        * Math.max(0.25f, Math.min(4.0f, inputs.directionalTexelSnapScale()));
                float[] centerLight = transformPoint(lightView, centerX, centerY, centerZ);
                float snappedCenterX = snapToTexel(centerLight[0], texelSize);
                float snappedCenterY = snapToTexel(centerLight[1], texelSize);
                float shiftX = snappedCenterX - centerLight[0];
                float shiftY = snappedCenterY - centerLight[1];
                minX += shiftX;
                maxX += shiftX;
                minY += shiftY;
                maxY += shiftY;
            }
            float zPad = Math.max(10f, radius);
            float[] lightProj = ortho(minX, maxX, minY, maxY, minZ - zPad, maxZ + zPad);
            shadowLightViewProjMatrices[cascade] = mul(lightProj, lightView);
        }
    }

    static float snapToTexel(float value, float texelSize) {
        if (texelSize <= 0.0f || !Float.isFinite(texelSize)) {
            return value;
        }
        return (float) Math.floor(value / texelSize) * texelSize;
    }

    /**
     * Sets the default normalized device coordinate (NDC) values for point light shadow cascades.
     * This method initializes the provided array to default values, typically used to represent
     * fully utilized shadow cascades for point lights. Each element in the array is set to 1.0.
     *
     * @param shadowCascadeSplitNdc An array representing the normalized device coordinate split
     *                              distances for shadow cascades. The array should have a length
     *                              of at least 3, as this method sets the first three elements to 1.0.
     */
    private static void setPointSplitDefaults(float[] shadowCascadeSplitNdc) {
        shadowCascadeSplitNdc[0] = 1f;
        shadowCascadeSplitNdc[1] = 1f;
        shadowCascadeSplitNdc[2] = 1f;
    }

    /**
     * Represents the input parameters required for shadow computation in a Vulkan-based rendering engine.
     * This record encapsulates information about lights (point, directional, and spot), shadow settings,
     * and the necessary matrices for shadow mapping calculations.
     *
     * Fields:
     * - `pointLightIsSpot`: Indicates whether the point light functions as a spotlight (1.0 for true, 0.0 for false).
     * - `pointLightDirX`: X component of the point light's direction.
     * - `pointLightDirY`: Y component of the point light's direction.
     * - `pointLightDirZ`: Z component of the point light's direction.
     * - `pointLightPosX`: X position of the point light in world space.
     * - `pointLightPosY`: Y position of the point light in world space.
     * - `pointLightPosZ`: Z position of the point light in world space.
     * - `pointLightOuterCos`: The cosine of the outer angle for spotlights, used for spot light attenuation.
     * - `pointShadowEnabled`: Indicates whether shadows are enabled for point lights.
     * - `pointShadowFarPlane`: The far clipping plane distance for point light shadow maps.
     * - `shadowCascadeCount`: The number of cascades used in cascaded shadow mapping for directional lights.
     * - `viewMatrix`: A flat array representing a 4x4 matrix describing the view transformations for shadows.
     * - `projMatrix`: A flat array representing a 4x4 matrix describing the projection transformations for shadows.
     * - `dirLightDirX`: X component of the direction of the directional light.
     * - `dirLightDirY`: Y component of the direction of the directional light.
     * - `dirLightDirZ`: Z component of the direction of the directional light.
     * - `maxShadowMatrices`: The maximum number of shadow matrices supported for shadow mapping.
     * - `maxShadowCascades`: The maximum number of shadow cascades supported by the system.
     * - `pointShadowFaces`: The number of shadow map faces for point lights, typically 6 for cube maps.
     */
    public record ShadowInputs(
            float pointLightIsSpot,
            float pointLightDirX,
            float pointLightDirY,
            float pointLightDirZ,
            float pointLightPosX,
            float pointLightPosY,
            float pointLightPosZ,
            float pointLightOuterCos,
            boolean pointShadowEnabled,
            float pointShadowFarPlane,
            int shadowCascadeCount,
            int shadowMapResolution,
            boolean directionalTexelSnapEnabled,
            float directionalTexelSnapScale,
            float[] viewMatrix,
            float[] projMatrix,
            int localLightCount,
            float[] localLightPosRange,
            float[] localLightDirInner,
            float[] localLightOuterTypeShadow,
            float dirLightDirX,
            float dirLightDirY,
            float dirLightDirZ,
            int maxShadowMatrices,
            int maxShadowCascades,
            int pointShadowFaces
    ) {
    }
}
