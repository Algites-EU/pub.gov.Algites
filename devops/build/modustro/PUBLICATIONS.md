# Modustro Builder Input Subscriptions and Output Publications

This document is the implementation reference for external inputs, publication execution, and publication
finalization in Modustro Builder. It describes the canonical models used by repository defaults,
`modustro-source-repository.yml`, `modustro-artifact-set.yml`, and `modustro-artifact.yml`.

External resources consumed by a build use `InputSubscriptions`; produced outputs use `OutputPublications`.
Finalization has four consistency boundaries: concrete publication, output, logical artifact, and Version Scope.
The publication boundary supports recursive dependent actions; higher boundaries receive complete immutable results.

The former `ResourceEndpoints` and `PostPublicationActions` contracts are obsolete. There is no `download`, `upload`,
or `manage` action dimension in the canonical model. Old post-action declarations must be migrated to the appropriate
finalization boundary; snapshot cleanup belongs at Version Scope, not under every release publication.

## 1. Symmetric top-level model

| Declaration | Location and responsibility |
| --- | --- |
| `InputSubscriptions` | Technology/input selector; external sources consumed by the build. |
| `OutputPublications` | Technology/output selector; independent Snapshot/Release publication policy. |
| `PublicationEndpoints` / `Publications` | Destination transport and concrete publication forms. |
| `PublicationFinalizationActions` | Direct dependent actions of a concrete publication. |
| `FinalizationActions` | Recursive children of a publication finalization action. |
| `OutputPublicationFinalizationActions` | Flat actions after all publication trees of one output. |
| `ArtifactPublicationFinalizationActions` | Flat Snapshot/Release actions after all outputs of one logical artifact. |
| `VersionScopePublicationFinalizationActions` | Flat Snapshot/Release actions after all artifacts of one version boundary. |

`TechnologyKind` is mandatory in every canonical input/output declaration. A declaration never means all technologies.
Hierarchical metadata may be merged before validation, but each effective declaration identifies exactly one technology.
Repository docs and schema-site publications use the reserved `modustro` technology; native Java/Python/MPS declarations
retain their own technology. Output selectors are logical publication categories, not technology-specific package formats.

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

`OutputPublications` is the sole publication-destination model. It owns destination URIs, transport adapters, credential
profiles, retry/failure policy, root ordering, concrete forms, publication finalizers, and output-level finalizers.
Artifact and Version Scope finalizers are separate top-level stability branches, not endpoint declarations.

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
          ExecutionOrder: 0
          ExecutionFailurePolicy: fail_build_on_failure
          PublicationRetryCount: 2
          PublicationWaitForNextAttemptMillis: 1000
          PublicationAttemptTimeoutMillis: 60000
          ShowPublicationProgressIfPossible: true

          Publications:
            - Id: standard
              PublicationFinalizationActions:
                - Id: build-record
                  PublicationFinalizationActionAdapter: modustro-build-record
