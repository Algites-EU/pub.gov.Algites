# Modustro Builder build-support bundle

This artifact is the dependency bundle for the Modustro Builder infrastructure.
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

Future Modustro Builder build-runtime libraries that are not related to version handling may be added here as well. The artifact path is therefore deliberately `devops/build/modustrobuild` rather than a version-specific path.

## Build-support dependency versions

The current source artifact declares its build-support dependencies in `modustro-artifact.yml` through `DependencyKind: modustro` items with `Usages: [product_api]`, so the generated Java publication exports them transitively. The bundle also exports `modustro/builder/coreimpl`; `coreimpl` in turn exports the Gradle-independent `coreintf` contract layer.

The current dependency requirement uses the exact Algites v1 snapshot version `1.0-SNAPSHOT`; repository version metadata separately uses `ReleaseLineVersion`, `Revision`, and `QualifierKind`. Changing the build-support dependency set or its versions is therefore a governed source-metadata change of this artifact.

The bootstrap remains intentionally asymmetric: the root `settings.gradle.kts` obtains the build-runtime classes once from an already-published `modustrobuild:1.0-SNAPSHOT`. Gradle propagates this classpath to project build scripts and applied Project scripts. Applied Settings scripts instead use the base Settings scope, which does not inherit the top-level Settings script classpath. The top-level Settings script therefore registers ResourceEndpoint declaration, merge, and effective-model operations on `gradle.extra`; the Settings-compatible metadata resolver invokes them using opaque declaration handles. These handles remain actual generated Builder DTOs from the same single API classloader, and all endpoint semantics stay in Core. The published `product_api` dependency graph of that bundle supplies Modustro Builder `coreimpl`/`coreintf`, Defs Codegen, naming support, and the remaining build-runtime libraries transitively. The current source artifacts are then rebuilt and republished using the repository dependency declarations. A new build-runtime API therefore has to be published in a bootstrap stage before a subsequent root/settings script revision starts importing it; this avoids a same-build self-dependency while allowing the build infrastructure to dogfood its own dependency model.

## Publication identity

With the current repository metadata the Java publication identity is derived as:

```text
GroupId:    eu.algites.tool.build
ArtifactId: pub.gov.Algites_devops.build.modustrobuild
```

The artifact version follows the version of `pub.gov.Algites`, so a published `modustrobuild` version describes the Modustro Builder build-support dependency set belonging to that governance generation.

## Intended bootstrap use

The current source artifact is published as `pub.gov.Algites_devops.build.modustrobuild`. During the bootstrap transition, the root settings script consumes the already-published external bootstrap coordinate:

```text
eu.algites.tool.build:pub.gov.Algites_devops.build.modustrobuild:<bootstrap-version>
```

After `modustrobuild` has been published once, a following activation revision can switch the bootstrap coordinate without mixing old Builder names into the new source model.

Its published API dependency graph supplies the concrete Builder, Defs Codegen, naming, versioning, and credential support required by the script. The settings-level bootstrap is the single Builder classpath owner for a Gradle build domain. Project and applied script plugins must not redeclare `modustrobuild`, `coreimpl`, or `coreintf` on independent buildscript classpaths.

The build of a new `pub.gov.Algites` generation must still use already-published bootstrap versions of every API imported during bootstrap. After a bootstrap-stage publication has made a new Builder API available, a following activation revision can safely import that API from settings/root scripts.

## Current Gradle bootstrap

Root Settings load `builder/gradleinit:1.0-SNAPSHOT` and activate its compiled
Settings plugin. The plugin exports this `modustrobuild` bundle transitively, so
Settings, Project and applied Project scripts share the same Builder classpath.
No applied Settings discovery script or registered endpoint-operation table is
needed. Every isolated build domain bootstraps independently.

The floating snapshot coordinate remains intentional during development. The
[first-publication bootstrap](../modustro/builder/gradleinit/README.md) builds the
new plugin independently before its activation in consumer CI.

## Phase-3 output adapter

The active root Gradle script imports the published Modustro Builder `coreimpl` producer registry from this bootstrap bundle. Effective `BuildOutputTypes` are resolved from artifact metadata, converted by Modustro into portable production plans, and only then mapped to Gradle/native tasks. The bundle therefore bootstraps the portable decision layer, while the Gradle script remains the execution adapter rather than the owner of BuildOutput selection semantics.

### Standalone metadata Settings template

`gradle/tool/repository/modustro-bootstrap-settings.template.gradle.kts` is the
same small bootstrap as the repository Settings. Documentation helper builds copy
it as their top-level Settings and set `modustro.gradleinit.metadataOnly=true`.
All metadata and credential initialization runs in the compiled plugin.
