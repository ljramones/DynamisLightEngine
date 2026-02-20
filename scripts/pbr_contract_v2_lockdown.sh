#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

source "$ROOT_DIR/scripts/check_java25.sh"
enforce_java25_for_maven

echo "PBR contract v2 lockdown"
echo "  - descriptor completeness and cross-capability validation"
echo "  - planner regression coverage"
echo "  - runtime warning/typed diagnostics integration"

mvn -q -pl engine-api,engine-impl-common,engine-spi,engine-impl-vulkan -am test \
  -Dtest=RenderCapabilityContractV2ValidatorTest,RenderCapabilityContractV2CiGateTest,VulkanCapabilityContractV2DescriptorsTest,VulkanPbrCapabilityPlannerTest,VulkanPbrCapabilityPlanIntegrationTest,VulkanPipelineProfileResolverTest,VulkanPipelineProfileCompilerTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo "PBR contract v2 lockdown complete."
