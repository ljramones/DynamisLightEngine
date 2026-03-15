package org.dynamisengine.light.impl.vulkan.reflection;

public record ReflectionRtPipelineDiagnostics(
        String blasLifecycleState,
        String tlasLifecycleState,
        String sbtLifecycleState,
        int blasObjectCount,
        int tlasInstanceCount,
        int sbtRecordCount
) {
}
