# Modustro Builder Architecture Specification

## 1. Status and scope

This specification defines the staged replacement architecture for the Algites build model under **Modustro Builder**.

Phase 1 established the portable contracts. Phase 2 connected dependency authoring and resolution to those semantics. Phase 3 adds concrete BuildOutputType producers, `PreparedSourceSet`, output selection, and an active Gradle adapter that consumes portable production plans. Modustro `coreintf` and `coreimpl` remain independent of the Gradle API; Gradle stays at the execution/integration edge.

The portable implementation lives under:

```text
devops/build/modustro/builder/coreintf
devops/build/modustro/builder/coreimpl
```

with Java packages below `eu.algites.pltf.modustro.builder`. Neither artifact may depend on the Gradle API.

## 2. Hierarchical inheritance

Scalar properties distinguish three states:

- property absent: inherit the parent value;
- property explicitly `null`: clear the inherited value;
- property containing a value: override the inherited value.

Keyed item collections use `ItemsInheritancePolicy`:

- `merge_missing_items` keeps inherited items that are not locally re-declared;
- `remove_missing_items` removes inherited items absent from the local collection;
- a local item with the same identity as an inherited item is recursively merged under either policy.

Some collections are explicitly **merge-only**. Dependency `Usages` and `RequiredBuildOutputTypes` are merge-only because a descendant declaration must not silently remove a requirement inherited from an ancestor.

## 3. TechnologyKinds

A logical artifact may declare one or more TechnologyKinds. YAML/JSON allow a compact form:

```yaml
TechnologyKinds:
  - java
  - python
```

and an extended form:

```yaml
TechnologyKinds:
  Items:
    - TechnologyKind: java
      BuildOutputTypes:
        Items:
          - BuildOutputType: java_classes_jar
          - BuildOutputType: java_sources_jar
```

Omitted `BuildOutputTypes` first inherit through the structural hierarchy. Built-in defaults are applied only when the effective hierarchy does not provide output selections for that TechnologyKind.

A TechnologyKind definition contains:

- supported capabilities;
- supported BuildOutputTypes;
- `DefaultBuildOutputTypes`;
- `DefaultDependencyOutputTypes`.

The two default sets are independent.

## 4. BuildOutputTypes

Every BuildOutputType declares:

- `CanBeProduced`: whether the output can be requested as a build product;
- `CanBeUsedInDependency`: whether a dependency may request that output;
- optional `DependencyOutputAlternatives` for a virtual dependency-only output;
- optional type-specific configuration schema.

A type is invalid when both booleans are false.

A BuildOutputType with `DependencyOutputAlternatives` is virtual: it must be non-producible and dependency-usable. Every alternative must be a concrete producible dependency output of the same TechnologyKind.

Initial contracts are:

```text
java_classes_jar   produced=yes  dependency=yes
java_sources_jar   produced=yes  dependency=yes
java_javadoc_jar   produced=yes  dependency=yes

python_wheel        produced=yes  dependency=yes
python_sdist        produced=yes  dependency=yes
python_distribution produced=no   dependency=yes
                    alternatives=[python_wheel, python_sdist]

modustro_docs_site  produced=yes  dependency=no
schema_site         produced=yes  dependency=no
```

Java defaults:

```text
DefaultBuildOutputTypes      = [java_classes_jar, java_sources_jar]
DefaultDependencyOutputTypes = [java_classes_jar]
```

Python defaults:

```text
DefaultBuildOutputTypes      = [python_wheel, python_sdist]
DefaultDependencyOutputTypes = [python_distribution]
```

`python_distribution` expresses dependency-consumption OR semantics. The Python handler may prefer a wheel and fall back to an sdist according to the native resolver and platform context.

## 5. Capabilities

Capability is a property of a TechnologyKind and is distinct from BuildOutputType. A capability states what a technology implementation can do; an output type identifies a concrete or virtual output contract.

Initial capability identities are reserved as follows:

```text
java/python:
  source_native_processing
  dependency_resolution
  generation_of_native_documentation

modustro:
  publication_of_global_schemas
  publication_of_docs_site
  docs_site_content
```

`publication_of_docs_site` is repository-scoped. `publication_of_global_schemas` and `docs_site_content` may be configured at repository, artifact-set, or artifact scope.

