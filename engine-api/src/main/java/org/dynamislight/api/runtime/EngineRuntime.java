package org.dynamislight.api.runtime;

import org.dynamislight.api.config.EngineConfig;
import org.dynamislight.api.error.EngineException;
import org.dynamislight.api.input.EngineInput;
import org.dynamislight.api.resource.EngineResourceService;
import org.dynamislight.api.scene.SceneDescriptor;

/**
 * Core runtime contract used by hosts to drive the engine as a black box.
 *
 * <p>Lifecycle order is strict: {@code initialize()} once, then any number of
 * {@code loadScene()/update()/render()/resize()} calls, then {@code shutdown()}.
 * Re-initialization after shutdown is not supported in v1.</p>
 *
 * <p>Threading contract (v1): all methods are called from one designated engine
 * thread. Calls are not reentrant. Callback handlers must not synchronously call
 * back into this runtime.</p>
 */
public interface EngineRuntime extends AutoCloseable {
    /**
     * Retrieves the API version of the engine.
     *
     * The API version specifies the major, minor, and patch levels of the engine's
     * interface. This information can be used to determine compatibility between
     * the runtime engine and the host environment.
     *
     * @return the version of the engine API as an {@code EngineApiVersion} record, which
     *         contains the major, minor, and patch version numbers.
     */
    EngineApiVersion apiVersion();

    /**
     * Initializes the engine runtime with the specified configuration and host callbacks.
     *
     * This method prepares the engine for operation by applying the provided configuration
     * and associating the host callbacks that will handle engine events, logging, and errors.
     * Initialization must be called once before using other methods of the engine runtime.
     * Re-initialization after shutdown is not supported in version 1 of the runtime.
     *
     * @param config the configuration parameters for engine setup; must not be null.
     *               Includes information such as rendering backend, application name,
     *               initial dimensions, target frame rate, DPI scale, and asset root path.
     * @param host   the callback interface implemented by the host application to handle
     *               engine lifecycle events, log messages, and errors; must not be null.
     * @throws EngineException if an error occurs during initialization, such as invalid
     *                         configuration parameters or issues with the rendering backend.
     */
    void initialize(EngineConfig config, EngineHostCallbacks host) throws EngineException;

    /**
     * Loads a scene into the engine runtime for rendering and simulation.
     *
     * The scene contains all necessary descriptors for cameras, transforms, meshes,
     * materials, lights, environmental settings, and other scene-specific data.
     * This method replaces the currently loaded scene in the runtime, if any.
     *
     * @param scene the descriptor of the scene to load, including its name,
     *              cameras, active camera ID, transforms, meshes, materials,
     *              lights, and other settings. Must not be null.
     * @throws EngineException if an error occurs while loading the scene, such as
     *                         incompatibility issues, data corruption, or engine
     *                         internal failures.
     */
    void loadScene(SceneDescriptor scene) throws EngineException;

    /**
     * Updates the engine runtime by processing a single frame based on the elapsed time and input state.
     *
     * This method performs necessary computations to advance the state of the engine, including
     * game logic, physics simulation, and resource management. The update operation is driven by
     * the provided input metadata and elapsed time since the last frame.
     *
     * @param dtSeconds the elapsed time, in seconds, since the previous frame; must be non-negative.
     * @param input     the current user input state, including mouse, keyboard, and scrolling interactions;
     *                  must not be null.
     * @return an {@code EngineFrameResult} containing information about the processed frame, such as
     *         timing metrics and any warnings generated during the update phase.
     * @throws EngineException if an error occurs during the update process, including but not limited to
     *                         illegal states, resource issues, or internal engine failures.
     */
    EngineFrameResult update(double dtSeconds, EngineInput input) throws EngineException;

    /**
     * Renders the current scene in the engine runtime.
     *
     * This method performs the rendering operation for the current frame, which includes
     * executing GPU commands, applying graphical shaders, processing scene data, and
     * presenting the output to the designated display or render target. If no scene has been
     * loaded, the behavior of this method is undefined and may throw an exception.
     *
     * The rendering process uses the current state of the engine, including loaded assets,
     * camera configurations, lighting setups, and other rendering-related parameters.
     *
     * @return an {@code EngineFrameResult} containing details about the rendered frame,
     *         such as timing metrics, the frame index, and any warnings encountered during
     *         the rendering process.
     * @throws EngineException if an error occurs during rendering, such as resource
     *                         allocation failures, driver issues, or invalid rendering states.
     */
    EngineFrameResult render() throws EngineException;

    /**
     * Resizes the rendering viewport of the engine runtime and updates the pixel density scaling factor.
     *
     * This method adjusts the rendering resolution based on the specified width, height,
     * and DPI scale factor. It is often used to handle changes in the application window size
     * or to support high-DPI displays for improved rendering quality.
     *
     * @param widthPx the new width of the rendering viewport, in pixels; must be non-negative.
     * @param heightPx the new height of the rendering viewport, in pixels; must be non-negative.
     * @param dpiScale the scaling factor for DPI (dots per inch), representing the ratio between
     *                 logical and physical pixels. A value of 1.0 represents normal DPI. Must be positive.
     * @throws EngineException if resizing fails due to invalid dimensions or internal engine errors.
     */
    void resize(int widthPx, int heightPx, float dpiScale) throws EngineException;

