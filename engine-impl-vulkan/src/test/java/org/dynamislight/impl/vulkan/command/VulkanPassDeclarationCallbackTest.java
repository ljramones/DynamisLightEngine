package org.dynamislight.impl.vulkan.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.dynamislight.impl.vulkan.graph.VulkanExecutableRenderGraphBuilder;
import org.junit.jupiter.api.Test;

class VulkanPassDeclarationCallbackTest {
    @Test
    void shadowRecorderDeclaresNonNullCallback() {
        VulkanShadowPassRecorder recorder = new VulkanShadowPassRecorder();
        VulkanExecutableRenderGraphBuilder builder = new VulkanExecutableRenderGraphBuilder();

        recorder.declarePasses(
                builder,
                null,
                null,
                new VulkanRenderCommandRecorder.ShadowPassInputs(
                        1, 1024, true, false, 4, 24, 24, 6,
                        1L, 1L, 1L, 1L, new long[]{1L}, 1L, 1, false, false
                ),
                List.of(),
                i -> 0
        );

        var declarations = builder.build();
        assertFalse(declarations.isEmpty());
        assertTrue(declarations.stream().allMatch(d -> d.executeCallback() != null));
    }

    @Test
    void mainRecorderDeclaresNonNullCallback() {
        VulkanMainPassRecorder recorder = new VulkanMainPassRecorder();
        VulkanExecutableRenderGraphBuilder builder = new VulkanExecutableRenderGraphBuilder();

        recorder.declarePasses(
                builder,
                null,
                null,
                new VulkanRenderCommandRecorder.MainPassInputs(
                        1, 1280, 720, 1L, 1L, 1L, 1L, 1L, 0, 0f
                ),
                List.of(),
                i -> 0
        );

        var declarations = builder.build();
        assertFalse(declarations.isEmpty());
        assertTrue(declarations.stream().allMatch(d -> d.executeCallback() != null));
    }

    @Test
    void planarRecorderDeclaresCallbackWhenPlanarActive() {
        VulkanPlanarReflectionPassRecorder recorder = new VulkanPlanarReflectionPassRecorder();
        VulkanExecutableRenderGraphBuilder builder = new VulkanExecutableRenderGraphBuilder();

        recorder.declarePasses(
                builder,
                null,
                null,
                new VulkanRenderCommandRecorder.PlanarReflectionPassInputs(
                        1, 1280, 720, 1L, 1L, 1L, 1L, 1L,
                        (1 << 14) | (1 << 18) | (1 << 20),
                        0f,
                        0L, -1, -1,
                        2L, 3L,
                        false
                ),
                List.of(),
                i -> 0
        );

        var declarations = builder.build();
        assertFalse(declarations.isEmpty());
        assertTrue(declarations.stream().allMatch(d -> d.executeCallback() != null));
    }

    @Test
    void postRecorderDeclaresNonNullCallback() {
        VulkanPostCompositePassRecorder recorder = new VulkanPostCompositePassRecorder();
        VulkanExecutableRenderGraphBuilder builder = new VulkanExecutableRenderGraphBuilder();

        recorder.declarePasses(
                builder,
                null,
                null,
                new VulkanRenderCommandRecorder.PostCompositeInputs(
                        0, 1280, 720,
                        false, true, 1f, 2.2f,
                        false, false, 1f, 0.5f,
                        1f, 1f, 0f, 1f,
                        false, 1f,
                        false, 0.9f, false,
                        0f, 0f, 0f, 0f,
                        1f, 1f,
                        false, 0f,
                        false, 0,
                        1f, 1f, 1f, 0.9f, 0f, 0f,
                        0,
                        1L, 1L, 1L, 1L,
                        1L, 0L, 0L, 0L, 1L, 1L,
                        new long[]{1L}
                ),
                b -> { },
                b -> { }
        );

        var declarations = builder.build();
        assertFalse(declarations.isEmpty());
        assertTrue(declarations.stream().allMatch(d -> d.executeCallback() != null));
    }
}
