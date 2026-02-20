#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

echo "[rt-lockdown-full] Running RT reflections lockdown..."
bash "${ROOT_DIR}/scripts/rt_reflections_ci_lockdown_full.sh"

echo "[rt-lockdown-full] Running GI RT-detail + RT-multi lockdown..."
bash "${ROOT_DIR}/scripts/gi_phase2_rt_lockdown.sh"
bash "${ROOT_DIR}/scripts/gi_phase2_rt_multi_lockdown.sh"

echo "[rt-lockdown-full] Running RT cross-cut lockdown..."
bash "${ROOT_DIR}/scripts/rt_crosscut_lockdown.sh"

echo "[rt-lockdown-full] PASS (reflections + GI RT lanes + cross-cut gate)"
