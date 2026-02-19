#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

source "$ROOT_DIR/scripts/check_java25.sh"
enforce_java25_for_maven

echo "Shadow Phase A promotion lockdown"
echo "  - cadence promotion gate"
echo "  - point-face budget promotion gate"
echo "  - spot-projected promotion gate"
echo "  - consolidated phase-a promotion gate"

mvn -q -pl engine-api,engine-impl-vulkan -am test \
  -Dtest=EngineApiContractTest,VulkanShadowCapabilityWarningIntegrationTest#shadowCadencePromotionReadyWarningEmitsAfterStableWindow+shadowPointBudgetPromotionReadyWarningEmitsAfterStableWindow+shadowSpotProjectedPromotionReadyWarningEmitsAfterStableWindow+shadowPhaseAPromotionReadyWarningEmitsWhenCadencePointAndSpotAreReady+phaseAPromotionReadinessRequiresSustainedWindowAcrossBlessedTiers \
  -Dsurefire.failIfNoSpecifiedTests=false

echo "Shadow Phase A promotion lockdown complete."
