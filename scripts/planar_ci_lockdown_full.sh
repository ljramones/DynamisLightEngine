#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_ROOT="${DLE_PLANAR_LOCKDOWN_OUTPUT_ROOT:-artifacts/compare/planar-lockdown-full}"
OUTPUT_ROOT_ABS="${ROOT_DIR}/${OUTPUT_ROOT}"
LOG_DIR="${OUTPUT_ROOT_ABS}/logs"
COMPARE_DIR="${OUTPUT_ROOT_ABS}/compare"
mkdir -p "${LOG_DIR}" "${COMPARE_DIR}"

VULKAN_TESTS=(
  "planarReflectionPathEmitsScopeAndOrderingContracts"
  "planarReflectionContractReportsConfiguredPlaneHeight"
  "planarClipHeightSceneMaintainsMirrorCameraContractAcrossFrames"
  "planarContractCoverageIncludesHybridAndRtHybridModes"
  "planarResourceContractFallsBackWhenPlanarPathInactive"
  "planarStabilityEnvelopeBreachEmitsUnderStrictThresholdsWhenScopeIsEmpty"
  "planarPerfGateBreachEmitsUnderStrictCaps"
  "planarScopePolicyAllowsProbeOnlyWhenConfigured"
  "planarSceneCoverageMatrixEmitsContractsForInteriorOutdoorMultiAndDynamic"
)
VULKAN_TEST_FILTER="VulkanEngineRuntimeIntegrationTest#$(IFS=+; echo "${VULKAN_TESTS[*]}")"

COMPARE_TESTS=(
  "compareHarnessReflectionsPlanarSceneHasBoundedDiff"
  "compareHarnessReflectionsHybridSceneHasBoundedDiff"
  "compareHarnessReflectionsHiZProbeSceneHasBoundedDiff"
  "compareHarnessReflectionsRtFallbackSceneHasBoundedDiff"
)
COMPARE_TEST_FILTER="BackendParityIntegrationTest#$(IFS=+; echo "${COMPARE_TESTS[*]}")"

run_step() {
  local name="$1"
  shift
  local log_file="${LOG_DIR}/${name}.log"
  echo "[planar-lockdown] ${name}"
  echo "[planar-lockdown] command: $*"
  "$@" | tee "${log_file}"
}

run_step "vulkan-planar-contracts" \
  mvn -B -ntp -pl engine-impl-vulkan -am test \
    -DskipITs \
    -Dtest="${VULKAN_TEST_FILTER}" \
    -Dsurefire.failIfNoSpecifiedTests=false

run_step "compare-planar-reflections" \
  mvn -B -ntp -pl engine-host-sample -am test \
    -Ddle.compare.tests=true \
    -Ddle.compare.outputDir="${COMPARE_DIR}" \
    -Dtest="${COMPARE_TEST_FILTER}" \
    -Dsurefire.failIfNoSpecifiedTests=false

cat > "${OUTPUT_ROOT_ABS}/summary.txt" <<EOF
Planar CI lockdown completed successfully.

Output root: ${OUTPUT_ROOT}
Logs:
  - ${LOG_DIR}/vulkan-planar-contracts.log
  - ${LOG_DIR}/compare-planar-reflections.log

Compare output:
  - ${COMPARE_DIR}
EOF

echo "[planar-lockdown] complete: ${OUTPUT_ROOT}"