```

`Snapshot` and `Release` are independent inherited branches. `PublicationEnabled` controls publication of the output;
it does not control whether Builder produces the output itself.

### 3.1 Enablement and scheduling names by location

`PublicationEnabled` is the output-level publication gate; `ExecutionEnabled` is the item-level execution gate. YAML property names are
case-sensitive; the spelling is exactly `PublicationEnabled`. Their location determines their meaning:

| Configuration location | Enablement field / default | Scheduling field / default |
| --- | --- | --- |
| `OutputPublications` item, `Snapshot` or `Release` branch | `PublicationEnabled: false` | No order property on this branch. |
| `PublicationEndpoints` item | `ExecutionEnabled: true` | `ExecutionOrder: 0`; schedules direct root publication groups. |
| `Publications` item | `ExecutionEnabled: true` | No separate order property; uses its endpoint's publication policy. |
| Any publication/output/artifact/Version Scope finalization action, including nested `FinalizationActions` | `ExecutionEnabled: true` | `ExecutionOrder: 0`; schedules direct sibling actions in that list. |

`PublicationEnabled: false` disables publication of that output in that stability branch. `ExecutionEnabled: false` on an
endpoint, publication form, or finalization action disables only that item. Disabling a finalization action does not
disable the payload publication or sibling finalizers. Enabling an item does not bypass an enclosing disabled output
or endpoint, or the finalization completion barriers.

Execution control uses the same `ExecutionOrder` and `ExecutionFailurePolicy` names for endpoints and finalization actions; retry, timeout, and progress-control names remain operation-specific:

| Endpoint publication policy | Finalization-action policy |
| --- | --- |
| `ExecutionOrder` | `ExecutionOrder` |
| `ExecutionFailurePolicy` | `ExecutionFailurePolicy` |
| `PublicationRetryCount` | `RetryCount` |
| `PublicationWaitForNextAttemptMillis` | `WaitForNextAttemptMillis` |
| `PublicationAttemptTimeoutMillis` | `AttemptTimeoutMillis` |
| `ShowPublicationProgressIfPossible` | `ShowProgressIfPossible` |

These names are shared by defaults and structural descriptors. `algites-repository-defaults_1`,
`modustro-source-repository_1`, `modustro-artifact-set_1`, and `modustro-artifact_1` all reference the same definitions in
`modustro-builder-publications_1.yamldef.schema.json` for output publications and artifact/Version Scope finalization.
There is no defaults-specific renaming. Endpoints and finalization actions use `ExecutionEnabled` and
`ExecutionOrder` in every layer. The former item fields `Enabled`, `Order`, and endpoint `PublicationOrder` have been
replaced; they are not schema aliases. `PublicationEnabled` remains valid only on the output Snapshot/Release branch.

## 4. PublicationEndpoints

A `PublicationEndpoint` is a reusable effective destination configuration. Its stable `Id` is also the reference target
for `TargetPublicationEndpointId` from publication finalization actions.

Fields are:

- `Id`
- `ExecutionEnabled` (default `true`)
- `PublicationUri`
- `PublicationAdapter`
- `PublicationCredentialProfile`
- `ExecutionOrder` (default `0`, negative values allowed)
- `ExecutionFailurePolicy` (default `fail_build_on_failure`)
- `PublicationRetryCount` (default `0`)
- `PublicationWaitForNextAttemptMillis` (default `1000`)
- `PublicationAttemptTimeoutMillis` (optional positive timeout)
- `ShowPublicationProgressIfPossible` (default `true`)
- `Configuration` (adapter-specific non-secret values)
- `Publications`

Endpoint configuration is merged by `Id`. A descendant can change one endpoint property without repeating the rest.
An explicit empty endpoint list clears inherited endpoints for that branch.

### 4.1 Root ExecutionOrder

`ExecutionOrder` orders only direct root publications in the same scheduler scope. Lower values become eligible
first; roots with the same value may run concurrently. A later root order waits for completion of the direct root
publications in the preceding order group, not for their descendant publication finalization action trees.

This is intentional. Root scheduling and descendant scheduling are separate. The whole build still waits for every
required descendant tree before publication completion is considered final.

An endpoint Id is unique in its effective registry. Repeating it in a descendant layer is a sparse override, not
another scheduler unit. An explicit empty `PublicationEndpoints: []` clears that inherited branch.

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

Every enabled root `Publication` receives an implicit direct publication finalization action equivalent to:

```yaml
PublicationFinalizationActions:
  - Id: build-record
    ExecutionEnabled: true
    PublicationFinalizationActionAdapter: modustro-build-record
```

The resulting sidecar name is exactly:

```text
<published-filename>.modustro-build-record.yml
```

The complete published filename is preserved, including classifier and compound extension where applicable.

The implicit action can be configured or disabled by declaring the same stable `Id` explicitly:

```yaml
PublicationFinalizationActions:
  - Id: build-record
    ExecutionEnabled: false
