# Modustro Builder Input Subscriptions and Output Publications

This document is the normative implementation reference for external inputs and publication execution in Modustro Builder.
It describes the canonical `InputSubscriptions` and `OutputPublications` models used by repository defaults,
`modustro-source-repository.yml`, `modustro-artifact-set.yml`, and `modustro-artifact.yml`.

The former generalized `ResourceEndpoints` model is removed. Its former responsibilities are split deliberately:

- external resources consumed by a build are configured through `InputSubscriptions`;
- outputs produced by a build are configured through `OutputPublications`;
- operations that happen after a successful publication are recursive `PostPublicationActions` attached to that publication.

There is no `download`, `upload`, or `manage` action dimension in the canonical model.

## 1. Symmetric top-level model

The two external-I/O directions are intentionally named symmetrically:

```text
InputSubscriptions                 OutputPublications
        |                                  |
        +-- Subscriptions                  +-- Snapshot / Release
        |      |                            |      |
        |      +-- SubscriptionAdapter      |      +-- PublicationEndpoints
        |                                   |             |
        +-- TechnologyKind                  |             +-- Publications
        +-- InputSelector                   |                    |
                                            |                    +-- PostPublicationActions
                                            |                            |
                                            |                            +-- PostPublicationActions ...
                                            +-- TechnologyKind
                                            +-- OutputSelector
```

`TechnologyKind` is mandatory in every canonical declaration. A declaration never means "all technologies".
Hierarchical metadata may be merged before validation, but the effective declaration must always identify exactly one
technology.

## 2. InputSubscriptions

`InputSubscriptions` describes external content that Builder may consume. A subscription is not a publication target
and cannot carry upload or management actions.

```yaml
InputSubscriptions:
  - InputSelector: native_product_binaries
    TechnologyKind: java
    Subscriptions:
      - Id: maven-central
        Enabled: true
        Visibility: public
        Stability: release
        SubscriptionUri: https://repo1.maven.org/maven2/
        SubscriptionAdapter: maven-repository
        SubscriptionOrder: 0

      - Id: algites-public-snapshots
        Visibility: public
        Stability: snapshot
        SubscriptionUri: https://dl.cloudsmith.io/public/algites/java-snapshots-pub/maven/
        SubscriptionAdapter: maven-repository
        SubscriptionCredentialProfile: algites-java-public-snapshot-subscription
        SubscriptionOrder: 10
```

### 2.1 InputSelector

The selector may name one concrete native output or one virtual group. Concrete selectors are:

- `native_product_sources`
- `native_product_binaries`
- `native_product_documentation`
- `native_develop_sources`
- `native_develop_binaries`
- `native_develop_documentation`
- `modustro_docs_site`
- `schema_site`

Virtual groups are:

- `native_outputs`
- `native_product_outputs`
- `native_develop_outputs`
- `native_sources`
- `native_binaries`
- `native_documentation`

Group declarations are expanded before effective configuration is consumed. `native_outputs` is the broadest base;
other groups are applied in declaration order; a concrete selector has highest precedence.

### 2.2 Subscription fields

`Id` is the stable merge identity within one effective `TechnologyKind` + concrete input selector. Descendant metadata
may override a subscription sparsely by repeating the same `Id`.

`Enabled` defaults to `true`. `Visibility` is `public` or `private`. Public source repositories may consume only public
subscriptions; private source repositories may consume public and private subscriptions.

`Stability` is `release` or `snapshot` when the subscription distinguishes them. `SubscriptionUri` is the canonical
location consumed by the adapter. `SubscriptionAdapter` selects the implementation, for example
`maven-repository` or `python-repository`. `SubscriptionCredentialProfile` names a governed credential profile and
never embeds secret values. `SubscriptionOrder` orders candidate subscriptions where the consuming adapter uses
ordered lookup. `Configuration` carries adapter-specific non-secret values.

## 3. OutputPublications

`OutputPublications` is the sole publication model. It owns destination URIs, publication adapters, credential
profiles, retry/failure policy, root ordering, concrete publication forms, and all dependent post-publication work.

