#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_ROOT="${DLE_RT_REFLECTIONS_LOCKDOWN_OUTPUT_ROOT:-artifacts/compare/rt-reflections-lockdown-full}"
OUTPUT_ROOT_ABS="${ROOT_DIR}/${OUTPUT_ROOT}"
LOG_DIR="${OUTPUT_ROOT_ABS}/logs"
COMPARE_DIR="${OUTPUT_ROOT_ABS}/compare"
mkdir -p "${LOG_DIR}" "${COMPARE_DIR}"

VULKAN_TESTS=(
  "rtReflectionRequestInMockContextActivatesExecutionLaneAndDenoisePath"
  "rtReflectionRequireActiveEmitsBreachWhenLaneUnavailable"
  "rtReflectionRequireMultiBounceEmitsBreachWhenUnavailable"
  "rtReflectionRequireDedicatedPipelineEmitsBreachWhenUnavailable"
  "rtReflectionDedicatedPipelineEnabledActivatesPreviewLaneInMockContext"
  "rtPerfGateBreachEmitsUnderStrictCaps"
  "rtHybridCompositionBreachUsesConfiguredThresholdAndCooldown"
  "rtDenoiseEnvelopeBreachEmitsUnderStrictThresholds"
  "rtAsBudgetBreachEmitsUnderStrictThresholds"
  "transparentCandidatesEmitStageGateWarningUntilRtLaneIsActive"
  "transparentCandidatesWithRtPathEnablePreviewStageGateAndRuntimeIntegrationBit"
  "planarContractCoverageIncludesHybridAndRtHybridModes"
)
VULKAN_TEST_FILTER="VulkanEngineRuntimeIntegrationTest#$(IFS=+; echo "${VULKAN_TESTS[*]}")"

COMPARE_TESTS=(
  "compareHarnessReflectionsRtFallbackSceneHasBoundedDiff"
  "compareHarnessReflectionsHybridSceneHasBoundedDiff"
)
COMPARE_TEST_FILTER="BackendParityIntegrationTest#$(IFS=+; echo "${COMPARE_TESTS[*]}")"

run_step() {
  local name="$1"
  shift
  local log_file="${LOG_DIR}/${name}.log"
  echo "[rt-reflections-lockdown] ${name}"
  echo "[rt-reflections-lockdown] command: $*"
  "$@" | tee "${log_file}"
}

run_step "vulkan-rt-reflection-contracts" \
  mvn -B -ntp -pl engine-impl-vulkan -am test \
    -DskipITs \
    -Dtest="${VULKAN_TEST_FILTER}" \
    -Dsurefire.failIfNoSpecifiedTests=false

run_step "compare-rt-reflection-scenes" \
  mvn -B -ntp -pl engine-host-sample -am test \
    -Ddle.compare.tests=true \
    -Ddle.compare.outputDir="${COMPARE_DIR}" \
    -Dtest="${COMPARE_TEST_FILTER}" \
    -Dsurefire.failIfNoSpecifiedTests=false

cat > "${OUTPUT_ROOT_ABS}/summary.txt" <<EOF
RT reflections CI lockdown completed successfully.

Output root: ${OUTPUT_ROOT}
Logs:
  - ${LOG_DIR}/vulkan-rt-reflection-contracts.log
  - ${LOG_DIR}/compare-rt-reflection-scenes.log

Compare output:
  - ${COMPARE_DIR}
EOF

echo "[rt-reflections-lockdown] complete: ${OUTPUT_ROOT}"