```

Implicit build-record actions are added only to root publications. A publication finalization action never receives another
implicit build-record action; any deeper action must be declared explicitly. This prevents unbounded recursion.

A package index that cannot store arbitrary sidecars, such as a Python package index, should explicitly disable the
implicit build record or redirect it to a metadata-capable `PublicationEndpoint` with
`TargetPublicationEndpointId`.

Directory transports preserve the published relative parent path when placing the sidecar: a payload published as
`nested/payload.txt` receives `nested/payload.txt.modustro-build-record.yml` below its endpoint root. Maven transports
retain the actual resolved filename, including the reserved timestamp/build-number of a snapshot. A sidecar is not a
second artifact publication and its retry does not republish the parent payload.

## 7. Finalization declarations and recursive dependencies

| Consistency boundary | Declaration | Adapter property | Result supplied to the adapter |
| --- | --- | --- | --- |
| Concrete publication | `PublicationFinalizationActions` | `PublicationFinalizationActionAdapter` | Successful parent and ordered ancestor lineage. |
| Child of a publication action | `FinalizationActions` | `FinalizationActionAdapter` | Successful immediate parent and ordered ancestor lineage. |
| Output | `OutputPublicationFinalizationActions` | `OutputPublicationFinalizationActionAdapter` | All publication trees of this output. |
| Logical artifact | `ArtifactPublicationFinalizationActions` | `ArtifactPublicationFinalizationActionAdapter` | All output results of this artifact. |
| Version Scope | `VersionScopePublicationFinalizationActions` | `VersionScopePublicationFinalizationActionAdapter` | All artifact results in this shared version boundary. |

There are four finalization levels; the child row is recursion within the publication level, not a fifth boundary.
Only publication-level actions are recursive. Output, artifact, and Version Scope lists are flat and run after the
complete lower trees, including best-effort work, reach their terminal results and meet the boundary's success condition.
A missing expected output is incomplete, not an empty successful publication.

### 7.1 Fields and defaults

| Field | Meaning / default |
| --- | --- |
| `Id` | Required stable sibling merge identity; lowercase dash-separated name. |
| `ExecutionEnabled` | `true`; a sparse `ExecutionEnabled: false` disables the inherited action. |
| Level-specific adapter property | Required for an enabled effective action; selects the typed catalog entry above. |
| `ExecutionOrder` | `0`; negative values allowed; compares direct siblings within this list. |
| `Configuration` | Adapter-specific non-secret values. |
| `ExecutionFailurePolicy` | `fail_build_on_failure`, `propagate_failure`, or `ignore_failure`; default `fail_build_on_failure`. |
| `RetryCount` | Additional attempts after the first; default `0`. |
| `WaitForNextAttemptMillis` | Default `1000`. |
| `AttemptTimeoutMillis` | Optional positive per-attempt timeout. |
| `ShowProgressIfPossible` | Default `true`. |
| `TargetPublicationEndpointId` | Optional on recursive publication actions only; otherwise the root endpoint is inherited. |
| `FinalizationActions` | Recursive children of a publication-level action only. |

`PublicationFinalizationActions` is placed under a `Publications` item. `OutputPublicationFinalizationActions` is placed
in the selected output's Snapshot/Release branch. Artifact and Version Scope lists have their own top-level
Snapshot/Release branches in defaults or structural descriptors.

```yaml
OutputPublications:
  - OutputSelector: native_product_binaries
    TechnologyKind: java
    Release:
      PublicationEnabled: true
      PublicationEndpoints:
        - Id: java-release
          ExecutionEnabled: true
          PublicationUri: https://maven.example.invalid/releases/
          PublicationAdapter: maven-repository
          ExecutionOrder: 0
          Publications:
            - Id: standard
              ExecutionEnabled: true
              PublicationFinalizationActions:
                - Id: create-index
                  ExecutionEnabled: true
                  PublicationFinalizationActionAdapter: example-create-index
                  ExecutionOrder: 0
                  FinalizationActions:
                    - Id: sign-index
                      FinalizationActionAdapter: example-sign-content
      OutputPublicationFinalizationActions:
        - Id: output-summary
          OutputPublicationFinalizationActionAdapter: example-output-summary

ArtifactPublicationFinalizationActions:
  Release:
    - Id: artifact-summary
      ArtifactPublicationFinalizationActionAdapter: example-artifact-summary

VersionScopePublicationFinalizationActions:
  Release:
    - Id: release-summary
      VersionScopePublicationFinalizationActionAdapter: example-release-summary
```

The `example-*` adapters illustrate registration extension points; they are not built-in adapters. The built-in
`modustro-build-record` action is implicit unless disabled, including in this example.

### 7.2 Hard parent barrier and data flow

`sign-index` cannot start until `create-index` succeeds. If B requires A's produced content, B must be nested under A;
`ExecutionOrder` must not be used to imitate a data dependency. A recursive action can generate and publish a derived artifact,
or perform another adapter-defined operation that depends on its immediate parent's successful attempt.

### 7.3 Local ExecutionOrder

`ExecutionOrder` compares only direct siblings of one parent. For example:

```yaml
PublicationFinalizationActions:
  - Id: prepare
    ExecutionOrder: -10
    PublicationFinalizationActionAdapter: example-prepare
  - Id: notify-a
    ExecutionOrder: 0
    PublicationFinalizationActionAdapter: example-notify
  - Id: notify-b
    ExecutionOrder: 0
    PublicationFinalizationActionAdapter: example-notify
