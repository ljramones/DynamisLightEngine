#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

source "$ROOT_DIR/scripts/check_java25.sh"
enforce_java25_for_maven

RUNS="${DLE_SHADOW_REAL_LONGRUN_RUNS:-3}"
OUT_ROOT="${DLE_SHADOW_REAL_LONGRUN_OUTPUT_ROOT:-artifacts/compare/shadow-real-longrun-$(date +%Y%m%d-%H%M%S)}"
LOCK_THRESHOLDS="${DLE_SHADOW_REAL_LONGRUN_LOCK_THRESHOLDS:-1}"
THRESHOLD_OUT="${DLE_SHADOW_REAL_LONGRUN_THRESHOLD_OUTPUT_DIR:-$OUT_ROOT/threshold-lock}"

mkdir -p "$OUT_ROOT"

if ! ./scripts/aa_rebaseline_real_mac.sh preflight >/dev/null 2>&1; then
  echo "Real Vulkan preflight unavailable on this host; skipping guarded shadow real long-run."
  echo "Output root: $OUT_ROOT"
  exit 0
fi

echo "Shadow real Vulkan long-run"
echo "  runs: $RUNS"
echo "  output root: $OUT_ROOT"

declare -a TESTS=(
  "BackendParityIntegrationTest#compareHarnessShadowCascadeStressHasBoundedDiff"
  "BackendParityIntegrationTest#compareHarnessFogSmokeShadowPostStressHasBoundedDiff"
  "BackendParityIntegrationTest#compareHarnessSmokeShadowStressHasBoundedDiff"
)

for run in $(seq 1 "$RUNS"); do
  for test_case in "${TESTS[@]}"; do
    out_dir="$OUT_ROOT/run-${run}/$(echo "$test_case" | tr '#:' '--' | tr -cs 'a-zA-Z0-9._-' '-')"
    DLE_COMPARE_OUTPUT_DIR="$out_dir" \
    DLE_COMPARE_TEST_CLASS="$test_case" \
    DLE_COMPARE_VULKAN_MODE="real" \
    ./scripts/aa_rebaseline_real_mac.sh run
  done
done

if [[ "$LOCK_THRESHOLDS" == "1" ]]; then
  "$ROOT_DIR/scripts/shadow_lock_thresholds_guarded.sh" "$OUT_ROOT" "$THRESHOLD_OUT"
fi

echo "Shadow real Vulkan long-run complete."
echo "Artifacts: $OUT_ROOT"
