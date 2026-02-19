#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

MAX_FILE_LINES="${MAX_FILE_LINES:-1500}"
REPORT_THRESHOLD="${REPORT_THRESHOLD:-1000}"
MAX_VULKAN_ROOT_CLASSES="${MAX_VULKAN_ROOT_CLASSES:-8}"
SCOPE="${SCOPE:-vulkan}"
VULKAN_ROOT_PACKAGE_DIR="engine-impl-vulkan/src/main/java/org/dynamislight/impl/vulkan"

echo "[guardrails] Java structure guardrails"
echo "[guardrails] scope=${SCOPE} max_file_lines=${MAX_FILE_LINES} report_threshold=${REPORT_THRESHOLD} max_vulkan_root_classes=${MAX_VULKAN_ROOT_CLASSES}"

if [ ! -d "${VULKAN_ROOT_PACKAGE_DIR}" ]; then
  echo "[guardrails] ERROR: missing package directory: ${VULKAN_ROOT_PACKAGE_DIR}" >&2
  exit 1
fi

violations=0

if [ "${SCOPE}" = "all" ]; then
  SEARCH_ROOTS=(.)
else
  SEARCH_ROOTS=(engine-impl-vulkan)
fi

echo "[guardrails] Checking class line limits..."
while IFS= read -r -d '' file; do
  lines="$(wc -l < "${file}")"
  if [ "${lines}" -gt "${MAX_FILE_LINES}" ]; then
    echo "[guardrails] ERROR: ${file} has ${lines} lines (limit=${MAX_FILE_LINES})" >&2
    violations=$((violations + 1))
  fi
done < <(find "${SEARCH_ROOTS[@]}" -path '*/src/main/java/*.java' -print0)

echo "[guardrails] Reporting large classes (${REPORT_THRESHOLD}+ lines)..."
find "${SEARCH_ROOTS[@]}" -path '*/src/main/java/*.java' -print0 \
  | xargs -0 wc -l \
  | awk -v threshold="${REPORT_THRESHOLD}" '$1 >= threshold && $2 != "total" {print $1 " " $2}' \
  | sort -nr \
  || true

echo "[guardrails] Checking Vulkan root-package class count..."
vulkan_root_count="$(find "${VULKAN_ROOT_PACKAGE_DIR}" -maxdepth 1 -name '*.java' | wc -l | tr -d ' ')"
if [ "${vulkan_root_count}" -gt "${MAX_VULKAN_ROOT_CLASSES}" ]; then
  echo "[guardrails] ERROR: ${VULKAN_ROOT_PACKAGE_DIR} has ${vulkan_root_count} classes (limit=${MAX_VULKAN_ROOT_CLASSES})" >&2
  violations=$((violations + 1))
fi

echo "[guardrails] Vulkan root-package class count: ${vulkan_root_count}"

if [ "${violations}" -gt 0 ]; then
  echo "[guardrails] FAILED: ${violations} violation(s)." >&2
  exit 1
fi

echo "[guardrails] PASS"
