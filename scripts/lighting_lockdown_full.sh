#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

source "${ROOT_DIR}/scripts/check_java25.sh"
enforce_java25_for_maven

echo "Lighting lockdown full sequence"

bash "${ROOT_DIR}/scripts/lighting_contract_v2_lockdown.sh"
bash "${ROOT_DIR}/scripts/lighting_phase2_lockdown.sh"
bash "${ROOT_DIR}/scripts/lighting_advanced_lockdown.sh"

echo "Lighting lockdown full sequence complete."
