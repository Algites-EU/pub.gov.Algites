# Algites Artifact Developer Reference

## 1. Purpose and scope

This guide is the practical reference for developers and artifact authors working in an Algites source repository. It explains how to structure a repository, declare artifacts and artifact sets, select TechnologyKinds, configure inherited metadata, run the common Gradle lifecycle, maintain licensing metadata, generate documentation, and use the public GitHub workflow entry points.

This document is intentionally operational. The normative model remains defined by:

- [`../specs/Algites-Development-Structure-Specification.md`](../specs/Algites-Development-Structure-Specification.md) for structure, naming, inheritance, repository metadata, artifact identity, and publication contracts;
- [`../specs/Algites-Development-Lifecycle-Specification.md`](../specs/Algites-Development-Lifecycle-Specification.md) for CI, build, publication, release, lane, and licensing lifecycle rules;
- the JSON Schemas under [`../devops/build/yamldefs/src/product/yamldefs/`](../devops/build/yamldefs/src/product/yamldefs/) for machine-readable syntax validation.

If this guide and a normative specification differ, the normative specification and schema take precedence.

## 2. Repository model at a glance

An Algites source repository is a hierarchy of one source-repository root, zero or more artifact-set containers, and self-contained artifact leaves.

```text
repository root
├── algites-source-repository.yml
├── license-usage.yml                     optional local licensing declaration
├── licensing/                            repository-local license definitions/texts
├── <container>/
│   ├── algites-artifact-set.yml          optional, nestable
│   ├── <artifact>/
│   │   ├── algites-artifact.yml
│   │   ├── build.gradle.kts
│   │   ├── src/
│   │   │   ├── product/
│   │   │   │   └── <source-type>[.gen|.extgen]
│   │   │   └── develop/
│   │   │       └── <source-type>[.gen|.extgen]
│   │   └── doc/
│   │       ├── product/
│   │       └── develop/
│   └── <nested-container>/
│       └── algites-artifact-set.yml
└── ...
```

Discovery is structural. Once an `algites-artifact.yml` is found, discovery stops below that artifact. Directories inside the artifact are implementation details, not candidate artifact-set nodes.

Root infrastructure directories such as `.git`, `.gradle`, `.idea`, legacy root `run`, and root-level `build` are ignored only at the repository root. The same names may legitimately occur deeper in the structural hierarchy, for example `devops/build`.

Normal Algites build/runtime outputs are materialized below the repository-level `build/run` workspace rather than inside artifact source directories. For an artifact at `aac/coreintf`, its derived runtime/build workspace is:

```text
build/run/aac/coreintf/run/...
```

Repository-level derived outputs use `build/run/...` directly. Normal build tasks MUST NOT create `<artifact>/run` directories in the source hierarchy. The entire root `build/` directory is disposable build state and is not committed.

## 3. Metadata files and schemas

The standard source metadata files are:

| File | Structural role | Schema |
| --- | --- | --- |
| `algites-source-repository.yml` | repository root | `algites-source-repository_1.schema.json` |
| `algites-artifact-set.yml` | inheritable container | `algites-artifact-set_1.schema.json` |
| `algites-artifact.yml` | artifact leaf | `algites-artifact_1.schema.json` |
| `license-usage.yml` | hierarchical licensing declaration | `algites-license-usage_1.schema.json` |
| `licensing/license-definitions.yml` | repository-local license catalog | `algites-license-definitions_1.schema.json` |

Supporting reusable schemas include:

- `algites-version-context_1.schema.json`
- `algites-repository-matrix_1.schema.json`
- `algites-repository-defaults_1.schema.json`
- `algites-credential-profiles_1.schema.json`
- `algites-credentials_1.schema.json`
- `algites-publication-readiness_1.schema.json`
- `algites-artifact-manifest_1.schema.json`

The schemas use versioned filenames. A schema revision is therefore explicit and does not silently replace the meaning of an older version.

## 4. `algites-source-repository.yml`

A source repository begins with a root descriptor.

Minimal example:

```yaml
sourceRepository:
  id: pub.lib.Example
  name: Algites example public library repository

groupId: eu.algites.lib.example

versionContext:
  releaseLine: "1"
  revision: 0
  qualifierKind: SNAPSHOT
  qualifierLabel: SNAPSHOT
```

### 4.1 `sourceRepository`

