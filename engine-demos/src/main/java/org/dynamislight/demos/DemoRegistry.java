package org.dynamislight.demos;

import java.util.LinkedHashMap;
import java.util.Map;

final class DemoRegistry {
    private DemoRegistry() {
    }

    static Map<String, DemoDefinition> demos() {
        Map<String, DemoDefinition> demos = new LinkedHashMap<>();
        register(demos, new HelloTriangleDemo());
        register(demos, new MaterialBaselineDemo());
        register(demos, new LightsLocalArrayDemo());
        register(demos, new ShadowCascadeBaselineDemo());
        register(demos, new ShadowCascadeDebugDemo());
        register(demos, new ShadowLocalAtlasDemo());
        register(demos, new ShadowQualityMatrixDemo());
        register(demos, new ReflectionsSsrHizDemo());
        register(demos, new ReflectionsPlanarDemo());
        register(demos, new ReflectionsHybridDemo());
        register(demos, new FogSmokePostDemo());
        register(demos, new TelemetryExportDemo());
        register(demos, new ThresholdReplayDemo());
        register(demos, new AaMotionStressDemo());
        register(demos, new AaMatrixDemo());
        return Map.copyOf(demos);
    }

    private static void register(Map<String, DemoDefinition> demos, DemoDefinition demo) {
        demos.put(demo.id(), demo);
    }
}