A capability definition may reference a TechnologyKind-specific configuration schema. The configuration item itself remains flat; a generic `Configuration` wrapper is not required. Linked semantic validation selects the schema using `(TechnologyKind, Capability)`.

Phase 4 defines the first TechnologyKind-specific capability configuration contracts. Java native documentation uses a Javadoc-oriented configuration, Python native documentation uses a Sphinx-oriented configuration, and Modustro defines configurations for docs-site content/publication and global schema publication. The contracts are representation-specific canonical definitions in `coreintf`; a `CapabilityDefinition.ConfigurationSchemaId` identifies the logical configuration contract independently of its YAML/JSON/XML representation.

## 6. Dependency identity and usages

Dependency and DependencyConstraint identity contains at least:

```text
DependencyKind + GroupId + ArtifactId + VariantId
```

`VariantId` is therefore part of identity. Required output types are merge-only requirements on that identity rather than an independent dependency identity.

Portable usages are:

```text
product_api
product_implementation
product_compile_only
product_compile_only_api
product_runtime_only
product_annotation_processor
develop_implementation
develop_compile_only
develop_runtime_only
develop_annotation_processor
```

They form a merge-only set rather than one scalar Usage.

Dependency and DependencyConstraint collections are grouped by `DependencyKind`. Each group has `ItemsInheritancePolicy: merge_missing_items | remove_missing_items`; the default is `merge_missing_items`. This policy controls dependency membership only. For a same-identity dependency, `Usages` and `RequiredBuildOutputTypes` always merge, regardless of the group membership policy. Explicit empty `Items` is permitted.

The Java adapter maps these to standard Java/Java-Library Gradle configurations. The Python adapter intentionally performs a lossy mapping:

- `product_api`, `product_implementation`, and `product_runtime_only` become normal published/runtime Python package dependencies;
- `product_compile_only` maps to the Modustro build/source-processing role and is not published as a runtime requirement; Phase 2 resolves this role but its dedicated source-processing environment is materialized by later source-processing phases;
- `product_compile_only_api` has the same Python execution effect and produces a diagnostic because Python has no exported compile-only API equivalent;
- annotation-processor usages are initially diagnostic/no-op for Python until a Python processing hook is implemented;
- `develop_*` usages become development/build-environment dependencies and are not published as runtime package requirements.

## 7. Version requirements

`Exact` is a hard exact constraint. The Phase-2 Java bridge maps it to native strict Gradle semantics and the Python bridge maps it to `==`.

`Minimum` is a hard lower bound. `Maximum` may be strict or non-strict according to `MaximumStrict`. `Exclude` is a hard inherited collection (`Items` plus `ItemsInheritancePolicy`); explicit `Exclude: null` clears it. `Prefer` is only a preference.

One source declaration must not combine non-null `Exact` with non-null `Minimum`, `Maximum`, `MaximumStrict`, `Exclude`, or `Prefer`; canonical schemas enforce this. Hierarchical resolution may nevertheless retain an inherited `Prefer` next to a more specific `Exact`. In that case the preference is ignored and an informational diagnostic should identify the source of the winning exact requirement.

Conflicts between hard requirements are resolution failures only when the requirements participate simultaneously in the same native dependency graph. A child overriding an ancestor's direct `Exact` value is normal metadata inheritance and does not itself constitute a dependency conflict.

## 7.1 Phase-2 dependency technology bridge

`coreintf` defines a Gradle-independent `AIiDependencyTechnologyHandler` contract and a portable dependency technology resolution plan. `coreimpl` supplies built-in Java and Python handlers. A handler receives already-resolved dependency/constraint definitions and a TechnologyKind definition, applies `DefaultDependencyOutputTypes` when needed, validates that requested outputs are dependency-consumable, maps portable usages to technology-native logical roles, and emits non-fatal diagnostics for lossy mappings.

The Java handler exposes standard Gradle configuration **names** only; it imports no Gradle API. The current `modustro-root-build.gradle.kts` adapter creates/configures the actual Gradle dependencies and configurations. The Python handler exposes logical roles (`package_runtime`, `build_source_processing`, `development`, `no_op`); the current execution adapter continues to use pip for graph preflight. Phase 2 does not yet materialize a distinct Python source-processing environment; that execution concern follows with source processing/output production.

