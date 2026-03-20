package org.dynamisengine.light.impl.common.mesh;

import org.dynamisengine.meshforge.gpu.GpuGeometryUploadPlan;
import org.dynamisengine.meshforge.gpu.RuntimeGeometryPayload;

/**
 * Result of loading a mesh through {@link MeshForgeAssetService}.
 *
 * Contains everything a backend needs for GPU upload:
 * <ul>
 *   <li>{@link RuntimeGeometryPayload} - vertex/index buffers with layout metadata</li>
 *   <li>{@link GpuGeometryUploadPlan} - stride, counts, bindings for GPU allocation</li>
 * </ul>
 *
 * @param payload   GPU-ready geometry data
 * @param plan      upload plan with stride/count/binding info
 * @param assetPath original asset path (for logging/debug)
 * @param fromMgi   true if loaded from MGI (fast path), false if imported from source format
 */
public record MeshLoadResult(
        RuntimeGeometryPayload payload,
        GpuGeometryUploadPlan plan,
        String assetPath,
        boolean fromMgi
) {}
