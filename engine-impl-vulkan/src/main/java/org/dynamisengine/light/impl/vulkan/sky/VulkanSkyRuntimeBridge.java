package org.dynamisengine.light.impl.vulkan.sky;

import org.dynamisengine.light.impl.common.sky.SkyRenderBridge;
import org.dynamisengine.light.impl.vulkan.state.VulkanBackendResources;
import org.vectrix.core.Matrix4f;

import java.util.ServiceLoader;
import java.util.logging.Logger;

/**
 * Optional runtime bridge to DynamisSky via ServiceLoader SPI.
 */
public final class VulkanSkyRuntimeBridge {
    private static final Logger LOG = Logger.getLogger(VulkanSkyRuntimeBridge.class.getName());

    private SkyRenderBridge bridge;

    public void initialize(VulkanBackendResources resources) {
        if (resources == null || resources.device == null || resources.physicalDevice == null) {
            return;
        }
        for (SkyRenderBridge candidate : ServiceLoader.load(SkyRenderBridge.class)) {
            var ctx = new SkyRenderBridge.InitContext(
                resources.device.address(),
                resources.physicalDevice.address(),
                resources.renderPass,
                0L
            );
            if (candidate.initialize(ctx)) {
                this.bridge = candidate;
                break;
            }
        }
        if (bridge == null) {
            LOG.fine("DynamisSky bridge unavailable: no ServiceLoader provider found");
        }
    }

    public boolean active() {
        return bridge != null;
    }

    public void updateAndRecord(long commandBuffer, int frameIndex, Matrix4f viewProj, Matrix4f invViewProj) {
        if (!active()) {
            return;
        }
        bridge.updateAndRecord(commandBuffer, frameIndex, 1.0f / 60.0f,
                matrixToArray(invViewProj), matrixToArray(viewProj));
    }

    public float[] sunDirection() {
        if (!active()) {
            return new float[]{0f, -1f, 0f};
        }
        float[] dir = bridge.sunDirection();
        return dir != null ? dir : new float[]{0f, -1f, 0f};
    }

    public float[] sunColor() {
        if (!active()) {
            return new float[]{1f, 1f, 1f};
        }
        float[] color = bridge.sunColor();
        return color != null ? color : new float[]{1f, 1f, 1f};
    }

    public float sunIntensity() {
        if (!active()) {
            return 1f;
        }
        return bridge.sunIntensity();
    }

    private static float[] matrixToArray(Matrix4f m) {
        if (m == null) {
            return new float[16];
        }
        float[] a = new float[16];
        a[ 0] = m.m00(); a[ 1] = m.m01(); a[ 2] = m.m02(); a[ 3] = m.m03();
        a[ 4] = m.m10(); a[ 5] = m.m11(); a[ 6] = m.m12(); a[ 7] = m.m13();
        a[ 8] = m.m20(); a[ 9] = m.m21(); a[10] = m.m22(); a[11] = m.m23();
        a[12] = m.m30(); a[13] = m.m31(); a[14] = m.m32(); a[15] = m.m33();
        return a;
    }
}
