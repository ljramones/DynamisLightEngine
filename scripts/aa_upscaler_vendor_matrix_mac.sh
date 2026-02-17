#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

OUT_ROOT="${DLE_COMPARE_UPSCALER_MATRIX_OUTPUT_ROOT:-artifacts/compare/aa-upscaler-matrix-$(date +%Y%m%d-%H%M%S)}"
VULKAN_MODE="${DLE_COMPARE_VULKAN_MODE:-real}"
TEMPORAL_FRAMES="${DLE_COMPARE_TEMPORAL_FRAMES:-10}"
TEMPORAL_WINDOW="${DLE_COMPARE_TEMPORAL_WINDOW:-10}"
TSR_FRAME_BOOST="${DLE_COMPARE_TSR_FRAME_BOOST:-6}"
TEST_CLASS="${DLE_COMPARE_TEST_CLASS:-BackendParityIntegrationTest#compareHarnessTsrUpscalerHooksAcrossTargetedScenesStayBounded}"

mkdir -p "$OUT_ROOT"

echo "AA upscaler vendor matrix run"
echo "  output root: $OUT_ROOT"
echo "  vulkan mode: $VULKAN_MODE"
echo "  test: $TEST_CLASS"

DLE_COMPARE_OUTPUT_DIR="$OUT_ROOT" \
DLE_COMPARE_TEST_CLASS="$TEST_CLASS" \
DLE_COMPARE_VULKAN_MODE="$VULKAN_MODE" \
DLE_COMPARE_TEMPORAL_FRAMES="$TEMPORAL_FRAMES" \
DLE_COMPARE_TEMPORAL_WINDOW="$TEMPORAL_WINDOW" \
DLE_COMPARE_TSR_FRAME_BOOST="$TSR_FRAME_BOOST" \
"$ROOT_DIR/scripts/aa_rebaseline_real_mac.sh"

matrix_dir="$OUT_ROOT/vendor-matrix"
mkdir -p "$matrix_dir"
matrix_tsv="$matrix_dir/upscaler-vendor-matrix.tsv"

{
  echo -e "profile\tupscaler_mode\tvulkan_mode\tdiff\topengl_hook\tvulkan_hook\topengl_native_state\tvulkan_native_state"
  while IFS= read -r meta; do
    profile="$(sed -n 's/^compare.profileTag=//p' "$meta" | head -n1)"
    mode="$(sed -n 's/^compare.upscaler.mode=//p' "$meta" | head -n1)"
    vulkan_mode="$(sed -n 's/^compare.vulkan.mode=//p' "$meta" | head -n1)"
    diff="$(sed -n 's/^compare.diffMetric=//p' "$meta" | head -n1)"
    gl_warn="$(sed -n 's/^compare.opengl.warningCodes=//p' "$meta" | head -n1)"
    vk_warn="$(sed -n 's/^compare.vulkan.warningCodes=//p' "$meta" | head -n1)"

    [[ "$profile" != *"-tsr-"* ]] && continue
    [[ -z "$mode" ]] && continue

    gl_hook="no"
    vk_hook="no"
    gl_native="none"
    vk_native="none"
    [[ "$gl_warn" == *"UPSCALER_HOOK_ACTIVE"* ]] && gl_hook="yes"
    [[ "$vk_warn" == *"UPSCALER_HOOK_ACTIVE"* ]] && vk_hook="yes"
    if [[ "$gl_warn" == *"UPSCALER_NATIVE_ACTIVE"* ]]; then
      gl_native="active"
    elif [[ "$gl_warn" == *"UPSCALER_NATIVE_INACTIVE"* ]]; then
      gl_native="inactive"
    fi
    if [[ "$vk_warn" == *"UPSCALER_NATIVE_ACTIVE"* ]]; then
      vk_native="active"
    elif [[ "$vk_warn" == *"UPSCALER_NATIVE_INACTIVE"* ]]; then
      vk_native="inactive"
    fi

    echo -e "${profile}\t${mode}\t${vulkan_mode}\t${diff}\t${gl_hook}\t${vk_hook}\t${gl_native}\t${vk_native}"
  done < <(find "$OUT_ROOT" -name compare-metadata.properties -type f | sort)
} > "$matrix_tsv"

for vendor in fsr xess dlss; do
  if ! rg -q $'\t'"$vendor"$'\t' "$matrix_tsv"; then
    echo "Missing vendor rows for '$vendor' in matrix output ($matrix_tsv)." >&2
    exit 1
  fi
done

echo "AA upscaler vendor matrix report: $matrix_tsv"