| Attribute | Required | Meaning |
| --- | ---: | --- |
| `sourceRepository.id` | yes | Canonical source repository identity. |
| `sourceRepository.name` | no | Human-readable repository name. |
| `sourceRepository.visibility` | no | Explicit `pub` or `priv` visibility when needed. Normally repository identity/naming and governance determine visibility. |
| `sourceRepository.repositories` | no | Repository matrix declared at repository scope. |

The repository descriptor may also contain these top-level inheritable properties:

- `groupId`
- `versionContext`
- `credentialProfiles`
- `publicationReadiness`
- `deleteSnapshotWhenReleased`

These are top-level siblings of `sourceRepository`; do not nest them inside the `sourceRepository` object unless the schema explicitly defines a field there.

## 5. `algites-artifact-set.yml`

An artifact set groups descendant artifacts and can contribute inherited defaults.

```yaml
artifactSet:
  name: Example component family
  description: Shared metadata for the example component family.
  technologyKinds: [java]

groupId: eu.algites.example.component

publicationReadiness:
  level: snapshot
  cause: |
    Public API is still being stabilized.
    Snapshot publication is allowed for integration testing.
  author: Example Maintainer
```

### 5.1 `artifactSet` attributes

| Attribute | Required | Meaning |
| --- | ---: | --- |
| `artifactSet.name` | no | Human-readable set name. |
| `artifactSet.description` | no | Free-form description. |
| `artifactSet.technologyKinds` | no | TechnologyKinds made available to descendants as structural metadata. Allowed values currently include `java`, `python`, and `mps`. |
| `artifactSet.repositories` | no | Repository matrix contribution at this container. |
| `artifactSet.versionContext` | no | Version-context contribution at this container. |

Top-level `groupId`, `versionContext`, `credentialProfiles`, `publicationReadiness`, and `deleteSnapshotWhenReleased` are also allowed.

Artifact sets may be nested. Inheritance follows the actual structural path from repository root through every containing artifact set to the artifact.

## 6. `algites-artifact.yml`

Every artifact leaf has an artifact descriptor.

```yaml
artifact:
  technologyKinds: [java, python]
  name: Example definitions
  description: Shared definitions published for both Java and Python consumers.
```

### 6.1 `artifact` attributes

| Attribute | Required | Meaning |
| --- | ---: | --- |
| `artifact.technologyKinds` | yes | Technologies actually produced/published by the artifact. Current values: `java`, `python`, `mps`. |
| `artifact.name` | no | Human-readable artifact name. |
| `artifact.description` | no | Free-form description. |
| `artifact.repositories` | no | Repository matrix contribution for this artifact. |
| `artifact.versionContext` | no | Version-context contribution for this artifact. |

Top-level `groupId`, `versionContext`, `credentialProfiles`, `publicationReadiness`, and `deleteSnapshotWhenReleased` are also allowed.

`technologyKinds` is the normative declaration of build/publication technologies. Source directory names alone do not select a TechnologyKind.

## 7. Source layout

The canonical source layout is:

```text
src/{product|develop}/<source-type>[.<generation-kind>]
```

The general-purpose SourceTypes currently include:

- `java`
- `python`
- `xmldefs`
- `yamldefs`
- `jsondefs`
- `config`
- `resources`

Technology adapters may define additional SourceTypes.

### 7.1 Product vs development sources

- `src/product/...` contributes to the product/output.
- `src/develop/...` contains development-only/test/tooling sources.

### 7.2 Generated-source suffixes

| Form | Meaning | VCS rule |
| --- | --- | --- |
| `<type>` | manually maintained source | committed |
| `<type>.gen` | generated by the normal Algites/Gradle lifecycle | must not be committed |
| `<type>.extgen` | generated externally | committed, but not manually edited |

Examples:

```text
src/product/java
src/product/java.gen
src/product/python
src/product/yamldefs
src/product/jsondefs
src/product/config
src/develop/java
src/develop/python.gen
```

A multi-technology artifact does not have to contain handwritten source directories for every output. `pub.gov.Algites/devops/build/yamldefs` is an example: common YAML-definition sources are transformed into a generated Python package while the same logical artifact is also published for Java.

`schema` is not a canonical SourceType. Use `jsondefs`, `yamldefs`, or `xmldefs` for definitions according to their semantic representation, and use `config` for concrete configuration instances regardless of serialization format. The source-root name is not repeated inside the business-relative path.

