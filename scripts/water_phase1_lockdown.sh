#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

echo "[water-phase1-lockdown] Running water/ocean phase-1 integration lockdown..."
mvn -pl engine-impl-vulkan -am \
  -Dtest=VulkanWaterCapabilityPromotionIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test

echo "[water-phase1-lockdown] PASS"