    /**
     * Retrieves statistical information about the engine's runtime performance.
     *
     * This method provides details such as frame rate, CPU and GPU frame times,
     * the number of draw calls, triangles rendered, visible objects in the current
     * scene, and GPU memory usage. These metrics can be used to monitor and
     * analyze the performance characteristics of the engine.
     *
     * @return an {@code EngineStats} record containing the current runtime performance
     *         statistics, including frame rate, CPU and GPU timing, draw calls, triangles
     *         count, visible objects, and GPU memory usage.
     */
    EngineStats getStats();

    /**
     * Retrieves the capabilities and features supported by the engine.
     *
     * The returned information includes details about the engine's rendering
     * backend, supported graphics APIs, maximum texture sizes, shading features,
     * and other hardware or software capabilities. The data can be used to
     * determine compatibility and optimize the use of engine features for specific
     * hardware or configurations.
     *
     * @return an {@code EngineCapabilities} object containing information about
     *         the supported functionalities and configuration of the engine.
     */
    EngineCapabilities getCapabilities();

    /**
     * Provides access to the resource management service of the engine runtime.
     *
     * The {@code EngineResourceService} allows for operations such as resource allocation,
     * deallocation, and manipulation of engine assets. This method returns the service
     * interface used for managing these resources. If the resource service is not available
     * for the current runtime, this method throws an {@code UnsupportedOperationException}.
     *
     * @return an {@code EngineResourceService} instance for managing engine resources.
     * @throws UnsupportedOperationException if the resource service is unavailable for this runtime.
     */
    default EngineResourceService resources() {
        throw new UnsupportedOperationException("Resource service is not available for this runtime");
    }