For Python artifacts, the Algites adapter stages `jsondefs`, `yamldefs`, `xmldefs`, and `config` product roots into the wheel/sdist build tree while preserving the path below the source root. Multiple distributions may therefore share a package prefix only through PEP 420 namespace packages. Exact module/resource path collisions are build errors, and a shared cross-distribution prefix must not contain `__init__.py` in any contributing distribution.

For Python product code, each main public Algites `AI*` type MUST be declared in its own deterministic snake_case module named from that type (for example `AIcDisplayText` in `aic_display_text.py`). Private or implementation helper types MAY remain in the same module. Repository validation MUST reject a product module that declares multiple main public `AI*` types or whose filename does not match its public type.


### 7.3 Build/runtime workspace

Generated source directories remain physically below `src` because they are source roots consumed by compilers and development tools. Their generated nature does not make them ordinary build output.

Ordinary compiled/package/documentation working output uses a separate repository-level workspace:

```text
build/run/
├── bld/                                  repository-level build/runtime state
└── <artifact-relative-path>/
    └── run/
        └── bld/                          artifact-local build/runtime state
```

For example:

```text
aac/coreintf/src/product/python/...
aac/coreintf/src/product/python.gen/...
build/run/aac/coreintf/run/bld/gradle/...
build/run/aac/coreintf/run/bld/python/...
build/run/aac/coreintf/run/bld/algites-docs/...
```

This preserves the artifact-relative `run/...` convention while keeping it outside the source hierarchy. The repository root project uses `build/run/...` without an additional mirrored artifact path. A normal `./gradlew clean` removes the Algites run workspace. Derived development metadata intentionally maintained for IDE use, such as generated `pyproject.toml`, remains governed by the development lifecycle rather than by this build-output relocation.

## 8. Inheritance and effective metadata

Algites metadata is resolved from the structural path:

```text
source repository
    -> artifact set
        -> nested artifact set
            -> artifact
```

Different properties use different merge semantics. Do not assume every field follows nearest-value override.

### 8.1 `groupId`

`groupId` is an independent top-level inherited value. The nearest descendant declaration replaces the inherited value for that node and its descendants.

```yaml
# repository
 groupId: eu.algites.lib
```

```yaml
# descendant artifact set
 groupId: eu.algites.lib.specialized
```

### 8.2 Repository endpoint lists

Repository endpoints merge by stable endpoint `id` within the same matrix cell. A descendant can modify or disable an inherited endpoint without repeating every property.

```yaml
artifact:
  repositories:
    java:
      public:
        snapshot:
          download:
            - id: algites-java-public-snapshot-download
              enabled: false
```

### 8.3 `publicationReadiness` is a cap

Publication readiness is intentionally not ordinary child override inheritance. The effective level is the minimum level declared anywhere on the structural path.

```text
none < snapshot < release
```

A descendant cannot relax an ancestor restriction.

## 9. Publication readiness

`publicationReadiness` protects the publication lifecycle without preventing normal development builds.

```yaml
publicationReadiness:
  level: none
  cause: |
    Migration compatibility is incomplete.
    Publication is blocked until the compatibility suite passes.
  author: Example Maintainer
```

| Field | Required | Meaning |
| --- | ---: | --- |
| `level` | yes when object is present | `none`, `snapshot`, or `release`. |
| `cause` | no | Free-form explanation; multiline YAML is supported. |
| `author` | no | Informational author/owner string. It is not an authorization identity. |

If the object is absent, the implicit level is `release`.

### 9.1 Level semantics

- `none`: artifact cannot participate in snapshot or release publication;
- `snapshot`: snapshot publication is permitted, release publication is blocked;
- `release`: snapshot and release publication are permitted.

Readiness does **not** remove an artifact from discovery and does **not** prevent compilation/testing. A local project dependency may still cause tasks of a `none` artifact to run when another local artifact needs it.

Publication is different. Before `algitesPublish`, the framework validates the selected controlled publication closure. If a selected artifact depends on a local controlled artifact whose effective readiness is too low, publication stops before upload. Diagnostics include the blocking descriptor path and, when supplied, `cause` and `author`.

## 10. Version context

`versionContext` uses `algites-version-context_1.schema.json`.

Supported fields are:

| Field | Meaning |
| --- | --- |
| `lane` | lifecycle lane identity when used by the repository/version model |
| `releaseLine` | release line scope |
| `revision` | revision component |
| `qualifierKind` | `SNAPSHOT`, `PRE_RELEASE`, `RELEASE`, `FINAL`, or `POST_RELEASE` |
| `qualifierLabel` | optional qualifier label |

A version context is container-scoped and may be overridden at a lower structural boundary when artifacts need independent logical version lifecycles.

The effective version is resolved by Algites infrastructure; technology adapters map it into ecosystem-specific publication versions.

## 11. Repository matrix

Repository metadata is organized on four independent axes:

```text
TechnologyKind -> visibility -> stability -> usage -> endpoint list
```

Current axis values:

- TechnologyKind: `java`, `python`, `mps`
- visibility: `public`, `private`
- stability: `release`, `snapshot`
- usage: `download`, `upload`, `manage`

Example:

```yaml
repositories:
  java:
    public:
      snapshot:
        download:
          - id: algites-java-public-snapshot-download
            url: https://example.invalid/maven/snapshots/
```

### 11.1 Endpoint fields

| Field | Required | Meaning |
| --- | ---: | --- |
| `id` | yes | Stable canonical endpoint id. |
| `url` | no for an inherited amendment; normally yes for a concrete endpoint | Repository/package-manager URL. |
| `credentialProfile` | no | Non-secret credential profile id. |
| `enabled` | no | Defaults to enabled; lower levels can disable an inherited endpoint. |
| `usageProviderAdapter` | no | Provider adapter used for management operations such as package deletion. |

Public **download** defaults are maintained in `pub.gov.Algites/repository/defaults/algites-repository-download-defaults-public.yml`. Upload/manage governance is intentionally not part of the ordinary public artifact-author configuration.

### 11.2 Public governance resolution for local builds

Normal local builds do not need a checkout-specific configuration for public repository defaults. When `ALGITES_REPOSITORY_PUBLIC_DEFAULTS_FILE` is not set, the resolver loads the published defaults from `Algites-EU/pub.gov.Algites/main` on GitHub and materializes them under the Gradle user-home cache.

Set `ALGITES_REPOSITORY_PUBLIC_DEFAULTS_FILE` to a local file when developing governance itself, testing unpublished changes, or intentionally pinning the build to a locally materialized copy. CI may also set it explicitly to the governance material prepared by the workflow. Governed public upload/manage overlays and private defaults are never fetched through this public fallback; they remain explicit governance inputs.

The precedence is therefore:

1. explicit `ALGITES_REPOSITORY_PUBLIC_DEFAULTS_FILE`;
2. otherwise the published public GitHub defaults.

## 12. Credential profiles vs credential values

Repository metadata never contains passwords, tokens, certificates, or other secret values.

A repository endpoint references a non-secret profile:

```yaml
credentialProfiles:
  example-download:
    type: basic

artifact:
  repositories:
    java:
      private:
        release:
          download:
            - id: example-download
              url: https://example.invalid/maven/
              credentialProfile: example-download
```

Supported profile types are:

| Type | Required fields | Optional fields |
| --- | --- | --- |
| `basic` | `username`, `password` | — |
| `bearer` | `token` | — |
| `api-key` | `apiKey` | — |
| `certificate` | `certificate` | `privateKey`, `privateKeyPassword` |

Actual values are supplied through the universal `ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS` JSON document or an Algites secure-store integration. See [`../devops/build/credentials/README.md`](../devops/build/credentials/README.md).

### 12.1 Value sources

| Source | `value` contains | Materialized result |
| --- | --- | --- |
| `DIRECT_VALUE` | credential content | unchanged content |
| `FILE_CONTENT` | path to a file | UTF-8 file content |
| `SECRET_CONTENT` | name/key in the active secret-provider context | secret content |
| `ENVIRONMENT_VARIABLE_CONTENT` | environment-variable name | variable content |

The `_CONTENT` suffix describes what is obtained after resolution. For example, `FILE_CONTENT.value` is a path, not literal file content.

For a normal public developer build, upload/manage credentials are not required. The framework resolves only credentials needed for the requested repository contexts. Credential preflight is fail-fast when no effective declared `artifact.technologyKinds` remain after applying any optional TechnologyKind selection; an empty TechnologyKind set never means "all repository technologies".

