#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

OUT_ROOT="${DLE_SHADOW_LOCKDOWN_OUTPUT_ROOT:-artifacts/compare/shadow-lockdown-$(date +%Y%m%d-%H%M%S)}"
RUNS="${DLE_SHADOW_LOCKDOWN_RUNS:-3}"
LOCK_MIN_RUNS="${DLE_SHADOW_LOCKDOWN_MIN_RUNS:-$RUNS}"
PROMOTE_MODE="${DLE_SHADOW_LOCKDOWN_PROMOTE_MODE:-none}" # none|real|mock

echo "Shadow lockdown full run"
echo "  output root: $OUT_ROOT"
echo "  runs: $RUNS"
echo "  threshold min runs: $LOCK_MIN_RUNS"
echo "  promote mode: $PROMOTE_MODE"

mkdir -p "$OUT_ROOT"

echo "[1/3] Shadow CI matrix (real matrix + longrun)"
DLE_SHADOW_CI_OUTPUT_ROOT="$OUT_ROOT/shadow-ci" \
DLE_SHADOW_CI_REAL_MATRIX=1 \
DLE_SHADOW_CI_LONGRUN=1 \
DLE_COMPARE_THRESHOLD_LOCK_MIN_RUNS="$LOCK_MIN_RUNS" \
"$ROOT_DIR/scripts/shadow_ci_matrix.sh"

echo "[2/3] Guarded real-Vulkan shadow longrun"
DLE_SHADOW_REAL_LONGRUN_OUTPUT_ROOT="$OUT_ROOT/shadow-real-longrun" \
DLE_SHADOW_REAL_LONGRUN_RUNS="$RUNS" \
DLE_SHADOW_REAL_LONGRUN_LOCK_THRESHOLDS=1 \
DLE_COMPARE_THRESHOLD_LOCK_MIN_RUNS="$LOCK_MIN_RUNS" \
"$ROOT_DIR/scripts/shadow_real_longrun_guarded.sh"

echo "[3/3] Production quality sweeps (strict BVH)"
DLE_SHADOW_PROD_SWEEP_OUTPUT_ROOT="$OUT_ROOT/shadow-production-sweeps" \
DLE_SHADOW_PROD_SWEEP_RUNS="$RUNS" \
DLE_SHADOW_PROD_SWEEP_VULKAN_MODE=real \
DLE_SHADOW_PROD_SWEEP_PROFILE_SET=production \
DLE_SHADOW_PROD_SWEEP_LOCK_THRESHOLDS=1 \
DLE_SHADOW_PROD_SWEEP_RT_BVH_STRICT=1 \
DLE_COMPARE_THRESHOLD_LOCK_MIN_RUNS="$LOCK_MIN_RUNS" \
"$ROOT_DIR/scripts/shadow_production_quality_sweeps.sh"

if [[ "$PROMOTE_MODE" == "real" || "$PROMOTE_MODE" == "mock" ]]; then
  RECOMMENDED="$OUT_ROOT/shadow-production-sweeps/threshold-lock/recommended-thresholds.properties"
  if [[ -f "$RECOMMENDED" ]]; then
    echo "[promote] Promoting recommended thresholds ($PROMOTE_MODE) from: $RECOMMENDED"
    "$ROOT_DIR/scripts/promote_compare_thresholds.sh" "$RECOMMENDED" "$PROMOTE_MODE"
  else
    echo "[promote] No recommended thresholds found at: $RECOMMENDED (skipping)"
  fi
fi

echo "Shadow lockdown complete."
echo "Artifacts: $OUT_ROOT"
