#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

source "${ROOT_DIR}/scripts/check_java25.sh"
enforce_java25_for_maven

echo "GI In promotion bundle (Vulkan scope)"

bash "${ROOT_DIR}/scripts/gi_phase1_contract_v2_lockdown.sh"
bash "${ROOT_DIR}/scripts/gi_phase2_lockdown_full.sh"

echo "GI In promotion bundle complete."
