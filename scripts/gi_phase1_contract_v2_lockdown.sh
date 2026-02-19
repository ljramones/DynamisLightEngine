#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

source "${ROOT_DIR}/scripts/check_java25.sh"
enforce_java25_for_maven

echo "GI Phase 1 contract v2 lockdown"

mvn -q -pl engine-spi,engine-impl-vulkan -am \
  -Dtest=RenderCapabilityContractV2ValidatorTest,RenderCapabilityContractV2CiGateTest,VulkanCapabilityContractV2DescriptorsTest,VulkanGiCapabilityPlannerTest,VulkanGiCapabilityPlanIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test

echo "GI Phase 1 contract v2 lockdown complete."
