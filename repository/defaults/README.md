# Algites public repository defaults

This directory contains public repository-default governance data consumed by the common Algites repository resolver.

- `algites-repository-download-defaults-public.yml` contains canonical public **download** endpoints for all standard TechnologyKinds (`java`, `python`, `mps`).

The resolver implementation contains only repository-resolution mechanics; concrete public repository URLs are defined here. Explicit `dummy.invalid` URLs are intentional placeholders for cells whose current endpoint has not yet been confirmed. Replace them before using the corresponding TechnologyKind in production.

CI exposes this file to the resolver through `ALGITES_REPOSITORY_PUBLIC_DEFAULTS_FILE`. A local build may point the same environment variable at a checkout/copy of this file.
