#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

source "$ROOT_DIR/scripts/check_java25.sh"
enforce_java25_for_maven

echo "Post Phase A core lockdown"
echo "  - core post policy/envelope/promotion diagnostics"
echo "  - post module contract compatibility"

mvn -q -pl engine-api,engine-impl-common,engine-spi,engine-impl-vulkan -am test \
  -Dtest=RenderCapabilityContractV2ValidatorTest,RenderCapabilityContractV2CiGateTest,VulkanCapabilityContractV2DescriptorsTest,VulkanAaPostCapabilityPlanIntegrationTest,VulkanPostCorePromotionIntegrationTest,VulkanPostCompositePassRecorderTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo "Post Phase A core lockdown complete."
