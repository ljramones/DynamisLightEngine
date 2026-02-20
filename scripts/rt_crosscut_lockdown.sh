#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

echo "[rt-crosscut-lockdown] Running RT cross-cut integration lockdown..."
mvn -pl engine-impl-vulkan -am \
  -Dtest=VulkanRtCrossCutPromotionIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test

echo "[rt-crosscut-lockdown] PASS"
