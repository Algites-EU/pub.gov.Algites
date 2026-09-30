# Modustro Builder

`modustro/builder` contains the Gradle-independent core model and implementation of the Modustro build system.

Phase 1 deliberately separates the portable Builder model from the existing Gradle integration:

- `coreintf` contains public Java contracts, immutable model types, and canonical machine-readable definitions;
- `coreimpl` contains reusable Gradle-independent implementations such as inheritance resolution and model validation;
- the existing `devops/build/algitesbuild` and shared Gradle scripts remain the integration edge until later migration phases.

The package namespace is `eu.algites.pltf.modustro.builder`.
