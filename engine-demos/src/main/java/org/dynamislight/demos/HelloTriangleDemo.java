package org.dynamislight.demos;

import org.dynamislight.api.scene.SceneDescriptor;

final class HelloTriangleDemo implements DemoDefinition {
    @Override
    public String id() {
        return "hello-triangle";
    }

    @Override
    public String description() {
        return "Minimal scene bootstrap with directional + spot lighting and post stack.";
    }

    @Override
    public SceneDescriptor buildScene(DemoRequest request) {
        return DemoScenes.helloTriangleScene();
    }
}
