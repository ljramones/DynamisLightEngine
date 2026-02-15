#!/usr/bin/env bash
set -euo pipefail

OWNER_REPO="${1:-ljramones/DynamisLightEngine}"
export GH_REPO="$OWNER_REPO"

ensure_label() {
  local name="$1" color="$2" desc="$3"
  if gh label list -R "$OWNER_REPO" --limit 200 --search "$name" | awk '{print $1}' | grep -Fxq "$name"; then
    gh label edit "$name" -R "$OWNER_REPO" --color "$color" --description "$desc" >/dev/null
  else
    gh label create "$name" -R "$OWNER_REPO" --color "$color" --description "$desc" >/dev/null
  fi
}

ensure_milestone() {
  local title="$1" desc="$2"
  if gh api repos/{owner}/{repo}/milestones --paginate --jq '.[].title' | grep -Fxq "$title"; then
    return
  fi
  gh api repos/{owner}/{repo}/milestones -f title="$title" -f description="$desc" >/dev/null
}

create_issue() {
  local title="$1" body="$2" labels="$3" milestone="$4"
  if gh issue list -R "$OWNER_REPO" --state all --search "\"$title\" in:title" --limit 100 --json title --jq '.[].title' | grep -Fxq "$title"; then
    echo "Skip existing issue: $title"
    return
  fi
  gh issue create -R "$OWNER_REPO" --title "$title" --body "$body" --label "$labels" --milestone "$milestone" >/dev/null
  echo "Created issue: $title"
}

# Labels
ensure_label "type:feature" "1D76DB" "New feature work"
ensure_label "type:refactor" "5319E7" "Code structure improvements"
ensure_label "type:test" "0E8A16" "Test coverage and quality work"
ensure_label "type:docs" "0075CA" "Documentation improvements"
ensure_label "area:api" "B60205" "API module and contracts"
ensure_label "area:spi" "D93F0B" "SPI and backend discovery"
ensure_label "area:opengl" "FBCA04" "OpenGL backend work"
ensure_label "area:bridge" "C2E0C6" "DynamisFX bridge and mapping"
ensure_label "area:ci" "0052CC" "CI/CD and automation"
ensure_label "priority:p0" "B60205" "Highest priority"
ensure_label "priority:p1" "D93F0B" "Important but not blocking"

# Milestones
ensure_milestone "M1: API Contract Hardening" "Freeze v1 contract behavior and validation semantics."
ensure_milestone "M2: SPI Discovery And Backend Selection" "Robust backend discovery/selection behavior."
ensure_milestone "M3: OpenGL Real Bootstrap" "Replace stub render path with real OpenGL bootstrap."
ensure_milestone "M4: Bridge + Scene Ingestion Baseline" "Minimal but real host-to-engine mapping."
ensure_milestone "M5: Observability + CI" "Make behavior measurable and reproducible."
ensure_milestone "M6: Fog + Smoke Baseline" "First volumetric feature delivery."

# Issues
create_issue "M1-1: Add lifecycle/threading/ownership Javadocs across engine-api" $'- Labels: type:docs, area:api, priority:p0\n- Depends on: none\n\nAcceptance criteria:\n- Every public type in `engine-api` has class-level Javadoc.\n- `EngineRuntime` and `EngineHostCallbacks` document threading and reentrancy rules.\n- DTO ownership rules for buffers/handles are explicit.' "type:docs,area:api,priority:p0" "M1: API Contract Hardening"

create_issue "M1-2: Implement EngineConfig and SceneDescriptor validators" $'- Labels: type:feature, area:api, priority:p0\n- Depends on: M1-1\n\nAcceptance criteria:\n- Add validation utility for config and scene inputs.\n- Returns/throws `EngineException` with mapped `EngineErrorCode`.\n- Host sample uses validators before runtime calls.' "type:feature,area:api,priority:p0" "M1: API Contract Hardening"

create_issue "M1-3: Add API contract tests" $'- Labels: type:test, area:api, priority:p0\n- Depends on: M1-2\n\nAcceptance criteria:\n- Tests cover immutability copy semantics.\n- Tests cover version compatibility behavior.\n- Tests cover validator failure paths.' "type:test,area:api,priority:p0" "M1: API Contract Hardening"

