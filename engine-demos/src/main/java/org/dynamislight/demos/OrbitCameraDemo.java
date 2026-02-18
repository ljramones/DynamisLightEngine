package org.dynamislight.demos;

import java.util.Locale;
import java.util.Map;
import org.dynamislight.api.scene.SceneDescriptor;

final class OrbitCameraDemo implements DemoDefinition {
    @Override
    public String id() {
        return "orbit-camera";
    }

    @Override
    public String description() {
        return "Animated moving-image demo with an orbiting camera path.";
    }

    @Override
    public boolean isDynamicScene() {
        return true;
    }

    @Override
    public SceneDescriptor buildScene(DemoRequest request) {
        return sceneForFrame(request, 0, Math.max(1, request.seconds() * 60), 0.0);
    }

    @Override
    public SceneDescriptor sceneForFrame(DemoRequest request, int frameIndex, int totalFrames, double elapsedSeconds) {
        float radiansPerSecond = request.argFloat("orbit-speed-rads", 0.9f, 0.1f, 3.0f);
        float phase = request.argFloat("orbit-phase-rads", 0.0f, -6.28318f, 6.28318f);
        float angle = phase + (float) elapsedSeconds * radiansPerSecond;
        return DemoScenes.orbitCameraScene(angle);
    }

    @Override
    public Map<String, String> backendOptions(DemoRequest request) {
        String prefix = request.backendId().toLowerCase(Locale.ROOT);
        return Map.of(prefix + ".aaPreset", "quality");
    }
}
