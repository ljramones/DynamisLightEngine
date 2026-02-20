#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

echo "[sky-contract-v2-lockdown] Running sky capability v2 contract + planner lockdown..."
mvn -pl engine-impl-vulkan -am \
  -Dtest=VulkanSkyCapabilityPromotionIntegrationTest,VulkanSkyCapabilityPlannerTest,VulkanCapabilityContractV2DescriptorsTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test

echo "[sky-contract-v2-lockdown] PASS"
