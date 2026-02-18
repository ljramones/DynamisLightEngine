#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_ROOT="${DLE_PLANAR_REAL_SIGNOFF_OUTPUT_ROOT:-artifacts/compare/planar-real-signoff}"
OUTPUT_ROOT_ABS="${ROOT_DIR}/${OUTPUT_ROOT}"
LOG_DIR="${OUTPUT_ROOT_ABS}/logs"
mkdir -p "${LOG_DIR}"

REAL_TESTS=(
  "guardedRealVulkanInitPath"
  "guardedRealVulkanPlanarPerfTimingSourceFollowsTimestampAvailability"
  "planarClipHeightSceneMaintainsMirrorCameraContractAcrossFrames"
  "planarRapidCameraMovementMaintainsContractAndCoverage"
  "planarFrequentPlaneHeightChangesMaintainMirrorContract"
  "planarSelectiveScopeStressMaintainsDeterministicEligibleCounts"
)
REAL_TEST_FILTER="VulkanEngineRuntimeIntegrationTest#$(IFS=+; echo "${REAL_TESTS[*]}")"

run_step() {
  local name="$1"
  shift
  local log_file="${LOG_DIR}/${name}.log"
  echo "[planar-real-signoff] ${name}"
  echo "[planar-real-signoff] command: $*"
  "$@" | tee "${log_file}"
}

run_step "vulkan-planar-real-signoff" \
  mvn -B -ntp -pl engine-impl-vulkan -am test \
    -DskipITs \
    -Ddle.test.vulkan.real=true \
    -Dtest="${REAL_TEST_FILTER}" \
    -Dsurefire.failIfNoSpecifiedTests=false

if [[ "${DLE_PLANAR_REAL_SIGNOFF_LONG:-false}" == "true" ]]; then
  run_step "vulkan-planar-real-signoff-long" \
    mvn -B -ntp -pl engine-impl-vulkan -am test \
      -DskipITs \
      -Ddle.test.vulkan.real=true \
      -Ddle.test.vulkan.real.long=true \
      -Dtest="VulkanEngineRuntimeIntegrationTest#realVulkanExtendedEnduranceMaintainsProfilesAndAvoidsCallbackErrors+realVulkanLongEnduranceMatrixMaintainsProfilesAndErrorPathStability" \
      -Dsurefire.failIfNoSpecifiedTests=false
fi

cat > "${OUTPUT_ROOT_ABS}/summary.txt" <<EOF
Planar real-GPU signoff run completed successfully.

Output root: ${OUTPUT_ROOT}
Logs:
  - ${LOG_DIR}/vulkan-planar-real-signoff.log
EOF

if [[ "${DLE_PLANAR_REAL_SIGNOFF_LONG:-false}" == "true" ]]; then
  cat >> "${OUTPUT_ROOT_ABS}/summary.txt" <<EOF
  - ${LOG_DIR}/vulkan-planar-real-signoff-long.log
EOF
fi

echo "[planar-real-signoff] complete: ${OUTPUT_ROOT}"
