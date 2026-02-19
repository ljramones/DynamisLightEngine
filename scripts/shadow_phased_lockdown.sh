#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

source "$ROOT_DIR/scripts/check_java25.sh"
enforce_java25_for_maven

echo "Shadow Phase D lockdown"
echo "  - transparent receiver policy/cooldown stability"
echo "  - area + distance-field required-path breach coverage"
echo "  - area + distance policy stability matrix"
echo "  - consolidated phase-d promotion gate stability"

mvn -q -pl engine-api,engine-impl-vulkan -am test \
  -Dtest=EngineApiContractTest,VulkanShadowCapabilityWarningIntegrationTest#shadowTransparentReceiverEnvelopeBreachGateTriggersWithAggressiveThresholds+transparentReceiverBreachCooldownPreventsContinuousReemit+transparentReceiverPolicyStaysStableAcrossBlessedTiers+shadowExtendedModeRequiredBreachesTriggerForUnavailableAreaAndDistanceField+areaAndDistancePoliciesStayStableAcrossBlessedTiers+shadowPhaseDPromotionReadyWarningEmitsWhenPhaseDStabilityContractsHold+phaseDPromotionReadinessRequiresSustainedWindowAcrossBlessedTiers \
  -Dsurefire.failIfNoSpecifiedTests=false

echo "Shadow Phase D lockdown complete."