## 13. Licensing

Algites licensing is hierarchical and materialized into the repository root `LICENSE` and `LICENSES/` directory.

For normal public local builds, central public licensing governance is available automatically. When `ALGITES_LICENSING_PUBLIC_GOVERNANCE_DIRECTORY` is not set, the licensing resolver loads `license-definitions.yml`, the default `license-usage.yml`, and the referenced license texts from `Algites-EU/pub.gov.Algites/main` on GitHub and materializes them under the Gradle user-home cache. Set `ALGITES_LICENSING_PUBLIC_GOVERNANCE_DIRECTORY` to a local `pub.gov.Algites/licensing` directory when developing governance, testing unpublished changes, working from a deliberately materialized copy, or otherwise overriding the published fallback. The `pub.gov.Algites` repository itself continues to use its local `licensing/` directory directly. Private licensing governance has no public remote fallback and remains an explicit input.

### 13.1 `license-usage.yml`

Example:

```yaml
licenses:
  - id: Apache-2.0
    enabled: true
    contentKinds:
      - product
  - id: CC-BY-4.0
    enabled: true
    contentKinds:
      - documentation
```

Fields:

- `id`: license id;
- `enabled`: enables/disables the license at that hierarchy point;
- `contentKinds`: optional list containing `product` and/or `documentation`.

### 13.2 `licensing/license-definitions.yml`

This optional repository-local catalog adds license definitions not already supplied by the effective governance. It maps license ids to display metadata and canonical text files. Common Algites public licenses are normally defined centrally and do not need to be copied into each repository.

```yaml
licenses:
  - id: Apache-2.0
    name: Apache License 2.0
    url: https://www.apache.org/licenses/LICENSE-2.0
    text: texts/Apache-2.0.txt
```

### 13.3 Common licensing tasks

```bash
./gradlew rebuildAlgitesLicensing
./gradlew checkAlgitesLicensing
./gradlew verifyAlgitesLicensing
```

- `rebuildAlgitesLicensing` regenerates root `LICENSE` and `LICENSES/` from effective declarations;
- `checkAlgitesLicensing` is an explicit strict consistency check;
- `verifyAlgitesLicensing` is the lifecycle check and is strict by default. Central snapshot processing may explicitly use warning mode.

After changing licensing governance, rebuild and commit the materialized files.

## 14. Deterministic distributed artifact manifest

Every Java/Python distribution produced by the shared Algites adapters carries the deterministic logical-artifact manifest:

```text
algites-artifact-manifest.yml
```

The governed v1 structure is defined by `algites-artifact-manifest_1.schema.json`.

For every Java JAR produced by the project, including any sources JAR when present, the manifest is embedded at:

```text
META-INF/algites/algites-artifact-manifest.yml
```

For Python distributions, the same TechnologyKind-neutral manifest is embedded in both distribution forms:

- wheel: `<distribution>.dist-info/META-INF/algites/algites-artifact-manifest.yml`;
- source distribution: `META-INF/algites/algites-artifact-manifest.yml` below the source-distribution root directory.

The wheel location is intentionally scoped by the distribution's `.dist-info` directory so multiple installed Python distributions do not compete for one global `META-INF/algites` path. Future TechnologyKind adapters that define another distributable package format SHOULD embed the same logical-artifact manifest in that package using a format-appropriate metadata location.

The manifest identifies the **logical Algites artifact**, not one TechnologyKind-specific representation. Therefore it intentionally does not contain `technologyKinds`, Java/Python/MPS-specific coordinates, build-tool details, or documentation-tool details. The same logical artifact manifest can be embedded in all TechnologyKind outputs of that artifact.

Conceptual v1 example:

```yaml
manifestVersion: 1
artifact:
  repositoryId: pub.lib.Example
  localArtifactId: api.core
  artifactCoordinateId: pub.lib.Example_api.core
  groupId: eu.algites.lib.example
  version: 1.0-SNAPSHOT
  sourcePath: api/core
  structureKind: artifact
  name: Example Core API
  description: Shared API of the example library.
sourceMetadata:
  descriptorHierarchy:
    - structureKind: repository
      path: algites-source-repository.yml
      sha256: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
    - structureKind: artifact-set
      path: api/algites-artifact-set.yml
      sha256: 123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0
    - structureKind: artifact
      path: api/core/algites-artifact.yml
      sha256: 23456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef01
```

