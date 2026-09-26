[[PROPOSAL]]
# Algites Development Lifecycle Specification
**Version:** 2.0-draft
**Status:** Consolidated proposal


## 1. Introduction


### 1.1. Algites CI Policy

This document defines the **standard build, CI, release, and publication lifecycle** for Algites repositories. The common lifecycle is provider-independent. GitHub Actions is the current default CI implementation, but provider-specific workflows are adapters around the same Algites build and release model and MUST NOT become the semantic source of truth.

The goal is to provide a predictable, low-friction workflow:

- Keep **Gradle** as the single Algites build orchestrator across all supported TechnologyKinds; Maven is supported only as a Java publication/consumption compatibility format.
- Enable **safe test-only runs** on dedicated branches without publishing.
- Enable **fast compile-only runs** on feature branches.
- Support Algites-style **variant branches** (e.g. `jvm17/*`, `jvm21/*`, `and21/*`, `mps2025.1/*`) naturally.

> In Algites, a *variant* prefix encodes the **target platform baseline**, e.g. `jvm17` (JVM runtime ≥ 17), `and21` (Android minSdk 21), or `mps2025.1` (MPS toolchain version).

---

## 2. Integration of VCS-hook-initiated CI actions

The integration of VCS-hook-initiated CI actions includes the continuous integration of the compilation, test execution and packaging happening automatically after the commit to the remote VCS repository according to the commit branch which is executed.
Creation of the releases or nightly build deployments to the artifact repositories is not the part of the CI but is initiated independently from the general work with the repositories

### 2.1. Common Build and CI Tool Integration

#### 2.1.1. Terminology

- **Provider stub/workflow**: a small provider-specific entry point committed inside a repository (for GitHub, typically a file below `.github/workflows/`). It reacts to provider events and delegates to the shared Algites lifecycle entry points.
- **Algites lifecycle implementation**: shared provider-independent build/release logic, primarily exposed through Gradle and centrally governed scripts/actions. It is responsible for:
  - resolving repository/artifact metadata,
  - determining the selected TechnologyKinds,
  - selecting lifecycle mode from branch/event policy,
  - resolving technology-kind-specific toolchains only for selected technology kinds,
  - running construction, verification, packaging, documentation, and publication operations,
  - producing provider-neutral diagnostics and build traceability metadata.
- **Provider adapter**: optional integration that maps provider facilities such as tokens, issue links, summaries, and manual-dispatch inputs to the provider-independent lifecycle.

---

#### 2.1.2. Modes (High-Level Behavior)

The CI pipeline chooses a **mode** based on the branch name:

- **approval**: run all tests with packaging.
- **verification**: run all tests only.
- **construction**: compile only of all product and develop classes.
- **skip**: exit early; no construction/verification/approval.

---

#### 2.1.3. Branch Naming Rules

##### 2.1.3.1 Verification branches

Branches containing the following names are treated as to be verified on push:

- `main`
- `master`
- `hotfix`
- `lane/*`
- `*/main`
- `*/master`
- `*/hotfix`
- `*/lane/*`

Verification includes the start of the unit as well integration tests and creation of the production packages.

Examples:

- `main`
- `jvm17/hotfix`
- `jvm21/lane/1.1`
- `and21/master`

**Mode = verify**

---

##### 2.1.3.2 Test-only branches

Branches containing the segment `develop` are treated as test-only, where not packaging is executed, but the tests and integration tests are:

- `develop`
- `*/develop`

Examples:

- `develop`
- `jvm17/develop`
- `and21/develop`

**Mode = test**

---

##### 2.1.3.3 Compile-only feature branches

Feature branches are compiled without tests:

- `feature/*`
- `*/feature/*`

Examples:

- `feature/ci-experiment`
- `jvm17/feature/ID.123_new-compat`

**Mode = compile**

---

##### 2.1.3.4 All other branches

All branches not matching the above patterns are treated as:

**Mode = skip**

#### 2.1.4. (reserved)

#### 2.1.5. Build Tool - Gradle

The unified Algites build is orchestrated by Gradle in all repositories and for all supported TechnologyKinds. Gradle coordinates technology-kind-specific adapters; an adapter MAY delegate execution to a native ecosystem toolchain (for example Python packaging tools or MPS tooling) while preserving a deterministic Gradle task graph.

Java artifacts may additionally publish Maven-compatible metadata and packages for Maven consumers. Maven is not an Algites build tool. Python and other TechnologyKinds publish through their own technology-kind-specific repository protocols and package formats.

##### 2.1.5.1 Gradle bootstrap handling

###### 2.1.5.1.1 Scope and lifecycle classification

Gradle bootstrap handling defines **how repositories and plugin resolution are initialized** before any project or build logic is evaluated.

Within Algites, bootstrap handling is treated as a **Lifecycle concern**, not a structural one. It determines:

- where Gradle resolves plugins from
- where dependencies are resolved from
- how public and private trust domains are separated
- how reproducibility and auditability are achieved

---

###### 2.1.5.1.2 Design decision: no shared pre-settings bootstrap

Algites explicitly **does not use any shared bootstrap mechanism executed before `settings.gradle.kts`**.

The following approaches were evaluated and rejected:

- Git-shared bootstrap (subtree / submodule)
- External `settings`-applied Kotlin scripts
- Global Gradle init scripts (`~/.gradle/init.gradle`)
- Maven-style external settings (`settings.xml`-like behavior)

Reasons for rejection:

- excessive tooling complexity
- IDE incompatibilities
- hidden or non-versioned configuration
- high onboarding and operational cost

Instead, Algites adopts **explicit, repository-local bootstrap configuration**.

---

###### 2.1.5.1.3 Normative rule

> **Every repository defines its own Gradle bootstrap explicitly in its root `settings.gradle.kts`.**

There is:

- no shared pre-bootstrap
- no implicit external configuration
- no cross-repository initialization logic

This rule favors:

- clarity over indirection
- explicitness over magic
- reproducibility over centralization

---

###### 2.1.5.1.4 Bootstrap responsibilities

The bootstrap defined in `settings.gradle.kts` is responsible only for the repositories and settings required **before** shared Algites build logic and artifact metadata can be resolved. Typical responsibilities include:

- Gradle plugin resolution,
- retrieval of the minimal shared Algites build infrastructure when needed,
- repository-local Gradle initialization required by the selected Gradle distribution.

It must **not** become the authoritative configuration for artifact publication repositories, artifact versions, TechnologyKind selection, or build behavior.

###### 2.1.5.1.5 Bootstrap repositories vs Algites artifact repositories

Two repository concepts are intentionally distinct:

- **Bootstrap repositories** are explicit and repository-local in `settings.gradle.kts`. They exist only to make Gradle and shared build infrastructure resolvable.
- **Algites artifact repositories** are resolved after metadata loading from the publication repository matrix defined by TechnologyKind x visibility (`public`/`private`) x stability (`release`/`snapshot`) x URL usage (`download`/`upload`/`manage`). The matrix inherits from Algites defaults through `algites-source-repository.yml` and nested `algites-artifact.yml` files.

A Java/Maven repository used for dependency resolution or publication is therefore not automatically a bootstrap repository. Python, MPS, and future technology-kind-specific repositories are resolved by their adapters and effective artifact metadata.

###### 2.1.5.1.6 Public vs private trust domains

Public repository bootstrap MUST NOT require private credentials or private-only endpoints. Private repositories MAY add private bootstrap endpoints when they are actually required for bootstrap. Artifact publication visibility is enforced independently by the effective Algites publication repository matrix and repository visibility policy.

###### 2.1.5.1.7 Change management

Bootstrap changes should remain rare, deliberate, explicit, and repository-local. Publication repository changes belong in Algites metadata/defaults and SHOULD NOT require copying technology-specific repository URLs into every `settings.gradle.kts`.

###### 2.1.5.1.8 Normative summary

- `settings.gradle.kts` remains the explicit source of Gradle bootstrap configuration.
- No hidden global Gradle init/bootstrap mechanism is required.
- Bootstrap configuration is minimal and distinct from the Algites publication repository matrix.
- Artifact-specific repository policy is resolved only after Algites metadata and TechnologyKind adapters are available.


---

##### 2.1.5.2. What Gets Executed (by Mode and TechnologyKind)

Lifecycle mode and TechnologyKind selection are independent dimensions. For every targeted artifact/cascade, the lifecycle first resolves the effective selected `technologyKinds` and then asks each selected TechnologyKind adapter to contribute the tasks required by the mode.

###### 2.1.5.2.1 Mode = approval

Run full verification plus packaging for all selected TechnologyKinds. Publication is a separate explicit lifecycle operation unless a concrete release action requests it.

###### 2.1.5.2.2 Mode = verification

Run all applicable unit/integration verification for the selected TechnologyKinds without requiring publication.

###### 2.1.5.2.3 Mode = construction

Construct/compile/generate the selected product and develop sources without running the full verification suite.

###### 2.1.5.2.4 Mode = skip

Exit early after logging the decision.

###### 2.1.5.2.5 TechnologyKind selection

- If no technology-kind filter is supplied, all effective artifact `technologyKinds` are selected.
- An explicit filter may select one or more technology kinds, for example only `java` or only `python`.
- Artifacts in a cascade that do not support a requested technology kind are skipped for that technology kind.
- Selecting several technology kinds does not require their task graphs to be coupled; Gradle executes only real task dependencies.
- Shared transformations MAY be shared tasks; technology-specific transformations MAY run independently and remain independently cacheable when their inputs/outputs permit it.

---

##### 2.1.5.3. TechnologyKind-Specific Toolchain Resolution

