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
