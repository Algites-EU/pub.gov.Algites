#!/usr/bin/env bash
set -euo pipefail

# Runs the portable publication checks without Gradle, TestNG or external repositories.
locBuilderDirectory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
locTemporaryDirectory="$(mktemp -d)"
trap 'rm -rf -- "$locTemporaryDirectory"' EXIT
shopt -s globstar nullglob
locSources=("$locBuilderDirectory"/coreintf/src/product/java/**/*.java)
for locPackage in publication catalog subscription; do
    locSources+=("$locBuilderDirectory"/coreimpl/src/product/java/eu/algites/pltf/modustro/builder/"$locPackage"/**/*.java)
done
locChecksDirectory="$locBuilderDirectory/coreimpl/src/develop/java/eu/algites/pltf/modustro/builder/publication"
locChecks=(AIcPublicationGraphChecks AIcFinalizationBarrierChecks AIcMavenBuildRecordChecks AIcRemoveCorrespondingSnapshotsChecks AIcPublicationDomainBridgeChecks)
locSources+=("$locChecksDirectory/AIcPublicationCheckAssertions.java")
for locCheck in "${locChecks[@]}"; do
    locSources+=("$locChecksDirectory/$locCheck.java")
done
if command -v javac >/dev/null 2>&1; then
    javac -d "$locTemporaryDirectory" "${locSources[@]}"
else
    java -m jdk.compiler/com.sun.tools.javac.Main -d "$locTemporaryDirectory" "${locSources[@]}"
fi
for locCheck in "${locChecks[@]}"; do
    java -cp "$locTemporaryDirectory" "eu.algites.pltf.modustro.builder.publication.$locCheck"
done
