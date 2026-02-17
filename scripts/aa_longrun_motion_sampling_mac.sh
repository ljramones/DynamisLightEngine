#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

RUNS="${DLE_COMPARE_LONGRUN_MOTION_RUNS:-4}"
SLEEP_SEC="${DLE_COMPARE_LONGRUN_MOTION_SLEEP_SEC:-0}"
OUT_ROOT="${DLE_COMPARE_LONGRUN_MOTION_OUTPUT_ROOT:-artifacts/compare/aa-motion-longrun-$(date +%Y%m%d-%H%M%S)}"
VULKAN_MODE="${DLE_COMPARE_VULKAN_MODE:-real}"
TEMPORAL_FRAMES="${DLE_COMPARE_TEMPORAL_FRAMES:-10}"
TEMPORAL_WINDOW="${DLE_COMPARE_TEMPORAL_WINDOW:-10}"
TSR_FRAME_BOOST="${DLE_COMPARE_TSR_FRAME_BOOST:-6}"

mkdir -p "$OUT_ROOT"

TESTS=(
  "BackendParityIntegrationTest#compareHarnessAnimatedMotionTargetedScenesStayBounded"
  "BackendParityIntegrationTest#compareHarnessTsrUpscalerHooksAcrossTargetedScenesStayBounded"
)

echo "AA motion long-run sampling"
echo "  runs: $RUNS"
echo "  output root: $OUT_ROOT"
echo "  vulkan mode: $VULKAN_MODE"
echo "  temporal frames/window: $TEMPORAL_FRAMES/$TEMPORAL_WINDOW"
echo "  tsr frame boost: $TSR_FRAME_BOOST"

for ((run=1; run<=RUNS; run++)); do
  for test_class in "${TESTS[@]}"; do
    test_slug="$(echo "$test_class" | tr '#:' '--' | tr -cs 'a-zA-Z0-9._-' '-')"
    run_out="$OUT_ROOT/run-$(printf '%02d' "$run")-$test_slug"
    echo ""
    echo "[$(date +%H:%M:%S)] run $run/$RUNS :: $test_class"
    DLE_COMPARE_OUTPUT_DIR="$run_out" \
    DLE_COMPARE_TEST_CLASS="$test_class" \
    DLE_COMPARE_VULKAN_MODE="$VULKAN_MODE" \
    DLE_COMPARE_TEMPORAL_FRAMES="$TEMPORAL_FRAMES" \
    DLE_COMPARE_TEMPORAL_WINDOW="$TEMPORAL_WINDOW" \
    DLE_COMPARE_TSR_FRAME_BOOST="$TSR_FRAME_BOOST" \
    "$ROOT_DIR/scripts/aa_rebaseline_real_mac.sh"
  done
  if [[ "$SLEEP_SEC" -gt 0 && "$run" -lt "$RUNS" ]]; then
    sleep "$SLEEP_SEC"
  fi
done

echo ""
echo "Locking thresholds from motion long-run samples..."
DLE_COMPARE_THRESHOLD_LOCK_MIN_RUNS="${DLE_COMPARE_THRESHOLD_LOCK_MIN_RUNS:-3}" \
  "$ROOT_DIR/scripts/aa_lock_thresholds.sh" "$OUT_ROOT"

echo "AA motion long-run sampling complete."
echo "Artifacts: $OUT_ROOT"
