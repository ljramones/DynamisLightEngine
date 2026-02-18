#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

source "$ROOT_DIR/scripts/check_java25.sh"
enforce_java25_for_maven

RUNS="${DLE_SHADOW_PROD_SWEEP_RUNS:-3}"
VULKAN_MODE="${DLE_SHADOW_PROD_SWEEP_VULKAN_MODE:-real}"
OUT_ROOT="${DLE_SHADOW_PROD_SWEEP_OUTPUT_ROOT:-artifacts/compare/shadow-production-sweeps-$(date +%Y%m%d-%H%M%S)}"
LOCK_THRESHOLDS="${DLE_SHADOW_PROD_SWEEP_LOCK_THRESHOLDS:-1}"
THRESHOLD_OUT="${DLE_SHADOW_PROD_SWEEP_THRESHOLD_OUTPUT_DIR:-$OUT_ROOT/threshold-lock}"
PROFILE_SET="${DLE_SHADOW_PROD_SWEEP_PROFILE_SET:-production}"
RT_BVH_STRICT="${DLE_SHADOW_PROD_SWEEP_RT_BVH_STRICT:-0}"
PROMOTE_MODE="${DLE_SHADOW_PROD_SWEEP_PROMOTE_MODE:-none}" # none|real|mock

mkdir -p "$OUT_ROOT"

if [[ "$VULKAN_MODE" == "real" ]]; then
  if ! ./scripts/aa_rebaseline_real_mac.sh preflight >/dev/null 2>&1; then
    echo "Real Vulkan preflight unavailable on this host; skipping production shadow sweeps."
    echo "Output root: $OUT_ROOT"
    exit 0
  fi
fi

echo "Shadow production quality sweeps"
echo "  runs: $RUNS"
echo "  vulkan mode: $VULKAN_MODE"
echo "  profile set: $PROFILE_SET"
echo "  rt bvh strict: $RT_BVH_STRICT"
echo "  promote mode: $PROMOTE_MODE"
echo "  output root: $OUT_ROOT"

declare -a TESTS=(
  "BackendParityIntegrationTest#compareHarnessShadowCascadeStressHasBoundedDiff"
  "BackendParityIntegrationTest#compareHarnessShadowSceneHasBoundedDiff"
  "BackendParityIntegrationTest#compareHarnessFogShadowStressHasBoundedDiff"
  "BackendParityIntegrationTest#compareHarnessFogSmokeShadowPostStressHasBoundedDiff"
  "BackendParityIntegrationTest#compareHarnessSmokeShadowStressHasBoundedDiff"
)

