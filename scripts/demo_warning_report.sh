#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

INPUT="${1:-}"
if [[ -z "$INPUT" ]]; then
  INPUT="$(
    {
      ls -1t artifacts/demos/*.jsonl 2>/dev/null
      ls -1t engine-demos/artifacts/demos/*.jsonl 2>/dev/null
    } | head -n 1 || true
  )"
fi

if [[ -n "$INPUT" && ! -f "$INPUT" && "$INPUT" == artifacts/demos/* && -f "engine-demos/$INPUT" ]]; then
  INPUT="engine-demos/$INPUT"
fi

if [[ -n "$INPUT" && ! -f "$INPUT" && "$INPUT" == engine-demos/artifacts/demos/* && -f "${INPUT#engine-demos/}" ]]; then
  INPUT="${INPUT#engine-demos/}"
fi

if [[ -z "$INPUT" || ! -f "$INPUT" ]]; then
  echo "Telemetry jsonl file not found: ${INPUT:-<none>}" >&2
  echo "Pass one explicitly, for example:" >&2
  echo "  ./scripts/demo_warning_report.sh artifacts/demos/<file>.jsonl" >&2
  echo >&2
  echo "Recent telemetry files:" >&2
  {
    ls -1t artifacts/demos/*.jsonl 2>/dev/null
    ls -1t engine-demos/artifacts/demos/*.jsonl 2>/dev/null
  } | head -n 10 >&2 || true
  exit 1
fi

echo "Telemetry file: $INPUT"

if command -v jq >/dev/null 2>&1; then
  echo "Frames with warnings by warning code:"
  jq -r '
    select(.type=="frame")
    | .warningCodes
    | select(length > 0)
    | split(",")[]
    | select(length > 0)
  ' "$INPUT" | sort | uniq -c | sort -nr
  echo
  echo "Distinct warnings per frame count:"
  jq -r '
    select(.type=="frame")
    | .warningCount
  ' "$INPUT" | sort -n | uniq -c
else
  echo "jq not found; install jq for detailed warning breakdown."
  echo "Fallback frame warningCount histogram:"
  awk -F'"warningCount":' '
    /"type":"frame"/ && NF>1 {
      split($2, a, ",");
      c[a[1]]++;
    }
    END {
      for (k in c) printf "%7d %s\n", c[k], k;
    }
  ' "$INPUT" | sort -n
fi
