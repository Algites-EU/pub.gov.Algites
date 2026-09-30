# Algites build support bundle

This artifact is the dependency bundle for the Algites build infrastructure.
It provides a single published dependency through which Gradle bootstrap code can obtain the reusable libraries required by a specific generation of the Algites governance/build tooling.

The artifact intentionally owns the concrete dependency set used by the build infrastructure. Consumers should depend on this artifact instead of declaring the individual build-library dependencies themselves.

## Current dependencies

The bundle aggregates Modustro Builder core implementation plus the version infrastructure from `pub.lib.General`:

```text
pub.gov.Algites/devops/build/modustro/builder/coreimpl
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

The current source artifact declares its build-support dependencies in `algites-artifact.yml` through `DependencyKind: modustro` items with `Usages: [product_api]`, so the generated Java publication exports them transitively. The bundle also exports `modustro/builder/coreimpl`; `coreimpl` in turn exports the Gradle-independent `coreintf` contract layer.

The current dependency requirement uses the exact Algites v1 snapshot version `1.0-SNAPSHOT`; repository version metadata separately uses `ReleaseLineVersion`, `Revision`, and `QualifierKind`. Changing the build-support dependency set or its versions is therefore a governed source-metadata change of this artifact.

The bootstrap remains intentionally asymmetric: the shared root build script obtains the version/conversion classes from an already-published `algitesbuild:1.0-SNAPSHOT`, while the current source `algitesbuild` artifact is built and republished using the dependency declarations above. This avoids a same-build self-dependency while allowing the build infrastructure to dogfood its own dependency model.

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

## Current Gradle bootstrap

The shared `gradle/tool/repository/algites-root-build.gradle.kts` currently loads this artifact on its script classpath from the public Cloudsmith snapshot repository using version `1.0-SNAPSHOT`. The script therefore imports the version/conversion classes transitively exposed by this bundle instead of declaring the individual `pub.lib.General` artifacts itself.

The floating snapshot coordinate is intentional during the current development phase. It can later be replaced by an immutable/released bootstrap coordinate without changing the dependency-model implementation or its consumers.
