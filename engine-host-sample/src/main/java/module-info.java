module org.dynamisengine.light.sample {
    requires org.dynamisengine.light.api;
    requires org.dynamisengine.light.spi;
    requires org.dynamisengine.light.impl.opengl;
    requires org.dynamisengine.light.impl.vulkan;

    requires org.dynamisengine.core;
    requires org.vectrix;

    // Automatic modules (no module-info.java in these artifacts)
    requires ecs.api;
    requires ecs.core;
    requires scene.api;
    requires scene.core;
    requires session.api;
    requires session.core;
    requires session.runtime;

    requires java.desktop;  // javax.imageio

    exports org.dynamisengine.light.sample;
    exports org.dynamisengine.light.sample.save;
    exports org.dynamisengine.light.sample.save.codecs;
}
