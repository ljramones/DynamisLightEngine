#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_ROOT="${DLE_RT_REFLECTIONS_REAL_LONGRUN_OUTPUT_ROOT:-artifacts/compare/rt-reflections-real-longrun}"
OUTPUT_ROOT_ABS="${ROOT_DIR}/${OUTPUT_ROOT}"
LOG_DIR="${OUTPUT_ROOT_ABS}/logs"
mkdir -p "${LOG_DIR}"

ITERATIONS="${DLE_RT_REFLECTIONS_LONGRUN_ITERATIONS:-5}"
if ! [[ "${ITERATIONS}" =~ ^[0-9]+$ ]] || [[ "${ITERATIONS}" -lt 1 ]]; then
  echo "[rt-reflections-real-longrun] invalid iteration count: ${ITERATIONS}" >&2
  exit 1
fi

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
  echo "[rt-reflections-real-longrun] ${name}"
  echo "[rt-reflections-real-longrun] command: $*"
  "$@" | tee "${log_file}"
}

for i in $(seq 1 "${ITERATIONS}"); do
  run_step "iteration-${i}" \
    mvn -B -ntp -pl engine-impl-vulkan -am test \
      -DskipITs \
      -Ddle.test.vulkan.real=true \
      -Dtest="${REAL_TEST_FILTER}" \
      -Dsurefire.failIfNoSpecifiedTests=false
done

cat > "${OUTPUT_ROOT_ABS}/summary.txt" <<EOF
RT reflections real-GPU long-run signoff completed successfully.

Iterations: ${ITERATIONS}
Output root: ${OUTPUT_ROOT}
Logs:
  - ${LOG_DIR}/iteration-1.log ... ${LOG_DIR}/iteration-${ITERATIONS}.log
EOF

echo "[rt-reflections-real-longrun] complete: ${OUTPUT_ROOT}"
