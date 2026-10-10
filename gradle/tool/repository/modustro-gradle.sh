#!/usr/bin/env bash
set -euo pipefail

# Prevent all Python subprocesses spawned by Gradle or its build logic from
# creating transient bytecode beside staged source and dependency files.
export PYTHONDONTWRITEBYTECODE=1

# Default build caching and diagnostic stacktraces without overriding explicit choices.
locWrapper="${MODUSTRO_GRADLE_WRAPPER:-}"
locWrapperRoot="$PWD"
if [[ -z "$locWrapper" ]]; then
  while [[ ! -f "$locWrapperRoot/gradlew" ]]; do
    locParent="$(dirname "$locWrapperRoot")"
    if [[ "$locParent" == "$locWrapperRoot" ]]; then
      echo "Cannot locate Gradle wrapper above '$PWD'." >&2
      exit 2
    fi
    locWrapperRoot="$locParent"
  done
  locWrapper="$locWrapperRoot/gradlew"
else
  locWrapperRoot="$(cd "$(dirname "$locWrapper")" && pwd -P)"
fi
locProjectRoot="$PWD"
locArguments=("$@")
locCacheChoice=false
locTraceChoice=false
for ((locIndex=0; locIndex<${#locArguments[@]}; locIndex++)); do
  locArgument="${locArguments[$locIndex]}"
  case "$locArgument" in
    --build-cache|--no-build-cache|-Dorg.gradle.caching=*|-Porg.gradle.caching=*) locCacheChoice=true ;;
    --stacktrace|--full-stacktrace|-s|-S) locTraceChoice=true ;;
    --project-dir|-p) locProjectRoot="${locArguments[$((locIndex+1))]}" ;;
    --project-dir=*) locProjectRoot="${locArgument#*=}" ;;
    --gradle-user-home|-g) locGradleUserHome="${locArguments[$((locIndex+1))]}" ;;
    --gradle-user-home=*) locGradleUserHome="${locArgument#*=}" ;;
  esac
done
locGradleUserHome="${locGradleUserHome:-${GRADLE_USER_HOME:-$HOME/.gradle}}"
if [[ "${GRADLE_OPTS:-} ${JAVA_OPTS:-} ${JAVA_TOOL_OPTIONS:-} ${JDK_JAVA_OPTIONS:-}" == *org.gradle.caching* ]]; then
  locCacheChoice=true
fi
for locProperties in "$locProjectRoot/gradle.properties" "$locWrapperRoot/gradle.properties" "$locGradleUserHome/gradle.properties"; do
  if [[ -f "$locProperties" ]] && LC_ALL=C awk '
    /^[[:space:]]*[#!]/ { next }
    /^[[:space:]]*org[.]gradle[.]caching([[:space:]]*[:=]|[[:space:]]+)/ { found=1 }
    END { exit !found }
  ' "$locProperties"; then
    locCacheChoice=true
  fi
done
locDefaults=()
if [[ "$locCacheChoice" == false ]]; then locDefaults+=(--build-cache); fi
if [[ "$locTraceChoice" == false ]]; then locDefaults+=(--stacktrace); fi
exec "$locWrapper" "${locDefaults[@]}" "${locArguments[@]}"
