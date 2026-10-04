#!/usr/bin/env bash
set -euo pipefail
locRoot="${1:-.}"
cd -- "$locRoot"
[[ -f modustro-source-repository.yml ]] || { echo "Run in the repository root." >&2; exit 1; }
git rev-parse --show-toplevel >/dev/null
locOld=repository/defaults/algites-repository-download-defaults-public.yml
locNew=repository/defaults/algites-repository-defaults-public.yml
if git ls-files --error-unmatch "$locNew" >/dev/null 2>&1; then
    if git ls-files --error-unmatch "$locOld" >/dev/null 2>&1; then
        echo "Both names are tracked; resolve the duplicate explicitly." >&2; exit 1
    fi
    echo "Rename already applied."; exit 0
fi
if ! git ls-files --error-unmatch "$locOld" >/dev/null 2>&1; then
    echo "The old file is not tracked. No rename is necessary."; exit 0
fi
locSaved=""
cleanup() { [[ -z "$locSaved" ]] || rm -f -- "$locSaved"; }
trap cleanup EXIT
if [[ -f "$locNew" ]]; then
    locSaved="$(mktemp)"
    cp -p -- "$locNew" "$locSaved"
    rm -- "$locNew"
fi
# Support an overlay that removed the old tracked file without staging its removal.
if [[ ! -f "$locOld" ]]; then
    git show "HEAD:$locOld" > "$locOld"
fi
git mv -- "$locOld" "$locNew"
if [[ -n "$locSaved" ]]; then cp -p -- "$locSaved" "$locNew"; fi
echo "Rename staged; the updated file contents remain available for review."
