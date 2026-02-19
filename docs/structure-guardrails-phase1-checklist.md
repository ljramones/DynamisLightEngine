# Structure Guardrails Phase 1 Checklist

Goal: keep maintainability guardrails active while continuing capability work.

## Scope

- Enforce Java class-size ceiling.
- Enforce Vulkan root-package hygiene.
- Keep docs/wishlist synchronized with structural policy.
- Require verification commands on each structural refactor slice.

## Guardrail Defaults

- Class hard limit: `1500` lines (`MAX_FILE_LINES`).
- Large-class report threshold: `1000` lines (`REPORT_THRESHOLD`).
- Vulkan root package max direct classes: `8` (`MAX_VULKAN_ROOT_CLASSES`).
- Guardrail scope default: `vulkan` (`SCOPE=vulkan`); optional full-tree scan with `SCOPE=all`.
- Script: `scripts/java_structure_guardrails.sh`.

## Phase 1 Checklist

- [x] Add automated guardrail script for class size + root package count.
- [x] Baseline current Vulkan root package direct class count.
- [x] Record guardrail policy in docs.
- [x] Update wishlist with infrastructure guardrail status.
- [x] Run guardrail script locally and keep output clean.
- [x] Run focused Vulkan verification (`compile` + key integration test).

## Verification Commands

```bash
scripts/java_structure_guardrails.sh
# optional full-tree audit:
SCOPE=all scripts/java_structure_guardrails.sh
mvn -pl engine-impl-vulkan -am -DskipTests compile
mvn -pl engine-impl-vulkan -am -Dtest=VulkanEngineRuntimeIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

## Next Phase (follow-up)

- Add module-specific package-count caps for other hotspots when needed.
- Add CI workflow lane invoking `scripts/java_structure_guardrails.sh` on PRs.
- Continue splitting files that approach `1200+` lines before they reach hard limit.