```

`prepare` finishes before `notify-a` and `notify-b` become eligible. Both order-0 actions may run concurrently.
A later sibling group waits for the direct attempts in the previous group, not their recursive descendants.
If A has order 0 and child A1, and sibling B has order 1, B can start after A succeeds while A1 is still running.
If B needs A1's output, B belongs under A1. Higher flat lists also use ordered groups, but have no child trees.

Required failure prevents later groups at that level. Ignored failure permits later siblings, but children of an
unsuccessful parent are skipped. Their observable futures terminate exceptionally instead of remaining pending.

## 8. Execution lineage and content URI semantics

Every publication finalization action attempt receives the complete ordered ancestor lineage from the root publication to its immediate
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

Configuration and result metadata are deep-frozen, including nested maps and collections. Higher contexts retain
complete execution trees: payload/endpoint information, publication result, recursive action results, and diagnostic
failures. They expose no mutable live BuildService or scheduler state. `completed: false` identifies an expected output
that never executed; terminal status and successful status are separate concepts.

## 9. Typed adapter contracts and catalog

The immutable `AIcAdapterCatalog` indexes six categories by adapter Id:

| Category | Portable interface |
| --- | --- |
| Input subscription | `AIiSubscriptionAdapter` |
| Publication transport | `AIiPublicationAdapter` |
| Recursive publication finalization | `AIiPublicationFinalizationActionAdapter` |
| Output finalization | `AIiOutputPublicationFinalizationActionAdapter` |
| Artifact finalization | `AIiArtifactPublicationFinalizationActionAdapter` |
| Version Scope finalization | `AIiVersionScopePublicationFinalizationActionAdapter` |

Root `PublicationFinalizationActionAdapter` and child `FinalizationActionAdapter` names resolve within the same
recursive publication-finalization category. One implementation/module can implement multiple typed interfaces.
Duplicate Ids within a category and unknown adapter lookups are rejected. Bootstrap registers adapters explicitly;
automatic external plugin discovery is not implemented.

Each publication/finalization adapter executes exactly one attempt. The scheduler owns ordering, retry, timeout,
failure handling, cancellation, and progress. A recursive action receives its configuration, complete ancestor lineage,
input URI, frozen root payload, optional resolved target endpoint, credentials, attempt/deadline/cancellation information,
progress reporter, and a publication delegate for derived publications. Higher typed contexts receive immutable output,
artifact, or Version Scope results; they do not invent one shared input URI for a collection of outputs.

Built-in transports include Maven HTTP repositories, local Maven repositories, Python package repositories,
local filesystem copy, HTTP directory, Git branch, and S3-compatible object storage. The publication-finalization
category supplies `modustro-build-record`; Version Scope supplies `modustro-remove-corresponding-snapshots` and
`modustro-refresh-docs-site`. Output and artifact catalogs currently contain no built-in actions.
Subscription catalog entries identify bootstrap adapters; actual dependency repository resolution remains with the
technology/Gradle integration. Registering a subscription entry alone does not execute a new dependency resolver.

Progress may be determinate (completed/total/unit) or indeterminate (message). Adapters cooperate with deadline and
cancellation; cancellation cannot undo remote side effects or guarantee immediate termination of an uncooperative adapter.
Retry requires declared retry safety; increasing `RetryCount` does not make a non-idempotent operation safe automatically.
Credential resolution and an unsuccessful returned action result are attempt failures governed by the same scheduler policy.

## 10. TargetPublicationEndpointId

A publication finalization action inherits its root publication endpoint by default. If it must act on or publish through another endpoint,
it references that endpoint by stable Id:

```yaml
PublicationFinalizationActions:
  - Id: publish-build-record
    PublicationFinalizationActionAdapter: modustro-build-record
    TargetPublicationEndpointId: metadata-sidecars
