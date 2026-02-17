#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "This script is intended for macOS (Darwin)." >&2
  exit 1
fi

RUNS="${DLE_COMPARE_LONGRUN_RUNS:-5}"
SLEEP_SEC="${DLE_COMPARE_LONGRUN_SLEEP_SEC:-0}"
BASE_DIR="${DLE_COMPARE_LONGRUN_OUTPUT_ROOT:-artifacts/compare/aa-longrun-$(date +%Y%m%d-%H%M%S)}"
LOCK_OUT_DIR="${DLE_COMPARE_LONGRUN_LOCK_DIR:-$BASE_DIR/threshold-lock}"
TEST_CLASS="${DLE_COMPARE_TEST_CLASS:-BackendParityIntegrationTest}"

if ! [[ "$RUNS" =~ ^[0-9]+$ ]] || [[ "$RUNS" -lt 1 ]]; then
  echo "DLE_COMPARE_LONGRUN_RUNS must be a positive integer." >&2
  exit 1
fi

echo "AA long-run real Vulkan sampling"
echo "Runs: $RUNS"
echo "Base output: $BASE_DIR"
echo "Threshold lock output: $LOCK_OUT_DIR"
echo "Test class: $TEST_CLASS"

for i in $(seq 1 "$RUNS"); do
  run_id="$(printf '%02d' "$i")"
  run_out="$BASE_DIR/run-$run_id"
  echo ""
  echo "=== Long-run sample $run_id / $RUNS ==="
  DLE_COMPARE_OUTPUT_DIR="$run_out" \
  DLE_COMPARE_TEST_CLASS="$TEST_CLASS" \
  DLE_COMPARE_VULKAN_MODE=real \
  ./scripts/aa_rebaseline_real_mac.sh run
  if [[ "$SLEEP_SEC" != "0" ]]; then
    sleep "$SLEEP_SEC"
  fi
done

echo ""
echo "=== Locking thresholds from long-run samples ==="
DLE_COMPARE_THRESHOLD_LOCK_MIN_RUNS="${DLE_COMPARE_THRESHOLD_LOCK_MIN_RUNS:-3}" \
./scripts/aa_lock_thresholds.sh "$BASE_DIR" "$LOCK_OUT_DIR"

echo ""
echo "Long-run sampling complete."
echo "Samples: $BASE_DIR"
echo "Threshold report: $LOCK_OUT_DIR/threshold-lock-report.tsv"
echo "Recommended thresholds: $LOCK_OUT_DIR/recommended-thresholds.properties"
