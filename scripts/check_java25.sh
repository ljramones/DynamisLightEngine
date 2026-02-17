#!/usr/bin/env bash
set -euo pipefail

# Shared JDK guard for Maven-based scripts.
# Usage:
#   source scripts/check_java25.sh
#   enforce_java25_for_maven

enforce_java25_for_maven() {
  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
  fi
  export PATH="$JAVA_HOME/bin:$PATH"

  local mvn_java
  mvn_java="$(mvn -version 2>/dev/null | awk -F': ' '/^Java version:/{print $2}' | cut -d',' -f1)"
  if [[ -z "${mvn_java}" || "${mvn_java}" != 25* ]]; then
    echo "ERROR: Maven is not running on JDK 25 (detected: ${mvn_java:-unknown})." >&2
    echo "Set JAVA_HOME to JDK 25 before running this command." >&2
    echo "Example: export JAVA_HOME=\$HOME/.jenv/versions/25" >&2
    exit 1
  fi
}

