# Modustro Builder Gradle initialization

`gradleinit` is the compiled Gradle integration edge. Builder `coreintf` and
`coreimpl` remain independent of Gradle. The existing metadata orchestration is
compiled here instead of being evaluated as Settings/Project script plugins;
Input-subscription and output-publication normalization still delegates portable semantics to Core.

## Version Scope dependency defaults

An omitted `VersionRequirement` on a `DependencyKind: modustro` dependency
inherits the **effective version of its declaring artifact**, but only if the
referenced artifact is found in the **same Version Scope** of the same source
repository. The metadata resolver matches the canonical local `ArtifactId`,
`VariantId` and (when supplied) `GroupId`; it does not infer versions from a
matching name in another repository. The resolved metadata contains an
explicit `VersionRequirement.Exact`, so native dependency handlers and the
published dependency metadata receive an ordinary exact version requirement.

An explicitly declared `VersionRequirement` always takes precedence, including
an explicitly null `Exact`. A missing version for a known local dependency in
another Version Scope is an error: that dependency must state its own version
requirement. Missing versions for external dependencies are **not** populated
from the consumer's Version Scope. `DependencyConstraints` continue to require
their own explicit `VersionRequirement`.

The rule applies after descriptor inheritance and also when metadata resolution
is restricted to a single artifact or subtree: the resolver can inspect the
repository's artifact identities to identify local targets outside the selected
subtree. Version-scope membership is determined by the nearest effective
version declaration, not by the directory's common parent alone.

## Consumer bootstrap

Copy the repository root `settings.gradle.kts` (also available as
`gradle/tool/repository/modustro-bootstrap-settings.template.gradle.kts`). It loads
one artifact and applies `eu.algites.pltf.modustro.builder.settings`. The artifact
exports the existing `modustrobuild` dependency bundle transitively. No endpoint
operation table, Builder imports, discovery-script URLs or independent Project
buildscript dependencies belong in consumer Settings.

Coordinate:

```text
eu.algites.pltf.modustro.builder:pub.gov.Algites_devops.build.modustro.builder.gradleinit:1.0-SNAPSHOT
```

The Settings plugin owns one `AIcModustroGradleRuntime` per Gradle build domain.
It discovers artifact projects and isolated builds, resolves descriptor metadata,
registers repositories from effective InputSubscriptions and initializes credential providers. The Project
plugin `eu.algites.pltf.modustro.builder.repository` reuses that runtime, exposes
compatibility metadata properties and registers the existing metadata CLI tasks.
Public defaults are bundled from the canonical governance defaults file during
packaging; explicit `ALGITES_REPOSITORY_*_DEFAULTS_FILE` overrides retain precedence.
No raw GitHub download is performed by the initialization plugin.

Applied Project scripts inherit the top-level Settings bootstrap classpath. No
applied Settings script is needed. Isolated build roots use the same small
Settings bootstrap and construct their own runtime; live Builder objects are
never handed across included-build boundaries.

## First publication

**Publish this artifact before activating the new root Settings in CI.** Its
first build must not load the consumer Settings that already require gradleinit.
Publish CoreIntf and CoreImpl from the same checkpoint first: this integration
uses their current publication-domain API. The accompanying bootstrap archive
contains freshly rebuilt Maven artifacts and their matching POM/module metadata;
older Core JARs cannot supply the new bridge contracts.
The bootstrap script creates temporary independent Settings and builds this
module against the already-published `modustrobuild` bundle. It does not rewrite
the repository Settings and does not apply repository conventions to itself.
Use JDK 17 and the repository's Gradle 9.2.1 wrapper:

```bash
bash devops/build/modustro/builder/gradleinit/bootstrap.sh \
  :gradleinit:assemble :gradleinit:test :gradleinit:validatePlugins \
  :gradleinit:generatePomFileForMavenJavaPublication
```

