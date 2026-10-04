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

## Phase 5 ResourceEndpoints

Phase 5 establishes `ResourceEndpoints` as the canonical generalized resource-access model. The portable model defines ResourceKinds, endpoint actions, ResourceKind-specific stability rules, effective endpoint definitions, and validation independent of Gradle.

The canonical selection dimensions are `TechnologyKind / ResourceKind / Visibility / Action`. `Stability` is endpoint data. Built-in output/resource kinds are `native_binary_output`, `native_source_output`, `native_documentation_output`, `modustro_docs_site`, and `schema_site`. Provider-specific resource behavior uses the generic `ResourceEndpointProviderAdapter` name.

Phase 5.2 separates Builder publishing policy from generalized resource access. Publishing configuration is declared directly under the five output kinds, split into `Snapshot` / `Release`, and contains branch-level `PublishingEnabled` plus independently inherited `PublishingEndpoints`. Concrete build outputs are classified by the portable `AIcBuiltinPublishingOutputKindMapper`; artifact metadata never enumerates that mapping. Snapshot invocation overrides use `DEFAULT`, `FORCE_ON`, or `FORCE_OFF` and affect only branch-level publishing enablement. Release publishing remains descriptor-only.

The active dependency-resolution adapter continues to consume canonical `ResourceEndpoints`; publishing execution uses the dedicated Phase-5.2 PublishingEndpoint model and the common publishing scheduler. Phase 5.1 removes the superseded repository-matrix input completely.

## Phase 5.1 definition-driven generated sources

Phase 5.1A connects the reusable `pub.tool.General` Defs Codegen API to the Builder `source_native_processing` lifecycle. Canonical definitions are discovered automatically below `src/product/yamldefs`, `src/product/jsondefs`, and `src/product/xmldefs`; SourceKind, package/module namespace, and Java/Python targets are derived from source placement and TechnologyKinds rather than enumerated in artifact metadata. Generated sources are written to canonical `.gen` source roots and are reproducible build state rather than handwritten source. The first active consumer is the Builder `coreintf` ResourceEndpoint model, which generates versioned `AIcgd..._1` data objects and `AIng..._1` enums from canonical definitions.

Structured-data loading remains separate from effective-model construction. Phase 5.1B supplies a separate optional Jackson YAML/JSON/XML loader artifact and moves ResourceEndpoint declaration inheritance, `Enabled=true` defaulting, ResourceKind semantic validation, and typed endpoint selection into Modustro Builder. The metadata resolver now carries generated `AIcgdResourceEndpoint_1` declarations and delegates their merge/effective resolution to `coreimpl`; the root Gradle build consumes the resulting `AIcResourceEndpointCatalog` through a thin metadata bridge. Settings-level Java repository registration remains a bootstrap adapter over the already validated normalized endpoint output.

## Phase 6 publication layer

The Phase-6 portable core separates publication planning from provider execution. `PublicationDestinations` contains only effective PublishingEndpoint ids; omitted destinations select every enabled endpoint for the selected output kind and stability. Global schema publication validates `GlobalPublicationPathId` at the trust boundary and maintains server-controlled draft/release metadata with monotonic draft revisions and immutable releases. Provider-specific publication is performed by PublishingAdapter implementations; generic ordering, retries, failure handling, deadlines, and progress remain scheduler responsibilities.

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