Each `sha256` is calculated from the exact bytes of the corresponding source descriptor. The ordered `descriptorHierarchy` therefore records which repository/artifact-set/artifact descriptors governed the artifact and allows those source descriptors to be independently verified.

The manifest is deliberately **cache-stable with respect to unrelated build context**. It MUST NOT contain values such as:

- generation/build timestamps;
- Git commit IDs, branch names, tags, or dirty-working-tree state;
- CI workflow/run/job IDs;
- runner or workstation identity;
- repository upload endpoint selection or credentials;
- other data that can change while the relevant artifact sources and descriptor hierarchy remain unchanged.

A change to a descriptor in the effective hierarchy changes its SHA-256 and therefore changes the manifest. A change only to unrelated CI/Git execution context does not.

Generated artifact documentation also publishes a copy named `algites-artifact-manifest.yml` in the logical artifact publication directory and exposes the descriptor hierarchy and hashes in the artifact page header. Documentation-specific provenance such as source ref, Git commit, and generation time remains separate documentation metadata; it is not copied into the artifact manifest.

## 15. Common Gradle lifecycle

The exact task graph is TechnologyKind-dependent. These are the common entry points exposed by the shared Algites root build.

| Task | Purpose |
| --- | --- |
| `prepareDevelopment` | Generate effective development metadata needed by supported TechnologyKinds. |
| `refreshDevelopment` | Force regeneration of effective development metadata. |
| `algitesBuild` | Build all effective or explicitly selected TechnologyKinds. |
| `validateAlgitesPublicationReadiness` | Validate readiness of the selected publication closure. Normally invoked by publication. |
| `algitesPublish` | Publish all effective or selected TechnologyKinds. Publication credentials/overlays are normally supplied only by governed automation. |
| `resolveAlgitesRequiredCredentials` | Resolve enabled repository endpoints and required credential profile/type pairs. |
| `printAlgitesDeploymentPlan` | Print effective deployment/repository configuration. |
| `printAlgitesArtifactModel` | Print discovered artifact metadata. |
| `resolveAllAlgitesArtifactDirectoryMetadata` | Resolve metadata for all discovered artifact directories. |
| `generateAlgitesDocsSite` | Generate the aggregate documentation site. |
| `rebuildAlgitesLicensing` | Rebuild materialized licensing files. |
| `checkAlgitesLicensing` | Strict licensing consistency check. |
| `verifyAlgitesLicensing` | Lifecycle licensing validation. |

Technology adapters add their own tasks. Examples include Java `build`/`test`/`javadoc`, Python `generatePythonProjectMetadata`, `buildPython`, `publishPython`, and MPS runtime/documentation tasks.

## 16. Common Gradle properties and environment inputs

### 16.1 Technology selection

```text
-Palgites.technologyKinds=java,python
ALGITES_TECHNOLOGY_KINDS=java,python
```

Empty/absent means all declared TechnologyKinds. Effective technologies are the intersection of declared and requested kinds for each artifact.

### 16.2 Python executable

```text
-Palgites.python.executable=python3
ALGITES_PYTHON_EXECUTABLE=python3
```

Selects the interpreter used by Python packaging/documentation adapters. CI must ensure required Python tooling such as `build` or documentation generators is installed into that interpreter.

For logical snapshot versions such as `1.0-SNAPSHOT`, normal local non-publication builds use Python development version `1.0.dev0`. Snapshot publication uses an immutable build instance version instead, for example:

```text
1.0.dev20260921100435123
```

The decimal suffix is one UTC snapshot-instance timestamp generated once by the centralized snapshot worker and shared by all Python packages and snapshot documentation in that worker run. A manual/local Python snapshot publication must provide the same kind of value through either:

```text
-Palgites.snapshot.instanceId=20260921100435123
ALGITES_SNAPSHOT_INSTANCE_ID=20260921100435123
```

The value contains decimal digits only. It is build execution metadata, not logical artifact metadata, so it is intentionally absent from `algites-artifact-manifest.yml`.

When documentation is generated as part of that centralized snapshot worker run, the same snapshot-instance id is propagated into the documentation and the concrete Python package version is shown there. A standalone/manual documentation run has no concrete package build to identify; in that case the documentation explicitly reports that the Python package version is not tied to a concrete package build instead of inventing `*.dev0`.

