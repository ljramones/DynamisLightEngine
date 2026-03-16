module org.dynamisengine.light.api {
    exports org.dynamisengine.light.api.config;
    exports org.dynamisengine.light.api.error;
    exports org.dynamisengine.light.api.event;
    exports org.dynamisengine.light.api.input;
    exports org.dynamisengine.light.api.logging;
    exports org.dynamisengine.light.api.mesh;
    exports org.dynamisengine.light.api.resource;
    exports org.dynamisengine.light.api.runtime;
    exports org.dynamisengine.light.api.scene;
    exports org.dynamisengine.light.api.validation;

    requires transitive dynamis.gpu.api;
}
