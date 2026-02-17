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

MODE_NORMALIZED="$(printf '%s' "$VULKAN_MODE" | tr '[:upper:]' '[:lower:]')"
VULKAN_LOADER_DIR=""
if VULKAN_LOADER_DIR="$(find_vulkan_loader_dir)"; then
  export DYLD_FALLBACK_LIBRARY_PATH="${VULKAN_LOADER_DIR}${DYLD_FALLBACK_LIBRARY_PATH:+:$DYLD_FALLBACK_LIBRARY_PATH}"
fi
JVM_ARG_LINE="-XstartOnFirstThread"

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

echo "Using JAVA_HOME=$JAVA_HOME"
java -version
echo "Writing compare artifacts to: $OUT_DIR"
echo "Vulkan mode: $MODE_NORMALIZED (mockContext=$VULKAN_MOCK_CONTEXT)"
if [[ -n "$VULKAN_LOADER_DIR" ]]; then
  echo "Vulkan loader dir: $VULKAN_LOADER_DIR"
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
    run_compare_tests "$VULKAN_MOCK_CONTEXT" "$LOG_FILE"
  else
    exit 1
  fi
fi

echo "AA real-hardware compare rebaseline run complete."
echo "Artifacts: $OUT_DIR"
