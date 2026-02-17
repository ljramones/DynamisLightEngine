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

echo "Using JAVA_HOME=$JAVA_HOME"
java -version
echo "Writing compare artifacts to: $OUT_DIR"

mvn -q -pl engine-host-sample -am test \
  -DargLine="-XstartOnFirstThread" \
  -Ddle.compare.tests=true \
  -Ddle.compare.outputDir="$OUT_DIR" \
  -Ddle.compare.opengl.mockContext=false \
  -Ddle.compare.vulkan.mockContext=false \
  -Ddle.compare.vulkan.postOffscreen=true \
  -Dtest="$TEST_CLASS" \
  -Dsurefire.failIfNoSpecifiedTests=false

echo "AA real-hardware compare rebaseline run complete."
echo "Artifacts: $OUT_DIR"
