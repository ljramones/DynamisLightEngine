#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

source "${ROOT_DIR}/scripts/check_java25.sh"
enforce_java25_for_maven

echo "GI Phase 2 full lockdown"

bash "${ROOT_DIR}/scripts/gi_phase2_ssgi_lockdown.sh"
bash "${ROOT_DIR}/scripts/gi_phase2_probe_lockdown.sh"
bash "${ROOT_DIR}/scripts/gi_phase2_rt_lockdown.sh"

echo "GI Phase 2 full lockdown complete."
