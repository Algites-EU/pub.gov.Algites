# Algites build support bundle

This artifact is the dependency bundle for the Algites build infrastructure.
It provides a single published dependency through which Gradle bootstrap code can obtain the reusable libraries required by a specific generation of the Algites governance/build tooling.

The artifact intentionally owns the concrete dependency set used by the build infrastructure. Consumers should depend on this artifact instead of declaring the individual build-library dependencies themselves.

## Current dependencies

The bundle aggregates Modustro Builder core implementation, reusable Defs Codegen/naming infrastructure, and the version infrastructure from `pub.lib.General`:

```text
pub.gov.Algites/devops/build/modustro/builder/coreimpl
pub.tool.General/generators/code/defscodegen/coreintf
pub.tool.General/generators/code/defscodegen/coreimpl
pub.lib.General/naming/convention/coreimpl
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

## Build-support dependency versions

The current source artifact declares its build-support dependencies in `algites-artifact.yml` through `DependencyKind: modustro` items with `Usages: [product_api]`, so the generated Java publication exports them transitively. The bundle also exports `modustro/builder/coreimpl`; `coreimpl` in turn exports the Gradle-independent `coreintf` contract layer.

The current dependency requirement uses the exact Algites v1 snapshot version `1.0-SNAPSHOT`; repository version metadata separately uses `ReleaseLineVersion`, `Revision`, and `QualifierKind`. Changing the build-support dependency set or its versions is therefore a governed source-metadata change of this artifact.

The bootstrap remains intentionally asymmetric: the shared root build script obtains its directly imported build-runtime classes from an already-published `algitesbuild:1.0-SNAPSHOT`. The published `product_api` dependency graph of that bundle supplies Modustro Builder `coreimpl`/`coreintf`, Defs Codegen, naming support, and the remaining build-runtime libraries transitively. The current source artifacts are then rebuilt and republished using the repository dependency declarations. A new build-runtime API therefore has to be published in a bootstrap stage before a subsequent root/settings script revision starts importing it; this avoids a same-build self-dependency while allowing the build infrastructure to dogfood its own dependency model.

## Publication identity

With the current repository metadata the Java publication identity is derived as:

```text
GroupId:    eu.algites.tool.build
ArtifactId: pub.gov.Algites_devops.build.algitesbuild
```

The artifact version follows the version of `pub.gov.Algites`, so a published `algitesbuild` version describes the build-support dependency set belonging to that governance generation.

## Intended bootstrap use

The shared root Gradle build has one direct governed bootstrap dependency:

```text
eu.algites.tool.build:pub.gov.Algites_devops.build.algitesbuild:<bootstrap-version>
```

Its published API dependency graph supplies the concrete Builder, Defs Codegen, naming, versioning, and credential support required by the script. Settings-phase code that must execute before the root buildscript is available may declare the narrow already-published support artifact it needs explicitly. The following Phase 5.1B activation stage will use this for the ResourceEndpoint resolver during metadata resolution.

The build of a new `pub.gov.Algites` generation must still use already-published bootstrap versions of every API imported during bootstrap. After a bootstrap-stage publication has made a new Builder API available, a following activation revision can safely import that API from settings/root scripts.

## Current Gradle bootstrap

The shared `gradle/tool/repository/algites-root-build.gradle.kts` loads only `algitesbuild:1.0-SNAPSHOT` directly from the public Cloudsmith snapshot repository. Modustro Builder, Defs Codegen, and the Algites naming-profile implementation are supplied transitively by the published `algitesbuild` metadata.

Phase 5.1A uses Defs Codegen directly from the Gradle adapter during `source_native_processing`; no CLI process is spawned. The Phase 5.1B bootstrap stage published by this revision keeps the existing settings resolver unchanged; the following activation revision can then bootstrap the newly published Builder core narrowly for ResourceEndpoint declaration/effective-model resolution.

The floating snapshot coordinate is intentional during the current development phase. It can later be replaced by an immutable/released bootstrap coordinate without changing the dependency-model implementation or its consumers.

## Phase-3 output adapter

The active root Gradle script imports the published Modustro Builder `coreimpl` producer registry from this bootstrap bundle. Effective `BuildOutputTypes` are resolved from artifact metadata, converted by Modustro into portable production plans, and only then mapped to Gradle/native tasks. The bundle therefore bootstraps the portable decision layer, while the Gradle script remains the execution adapter rather than the owner of BuildOutput selection semantics.
