package org.dynamislight.demos;

import java.util.LinkedHashMap;
import java.util.Map;

final class DemoRegistry {
    private DemoRegistry() {
    }

    static Map<String, DemoDefinition> demos() {
        Map<String, DemoDefinition> demos = new LinkedHashMap<>();
        register(demos, new HelloTriangleDemo());
        register(demos, new AaMatrixDemo());
        return Map.copyOf(demos);
    }

    private static void register(Map<String, DemoDefinition> demos, DemoDefinition demo) {
        demos.put(demo.id(), demo);
    }
}
