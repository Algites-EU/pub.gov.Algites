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