```

The effective publication plan carries a registry of endpoints from both Snapshot and Release branches of the same
resolved output. This permits a release action to target a configured snapshot endpoint without duplicating URI,
credentials, or provider configuration.

`TargetPublicationEndpointId` belongs only to recursive publication finalization. Higher adapters obtain endpoint
registries from their complete lower results and resolve only credentials needed by their own attempt. A target must
exist in the effective registry; the Id is not a URI, credential name, or permission to discover arbitrary endpoints.

## 11. Version Scope lifecycle, cleanup, and repository docs

A Version Scope is the structural boundary owning an inherited logical version. It may contain multiple artifacts,
output technologies, transport identities, and isolated Gradle build domains. A descendant version declaration creates
a separate boundary. Artifact aggregation uses logical artifact path, scope, version, and stability; differing Java/Maven
and Python identities must not split one artifact into unrelated boundaries.

### 11.1 Corresponding-snapshot cleanup

Snapshot cleanup is a Version Scope finalizer, not a top-level `manage` endpoint, a separate Gradle cleanup phase,
or a descendant of every successful release publication. It runs once after all native outputs/artifacts in the
release boundary succeed and deduplicates targets from the complete result tree.

```yaml
VersionScopePublicationFinalizationActions:
  Release:
    - Id: remove-corresponding-snapshots
      VersionScopePublicationFinalizationActionAdapter: modustro-remove-corresponding-snapshots
      ExecutionOrder: 10
      ExecutionFailurePolicy: ignore_failure
    - Id: refresh-docs-site
      VersionScopePublicationFinalizationActionAdapter: modustro-refresh-docs-site
      ExecutionOrder: 20
```

The cleanup adapter uses the configured snapshot endpoints in each output result. It has artifact identity/version,
endpoint URI/configuration, and a lazy credential resolver; no duplicate management endpoint metadata is needed.

| Provider | Technology | Removal boundary |
| --- | --- | --- |
| Cloudsmith | Java/Maven | Exact corresponding release-SNAPSHOT package version, including its classifiers. |
| Cloudsmith | Python | All package instances in the matching release.dev* series. |
| Repsy | Java/Maven | Exact corresponding release-SNAPSHOT artifact version through the management API. |
| Repsy | Python | Matching release.dev* package versions through the management API. |

`Configuration.Provider` can select `cloudsmith` or `repsy`; otherwise the endpoint host identifies the provider where
supported. Cloudsmith uses `Workspace`/`Repository`, Repsy uses `Repository`, and `ManagementApiUri` can override the
management API root. Credentials resolve lazily inside the cleanup attempt, so resolution errors obey its retry/failure
policy. Already-absent versions are successful no-ops. MPS has no configured cleanup contract.

Cleanup is governance policy, not standard Maven release behavior. `ignore_failure` records a cleanup failure without
invalidating the successful release boundary; required policy would fail that finalization instead.

### 11.2 Repository docs refresh

`modustro_docs_site` is repository-only, may aggregate multiple Version Scopes/versions, and is excluded from native
artifact and scope-completion barriers. The docs adapter returns a refresh request; it does not publish the site itself.

```yaml
VersionScopePublicationFinalizationActions:
  Snapshot:
    - Id: refresh-docs-site
      VersionScopePublicationFinalizationActionAdapter: modustro-refresh-docs-site
      ExecutionOrder: 20
