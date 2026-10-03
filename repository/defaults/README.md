# Algites public ResourceEndpoint defaults

This directory contains public ResourceEndpoint-default governance data consumed by the common Algites resolver.

- `algites-repository-download-defaults-public.yml` retains its historical filesystem name for workflow compatibility, but its document now uses the generalized `ResourceEndpoints` model.
- The public defaults currently define `native_binary_output` **download** endpoints for the standard Java, Python, and MPS TechnologyKinds.

A ResourceEndpoint is selected by `TechnologyKind`, `ResourceKind`, visibility, and action. `Stability` is endpoint data rather than a fixed matrix axis. The built-in `native_binary_output`, `native_source_output`, `native_documentation_output`, and `modustro_docs_site` ResourceKinds require `Stability`; `schema_site` forbids it.

CI exposes this file through `ALGITES_REPOSITORY_PUBLIC_DEFAULTS_FILE`. A local build may point the same environment variable at a checkout/copy of the file. The historical environment-variable name remains supported during the migration because it identifies the governance input file, not the metadata model contained in it.

Private-governance automation may additionally supply `ALGITES_REPOSITORY_GOVERNED_PUBLIC_DEFAULTS_FILE` for the combined public upload/manage overlay, or `ALGITES_REPOSITORY_PRIVATE_DEFAULTS_FILE` for private download/upload/manage endpoints. These defaults use `ResourceEndpoints` exclusively; the superseded repository-matrix input is not accepted.
