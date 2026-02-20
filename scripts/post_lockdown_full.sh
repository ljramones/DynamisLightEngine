#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

source "$ROOT_DIR/scripts/check_java25.sh"
enforce_java25_for_maven

echo "Post full lockdown bundle"
echo "  - AA/Post contract v2 lockdown"
echo "  - Post Phase A core lockdown"
echo "  - Post Phase B cinematic lockdown"

bash "$ROOT_DIR/scripts/aa_post_contract_v2_lockdown.sh"
bash "$ROOT_DIR/scripts/post_phaseA_core_lockdown.sh"
bash "$ROOT_DIR/scripts/post_phaseB_cinematic_lockdown.sh"

echo "Post full lockdown bundle complete."
