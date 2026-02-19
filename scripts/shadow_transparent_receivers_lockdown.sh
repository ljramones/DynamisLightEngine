#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

source "$ROOT_DIR/scripts/check_java25.sh"
enforce_java25_for_maven

echo "Shadow transparent-receiver lockdown"
echo "  - policy + envelope breach gate"
echo "  - cooldown behavior under sustained breach"
echo "  - blessed-tier stability matrix"

mvn -q -pl engine-impl-vulkan -am test \
  -Dtest=VulkanShadowCapabilityWarningIntegrationTest#shadowTransparentReceiverEnvelopeBreachGateTriggersWithAggressiveThresholds+transparentReceiverBreachCooldownPreventsContinuousReemit+transparentReceiverPolicyStaysStableAcrossBlessedTiers \
  -Dsurefire.failIfNoSpecifiedTests=false

echo "Shadow transparent-receiver lockdown complete."
