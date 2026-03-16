package org.dynamisengine.light.impl.common.sky;

/**
 * SPI for optional sky rendering integration. Implementations are discovered
 * via {@link java.util.ServiceLoader}.
 */
public interface SkyRenderBridge {

    /** Initialize the sky renderer with backend-specific handles. */
    boolean initialize(InitContext context);

    /** Update camera and record sky rendering commands for this frame. */
    void updateAndRecord(long commandBuffer, int frameIndex, float deltaTime,
                         float[] invViewProjMatrix, float[] viewProjMatrix);

    /** Current sun direction as [x, y, z]. Returns null if unavailable. */
    float[] sunDirection();

    /** Current sun color as [r, g, b]. Returns null if unavailable. */
    float[] sunColor();

    /** Current sun intensity. Returns 1.0 if unavailable. */
    float sunIntensity();

    /** Release resources. */
    default void shutdown() {}

    record InitContext(
        long deviceHandle,
        long physicalDeviceHandle,
        long renderPass,
        long descriptorPool
    ) {}
}