The JAR is under this module's `build/libs/`; the Maven POM is
`build/publications/mavenJava/pom-default.xml`. Publish the JAR **and** POM so the
Builder runtime dependencies remain transitive. Plugin marker artifacts are not
needed: consumers apply the plugin from the explicitly loaded Maven artifact.
For a Maven upload using this bootstrap build:

```bash
export MODUSTRO_GRADLEINIT_BOOTSTRAP_USERNAME='<repository username>'
export MODUSTRO_GRADLEINIT_BOOTSTRAP_PASSWORD='<repository password>'
bash devops/build/modustro/builder/gradleinit/bootstrap.sh \
  :gradleinit:publishMavenJavaPublicationToBootstrapRepository \
  -Pmodustro.gradleinit.bootstrapRepositoryUrl='<Maven upload URL>'
```

Without arguments the bootstrap script publishes to Maven Local. To consume that
local bootstrap, explicitly enable `-Pmodustro.useMavenLocalForResolution=true`
or `MODUSTRO_USE_MAVEN_LOCAL_FOR_RESOLUTION=true`. Maven Local is otherwise off.
`MODUSTRO_GRADLE_EXECUTABLE` optionally selects another Gradle executable, and
`modustro.gradleinit.version` optionally overrides the bootstrap publication version.

## Metadata helper builds

Temporary helper builds copy the same Settings bootstrap and set
`modustro.gradleinit.metadataOnly=true` in their `gradle.properties`. This installs
compiled metadata/credential services and the shared Builder classpath without
requiring a source-repository descriptor in the helper directory. The universal
documentation workflow uses this mode.

## Validation

TestNG/Gradle TestKit integration tests exercise the actual top-level buildscript
bootstrap, Settings/Project/applied-Project class identity, subscription/publication inheritance,
project discovery, metadata-only helper initialization and isolated-build inheritance with domain-local Gradle paths. Gradle plugin validation
checks the compiled adapter. Core classes are not shaded or copied into the plugin.


Native Java/Python, documentation and schema publication tasks now pass only
serializable payload paths, coordinates, publication plans and credential-profile
metadata to compiled task classes. A shared invocation-scoped BuildService owns
the Core scheduler and resolves credentials at execution, keeping secrets and
live Project/script objects out of configuration cache. It preserves required
completion barriers and awaits best-effort attempts before teardown. A TestKit
regression publishes a local payload on two invocations with cache reuse.

Publication tasks depend on a preparation task that declares all expected native
outputs through serializable providers. The service includes missing declared
outputs in failed scope records, drains complete output trees, and then finalizes
artifacts and Version Scopes. Successful scope finalizers can request one
repository docs-site refresh. Native publication records retain the full lower
execution tree and protect COMPLETE records within one invocation.

For isolated domains, `modustroExportPublicationResults` commits complete local
artifact trees. `modustroFinalizePublicationScopes` waits for included-domain
receipts and finalizes each shared Version Scope at its owning domain. Plain JSON
packets reconstruct immutable Core values without sharing live Gradle objects.
Missing producers and stale or contradictory packets prevent COMPLETE. Resolved
credential values are excluded; finalizer credentials are resolved at execution
against the originating domain's directory.

The phase controller supplies a shared `MODUSTRO_BUILD_INVOCATION_ID` across its
child and parent builds. Direct composite publication needs a fresh common ID
through that environment variable or `-Pmodustro.build.invocationId=<fresh-id>`.
Native publication fails before upload if it is missing. Committed packets and
COMPLETE records cannot be replaced within one invocation; a new native attempt
requires a new ID. The repository-root domain alone publishes the aggregate docs
site after successful scope finalization.

Effective metadata is obtained through a Gradle ValueSource. Generated output
directories do not invalidate configuration cache when their appearance leaves
the effective metadata unchanged; descriptor changes remain observable.

TestKit checks include a real root/included-build publication, mixed Java/Python
output identities, a missing included producer, a missing shared invocation ID,
and configuration-cache reuse. Consult the checkpoint validation report for the
executed checks and any remaining bootstrap limitations.