Toolchains are resolved only for selected TechnologyKinds. Every TechnologyKind adapter owns its resolution rules and diagnostics.

For the Java adapter, unless a more specific Algites metadata rule overrides it, the current compatibility resolution order remains:

1. `.java-version` (single integer per line, e.g. `17`)
2. `gradle.properties` containing `javaVersion=<n>`
3. Algites Java default (currently `17`)

Python, MPS, and future adapters MUST define equivalent deterministic resolution rules before their TechnologyKinds are enabled.

---

##### 2.1.5.4. Development Environment Preparation

Algites defines repository-level Gradle lifecycle tasks for deterministic preparation of development metadata required by IDEs and ecosystem tooling. These tasks are provider-independent and MUST remain usable without a specific IDE.

###### 2.1.5.4.1 `prepareDevelopment`

`prepareDevelopment` is the canonical idempotent preparation task. It SHOULD:

- resolve repository and artifact metadata required for development tooling;
- create or update derived development descriptors for all relevant artifacts; when no preparation technology-kind filter is supplied, all effective TechnologyKinds are prepared;
- generate Python `pyproject.toml` files from resolved Algites metadata plus optional committed `pyproject.toml.tpl` files;
- preserve unrelated user/development state;
- perform no release/publication operation.

A freshly cloned repository MUST be able to reach a normal IDE-ready state by running:

```text
./gradlew prepareDevelopment
```

Generated development descriptors remain derived data and MUST NOT become independent sources of truth.

###### 2.1.5.4.2 `refreshDevelopment`

`refreshDevelopment` forces recreation of Algites-managed derived development metadata and then establishes the same final state as `prepareDevelopment`. It MAY remove only metadata that the Algites development lifecycle owns. It MUST NOT behave as a general deletion of user IDE settings or unrelated local files.

###### 2.1.5.4.3 Relationship to `clean`

The standard `clean` lifecycle remains primarily destructive for build/generated-source outputs. Normal build/runtime outputs are centralized below the repository-level `build/run` workspace; `clean` MUST remove the applicable Algites run workspace and SHOULD remove reproducible `src/{product|develop}/*.gen` directories according to their technology/source-generation lifecycle.

Derived working metadata needed by IDEs, such as generated `pyproject.toml`, SHOULD remain present across ordinary `clean` operations. This avoids destabilizing an open IDE project and avoids giving `clean` the surprising behavior of deleting and immediately regenerating development descriptors.

Build/package tasks that consume generated development metadata MUST depend on the corresponding generation task so that a retained descriptor cannot silently become stale.

###### 2.1.5.4.4 JetBrains IDE/MPS startup bootstrap

Algites repositories that maintain shared JetBrains project metadata SHOULD commit a minimal shared JetBrains project bootstrap that invokes `./gradlew prepareDevelopment` as a project Startup Task when the repository is opened in a supporting JetBrains IDE. This integration is intended to make a fresh clone usable without a manual preparation step while keeping Gradle as the actual implementation.

Rules:

- the bootstrap is repository-level, not duplicated per artifact;
- only shareable project configuration required to invoke `prepareDevelopment` SHOULD be committed; user-specific workspace state remains ignored;
- the physical project-configuration location MAY differ by JetBrains product (for example IntelliJ-family `.idea` project files versus MPS-specific project configuration); the repository SHOULD follow the native product convention rather than forcing one path on all products;
- IDE project-trust / safe-mode behavior MUST be respected; the bootstrap MUST NOT attempt to bypass the IDE security model;
- the startup integration is an optional convenience layer. `./gradlew prepareDevelopment` remains the portable source of behavior for non-JetBrains IDEs and command-line development.

The committed startup configuration itself is stable bootstrap metadata. It SHOULD NOT contain generated artifact-specific Python identity/version data; those values are produced by `prepareDevelopment`.

##### 2.1.5.5. Issue references (optional but recommended)

Algites CI can automatically detect **GitHub Issue references** related to a build and display them in the
Actions **Job Summary** as clickable links.

###### 2.1.5.5.1 Branch naming convention (strict)

For feature branches, prefix the feature “slug” with an explicit **ID marker** and terminate it with an underscore:

- `feature/ID.123_some-description`
- `jvm17/feature/ID.123_some-description`
- `feature-testrun/ID.123_ci-experiment`

Cross-repository shorthand (Algites-EU only) is also supported:

- `feature/ID.pub.tool.Java-123_some-description`
- `j25/feature-testrun/ID.pub.tool.Java-123_ci-experiment`

> The `ID.` prefix is **required** in branch names to avoid accidental matches (e.g. variant branches like `jvm17/*` or `and21/*`).
> The underscore `_` acts as the delimiter that ends the issue reference.

###### 2.1.5.5.2 Commit message convention (flexible)

In commit subjects, CI detects references introduced by `#`.

Supported forms:

- `#123` (issue in the same repository)
- `#ID.123` (same as `#123`)
- `#pub.tool.Java-123` (cross-repo shorthand, Algites-EU only)
- `#ID.pub.tool.Java-123` (same as above)

###### 2.1.5.5.3 What CI does with it

If issue references are detected (from branch name and/or commit subjects), CI will:

- list them as clickable links in the Actions **Job Summary**
- emit log notices for quick scanning

> Note: this is informational only. Posting comments to issues would require additional GitHub App permissions
> and is intentionally **not enabled by default**.

---

#### 2.1.6. Recommendations

- Prefer variant branching (`jvm17/*`, `jvm21/*`, `and21/*`, `mps2025.1/*`) to keep cross-variant feature migration explicit.
- Use `feature-testrun/*` for safe CI experiments without publishing.
- Use `feature/*` for normal development with fast compile feedback.
- Keep publication/release actions explicit and constrained by branch/lane policy; snapshot publication MAY freely target a selected subset of TechnologyKinds, while an incomplete release requires the explicit incomplete-release override.
- Use issue identification wherever practical to keep change traceability as transparent as possible.

---

#### 2.1.7. Publication Repository Resolution

Before dependency download or publication, the lifecycle resolves the effective repository matrix cell for every selected TechnologyKind and operation:

```text
<technology-kind> x <public|private> x <release|snapshot> x <download|upload|manage>
```

Each cell contains zero or more repository endpoints. Endpoints are merged by stable endpoint `id`; `enabled` defaults to `true`. Lower metadata levels can therefore disable or re-enable inherited endpoints, change only an endpoint URL/profile reference, or add another endpoint without replacing the whole cell.

Resolution order is:

```text
Algites public-governance download defaults
        -> optional private-governance defaults overlays
        -> algites-source-repository.yml
        -> ancestor algites-artifact-set.yml / algites-artifact.yml
        -> descendant algites-artifact-set.yml / algites-artifact.yml
```

The canonical endpoint shape is:

```yaml
repositories:
  java:
    private:
      release:
        download:
          - id: algites-java-private-release-download
            url: https://example.invalid/maven/
            credentialProfile: algites-java-private-release-download
            enabled: true
```

Canonical Algites endpoint ids encode all four dimensions. External/custom targets keep the same four-dimensional suffix and may add a qualifier, for example `algites-acme-java-private-release-download`.

Credential profiles are independent inherited metadata. A profile may be defined or overridden at repository, artifact-set, or artifact level:

```yaml
credentialProfiles:
  algites-java-private-release-download:
    type: basic
```

Supported profile types are the closed set `basic`, `bearer`, `api_key`, and `certificate`. Their canonical fields are:

| type | required fields | optional fields |
| --- | --- | --- |
| `basic` | `username`, `password` | — |
| `bearer` | `token` | — |
| `api_key` | `apiKey` | — |
| `certificate` | `certificate` | `privateKey`, `privateKeyPassword` |

Core support for a credential type does not imply that every TechnologyKind repository adapter can apply that authentication mechanism. Unsupported endpoint/type combinations MUST fail rather than silently fall back to another authentication mechanism.

Secret values use one provider-independent credential document supplied as `ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS` and governed by `algites-credentials_1.schema.json`. Every credential field has exactly the properties `source` and `value`. The closed value-source enum is:

| source | interpretation of `value` |
| --- | --- |
| `direct_value` | `value` is the credential content itself |
| `file_content` | `value` is a file path; the file content is the credential content |
| `secret_content` | `value` is a secret name/key in the current secret-provider context |
| `environment_variable_content` | `value` is an environment-variable name |

The `_CONTENT` suffix identifies the content produced by resolution, not the literal content of `value`. In particular, `file_content.value` is a path and `secret_content.value` is a secret identifier.

The document is keyed first by credential profile id and then by credential type. A profile MAY retain multiple typed values even though its effective non-secret profile declaration selects exactly one type for a particular endpoint. Example:

```json
{
  "algites-java-private-release-download": {
    "basic": {
      "username": { "source": "direct_value", "value": "algites-user" },
      "password": { "source": "secret_content", "value": "ALGITES_JAVA_PRIVATE_PASSWORD" }
    },
    "bearer": {
      "token": { "source": "environment_variable_content", "value": "ALGITES_JAVA_PRIVATE_TOKEN" }
    }
  }
}
```

Local users need only the subset of profiles required by the operations they execute. The universal `ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS` document is also the canonical persistent local representation; there is no second profile/type credential format. A non-empty `ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS` environment variable is an explicit per-process override. Otherwise the installed local credential bootstrap reads the same document from the highest-priority available Algites operating-system secure store. Because one document may retain multiple typed entries under a profile, a later type override does not reinterpret or destroy values retained for another type.

Provider adapters MAY materialize a credential document before invoking the final processing. Materialization MUST preserve the same schema: a resolved field becomes `direct_value` with the resolved content. A provider adapter MUST NOT invent a second credential format.

