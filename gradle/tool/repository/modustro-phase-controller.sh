#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: modustro-phase-controller.sh [--through resolve|prepare|compile|verify|package|publish] [--build-root DIR] [-- GRADLE_ARGS...]

Runs Modustro Builder phases as separate Gradle invocations. Each invocation
covers the current build domain and all included isolated child domains, so a
successful return is the repository-wide barrier for that phase.
USAGE
}

through="publish"
build_root="${MODUSTRO_GRADLE_BUILD_ROOT:-$PWD}"
gradle_args=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --through)
      through="${2,,}"
      shift 2
      ;;
    --build-root)
      build_root="$2"
      shift 2
      ;;
    --)
      shift
      gradle_args+=("$@")
      break
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

case "$through" in
  resolve|prepare|compile|verify|package|publish)
    ;;
  *)
    echo "Unsupported terminal phase '$through'." >&2
    usage >&2
    exit 2
    ;;
esac

build_root="$(cd "$build_root" && pwd -P)"
source_root="$build_root"
while [[ ! -f "$source_root/modustro-source-repository.yml" ]]; do
  parent="$(dirname "$source_root")"
  if [[ "$parent" == "$source_root" ]]; then
    echo "Cannot locate modustro-source-repository.yml above build root '$build_root'." >&2
    exit 2
  fi
  source_root="$parent"
done

wrapper_root="$build_root"
while [[ ! -f "$wrapper_root/gradlew" ]]; do
  parent="$(dirname "$wrapper_root")"
  if [[ "$parent" == "$wrapper_root" ]]; then
    echo "Cannot locate gradlew on the ancestor path of build root '$build_root'." >&2
    exit 2
  fi
  wrapper_root="$parent"
done
wrapper="$wrapper_root/gradlew"
if [[ ! -x "$wrapper" ]]; then
  chmod +x "$wrapper" 2>/dev/null || true
fi
if [[ ! -x "$wrapper" ]]; then
  echo "Gradle wrapper is not executable: $wrapper" >&2
  exit 2
fi

phases=(resolve prepare compile verify package publish)
tasks=(modustroResolvePhase modustroPreparePhase modustroCompilePhase modustroVerifyPhase modustroPackagePhase modustroPublishPhase)
for i in "${!phases[@]}"; do
  echo "=== Modustro phase: ${phases[$i]} ==="
  "$wrapper" --no-daemon --project-dir "$build_root" "${gradle_args[@]}" "${tasks[$i]}"
  if [[ "${phases[$i]}" == "$through" ]]; then
    break
  fi
done
