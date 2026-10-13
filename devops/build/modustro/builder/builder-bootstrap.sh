#!/usr/bin/env bash
set -euo pipefail

# Emergency standalone bootstrap. No Modustro settings/project plugin is loaded.
locBuilderDirectory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
locRepositoryDirectory="$(cd -- "$locBuilderDirectory/../../../.." && pwd -P)"
locTemporaryDirectory="$(mktemp -d "${TMPDIR:-/tmp}/modustro-builder-bootstrap.XXXXXXXX")"
trap 'rm -rf -- "$locTemporaryDirectory"' EXIT
command -v algites-cloudsmith >/dev/null || { echo "algites-cloudsmith is required" >&2; exit 2; }

cat > "$locTemporaryDirectory/settings.gradle" <<'GRADLE'
pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
rootProject.name = 'modustro-builder-emergency-bootstrap'
['yamldefs', 'modustrobuild', 'coreintf', 'jackson', 'coreimpl', 'gradleinit'].each { name ->
    include ':' + name
}
GRADLE

cat > "$locTemporaryDirectory/build.gradle" <<'GRADLE'
allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        maven { url = uri('https://dl.cloudsmith.io/public/algites/java-snapshots-pub/maven/') }
    }
}
subprojects {
    configurations.configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute module('eu.algites.pltf.modustro.builder:pub.gov.Algites_devops.build.modustro.builder.coreintf') using project(':coreintf')
            substitute module('eu.algites.tool.build:pub.gov.Algites_devops.build.modustrobuild') using project(':modustrobuild')
        }
    }
}
GRADLE

locModules=(yamldefs modustrobuild coreintf jackson coreimpl gradleinit)
locSources=(
    "$locRepositoryDirectory/devops/build/yamldefs"
    "$locRepositoryDirectory/devops/build/modustrobuild"
    "$locBuilderDirectory/coreintf"
    "$locBuilderDirectory/structureddata/jackson"
    "$locBuilderDirectory/coreimpl"
    "$locBuilderDirectory/gradleinit"
)
for locIndex in "${!locModules[@]}"; do
    locModule="${locModules[$locIndex]}"
    mkdir -p "$locTemporaryDirectory/$locModule"
    if [[ -d "${locSources[$locIndex]}/src" ]]; then
        ln -s "${locSources[$locIndex]}/src" "$locTemporaryDirectory/$locModule/src"
    fi
done
# The actual gradleinit build defines both Settings/Project plugin descriptors.
cp "$locBuilderDirectory/gradleinit/build.gradle.kts" "$locTemporaryDirectory/gradleinit/build.gradle.kts"
# The standalone temporary project must embed the same repository defaults.
python3 - "$locTemporaryDirectory/gradleinit/build.gradle.kts" "$locRepositoryDirectory/repository/defaults" <<'PYTHON'
from pathlib import Path
import sys
locFile = Path(sys.argv[1])
locText = locFile.read_text()
locText = locText.replace('../../../../../repository/defaults', sys.argv[2])
locFile.write_text(locText)
PYTHON

cat > "$locTemporaryDirectory/module.gradle" <<'GRADLE'
apply plugin: 'java-library'
apply plugin: 'maven-publish'
group = project.findProperty('bootstrapGroup')
version = project.findProperty('modustro.builder.bootstrapVersion') ?: '1.0-SNAPSHOT'
java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
    withSourcesJar()
}
sourceSets.main {
    java.srcDirs = ['src/product/java', 'src/product/java.gen']
    resources.srcDirs = ['src/product/resources', 'src/product/yamldefs', 'src/product/xmldefs', 'src/product/jsondefs']
}
sourceSets.test {
    java.srcDirs = ['src/develop/java']
    resources.srcDirs = ['src/develop/resources']
}
tasks.withType(Test).configureEach { useTestNG() }
publishing {
    publications {
        mavenJava(MavenPublication) {
            from components.java
            artifactId = project.findProperty('bootstrapArtifactId')
        }
    }
}
GRADLE