Transient cross-process variables used only internally by Algites workflows/actions/scripts MUST use the `_TMP_ALGITES_*` prefix. They are implementation transport, are not supported local configuration variables, and MUST NOT be created as repository/organization secrets by users. Stable user-/DevOps-configurable environment contracts retain the `ALGITES_*` prefix.

For GitHub Actions the resolution is deliberately two-phase. `resolveAlgitesRequiredCredentials` first evaluates the same inherited repository metadata in credential-preflight mode and returns the union of profile/type pairs referenced by enabled repository endpoints in the requested download/upload/manage context. The trusted GitHub credential bridge then receives the original `ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS`, the GitHub secret context, environment, and runner filesystem; discards every profile/type pair not listed by the preflight plan; materializes all retained fields; and exposes only the reduced `direct_value` document to the subsequent Gradle processing.

`_TMP_ALGITES_CREDENTIAL_SECRETS_JSON` carries an optional provider secret context required for exact-name `secret_content` lookup. It is not a credential document and MUST NOT contain profile-selection semantics. GitHub wrappers populate it from the complete GitHub Actions `secrets` context for the trusted bridge. For local processing it is normally absent: the installed Java resolver and Gradle bootstrap resolve a missing `secret_content` key from a named value in the Algites local secure store. When `ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS` itself is not set in the environment, the Gradle bootstrap obtains the stored universal document through the installed `algites-credentials` helper (`ALGITES_CREDENTIAL_CLI` MAY override its executable path). This does not create another credential schema; the helper is only a secure-store bootstrap adapter.

Concrete public download locations are public-governance data in `pub.gov.Algites/repository/defaults/algites-repository-download-defaults-public.yml`, supplied through `ALGITES_REPOSITORY_PUBLIC_DEFAULTS_FILE`; they are not hard-coded in the resolver. Governed public upload/manage locations and all private download/upload/manage locations remain private-governance data. All defaults files use `algites-repository-defaults_1.schema.json` and may contain endpoint definitions plus non-secret `credentialProfiles`; they MUST NOT contain secret values. `ALGITES_REPOSITORY_GOVERNED_PUBLIC_DEFAULTS_FILE` supplies the combined public upload/manage overlay and `ALGITES_REPOSITORY_PRIVATE_DEFAULTS_FILE` supplies the combined private download/upload/manage overlay.

Repository visibility usage is constrained by source-repository visibility:

- `pub` repositories resolve only `public` download cells, publish only to `public` upload cells, and manage only `public` management cells;
- `priv` repositories may resolve both `public` and `private` download cells and publish/manage their own artifacts only through `private` upload/manage cells.

Source-repository visibility is authoritative. Provider or execution inputs MAY validate the expected `pub`/`priv` value but MUST NOT change repository visibility.

The initial public-governance defaults preserve the existing Java behaviour (Maven Central for release dependencies and the public Algites Cloudsmith repository for snapshot dependencies), define PyPI as the Python public release index, and contain explicit `dummy.invalid` placeholders for public cells whose current endpoint still needs confirmation. The defaults matrix covers every standard TechnologyKind (`java`, `python`, `mps`). Private downloads and all canonical upload/management endpoints are supplied only from private governance and likewise contain explicit placeholders where the current endpoint is not yet confirmed.

Provider integrations SHOULD transfer only the required private-governance definition files, not clone the complete governance repository. The GitHub integration obtains the required files through authenticated GitHub Contents API requests. `PRIVATE_GOVERNANCE_GITHUB_APP_*` credentials are distinct from target-repository GitHub App credentials and MAY refer to a different GitHub App.

GitHub Actions workflows MUST NOT maintain TechnologyKind-specific or credential-type-specific secret mappings. The provider-independent profile/type/field contract is defined by Algites metadata and the universal credential document. GitHub-specific code is limited to the trusted bridge that materializes the preflight-selected subset of that document.

Because GitHub does not expose Actions secret values through its REST API, `secret_content` references are resolved from the GitHub Actions `secrets` context supplied directly to the trusted first-party bridge. The bridge MUST NOT log credential values and MUST NOT forward profile/type pairs that are absent from the Gradle preflight plan.

Publication and repository-management capability are centralized in `priv.gov.Algites`. Snapshot workers already run there, and release wrappers in target repositories MUST only dispatch a central private-governance release worker and wait for its result. Upload/manage endpoint overlays and their credential secrets therefore remain unavailable to ordinary target-repository builds. Private ordinary CI MAY receive private-download credentials because resolving private dependencies is a normal private-build capability; publication and management credentials are not.

Publication workflows invoke the common `algitesPublish` orchestration task. Technology-specific publication remains adapter-specific (for example Java `publish`, Python `publishPython`); declaration of a TechnologyKind alone does not imply that its build/publication adapter already exists.

