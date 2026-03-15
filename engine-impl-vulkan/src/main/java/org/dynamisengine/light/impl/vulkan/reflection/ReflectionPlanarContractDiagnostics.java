package org.dynamisengine.light.impl.vulkan.reflection;

public record ReflectionPlanarContractDiagnostics(
        String status,
        int scopedMeshEligibleCount,
        int scopedMeshExcludedCount,
        boolean mirrorCameraActive,
        boolean dedicatedCaptureLaneActive
) {
}
