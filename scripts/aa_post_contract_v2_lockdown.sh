#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

source "$ROOT_DIR/scripts/check_java25.sh"
enforce_java25_for_maven

echo "AA/Post contract v2 lockdown"
echo "  - v2 descriptor completeness (AA + Post)"
echo "  - cross-capability validation with shadow + reflection"
echo "  - contract validator/ci-gate regression checks"

mvn -q -pl engine-spi,engine-impl-vulkan -am test \
  -Dtest=RenderCapabilityContractV2ValidatorTest,RenderCapabilityContractV2CiGateTest,VulkanCapabilityContractV2DescriptorsTest,VulkanAaPostCapabilityPlanIntegrationTest,VulkanAaTemporalWarningEmitterTest,VulkanAaTemporalMaterialWarningEmitterTest,VulkanAaUpscaleWarningEmitterTest,VulkanPostCompositePassRecorderTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo "AA/Post contract v2 lockdown complete."
