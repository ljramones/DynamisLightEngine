package org.dynamislight.spi.render;

import java.util.List;

/**
 * Push-constant requirement metadata.
 *
 * @param targetPassId host pass where requirement applies
 * @param stages shader stages that consume this range
 * @param byteOffset push-constant offset
 * @param byteSize push-constant range size
 */
public record RenderPushConstantRequirement(
        String targetPassId,
        List<RenderShaderStage> stages,
        int byteOffset,
        int byteSize
) {
    public RenderPushConstantRequirement {
        targetPassId = targetPassId == null ? "" : targetPassId.trim();
        stages = stages == null ? List.of() : List.copyOf(stages);
        byteOffset = Math.max(0, byteOffset);
        byteSize = Math.max(0, byteSize);
    }
}
