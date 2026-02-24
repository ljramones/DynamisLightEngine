package org.dynamislight.impl.vulkan.sky;

import org.dynamislight.impl.vulkan.state.VulkanBackendResources;
import org.vectrix.core.Matrix4f;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Optional runtime bridge to DynamisSky via reflection.
 */
public final class VulkanSkyRuntimeBridge {
    private static final Logger LOG = Logger.getLogger(VulkanSkyRuntimeBridge.class.getName());

    private Object integration;
    private Method updateMethod;
    private Method recordBackgroundMethod;
    private Method recordCelestialMethod;
    private Method getSunStateMethod;

    public void initialize(VulkanBackendResources resources) {
        if (resources == null || resources.device == null || resources.physicalDevice == null) {
            return;
        }
        try {
            Class<?> memoryOpsClass = Class.forName("org.dynamissky.vulkan.lut.LwjglGpuMemoryOps");
            Object memoryOps = memoryOpsClass
                    .getConstructor(org.lwjgl.vulkan.VkDevice.class, org.lwjgl.vulkan.VkPhysicalDevice.class)
                    .newInstance(resources.device, resources.physicalDevice);

            Class<?> skyConfigClass = Class.forName("org.dynamissky.vulkan.SkyConfig");
            Object builder = skyConfigClass.getMethod("builder").invoke(null);
            Object config = builder.getClass().getMethod("build").invoke(builder);

            Class<?> integrationClass = Class.forName("org.dynamissky.vulkan.integration.VulkanSkyIntegration");
            integration = integrationClass
                    .getMethod("create", long.class, long.class, Class.forName("org.dynamissky.vulkan.lut.GpuMemoryOps"), long.class, skyConfigClass)
                    .invoke(null, resources.device.address(), resources.renderPass, memoryOps, 0L, config);

            Class<?> cameraStateClass = Class.forName("org.dynamissky.vulkan.lut.CameraState");
            updateMethod = integrationClass.getMethod("update", long.class, cameraStateClass, float.class, int.class);
            recordBackgroundMethod = integrationClass.getMethod("recordBackground", long.class, Matrix4f.class, int.class);
            recordCelestialMethod = integrationClass.getMethod("recordCelestial", long.class, Matrix4f.class, int.class);
            getSunStateMethod = integrationClass.getMethod("getSunState");
        } catch (Throwable t) {
            integration = null;
            updateMethod = null;
            recordBackgroundMethod = null;
            recordCelestialMethod = null;
            getSunStateMethod = null;
            LOG.fine("DynamisSky bridge unavailable: " + t.getMessage());
        }
    }

    public boolean active() {
        return integration != null;
    }

    public void updateAndRecord(long commandBuffer, int frameIndex, Matrix4f viewProj, Matrix4f invViewProj) {
        if (!active()) {
            return;
        }
        try {
            Object cameraState = Class.forName("org.dynamissky.vulkan.lut.CameraState")
                    .getMethod("defaultState")
                    .invoke(null);
            updateMethod.invoke(integration, commandBuffer, cameraState, 1.0f / 60.0f, frameIndex);
            recordBackgroundMethod.invoke(integration, commandBuffer, invViewProj, frameIndex);
            recordCelestialMethod.invoke(integration, commandBuffer, viewProj, frameIndex);
        } catch (Throwable t) {
            LOG.fine("DynamisSky frame bridge failure: " + t.getMessage());
        }
    }

    public float[] sunDirection() {
        Object sun = sunState();
        if (sun == null) {
            return new float[]{0f, -1f, 0f};
        }
        try {
            Object dir = sun.getClass().getMethod("direction").invoke(sun);
            float x = ((Number) dir.getClass().getMethod("x").invoke(dir)).floatValue();
            float y = ((Number) dir.getClass().getMethod("y").invoke(dir)).floatValue();
            float z = ((Number) dir.getClass().getMethod("z").invoke(dir)).floatValue();
            return new float[]{x, y, z};
        } catch (Throwable t) {
            return new float[]{0f, -1f, 0f};
        }
    }

    public float[] sunColor() {
        Object sun = sunState();
        if (sun == null) {
            return new float[]{1f, 1f, 1f};
        }
        try {
            Object color = sun.getClass().getMethod("color").invoke(sun);
            float r = ((Number) color.getClass().getMethod("r").invoke(color)).floatValue();
            float g = ((Number) color.getClass().getMethod("g").invoke(color)).floatValue();
            float b = ((Number) color.getClass().getMethod("b").invoke(color)).floatValue();
            return new float[]{r, g, b};
        } catch (Throwable t) {
            return new float[]{1f, 1f, 1f};
        }
    }

    public float sunIntensity() {
        Object sun = sunState();
        if (sun == null) {
            return 1f;
        }
        try {
            return ((Number) sun.getClass().getMethod("intensity").invoke(sun)).floatValue();
        } catch (Throwable t) {
            return 1f;
        }
    }

    private Object sunState() {
        if (!active()) {
            return null;
        }
        try {
            return getSunStateMethod.invoke(integration);
        } catch (Throwable t) {
            return null;
        }
    }
}
