module org.dynamisengine.light.spi {
    exports org.dynamisengine.light.spi;
    exports org.dynamisengine.light.spi.registry;
    exports org.dynamisengine.light.spi.render;

    requires transitive org.dynamisengine.light.api;

    uses org.dynamisengine.light.spi.EngineBackendProvider;
}