```yaml
OutputPublications:
  - OutputSelector: native_product_binaries
    TechnologyKind: java

    Snapshot:
      PublicationEnabled: true
      PublicationEndpoints:
        - Id: java-public-snapshots
          PublicationUri: https://maven.example.invalid/snapshots/
          PublicationAdapter: maven-repository
          PublicationCredentialProfile: java-public-snapshot-publication
          PublicationOrder: 0
          PublicationFailurePolicy: FAIL_BUILD_ON_PUBLICATION_FAILURE
          PublicationRetryCount: 2
          PublicationWaitForNextAttemptMillis: 1000
          PublicationAttemptTimeoutMillis: 60000
          ShowPublicationProgressIfPossible: true

          Publications:
            - Id: standard
              PostPublicationActions:
                - Id: build-record
                  PostPublicationActionAdapter: modustro-build-record
```

`Snapshot` and `Release` are independent inherited branches. `PublicationEnabled` controls publication of the output;
it does not control whether Builder produces the output itself.

## 4. PublicationEndpoints

A `PublicationEndpoint` is a reusable effective destination configuration. Its stable `Id` is also the reference target
for `TargetPublicationEndpointId` from post-publication actions.

Fields are:

- `Id`
- `Enabled` (default `true`)
- `PublicationUri`
- `PublicationAdapter`
- `PublicationCredentialProfile`
- `PublicationOrder` (default `0`, negative values allowed)
- `PublicationFailurePolicy` (default `FAIL_BUILD_ON_PUBLICATION_FAILURE`)
- `PublicationRetryCount` (default `0`)
- `PublicationWaitForNextAttemptMillis` (default `1000`)
- `PublicationAttemptTimeoutMillis` (optional positive timeout)
- `ShowPublicationProgressIfPossible` (default `true`)
- `Configuration` (adapter-specific non-secret values)
- `Publications`

Endpoint configuration is merged by `Id`. A descendant can change one endpoint property without repeating the rest.
An explicit empty endpoint list clears inherited endpoints for that branch.

### 4.1 Root PublicationOrder

`PublicationOrder` orders only direct root publications in the same scheduler scope. Lower values become eligible
first; roots with the same value may run concurrently. A later root order waits for completion of the direct root
publications in the preceding order group, not for their descendant post-action trees.

This is intentional. Root scheduling and descendant scheduling are separate. The whole build still waits for every
required descendant tree before publication completion is considered final.

## 5. Publications

`Publications` names concrete publication forms sent through one endpoint. If the list is omitted, Builder uses one
implicit publication with `Id: standard`.

For Maven-compatible output forms a publication may override `Classifier` and `Extension`:

```yaml
Publications:
  - Id: standard

  - Id: sources
    Classifier: sources
    Extension: jar

  - Id: javadoc
    Classifier: javadoc
    Extension: jar
```

Each root publication is an independently retried/failing scheduler unit. A retry never reruns another root
publication merely because one publication failed.

For timestamped Maven snapshots, all forms planned from the same payload use the same reserved snapshot instance so
that the primary artifact and its sidecars resolve to one consistent timestamp/build-number pair.

## 6. Implicit build record

Every enabled root `Publication` receives an implicit direct post-action equivalent to:

```yaml
PostPublicationActions:
  - Id: build-record
    Enabled: true
    PostPublicationActionAdapter: modustro-build-record
```

The resulting sidecar name is exactly:

```text
<published-filename>.modustro-build-record.yml
```

The complete published filename is preserved, including classifier and compound extension where applicable.

The implicit action can be configured or disabled by declaring the same stable `Id` explicitly:

```yaml
PostPublicationActions:
  - Id: build-record
    Enabled: false
```

Implicit build-record actions are added only to root publications. A post-publication action never receives another
implicit build-record action; any deeper action must be declared explicitly. This prevents unbounded recursion.

A package index that cannot store arbitrary sidecars, such as a Python package index, should explicitly disable the
implicit build record or redirect it to a metadata-capable `PublicationEndpoint` with
`TargetPublicationEndpointId`.

## 7. PostPublicationActions

`PostPublicationActions` is the single generic descendant mechanism. The old distinction between an
"extended publication" and another post-publication operation is intentionally removed.

A post-action may:

- generate and publish a derived artifact, such as a build record, checksum, signature, or index;
- invoke a provider operation such as promotion or cache invalidation;
- remove a corresponding snapshot after a successful release;
- perform any other adapter-defined action whose hard dependency is the successful completion of its parent.

The canonical fields are:

