# Modustro Builder core interfaces

This artifact is the Gradle-independent public contract layer of Modustro Builder.

It contains:

- technology, capability, build-output, dependency, version and inheritance model types;
- portable Java interfaces for inheritance, model validation, and dependency technology handlers;
- portable dependency-resolution plan and diagnostic contracts;
- canonical versioned definitions in YAML-definition, JSON-definition and XML-definition source roots;
- global-publication metadata sidecars for every canonical definition.

The dependency model uses `DependencyKind: modustro` for Modustro-controlled artifact references. Dependency/constraint groups use `ItemsInheritancePolicy` to control membership, while a surviving same-identity dependency always merge-composes its `Usages` and `RequiredBuildOutputTypes`; neither property participates in dependency identity. `VariantId` remains part of the dependency identity. Version scalars support explicit `null` clearing, and the hard `Exclude` collection has its own item-inheritance policy.

No Gradle API type is part of this artifact's public or implementation dependencies.
