#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

source "$ROOT_DIR/scripts/check_java25.sh"
enforce_java25_for_maven

SOURCE_DIR="${1:-${DLE_SHADOW_THRESHOLD_SOURCE_DIR:-artifacts/compare/shadow-real-longrun}}"
OUT_DIR="${2:-${DLE_SHADOW_THRESHOLD_OUTPUT_DIR:-artifacts/compare/shadow-threshold-lock}}"
MIN_RUNS="${DLE_COMPARE_THRESHOLD_LOCK_MIN_RUNS:-2}"

if [[ ! -d "$SOURCE_DIR" ]]; then
  echo "Shadow threshold lock source directory not found; skipping."
  echo "  source: $SOURCE_DIR"
  exit 0
fi

if ! find "$SOURCE_DIR" -type f -name 'compare-metadata.properties' -print -quit | grep -q .; then
  echo "No compare metadata under shadow threshold source; skipping."
  echo "  source: $SOURCE_DIR"
  exit 0
fi

mkdir -p "$OUT_DIR"

echo "Shadow threshold lock"
echo "  source: $SOURCE_DIR"
echo "  out: $OUT_DIR"
echo "  min runs: $MIN_RUNS"

DLE_COMPARE_THRESHOLD_LOCK_MIN_RUNS="$MIN_RUNS" \
  "$ROOT_DIR/scripts/aa_lock_thresholds.sh" "$SOURCE_DIR" "$OUT_DIR"

echo "Shadow threshold lock complete."
echo "  report: $OUT_DIR/threshold-lock-report.tsv"
echo "  recommended: $OUT_DIR/recommended-thresholds.properties"
