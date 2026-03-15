package org.dynamisengine.light.impl.vulkan.runtime.model;

public record MeshGeometryCacheProfile(long hits, long misses, long evictions, int entries, int maxEntries) {
}