Python snapshot publication MUST use an immutable PEP 440 development-release version for each concrete snapshot build. The logical Algites version remains, for example, `1.0-SNAPSHOT`, while the Python distribution version is `1.0.dev<snapshotInstanceId>`. Central snapshot automation generates one UTC decimal timestamp (`yyyyMMddHHmmssSSS`) as the snapshot instance id and supplies that same value to every Python artifact and to the corresponding documentation generation. Normal local non-publication builds MAY omit the instance id and use `.dev0` as development metadata, but publishing a Python snapshot without an explicit snapshot instance id MUST fail. The snapshot instance id is execution metadata and MUST NOT be added to the deterministic logical-artifact manifest.

After the complete release workflow succeeds, a final best-effort released-snapshot cleanup MAY run when the effective inherited `deleteSnapshotWhenReleased` value is `true` (the global default). Cleanup uses the `snapshot.manage` matrix cells, not upload endpoints. Management endpoints may use different URLs and credentials and require an implementation-supported `usageProviderAdapter`; the generic lifecycle never assumes that the upload URL supports HTTP DELETE. For Java, cleanup selects the exact corresponding `-SNAPSHOT` version. For Python, cleanup selects the complete timestamped development-release series for the released line, for example release `1.0` selects `1.0.dev*`. The Cloudsmith adapter supports this prefix selection; a management adapter that cannot enumerate matching versions MUST report/skip that TechnologyKind rather than delete an unrelated version. Cleanup failure is non-fatal for the completed release and is reported for manual remediation.

#### 2.1.8. Governed YAML Schema Resolution

Algites YAML configuration schemas are versioned contracts. Schema filenames MUST carry their schema version suffix from the first version, using the convention defined by the Structure Specification (`_1`, `_2`, ...).

Lifecycle tooling MUST therefore resolve an explicit schema version rather than relying on an unversioned mutable schema filename. Incompatible schema evolution requires selecting a new schema version; old schema versions MAY remain available for validation of older repository states.

Schemas for public Algites YAML configuration SHOULD be sourced from the governed public schema artifact rather than copied ad hoc into individual repositories. CI/development validation MAY cache these schemas, but the resolved schema identity/version MUST remain visible in diagnostics.

---

### 2.2. Specifics of the Integration With GitHub Actions facility

GitHub Actions is a provider adapter around the provider-independent Algites lifecycle. Canonical repository and credential semantics remain defined by Algites metadata and Gradle/bootstrap logic.

For credentials, GitHub Actions uses the universal `ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS` document. Reusable/private wrappers serialize their available `secrets` context into `_TMP_ALGITES_CREDENTIAL_SECRETS_JSON` only for the trusted first-party credential bridge. The bridge consumes the Gradle preflight plan, resolves only the required profile/type pairs, materializes their fields according to the closed source enum, and exposes the resulting reduced `ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS` document to subsequent processing. No workflow may infer credential fields independently from the profile/type contract, and the bridge MUST NOT log credential values.

Release publication is centrally executed in `priv.gov.Algites`. Public/private target-repository wrappers authenticate only to private governance, dispatch the central worker with the immutable target repository/branch/SHA request, and wait for its conclusion. The central worker validates source-repository visibility, obtains visibility-specific repository overlays, binds canonical credential secrets, checks out the exact target revision, and invokes `algitesPublish`. Snapshot publication follows the same centralized credential and repository-default model.

---

## 3. Algites Release & Upmerge Policy

This chapter describes how Algites repositories perform **versioning, releases, and upmerges** independently of the CI provider, using:
- **variant-prefixed branches** (e.g. `jvm17/...`, `and21/...`)
- **lane branches** (e.g. `*/lane/1.1`)
- container-scoped version contexts
- immutable release source revisions/tags
- explicit release and lane-creation operations

---

### 3.1 Common Policy

#### 3.1.1. Branch layout

##### 3.1.1.1 Variant
A **variant** is the first path segment in the branch name.

Examples:
- `jvm17/lane/1.1`
- `and21/lane/1.1`

Variant encodes the **target platform baseline** (JVM / Android / MPS, …).

##### 3.1.1.2 Lane
A **lane** is a stable minor line `<A>.<B>` and lives in the branch name:

- `*/lane/<A>.<B>` (example: `jvm17/lane/1.1`)

Lane meaning:
- `A` = major line
- `B` = minor line
- `C` = patch/fix (computed from tags, not stored in sources)

---

#### 3.1.2. Repository metadata

To use the lanes on the project, the repo MUST contain `algites-source-repository.yml` in repository root with the defined lane identification:

```yaml
algites.repository.lane: "1.1"
```

CI enforces:
- If the branch name contains `/lane/<X>/`, then `algites.repository.lane` must equal `<X>`.

> To enforce this as a *hard rule*, protect branches (e.g. `*/lane/*`) and require the CI status check.

---

#### 3.1.3. Versioning and Version Scope

