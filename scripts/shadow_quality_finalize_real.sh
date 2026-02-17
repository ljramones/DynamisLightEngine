#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

source "$ROOT_DIR/scripts/check_java25.sh"
enforce_java25_for_maven

RUNS="${DLE_SHADOW_FINALIZE_RUNS:-5}"
OUT_ROOT="${DLE_SHADOW_FINALIZE_OUTPUT_ROOT:-artifacts/compare/shadow-finalize-$(date +%Y%m%d-%H%M%S)}"
PROFILE_SET="${DLE_SHADOW_FINALIZE_PROFILE_SET:-production}"
PROMOTE_MODE="${DLE_SHADOW_FINALIZE_PROMOTE_MODE:-real}" # real|mock|none
STRICT_BVH="${DLE_SHADOW_FINALIZE_RT_BVH_STRICT:-1}"
MIN_RUNS="${DLE_SHADOW_FINALIZE_MIN_RUNS:-$RUNS}"

echo "Shadow quality finalize (real Vulkan)"
echo "  runs: $RUNS"
echo "  profile set: $PROFILE_SET"
echo "  strict BVH: $STRICT_BVH"
echo "  promote mode: $PROMOTE_MODE"
echo "  output root: $OUT_ROOT"

DLE_SHADOW_PROD_SWEEP_RUNS="$RUNS" \
DLE_SHADOW_PROD_SWEEP_VULKAN_MODE=real \
DLE_SHADOW_PROD_SWEEP_OUTPUT_ROOT="$OUT_ROOT" \
DLE_SHADOW_PROD_SWEEP_PROFILE_SET="$PROFILE_SET" \
DLE_SHADOW_PROD_SWEEP_LOCK_THRESHOLDS=1 \
DLE_SHADOW_PROD_SWEEP_RT_BVH_STRICT="$STRICT_BVH" \
DLE_SHADOW_PROD_SWEEP_PROMOTE_MODE="$PROMOTE_MODE" \
DLE_COMPARE_THRESHOLD_LOCK_MIN_RUNS="$MIN_RUNS" \
"$ROOT_DIR/scripts/shadow_production_quality_sweeps.sh"

echo "Shadow quality finalize complete."
echo "Artifacts: $OUT_ROOT"

