#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "This script is intended for macOS (Darwin)." >&2
  exit 1
fi

if ! command -v /usr/libexec/java_home >/dev/null 2>&1; then
  echo "Cannot find /usr/libexec/java_home to resolve JDK 25." >&2
  exit 1
fi

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 25)}"
export PATH="$JAVA_HOME/bin:$PATH"

OUT_DIR="${DLE_COMPARE_OUTPUT_DIR:-artifacts/compare/aa-real-$(date +%Y%m%d-%H%M%S)}"
TEST_CLASS="${DLE_COMPARE_TEST_CLASS:-BackendParityIntegrationTest}"
VULKAN_MODE="${DLE_COMPARE_VULKAN_MODE:-mock}" # mock(default) | auto | real
SCRIPT_COMMAND="${1:-run}" # run(default) | preflight

find_vulkan_loader_dir() {
  local candidate
  for candidate in \
    "${DLE_VULKAN_LOADER_DIR:-}" \
    "${VULKAN_SDK:-}/lib" \
    /opt/homebrew/lib \
    /usr/local/lib \
    /opt/homebrew/opt/molten-vk/lib \
    /usr/local/opt/molten-vk/lib; do
    [[ -z "$candidate" ]] && continue
    if [[ -f "$candidate/libvulkan.1.dylib" || -f "$candidate/libvulkan.dylib" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

find_vulkan_icd_json() {
  local candidate
  for candidate in \
    "${DLE_VULKAN_ICD_JSON:-}" \
    "${VULKAN_SDK:-}/share/vulkan/icd.d/MoltenVK_icd.json" \
    /opt/homebrew/share/vulkan/icd.d/MoltenVK_icd.json \
    /usr/local/share/vulkan/icd.d/MoltenVK_icd.json \
    /opt/homebrew/share/vulkan/icd.d/MoltenVK_icd.json.bak; do
    [[ -z "$candidate" ]] && continue
    if [[ -f "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

run_vulkan_preflight() {
  local loader_dir="$1"
  local icd_json="$2"
  local preflight_log
  local loader_file=""
  local failures=()
  if [[ -z "$loader_dir" ]]; then
    failures+=("Vulkan loader not found (missing libvulkan.1.dylib / libvulkan.dylib).")
  else
    if [[ -f "$loader_dir/libvulkan.1.dylib" ]]; then
      loader_file="$loader_dir/libvulkan.1.dylib"
    elif [[ -f "$loader_dir/libvulkan.dylib" ]]; then
      loader_file="$loader_dir/libvulkan.dylib"
    else
      failures+=("Vulkan loader directory '$loader_dir' does not contain libvulkan.1.dylib or libvulkan.dylib.")
    fi
  fi

  if ! command -v vulkaninfo >/dev/null 2>&1; then
    failures+=("vulkaninfo command not found (install Vulkan SDK tools).")
  fi

  if [[ -n "$icd_json" && ! -f "$icd_json" ]]; then
    failures+=("Configured Vulkan ICD JSON does not exist: $icd_json")
  fi

  if [[ "${#failures[@]}" -eq 0 ]]; then
    preflight_log="$(mktemp -t dle-vulkan-preflight.XXXXXX.log)"
    if ! VK_ICD_FILENAMES="${icd_json:-${VK_ICD_FILENAMES:-}}" vulkaninfo >"$preflight_log" 2>&1; then
      failures+=("vulkaninfo failed to initialize Vulkan (check ICD/loader setup).")
    else
      if ! grep -qi "VK_KHR_surface" "$preflight_log"; then
        failures+=("Required Vulkan extension VK_KHR_surface is missing.")
      fi
      if ! grep -qi "VK_EXT_metal_surface" "$preflight_log"; then
        failures+=("Required Vulkan extension VK_EXT_metal_surface is missing.")
      fi
      if ! grep -qi "GPU id" "$preflight_log"; then
        failures+=("No Vulkan GPU enumerated by loader (ICD may be missing/broken).")
      fi
    fi
  fi

  if [[ "${#failures[@]}" -ne 0 ]]; then
    echo "Real Vulkan preflight: FAILED" >&2
    for item in "${failures[@]}"; do
      echo "  - $item" >&2
    done
    echo "Hints:" >&2
    echo "  - Ensure Homebrew vulkan-loader/molten-vk are installed." >&2
    echo "  - Ensure your loader path exposes libvulkan.1.dylib." >&2
    echo "  - Ensure vulkaninfo reports VK_KHR_surface and VK_EXT_metal_surface." >&2
    return 1
  fi

  echo "Real Vulkan preflight: OK"
  echo "Loader: $loader_file"
  if [[ -n "$icd_json" ]]; then
    echo "ICD: $icd_json"
  else
    echo "ICD: auto-discovery (VK_ICD_FILENAMES not set)"
  fi
}

MODE_NORMALIZED="$(printf '%s' "$VULKAN_MODE" | tr '[:upper:]' '[:lower:]')"
VULKAN_LOADER_DIR=""
VULKAN_ICD_JSON=""
if VULKAN_LOADER_DIR="$(find_vulkan_loader_dir)"; then
  export DYLD_FALLBACK_LIBRARY_PATH="${VULKAN_LOADER_DIR}${DYLD_FALLBACK_LIBRARY_PATH:+:$DYLD_FALLBACK_LIBRARY_PATH}"
fi
if VULKAN_ICD_JSON="$(find_vulkan_icd_json)"; then
  export VK_ICD_FILENAMES="$VULKAN_ICD_JSON"
fi
JVM_ARG_LINE="-XstartOnFirstThread"
if [[ -n "$VULKAN_LOADER_DIR" ]]; then
  if [[ -f "$VULKAN_LOADER_DIR/libvulkan.1.dylib" ]]; then
    JVM_ARG_LINE="$JVM_ARG_LINE -Dorg.lwjgl.vulkan.libname=$VULKAN_LOADER_DIR/libvulkan.1.dylib"
  elif [[ -f "$VULKAN_LOADER_DIR/libvulkan.dylib" ]]; then
    JVM_ARG_LINE="$JVM_ARG_LINE -Dorg.lwjgl.vulkan.libname=$VULKAN_LOADER_DIR/libvulkan.dylib"
  fi
fi
OUT_BASE_DIR="$OUT_DIR"

case "$MODE_NORMALIZED" in
  real)
    if [[ -z "$VULKAN_LOADER_DIR" ]]; then
      echo "Vulkan real mode requested, but no Vulkan loader library was found (libvulkan.1.dylib)." >&2
      echo "Install a Vulkan loader (e.g. Vulkan SDK/MoltenVK), or rerun with DLE_COMPARE_VULKAN_MODE=mock or auto." >&2
      exit 1
    fi
    VULKAN_MOCK_CONTEXT=false
    ;;
  mock)
    VULKAN_MOCK_CONTEXT=true
    ;;
  auto)
    if [[ -n "$VULKAN_LOADER_DIR" ]]; then
      VULKAN_MOCK_CONTEXT=false
    else
      VULKAN_MOCK_CONTEXT=true
      echo "No Vulkan loader found; falling back to Vulkan mock context." >&2
    fi
    ;;
  *)
    echo "Invalid DLE_COMPARE_VULKAN_MODE='$VULKAN_MODE' (expected: auto|real|mock)." >&2
    exit 1
    ;;
esac

if [[ "$SCRIPT_COMMAND" == "preflight" ]]; then
  run_vulkan_preflight "$VULKAN_LOADER_DIR" "$VULKAN_ICD_JSON"
  exit 0
fi

if [[ "$SCRIPT_COMMAND" != "run" ]]; then
  echo "Invalid command '$SCRIPT_COMMAND' (expected: run|preflight)." >&2
  exit 1
fi

if [[ "$MODE_NORMALIZED" == "real" || "$MODE_NORMALIZED" == "auto" ]]; then
  if [[ "$VULKAN_MOCK_CONTEXT" == "false" ]]; then
    run_vulkan_preflight "$VULKAN_LOADER_DIR" "$VULKAN_ICD_JSON"
  fi
fi

if [[ "$VULKAN_MOCK_CONTEXT" == "true" ]]; then
  OUT_DIR="${OUT_BASE_DIR%/}/vulkan_mock"
else
  OUT_DIR="${OUT_BASE_DIR%/}/vulkan_real"
fi

echo "Using JAVA_HOME=$JAVA_HOME"
java -version
echo "Writing compare artifacts to: $OUT_DIR"
echo "Vulkan mode: $MODE_NORMALIZED (mockContext=$VULKAN_MOCK_CONTEXT)"
if [[ -n "$VULKAN_LOADER_DIR" ]]; then
  echo "Vulkan loader dir: $VULKAN_LOADER_DIR"
fi
if [[ -n "${VULKAN_ICD_JSON:-}" ]]; then
  echo "Vulkan ICD json: $VULKAN_ICD_JSON"
fi

run_compare_tests() {
  local mock_context="$1"
  local log_file="$2"
  set +e
  mvn -q -pl engine-host-sample -am test \
    -DargLine="$JVM_ARG_LINE" \
    -Ddle.compare.tests=true \
    -Ddle.compare.outputDir="$OUT_DIR" \
    -Ddle.compare.opengl.mockContext=false \
    -Ddle.compare.vulkan.mockContext="$mock_context" \
    -Ddle.compare.vulkan.postOffscreen=true \
    -Dtest="$TEST_CLASS" \
    -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tee "$log_file"
  local status=${PIPESTATUS[0]}
  set -e
  return "$status"
}

LOG_FILE="$(mktemp -t dle-aa-rebaseline.XXXXXX.log)"
if ! run_compare_tests "$VULKAN_MOCK_CONTEXT" "$LOG_FILE"; then
  if [[ "$MODE_NORMALIZED" == "auto" && "$VULKAN_MOCK_CONTEXT" == "false" ]] && \
    rg -q "No required Vulkan instance extensions from GLFW|Failed to locate library: libvulkan\\.1\\.dylib|Could not initialize class org\\.lwjgl\\.glfw\\.GLFWVulkan" "$LOG_FILE"; then
    echo "Real Vulkan initialization failed; auto mode is retrying with Vulkan mock context." >&2
    VULKAN_MOCK_CONTEXT=true
    OUT_DIR="${OUT_BASE_DIR%/}/vulkan_mock"
    run_compare_tests "$VULKAN_MOCK_CONTEXT" "$LOG_FILE"
  else
    exit 1
  fi
fi

echo "AA real-hardware compare rebaseline run complete."
echo "Artifacts: $OUT_DIR"
