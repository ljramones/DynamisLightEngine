#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "This launcher is for macOS. Use standard demo commands on other platforms." >&2
  exit 1
fi

source "$ROOT_DIR/scripts/check_java25.sh"
enforce_java25_for_maven

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
    "${VULKAN_SDK:-}/etc/vulkan/icd.d/MoltenVK_icd.json" \
    /opt/homebrew/etc/vulkan/icd.d/MoltenVK_icd.json \
    /opt/homebrew/share/vulkan/icd.d/MoltenVK_icd.json \
    /usr/local/etc/vulkan/icd.d/MoltenVK_icd.json \
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

VULKAN_LOADER_DIR="$(find_vulkan_loader_dir || true)"
VULKAN_ICD_JSON="$(find_vulkan_icd_json || true)"
VULKAN_LIBNAME=""
if [[ -n "$VULKAN_LOADER_DIR" ]]; then
  if [[ -f "$VULKAN_LOADER_DIR/libvulkan.1.dylib" ]]; then
    VULKAN_LIBNAME="$VULKAN_LOADER_DIR/libvulkan.1.dylib"
  elif [[ -f "$VULKAN_LOADER_DIR/libvulkan.dylib" ]]; then
    VULKAN_LIBNAME="$VULKAN_LOADER_DIR/libvulkan.dylib"
  fi
fi

if [[ -n "$VULKAN_LOADER_DIR" ]]; then
  export DYLD_FALLBACK_LIBRARY_PATH="${VULKAN_LOADER_DIR}${DYLD_FALLBACK_LIBRARY_PATH:+:$DYLD_FALLBACK_LIBRARY_PATH}"
fi
if [[ -n "$VULKAN_ICD_JSON" ]]; then
  export VK_ICD_FILENAMES="$VULKAN_ICD_JSON"
fi

if [[ -n "$VULKAN_LIBNAME" ]]; then
  echo "Demo launcher Vulkan loader: $VULKAN_LIBNAME"
else
  echo "Demo launcher Vulkan loader not found; real Vulkan may fail." >&2
fi
if [[ -n "$VULKAN_ICD_JSON" ]]; then
  echo "Demo launcher Vulkan ICD: $VULKAN_ICD_JSON"
else
  echo "Demo launcher Vulkan ICD not found; real Vulkan may fail." >&2
fi

exec mvn -f engine-demos/pom.xml -DskipTests exec:exec \
  -Dexec.executable=java \
  -Dexec.classpathScope=runtime \
  -Dexec.args="-XstartOnFirstThread ${VULKAN_LIBNAME:+-Dorg.lwjgl.vulkan.libname=$VULKAN_LIBNAME} -cp %classpath org.dynamislight.demos.DemoRunner $*"
