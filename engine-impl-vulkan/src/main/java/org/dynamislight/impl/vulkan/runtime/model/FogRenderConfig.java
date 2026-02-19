package org.dynamislight.impl.vulkan.runtime.model;

public record FogRenderConfig(boolean enabled, float r, float g, float b, float density, int steps, boolean degraded) {
}
