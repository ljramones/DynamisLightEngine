package org.dynamislight.impl.vulkan.command;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight runtime barrier trace collector for B.2 equivalence checks.
 */
public final class VulkanRuntimeBarrierTrace {
    private final List<ImageBarrierEvent> imageBarrierEvents = new ArrayList<>();

    public void recordImageBarrier(ImageBarrierEvent event) {
        if (event != null) {
            imageBarrierEvents.add(event);
        }
    }

    public List<ImageBarrierEvent> imageBarrierEvents() {
        return List.copyOf(imageBarrierEvents);
    }

    public void clear() {
        imageBarrierEvents.clear();
    }

    public record ImageBarrierEvent(
            int srcStageMask,
            int dstStageMask,
            int srcAccessMask,
            int dstAccessMask,
            int oldLayout,
            int newLayout,
            long imageHandle
    ) {
    }
}