`RequiredBuildOutputTypes` remains a dependency-consumption concern and uses `DefaultDependencyOutputTypes` when omitted. Build production is independent: Phase 3B now resolves effective `BuildOutputTypes` per TechnologyKind and materializes the selected concrete outputs through the producer registry. Virtual dependency-only outputs such as `python_distribution` cannot be requested as build products.

## 8. Build preparation and producer architecture

Phase 3 introduces a technology-neutral internal `PreparedSourceSet` containing applicable declared native sources, generated sources, and resource roots. Source-native processing remains a capability/demand-graph concern for Phase 4; Phase 3 defines the portable handoff consumed by output producers.

Conceptually:

```text
native sources
    -> source_native_processing
    -> PreparedSourceSet
```

For Java:

```text
PreparedSourceSet + compile dependency graph
    -> compilation
    -> compiled classes/resources
    -> java_classes_jar

PreparedSourceSet
    -> java_sources_jar

PreparedSourceSet + documentation classpath
    -> generation_of_native_documentation
    -> java_javadoc_jar and/or modustro_docs_site input
```

For Python:

```text
PreparedSourceSet + package metadata -> python_wheel / python_sdist
PreparedSourceSet + resolved runtime environment -> future deployment-package outputs
```

Phase 3A introduces a hard-wired Gradle-independent producer registry in `coreimpl`. A producer is selected by `(TechnologyKind, BuildOutputType)` and returns a portable production plan containing the prepared source set, a built-in production primitive, and the additional logical inputs required by that primitive. The initial producers cover Java classes/source/Javadoc JARs and Python wheel/sdist. Virtual `python_distribution` remains dependency-only and has no direct producer.

Phase 3A deliberately left the active Gradle orchestration unchanged so the new `coreintf`/`coreimpl` binaries could be published without a bootstrap cycle. Phase 3B activates the model: the metadata resolver computes effective `BuildOutputTypes`, `coreimpl` creates portable production plans, and the Gradle adapter maps those plans to native execution primitives. The long-term architecture associates a BuildOutputType with a producer capability/profile and optional supporting capabilities rather than embedding implementation-artifact coordinates directly in the BuildOutputType definition.


### Phase-3B active output-selection semantics

`TechnologyKinds` may use the compact list form when no technology-specific output override is required. The extended form selects BuildOutputTypes per TechnologyKind and participates in normal hierarchy inheritance:

```yaml
TechnologyKinds:
  Items:
    - TechnologyKind: java
      BuildOutputTypes:
        Items:
          - BuildOutputType: java_classes_jar
          - BuildOutputType: java_sources_jar
```

`TechnologyKinds.ItemsInheritancePolicy` controls TechnologyKind membership. Within one same-identity TechnologyKind, `BuildOutputTypes.ItemsInheritancePolicy` controls output membership. In both collections, `merge_missing_items` is the default; `remove_missing_items` removes inherited members that are not present locally. Omitting `BuildOutputTypes` inherits an ancestor selection if one exists; only when the effective hierarchy contains no selection does the built-in `DefaultBuildOutputTypes` set apply. An explicit empty `BuildOutputTypes.Items: []` therefore means that the TechnologyKind is retained but no build output is requested for it.

The active Phase-3B adapter currently maps production plans as follows:

- `java_classes_jar` -> Gradle `jar`;
- `java_sources_jar` -> Gradle `sourcesJar`;
- `java_javadoc_jar` -> Gradle `javadocJar`;
- `python_wheel` -> `python -m build --wheel`;
- `python_sdist` -> `python -m build --sdist`;
- the default Python selection requests both wheel and sdist in one native build staging flow.

`modustroBuild` still performs the normal verification/test lifecycle for an effective Java TechnologyKind, but output packaging is now driven by the selected production plans rather than by unconditional Gradle packaging defaults. Phase 4 retains the hard-wired producer registry for producer selection but adds portable capability requirements to every production plan and introduces the capability demand DAG. The planner deduplicates common demands and expands capability prerequisites. Phase 4A published the portable graph/model first as a bootstrap stage. Phase 4B activates the DAG in the Gradle adapter and documentation generation.


### Phase-4 capability demand graph

Every concrete `AIcBuildOutputProductionPlan` carries the capability IDs required by that producer. Initial mappings are:

- `java_classes_jar` -> `source_native_processing`, `dependency_resolution`;
- `java_sources_jar` -> `source_native_processing`;
- `java_javadoc_jar` -> `generation_of_native_documentation`;
- `python_wheel` -> `source_native_processing`, `dependency_resolution`;
- `python_sdist` -> `source_native_processing`.

`generation_of_native_documentation` has built-in prerequisites `source_native_processing` and `dependency_resolution` for Java and Python. `AIcBuiltinCapabilityDemandPlanner` expands these prerequisites and deduplicates demands by `(TechnologyKind, Capability, Scope, ScopeIdentity)`. The resulting graph is a portable DAG and has no Gradle dependency. Additional repository/artifact-set/artifact demands such as `publication_of_docs_site`, `docs_site_content`, and `publication_of_global_schemas` can be added to the same graph.

Phase 4A was the bootstrap stage in which the graph/model and configuration schemas were published without changing active orchestration. Phase 4B consumes the graph in the Gradle adapter. Build-output demands now control whether technology dependency-resolution preflight is required, and `source_native_processing` is materialized as one lifecycle boundary per TechnologyKind/artifact. The built-in Python implementation also owns the standard resource-to-package transformation: `jsondefs`, `yamldefs`, `xmldefs`, and `config` product roots are staged beneath the derived artifact import namespace in the disposable run workspace, so individual artifacts must not implement equivalent Gradle copy tasks. Documentation generation adds an explicit `generation_of_native_documentation` demand; the planner expands it to `source_native_processing` and `dependency_resolution`, and Gradle task dependency deduplication ensures that shared prerequisites execute once even when multiple outputs or documentation consumers require them. The documentation site itself is represented by repository-scoped `publication_of_docs_site` and scoped `docs_site_content` demands. Global-schema publication is activated in Phase 6 through the same capability/ResourceEndpoint model.

## 9. Phase-5 generalized ResourceEndpoints

Phase 5 replaces the native-package-only repository matrix as the canonical endpoint model with generalized **ResourceEndpoints**. The endpoint selection key has four structural dimensions:

```text
TechnologyKind / ResourceKind / Visibility / Action
```

`Stability` is deliberately not a fifth mandatory matrix dimension. It is endpoint data whose presence is governed by the selected ResourceKind. This lets one endpoint model serve both native package repositories and publication resources whose lifecycle is not meaningfully split into release/snapshot channels.

The built-in output/resource kinds are:

| ResourceKind | TechnologyKinds | Stability | Role |
| --- | --- | --- | --- |
| `native_binary_output` | `java`, `python`, `mps` | required (`release` / `snapshot`) | native binary packages and dependency resources |
| `native_source_output` | `java`, `python`, `mps` | required (`release` / `snapshot`) | native source packages |
| `native_documentation_output` | `java`, `python`, `mps` | required (`release` / `snapshot`) | technology-native documentation packages |
| `modustro_docs_site` | `modustro` | required (`release` / `snapshot`) | aggregate Modustro documentation site |
| `schema_site` | `modustro` | forbidden as a ResourceEndpoint property | globally published canonical schemas |

Concrete build outputs map to these publishing kinds independently of the build producer: `java_classes_jar` and `python_wheel` map to `native_binary_output`; `java_sources_jar` and `python_sdist` map to `native_source_output`; `java_javadoc_jar` maps to `native_documentation_output`. The mapping belongs to Builder core and is not encoded in individual artifact descriptors.

A canonical endpoint declaration therefore has the shape:

```yaml
ResourceEndpoints:
  java:
    native_binary_output:
      public:
        upload:
          - Id: algites-java-native-build-output-public-snapshot-upload
            Url: https://example.invalid/maven/
            Stability: snapshot
            CredentialProfile: algites-java-public-snapshot-upload
```

A schema-site endpoint intentionally omits Stability:

```yaml
ResourceEndpoints:
  modustro:
    schema_site:
      public:
        upload:
          - Id: algites-modustro-schema-site-public-upload
            Url: https://example.invalid/schema-site/
```

Each endpoint may define `Id`, `Url`, `CredentialProfile`, `Enabled`, `Stability` when permitted/required by its ResourceKind, and optional `ResourceEndpointProviderAdapter`. `Enabled` defaults to true. Inheritance merges endpoints by `Id` inside the same four-dimensional cell, so a descendant can change one property or disable an inherited endpoint without copying the remaining endpoint definition.