declare -a PROFILES
if [[ "$PROFILE_SET" == "production" ]]; then
  PROFILES=(
    "pcf_baseline::"
    "pcss_contact::-Dvulkan.shadow.filterPath=pcss -Dvulkan.shadow.pcssSoftness=1.25 -Dvulkan.shadow.contactShadows=true -Dvulkan.shadow.contactStrength=1.15 -Dvulkan.shadow.contactTemporalMotionScale=1.3 -Dvulkan.shadow.contactTemporalMinStability=0.60"
    "vsm_quality::-Dvulkan.shadow.filterPath=vsm -Dvulkan.shadow.momentBlend=1.15 -Dvulkan.shadow.momentBleedReduction=1.10"
    "evsm_quality::-Dvulkan.shadow.filterPath=evsm -Dvulkan.shadow.momentBlend=1.20 -Dvulkan.shadow.momentBleedReduction=1.20"
    "rt_optional_pcss::-Dvulkan.shadow.filterPath=pcss -Dvulkan.shadow.contactShadows=true -Dvulkan.shadow.rtMode=optional -Dvulkan.shadow.rtDenoiseStrength=0.78 -Dvulkan.shadow.rtRayLength=120 -Dvulkan.shadow.rtSampleCount=4"
    "rt_bvh_pcss::-Dvulkan.shadow.filterPath=pcss -Dvulkan.shadow.contactShadows=true -Dvulkan.shadow.rtMode=bvh -Dvulkan.shadow.rtDenoiseStrength=0.82 -Dvulkan.shadow.rtRayLength=140 -Dvulkan.shadow.rtSampleCount=6 -Dvulkan.shadow.rtBvhStrict=$RT_BVH_STRICT"
    "rt_bvh_dedicated_pcss::-Dvulkan.shadow.filterPath=pcss -Dvulkan.shadow.contactShadows=true -Dvulkan.shadow.rtMode=bvh_dedicated -Dvulkan.shadow.rtDenoiseStrength=0.82 -Dvulkan.shadow.rtRayLength=140 -Dvulkan.shadow.rtSampleCount=6 -Dvulkan.shadow.rtDedicatedDenoiseStrength=0.94 -Dvulkan.shadow.rtDedicatedRayLength=180 -Dvulkan.shadow.rtDedicatedSampleCount=10 -Dvulkan.shadow.rtBvhStrict=0"
    "rt_bvh_production_pcss::-Dvulkan.shadow.filterPath=pcss -Dvulkan.shadow.contactShadows=true -Dvulkan.shadow.rtMode=bvh_production -Dvulkan.shadow.rtDenoiseStrength=0.82 -Dvulkan.shadow.rtRayLength=140 -Dvulkan.shadow.rtSampleCount=6 -Dvulkan.shadow.rtProductionDenoiseStrength=0.97 -Dvulkan.shadow.rtProductionRayLength=240 -Dvulkan.shadow.rtProductionSampleCount=14 -Dvulkan.shadow.rtBvhStrict=$RT_BVH_STRICT"
    "rt_native_pcss::-Dvulkan.shadow.filterPath=pcss -Dvulkan.shadow.contactShadows=true -Dvulkan.shadow.rtMode=rt_native -Dvulkan.shadow.rtDenoiseStrength=0.86 -Dvulkan.shadow.rtRayLength=220 -Dvulkan.shadow.rtSampleCount=12 -Dvulkan.shadow.rtBvhStrict=0"
    "rt_native_denoised_pcss::-Dvulkan.shadow.filterPath=pcss -Dvulkan.shadow.contactShadows=true -Dvulkan.shadow.rtMode=rt_native_denoised -Dvulkan.shadow.rtDenoiseStrength=0.88 -Dvulkan.shadow.rtRayLength=240 -Dvulkan.shadow.rtSampleCount=12 -Dvulkan.shadow.rtDedicatedDenoiseStrength=0.98 -Dvulkan.shadow.rtDedicatedRayLength=260 -Dvulkan.shadow.rtDedicatedSampleCount=14 -Dvulkan.shadow.rtBvhStrict=0"
  )
else
  PROFILES=(
    "pcf_baseline::"
  )
fi

for run in $(seq 1 "$RUNS"); do
  for profile in "${PROFILES[@]}"; do
    mode="${profile%%::*}"
    extra_args="${profile#*::}"
    for test_case in "${TESTS[@]}"; do
      out_dir="$OUT_ROOT/run-${run}/${mode}/$(echo "$test_case" | tr '#:' '--' | tr -cs 'a-zA-Z0-9._-' '-')"
      DLE_COMPARE_OUTPUT_DIR="$out_dir" \
      DLE_COMPARE_TEST_CLASS="$test_case" \
      DLE_COMPARE_VULKAN_MODE="$VULKAN_MODE" \
      DLE_COMPARE_EXTRA_MVN_ARGS="$extra_args" \
      ./scripts/aa_rebaseline_real_mac.sh run
    done
  done
done

if [[ "$LOCK_THRESHOLDS" == "1" ]]; then
  "$ROOT_DIR/scripts/shadow_lock_thresholds_guarded.sh" "$OUT_ROOT" "$THRESHOLD_OUT"
  if [[ "$PROMOTE_MODE" == "real" || "$PROMOTE_MODE" == "mock" ]]; then
    "$ROOT_DIR/scripts/promote_compare_thresholds.sh" "$THRESHOLD_OUT/recommended-thresholds.properties" "$PROMOTE_MODE"
  fi
fi

echo "Shadow production quality sweeps complete."
echo "Artifacts: $OUT_ROOT"