A controlled version is resolved from the effective `ContainerVersionContext`. Repository root is one possible version scope, but nested containers/artifacts MAY override the context and therefore maintain an independent version lifecycle within the same source repository.

A release identity MUST therefore identify both:

- the resolved version, and
- the version scope/source revision to which that version belongs.

The historical tag form `v<A>.<B>.<C>-<variant>` remains valid for repositories where the repository root is the only version scope. Repositories with multiple independent version scopes MUST use a scope-disambiguated release identity defined by the release implementation; the scope identifier MUST be stable and derived from Algites artifact/container identity rather than an arbitrary display label.

##### 3.1.3.1 Snapshot versions

Snapshot computation is performed per effective version scope. A snapshot version is independent from TechnologyKind selection: the same logical snapshot version MAY be built for Java, Python, MPS, or any selected subset of TechnologyKinds.

##### 3.1.3.2 TechnologyKind-specific publication presence

Snapshot and ordinary build operations MAY select any subset of the declared TechnologyKinds. Their effective set is the intersection of the artifact's declared TechnologyKinds and the requested TechnologyKinds; if no request is supplied, all declared TechnologyKinds are selected. A TechnologyKind omitted from a snapshot/build is simply not updated by that operation.

Release publication is complete by default: the selected TechnologyKinds MUST cover all declared TechnologyKinds of every released logical artifact. A provider MAY expose an explicit `allowIncompleteTechnologyKinds` confirmation for an exceptional incomplete release. If that override is used, no artificial package is created for an omitted TechnologyKind, but the release version is final: an omitted TechnologyKind MUST NOT later be published under the same logical release version. Publishing it requires a new logical version.

#### 3.1.4. CI: when/what runs

The common CI lifecycle resolves the effective version scope, selected TechnologyKinds, lifecycle mode, and then invokes the corresponding Gradle/TechnologyKind-adapter task graph.

Documentation generation uses the same requested TechnologyKind selection contract as ordinary construction. A standalone documentation operation MAY therefore request a subset explicitly; if it does not, all declared TechnologyKinds are requested. For each logical artifact, only `declared ∩ requested` documentation outputs are generated. The documentation publication for that run MUST contain only those effective TechnologyKind directories; stale TechnologyKind documentation left by an earlier broader build is removed. The logical-artifact page retains all declared TechnologyKinds as metadata while showing generated sections only for the effective documentation set. It SHOULD also record the source ref/branch, exact source commit, and UTC generation date/time for the documentation build.


#### 3.1.5. Release process (explicit action)

Releases are deliberate operations initiated through the active CI/provider integration or an equivalent local/central Algites release entry point. The operation selects:

- branch/lane and effective version scope,
- release logical version,
- one or more TechnologyKinds to publish (default: all effective technology kinds),
- optional upmerge behavior.

The release operation MUST:

1. validate lane/version-scope consistency,
2. resolve and freeze the immutable release source revision,
3. resolve the requested TechnologyKind set and reject an incomplete selection unless the explicit incomplete-release override is enabled,
4. compute or validate the release identity/tag,
5. obtain the governed private-download and visibility-specific upload repository-default overlays from private governance,
6. execute construction and verification required by each selected TechnologyKind adapter,
7. publish only the selected TechnologyKinds to the effective upload cells matching the source repository visibility and release stability,
8. record which TechnologyKind-specific publications actually exist for the logical version; when an incomplete release was explicitly allowed, omitted TechnologyKinds remain permanently absent from that release version,
9. after all required release processing succeeds, perform best-effort cleanup only for snapshot packages corresponding to TechnologyKinds that were actually selected/released, through the visibility-specific `snapshot.manage` endpoints when the effective `deleteSnapshotWhenReleased` policy is `true`.

Ordinary repository builds do not receive upload or management overlays. Snapshot and release publication are therefore centrally orchestrated operations rather than normal local project capabilities. The release publication phase materializes only download/upload credentials; the final cleanup phase separately materializes only management credentials. Provider-specific workflow code is responsible for obtaining the private overlay files and credentials; the Gradle resolver is responsible for deterministic repository-matrix resolution.

##### 3.1.5.1 Release identity/tag naming

Release identities are computed automatically from the effective version scope, version, and variant policy. Provider UI MUST NOT be the semantic source of the tag name. Legacy single-scope repositories may continue using `v<A>.<B>.<C>-<variant>`; multi-scope repositories require a stable scope-disambiguated form.

---

#### 3.1.6. Upmerge support

After a successful release, you can upmerge the resulting changes to higher lanes (same major).

The release lifecycle supports:
- `upmerge_mode=none` → do nothing
- `upmerge_mode=next` → upmerge to next minor lane if it exists (e.g. `1.1` → `1.2`)
- `upmerge_mode=all-higher` → upmerge to all higher lanes that exist (within the same major)
- `upmerge_mode=explicit` → upmerge only to branches you list

