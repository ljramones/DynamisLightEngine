#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

source "$ROOT_DIR/scripts/check_java25.sh"
enforce_java25_for_maven

echo "Shadow Phase C lockdown"
echo "  - cache policy/churn gates"
echo "  - RT denoise/perf envelope gates"
echo "  - hybrid composition envelope gates"
echo "  - blessed-tier stability checks"

mvn -q -pl engine-impl-vulkan -am test \
  -Dtest=VulkanShadowCapabilityWarningIntegrationTest#shadowCacheBreachGateTriggersWithAggressiveThresholds+shadowRtDenoiseEnvelopeBreachGateTriggersWithAggressiveThresholds+shadowHybridCompositionBreachGateTriggersWithAggressiveThresholds+cacheEnvelopeStaysStableAcrossBlessedTiers+rtEnvelopeStaysStableAcrossBlessedTiers+hybridEnvelopeStaysStableAcrossBlessedTiers \
  -Dsurefire.failIfNoSpecifiedTests=false

echo "Shadow Phase C lockdown complete."