- `Id`
- `Enabled` (default `true`)
- `PostPublicationActionAdapter`
- `TargetPublicationEndpointId` (optional)
- `Order` (default `0`, local to direct siblings, negative values allowed)
- `Configuration`
- `FailurePolicy` (default `FAIL_BUILD_ON_FAILURE`)
- `RetryCount` (default `0`)
- `WaitForNextAttemptMillis` (default `1000`)
- `AttemptTimeoutMillis` (optional positive timeout)
- `ShowProgressIfPossible` (default `true`)
- recursive `PostPublicationActions`

### 7.1 Hard parent barrier

A post-action cannot begin before its immediate parent succeeds. This is a hard dependency and also defines data flow.
If action B requires the result of action A, B must be nested under A; `Order` must not be used to fake a data
dependency.

```yaml
PostPublicationActions:
  - Id: create-index
    PostPublicationActionAdapter: create-index
    PostPublicationActions:
      - Id: sign-index
        PostPublicationActionAdapter: sign-content
```

`sign-index` is eligible only after `create-index` has completed successfully.

### 7.2 Local Order

`Order` is compared only among direct siblings of one parent:

```yaml
PostPublicationActions:
  - Id: prepare
    Order: -10
    PostPublicationActionAdapter: prepare-release

  - Id: notify-a
    Order: 0
    PostPublicationActionAdapter: notify

  - Id: notify-b
    Order: 0
    PostPublicationActionAdapter: notify
```

`prepare` completes before `notify-a` and `notify-b` become eligible. The two order-0 actions may run concurrently.
A later sibling order waits only for the direct actions in the previous sibling order group. It does not wait for
those siblings' descendants. Descendant dependencies are expressed by nesting.

Therefore:

```text
Publication P
  |
  +-- A (Order 0) ----> A1
  |
  +-- B (Order 1)
```

means that B waits for A itself, but A1 may still be running when B starts. If B must wait for A1, B belongs under A1
or the graph must otherwise express that dependency explicitly.

## 8. Execution lineage and content URI semantics

Every post-action attempt receives the complete ordered ancestor lineage from the root publication to its immediate
parent. Each retained execution step exposes:

- step `Id` and kind;
- the single canonical `InputUri` for that step;
- the single canonical `OutputUri`, when the step produced one;
- effective step configuration;
- result metadata.

There is deliberately no unordered list of "equivalent URIs". An adapter must never guess which locator is canonical.
A step's `InputUri` identifies the content on which that step operates; its `OutputUri` identifies the result produced
by that step. The child receives the parent's output URI as its input URI when an output exists, otherwise the input
URI is propagated.

The lineage contains ancestors only. Sibling execution state is not exposed, preventing behavior from depending on
which parallel sibling happened to complete first.

The context does not expose an already-open `InputStream`. A URI may identify a local file, a mounted resource, or a
remote object, including content that never existed locally. The action adapter owns URI dereferencing and may use
its target endpoint credentials/configuration where required. The frozen root publication payload is also retained in
the action context for root artifact identity and metadata.

## 9. PublicationAdapter and PostPublicationActionAdapter

Publication transport and dependent actions use two independent interfaces:

```text
PublicationAdapter
PostPublicationActionAdapter
```

One implementation/module may implement either or both interfaces. A publication adapter executes exactly one
publication attempt; scheduler policy owns ordering, retry, timeout, failure handling, cancellation, and progress.
A post-action adapter likewise executes exactly one action attempt and receives its action configuration, complete
ancestor lineage, input URI, resolved target endpoint when one is selected, credentials, deadline/cancellation,
progress reporting, and a publication delegate for derived publications.

Built-in publication adapters currently include Maven HTTP repositories, local Maven repositories, Python package
repositories, local filesystem copy, HTTP directory, Git branch, and S3-compatible object storage. Provider-specific
post-actions can be added without adding a new top-level endpoint/action category.

## 10. TargetPublicationEndpointId

A post-action inherits its root publication endpoint by default. If it must act on or publish through another endpoint,
it references that endpoint by stable Id:

```yaml
PostPublicationActions:
  - Id: publish-build-record
    PostPublicationActionAdapter: modustro-build-record
    TargetPublicationEndpointId: metadata-sidecars
```

The effective publication plan carries a registry of endpoints from both Snapshot and Release branches of the same
resolved output. This permits a release action to target a configured snapshot endpoint without duplicating URI,
credentials, or provider configuration.

## 11. Snapshot cleanup as a post-action

Snapshot cleanup is no longer a top-level `manage` endpoint or a separate Gradle cleanup phase. It is a lifecycle
operation dependent on a successful release and therefore belongs in the release publication tree.

