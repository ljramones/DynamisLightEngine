#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

source "$ROOT_DIR/scripts/check_java25.sh"
enforce_java25_for_maven

OUT_ROOT="${DLE_SHADOW_STABLE_PROMOTE_OUTPUT_ROOT:-artifacts/compare/shadow-stable-promote-$(date +%Y%m%d-%H%M%S)}"
RUNS_PER_PASS="${DLE_SHADOW_STABLE_PROMOTE_RUNS_PER_PASS:-3}"
MIN_RUNS="${DLE_SHADOW_STABLE_PROMOTE_MIN_RUNS:-$RUNS_PER_PASS}"
PROFILE_SET="${DLE_SHADOW_STABLE_PROMOTE_PROFILE_SET:-production}"

PASS1="$OUT_ROOT/pass-1"
PASS2="$OUT_ROOT/pass-2"
REC1="$PASS1/threshold-lock/recommended-thresholds.properties"
REC2="$PASS2/threshold-lock/recommended-thresholds.properties"

echo "Shadow stable threshold promotion (real Vulkan)"
echo "  output root: $OUT_ROOT"
echo "  runs/pass: $RUNS_PER_PASS"
echo "  min runs: $MIN_RUNS"
echo "  profile set: $PROFILE_SET"

run_pass() {
  local pass_out="$1"
  DLE_SHADOW_FINALIZE_OUTPUT_ROOT="$pass_out" \
  DLE_SHADOW_FINALIZE_RUNS="$RUNS_PER_PASS" \
  DLE_SHADOW_FINALIZE_MIN_RUNS="$MIN_RUNS" \
  DLE_SHADOW_FINALIZE_PROFILE_SET="$PROFILE_SET" \
  DLE_SHADOW_FINALIZE_PROMOTE_MODE="none" \
  "$ROOT_DIR/scripts/shadow_quality_finalize_real.sh"
}

run_pass "$PASS1"
run_pass "$PASS2"

if [[ ! -f "$REC1" || ! -f "$REC2" ]]; then
  echo "Missing recommendation files:"
  echo "  $REC1"
  echo "  $REC2"
  exit 1
fi

if cmp -s "$REC1" "$REC2"; then
  echo "Stable recommendation detected across both passes; promoting to real profile."
  "$ROOT_DIR/scripts/promote_compare_thresholds.sh" "$REC2" real
  echo "Promotion complete."
else
  echo "Recommendations differ across passes; skipping promotion."
  echo "Diff preview:"
  diff -u "$REC1" "$REC2" || true
  exit 2
fi

