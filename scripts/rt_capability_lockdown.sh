#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

echo "[rt-capability-lockdown] Running RT capability integration lockdown..."
mvn -pl engine-impl-vulkan -am \
  -Dtest=VulkanRtCapabilityPromotionIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test

echo "[rt-capability-lockdown] PASS"