A provider adapter may be configured as follows once registered:

```yaml
Release:
  PublicationEnabled: true
  PublicationEndpoints:
    - Id: java-release
      PublicationUri: https://repository.example.invalid/releases/
      PublicationAdapter: maven-repository
      Publications:
        - Id: standard
          PostPublicationActions:
            - Id: remove-corresponding-snapshot
              PostPublicationActionAdapter: provider-remove-corresponding-snapshot
              TargetPublicationEndpointId: java-snapshot
              FailurePolicy: IGNORE_FAILURE
```

The cleanup adapter receives the entire successful release lineage and the resolved target endpoint. It therefore has
all artifact identity/version information plus the target endpoint URI, configuration, and credentials and does not
need duplicate `manage` metadata.

Cleanup behavior is governance policy, not standard Maven release behavior. A Maven release does not itself imply
removal of the matching snapshot from a remote repository.

## 12. Object-storage publication

The built-in `s3-object-storage` publication adapter supports S3-compatible HTTP object storage, including Scaleway
Object Storage. `PublicationUri` is the bucket/prefix root. Provider-specific non-secret parameters are supplied in
`Configuration`:

```yaml
PublicationEndpoints:
  - Id: public-schema-site
    PublicationUri: https://s3.fr-par.scw.cloud/algites-dev-defs-public/
    PublicationAdapter: s3-object-storage
    PublicationCredentialProfile: algites-modustro-schema-site-public-publication
    Configuration:
      Region: fr-par
```

For the current basic credential profile mapping, username is the S3 access key and password is the S3 secret key.
Secret values remain outside governance YAML.

## 13. Failure, retry and completion semantics

Root publication failure policy is `PublicationFailurePolicy`; post-action failure policy is `FailurePolicy`.
Required failures fail the publication completion future. Ignored failures remain observable in the result maps but do
not fail required completion.

Retries belong to the failing unit only. Retrying a build-record action does not republish its parent artifact.
Retry-capable adapters must explicitly report retry safety. Timeouts and cancellation are scheduler-owned; adapters
execute one attempt and cooperate with the supplied deadline/cancellation context.

`requiredCompletion` completes only after all required root publications and all required descendant action trees have
completed, even though root-order and sibling-order barriers intentionally ignore descendant completion when deciding
when another direct sibling/root becomes eligible.

## 14. Hierarchical merge rules

At each descriptor/default layer:

1. selector groups are expanded to concrete output/input kinds;
2. declarations are keyed by `TechnologyKind` plus concrete selector;
3. endpoint/subscription lists merge by stable `Id`;
4. `Publications` merge by stable `Id`;
5. `PostPublicationActions` merge recursively by stable sibling `Id`;
6. sparse descendants inherit unspecified properties;
7. an explicit empty list clears the inherited list at that location.

This permits a repository default to define the transport once while an artifact changes only one retry policy,
disables one inherited action, or adds one new publication form.

## 15. Credential preflight

Credential preflight has only two usage classes:

```text
subscription
publication
```

Canonical Gradle properties are:

```text
algites.credential.usages
algites.credential.subscription.stabilities
algites.credential.publication.stabilities
algites.credential.outputKinds
algites.credential.output
```

Environment counterparts are `ALGITES_CREDENTIAL_USAGES`,
`ALGITES_CREDENTIAL_SUBSCRIPTION_STABILITIES`, `ALGITES_CREDENTIAL_PUBLICATION_STABILITIES`,
`ALGITES_CREDENTIAL_OUTPUT_KINDS`, and `ALGITES_CREDENTIAL_OUTPUT`.

The preflight plan reports `inputSubscriptions`, `publicationEndpoints`, and the required credential profile/type
pairs. There is no `manage` credential usage.

## 16. Design invariants

The implementation and canonical schemas enforce these invariants:

- effective `TechnologyKind` is always known and singular;
- input resolution and output publication are separate concepts;
- publication destinations exist only under `OutputPublications`;
- post-publication lifecycle work exists only as `PostPublicationActions`;
- tree nesting represents hard dependency and data flow;
- `Order` represents only scheduling among direct siblings;
- content has one canonical URI per input/output role;
- adapters receive complete ancestor lineage but not sibling state;
- secrets never appear in governance endpoint definitions;
- implicit build records apply only to root publications;
- required `GroupId` is resolved before a publication plan is created.
