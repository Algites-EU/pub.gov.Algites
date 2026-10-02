# Modustro Builder core implementation

This artifact contains Gradle-independent reusable implementations of the contracts from `coreintf`.

Phase 1 established:

- scalar inheritance with absent / explicit-null / explicit-value semantics;
- keyed collection inheritance with `merge_missing_items` and `remove_missing_items` membership policies;
- merge-only set inheritance for properties such as dependency usages and required dependency output types;
- validation of TechnologyKind and BuildOutputType definitions, including virtual dependency output alternatives.

Phase 2 adds the dependency technology bridge:

- Java usage mapping to standard logical Gradle Java/Java-Library configuration names without importing the Gradle API;
- Python usage mapping to package/runtime, build/source-processing, development, or temporary no-op roles;
- explicit diagnostics for lossy Python mappings;
- validation and defaulting of dependency BuildOutputTypes, including virtual `python_distribution`;
- a portable dependency-resolution plan that can be applied by an execution adapter such as the current Gradle integration.

Gradle adaptation remains outside this artifact.

Phase 3 adds the build-output producer layer and activates it in the Gradle adapter:

- hard-wired producers for `java_classes_jar`, `java_sources_jar`, `java_javadoc_jar`, `python_wheel`, and `python_sdist`;
- a producer registry keyed by `TechnologyKind + BuildOutputType`;
- portable production plans consuming `PreparedSourceSet`;
- built-in default output selection through the existing TechnologyKind definitions;
- explicit rejection of virtual dependency-only outputs such as `python_distribution` as directly producible outputs.

Phase 4 adds demand-driven capabilities. Production plans declare their capability requirements, `AIcBuiltinCapabilityDemandPlanner` deduplicates them and expands prerequisites, and the active Gradle adapter materializes the resulting source-processing and dependency-resolution boundaries. Native documentation is represented by the same graph rather than by a separate dependency chain.

Phase 5.1B adds the effective ResourceEndpoint pipeline:

- `AIcResourceEndpointResolver` merge-composes generated `AIcgdResourceEndpoint_1` declarations, applies `Enabled=true` after inheritance, constructs the handwritten effective model, and runs ResourceKind semantic validation;
- `AIcResourceEndpointMetadataBridge` converts the normalized Algites metadata representation used by execution adapters into the same generated DTO/effective-model pipeline;
- `AIcResourceEndpointCatalog` provides globally unique endpoint-id lookup and typed operation-context selection.

The optional `builder/structureddata/jackson` artifact supplies YAML/JSON/XML representation mapping without putting Jackson on the core/bootstrap dependency path.

