package org.dynamislight.demos;

import org.dynamislight.api.scene.SceneDescriptor;

final class AaMotionStressDemo implements DemoDefinition {
    @Override
    public String id() {
        return "aa-motion-stress";
    }

    @Override
    public String description() {
        return "AA stress lane with thin geometry, alpha-like layering, and fast edge transitions.";
    }

    @Override
    public SceneDescriptor buildScene(DemoRequest request) {
        String aaMode = request.arg("aa-mode", "taa").toLowerCase();
        float blend = request.argFloat("aa-blend", 0.80f, 0.0f, 0.95f);
        float renderScale = request.argFloat("aa-render-scale", defaultRenderScale(aaMode), 0.5f, 1.0f);
        return DemoScenes.aaMotionStressScene(aaMode, blend, renderScale);
    }

    private static float defaultRenderScale(String aaMode) {
        return switch (aaMode) {
            case "tsr" -> 0.64f;
            case "tuua" -> 0.72f;
            case "dlaa" -> 1.0f;
            case "fxaa_low" -> 1.0f;
            default -> 1.0f;
        };
    }
}

