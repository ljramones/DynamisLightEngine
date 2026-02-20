#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

echo "[vfx-phase1-lockdown] Running VFX/particles phase-1 integration lockdown..."
mvn -pl engine-impl-vulkan -am \
  -Dtest=VulkanVfxCapabilityPromotionIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test

echo "[vfx-phase1-lockdown] PASS"
