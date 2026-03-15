package org.dynamisengine.light.api.runtime;

import org.dynamisengine.light.api.config.EngineConfig;
import org.dynamisengine.light.api.error.EngineException;
import org.dynamisengine.light.api.input.EngineInput;
import org.dynamisengine.light.api.mesh.MeshUploadRequest;
import org.dynamisengine.light.api.mesh.MeshUploadResult;
import org.dynamisengine.light.api.resource.EngineResourceService;
import org.dynamisengine.light.api.scene.SceneDescriptor;

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
     * Updates the joint matrix palette for a previously loaded skinned mesh.
     *
     * The mesh handle is backend-assigned at scene load time and is stable for the life of
     * the loaded scene. The joint matrix array must contain packed {@code mat4} values
     * ({@code 16} floats per joint) in row-major engine order.
     *
     * @param meshHandle mesh handle identifying the skinned mesh to update.
     * @param jointMatrices joint palette data ({@code 16 * jointCount} floats).
     * @throws EngineException if the runtime is not initialized, the handle is invalid,
     *                         or the target mesh is not skinned.
     */
    void updateSkinnedMesh(int meshHandle, float[] jointMatrices) throws EngineException;

    /**
     * Updates morph target blend weights for a previously loaded mesh.
     *
     * The mesh handle is backend-assigned at scene load time and is stable for the life of
     * the loaded scene. The provided array is copied by the runtime on update.
     *
     * @param meshHandle mesh handle identifying the morph-target mesh to update.
     * @param weights morph target weights, one float per target.
     * @throws EngineException if the runtime is not initialized, the handle is invalid,
     *                         or the target mesh has no morph targets.
     */
    void updateMorphWeights(int meshHandle, float[] weights) throws EngineException;

    /**
     * Registers an instanced draw batch for a previously loaded mesh.
     *
     * @param meshHandle mesh handle identifying the base mesh.
     * @param modelMatrices per-instance model matrices, one 4x4 matrix ({@code 16} floats) per instance.
     * @return backend-assigned batch handle.
     * @throws EngineException if the runtime is not initialized, the handle is invalid, or data is malformed.
     */
    int registerInstanceBatch(int meshHandle, float[][] modelMatrices) throws EngineException;

    /**
     * Registers an externally uploaded mesh and returns the resulting runtime mesh handle.
     *
     * @param request backend-neutral mesh upload request carrying format, payload, and dedupe hash.
     * @return upload result containing mesh handle and reuse metadata.
     * @throws EngineException if the runtime is not initialized, request format is unsupported, or upload fails.
     */
    MeshUploadResult registerMesh(MeshUploadRequest request) throws EngineException;

    /**
     * Removes a previously registered external mesh.
     *
     * @param meshHandle mesh handle returned by {@link #registerMesh(MeshUploadRequest)}.
     * @throws EngineException if the runtime is not initialized or meshHandle is invalid.
     */
    void removeMesh(int meshHandle) throws EngineException;

    /**
     * Updates model matrices for an existing instanced draw batch.
     *
     * @param batchHandle batch handle returned by {@link #registerInstanceBatch(int, float[][])}.
     * @param modelMatrices per-instance model matrices, one 4x4 matrix ({@code 16} floats) per instance.
     * @throws EngineException if the runtime is not initialized, the handle is invalid, or data is malformed.
     */
    void updateInstanceBatch(int batchHandle, float[][] modelMatrices) throws EngineException;

    /**
     * Removes an existing instanced draw batch.
     *
     * @param batchHandle batch handle returned by {@link #registerInstanceBatch(int, float[][])}.
     * @throws EngineException if the runtime is not initialized or the handle is invalid.
     */
    void removeInstanceBatch(int batchHandle) throws EngineException;

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
     * Retrieves backend-agnostic GI promotion diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link GiPromotionDiagnostics#unavailable()}.
     *
     * @return current GI promotion diagnostics snapshot.
     */
    default GiPromotionDiagnostics giPromotionDiagnostics() {
        return GiPromotionDiagnostics.unavailable();
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
     * Retrieves backend-agnostic lighting promotion diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link LightingPromotionDiagnostics#unavailable()}.
     *
     * @return current lighting promotion diagnostics snapshot.
     */
    default LightingPromotionDiagnostics lightingPromotionDiagnostics() {
        return LightingPromotionDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic lighting emissive-policy diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link LightingEmissiveDiagnostics#unavailable()}.
     *
     * @return current lighting emissive diagnostics snapshot.
     */
    default LightingEmissiveDiagnostics lightingEmissiveDiagnostics() {
        return LightingEmissiveDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic advanced-lighting diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link LightingAdvancedDiagnostics#unavailable()}.
     *
     * @return current advanced-lighting diagnostics snapshot.
     */
    default LightingAdvancedDiagnostics lightingAdvancedDiagnostics() {
        return LightingAdvancedDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic core post-pipeline promotion diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link PostCorePromotionDiagnostics#unavailable()}.
     *
     * @return current core post promotion diagnostics snapshot.
     */
    default PostCorePromotionDiagnostics postCorePromotionDiagnostics() {
        return PostCorePromotionDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic cinematic post promotion diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link PostCinematicPromotionDiagnostics#unavailable()}.
     *
     * @return current cinematic post promotion diagnostics snapshot.
     */
    default PostCinematicPromotionDiagnostics postCinematicPromotionDiagnostics() {
        return PostCinematicPromotionDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic RT cross-cut diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link RtCrossCutDiagnostics#unavailable()}.
     *
     * @return current RT cross-cut diagnostics snapshot.
     */
    default RtCrossCutDiagnostics rtCrossCutDiagnostics() {
        return RtCrossCutDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic RT capability diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link RtCapabilityDiagnostics#unavailable()}.
     *
     * @return current RT capability diagnostics snapshot.
     */
    default RtCapabilityDiagnostics rtCapabilityDiagnostics() {
        return RtCapabilityDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic RT capability promotion diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link RtCapabilityPromotionDiagnostics#unavailable()}.
     *
     * @return current RT capability promotion diagnostics snapshot.
     */
    default RtCapabilityPromotionDiagnostics rtCapabilityPromotionDiagnostics() {
        return RtCapabilityPromotionDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic PBR/shading capability-plan diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link PbrCapabilityDiagnostics#unavailable()}.
     *
     * @return current PBR/shading capability-plan diagnostics snapshot.
     */
    default PbrCapabilityDiagnostics pbrCapabilityDiagnostics() {
        return PbrCapabilityDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic PBR promotion diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link PbrPromotionDiagnostics#unavailable()}.
     *
     * @return current PBR promotion diagnostics snapshot.
     */
    default PbrPromotionDiagnostics pbrPromotionDiagnostics() {
        return PbrPromotionDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic sky/atmosphere capability diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link SkyCapabilityDiagnostics#unavailable()}.
     *
     * @return current sky/atmosphere capability diagnostics snapshot.
     */
    default SkyCapabilityDiagnostics skyCapabilityDiagnostics() {
        return SkyCapabilityDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic sky/atmosphere promotion diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link SkyPromotionDiagnostics#unavailable()}.
     *
     * @return current sky/atmosphere promotion diagnostics snapshot.
     */
    default SkyPromotionDiagnostics skyPromotionDiagnostics() {
        return SkyPromotionDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic geometry/detail capability diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link GeometryCapabilityDiagnostics#unavailable()}.
     *
     * @return current geometry/detail capability diagnostics snapshot.
     */
    default GeometryCapabilityDiagnostics geometryCapabilityDiagnostics() {
        return GeometryCapabilityDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic geometry/detail promotion diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link GeometryPromotionDiagnostics#unavailable()}.
     *
     * @return current geometry/detail promotion diagnostics snapshot.
     */
    default GeometryPromotionDiagnostics geometryPromotionDiagnostics() {
        return GeometryPromotionDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic VFX/particles capability diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link VfxCapabilityDiagnostics#unavailable()}.
     *
     * @return current VFX/particles capability diagnostics snapshot.
     */
    default VfxCapabilityDiagnostics vfxCapabilityDiagnostics() {
        return VfxCapabilityDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic VFX/particles promotion diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link VfxPromotionDiagnostics#unavailable()}.
     *
     * @return current VFX/particles promotion diagnostics snapshot.
     */
    default VfxPromotionDiagnostics vfxPromotionDiagnostics() {
        return VfxPromotionDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic water/ocean capability diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link WaterCapabilityDiagnostics#unavailable()}.
     *
     * @return current water/ocean capability diagnostics snapshot.
     */
    default WaterCapabilityDiagnostics waterCapabilityDiagnostics() {
        return WaterCapabilityDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic water/ocean promotion diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link WaterPromotionDiagnostics#unavailable()}.
     *
     * @return current water/ocean promotion diagnostics snapshot.
     */
    default WaterPromotionDiagnostics waterPromotionDiagnostics() {
        return WaterPromotionDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic terrain capability diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link TerrainCapabilityDiagnostics#unavailable()}.
     *
     * @return current terrain capability diagnostics snapshot.
     */
    default TerrainCapabilityDiagnostics terrainCapabilityDiagnostics() {
        return TerrainCapabilityDiagnostics.unavailable();
    }

    /**
     * Retrieves backend-agnostic terrain promotion diagnostics.
     *
     * Backends that do not expose this snapshot return
     * {@link TerrainPromotionDiagnostics#unavailable()}.
     *
     * @return current terrain promotion diagnostics snapshot.
     */
    default TerrainPromotionDiagnostics terrainPromotionDiagnostics() {
        return TerrainPromotionDiagnostics.unavailable();
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
