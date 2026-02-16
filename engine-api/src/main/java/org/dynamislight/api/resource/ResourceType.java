package org.dynamislight.api.resource;

/**
 * Enum representing the type of a managed resource in the system.
 *
 * The {@code ResourceType} enum provides categorization for resources, allowing them
 * to be grouped and managed based on their purpose and usage in the runtime environment.
 * Each constant represents a distinct class of resources commonly used in real-time engines.
 *
 * Constants:
 *
 * - {@code MESH}:
 *   Represents a 3D geometry or model resource, typically used for rendering objects
 *   in a scene.
 *
 * - {@code MATERIAL}:
 *   Represents a resource defining surface properties, shaders, and textures
 *   to control how objects are visually rendered.
 *
 * - {@code TEXTURE}:
 *   Represents a 2D or 3D image resource, used as input for material rendering or
 *   other image-based operations.
 *
 * - {@code SHADER}:
 *   Represents a programmable GPU shader resource, used to define vertex, fragment,
 *   or other pipeline stages for rendering.
 *
 * - {@code SCENE_DATA}:
 *   Represents a resource containing hierarchical or structural information about
 *   a scene, such as entities, transformations, or scene graph configurations.
 *
 * - {@code GENERIC}:
 *   Represents a resource that does not fall into the above categories or serves a
 *   generic purpose, allowing flexibility in resource tracking and usage.
 */
public enum ResourceType {
    MESH,
    MATERIAL,
    TEXTURE,
    SHADER,
    SCENE_DATA,
    GENERIC
}