create_issue "M2-1: Add BackendRegistry helper" $'- Labels: type:feature, area:spi, priority:p0\n- Depends on: M1-3\n\nAcceptance criteria:\n- Resolve backend by id using `ServiceLoader`.\n- Expose discovered providers with metadata.\n- Handle no-match with `BACKEND_NOT_FOUND`.' "type:feature,area:spi,priority:p0" "M2: SPI Discovery And Backend Selection"

create_issue "M2-2: Add duplicate-id and version mismatch handling" $'- Labels: type:feature, area:spi, priority:p0\n- Depends on: M2-1\n\nAcceptance criteria:\n- Duplicate backend IDs are detected deterministically.\n- Unsupported API versions fail with clear error.\n- Behavior is covered by tests.' "type:feature,area:spi,priority:p0" "M2: SPI Discovery And Backend Selection"

create_issue "M3-1: Add OpenGL binding and context init" $'- Labels: type:feature, area:opengl, priority:p0\n- Depends on: M2-2\n\nAcceptance criteria:\n- OpenGL backend initializes a real graphics context.\n- Initialization failures map to `BACKEND_INIT_FAILED`.\n- Runtime still respects lifecycle contracts.' "type:feature,area:opengl,priority:p0" "M3: OpenGL Real Bootstrap"

create_issue "M3-2: Implement clear-color + triangle render" $'- Labels: type:feature, area:opengl, priority:p0\n- Depends on: M3-1\n\nAcceptance criteria:\n- `render()` performs actual draw work.\n- Resize updates viewport/swapchain resources.\n- Frame stats are populated from real timing.' "type:feature,area:opengl,priority:p0" "M3: OpenGL Real Bootstrap"

create_issue "M3-3: OpenGL lifecycle regression tests" $'- Labels: type:test, area:opengl, priority:p0\n- Depends on: M3-2\n\nAcceptance criteria:\n- Tests verify init -> load -> update/render -> resize -> shutdown path.\n- Tests verify invalid state and invalid argument failures.' "type:test,area:opengl,priority:p0" "M3: OpenGL Real Bootstrap"

create_issue "M4-1: Implement SceneMapper v1 baseline mapping" $'- Labels: type:feature, area:bridge, priority:p1\n- Depends on: M3-3\n\nAcceptance criteria:\n- Maps cameras/transforms/meshes/materials/lights/environment.\n- Invalid source data maps to `SCENE_VALIDATION_FAILED`.' "type:feature,area:bridge,priority:p1" "M4: Bridge + Scene Ingestion Baseline"

create_issue "M4-2: Implement InputMapper key/mouse mapping matrix" $'- Labels: type:feature, area:bridge, priority:p1\n- Depends on: M4-1\n\nAcceptance criteria:\n- Stable mapping for movement/camera controls.\n- Unit tests cover key combinations and deltas.' "type:feature,area:bridge,priority:p1" "M4: Bridge + Scene Ingestion Baseline"

create_issue "M5-1: Standardize logs/events from runtime lifecycle and render loop" $'- Labels: type:feature, area:api, priority:p1\n- Depends on: M4-2\n\nAcceptance criteria:\n- Required categories emitted (`LIFECYCLE`, `RENDER`, `SCENE`, `SHADER`, `PERF`, `ERROR`).\n- Event emission is non-blocking and documented.' "type:feature,area:api,priority:p1" "M5: Observability + CI"

create_issue "M5-2: Add GitHub Actions CI (JDK 25)" $'- Labels: type:feature, area:ci, priority:p0\n- Depends on: M5-1\n\nAcceptance criteria:\n- CI runs `mvn test` on pull requests and main.\n- Build fails if Java version is not 25.' "type:feature,area:ci,priority:p0" "M5: Observability + CI"

create_issue "M6-1: Implement fog baseline (exponential + height)" $'- Labels: type:feature, area:opengl, priority:p1\n- Depends on: M5-2\n\nAcceptance criteria:\n- `FogDesc` is consumed and affects render output.\n- Quality tier impacts fog quality.' "type:feature,area:opengl,priority:p1" "M6: Fog + Smoke Baseline"

create_issue "M6-2: Implement smoke emitter baseline" $'- Labels: type:feature, area:opengl, priority:p1\n- Depends on: M6-1\n\nAcceptance criteria:\n- `SmokeEmitterDesc` is consumed and rendered.\n- Runtime emits `EngineWarning` when degrading quality.' "type:feature,area:opengl,priority:p1" "M6: Fog + Smoke Baseline"

echo "Backlog sync complete for $OWNER_REPO"
