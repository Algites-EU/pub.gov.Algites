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

Phase 3A adds the bootstrap-safe build-output producer layer without changing the active Gradle orchestration:

- hard-wired producers for `java_classes_jar`, `java_sources_jar`, `java_javadoc_jar`, `python_wheel`, and `python_sdist`;
- a producer registry keyed by `TechnologyKind + BuildOutputType`;
- portable production plans consuming `PreparedSourceSet`;
- built-in default output selection through the existing TechnologyKind definitions;
- explicit rejection of virtual dependency-only outputs such as `python_distribution` as directly producible outputs.

The active Gradle build still uses the Phase 2 orchestration in Phase 3A. After these classes are published, Phase 3B can switch the Gradle adapter to the producer plans without a bootstrap cycle.
