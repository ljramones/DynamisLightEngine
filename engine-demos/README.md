# DynamicLightEngine Demos Module

This module is the home for first-party demo content:
- demo scene descriptors
- material definitions
- reusable demo assets/manifests

## Layout

- `src/main/resources/demos/materials`:
  material definitions and material manifests.
- `src/main/resources/demos/scenes`:
  scene descriptors and scene manifests.

## Conventions

- Keep paths relative to `src/main/resources/demos`.
- Use stable IDs in manifests to avoid breaking references between scenes and materials.
- Add new demos under this module instead of `engine-host-sample` so sample host code stays runtime-focused.