`ResourceEndpointProviderAdapter` is the generalized name for provider-specific **resource access/management** behavior. The TechnologyKind/ResourceKind/Action combination owns default protocol behavior; a provider adapter is used only when a concrete resource provider requires behavior outside that default contract. Publishing is no longer modeled as a ResourceEndpoint specialization in Phase 5.2. Publication targets use `PublishingEndpoints` and their separate `PublishingAdapter` contract, so generic publishing order/retry/failure/timeout/progress semantics do not leak into download or management endpoints.

The source-repository visibility policy is preserved while resource access and publishing use separate endpoint models:

- public source repositories may consume only public ResourceEndpoints; their publication targets come only from public-governance PublishingEndpoints;
- private source repositories may consume public and private ResourceEndpoints; publication targets for their own outputs come from authorized private PublishingEndpoint overlays.

Phase 5.1 removes the former repository-matrix input and its compatibility projection. Phase 5.2 keeps `ResourceEndpoints` as the generalized resource-resolution/management representation and uses `PublishingEndpoints` exclusively for publishing.

Phase 5.2 makes canonical definition code generation convention-driven at `source_native_processing`. The Gradle adapter discovers canonical product definition roots, derives SourceKind/package/targets, calls the reusable Defs Codegen Java API directly, and writes reproducible output to `.gen` source roots. Generated transport/data types remain distinct from handwritten effective Builder models.

Phase 5.2 also separates **publishing configuration** from the generalized ResourceEndpoint catalog. Publication policy is declared directly under each publishing output kind and then under `Snapshot` / `Release`. Each branch carries `PublishingEnabled` plus `PublishingEndpoints`; endpoint arrays merge hierarchically by stable `Id`, while individual endpoint properties inherit independently. The effective endpoint defaults are `Enabled=true`, `PublishingOrder=0`, `PublishingFailurePolicy=FAIL_BUILD_ON_PUBLISHING_FAILURE`, `PublishingRetryCount=0`, `PublishingRetryDelayMillis=1000`, and `ShowPublishingProgressIfPossible=true`. Negative publishing orders are valid. `PublishingAttemptTimeoutMillis` is optional and, when present, must be positive.

Snapshot invocations expose five independent `DEFAULT` / `FORCE_ON` / `FORCE_OFF` overrides for `native_binary_output`, `native_source_output`, `native_documentation_output`, `modustro_docs_site`, and `schema_site`. Overrides affect only the branch-level `PublishingEnabled`; they never rewrite endpoint `Enabled`. Release publishing is descriptor-only and rejects a non-default portable override. Publishing enablement is intentionally independent from code generation, compilation, verification, and packaging.

Phase 5.1B completes the ResourceEndpoint transport/effective-model split. Generated `AIcgdResourceEndpoint_1` values represent precedence-ordered declarations and therefore permit inherited amendment fields such as `Url`, `Enabled`, and `Stability` to remain absent. Builder Core merge-composes declarations by the four-dimensional cell plus `Id`, applies defaults only after inheritance, validates the effective ResourceKind contract, and exposes immutable typed selection through `AIcResourceEndpointCatalog`. Structured-data loaders perform YAML/JSON/XML representation mapping only; they do not apply inheritance or endpoint semantics. The Jackson implementation is packaged as the separate optional `builder/structureddata/jackson` artifact so the Builder core and Algites bootstrap path do not acquire Jackson transitively.

`PublicationDestinations` in a publication capability configuration denotes an optional set of **PublishingEndpoint IDs** for the corresponding output kind and stability branch. It never contains URLs, credentials, or provider-specific state. When omitted, all enabled effective PublishingEndpoints of that output/stability branch participate. This is an optional selection/filter only; inheritance, endpoint enablement, runtime Snapshot overrides, ordering, retries, failure handling, attempt timeouts, cancellation, and progress remain properties of the Builder publishing model and scheduler.

Resource-endpoint and publishing-endpoint credential materialization remain separate from both endpoint models. Descriptors contain only credential-profile references; secret values continue to be resolved by the provider-independent credential document and trusted CI bridge at execution time.

### Phase-5.2 publishing scheduler