for locModule in yamldefs modustrobuild coreintf jackson coreimpl; do
    if [[ "$locModule" == yamldefs || "$locModule" == modustrobuild ]]; then
        locGroup='eu.algites.tool.build'
    else
        locGroup='eu.algites.pltf.modustro.builder'
    fi
    case "$locModule" in
        yamldefs) locArtifact='pub.gov.Algites_devops.build.yamldefs' ;;
        modustrobuild) locArtifact='pub.gov.Algites_devops.build.modustrobuild' ;;
        jackson) locArtifact='pub.gov.Algites_devops.build.modustro.builder.structureddata.jackson' ;;
        *) locArtifact="pub.gov.Algites_devops.build.modustro.builder.${locModule}" ;;
    esac
    printf "ext.bootstrapGroup = '%s'\next.bootstrapArtifactId = '%s'\napply from: rootProject.file('module.gradle')\n" \
        "$locGroup" "$locArtifact" > "$locTemporaryDirectory/$locModule/build.gradle"
done

cat >> "$locTemporaryDirectory/build.gradle" <<'GRADLE'
project(':coreintf') {
    dependencies { testImplementation 'org.testng:testng:7.11.0' }
}
project(':jackson') {
    dependencies {
        api project(':coreintf')
        implementation 'com.fasterxml.jackson.core:jackson-databind:2.18.3'
        implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.3'
        implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.18.3'
        testImplementation 'org.testng:testng:7.11.0'
    }
}
project(':coreimpl') {
    dependencies {
        api project(':coreintf')
        implementation 'com.fasterxml.jackson.core:jackson-databind:2.18.3'
        implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.3'
        implementation 'eu.algites.lib.common:pub.lib.General_version.core:1.0-SNAPSHOT'
        implementation 'eu.algites.lib.common:pub.lib.General_version.scheme.algites.v1:1.0-SNAPSHOT'
        implementation 'eu.algites.lib.common:pub.lib.General_version.scheme.gradle:1.0-SNAPSHOT'
        implementation 'eu.algites.lib.common:pub.lib.General_version.scheme.pep440:1.0-SNAPSHOT'
        implementation 'eu.algites.lib.common:pub.lib.General_version.scheme.conversion.algites2gradle.v1:1.0-SNAPSHOT'
        implementation 'eu.algites.lib.common:pub.lib.General_version.scheme.conversion.algites2pep440.v1:1.0-SNAPSHOT'
        testImplementation 'org.testng:testng:7.11.0'
    }
}
project(':modustrobuild') {
    dependencies {
        api project(':coreimpl')
        api 'eu.algites.tool.codegen:pub.tool.General_generators.code.defscodegen.coreimpl:1.0-SNAPSHOT'
        api 'eu.algites.lib.naming:pub.lib.General_naming.convention.coreimpl:1.0-SNAPSHOT'
    }
}
GRADLE

locVersion="${MODUSTRO_BUILDER_BOOTSTRAP_VERSION:-1.0-SNAPSHOT}"
locGradleCommand=(bash "$locRepositoryDirectory/gradlew")
if [[ -n "${MODUSTRO_GRADLE_EXECUTABLE:-}" ]]; then
    locGradleCommand=("${MODUSTRO_GRADLE_EXECUTABLE}")
fi
"${locGradleCommand[@]}" --no-daemon --project-dir "$locTemporaryDirectory" \
    -Pmodustro.builder.bootstrapVersion="$locVersion" \
    -Pmodustro.gradleinit.version="$locVersion" \
    :yamldefs:publishToMavenLocal :coreintf:publishToMavenLocal \
    :jackson:publishToMavenLocal :coreimpl:publishToMavenLocal \
    :modustrobuild:publishToMavenLocal :gradleinit:publishToMavenLocal

