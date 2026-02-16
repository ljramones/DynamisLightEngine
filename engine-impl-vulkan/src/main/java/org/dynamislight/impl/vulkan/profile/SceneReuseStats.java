package org.dynamislight.impl.vulkan.profile;

public record SceneReuseStats(
        long reuseHits,
        long reorderReuseHits,
        long textureRebindHits,
        long fullRebuilds,
        long meshBufferRebuilds,
        long descriptorPoolBuilds,
        long descriptorPoolRebuilds
) {
}
