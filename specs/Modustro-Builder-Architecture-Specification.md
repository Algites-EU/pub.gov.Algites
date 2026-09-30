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

docs_site           produced=yes  dependency=no
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

Phase 4A defines the first TechnologyKind-specific capability configuration contracts. Java native documentation uses a Javadoc-oriented configuration, Python native documentation uses a Sphinx-oriented configuration, and Modustro defines configurations for docs-site content/publication and global schema publication. The contracts are representation-specific canonical definitions in `coreintf`; a `CapabilityDefinition.ConfigurationSchemaId` identifies the logical configuration contract independently of its YAML/JSON/XML representation.

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

The Java handler exposes standard Gradle configuration **names** only; it imports no Gradle API. The current `algites-root-build.gradle.kts` adapter creates/configures the actual Gradle dependencies and configurations. The Python handler exposes logical roles (`package_runtime`, `build_source_processing`, `development`, `no_op`); the current execution adapter continues to use pip for graph preflight. Phase 2 does not yet materialize a distinct Python source-processing environment; that execution concern follows with source processing/output production.

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
    -> java_javadoc_jar and/or docs_site input
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

`algitesBuild` still performs the normal verification/test lifecycle for an effective Java TechnologyKind, but output packaging is now driven by the selected production plans rather than by unconditional Gradle packaging defaults. Phase 4A retains the hard-wired producer registry for producer selection but adds portable capability requirements to every production plan and introduces the capability demand DAG. The planner deduplicates common demands and expands capability prerequisites. Phase 4B activates this DAG in the Gradle adapter and documentation generation; Phase 4A deliberately leaves the active Gradle orchestration unchanged for bootstrap publication.


### Phase-4A capability demand graph

Every concrete `AIcBuildOutputProductionPlan` carries the capability IDs required by that producer. Initial mappings are:

- `java_classes_jar` -> `source_native_processing`, `dependency_resolution`;
- `java_sources_jar` -> `source_native_processing`;
- `java_javadoc_jar` -> `generation_of_native_documentation`;
- `python_wheel` -> `source_native_processing`, `dependency_resolution`;
- `python_sdist` -> `source_native_processing`.

`generation_of_native_documentation` has built-in prerequisites `source_native_processing` and `dependency_resolution` for Java and Python. `AIcBuiltinCapabilityDemandPlanner` expands these prerequisites and deduplicates demands by `(TechnologyKind, Capability, Scope, ScopeIdentity)`. The resulting graph is a portable DAG and has no Gradle dependency. Additional repository/artifact-set/artifact demands such as `publication_of_docs_site`, `docs_site_content`, and `publication_of_global_schemas` can be added to the same graph.

Phase 4A is a bootstrap stage: the graph/model and configuration schemas are published first. Phase 4B will make the Gradle adapter consume the graph so shared prerequisites execute once even when multiple outputs or documentation consumers request them.

## 9. Canonical definitions and global publication metadata

`coreintf` publishes representation-specific canonical definitions below `yamldefs`, `jsondefs`, and `xmldefs` source roots. The representations model the same logical contract but MAY differ where the target representation or its schema technology supports different constraints. Versioned definition files use `_1` and also contain an internal definition version.

When YAML or JSON definitions are expressed as JSON Schema, the target representation is retained in the logical filename: `<name>_<version>.yamldef.schema.json` below `yamldefs` and `<name>_<version>.jsondef.schema.json` below `jsondefs`. XSD definitions retain the normal `<name>_<version>.xsd` form because the `.xsd` extension already identifies the XML representation. Representation-specific JSON Schema resources MUST also use distinct `$id` values; two representations that happen to have identical schema contents are still independent contracts and may diverge later.

Every canonical definition created by Modustro Builder carries a `<definition-file>.meta.yml` sidecar with:

```yaml
MetadataVersion: 1
DefinitionVersion: 1
GlobalPublicationPathId: ...
```

`GlobalPublicationPathId` is deliberately path-oriented: authors should choose a stable identifier that maps transparently to the global publication path rather than an opaque implementation identifier.

A later schema-site phase will make a valid sidecar mandatory for every selected publication source. Automatic sidecar generation will be an explicit task; an ordinary build will validate rather than mutate source metadata.

## 10. Planned continuation

The staged continuation is:

1. portable model foundation (complete);
2. dependency model and Java/Python native-resolution bridges (complete);
3. concrete BuildOutputType producers, `PreparedSourceSet`, effective output selection, and Gradle adapter activation (complete);
4. technology capabilities and demand-driven build graph;
5. generalized ResourceEndpoints and publication/deployment infrastructure;
6. `docs_site` and `schema_site` generation/publication;
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
