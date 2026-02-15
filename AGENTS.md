# Repository Guidelines

## Project Structure & Module Organization
DynamicLightEngine is a multi-module Maven project targeting Java/JDK 25:

- `engine-api`: stable host/runtime boundary DTOs and contracts (`org.dynamislight.api`)
- `engine-spi`: backend provider SPI and metadata (`org.dynamislight.spi`)
- `engine-impl-common`: shared backend runtime lifecycle base
- `engine-impl-opengl`: first runtime backend + ServiceLoader registration
- `engine-impl-vulkan`: Vulkan backend skeleton + ServiceLoader registration
- `engine-bridge-dynamisfx`: host bridge/mapping layer for DynamisFX
- `engine-host-sample`: minimal console host that discovers and runs a backend

Keep API and SPI JavaFX-agnostic. Backend-native objects must not cross `engine-api`.

## Build, Test, and Development Commands
Run from repository root:

- `mvn clean compile`: compile all modules
- `mvn test`: run tests across modules
- `mvn -pl engine-impl-opengl -am compile`: build OpenGL backend and dependencies
- `mvn -pl engine-impl-vulkan -am compile`: build Vulkan backend and dependencies
- `mvn -pl engine-api install`: publish local snapshot of API module
- `mvn -DskipTests install && mvn -f engine-host-sample/pom.xml exec:java`: run sample host loop

Use JDK 25 for normal development (`maven.compiler.release=25` in root `pom.xml`).

## Coding Style & Naming Conventions
Use 4-space indentation and UTF-8 source files. Prefer immutable records for DTOs and keep constructors defensive (`List.copyOf`, `Map.copyOf`, `Set.copyOf`).

- Classes/records/interfaces: `PascalCase`
- Methods/fields: `camelCase`
- Enums/constants: `UPPER_SNAKE_CASE`
- Package names: lowercase (`org.dynamislight.*`)

Respect lifecycle and threading contracts in runtime code: single engine thread, no callback reentrancy.

## Testing Guidelines
Place tests under each module at `src/test/java`. Mirror package structure and name tests by behavior (example: `OpenGlEngineRuntimeLifecycleTest`).

Minimum test coverage for new runtime features:

- lifecycle state transitions (`initialize`, `loadScene`, `render`, `shutdown`)
- invalid state/argument error paths (`EngineException`, `EngineErrorCode`)
- ServiceLoader backend discovery

## Commit & Pull Request Guidelines
Use Conventional Commits:

- `feat(api): add EngineCapabilities field`
- `fix(opengl): enforce initialize-before-render`
- `test(spi): add provider discovery tests`

PRs must include:

- concise problem/solution summary
- linked issue or task ID
- verification notes with exact commands run
- logs/screenshots when runtime behavior changes are visible