The generic `AIcPublishingScheduler` owns publication orchestration. A PublishingAdapter executes exactly one attempt and never implements a second generic retry/order scheduler. Every attempt receives the effective endpoint, attempt number, configured timeout/deadline, cooperative cancellation token, materialized credentials, and a Builder-neutral progress reporter. Adapters may report determinate progress (`completed`, `total`, `unit`) or indeterminate/status updates. `ShowPublishingProgressIfPossible=false` suppresses continuous progress while preserving final success/failure diagnostics.

`PublishingOrder` is a **start barrier**. Lower values start first and negative values are valid. Endpoints with the same effective order are eligible for parallel execution. Before a higher order starts, the scheduler waits only for endpoints whose failure policy is `FAIL_BUILD_ON_PUBLISHING_FAILURE`. An `IGNORE_PUBLISHING_FAILURE` endpoint may continue in the background across later publishing groups and other build-session work. Phase 5.2 intentionally keeps such background activity owned by the current Gradle/build session; it is not detached work that survives a completed Gradle process.

Retries are counted after the initial attempt (`PublishingRetryCount=0` therefore means one total attempt). The scheduler rejects `PublishingRetryCount>0` for an adapter that does not declare automatic retry safety. Timeout enforcement is dual: the scheduler enforces the external deadline/cancellation boundary, while the adapter also receives the deadline so it can configure native I/O/process timeouts and cooperate with cancellation.

### Phase-5.2 Gradle build domains and global phase barriers

`NestedGradleSettingsBuildPolicy` is hierarchical and has two values: `IGNORE_NESTED_SETTINGS` and `USE_ISOLATED_BUILD_ON_NESTED_SETTINGS`. With `IGNORE_NESTED_SETTINGS`, a nested `settings.gradle(.kts)` does not create a Modustro boundary and discovery continues in the current Gradle domain. With `USE_ISOLATED_BUILD_ON_NESTED_SETTINGS`, discovery stops at that nested settings root and registers it as an included isolated build. The child domain reconstructs effective Modustro state in its own classloader by locating the single ancestor `modustro-source-repository.yml`, loading the descriptor ancestry from that source-repository root to its local Gradle build root, and resolving configuration independently. Live Builder/JVM domain objects never cross that build boundary.

A source-repository infrastructure has exactly one relevant `modustro-source-repository.yml`. Absence is an error; conflicting/multiple relevant roots are an error. The source-repository root is a Modustro concept and need not be the VCS root.

The central phase controller executes the build-domain tree horizontally. The canonical order is:

```text
RESOLVE -> PREPARE -> COMPILE -> VERIFY -> PACKAGE -> PUBLISH
```

Each phase is a separate Gradle invocation. The phase task in the current domain depends on the same phase task in every included isolated child domain. Successful return from that invocation is therefore the required global barrier before the controller starts the next phase. If any domain fails a required phase, the controller stops immediately and no later phase starts. In particular, `PUBLISH` cannot begin until `PACKAGE` has succeeded for every relevant domain.

The portable Java controller is `AIcGradlePhaseController`; `gradle/tool/repository/modustro-phase-controller.sh` is the command-line/CI entry point. The shared public CI workflow auto-selects the phase controller for repositories that contain `modustro-source-repository.yml`, while not-yet-migrated repositories retain the previous task path until their individual migration.

### Phase-5.2 remote publication consistency boundary

Remote publication is deliberately **not globally transactional** in Phase 5.2. There is no repository-wide remote staging transaction, rollback protocol, or all-or-nothing commit across endpoints/artifacts. A concrete attempt may partially mutate a remote system before failing, just as an ordinary Maven publication can expose one uploaded file before a later file fails. Retry safety is therefore an adapter contract rather than an assumption.

The guarantee provided by the phase controller is the useful boundary: every mandatory resolve/prepare/compile/verify/package operation across all relevant build domains completes successfully before any publishing phase starts. Once publication starts, failures are handled according to each PublishingEndpoint failure policy and retry contract; already completed remote side effects are not rolled back globally.

## 10. Canonical definitions and global publication metadata

`coreintf` publishes representation-specific canonical definitions below `yamldefs`, `jsondefs`, and `xmldefs` source roots. The representations model the same logical contract but MAY differ where the target representation or its schema technology supports different constraints. Versioned definition files use `_1` and also contain an internal definition version.

