#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

SOURCE_FILE="${1:-}"
MODE="${2:-real}"

if [[ -z "$SOURCE_FILE" ]]; then
  echo "Usage: $0 <recommended-thresholds.properties> [real|mock]" >&2
  exit 1
fi
if [[ ! -f "$SOURCE_FILE" ]]; then
  echo "Source thresholds file not found: $SOURCE_FILE" >&2
  exit 1
fi

case "${MODE,,}" in
  real)
    TARGET_FILE="engine-host-sample/src/test/resources/thresholds/vulkan-real.properties"
    MODE_LABEL="strict real-Vulkan"
    ;;
  mock)
    TARGET_FILE="engine-host-sample/src/test/resources/thresholds/vulkan-mock.properties"
    MODE_LABEL="fallback mock-Vulkan"
    ;;
  *)
    echo "Invalid mode '$MODE' (expected real|mock)" >&2
    exit 1
    ;;
esac

mkdir -p "$(dirname "$TARGET_FILE")"
TMP_FILE="$(mktemp -t dle-threshold-promote.XXXXXX)"

{
  echo "# Promoted compare threshold overrides (${MODE_LABEL})."
  echo "# Source: $SOURCE_FILE"
  echo "# Promoted at: $(date -u +'%Y-%m-%dT%H:%M:%SZ')"
  echo "# Format: threshold.<profile>=<decimal>"
  awk '/^threshold\./ {print $0}' "$SOURCE_FILE" | LC_ALL=C sort
} > "$TMP_FILE"

mv "$TMP_FILE" "$TARGET_FILE"

echo "Promoted compare thresholds"
echo "  mode: $MODE"
echo "  target: $TARGET_FILE"
