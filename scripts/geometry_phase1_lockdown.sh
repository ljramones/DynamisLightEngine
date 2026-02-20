#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

echo "[geometry-phase1-lockdown] Running geometry/detail phase-1 integration lockdown..."
mvn -pl engine-impl-vulkan -am \
  -Dtest=VulkanGeometryCapabilityPromotionIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test

echo "[geometry-phase1-lockdown] PASS"
