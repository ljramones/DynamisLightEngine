module org.dynamisengine.light.impl.vulkan {
    requires transitive org.dynamisengine.light.api;
    requires org.dynamisengine.light.spi;
    requires org.dynamisengine.light.impl.common;
    requires org.dynamisengine.animis.runtime;
    requires meshforge;
    requires meshforge.loader;
    requires dynamis.gpu.api;
    requires dynamis.gpu.vulkan;
    requires org.dynamisvfx.api;
    requires org.dynamisvfx.vulkan;
    requires org.lwjgl;
    requires org.lwjgl.glfw;
    requires org.lwjgl.vulkan;
    requires org.lwjgl.shaderc;
    requires org.lwjgl.stb;
    requires com.fasterxml.jackson.databind;
    requires org.vectrix;
    requires java.desktop;
    requires java.logging;

    exports org.dynamisengine.light.impl.vulkan;
    exports org.dynamisengine.light.impl.vulkan.bootstrap;

    opens org.dynamisengine.light.impl.vulkan.state;

    uses org.dynamisengine.light.impl.common.sky.SkyRenderBridge;

    provides org.dynamisengine.light.spi.EngineBackendProvider
        with org.dynamisengine.light.impl.vulkan.bootstrap.VulkanBackendProvider;
}