```

The invocation service accepts requests from COMPLETE scopes, deduplicates them, and refreshes the aggregate repository
site after native finalization. `modustroPublish` integrates `refreshModustroDocsSite`; explicit `publishModustroDocsSite`
remains available. Only the repository-root build domain publishes the aggregate docs site; included domains return
refresh requests to the owner. Docs index generation runs after scope finalization, not in a cycle before native export.

### 11.3 Expected outputs and attempt records

Before native publication tasks execute, a preparation task supplies the expected-output manifest to the invocation
BuildService. Descriptor-declared enabled outputs without registered producers/tasks remain incomplete. Tasks whose
effective publication policy is disabled are excluded. A scope with no native publication attempted is not finalized
merely because its descriptor exists.

Partial attempts remain FAILED and suppress release cleanup/docs refresh. A later fresh invocation may repair publication;
automatic continuation of a partially committed invocation is not implemented. The state model is PUBLISHING,
FINALIZING, COMPLETE, and FAILED. Human-readable scope records reside below
`build/run/publication-records/<sanitized-invocation-and-fingerprint>/version-scopes/`, with collision-resistant names,
temporary-file writes, and atomic replacement where supported.

An existing COMPLETE record cannot be replaced under the same invocation identity. Native publication is refused after
that scope is frozen in the invocation; the portable scheduler returns an already-COMPLETE scope unchanged. These are
local invocation guards, not a distributed transaction or a guarantee of provider-side release immutability. Removing
the local run workspace does not undo remote publications.

### 11.4 Coordination across isolated Gradle builds

Each participating domain completes its output/artifact trees and commits an immutable `artifact-results` packet.
The domain owning the Version Scope aggregates expected domains/artifacts and waits for included-domain finalization
receipts before running scope finalizers. Missing producers/domains, incomplete outputs, stale identities, conflicting
plans, and duplicate contributions prevent COMPLETE instead of being treated as empty success.

Bridge format version 1 uses ordinary JSON-compatible values. It reconstructs immutable Core result trees, retaining
endpoint registries, content URIs, recursive results, configurations, metadata, and exception type/message/cause diagnostics.
Credential-profile definitions/names cross the bridge; resolved secrets do not. Credentials are resolved lazily against
the originating domain's directory. Java serialization, shared live objects, and classloader identity assumptions are
not part of the protocol. Custom metadata must be JSON-compatible; arbitrary Java objects cannot cross this boundary.

Packets reside below `build/run/publication-coordination/<invocation-fingerprint>/` in `artifact-results` and
`scope-finalization` stages. Writes are locked and atomic where supported; replaying identical committed bytes is allowed,
but replacing them with different results is rejected. This is invocation-local filesystem coordination.

All domains use one fresh `MODUSTRO_BUILD_INVOCATION_ID` or `-Pmodustro.build.invocationId=<fresh-id>`.
The phase controller establishes the environment identity and preserves a supplied CI identity across phases/children.
Direct composite publication without a common identity fails before native upload. A new native attempt needs a fresh
identity; a later explicit docs-only refresh can read existing receipts without publishing native outputs again.

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

## 13. Failure, retry, and completion semantics

Publication endpoints and finalization actions use the same `ExecutionFailurePolicy`, whose canonical schema type is
`BuildExecutionFailurePolicy`. The values are `fail_build_on_failure`, `propagate_failure`, and `ignore_failure`.
The scheduler tracks local execution outcome separately from the failure flow leaving an execution boundary.

| Failure arriving at the boundary | `fail_build_on_failure` | `propagate_failure` | `ignore_failure` |
| --- | --- | --- | --- |
| No failure | `NONE` | `NONE` | `NONE` |
| Local execution failure | `BUILD_FAILURE` | `PROPAGATED_FAILURE` | `NONE` |
| Propagated descendant failure | `BUILD_FAILURE` | `PROPAGATED_FAILURE` | `NONE` |
| Existing terminal `BUILD_FAILURE` from a descendant | `BUILD_FAILURE` | `BUILD_FAILURE` | `BUILD_FAILURE` |

`BUILD_FAILURE` is terminal and cannot be absorbed by any ancestor. `PROPAGATED_FAILURE` is offered to the immediate
parent boundary, which may fail the build, propagate the failure again, or ignore it. An unhandled propagated failure
that reaches the build root fails the build. Ignored and propagated failures remain visible in execution results even
when a parent completes its own local work successfully.

Retries belong only to the failing scheduler unit: retrying a build-record action does not republish its parent or
rerun another successful root. Retry-capable adapters declare safety. Timeouts and cancellation are scheduler-owned;
adapters execute one attempt and cooperate with the supplied deadline/cancellation context.

`requiredCompletion` awaits required roots and required descendant work, while unrelated ignored work may continue.
Direct root/sibling order gates wait only for direct attempts. Complete output results, higher finalization barriers,
and scheduler shutdown drain the full lower graph, including ignored work, so they expose terminal immutable trees.

An ignored transport failure can leave required completion successful but still prevent output/artifact/scope success,
because the payload was not successfully published. An ignored finalizer failure is recorded but does not invalidate
that completed boundary. Expected-but-unexecuted work remains incomplete. An unsuccessful parent cannot activate its
children even when its own failure was ignored.

## 14. Hierarchical merge and defaults ownership

At each descriptor/default layer:

1. Selector groups expand to concrete output/input kinds.
2. Input/output declarations are keyed by `TechnologyKind` plus concrete selector.
3. Endpoint/subscription lists merge by stable `Id`.
4. `Publications` merge by stable `Id`.
5. Root `PublicationFinalizationActions` and recursive child `FinalizationActions` merge by stable sibling `Id`.
6. Output, artifact, and Version Scope flat lists merge by stable `Id` independently for Snapshot and Release.
7. Sparse descendants inherit unspecified properties; an explicit empty list clears that inherited list.

A repeated Id is one effective action, not duplicate execution. The same Id can exist under another parent or in
another stability branch because those are separate merge locations. A descriptor can disable just one inherited action
with `ExecutionEnabled: false` (not `PublicationEnabled: false`), without repeating its adapter or retry configuration:

```yaml
VersionScopePublicationFinalizationActions:
  Release:
    - Id: remove-corresponding-snapshots
      ExecutionEnabled: false
