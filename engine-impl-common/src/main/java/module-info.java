module org.dynamisengine.light.impl.common {
    exports org.dynamisengine.light.impl.common to
            org.dynamisengine.light.impl.opengl,
            org.dynamisengine.light.impl.vulkan;
    exports org.dynamisengine.light.impl.common.framegraph to
            org.dynamisengine.light.impl.opengl,
            org.dynamisengine.light.impl.vulkan;
    exports org.dynamisengine.light.impl.common.shadow to
            org.dynamisengine.light.impl.opengl,
            org.dynamisengine.light.impl.vulkan;
    exports org.dynamisengine.light.impl.common.texture to
            org.dynamisengine.light.impl.opengl,
            org.dynamisengine.light.impl.vulkan;
    exports org.dynamisengine.light.impl.common.upscale to
            org.dynamisengine.light.impl.opengl,
            org.dynamisengine.light.impl.vulkan;
    exports org.dynamisengine.light.impl.common.sky to
            org.dynamisengine.light.impl.opengl,
            org.dynamisengine.light.impl.vulkan,
            org.dynamisengine.sky.vulkan;

    requires transitive org.dynamisengine.light.api;
    requires java.desktop;
    requires org.lwjgl;
    requires org.lwjgl.ktx;
    requires com.github.luben.zstd_jni;

    uses org.dynamisengine.light.impl.common.upscale.ExternalUpscalerBridge;
    uses org.dynamisengine.light.impl.common.upscale.VendorUpscalerSdkProvider;
}
