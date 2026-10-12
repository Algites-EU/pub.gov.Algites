#!/usr/bin/env bash
set -euo pipefail

# Run from the root of the CURRENT pub.gov.Algites checkout.
# Uses the standard Modustro Gradle Maven publications; does not create synthetic POMs.
locRoot="$(pwd -P)"
[[ -f "$locRoot/modustro-source-repository.yml" && -f "$locRoot/gradlew" ]] || {
  echo 'ERROR: Run this script from the pub.gov.Algites repository root.' >&2; exit 2;
}
command -v algites-cloudsmith >/dev/null || { echo 'ERROR: algites-cloudsmith is not installed.' >&2; exit 2; }
command -v python3 >/dev/null || { echo 'ERROR: python3 is missing.' >&2; exit 2; }
locRepo="${MODUSTRO_BOOTSTRAP_CLOUDSMITH_REPO:-algites/java-snapshots-pub}"
locVersion="${MODUSTRO_BOOTSTRAP_VERSION:-1.0-SNAPSHOT}"
locM2="${HOME}/.m2/repository"
locOut="${MODUSTRO_BOOTSTRAP_OUTPUT_DIR:-${locRoot}/build/run/bootstrap-cloudsmith}"
mkdir -p "$locOut"
locOut="$(cd "$locOut" && pwd -P)"
locManifest="$locOut/packages.tsv"
locCommands="$locOut/upload-cloudsmith.sh"
locPython="$locOut/prepare.py"

# Only modules with Maven-published Java artifacts; dependencies are resolved normally.
locProjects=(
  ':devops:build:yamldefs'
  ':devops:build:modustrobuild'
  ':devops:build:modustro:builder:coreintf'
  ':devops:build:modustro:builder:structureddata:jackson'
  ':devops:build:modustro:builder:coreimpl'
  ':devops:build:modustro:builder:gradleinit'
)

# Build/publish to Maven local only; never run the repository's remote publish task.
locTasks=()
for locProject in "${locProjects[@]}"; do locTasks+=("${locProject}:publishToMavenLocal"); done
printf 'Building and publishing to Maven Local, version %s...\n' "$locVersion"
bash ./gradlew --no-daemon --stacktrace "${locTasks[@]}"

# Enumerate exactly the intended coordinates, not all of ~/.m2.
cat > "$locPython" <<'PY'
import pathlib, sys, xml.etree.ElementTree as ET
root, version, manifest = pathlib.Path(sys.argv[1]), sys.argv[2], pathlib.Path(sys.argv[3])
artifacts = [
 'pub.gov.Algites_devops.build.yamldefs',
 'pub.gov.Algites_devops.build.modustrobuild',
 'pub.gov.Algites_devops.build.modustro.builder.coreintf',
 'pub.gov.Algites_devops.build.modustro.builder.structureddata.jackson',
 'pub.gov.Algites_devops.build.modustro.builder.coreimpl',
 'pub.gov.Algites_devops.build.modustro.builder.gradleinit',
]
rows=[]
for art in artifacts:
    matches=list(root.glob('**/'+art+'/'+version+'/'+art+'-'+version+'.pom'))
    if len(matches)!=1:
        raise SystemExit(f'ERROR: Expected one Maven Local POM for {art}:{version}, found {len(matches)}: {matches}')
    pom=matches[0]
    tree=ET.parse(pom)
    def val(key):
        el=tree.find('{*}'+key)
        return el.text.strip() if el is not None and el.text else ''
    aid,gid,ver=val('artifactId'),val('groupId'),val('version')
    jar=pom.with_suffix('.jar')
    module=pom.with_suffix('.module')
    src=pom.with_name(pom.stem+'-sources.jar')
    if (aid,gid,ver)!=(art, '.'.join(pom.relative_to(root).parts[:-3]), version):
        raise SystemExit(f'ERROR: GAV mismatch in {pom}: {gid}:{aid}:{ver}')
    if not jar.is_file() or jar.stat().st_size<100:
        raise SystemExit(f'ERROR: Missing/empty JAR for {pom}')
    if not module.is_file():
        raise SystemExit(f'ERROR: Missing Gradle .module metadata for {pom}')
    if 'build-record' in jar.name or 'build-record' in pom.name:
        raise SystemExit(f'ERROR: Unexpected record in native Maven package: {pom}')
    rows.append((str(jar),str(pom),str(module),str(src) if src.is_file() else ''))
manifest.write_text(''.join('\t'.join(row)+'\n' for row in rows),encoding='utf-8')
print(f'Validated {len(rows)} complete Maven packages; manifest: {manifest}')
PY
python3 "$locPython" "$locM2" "$locVersion" "$locManifest"

# Produce a persistent upload batch for audit/retry. The wrapper retrieves the API key once.
cat > "$locCommands" <<'UPLOAD'
#!/usr/bin/env bash
set -euo pipefail
locDir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
locManifest="$locDir/packages.tsv"
locRepo="${MODUSTRO_BOOTSTRAP_CLOUDSMITH_REPO:-algites/java-snapshots-pub}"
[[ -s "$locManifest" ]] || { echo 'ERROR: Missing packages.tsv' >&2; exit 2; }
command -v algites-cloudsmith >/dev/null || exit 2
# A single Secret Service session wraps the complete upload batch.
algites-cloudsmith --session -- bash -c '
  set -euo pipefail
  locRepo="$1"; locManifest="$2"
  while IFS=$'"'"'\t'"'"' read -r locJar locPom locModule locSources; do
    [[ -s "$locJar" && -s "$locPom" && -s "$locModule" ]] || { echo "ERROR: Missing staged Maven file: $locPom" >&2; exit 2; }
    locArgs=(--pom-file "$locPom" --extra-files "$locModule")
    if [[ -n "$locSources" ]]; then locArgs+=(--sources-file "$locSources"); fi
    printf "Uploading: %s\n" "$locPom"
    cloudsmith push maven "$locRepo" "$locJar" "${locArgs[@]}"
  done < "$locManifest"
' _ "$locRepo" "$locManifest"
UPLOAD
chmod 700 "$locCommands"
printf '\nPackages are ready. Upload target: %s\n' "$locRepo"
cat "$locManifest"
if [[ "${MODUSTRO_BOOTSTRAP_PREPARE_ONLY:-0}" == 1 ]]; then
  echo "PREPARE ONLY: Execute $locCommands when ready."
else
  "$locCommands"
  echo 'Uploaded. Verify Cloudsmith synchronization before rerunning private deployment.'
fi
