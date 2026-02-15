# DynamicLightEngine

Cross-platform Java rendering/game engine scaffold using a stable Java-first API boundary and backend SPI.

## Requirements

- JDK 25 (project enforces Java 25 via Maven Enforcer)
- Maven 3.9+

## JDK 25 setup

If you use `jenv`:

```bash
jenv local 25.0.1
java -version
mvn -version
```

Or shell-only:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -version
```

## Modules

- `engine-api`: immutable DTOs and runtime contracts (`org.dynamislight.api`)
- `engine-spi`: backend discovery SPI (`ServiceLoader`)
- `engine-impl-common`: shared lifecycle/runtime base for backend implementations
- `engine-impl-opengl`: OpenGL backend skeleton
- `engine-impl-vulkan`: Vulkan backend skeleton
- `engine-bridge-dynamisfx`: host bridge/mapping layer
- `engine-host-sample`: minimal console host that runs the lifecycle

## Build and test

```bash
mvn clean compile
mvn test
```

## Run sample host

Install snapshots, then run the sample host:

```bash
mvn -DskipTests install
mvn -f engine-host-sample/pom.xml exec:java
```

Select backend by argument:

```bash
mvn -f engine-host-sample/pom.xml exec:java -Dexec.args="vulkan"
```

## Current status

This is compile-first scaffolding for v1 interface contracts. OpenGL and Vulkan modules currently provide lifecycle-safe stub runtimes to validate API shape, backend discovery, and host integration flow.
