#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_ROOT="${DLE_RT_REFLECTIONS_PROMOTION_OUTPUT_ROOT:-artifacts/compare/rt-reflections-promotion-bundle}"
OUTPUT_ROOT_ABS="${ROOT_DIR}/${OUTPUT_ROOT}"
LOG_DIR="${OUTPUT_ROOT_ABS}/logs"
mkdir -p "${LOG_DIR}"

run_step() {
  local name="$1"
  shift
  local log_file="${LOG_DIR}/${name}.log"
  echo "[rt-reflections-promotion] ${name}"
  echo "[rt-reflections-promotion] command: $*"
  "$@" | tee "${log_file}"
}

run_step "lockdown" \
  "${ROOT_DIR}/scripts/rt_reflections_ci_lockdown_full.sh"

run_step "real-signoff" \
  "${ROOT_DIR}/scripts/rt_reflections_real_gpu_signoff.sh"

run_step "real-longrun" \
  "${ROOT_DIR}/scripts/rt_reflections_real_longrun_signoff.sh"

cat > "${OUTPUT_ROOT_ABS}/summary.txt" <<EOF
RT reflections promotion bundle completed successfully.

Output root: ${OUTPUT_ROOT}
Logs:
  - ${LOG_DIR}/lockdown.log
  - ${LOG_DIR}/real-signoff.log
  - ${LOG_DIR}/real-longrun.log
EOF

echo "[rt-reflections-promotion] complete: ${OUTPUT_ROOT}"
