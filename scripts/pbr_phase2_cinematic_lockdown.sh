#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

source "$ROOT_DIR/scripts/check_java25.sh"
enforce_java25_for_maven

echo "PBR Phase-2 cinematic lockdown"
echo "  - cinematic mode/planner coverage"
echo "  - surface-optics envelope + promotion gates"

mvn -q -pl engine-api,engine-impl-common,engine-spi,engine-impl-vulkan -am test \
  -Dtest=VulkanPbrCapabilityPlannerTest,VulkanPbrCapabilityPlanIntegrationTest,VulkanCapabilityContractV2DescriptorsTest,VulkanPipelineProfileResolverTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo "PBR Phase-2 cinematic lockdown complete."
