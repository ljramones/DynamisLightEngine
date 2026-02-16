package org.dynamislight.impl.vulkan;

import java.util.Set;
import org.dynamislight.api.EngineCapabilities;
import org.dynamislight.api.EngineConfig;
import org.dynamislight.api.EngineException;
import org.dynamislight.api.EngineWarning;
import org.dynamislight.api.QualityTier;
import org.dynamislight.impl.common.AbstractEngineRuntime;

public final class VulkanEngineRuntime extends AbstractEngineRuntime {
    private final VulkanContext context = new VulkanContext();
    private boolean mockContext = true;

    public VulkanEngineRuntime() {
        super(
                "Vulkan",
                new EngineCapabilities(
                        Set.of("vulkan"),
                        true,
                        true,
                        true,
                        true,
                        7680,
                        4320,
                        Set.of(QualityTier.LOW, QualityTier.MEDIUM, QualityTier.HIGH, QualityTier.ULTRA)
                ),
                16.2,
                7.8
        );
    }

    @Override
    protected void onInitialize(EngineConfig config) throws EngineException {
        mockContext = Boolean.parseBoolean(config.backendOptions().getOrDefault("vulkan.mockContext", "true"));
        if (!mockContext) {
            context.initialize(config.appName());
        }
    }

    @Override
    protected RenderMetrics onRender() {
        if (mockContext) {
            return null;
        }
        VulkanContext.VulkanFrameMetrics frame = context.renderFrame();
        return renderMetrics(
                frame.cpuFrameMs(),
                frame.gpuFrameMs(),
                frame.drawCalls(),
                frame.triangles(),
                frame.visibleObjects(),
                frame.gpuMemoryBytes()
        );
    }

    @Override
    protected void onShutdown() {
        if (!mockContext) {
            context.shutdown();
        }
    }

    @Override
    protected java.util.List<EngineWarning> baselineWarnings() {
        return java.util.List.of(new EngineWarning("FEATURE_LIMITED", "Vulkan backend currently runs without presentation surface"));
    }
}
