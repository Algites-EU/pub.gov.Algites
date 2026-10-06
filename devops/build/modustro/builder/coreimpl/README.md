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

Phase 3 adds the build-output producer layer and activates it in the Gradle adapter:

- hard-wired producers for `java_classes_jar`, `java_sources_jar`, `java_javadoc_jar`, `python_wheel`, and `python_sdist`;
- a producer registry keyed by `TechnologyKind + BuildOutputType`;
- portable production plans consuming `PreparedSourceSet`;
- built-in default output selection through the existing TechnologyKind definitions;
- explicit rejection of virtual dependency-only outputs such as `python_distribution` as directly producible outputs.

Phase 4 adds demand-driven capabilities. Production plans declare their capability requirements, `AIcBuiltinCapabilityDemandPlanner` deduplicates them and expands prerequisites, and the active Gradle adapter materializes the resulting source-processing and dependency-resolution boundaries. Native documentation is represented by the same graph rather than by a separate dependency chain.

Phase 5 adds the effective external-I/O pipeline:

- `AIcInputSubscriptionResolver` merges inherited subscriptions by technology, selector and stable `Id`;
- `AIcPublicationConfiguration` expands output selectors and recursively merge-composes publication endpoints, concrete
  publications and recursive publication finalization actions, plus flat higher finalization lists;
- `AIcPublicationPlanner` creates independent root publication jobs, injects the implicit root build-record action and
  resolves the cross-lane endpoint registry used by target actions;
- `AIcPublicationScheduler` owns root/local order, complete output/artifact/Version Scope barriers, retries, timeouts,
  cancellation, failure policy, progress and required-completion propagation;
- transport and finalization adapters execute one attempt and remain independent of Gradle;
- `AIcBuiltinAdapterCatalog` registers all six adapter categories, including Version Scope snapshot cleanup and
  repository docs-refresh requests.

Portable regression entry points cover recursive scheduling, all four barriers, retry/timeout and credential failures,
missing outputs, immutable snapshots, Maven build-record transport and Cloudsmith/Repsy snapshot cleanup. TestNG wrappers
call the same check classes. The portable domain bridge additionally round-trips complete immutable result trees,
validates missing/stale/duplicate domain results, and checks immutable atomic handoff storage. Gradle orchestration
has separate TestKit coverage in `gradleinit`.

Run `bash devops/build/modustro/builder/coreimpl/verify-publication-core.sh` from the repository root with JDK 17 or newer.
This checks the publication/subscription/catalog packages and all core interfaces without external dependencies;
it does not replace the full CoreImpl, Gradleinit, TestNG or generated-definition bootstrap checks.

The optional `builder/structureddata/jackson` artifact supplies YAML/JSON/XML representation mapping without putting Jackson on the core/bootstrap dependency path.


## Phase 6 publication services

Phase 6 adds Gradle-independent publication services. `AIcGlobalPublicationPathValidator` enforces the canonical path contract at the publication trust boundary, and `AIcGlobalPublicationDeployMetadataResolver` owns draft revision advancement, draft-to-release transition, and released-content immutability. Storage/provider adapters remain outside Builder Core.
