# Algites public repository defaults

This directory contains public non-secret defaults consumed by the Modustro Builder metadata resolver.

`algites-repository-defaults-public.yml` is the canonical public baseline. It uses the current two-direction model:

- `InputSubscriptions` for external public inputs consumed by builds;
- `OutputPublications` for public outputs whose destination configuration is itself safe to publish.

Every declaration has an explicit `TechnologyKind`. Subscriptions identify `SubscriptionUri`, `SubscriptionAdapter`,
visibility/stability, optional credential profile, and adapter-specific `Configuration`. Output publication branches
identify `PublicationEndpoints`, root `Publications`, and recursive `PostPublicationActions`.

The file contains no secret credential values. A `SubscriptionCredentialProfile` or
`PublicationCredentialProfile` is only a reference to a governed profile whose value is resolved separately.

For ordinary local builds, the resolver can fetch this public file from the published `pub.gov.Algites` governance
revision when `ALGITES_REPOSITORY_PUBLIC_DEFAULTS_FILE` is not supplied. Governance development and CI may point that
environment variable at an explicitly materialized local copy.

Private-governance automation may additionally provide `ALGITES_REPOSITORY_GOVERNED_PUBLIC_DEFAULTS_FILE` or
`ALGITES_REPOSITORY_PRIVATE_DEFAULTS_FILE`. Those overlays use the same `InputSubscriptions` / `OutputPublications`
model and are merged hierarchically by stable Id.

See [`../../devops/build/modustro/PUBLICATIONS.md`](../../devops/build/modustro/PUBLICATIONS.md) for the complete input,
publication, action-tree, ordering, retry/failure, lineage, and URI semantics.
