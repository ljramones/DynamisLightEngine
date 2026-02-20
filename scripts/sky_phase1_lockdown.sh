#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

echo "[sky-phase1-lockdown] Running sky/atmosphere phase-1 integration lockdown..."
mvn -pl engine-impl-vulkan -am \
  -Dtest=VulkanSkyCapabilityPromotionIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test

echo "[sky-phase1-lockdown] PASS"
