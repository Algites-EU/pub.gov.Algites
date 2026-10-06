# Modustro Builder

`modustro/builder` contains the portable core and its separate integration adapters of the Modustro build system.

The staged migration deliberately separates the portable Builder model from the existing Gradle integration:

- `coreintf` contains public Java contracts, immutable model types, and canonical machine-readable definitions;
- `coreimpl` contains reusable Gradle-independent implementations such as inheritance resolution, model validation, and Java/Python dependency technology bridges;
- `gradleinit` is the compiled Settings/Project integration edge; `devops/build/modustrobuild` supplies its runtime dependency bundle, and shared Gradle scripts provide execution adapters.

Phase 2 connects the active Algites dependency metadata bridge to the new semantics: `DependencyKind: modustro`, merge-only `Usages`, `RequiredBuildOutputTypes`, and per-technology Java/Python mapping. Phase 3 adds `PreparedSourceSet`, the hard-wired Gradle-independent BuildOutput producer registry, portable production plans, hierarchical `BuildOutputTypes` selection, and the active Gradle adapter for Java JAR/source/Javadoc outputs and Python wheel/sdist outputs.

The package namespace is `eu.algites.pltf.modustro.builder`.

## Phase 4 capability planning

Phase 4 adds the Gradle-independent capability demand graph and TechnologyKind-specific capability configuration contracts. Build-output production plans declare required capabilities; the built-in planner deduplicates them and expands prerequisites. Phase 4A published that portable model as a bootstrap stage. Phase 4B activates it in the Gradle adapter and documentation generation: dependency-resolution preflight is demand-driven, source-native processing has one shared lifecycle boundary per TechnologyKind/artifact, and native documentation expands through the same prerequisite graph.

## Phase 5 input and output I/O model

Phase 5 uses two explicit and symmetric external-I/O concepts. `InputSubscriptions` describes external inputs consumed
by builds; `OutputPublications` describes outputs published by builds. Every declaration carries one explicit
`TechnologyKind`. The former generalized endpoint action matrix is removed.

Subscriptions merge by stable `Id` inside an expanded `TechnologyKind + InputSelector` scope. Publication branches
merge by `TechnologyKind + OutputSelector`, then by stable endpoint/publication ids and recursive publication
finalization ids. Virtual selectors are expanded before effective configuration is consumed.

Publication execution has four finalization boundaries. `PublicationFinalizationActions` is recursive, with child
`FinalizationActions`; output, artifact and Version Scope finalization lists are flat. Root `ExecutionOrder` and local
`ExecutionOrder` wait for direct attempts in the previous group, while higher boundaries await complete lower result trees.
Publication action contexts expose ancestor lineage with one input URI and one optional output URI per step.
Higher contexts expose immutable results for their whole boundary.

The implicit root `build-record` action uses `PublicationFinalizationActionAdapter`. Corresponding-snapshot cleanup
runs once at Version Scope finalization. Docs refresh is requested there and deduplicated at repository publication.
The unified adapter catalog covers subscriptions, transports and all four finalization levels.
See [PUBLICATIONS.md](../PUBLICATIONS.md) for configuration, failure semantics and current integration limits.

## Phase 5.1 definition-driven generated sources

Phase 5.1A connects the reusable `pub.tool.General` Defs Codegen API to the Builder `source_native_processing` lifecycle.
Canonical definitions are discovered automatically below `src/product/yamldefs`, `src/product/jsondefs`, and
`src/product/xmldefs`; SourceKind, package/module namespace, and Java/Python targets are derived from source placement
and TechnologyKinds rather than enumerated in artifact metadata. Generated sources are written to canonical `.gen`
source roots and are reproducible build state rather than handwritten source.

Structured-data loading remains separate from effective-model construction. The metadata resolver normalizes canonical
`InputSubscriptions` and `OutputPublications`, while Builder Core owns publication planning, finalization
execution, scheduling, retry/failure policy, URI/lineage semantics, and adapter contracts. Settings-level Java repository
registration is a bootstrap adapter over normalized input subscriptions.

## Phase 6 publication layer

The Phase-6 portable core separates publication planning from provider execution. Global schema publication validates `GlobalPublicationPathId` at the trust boundary and maintains server-controlled draft/release metadata with monotonic draft revisions and immutable releases. Provider-specific publication is performed by PublicationAdapter implementations; generic ordering, retries, failure handling, deadlines, and progress remain scheduler responsibilities.

## Compiled Gradle initialization

`gradleinit` replaces applied Settings discovery, metadata resolver and credential
initialization scripts. Root Settings bootstrap a single artifact and activate its
Settings plugin. Public defaults are packaged in the artifact; Builder Core keeps
endpoint semantics and remains Gradle-independent. See [gradleinit](gradleinit/README.md)
for the first-publication bootstrap and metadata-only helper mode.


Compatible YAML/JSON definitions targeting one native type are merged by their
canonical contract; format-specific IDs and descriptions do not cause a source
collision, and documentation from every representation is preserved. XML
contracts use an `xmldefs` subnamespace because their element names and document
structure are independent of the YAML/JSON wire model. This also avoids silently
substituting one representation for an incompatible XML contract.

The conventions now consume `AIcCanonicalDefinitionMerger` from the Defs Codegen
CoreImpl artifact. Bootstrap/publish the updated `pub.tool.General` generator
artifacts before activating these scripts in remote CI. Local verification uses
Maven Local explicitly; credentials are resolved only at task execution.