### 16.3 Licensing validation

```text
-Palgites.licensing.validationMode=strict
-Palgites.licensing.validationMode=warn
```

`strict` is the default. `warn` is used only by lifecycle contexts that explicitly permit non-blocking licensing inconsistencies, such as current centralized snapshot processing.

### 16.4 Credential-preflight properties

```text
-Palgites.credential.usages=download,upload
-Palgites.credential.download.stabilities=release,snapshot
-Palgites.credential.upload.stabilities=snapshot
-Palgites.credential.manage.stabilities=snapshot
-Palgites.credential.output=/path/to/required-credentials.json
```

Environment equivalents are available as `ALGITES_CREDENTIAL_USAGES`, `ALGITES_CREDENTIAL_DOWNLOAD_STABILITIES`, `ALGITES_CREDENTIAL_UPLOAD_STABILITIES`, `ALGITES_CREDENTIAL_MANAGE_STABILITIES`, and `ALGITES_CREDENTIAL_OUTPUT`.

Normal developer builds normally need only download usage.

### 16.5 Documentation properties

Important documentation inputs include:

```text
-Palgites.docs.siteRoot=docs-site
-Palgites.docs.publicationKind=preview|snapshot|release
-Palgites.docs.publicationId=<id>
-Palgites.docs.sourceRef=<ref>
-Palgites.docs.sourceCommit=<commit>
-Palgites.docs.generatedAt=<UTC timestamp>
-Palgites.docs.repositoryHomeUrl=<url>
```

The documentation system may also be given script/adapter overrides such as `algites.docs.baseScript`, `algites.docs.javaScript`, `algites.docs.pythonScript`, `algites.docs.mpsScript`, and `algites.docs.repositoryMetadataResolverScript`. These are infrastructure override points rather than normal artifact-author settings.

## 17. Public GitHub workflow entry points

### 17.1 `Algites CI (Public)`

File: `.github/workflows/algites-ci-pub.yml`

Triggers:

- every push;
- manual `workflow_dispatch`.

It delegates to the reusable public Algites CI wrapper. The common CI resolves branch/lifecycle mode, repository download defaults, TechnologyKinds, toolchains, credentials required for download, and the Gradle task appropriate to the mode.

The shared CI implementation exposes optional task overrides for approval, verification, and construction modes, but a normal repository should use the standard wrapper unless it has an explicit reason to customize them.

### 17.2 `Algites Documentation Site Automation Process`

File: `.github/workflows/algites-docs-site-automation-process.yml`

This file is a deliberately stable per-repository automation bridge. It has exactly one automation input:

| Input | Meaning |
| --- | --- |
| `request` | Opaque JSON request produced by centralized Algites DevOps automation and passed unchanged to the reusable documentation workflow. |

The bridge does not declare publication, TechnologyKind, snapshot-instance, toolchain, or other operational fields individually. The request contract is owned by `pub.gov.Algites`; central automation may add request fields without requiring this file to be changed in every artifact repository. After a repository has adopted this generic bridge, normal documentation protocol evolution must not require bridge synchronization.

The bridge verifies that the caller is Algites GitHub App automation and delegates the opaque request to `.github/workflows/algites-universal-docs-site.yml`. The reusable workflow interprets the fields it knows and ignores unknown request fields for forward compatibility. It checks out the selected source ref, loads public download/licensing governance, installs Java/Python documentation toolchains, updates the persistent documentation branch, and for public repositories can publish GitHub Pages.

Manual preview generation is intentionally separate in `.github/workflows/algites-docs-site-manual-preview.yml`; artifact developers therefore do not need to construct the automation JSON request manually.

The reusable workflow continues to expose typed `workflow_call` inputs for direct/manual infrastructure callers. The generic automation request overrides the corresponding typed values when present.

### 17.3 `Algites Universal Create Lane`

File: `.github/workflows/algites-universal-github-create-lane.yml`

This workflow is both manually dispatchable and reusable. Main inputs:

- `source_lane`
- `new_lane`
- `variants`: `auto` or explicit selection
- `variants_explicit`: comma-separated variants when explicit mode is used

The lifecycle creates the new lane branch(es) from the selected source lane and updates repository lane metadata according to the lifecycle specification.

### 17.4 Snapshot and release publication

