# Post Stack Execution Modularization Checklist

Date: 2026-02-19  
Scope: move post stack composition from descriptor-only planning to module-owned execution contracts.

## Objectives

- [x] Add module-owned execution contract model for post stack slices.
- [x] Assign ownership boundaries by feature:
  - `vulkan.post` (`tonemap`, `bloom`, `ssao`, `smaa`)
  - `vulkan.aa` (`post.aa.taa_resolve`)
  - `vulkan.reflections` (`post.reflections.resolve`)
- [x] Replace legacy post module plan with execution-contract plan.
- [x] Derive post-pass graph read/write declarations from active execution contracts.
- [x] Add unit tests for ownership boundaries and pass IO contract derivation.

## Verification

- `mvn -pl engine-impl-vulkan -am -Dtest=VulkanPostCompositePassRecorderTest -Dsurefire.failIfNoSpecifiedTests=false test`
