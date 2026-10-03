# Modustro Builder Gradle initialization

`gradleinit` is the compiled Gradle integration edge. Builder `coreintf` and
`coreimpl` remain independent of Gradle. The existing metadata orchestration is
compiled here instead of being evaluated as Settings/Project script plugins;
ResourceEndpoint declaration merge, defaulting and validation still call Core.

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
registers download repositories and initializes credential providers. The Project
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
bootstrap, Settings/Project/applied-Project class identity, endpoint inheritance,
project discovery, metadata-only helper initialization and isolated-build inheritance with domain-local Gradle paths. Gradle plugin validation
checks the compiled adapter. Core classes are not shaded or copied into the plugin.