When YAML or JSON definitions are expressed as JSON Schema, the target representation is retained in the logical filename: `<name>_<version>.yamldef.schema.json` below `yamldefs` and `<name>_<version>.jsondef.schema.json` below `jsondefs`. XSD definitions retain the normal `<name>_<version>.xsd` form because the `.xsd` extension already identifies the XML representation. Representation-specific JSON Schema resources MUST also use distinct `$id` values; two representations that happen to have identical schema contents are still independent contracts and may diverge later.

Every canonical definition created by Modustro Builder carries a `<definition-file>.meta.yml` **user sidecar** described by `global-publication-user-metadata_1`. The source-side business content is intentionally limited to:

```yaml
GlobalPublicationPathId: ...
```

`GlobalPublicationPathId` is deliberately path-oriented: authors should choose a stable identifier that maps transparently to the global publication path rather than an opaque implementation identifier. The source contract is strict: publication state, deployment revision, timestamps, publisher identity, and other deployment-owned values are invalid when supplied by an author.

The user sidecar is only input to publication. At the publication trust boundary the deployment adapter validates it, reads trusted deployment state, and creates a separate `global-publication-deploy-metadata_1` sidecar. The deploy contract contains `GlobalPublicationPathId`, server-controlled `PublicationState` (`draft` or `release`), `PublicationRevision`, `FirstPublishedAt`, `PublishedAt`, optional `ReleasedAt`, and optional `PublishedBy`. Draft replacement increments `PublicationRevision`; release freezes the deployed definition at its current content revision. A released path is immutable and cannot transition back to draft. `PublishedBy`, when available, is derived exclusively from authenticated publisher/CI execution context and must never be accepted from source metadata.

This split is a trust-boundary rule, not merely a serialization preference: a publisher must parse and validate user metadata, discard the input representation, and construct deploy metadata from validated author input plus server-controlled state. It must not copy arbitrary fields from the source sidecar and then append its own fields.

Phase 6 makes a valid user sidecar mandatory for every selected global-schema publication source. Automatic user-sidecar generation will be an explicit task; an ordinary build will validate rather than mutate source metadata. Deploy sidecars are generated only by publication/deployment infrastructure and are not committed to the source repository.

## 11. Planned continuation

The staged continuation is:

1. portable model foundation (complete);
2. dependency model and Java/Python native-resolution bridges (complete);
3. concrete BuildOutputType producers, `PreparedSourceSet`, effective output selection, and Gradle adapter activation (complete);
4. technology capabilities and demand-driven build graph (complete);
5. generalized ResourceEndpoints and publication/deployment infrastructure (complete);
6. `modustro_docs_site` and `schema_site` generation/publication;
7. additional producers, including `python_aws_lambda_zip`, and gradual removal of business logic from Gradle scripts;
8. later producer/plugin discovery through AAC capabilities/providers.

The AAC integration phase is intentionally later. Modustro Builder Core must remain usable without Gradle and without AAC during the staged migration.


### Common root-document schema metadata

YAML and JSON root documents may carry an optional `$schema` URI as technical document metadata. The metadata is not part of the business model. The common contracts are published as:

```text
https://defs.dev.algites.eu/api/yamldefs/eu/algites/pltf/modustro/builder/common/document-common-metadata_1.yamldef.schema.json
https://defs.dev.algites.eu/api/jsondefs/eu/algites/pltf/modustro/builder/common/document-common-metadata_1.jsondef.schema.json
```

Object-root schemas compose the matching common contract. Schemas that are embedded by another contract expose and use `#EmbeddedContent` so `$schema` remains a root-document concern. Array/value-only contracts are not forced into an object wrapper.

Generated YAML root documents carry all three interoperable forms with the same URI:

```yaml
# yaml-language-server: $schema=<URI>
# $schema: <URI>
$schema: <URI>
```

Strict validation that these three values are identical and equal to the publication base URL plus the definition sidecar `GlobalPublicationPathId` is intentionally deferred until global definition deployment is operational.

`GlobalPublicationPathId` is always the logical/package-relative definition path below its canonical definition source root. Technical source-root segments and synthetic representation directories are not inserted. The publication endpoint provides `/api/yamldefs/`, `/api/jsondefs/`, or `/api/xmldefs/` separately.
