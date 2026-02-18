#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_ROOT="${DLE_RT_REFLECTIONS_REAL_SIGNOFF_OUTPUT_ROOT:-artifacts/compare/rt-reflections-real-signoff}"
OUTPUT_ROOT_ABS="${ROOT_DIR}/${OUTPUT_ROOT}"
LOG_DIR="${OUTPUT_ROOT_ABS}/logs"
mkdir -p "${LOG_DIR}"

REAL_TESTS=(
  "guardedRealVulkanInitPath"
  "guardedRealVulkanRtReflectionContractEmitsPathDiagnostics"
  "guardedRealVulkanRtRequireActiveBehaviorMatchesLaneAvailability"
  "guardedRealVulkanRtRequireDedicatedPipelineFollowsCapabilityAndEnableState"
)
REAL_TEST_FILTER="VulkanEngineRuntimeIntegrationTest#$(IFS=+; echo "${REAL_TESTS[*]}")"

run_step() {
  local name="$1"
  shift
  local log_file="${LOG_DIR}/${name}.log"
  echo "[rt-reflections-real-signoff] ${name}"
  echo "[rt-reflections-real-signoff] command: $*"
  "$@" | tee "${log_file}"
}

run_step "vulkan-rt-reflections-real-signoff" \
  mvn -B -ntp -pl engine-impl-vulkan -am test \
    -DskipITs \
    -Ddle.test.vulkan.real=true \
    -Dtest="${REAL_TEST_FILTER}" \
    -Dsurefire.failIfNoSpecifiedTests=false

cat > "${OUTPUT_ROOT_ABS}/summary.txt" <<EOF
RT reflections real-GPU signoff run completed successfully.

Output root: ${OUTPUT_ROOT}
Logs:
  - ${LOG_DIR}/vulkan-rt-reflections-real-signoff.log
EOF

echo "[rt-reflections-real-signoff] complete: ${OUTPUT_ROOT}"