```

`Release: []` clears the entire inherited release finalizer list. Omitting Release inherits it; an empty Snapshot list
does not clear Release. Clearing all release finalizers also removes inherited docs refresh, so disable one Id when that
is the intended change.

### 14.1 Public policy and private overlay

General policy (release snapshot cleanup and successful-scope docs refresh) is owned by
`pub.gov.Algites/repository/defaults/algites-repository-defaults-public.yml`. Public governed endpoints, credentials,
and provider-specific configuration belong in the optional
`priv.gov.Algites/repository/defaults/algites-repository-governed-defaults-public.yml` overlay. That overlay must not
repeat an unchanged general policy. It may sparsely override an action when a governed-specific difference is intended.
Identical declarations in both layers merge to one action, but duplication can mask future public-policy changes.

The resolver applies built-in defaults, then public defaults, governed public overlay, private defaults, and structural
repository/artifact-set/artifact descriptors. The external file inputs, in that order, are:

- `ALGITES_REPOSITORY_PUBLIC_DEFAULTS_FILE`
- `ALGITES_REPOSITORY_GOVERNED_PUBLIC_DEFAULTS_FILE`
- `ALGITES_REPOSITORY_PRIVATE_DEFAULTS_FILE`

An explicit public-defaults path overrides the bundled copy. Without it, public defaults come from the current
GradleInit JAR and are materialized in a content-hash directory below the Gradle user-home cache. This fallback does
not fetch GitHub and stays aligned with the installed plugin version. Governed/private overlays are explicit optional
inputs; they are not downloaded by the public fallback. Nonexistent explicitly configured files fail resolution.

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

## 16. Design invariants and verification boundary

- Effective `TechnologyKind` is known and singular; repository docs/schema outputs use `modustro`.
- Input resolution and output publication are separate; destinations exist only under `OutputPublications`.
- Four typed finalization boundaries distinguish recursive publication work from flat higher work.
- Tree nesting represents hard dependency/data flow; `ExecutionOrder` schedules only direct siblings.
- Each content step has one canonical input URI and, when produced, one output URI.
- Recursive contexts expose ancestors but no sibling state; higher contexts expose complete immutable lower trees.
- Secrets never appear in governance endpoint definitions or persisted cross-domain packets.
- Implicit build records apply only to root publications and preserve the actual published filename/path.
- Required `GroupId` resolves before publication planning.
- Missing expected work cannot become a successful empty artifact or Version Scope.
- One logical artifact may contain outputs with different technologies and transport identities.
- A shared scope finalizes only at its owner after all participating domains contribute complete results.
- COMPLETE records/packets cannot be rewritten under one invocation identity; native retries use a fresh identity.
- Repository docs refresh is deduplicated after COMPLETE scope results and is not a native completion prerequisite.

Portable regressions cover publication graphs, finalization barriers, Maven/build-record snapshot pairing, cleanup,
and domain bridge/store behavior. Gradle/TestKit checks cover real script compilation, configuration-cache reuse,
shared-scope composite publication, missing common identities/producers, and immutable committed invocation guards.
The actual public repository's publication graph has also been configured with the rebuilt plugin. Bootstrap build
and executed validation evidence are recorded in the checkpoint's `VALIDATION.md`.

Provider cleanup tests use mock HTTP. A passing graph/test suite does not claim that production Cloudsmith/Repsy
credentials, remote uploads, or remote cleanup have been exercised. Those are integration checks in the target environment.