# Build Python distributions independently of the Modustro Gradle plugin.
# The workspace merges namespace package trees from all Python source roots,
# as the normal Python adapter does before invoking setuptools.
locPythonVersion="${MODUSTRO_BUILDER_BOOTSTRAP_PYTHON_VERSION:-}"
if [[ -z "$locPythonVersion" ]]; then
    if [[ "$locVersion" =~ ^([0-9]+(\.[0-9]+)*)-SNAPSHOT$ ]]; then
        locPythonVersion="${BASH_REMATCH[1]}.dev$(date -u +%Y%m%d%H%M%S)"
    elif [[ "$locVersion" =~ ^[0-9]+(\.[0-9]+)*$ ]]; then
        locPythonVersion="$locVersion"
    else
        echo "Set MODUSTRO_BUILDER_BOOTSTRAP_PYTHON_VERSION for nonstandard version $locVersion" >&2
        exit 2
    fi
fi
locPythonOutput="$locTemporaryDirectory/python-dist"
locPythonManifest="$locTemporaryDirectory/python-packages.tsv"
mkdir -p "$locPythonOutput"
PYTHONDONTWRITEBYTECODE=1 python3 - "$locPythonVersion" "$locPythonOutput" "$locPythonManifest" "$locRepositoryDirectory" \
    "$locRepositoryDirectory/devops/build/yamldefs" "$locBuilderDirectory/coreintf" <<'PYTHON'
import pathlib
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import tomllib
import zipfile

version, output_directory, manifest_file, repository_directory, *artifact_directories = sys.argv[1:]
output_directory = pathlib.Path(output_directory)
manifest_file = pathlib.Path(manifest_file)
with manifest_file.open('w', encoding='utf-8') as manifest:
    for artifact_string in artifact_directories:
        artifact = pathlib.Path(artifact_string)
        original_metadata = (artifact / 'pyproject.toml').read_text(encoding='utf-8')
        parsed = tomllib.loads(original_metadata)
        distribution = parsed['project']['name']
        roots = parsed['tool']['setuptools']['packages']['find']['where']
        with tempfile.TemporaryDirectory(prefix='modustro-bootstrap-python-') as temporary:
            project = pathlib.Path(temporary)
            source_root = project / 'src' / 'python'
            source_root.mkdir(parents=True)
            for root in roots:
                location = (artifact / root).resolve()
                if not location.is_relative_to(artifact.resolve()):
                    raise RuntimeError(f'Python source root escapes artifact: {root}')
                if not location.is_dir():
                    continue
                for source in sorted(location.rglob('*')):
                    if not source.is_file() or '__pycache__' in source.parts or source.suffix in ('.pyc', '.pyo'):
                        continue
                    destination = source_root / source.relative_to(location)
                    if destination.exists():
                        if destination.read_bytes() != source.read_bytes():
                            raise RuntimeError(f'Conflicting Python sources: {destination.relative_to(source_root)}')
                        continue
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(source, destination)
            metadata = re.sub(r'(?m)^version\s*=\s*"[^"]+"\s*$', f'version = "{version}"', original_metadata, count=1)
            metadata = re.sub(r'(?m)^where\s*=.*$', 'where = ["src/python"]', metadata, count=1)
            if metadata == original_metadata and parsed['project']['version'] != version:
                raise RuntimeError(f'Unable to update Python version for {distribution}')
            (project / 'pyproject.toml').write_text(metadata, encoding='utf-8')
            for license_file in parsed['project'].get('license-files', []):
                original = artifact / license_file
                if not original.is_file():
                    original = pathlib.Path(repository_directory) / license_file
                if not original.is_file():
                    raise RuntimeError(f'Missing Python license {license_file} for {distribution}')
                destination = project / license_file
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(original, destination)
            before = set(output_directory.iterdir())
            subprocess.run([sys.executable, '-m', 'build', '--no-isolation', '--outdir', str(output_directory)],
                           cwd=project, check=True)
            created = sorted(set(output_directory.iterdir()) - before)
            wheels = [item for item in created if item.suffix == '.whl']
            sdists = [item for item in created if item.name.endswith('.tar.gz')]
            if len(wheels) != 1 or len(sdists) != 1:
                raise RuntimeError(f'Expected one wheel and one sdist for {distribution}: {created}')
            for wheel in wheels:
                with zipfile.ZipFile(wheel) as archive:
                    if not any(name.endswith('.dist-info/METADATA') for name in archive.namelist()):
                        raise RuntimeError(f'Missing wheel metadata: {wheel}')
            for sdist in sdists:
                with tarfile.open(sdist, 'r:gz') as archive:
                    if not any(name.endswith('/PKG-INFO') for name in archive.getnames()):
                        raise RuntimeError(f'Missing sdist metadata: {sdist}')
            manifest.write(f'{wheels[0]}\t{sdists[0]}\n')
