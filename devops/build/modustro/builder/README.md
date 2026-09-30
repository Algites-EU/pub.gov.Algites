# Modustro Builder

`modustro/builder` contains the Gradle-independent core model and implementation of the Modustro build system.

The staged migration deliberately separates the portable Builder model from the existing Gradle integration:

- `coreintf` contains public Java contracts, immutable model types, and canonical machine-readable definitions;
- `coreimpl` contains reusable Gradle-independent implementations such as inheritance resolution, model validation, and Java/Python dependency technology bridges;
- `devops/build/algitesbuild` and shared Gradle scripts remain the current execution/integration edge.

Phase 2 connects the active Algites dependency metadata bridge to the new semantics: `DependencyKind: modustro`, merge-only `Usages`, `RequiredBuildOutputTypes`, and per-technology Java/Python mapping. Concrete BuildOutput producers remain Phase 3 work.

The package namespace is `eu.algites.pltf.modustro.builder`.
