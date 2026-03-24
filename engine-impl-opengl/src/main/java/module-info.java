module org.dynamisengine.light.impl.opengl {
    requires transitive org.dynamisengine.light.api;
    requires org.dynamisengine.light.spi;
    requires org.dynamisengine.light.impl.common;
    requires org.lwjgl;
    requires org.lwjgl.glfw;
    requires org.lwjgl.opengl;
    requires org.lwjgl.stb;
    requires com.fasterxml.jackson.databind;
    requires java.desktop;
    requires meshforge;
    requires meshforge.dynamisgpu;
    requires org.dynamisengine.debug.api;
    requires org.dynamisengine.ui.api;
    requires org.dynamisengine.ui.core;
    requires org.dynamisengine.ui.debug;

    exports org.dynamisengine.light.impl.opengl;

    provides org.dynamisengine.light.spi.EngineBackendProvider
        with org.dynamisengine.light.impl.opengl.OpenGlBackendProvider;
}
