package org.dynamislight.impl.vulkan.vfx;

import org.dynamisvfx.api.DebrisSpawnEvent;
import org.dynamisvfx.api.PhysicsHandoff;

import java.util.logging.Logger;

public final class VulkanVfxPhysicsHandoffAdapter implements PhysicsHandoff {
    private static final Logger LOG = Logger.getLogger(VulkanVfxPhysicsHandoffAdapter.class.getName());

    @Override
    public void onDebrisSpawn(DebrisSpawnEvent event) {
        if (event == null) {
            return;
        }
        LOG.fine(() -> "[VFX] Debris spawn emitter=" + event.sourceEmitterId() + ", mass=" + event.mass());
    }
}
