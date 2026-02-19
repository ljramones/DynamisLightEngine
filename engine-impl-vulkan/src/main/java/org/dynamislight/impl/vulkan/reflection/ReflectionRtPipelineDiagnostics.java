package org.dynamislight.impl.vulkan.reflection;

public record ReflectionRtPipelineDiagnostics(
        String blasLifecycleState,
        String tlasLifecycleState,
        String sbtLifecycleState,
        int blasObjectCount,
        int tlasInstanceCount,
        int sbtRecordCount
) {
}
