# Modustro Builder Jackson structured-data adapter

This artifact implements the Gradle-independent `AIiStructuredDataLoader` contract for YAML, JSON, and XML using Jackson. It is intentionally separate from `builder/coreimpl` so Jackson does not become a transitive dependency of the Modustro Builder bootstrap classpath.

The adapter accepts Jackson versions `>=2.12.0,<3.0.0`; 2.12 is the minimum because generated Java definition DTOs use records. Representation mapping is intentionally separate from Modustro inheritance, defaults, semantic validation, and effective-model construction.
