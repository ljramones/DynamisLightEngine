package org.dynamislight.demos;

import org.dynamislight.api.scene.SceneDescriptor;

final class AaMatrixDemo implements DemoDefinition {
    @Override
    public String id() {
        return "aa-matrix";
    }

    @Override
    public String description() {
        return "AA-focused scene. Configure with --aa-mode and optional render-scale tuning args.";
    }

    @Override
    public SceneDescriptor buildScene(DemoRequest request) {
        String aaMode = request.arg("aa-mode", "tsr").toLowerCase();
        float blend = request.argFloat("aa-blend", 0.78f, 0.0f, 0.95f);
        float renderScale = request.argFloat("aa-render-scale", defaultRenderScale(aaMode), 0.5f, 1.0f);
        return DemoScenes.sceneWithAa(aaMode, true, blend, renderScale);
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
