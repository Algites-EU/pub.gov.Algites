# Modustro Builder core interfaces

This artifact is the Gradle-independent public contract layer of Modustro Builder.

It contains:

- technology, capability, build-output, dependency, version and inheritance model types;
- portable Java interfaces for inheritance and model validation;
- canonical v1 definitions in YAML-definition, JSON-definition and XML-definition source roots;
- global-publication metadata sidecars for every canonical definition.

No Gradle API type is part of this artifact's public or implementation dependencies.