    /**
     * Retrieves backend-agnostic reflection adaptive-trend SLO diagnostics.
     *
     * Backends that do not expose reflection SLO diagnostics return
     * {@link ReflectionAdaptiveTrendSloDiagnostics#unavailable()}.
     *
     * @return current reflection adaptive-trend SLO diagnostics snapshot.
     */
    default ReflectionAdaptiveTrendSloDiagnostics reflectionAdaptiveTrendSloDiagnostics() {
        return ReflectionAdaptiveTrendSloDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic shadow capability diagnostics.
     *
     * Backends that do not expose shadow capability diagnostics return
     * {@link ShadowCapabilityDiagnostics#unavailable()}.
     *
     * @return current shadow capability diagnostics snapshot.
     */
    default ShadowCapabilityDiagnostics shadowCapabilityDiagnostics() {
        return ShadowCapabilityDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic shadow cadence diagnostics.
     *
     * Backends that do not expose shadow cadence diagnostics return
     * {@link ShadowCadenceDiagnostics#unavailable()}.
     *
     * @return current shadow cadence diagnostics snapshot.
     */
    default ShadowCadenceDiagnostics shadowCadenceDiagnostics() {
        return ShadowCadenceDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic point-shadow face-budget diagnostics.
     *
     * Backends that do not expose point budget diagnostics return
     * {@link ShadowPointBudgetDiagnostics#unavailable()}.
     *
     * @return current point-shadow face-budget diagnostics snapshot.
     */
    default ShadowPointBudgetDiagnostics shadowPointBudgetDiagnostics() {
        return ShadowPointBudgetDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic spot-projected shadow diagnostics.
     *
     * Backends that do not expose spot diagnostics return
     * {@link ShadowSpotProjectedDiagnostics#unavailable()}.
     *
     * @return current spot-projected shadow diagnostics snapshot.
     */
    default ShadowSpotProjectedDiagnostics shadowSpotProjectedDiagnostics() {
        return ShadowSpotProjectedDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic shadow-cache diagnostics.
     *
     * Backends that do not expose cache diagnostics return
     * {@link ShadowCacheDiagnostics#unavailable()}.
     *
     * @return current shadow-cache diagnostics snapshot.
     */
    default ShadowCacheDiagnostics shadowCacheDiagnostics() {
        return ShadowCacheDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic shadow RT diagnostics.
     *
     * Backends that do not expose RT diagnostics return
     * {@link ShadowRtDiagnostics#unavailable()}.
     *
     * @return current shadow RT diagnostics snapshot.
     */
    default ShadowRtDiagnostics shadowRtDiagnostics() {
        return ShadowRtDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic shadow hybrid composition diagnostics.
     *
     * Backends that do not expose hybrid diagnostics return
     * {@link ShadowHybridDiagnostics#unavailable()}.
     *
     * @return current shadow hybrid diagnostics snapshot.
     */
    default ShadowHybridDiagnostics shadowHybridDiagnostics() {
        return ShadowHybridDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic transparent shadow receiver diagnostics.
     *
     * Backends that do not expose transparent receiver diagnostics return
     * {@link ShadowTransparentReceiverDiagnostics#unavailable()}.
     *
     * @return current transparent shadow receiver diagnostics snapshot.
     */
    default ShadowTransparentReceiverDiagnostics shadowTransparentReceiverDiagnostics() {
        return ShadowTransparentReceiverDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic diagnostics for optional shadow expansion modes.
     *
     * Backends that do not expose expansion-mode diagnostics return
     * {@link ShadowExtendedModeDiagnostics#unavailable()}.
     *
     * @return current expansion-mode diagnostics snapshot.
     */
    default ShadowExtendedModeDiagnostics shadowExtendedModeDiagnostics() {
        return ShadowExtendedModeDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic shadow topology coverage diagnostics.
     *
     * Backends that do not expose topology diagnostics return
     * {@link ShadowTopologyDiagnostics#unavailable()}.
     *
     * @return current shadow topology diagnostics snapshot.
     */
    default ShadowTopologyDiagnostics shadowTopologyDiagnostics() {
        return ShadowTopologyDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic Phase A shadow promotion diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link ShadowPhaseAPromotionDiagnostics#unavailable()}.
     *
     * @return current Phase A shadow promotion diagnostics snapshot.
     */
    default ShadowPhaseAPromotionDiagnostics shadowPhaseAPromotionDiagnostics() {
        return ShadowPhaseAPromotionDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic Phase D shadow promotion diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link ShadowPhaseDPromotionDiagnostics#unavailable()}.
     *
     * @return current Phase D shadow promotion diagnostics snapshot.
     */
    default ShadowPhaseDPromotionDiagnostics shadowPhaseDPromotionDiagnostics() {
        return ShadowPhaseDPromotionDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic AA/post capability-plan diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link AaPostCapabilityDiagnostics#unavailable()}.
     *
     * @return current AA/post capability-plan diagnostics snapshot.
     */
    default AaPostCapabilityDiagnostics aaPostCapabilityDiagnostics() {
        return AaPostCapabilityDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic AA temporal hardening/promotion diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link AaTemporalPromotionDiagnostics#unavailable()}.
     *
     * @return current AA temporal promotion diagnostics snapshot.
     */
    default AaTemporalPromotionDiagnostics aaTemporalPromotionDiagnostics() {
        return AaTemporalPromotionDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic AA upscale hardening/promotion diagnostics for TSR/TUUA paths.
     *
     * Backends that do not expose this snapshot return
     * {@link AaUpscalePromotionDiagnostics#unavailable()}.
     *
     * @return current AA upscale promotion diagnostics snapshot.
     */
    default AaUpscalePromotionDiagnostics aaUpscalePromotionDiagnostics() {
        return AaUpscalePromotionDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic AA MSAA/hybrid hardening/promotion diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link AaMsaaPromotionDiagnostics#unavailable()}.
     *
     * @return current AA MSAA/hybrid promotion diagnostics snapshot.
     */
    default AaMsaaPromotionDiagnostics aaMsaaPromotionDiagnostics() {
        return AaMsaaPromotionDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic AA quality-mode diagnostics (DLAA + specular AA).
     *
     * Backends that do not expose this snapshot return
     * {@link AaQualityPromotionDiagnostics#unavailable()}.
     *
     * @return current AA quality-mode diagnostics snapshot.
     */
    default AaQualityPromotionDiagnostics aaQualityPromotionDiagnostics() {
        return AaQualityPromotionDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic GI capability-plan diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link GiCapabilityDiagnostics#unavailable()}.
     *
     * @return current GI capability-plan diagnostics snapshot.
     */
    default GiCapabilityDiagnostics giCapabilityDiagnostics() {
        return GiCapabilityDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic lighting capability-plan diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link LightingCapabilityDiagnostics#unavailable()}.
     *
     * @return current lighting capability-plan diagnostics snapshot.
     */
    default LightingCapabilityDiagnostics lightingCapabilityDiagnostics() {
        return LightingCapabilityDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic lighting budget diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link LightingBudgetDiagnostics#unavailable()}.
     *
     * @return current lighting budget diagnostics snapshot.
     */
    default LightingBudgetDiagnostics lightingBudgetDiagnostics() {
        return LightingBudgetDiagnostics.unavailable();
    }

    /**
     * Shuts down the engine runtime and releases all allocated resources.
     *
     * This method ensures that the engine is properly terminated, including the release
     * of GPU resources, memory buffers, and other system-level allocations. Once this
     * method is called, the engine cannot be used unless re-initialized.
     * It is the caller's responsibility to ensure that no further operations are invoked
     * on the engine runtime after shutdown.
     *
     * The shutdown operation is designed to clean up all internal states of the engine,
     * terminate background threads, and perform any necessary finalization required
     * for safe disposal. This method should be called once during the engine's lifecycle
     * to prevent resource leaks.
     *
     * @throws EngineException if an error occurs during the shutdown process,
     *                         such as failure to release resources or incomplete cleanup.
     */
    void shutdown();

    /**
     * Closes the resource and performs necessary cleanup operations.
     * This method is typically used to release any resources or
     * terminate processes associated with the implementation.
     *
     * The default implementation invokes the {@code shutdown()} method
     * to handle the closure of the resource. Implementors may override
     * this method if additional cleanup logic is required.
     */
    @Override
    default void close() {
        shutdown();
    }
}