PYTHON

locRepo="${MODUSTRO_BUILDER_BOOTSTRAP_CLOUDSMITH_REPO:-algites/java-snapshots-pub}"
locPythonRepo="${MODUSTRO_BUILDER_BOOTSTRAP_PYTHON_CLOUDSMITH_REPO:-algites/python-snapshots-pub}"
locManifest="$locTemporaryDirectory/packages.tsv"
for locIndex in "${!locModules[@]}"; do
    locModule="${locModules[$locIndex]}"
    case "$locModule" in
        yamldefs|modustrobuild) locGroup='eu/algites/tool/build' ;;
        *) locGroup='eu/algites/pltf/modustro/builder' ;;
    esac
    case "$locModule" in
        yamldefs) locArtifact='pub.gov.Algites_devops.build.yamldefs' ;;
        modustrobuild) locArtifact='pub.gov.Algites_devops.build.modustrobuild' ;;
        jackson) locArtifact='pub.gov.Algites_devops.build.modustro.builder.structureddata.jackson' ;;
        *) locArtifact="pub.gov.Algites_devops.build.modustro.builder.${locModule}" ;;
    esac
    locPath="${HOME}/.m2/repository/$locGroup/$locArtifact/$locVersion/$locArtifact-$locVersion"
    for locExtension in jar pom module; do
        [[ -s "$locPath.$locExtension" ]] || { echo "Missing $locPath.$locExtension" >&2; exit 1; }
    done
    printf '%s\t%s\t%s\t%s\n' "$locPath.jar" "$locPath.pom" \
        "$locPath.module" "$locPath-sources.jar" >> "$locManifest"
done

if [[ "${MODUSTRO_BOOTSTRAP_PREPARE_ONLY:-0}" == 1 ]]; then
    locOut="${MODUSTRO_BOOTSTRAP_OUTPUT_DIR:-$locRepositoryDirectory/build/run/builder-bootstrap}"
    mkdir -p "$locOut"
    cp "$locManifest" "$locOut/packages.tsv"
    cp "$locPythonManifest" "$locOut/python-packages.tsv"
    cp -a "$locPythonOutput" "$locOut/python-dist"
    printf 'Prepared six Maven and four Python bootstrap distributions: %s\n' "$locOut/packages.tsv"
    exit 0
fi
algites-cloudsmith --session -- bash -c '
    set -euo pipefail
    locRepo="$1"; locManifest="$2"; locPythonRepo="$3"; locPythonManifest="$4"
    while IFS=$'"'"'\t'"'"' read -r locJar locPom locModule locSources; do
        locArgs=(--pom-file "$locPom" --extra-files "$locModule")
        if [[ -s "$locSources" ]]; then locArgs+=(--sources-file "$locSources"); fi
        cloudsmith push maven "$locRepo" "$locJar" "${locArgs[@]}"
    done < "$locManifest"
    while IFS=$'"'"'\t'"'"' read -r locWheel locSdist; do
        cloudsmith push python "$locPythonRepo" "$locWheel"
        cloudsmith push python "$locPythonRepo" "$locSdist"
    done < "$locPythonManifest"
' _ "$locRepo" "$locManifest" "$locPythonRepo" "$locPythonManifest"
