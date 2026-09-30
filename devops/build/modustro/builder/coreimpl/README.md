# Modustro Builder core implementation

This artifact contains Gradle-independent reusable implementations of the contracts from `coreintf`.

Phase 1 provides:

- scalar inheritance with absent / explicit-null / explicit-value semantics;
- keyed collection inheritance with `mergeMissingItems` and `removeMissingItems` membership policies;
- merge-only set inheritance for properties such as dependency usages and required dependency output types;
- validation of TechnologyKind and BuildOutputType definitions, including virtual dependency output alternatives.

Gradle adaptation remains outside this artifact.
