# Algites build support bundle

This artifact is the dependency bundle for the Algites build infrastructure.
It provides a single published dependency through which Gradle bootstrap code can obtain the reusable libraries required by a specific generation of the Algites governance/build tooling.

The artifact intentionally owns the concrete dependency set used by the build infrastructure. Consumers should depend on this artifact instead of declaring the individual build-library dependencies themselves.

## Current dependencies

The first version aggregates the version infrastructure from `pub.lib.General`:

```text
pub.lib.General/version/core
pub.lib.General/version/scheme/algites/v1
pub.lib.General/version/scheme/maven
pub.lib.General/version/scheme/gradle
pub.lib.General/version/scheme/pep440
pub.lib.General/version/scheme/conversion/common
pub.lib.General/version/scheme/conversion/maven2gradle
pub.lib.General/version/scheme/conversion/algites2maven/v1
pub.lib.General/version/scheme/conversion/algites2gradle/v1
pub.lib.General/version/scheme/conversion/algites2pep440/v1
```

All dependencies are exposed as Gradle `api` dependencies so they remain transitively available to consumers of the bundle.

Future Algites build-runtime libraries that are not related to version handling may be added here as well. The artifact path is therefore deliberately `devops/build/algitesbuild` rather than a version-specific path.

## `pub.lib.General` version

Until the Algites dependency model is used by the build infrastructure itself, the `pub.lib.General` version is declared directly by this artifact's Gradle build.

The default is:

```text
1.0-SNAPSHOT
```

It can be overridden when building/publishing this artifact with:

```bash
./gradlew ... -Palgites.build.pubLibGeneral.version=<version>
```

The effective dependency version is written to the published Gradle module metadata/POM by the normal Gradle publication process. Consumers of this artifact therefore do not need to select the individual `pub.lib.General` versions themselves.

A floating `*-SNAPSHOT` dependency is suitable for the initial bootstrap phase but does not identify one immutable snapshot instance. Once immutable release or snapshot coordinates are available, this artifact should pin those coordinates instead.

## Publication identity

With the current repository metadata the Java publication identity is derived as:

```text
GroupId:    eu.algites.tool.build
ArtifactId: pub.gov.Algites_devops.build.algitesbuild
```

The artifact version follows the version of `pub.gov.Algites`, so a published `algitesbuild` version describes the build-support dependency set belonging to that governance generation.

## Intended bootstrap use

Gradle bootstrap code should eventually need only this one external build-support dependency:

```text
eu.algites.tool.build:pub.gov.Algites_devops.build.algitesbuild:<bootstrap-version>
```

The build of a new `pub.gov.Algites` generation must still use an already-published bootstrap version. After the new governance generation has published its own `algitesbuild` artifact, downstream builds such as `priv.gov.Algites` can use that newly published version.