Artifact authors normally do not embed upload/manage repository secrets or central publication logic in the source repository. Snapshot deployment and release publication are governed centrally. Public repositories may expose thin provider wrappers, but the private governance repository owns the operational upload/manage overlays and central workers.

## 18. Practical recipes

### 18.1 Add a Java artifact

1. Create the structural directory.
2. Add `algites-artifact.yml`:

```yaml
artifact:
  technologyKinds: [java]
  name: Example API
```

3. Add the Gradle project/build file.
4. Put production sources in `src/product/java` and development/test sources in `src/develop/java` according to the adapter conventions.
5. Run metadata/model and build checks.

### 18.2 Add a multi-technology artifact

```yaml
artifact:
  technologyKinds: [java, python]
  name: Shared definitions
```

A single logical artifact/version may produce technology-specific outputs. Shared neutral source can live in a SourceType such as `yamldefs`; adapters may generate language-specific packaging input under `*.gen`.

### 18.3 Temporarily block publication

```yaml
publicationReadiness:
  level: none
  cause: |
    Artifact format migration is incomplete.
    Build and tests may continue, but nothing should be published yet.
  author: Maintainer Name
```

Local build/test remains available. Publication fails if this artifact enters the controlled publication closure.

### 18.4 Permit snapshots but block release

```yaml
publicationReadiness:
  level: snapshot
  cause: Public API is not release-stable yet.
  author: Maintainer Name
```

### 18.5 Build only selected technologies

```bash
./gradlew -Palgites.technologyKinds=java algitesBuild
```

or:

```bash
ALGITES_TECHNOLOGY_KINDS=java,python ./gradlew algitesBuild
```

### 18.6 Inspect effective metadata

```bash
./gradlew printAlgitesArtifactModel
./gradlew resolveAllAlgitesArtifactDirectoryMetadata
./gradlew printAlgitesDeploymentPlan
```

## 19. Common failure modes

### Unknown or missing TechnologyKind

Check `artifact.technologyKinds` and make sure the shared infrastructure has an adapter for the declared value.

### Publication readiness blocks publish

Read the blocking declaration(s) printed by the task. The error identifies descriptor paths and includes `author` and `cause` when present. Fix the underlying reason and raise/remove the limiting declaration; there is intentionally no readiness override.

### Missing or unresolved TechnologyKind during credential preflight

If `resolveAlgitesRequiredCredentials` reports that no TechnologyKinds were resolved, verify that each distributable artifact uses the current `artifact.technologyKinds` declaration. Legacy keys such as `artifact.type` are not a TechnologyKind declaration. An explicit TechnologyKind selection only narrows declared artifact technologies; it does not create missing declarations. An accidental empty resolution is rejected rather than expanded to all repository technologies.

### Missing repository credentials

Run `resolveAlgitesRequiredCredentials` for the intended context. Confirm that endpoint `credentialProfile` ids match the universal credential document and that the selected profile contains the type required by non-secret repository metadata.

### Licensing materialization is stale

Run:

```bash
./gradlew rebuildAlgitesLicensing
```

Review and commit the generated `LICENSE`/`LICENSES/` changes.

### Python packaging tool not found

The selected Python interpreter must provide the tools used by the adapter, currently including `python -m build` for packaging and `python -m twine` for publication. Central publication workflows install these tools; local developers must provide them when invoking the corresponding tasks locally.

### Documentation contains stale technology output

Generate through `generateAlgitesDocsSite` or the standard documentation workflow. The documentation publication lifecycle clears/rebuilds the selected publication and should not be emulated by manually copying old generated directories.

## 20. Reference map

Use this guide for day-to-day authoring, then consult the source of truth when needed:

- structure, naming, inheritance, output model: `specs/Algites-Development-Structure-Specification.md`
- CI/release/lane/licensing lifecycle: `specs/Algites-Development-Lifecycle-Specification.md`
- exact YAML syntax: `devops/build/yamldefs/src/product/yamldefs/*.schema.json`
- credentials: `devops/build/credentials/README.md`
- public repository defaults: `repository/defaults/README.md`
- licensing: `licensing/README.md`
- shared Gradle implementation: `gradle/tool/`
- reusable public workflows: `.github/workflows/`

The private DevOps operator guide is maintained separately in `priv.gov.Algites` because it documents private upload/manage governance, centralized publication workers, repository service lists, and operational credentials.
