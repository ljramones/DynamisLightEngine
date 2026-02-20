# Sky Contract v2 Checklist (Vulkan Scope)

- [x] Add deterministic sky capability planner (`VulkanSkyCapabilityPlanner`) with explicit expected/active/pruned signals.
- [x] Add planner output model (`VulkanSkyCapabilityPlan`) for parser-friendly runtime warning emission.
- [x] Add sky capability descriptor v2 (`VulkanSkyCapabilityDescriptorV2`) with:
  - [x] modes (`hdri`, `procedural`, `atmosphere`)
  - [x] post-composite shader contribution and module declaration
  - [x] descriptor/uniform/resource declarations
  - [x] telemetry declaration including `SKY_CAPABILITY_PLAN_ACTIVE`
- [x] Wire runtime sky state to planner output for warning/diagnostics consistency.
- [x] Add planner unit coverage (`VulkanSkyCapabilityPlannerTest`).
- [x] Add descriptor completeness + cross-capability validator coverage in `VulkanCapabilityContractV2DescriptorsTest`.
- [x] Add lockdown runner (`scripts/sky_contract_v2_lockdown.sh`).

Scope note:
- This checklist covers contract/planner/telemetry hardening only.
- Advanced sky execution paths (procedural sky, physical atmosphere, clouds, celestial/night effects) remain future implementation work.
