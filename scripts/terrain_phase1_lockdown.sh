#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

echo "[terrain-phase1-lockdown] Running terrain phase-1 integration lockdown..."
mvn -pl engine-impl-vulkan -am \
  -Dtest=VulkanTerrainCapabilityPromotionIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test

echo "[terrain-phase1-lockdown] PASS"
