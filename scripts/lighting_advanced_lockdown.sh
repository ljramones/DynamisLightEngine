#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

source "${ROOT_DIR}/scripts/check_java25.sh"
enforce_java25_for_maven

echo "Lighting advanced lockdown"

mvn -q -pl engine-impl-vulkan -am \
  -Dtest=VulkanLightingCapabilityPlanIntegrationTest,VulkanLightingCapabilityPlannerTest,VulkanCapabilityContractV2DescriptorsTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test

echo "Lighting advanced lockdown complete."
