#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

RUN_REAL_MATRIX="${DLE_SHADOW_CI_REAL_MATRIX:-0}"
RUN_LONGRUN="${DLE_SHADOW_CI_LONGRUN:-0}"
VULKAN_MODE="${DLE_COMPARE_VULKAN_MODE:-mock}"
OUT_ROOT="${DLE_SHADOW_CI_OUTPUT_ROOT:-artifacts/compare/shadow-ci-$(date +%Y%m%d-%H%M%S)}"

mkdir -p "$OUT_ROOT"

echo "Shadow CI matrix"
echo "  output root: $OUT_ROOT"
echo "  vulkan mode: $VULKAN_MODE"
echo "  run real matrix: $RUN_REAL_MATRIX"
echo "  run long-run: $RUN_LONGRUN"

echo ""
echo "[1/5] Shadow policy/planner unit tests"
mvn -q -pl engine-impl-common -am test \
  -Dtest=ShadowAtlasPlannerTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo ""
echo "[2/5] OpenGL/Vulkan shadow lifecycle + mapper tests"
mvn -q -pl engine-impl-opengl -am test \
  -Dtest=OpenGlEngineRuntimeLifecycleTest \
  -Dsurefire.failIfNoSpecifiedTests=false
mvn -q -pl engine-impl-vulkan -am test \
  -Dtest=VulkanEngineRuntimeLightingMapperTest,VulkanEngineRuntimeIntegrationTest#spotShadowRequestDoesNotEmitShadowTypeUnsupportedWarning+pointShadowRequestDoesNotEmitShadowTypeUnsupportedWarning \
  -Dsurefire.failIfNoSpecifiedTests=false

echo ""
echo "[3/5] Shadow compare harness gates (mock/real mode-driven)"
for test_case in \
  "BackendParityIntegrationTest#compareHarnessShadowCascadeStressHasBoundedDiff" \
  "BackendParityIntegrationTest#compareHarnessFogSmokeShadowPostStressHasBoundedDiff" \
  "BackendParityIntegrationTest#compareHarnessSmokeShadowStressHasBoundedDiff"; do
  out_dir="$OUT_ROOT/$(echo "$test_case" | tr '#:' '--' | tr -cs 'a-zA-Z0-9._-' '-')"
  DLE_COMPARE_OUTPUT_DIR="$out_dir" \
  DLE_COMPARE_TEST_CLASS="$test_case" \
  DLE_COMPARE_VULKAN_MODE="$VULKAN_MODE" \
  ./scripts/aa_rebaseline_real_mac.sh run
done

echo ""
echo "[4/5] Vulkan shadow depth-format divergence guard (d16/d32)"
for depth_format in d16 d32; do
  mvn -q -pl engine-impl-vulkan -am test \
    -Ddle.vulkan.shadow.depthFormat="$depth_format" \
    -Dtest=VulkanEngineRuntimeIntegrationTest#spotShadowRequestDoesNotEmitShadowTypeUnsupportedWarning+pointShadowRequestDoesNotEmitShadowTypeUnsupportedWarning \
    -Dsurefire.failIfNoSpecifiedTests=false
done

if [[ "$RUN_REAL_MATRIX" == "1" ]]; then
  echo ""
  echo "[5/5] Real Vulkan targeted shadow matrix"
  for depth_format in d16 d32; do
    out_dir="$OUT_ROOT/real-$depth_format"
    DLE_COMPARE_OUTPUT_DIR="$out_dir" \
    DLE_COMPARE_TEST_CLASS="BackendParityIntegrationTest#compareHarnessShadowCascadeStressHasBoundedDiff" \
    DLE_COMPARE_VULKAN_MODE="real" \
    DLE_COMPARE_VULKAN_SHADOW_DEPTH_FORMAT="$depth_format" \
    ./scripts/aa_rebaseline_real_mac.sh run
  done
fi

if [[ "$RUN_LONGRUN" == "1" ]]; then
  echo ""
  echo "Running shadow-focused long-run sweep"
  DLE_COMPARE_LONGRUN_MOTION_RUNS="${DLE_COMPARE_LONGRUN_MOTION_RUNS:-3}" \
  DLE_COMPARE_TEST_CLASS="BackendParityIntegrationTest#compareHarnessAnimatedMotionTargetedScenesStayBounded" \
  DLE_COMPARE_OUTPUT_DIR="$OUT_ROOT/longrun-motion" \
  DLE_COMPARE_VULKAN_MODE="$VULKAN_MODE" \
  ./scripts/aa_rebaseline_real_mac.sh longrun-motion
fi

echo ""
echo "Shadow CI matrix complete."
echo "Artifacts: $OUT_ROOT"