##### 3.1.6.1 “Preview only” (copy/paste workflow)
Set `upmerge_preview_only=true` to only print a **single CSV line** with compatible branches into the Action Summary, e.g.:

```
jvm17/lane/1.2,jvm17/lane/1.3
```

A provider adapter may expose that line for copy/paste; the semantic input remains `upmerge_mode=explicit` plus `upmerge_targets`.

##### 3.1.6.2 What the upmerge does technically
Upmerge is attempted via:
- creating a temporary branch from the target lane
- cherry-picking commits from the previous release tag (or only HEAD if no previous tag exists)
- opening a PR to the target lane

If cherry-pick conflicts, the release implementation skips that target (manual resolution is required).

---

#### 3.1.7. Creating a new lane (automation)

To start a new minor lane across variants, use:

- invoke the provider-specific **Algites Create Lane** action or an equivalent Algites lifecycle entry point

Inputs:
- `source_lane`: e.g. `1.1`
- `new_lane`: e.g. `1.2`
- `variants`: `auto` (discover existing variants with the source lane) or `explicit`

The lane-creation lifecycle:
- creates `*/lane/<new_lane>` from `*/lane/<source_lane>`
- updates the `algites.repository.lane` value in `algites-source-repository.yml` to `<new_lane>` in the new branch
- pushes the new branch

---

#### 3.1.8. Practical notes

##### 3.1.8.1 “How do I release the next lane after upmerge?”
Upmerge only transfers commits. A release of that next lane is still:
- a deliberate release action on `*/lane/1.2`.

##### 3.1.8.2 “What if artifacts need different version lifecycles?”

Use distinct `ContainerVersionContext` scopes. Artifacts do not need to be split into separate source repositories solely because their logical versions advance independently. The effective version scope remains inherited through the container hierarchy and may be overridden at an artifact/container boundary.

##### 3.1.8.3 “What if only one technology changed?”

Ordinary construction, verification, snapshot publication, and documentation may select only the affected TechnologyKind. A release still selects all declared TechnologyKinds by default because the logical version belongs to the logical artifact, not to one technology. If an intentionally partial release is required, the incomplete-release override must be confirmed explicitly; omitted TechnologyKinds then remain permanently absent from that release version and can appear again only under a later logical version.

---

#### 3.1.9. Counterpoints and trade-offs

- Scoped version contexts reduce pressure to split repositories, but release tags/diagnostics must always make the version scope unambiguous.
- Selective snapshot publication and explicitly incomplete releases reduce cross-technology coupling, but an incomplete release permanently leaves the omitted TechnologyKinds absent from that logical version; publication metadata and documentation should make the selected/available TechnologyKinds explicit.
- Gradle orchestration across non-JVM ecosystems provides one lifecycle entry point, but each TechnologyKind adapter must accurately model native-tool inputs, outputs, and failure modes rather than hiding them behind Java assumptions.
- Cherry-pick upmerge remains pragmatic, but conflicts require explicit review and resolution.

### 3.2 GitHub Actions specific Policy

GitHub Actions is currently the default provider adapter. Provider-specific workflow structure, reusable actions, credentials, and UI mappings are defined here only insofar as they adapt the provider-independent lifecycle above. Migration of build/release logic out of GitHub-specific YAML and into Algites-owned actions/scripts is intentionally compatible with this model.

(TBD)

---



**© Algites**

[[/PROPOSAL]]

## Licensing lifecycle checks

Repository licensing materialization is version-controlled but generated from the hierarchical Algites licensing model.

After changing public/private governance licensing, a repository-local `licensing/license-definitions.yml`, or any subtree `license-usage.yml`, a maintainer runs:

```bash
./gradlew rebuildAlgitesLicensing
```

and commits the resulting root `LICENSE` and `LICENSES/` changes.

Licensing validation is part of artifact lifecycle processing. `checkAlgitesLicensing` is always strict and is intended for an explicit repository consistency check. Lifecycle tasks use `verifyAlgitesLicensing`, whose default mode is also strict.

Normal builds therefore fail when the managed root `LICENSE` or `LICENSES/` materialization is stale or differs from the effective hierarchical licensing model. Release processing explicitly uses `-Palgites.licensing.validationMode=strict` and performs this verification before creating the release tag; a release MUST NOT proceed with inconsistent licensing materialization.

Snapshot deployment explicitly uses `-Palgites.licensing.validationMode=warn`. The same licensing resolution and comparison are executed, but inconsistencies are emitted as warnings and do not block snapshot build/publication. This keeps development snapshots available while making the repository inconsistency visible and actionable. `strict` and `warn` are the only supported lifecycle validation modes; `strict` is the default whenever the property is omitted.

Public CI receives only public licensing governance. Private CI receives both public and private governance. This visibility boundary is mandatory and makes public builds independent of access to `priv.gov.Algites`.
