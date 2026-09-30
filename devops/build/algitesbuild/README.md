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

The bootstrap remains intentionally asymmetric: the shared root build script obtains its directly imported build-runtime classes from already-published bootstrap artifacts. In Phase 3 this includes `algitesbuild:1.0-SNAPSHOT` plus the published Modustro Builder `coreintf` and `coreimpl` artifacts. The current source artifacts are then rebuilt and republished using the repository dependency declarations. This avoids a same-build self-dependency while allowing the build infrastructure to dogfood its own dependency and build-output model.

## Publication identity

With the current repository metadata the Java publication identity is derived as:

```text
GroupId:    eu.algites.tool.build
ArtifactId: pub.gov.Algites_devops.build.algitesbuild
```

The artifact version follows the version of `pub.gov.Algites`, so a published `algitesbuild` version describes the build-support dependency set belonging to that governance generation.

## Intended bootstrap use

Gradle bootstrap code currently needs these published build-support dependencies:

```text
eu.algites.tool.build:pub.gov.Algites_devops.build.algitesbuild:<bootstrap-version>
eu.algites.pltf.modustro.builder:pub.gov.Algites_devops.build.modustro.builder.coreintf:<bootstrap-version>
eu.algites.pltf.modustro.builder:pub.gov.Algites_devops.build.modustro.builder.coreimpl:<bootstrap-version>
```

The build of a new `pub.gov.Algites` generation must still use already-published bootstrap versions of every artifact directly imported by the shared root build script. After the new governance generation has published `algitesbuild` and the Modustro Builder core artifacts, downstream builds such as `priv.gov.Algites` and clean documentation builds can use that newly published bootstrap generation.

## Current Gradle bootstrap

The shared `gradle/tool/repository/algites-root-build.gradle.kts` currently loads `algitesbuild`, Modustro Builder `coreintf`, and Modustro Builder `coreimpl` explicitly on its script classpath from the public Cloudsmith snapshot repository using version `1.0-SNAPSHOT`. `algitesbuild` continues to expose the version/conversion libraries transitively, while the Modustro artifacts are declared directly because the root script imports their classes directly.

The floating snapshot coordinate is intentional during the current development phase. It can later be replaced by an immutable/released bootstrap coordinate without changing the dependency-model implementation or its consumers.

## Phase-3 output adapter

The active root Gradle script imports the published Modustro Builder `coreimpl` producer registry from this bootstrap bundle. Effective `BuildOutputTypes` are resolved from artifact metadata, converted by Modustro into portable production plans, and only then mapped to Gradle/native tasks. The bundle therefore bootstraps the portable decision layer, while the Gradle script remains the execution adapter rather than the owner of BuildOutput selection semantics.
