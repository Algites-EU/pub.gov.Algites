# Modustro Builder core interfaces

This artifact is the Gradle-independent public contract layer of Modustro Builder.

It contains:

- technology, capability, build-output, dependency, version and inheritance model types;
- portable Java interfaces for inheritance, model validation, and dependency technology handlers;
- portable dependency-resolution plan and diagnostic contracts;
- technology-neutral `PreparedSourceSet` plus portable build-output producer and production-plan contracts;
- canonical versioned definitions in YAML-definition, JSON-definition and XML-definition source roots;
- structured-data loading contracts for mapping YAML, JSON and XML documents to generated or handwritten data-object types;
- strict global-publication user sidecars for canonical source definitions plus a separate deployment-owned metadata contract for published state;
- Phase-6 publication endpoint-selection and deploy-state contracts for `docs_site` and `schema_site`.

The dependency model uses `DependencyKind: modustro` for Modustro-controlled artifact references. Dependency/constraint groups use `ItemsInheritancePolicy` to control membership, while a surviving same-identity dependency always merge-composes its `Usages` and `RequiredBuildOutputTypes`; neither property participates in dependency identity. `VariantId` remains part of the dependency identity. Version scalars support explicit `null` clearing, and the hard `Exclude` collection has its own item-inheritance policy.

## Definition-driven generated sources

The artifact declares `Artifact.DefinitionCodeGeneration` entries for canonical ResourceEndpoint definitions. During the `source_native_processing` lifecycle boundary, the common Algites build adapter invokes the reusable Defs Codegen Java API from `pub.tool.General` and materializes reproducible generated sources below the standard `.gen` source roots. Generated files are derived state and MUST NOT be edited manually.

The ResourceEndpoint model deliberately separates canonical serialized data from the effective Builder domain model:

```text
canonical yamldefs/jsondefs/xmldefs definition
    -> generated AIcgd..._N / AIng..._N source type
    -> structured-data loading
    -> inheritance/defaulting/semantic validation
    -> handwritten effective Builder model
```

Phase 5.1A establishes the generation and loading contracts. Phase 5.1B keeps the generated DTOs as transport declarations while moving ResourceEndpoint inheritance, defaulting, selection, and semantic validation into the handwritten Builder effective-model layer. Declaration fields that may be inherited, including `Url`, `Enabled`, and `Stability`, remain nullable until effective resolution; `Enabled` defaults to true only after inheritance.

The generated ResourceEndpoint Java types are based on first-class canonical definitions for the endpoint object and its visibility, action and stability enums. Human-readable definition/property descriptions are propagated by Defs Codegen into generated source documentation.

## Structured-data loading

`AIiStructuredDataLoader` is the representation-mapping boundary for YAML, JSON and XML. A loader maps a document to the requested generated or handwritten data-object class. It intentionally does not apply descriptor inheritance, effective defaults, cross-field semantic validation, or effective-model construction.

`AInStructuredDataFormat` identifies the supported serialization family. Concrete loader implementations belong outside this public core-interface artifact. `AIcResourceEndpointCatalog` is the immutable effective endpoint view used by execution adapters after declaration resolution.

No Gradle API type is part of this artifact's public or implementation dependencies.

## Phase 6 publication contracts

Phase 6 adds portable publication contracts without introducing storage-provider APIs into Builder Core. `AIcPublicationDestinationSelection` carries the effective `ResourceEndpoint` targets selected for one publication capability invocation. `AInGlobalPublicationState` and `AIcGlobalPublicationDeployMetadata` represent trusted deployment state independently from author-controlled sidecars. Canonical publication user/deploy metadata definitions and the Modustro publication capability configuration definitions are now included in definition-driven Java source generation so execution adapters can consume versioned generated DTOs rather than ad-hoc maps.
