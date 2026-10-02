# Modustro Builder

`modustro/builder` contains the Gradle-independent core model and implementation of the Modustro build system.

The staged migration deliberately separates the portable Builder model from the existing Gradle integration:

- `coreintf` contains public Java contracts, immutable model types, and canonical machine-readable definitions;
- `coreimpl` contains reusable Gradle-independent implementations such as inheritance resolution, model validation, and Java/Python dependency technology bridges;
- `devops/build/algitesbuild` and shared Gradle scripts remain the current execution/integration edge.

Phase 2 connects the active Algites dependency metadata bridge to the new semantics: `DependencyKind: modustro`, merge-only `Usages`, `RequiredBuildOutputTypes`, and per-technology Java/Python mapping. Phase 3 adds `PreparedSourceSet`, the hard-wired Gradle-independent BuildOutput producer registry, portable production plans, hierarchical `BuildOutputTypes` selection, and the active Gradle adapter for Java JAR/source/Javadoc outputs and Python wheel/sdist outputs.

The package namespace is `eu.algites.pltf.modustro.builder`.

## Phase 4 capability planning

Phase 4 adds the Gradle-independent capability demand graph and TechnologyKind-specific capability configuration contracts. Build-output production plans declare required capabilities; the built-in planner deduplicates them and expands prerequisites. Phase 4A published that portable model as a bootstrap stage. Phase 4B activates it in the Gradle adapter and documentation generation: dependency-resolution preflight is demand-driven, source-native processing has one shared lifecycle boundary per TechnologyKind/artifact, and native documentation expands through the same prerequisite graph.

## Phase 5 ResourceEndpoints

Phase 5 establishes `ResourceEndpoints` as the canonical external build/publication target model. The portable model defines ResourceKinds, endpoint actions, ResourceKind-specific stability rules, effective endpoint definitions, and validation independent of Gradle.

The canonical selection dimensions are `TechnologyKind / ResourceKind / Visibility / Action`. `Stability` is endpoint data: `native_build_output` and `docs_site` require it, while `schema_site` forbids it. Provider-specific behavior uses the generic `ResourceEndpointProviderAdapter` name.

The active Gradle adapter consumes canonical `resourceEndpoints` for Java/Python native dependency resolution, publication, credential preflight, and released-snapshot cleanup. Phase 5.1 removes the superseded repository-matrix input completely; only `ResourceEndpoints` participate in resolution. Phase 6 will activate `docs_site` and `schema_site` generation/publication on the same endpoint model.

## Phase 5.1 definition-driven generated sources

Phase 5.1A connects the reusable `pub.tool.General` Defs Codegen API to the Builder `source_native_processing` lifecycle. Artifacts may declare `Artifact.DefinitionCodeGeneration.Items`; each item selects one canonical product `yamldefs`, `jsondefs`, or `xmldefs` source below its matching source-kind root, one or more Java/Python targets, and a target package/module namespace. Generated sources are written to canonical `.gen` source roots and are reproducible build state rather than handwritten source. The first active consumer is the Builder `coreintf` ResourceEndpoint model, which generates versioned `AIcgd..._1` data objects and `AIng..._1` enums from canonical definitions.

Structured-data loading remains separate from effective-model construction. The Phase 5.1B bootstrap stage supplies the optional separate Jackson YAML/JSON/XML loader artifact plus the Gradle-independent ResourceEndpoint resolver, catalog, and normalized-metadata bridge. After this bootstrap artifact is snapshot-published, the activation stage can switch the settings metadata resolver and root Gradle adapter to those newly published classes without creating a same-build bootstrap dependency.
