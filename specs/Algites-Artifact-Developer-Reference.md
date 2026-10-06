# Algites Artifact Developer Reference

> The Gradle-independent build model is defined in `Modustro-Builder-Architecture-Specification.md`. The current Gradle and Python publication adapters use its publication and finalization contracts; section 11 describes their operational metadata, state records, and cross-domain coordination.

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
├── modustro-source-repository.yml
├── license-usage.yml                     optional local licensing declaration
├── licensing/                            repository-local license definitions/texts
├── <container>/
│   ├── modustro-artifact-set.yml          optional, nestable
│   ├── <artifact>/
│   │   ├── modustro-artifact.yml
│   │   ├── build.gradle.kts              optional custom Gradle behavior
│   │   ├── src/
│   │   │   ├── product/
│   │   │   │   └── <source-type>[.gen|.extgen]
│   │   │   └── develop/
│   │   │       └── <source-type>[.gen|.extgen]
│   │   └── doc/
│   │       ├── product/
│   │       └── develop/
│   └── <nested-container>/
│       └── modustro-artifact-set.yml
└── ...
```

Discovery is structural. Once an `modustro-artifact.yml` is found, discovery stops below that artifact. Directories inside the artifact are implementation details, not candidate artifact-set nodes.

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
| `modustro-source-repository.yml` | repository root | `modustro-source-repository_1.yamldef.schema.json` |
| `modustro-artifact-set.yml` | inheritable container | `modustro-artifact-set_1.yamldef.schema.json` |
| `modustro-artifact.yml` | artifact leaf | `modustro-artifact_1.yamldef.schema.json` |
| `license-usage.yml` | hierarchical licensing declaration | `algites-license-usage_1.yamldef.schema.json` |
| `licensing/license-definitions.yml` | repository-local license catalog | `algites-license-definitions_1.yamldef.schema.json` |

Supporting reusable schemas include:

- `algites-version_1.yamldef.schema.json`
- `algites-dependencies_1.yamldef.schema.json`
- `algites-version-requirement_1.yamldef.schema.json`
- `algites-environment-requirements_1.yamldef.schema.json`
- `algites-repository-defaults_1.yamldef.schema.json`
- `algites-credential-profiles_1.yamldef.schema.json`
- `algites-credentials_1.yamldef.schema.json`
- `algites-publication-readiness_1.yamldef.schema.json`
- `modustro-artifact-manifest_1.yamldef.schema.json`

The schemas use versioned filenames. A schema revision is therefore explicit and does not silently replace the meaning of an older version.

### 3.1 Dependency declarations

Dependency declarations are part of the inherited Algites metadata model and can be declared at repository, artifact-set, or artifact level. The dependency identity is:

```text
DependencyKind + GroupId + ArtifactId + VariantId
```

`Usages` and `RequiredBuildOutputTypes` do **not** participate in identity. When the same dependency identity is contributed by several hierarchy levels, both sets are merge-only and therefore accumulate rather than overwrite inherited requirements. VersionRequirement scalar properties inherit independently; an omitted property inherits, an explicit `null` clears the inherited value, and an explicit value overrides it.

Two top-level properties are supported:

- `Dependencies` creates dependency edges.
- `DependencyConstraints` constrains versions but does not create dependency edges.

Dependencies are grouped by `DependencyKind`. `modustro` references an artifact controlled by the Modustro model; `java` and `python` describe native ecosystem dependencies:

```yaml
Dependencies:
  - DependencyKind: modustro
    Items:
      - GroupId: eu.algites.lib.security
        ArtifactId: pub.lib.Security_credentials.coreimpl
        VariantId: jakarta
        Usages: [product_implementation, develop_implementation]
        RequiredBuildOutputTypes: [java_classes_jar]
        VersionRequirement:
          Minimum: ">=1.2.0"
          Maximum: "<2.0.0"
          MaximumStrict: false
          Exclude:
            Items: ["1.4.0", "1.6.0"]
          Prefer: "1.5.2"

  - DependencyKind: java
    Items:
      - GroupId: org.example
        ArtifactId: example-library
        Usages: [product_implementation]
        VersionRequirement:
          Minimum: ">=2.0"
          Maximum: "<3.0"
          Prefer: "2.5"

  - DependencyKind: python
    Items:
      - ArtifactId: pyyaml
        Usages: [product_implementation]
        VersionRequirement:
          Minimum: ">=6.0"
          Maximum: "<7.0"
```

For a Modustro dependency that resolves to an artifact in the same source repository, `GroupId` may be omitted; the build maps canonical `ArtifactId` plus optional `VariantId` to the corresponding local artifact. Native Java dependencies require `GroupId`; native Python dependencies use their Python distribution name in `ArtifactId`.

Each `DependencyKind` group may set `ItemsInheritancePolicy` to `merge_missing_items` (default) or `remove_missing_items`. The policy controls only membership of dependencies of that `DependencyKind`; a same-identity item is recursively merged in either mode. An explicit empty `Items: []` is valid, so `remove_missing_items` with an empty list removes all inherited dependencies of that kind. `Usages` and `RequiredBuildOutputTypes` inside a surviving same-identity dependency remain merge-only and cannot be narrowed by omission.

`Usages` is a merge-only set. Omitting it for a newly introduced dependency defaults to `product_implementation`. The active dependency bridge supports the standard Java/Java-Library roles `product_api`, `product_implementation`, `product_compile_only`, `product_compile_only_api`, `product_runtime_only`, `product_annotation_processor`, `develop_implementation`, `develop_compile_only`, `develop_runtime_only`, and `develop_annotation_processor`. The Java bridge maps each usage directly to the corresponding Gradle configuration. Python intentionally has a lossy mapping: product API/implementation/runtime roles become normal package/runtime dependencies; compile-only roles map to the Modustro build/source-processing role and are not published as runtime requirements (the bridge resolves this role but does not yet materialize its dedicated environment); annotation-processor roles are currently diagnostic/no-op; development roles stay outside published runtime package metadata.

`RequiredBuildOutputTypes` is also merge-only and is valid for `DependencyKind: modustro`. When omitted, the target TechnologyKind's `DefaultDependencyOutputTypes` apply. This is independent from the artifact's own `BuildOutputTypes`: dependency output requirements describe what a consumer needs from a target, while `BuildOutputTypes` describes what the current artifact should produce.

`VersionRequirement` is shared across dependency kinds. `Exact` is a hard exact requirement and is mapped to native strict/exact semantics. A single source declaration must not combine a non-null `Exact` with non-null range/preference properties. Hierarchical inheritance may nevertheless produce an effective `Exact` together with an inherited `Prefer`; in that case `Prefer` is ignored with an informational diagnostic. An explicit scalar `null` clears an inherited version property.

`Minimum` is a hard lower bound. `Maximum` may be strict or non-strict according to `MaximumStrict`; non-strict maxima may be relaxed only by the designated native-resolution fallback. `Exclude` remains hard and is itself an inherited collection using `Items` plus optional `ItemsInheritancePolicy`; `Exclude: null` explicitly clears inherited exclusions. `Prefer` is advisory. A conflict among simultaneously effective hard requirements is a native dependency-resolution failure, not an inheritance failure. Thus an artifact may override an inherited exact version successfully while a later WAR, deployment package, or other assembly can still fail when all participating artifacts are resolved together.

For Python dependency preflight the portable policy is evaluated in at most three global phases: `PREFERRED`, `NON_STRICT_MAXIMUMS`, and `STRICT_MAXIMUMS`. `PREFERRED` attempts preferred candidates, `NON_STRICT_MAXIMUMS` retains relaxable upper bounds, and `STRICT_MAXIMUMS` removes only non-strict upper bounds. The build always delegates the actual graph solution to the native Python resolver.

### 3.2 Canonical definitions and publication sidecars

Canonical machine-readable definitions belong under representation-specific source roots such as `src/product/yamldefs`, `src/product/jsondefs`, and `src/product/xmldefs`. Definition filenames are versioned (`..._1...`) so a changed contract does not silently replace an older one. YAML- and JSON-oriented definitions expressed as JSON Schema retain the target representation in the logical filename, for example `name_1.yamldef.schema.json` and `name_1.jsondef.schema.json`; XML definitions use their normal `.xsd` filename.

Every canonical definition that participates in global publication carries a sibling `<definition-file>.meta.yml` **user sidecar**. Its contract is `global-publication-user-metadata_1` and its only business field is `GlobalPublicationPathId`. The sidecar is author-controlled input to publication; deployment state, timestamps, revisions, publisher identity, and other server-controlled fields are forbidden by the user-metadata schema rather than ignored.

`GlobalPublicationPathId` is the logical path below the canonical definition source root. It therefore excludes technical segments such as `src/product/yamldefs` itself and must not insert an artificial representation directory; the publication endpoint already distinguishes `/api/yamldefs/`, `/api/jsondefs/`, and `/api/xmldefs/`.

The publication service validates the user sidecar and generates a separate `global-publication-deploy-metadata_1` sidecar for deployed content. Deploy metadata carries the validated `GlobalPublicationPathId` plus server-controlled `PublicationState` (`draft` or `release`), `PublicationRevision`, `FirstPublishedAt`, `PublishedAt`, optional `ReleasedAt`, and optional trusted `PublishedBy`. `PublicationRevision` is controlled by the publisher and monotonically increases when deployed draft content is replaced. Transitioning a definition to `release` freezes that content revision; a released `GlobalPublicationPathId` is immutable and must never transition back to `draft`. If `PublishedBy` is present, the deployment adapter must derive it from authenticated/trusted execution context and must never copy it from repository metadata or another author-controlled input.

The planned automatic definition `SystemId` follows the same logical-path rule: it is derived from the effective `GroupId`, the artifact-local logical path, the definition-relative directory path inside its canonical definition source root, and the logical filename. Source-root implementation segments are not part of that identity. Ordinary builds validate source metadata; automatic user-sidecar creation is reserved for an explicit generation task rather than silently mutating authored sources. Deploy sidecars are never generated in the source repository; they are created only at the publication trust boundary.

Root YAML/JSON documents may expose `$schema` as technical document metadata. For generated YAML roots the comment form used by YAML language servers, the commented `$schema` hint, and the actual `$schema` property should identify the same schema URI. Embedded contracts keep `$schema` at the document root rather than forcing it into embedded business objects.

## 4. `modustro-source-repository.yml`

A source repository begins with a root descriptor.

Minimal example:

```yaml
SourceRepository:
  Id: pub.lib.Example
  Name: Algites example public library repository

GroupId: eu.algites.lib.example

Version:
  ReleaseLineVersion: "1"
  Revision: 0
  QualifierKind: snapshot
```

### 4.1 `SourceRepository`

| Attribute | Required | Meaning |
| --- | ---: | --- |
| `SourceRepository.Id` | yes | Canonical source repository identity. |
| `SourceRepository.Name` | no | Human-readable repository name. |
| `SourceRepository.Visibility` | no | Explicit `pub` or `priv` visibility when needed. Normally repository identity/naming and governance determine visibility. |
| `SourceRepository.InputSubscriptions` | no | Input-subscription contribution at repository scope. |
| `SourceRepository.OutputPublications` | no | Output-publication contribution at repository scope. |

Wire names in all examples below follow the Algites structured-data naming convention from the Development Structure Specification: Algites fields use `UpperCamelCase` and symbolic values use `lower_snake_case`.
`TechnologyKind`, input/output selectors, visibility, and stability are symbolic values and therefore retain their governed `lower_snake_case` spelling where applicable.

The repository descriptor may also contain these top-level inheritable properties:

- `GroupId`
- `Version`
- `Dependencies`
- `DependencyConstraints`
- `CredentialProfiles`
- `PublicationReadiness`
- `InputSubscriptions`
- `OutputPublications`

These are top-level siblings of `SourceRepository`; do not nest them inside the `SourceRepository` object unless the schema explicitly defines a field there.

## 5. `modustro-artifact-set.yml`

An artifact set groups descendant artifacts and can contribute inherited defaults.

```yaml
ArtifactSet:
  Name: Example component family
  Description: Shared metadata for the example component family.
  TechnologyKinds: [java]

GroupId: eu.algites.example.component

PublicationReadiness:
  Level: snapshot
  Cause: |
    Public API is still being stabilized.
    Snapshot publication is allowed for integration testing.
  Author: Example Maintainer
```

### 5.1 `ArtifactSet` attributes

| Attribute | Required | Meaning |
| --- | ---: | --- |
| `ArtifactSet.Name` | no | Human-readable set name. |
| `ArtifactSet.Description` | no | Free-form description. |
| `ArtifactSet.TechnologyKinds` | no | TechnologyKinds made available to descendants as structural metadata. Allowed values currently include `java`, `python`, `mps`, and `modustro`. |
| `ArtifactSet.InputSubscriptions` | no | Input-subscription contribution at this container. |
| `ArtifactSet.OutputPublications` | no | Output-publication contribution at this container. |
| `ArtifactSet.Version` | no | Version-context contribution at this container. |

Top-level `GroupId`, `Version`, `Dependencies`, `DependencyConstraints`, `EnvironmentRequirements`, `CredentialProfiles`, `PublicationReadiness`, `InputSubscriptions`, and `OutputPublications` are also allowed.

Artifact sets may be nested. Inheritance follows the actual structural path from repository root through every containing artifact set to the artifact.

## 6. `modustro-artifact.yml`

Every artifact leaf has an artifact descriptor.

```yaml
Artifact:
  Name: Example definitions
  Description: Shared definitions published for both Java and Python consumers.
  TechnologyKinds: [java, python]
```

### 6.1 `artifact` attributes

| Attribute | Required | Meaning |
| --- | ---: | --- |
| `Artifact.TechnologyKinds` | yes | Technologies actually produced/published by the artifact. Current values: `java`, `python`, `mps`, `modustro`. |
| `Artifact.Name` | no | Human-readable artifact name. |
| `Artifact.Description` | no | Free-form description. |
| `Artifact.VariantId` | no | Optional lowercase dash-separated variant identity appended to native artifact/distribution identity. |
| `Artifact.InputSubscriptions` | no | Input-subscription contribution for this artifact. |
| `Artifact.OutputPublications` | no | Output-publication contribution for this artifact. |
| `Artifact.Version` | no | Version-context contribution for this artifact. |

Top-level `GroupId`, `Version`, `Dependencies`, `DependencyConstraints`, `EnvironmentRequirements`, `CredentialProfiles`, `PublicationReadiness`, `InputSubscriptions`, and `OutputPublications` are also allowed.

For readability, when `Name`, `Description`, and `TechnologyKinds` are present in an `Artifact` or `ArtifactSet` object, keep them in this order: `Name`, `Description`, `TechnologyKinds`. This is an authoring convention rather than YAML semantics.

`TechnologyKinds` is the normative declaration of build/publication technologies. Source directory names alone do not select a TechnologyKind.

### 6.1.1 Definition-driven generated sources

Canonical definitions connect to the standard `source_native_processing` lifecycle by convention. The common adapter discovers files below the representation-specific product definition roots, derives package/module namespaces from their relative directories, and generates Java/Python sources for the matching artifact TechnologyKinds. When multiple representation-specific definitions generate the same target path, identical generated content is deduplicated; divergent content is a build error. The standard adapter invokes the reusable Defs Codegen Java API directly; it does not shell out to the CLI.

Generated Java and Python files are materialized under `src/product/java.gen` and `src/product/python.gen` respectively, are reproducible, and MUST NOT be edited manually. The `.gen` roots may be shared by multiple generators: Defs Codegen tracks only its own generated files in disposable `build/run` state, removes stale owned files, and fails on duplicate target paths instead of overwriting them silently. `clean` removes standard `.gen` roots. A generator task attaches only to the corresponding `processModustro*NativeSources` capability boundary; downstream compile/package/documentation task wiring remains owned by the common adapter.

### 6.1.2 Selecting BuildOutputTypes

The compact form selects technologies and uses inherited/built-in output defaults:

```yaml
Artifact:
  TechnologyKinds: [java, python]
```

Use the extended form when one TechnologyKind needs an explicit build-output selection:

```yaml
Artifact:
  TechnologyKinds:
    Items:
      - TechnologyKind: java
        BuildOutputTypes:
          Items:
            - BuildOutputType: java_classes_jar
            - BuildOutputType: java_sources_jar
```

The built-in defaults currently are:

| TechnologyKind | Default build outputs | Default dependency output |
| --- | --- | --- |
| `java` | `java_classes_jar`, `java_sources_jar` | `java_classes_jar` |
| `python` | `python_wheel`, `python_sdist` | `python_distribution` |

`java_javadoc_jar` is supported but is not a default build output. `python_distribution` is virtual and dependency-only; requesting it as an artifact build output is an error.

`TechnologyKinds.ItemsInheritancePolicy` and nested `BuildOutputTypes.ItemsInheritancePolicy` accept `merge_missing_items` (default) and `remove_missing_items`. Omitting `BuildOutputTypes` inherits a selection from an ancestor if present; otherwise the built-in default applies. An explicit `BuildOutputTypes: { Items: [] }` retains the TechnologyKind while requesting no packaging output for it.

The active build applies these selections through Gradle-independent production plans: Java maps to classes JAR, sources JAR, and optional Javadoc JAR production, while Python maps to wheel and/or sdist production. The selected production plans then contribute their required capabilities to the capability demand graph. Dependency-resolution preflight is wired only when an effective output or documentation demand requires `dependency_resolution`, while shared prerequisites are deduplicated by the capability planner.

### 6.1.3 `PreparedSourceSet`, production plans, and capability demands

`AIcPreparedSourceSet` is the portable boundary between source discovery/preparation and output production. For one TechnologyKind it carries three deterministic root groups: native handwritten source roots, generated source roots, and resource/definition roots. Build-output producers consume this prepared description and return an `AIcBuildOutputProductionPlan`; the producer does not execute Gradle tasks itself.

A production plan identifies the concrete `BuildOutputType`, its built-in production primitive, additional logical inputs, and the capability IDs that must be satisfied first. The current built-in mappings are:

| BuildOutputType | Required capabilities |
| --- | --- |
| `java_classes_jar` | `source_native_processing`, `dependency_resolution` |
| `java_sources_jar` | `source_native_processing` |
| `java_javadoc_jar` | `generation_of_native_documentation` |
| `python_wheel` | `source_native_processing`, `dependency_resolution` |
| `python_sdist` | `source_native_processing` |

`generation_of_native_documentation` expands to `source_native_processing` plus `dependency_resolution` for Java and Python. Capability demands are deduplicated by `(TechnologyKind, Capability, Scope, ScopeIdentity)` and assembled into a prerequisite-before-dependent DAG. This lets multiple outputs and documentation consumers share one prerequisite without each wiring an independent Gradle dependency chain.

For artifact-local source generators, `source_native_processing` is the supported integration boundary. A generator that materializes files consumed as native/generated sources should make the corresponding `processModustroJavaNativeSources` or `processModustroPythonNativeSources` task depend on the generator. It SHOULD NOT additionally wire itself directly to downstream tasks such as `preparePythonBuildProject`, `buildPython`, `compileJava`, or packaging tasks; the common adapter owns those downstream dependencies from the capability graph. The `devops/build/yamldefs` artifact is the reference example for convention-only canonical definitions: its local Gradle script contains only the standard plugins. Modustro Builder discovers canonical definitions from the governed source roots and attaches the common generator to the corresponding `processModustro*NativeSources` capability boundary.

### 6.2 Environment requirements

`EnvironmentRequirements` reuses the same portable version-requirement model for build/test environments. For example:

```yaml
EnvironmentRequirements:
  Java:
    Minimum: ">=21"
    Prefer: "21"
  Python:
    Minimum: ">=3.14"
```

The Java adapter uses the preferred version, exact version, or minimum bound (in that order) to select the Java toolchain used for compilation and tests. Python publishes the hard `STRICT_MAXIMUMS` form as `Requires-Python`.

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

The active build and documentation adapters use one shared source-root resolver. For any SourceType it resolves the canonical `<type>`, `<type>.gen`, and `<type>.extgen` candidate roots under the requested `product` or `develop` scope; consumers may ignore roots that do not exist yet, while generators can materialize `.gen` roots later in the same build. Artifact-local Gradle scripts SHOULD NOT duplicate these lists.

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

A multi-technology artifact does not have to contain handwritten source directories for every output. `pub.gov.Algites/devops/build/yamldefs` is an example: common YAML-definition sources are packaged for Python by the generic TechnologyKind adapter while the same logical artifact is also published for Java. No artifact-specific Gradle staging code is required.

`schema` is not a canonical SourceType. Use `jsondefs`, `yamldefs`, or `xmldefs` for definitions according to their semantic representation, and use `config` for concrete configuration instances regardless of serialization format. The source-root name is not repeated inside the business-relative path.

For Python artifacts, the Algites adapter automatically stages `jsondefs`, `yamldefs`, `xmldefs`, and `config` product roots into the artifact's derived Python import namespace, preserving the path below each canonical source root. The generic `source_native_processing` implementation materializes these package resources in the disposable Algites run workspace; `preparePythonBuildProject` then places them below `src/product/python.gen` in the staged Python project. An artifact must not contain a custom Gradle copy task for this standard mapping. Native/generated Python source roots and staged resources are merged with collision detection. Shared package prefixes remain PEP 420 namespace packages, and the framework does not generate shared-parent `__init__.py` files.

The supported Python build entry point is the Algites Gradle lifecycle. The Python adapter prepares the staging project before invoking Python packaging tools, so an artifact MUST NOT introduce artifact-local `setup.py` or `MANIFEST.in` workarounds merely to copy or include `jsondefs`, `yamldefs`, `xmldefs`, or `config` product sources. A `pyproject.toml` may still describe Python distribution metadata, but direct `python -m build` execution against the unstaged repository source tree is not a supported Algites build mode. Only the Gradle adapter can coordinate the complete multi-TechnologyKind build and validate cross-distribution path collisions.

For Python product code, each main public Algites `AI*` type MUST be declared in its own deterministic snake_case module named from that type (for example `AIcDisplayText` in `aic_display_text.py`). Private or implementation helper types MAY remain in the same module. Repository validation MUST reject a product module that declares multiple main public `AI*` types or whose filename does not match its public type.

Python distribution names use an owner prefix derived from the effective `groupId`, not a hard-coded Algites prefix. Take the first at most two non-empty dot-separated `groupId` components and join them with `-`; the selected `groupId` dot is therefore converted to a hyphen for Python packaging. Append the normalized `ArtifactCoordinateId` with `-` only when both parts are non-empty. Examples: `eu.algites.product` produces owner prefix `eu-algites`; `com` produces `com`; an absent/empty `groupId` produces no owner prefix and no leading hyphen.


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

### 7.4 Java API documentation

New Algites Java product types must carry class/interface/enum Javadoc. Every declared `public` or `protected` constructor and method must also be documented, including `@param`, `@return`, and `@throws` tags where they are part of the contract. Overriding methods may use inherited API semantics, but their source should still have an explicit Javadoc block when the implementation is part of an Algites public/protected type. Private helpers need documentation only when their purpose, assumptions, or failure modes are not evident from the code.

Java source comments use Javadoc (`/** ... */`) or block comments (`/* ... */`); line comments are not used in Algites Java sources. Documentation should describe semantic contracts and invariants rather than merely restating identifiers.

## 8. Inheritance and effective metadata

Algites metadata is resolved from the structural path:

```text
source repository
    -> artifact set
        -> nested artifact set
            -> artifact
```

Different properties use different merge semantics. Do not assume every field follows nearest-value override.

### 8.1 `GroupId`

`GroupId` is an independent top-level inherited value. The nearest descendant declaration replaces the inherited value for that node and its descendants.

```yaml
# repository
 GroupId: eu.algites.lib
```

```yaml
# descendant artifact set
 GroupId: eu.algites.lib.specialized
```

### 8.2 InputSubscriptions and OutputPublications

`InputSubscriptions` merge by `TechnologyKind`, expanded concrete selector, and stable subscription `Id`.
`OutputPublications` merge by `TechnologyKind` and expanded concrete selector; `PublicationEndpoints`, `Publications`,
root `PublicationFinalizationActions`, and recursive child `FinalizationActions` merge by stable sibling `Id`.
Output, artifact, and Version Scope finalizer lists also merge by Id in their independent Snapshot/Release branches.
A repeated Id at the same merge location yields one effective action. A descendant can modify or disable an inherited
item without repeating unrelated properties.

```yaml
InputSubscriptions:
  - InputSelector: native_product_binaries
    TechnologyKind: java
    Subscriptions:
      - Id: algites-java-public-snapshot-subscription
        Enabled: false

OutputPublications:
  - OutputSelector: native_product_binaries
    TechnologyKind: java
    Snapshot:
      PublicationEndpoints:
        - Id: algites-java-public-snapshot-publication
          PublicationRetryCount: 2
```

An explicit empty list clears the inherited list at that exact level. `TechnologyKind` is required on every declaration
and is never inferred as "all technologies".

### 8.3 `PublicationReadiness` is a cap

Publication readiness is intentionally not ordinary child override inheritance. The effective level is the minimum level declared anywhere on the structural path.

```text
none < snapshot < release
```

A descendant cannot relax an ancestor restriction.

## 9. Publication readiness

`PublicationReadiness` protects the publication lifecycle without preventing normal development builds.

```yaml
PublicationReadiness:
  Level: none
  Cause: |
    Migration compatibility is incomplete.
    Publication is blocked until the compatibility suite passes.
  Author: Example Maintainer
```

| Field | Required | Meaning |
| --- | ---: | --- |
| `Level` | yes when object is present | `none`, `snapshot`, or `release`. |
| `Cause` | no | Free-form explanation; multiline YAML is supported. |
| `Author` | no | Informational author/owner string. It is not an authorization identity. |

If the object is absent, the implicit level is `release`.

### 9.1 Level semantics

- `none`: artifact cannot participate in snapshot or release publication;
- `snapshot`: snapshot publication is permitted, release publication is blocked;
- `release`: snapshot and release publication are permitted.

Readiness does **not** remove an artifact from discovery and does **not** prevent compilation/testing. A local project dependency may still cause tasks of a `none` artifact to run when another local artifact needs it.

Publication is different. Before `modustroPublish`, the framework validates the selected controlled publication closure. If a selected artifact depends on a local controlled artifact whose effective readiness is too low, publication stops before upload. Diagnostics include the blocking descriptor path and, when supplied, `Cause` and `Author`.

## 10. Version

`Version` uses `algites-version_1.yamldef.schema.json`.

Supported fields are:

| Field | Meaning |
| --- | --- |
| `Lane` | lifecycle lane identity when used by the repository/version model |
| `ReleaseLineVersion` | numeric release-line version scope |
| `Revision` | revision component |
| `QualifierKind` | optional portable qualifier; v1 currently defines `snapshot` |

`QualifierKind` is intentionally closed and portable. Version v1 removes the former free-form qualifier label; a snapshot is declared as `QualifierKind: snapshot`, while a final release omits `QualifierKind`. Additional portable qualifier kinds will be introduced only when their cross-technology mapping is defined.

A version declaration is container-scoped and may be overridden at a lower structural boundary when artifacts need independent logical version lifecycles.

The effective version is resolved by Algites infrastructure; technology adapters map it into ecosystem-specific publication versions.

## 11. InputSubscriptions and OutputPublications

Modustro Builder models external I/O as two explicit directions:

```text
InputSubscriptions  -> external content consumed by the build
OutputPublications  -> build outputs published to external destinations
```

The former `ResourceEndpoints` matrix and its `download`, `upload`, and `manage` action dimension are not part of the
active descriptor contract. Download/resource resolution became `InputSubscriptions`; publishing became
`OutputPublications`. Finalization distinguishes concrete publication, output, logical artifact, and Version Scope.
Recursive `PublicationFinalizationActions` and child `FinalizationActions` express dependencies within one publication;
flat higher finalizers receive complete results for their larger consistency boundary.

Every declaration has an explicit `TechnologyKind`. Omission never means "all technologies". Effective configuration
must identify exactly one of `java`, `python`, `mps`, or `modustro` for each declaration.

### 11.1 InputSubscriptions

An input-subscription declaration has this shape:

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

`InputSelector` may be a concrete output kind or a virtual group. Concrete native kinds are
`native_product_sources`, `native_product_binaries`, `native_product_documentation`, `native_develop_sources`,
`native_develop_binaries`, and `native_develop_documentation`. `modustro_docs_site` and `schema_site` are also valid
concrete selectors where an input use exists. Virtual groups are `native_outputs`, `native_product_outputs`,
`native_develop_outputs`, `native_sources`, `native_binaries`, and `native_documentation`.

Group selectors are expanded before the effective model is consumed. `native_outputs` is the broadest base; other
overlapping groups are applied in declaration order; a concrete selector has highest precedence.

A `Subscription` has these fields:

| Field | Required | Meaning |
| --- | ---: | --- |
| `Id` | yes | Stable sibling identity used for hierarchical merge. |
| `Enabled` | no | Defaults to `true`. |
| `Visibility` | no | `public` or `private`; defaults to `public`. |
| `Stability` | no | `release` or `snapshot` when the input channel distinguishes stability. |
| `SubscriptionUri` | required for enabled effective subscription | Canonical input location. |
| `SubscriptionAdapter` | required for enabled effective subscription | Adapter used to consume the input. |
| `SubscriptionCredentialProfile` | no | Non-secret credential profile reference. |
| `SubscriptionOrder` | no | Ordered lookup priority; defaults to `0`. |
| `Configuration` | no | Adapter-specific, non-secret configuration. |

Subscriptions merge by `TechnologyKind`, expanded concrete `InputSelector`, and stable `Id`. Sparse descendants inherit
unspecified properties. An explicit empty `Subscriptions: []` clears the inherited list for that declaration.

Public source repositories may consume only public subscriptions. Private source repositories may consume public and
private subscriptions. Public defaults are available from
`repository/defaults/algites-repository-defaults-public.yml`; private governance contributes additional overlays
explicitly.

### 11.2 OutputPublications

`OutputPublications` is the only canonical publication-destination model:

```yaml
OutputPublications:
  - OutputSelector: native_product_binaries
    TechnologyKind: java

    Snapshot:
      PublicationEnabled: true
      PublicationEndpoints:
        - Id: algites-java-snapshot
          PublicationUri: https://maven.example.invalid/snapshots/
          PublicationAdapter: maven-repository
          PublicationCredentialProfile: algites-java-snapshot-publication
          ExecutionOrder: 0
          PublicationFailurePolicy: FAIL_BUILD_ON_PUBLICATION_FAILURE
          PublicationRetryCount: 2
          PublicationWaitForNextAttemptMillis: 1000
          PublicationAttemptTimeoutMillis: 60000
          ShowPublicationProgressIfPossible: true

          Publications:
            - Id: standard
```

`Snapshot` and `Release` are independent inherited branches. `PublicationEnabled` controls publication only; it does
not disable source processing, compilation, verification, packaging, or output production.

The same selector expansion rules apply as for `InputSubscriptions`, but each declaration is always scoped to one
explicit `TechnologyKind`.

### 11.2.1 Enablement and scheduling names by location

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

The scheduling policy names also differ by operation:

| Endpoint publication policy | Finalization-action policy |
| --- | --- |
| `ExecutionOrder` | `ExecutionOrder` |
| `PublicationFailurePolicy` | `FailurePolicy` |
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

### 11.3 PublicationEndpoint fields

| Field | Required | Meaning |
| --- | ---: | --- |
| `Id` | yes | Stable endpoint identity and optional action target identity. |
| `ExecutionEnabled` | no | Defaults to `true`. |
| `PublicationUri` | required for enabled effective endpoint | Canonical destination URI. |
| `PublicationAdapter` | required for enabled effective endpoint | Adapter that performs one publication attempt. |
| `PublicationCredentialProfile` | no | Non-secret credential profile reference. |
| `ExecutionOrder` | no | Root scheduling order, default `0`; negative values are allowed. |
| `PublicationFailurePolicy` | no | Defaults to `FAIL_BUILD_ON_PUBLICATION_FAILURE`. |
| `PublicationRetryCount` | no | Additional attempts after the first; defaults to `0`. |
| `PublicationWaitForNextAttemptMillis` | no | Delay between attempts; defaults to `1000`. |
| `PublicationAttemptTimeoutMillis` | no | Positive timeout for one attempt. |
| `ShowPublicationProgressIfPossible` | no | Defaults to `true`. |
| `Configuration` | no | Adapter-specific, non-secret configuration. |
| `Publications` | no | Concrete forms; omitted means one implicit `Id: standard` publication. |

Endpoints merge by stable `Id`. A descendant can override only one endpoint field without repeating its URI,
adapter, credentials, and scheduler policy. `PublicationEndpoints: []` explicitly clears inherited endpoints.

### 11.4 Root Publications

A `PublicationEndpoint` may publish multiple concrete forms:

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

`Classifier` and `Extension` are Maven-compatible form overrides. Each root `Publication` is an independent scheduler
unit. A retry of one publication never retries another publication that already succeeded.

For Maven snapshots, all forms created from one root payload share one reserved timestamp/build-number instance so the
main artifact and sidecars stay internally consistent.

### 11.5 Implicit build-record publication finalization action

Every enabled root `Publication` receives one implicit direct publication finalization action:

```yaml
PublicationFinalizationActions:
  - Id: build-record
    PublicationFinalizationActionAdapter: modustro-build-record
```

Its file is named exactly:

```text
<published-filename>.modustro-build-record.yml
```

The complete published filename is preserved, including classifier and compound extension.

Declare the same stable action id to customize or disable it:

```yaml
PublicationFinalizationActions:
  - Id: build-record
    ExecutionEnabled: false
```

The implicit action is added only to root publications. Child actions never receive another implicit build record.
Python package indexes and any other transport that cannot store arbitrary sidecars must disable the action or target a
metadata-capable endpoint with `TargetPublicationEndpointId`.

### 11.6 Publication finalization levels

Four consistency boundaries are supported. Child `FinalizationActions` are recursion within the publication level,
not a fifth consistency boundary:

| Declaration | Location | Adapter property | Recursive |
| --- | --- | --- | --- |
| `PublicationFinalizationActions` | Root publication | `PublicationFinalizationActionAdapter` | Yes, via child `FinalizationActions` |
| `FinalizationActions` | Publication finalization action | `FinalizationActionAdapter` | Yes |
| `OutputPublicationFinalizationActions` | Output Snapshot/Release branch | `OutputPublicationFinalizationActionAdapter` | No |
| `ArtifactPublicationFinalizationActions` | Descriptor/default Snapshot/Release branch | `ArtifactPublicationFinalizationActionAdapter` | No |
| `VersionScopePublicationFinalizationActions` | Descriptor/default Snapshot/Release branch | `VersionScopePublicationFinalizationActionAdapter` | No |

The common action fields are:

| Field | Meaning / default |
| --- | --- |
| `Id` | Required stable lowercase dash-separated sibling identity; also the hierarchical merge key. |
| `ExecutionEnabled` | Default `true`; `false` disables this inherited action without clearing its siblings. |
| Adapter property | The level-specific property above; required for an enabled effective action. |
| `ExecutionOrder` | Default `0`; negative values allowed; orders direct siblings of this list. |
| `FailurePolicy` | `FAIL_BUILD_ON_FAILURE` (default) or `IGNORE_FAILURE`. |
| `RetryCount` | Additional attempts after the first; default `0`. |
| `WaitForNextAttemptMillis` | Default `1000`. |
| `AttemptTimeoutMillis` | Optional positive timeout for one attempt. |
| `ShowProgressIfPossible` | Default `true`. |
| `Configuration` | Adapter-specific non-secret values. |
| `TargetPublicationEndpointId` | Optional on recursive publication actions only; otherwise the root endpoint is inherited. |
| `FinalizationActions` | Recursive children of a publication-level action only. |

This fragment illustrates placement of every level:

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

`example-*` names are custom adapter extension points and must be registered; they are not built-in adapters.
Implicit `modustro-build-record` remains active unless explicitly disabled.

Output finalization waits for all publication trees of that output, including recursive descendants. Artifact finalization
waits for all expected output results of one logical artifact. Version Scope finalization waits for all artifacts in the
inherited version boundary. Each higher adapter receives complete immutable lower results, including ignored failures,
endpoint registries, and nested publication action results. Expected work that never executes remains incomplete.
Different Java/Python transport identities do not split one logical artifact into separate finalization boundaries.

Lists merge by Id within their own stability/parent location. A sparse descendant can disable one inherited action
with `ExecutionEnabled: false` (not `PublicationEnabled: false`):

```yaml
VersionScopePublicationFinalizationActions:
  Release:
    - Id: remove-corresponding-snapshots
      ExecutionEnabled: false
```

`Release: []` clears the entire inherited release finalizer list, including docs refresh. Omitting the branch inherits
it; clearing Snapshot leaves Release intact. Higher lists cannot contain recursive child actions.

### 11.7 Dependency tree and local ExecutionOrder

Tree nesting is a hard dependency and data-flow relationship. A child action cannot become eligible before its
immediate parent succeeds.

`ExecutionOrder` has a different purpose: it orders only direct siblings of one parent. Lower values run first and equal values
may run concurrently. A later sibling-order group waits for direct actions in the previous group, not for those
actions' descendants.

Likewise `ExecutionOrder` applies only among direct root publications. A later root order does not wait for the
publication finalization action descendants of an earlier root. The complete required publication operation still waits for all required
descendant trees before final completion.

Therefore, if B needs data produced by A, model B as a child of A. Do not encode the dependency merely as `A.ExecutionOrder: 0`
and `B.ExecutionOrder: 1`.

### 11.8 Execution lineage

Every `PublicationFinalizationActionAdapter` receives the entire ordered ancestor lineage from the root publication through its
immediate parent. Every lineage step exposes:

- step `Id` and kind;
- one canonical `InputUri`;
- one canonical `OutputUri` when the step produced an output;
- effective step configuration;
- result metadata.

There is deliberately no unordered set of equivalent URIs. A consumer never has to guess whether to use a local path,
a public URL, or a repository URL. The operation itself defines its one input URI and one output URI.

A child receives its parent's output URI as its input URI when an output exists; otherwise the input URI propagates.
The context exposes ancestors only, not sibling execution state, so parallel siblings cannot accidentally influence one
another based on completion timing.

The action API does not pass an already-open stream. The URI may identify a local file, mounted storage, or remote
content that no longer exists locally or never existed locally. The adapter owns URI dereferencing. The frozen root
publication payload remains available in the action context for root identity and metadata.

### 11.9 Targeting another publication endpoint

By default a publication finalization action uses the publication endpoint inherited from its root. A publication finalization action that must operate on a
different configured destination can reference it by stable Id:

```yaml
PublicationFinalizationActions:
  - Id: publish-build-record
    PublicationFinalizationActionAdapter: modustro-build-record
    TargetPublicationEndpointId: metadata-sidecars
```

The effective plan carries an endpoint registry covering both Snapshot and Release branches of the resolved output. A
release action can therefore target an already configured snapshot endpoint without duplicating destination URI,
credentials, or provider configuration.

### 11.10 Released-snapshot cleanup and docs refresh

Released-snapshot cleanup runs once at Version Scope finalization after every native artifact/output publication in
that release boundary succeeds. The built-in adapter deduplicates corresponding snapshot targets from the complete
immutable context and resolves publication credentials inside the attempt.

```yaml
VersionScopePublicationFinalizationActions:
  Snapshot:
    - Id: refresh-docs-site
      VersionScopePublicationFinalizationActionAdapter: modustro-refresh-docs-site
      ExecutionOrder: 20
  Release:
    - Id: remove-corresponding-snapshots
      VersionScopePublicationFinalizationActionAdapter: modustro-remove-corresponding-snapshots
      ExecutionOrder: 10
      FailurePolicy: IGNORE_FAILURE
    - Id: refresh-docs-site
      VersionScopePublicationFinalizationActionAdapter: modustro-refresh-docs-site
      ExecutionOrder: 20
```

Cleanup covers Cloudsmith/Repsy Java exact release-SNAPSHOT versions and Python release.dev* series. It is governance
policy; a Maven release alone does not imply snapshot deletion. Already-absent versions are successful no-ops.
`Configuration.Provider` can explicitly select `cloudsmith`/`repsy`; provider identity uses Cloudsmith
`Workspace`/`Repository` or Repsy `Repository`, with optional `ManagementApiUri`. No MPS cleanup contract is configured.
The inherited `IGNORE_FAILURE` records a cleanup failure without invalidating the release; it does not conceal the
failure from the result context.

Docs refresh is a request, deduplicated across COMPLETE scopes before repository-only `modustro_docs_site` publication.
The site may aggregate multiple Version Scopes/versions and does not participate in native output/scope completion.
`modustroPublish` finalizes native results before `refreshModustroDocsSite` generates aggregate indexes and publishes
once when requested. Included builds contribute requests; only the repository-root domain refreshes the aggregate site.
Explicit `publishModustroDocsSite` remains available.

The common actions above are owned by the public defaults in
`pub.gov.Algites/repository/defaults/algites-repository-defaults-public.yml`. The governed public overlay in
`priv.gov.Algites/repository/defaults/algites-repository-governed-defaults-public.yml` supplies endpoints, profiles,
and provider-specific configuration, with sparse action overrides only for intentional governed differences.
Repeating identical action Ids in both layers would merge to one effective action, but can mask later public-policy
changes; the common policy is therefore declared only in the public defaults.

Partial attempts remain FAILED and suppress cleanup/docs refresh. A fresh invocation may repair publication.
Scope/bridge behavior is described in 11.15–11.16. Portable and Gradle/TestKit regressions exercise these contracts;
production provider credentials, remote uploads, and provider cleanup still require target-environment integration checks.

### 11.11 Publication and action adapters

The immutable `AIcAdapterCatalog` indexes six typed categories by adapter Id:

| Category | Portable interface | Current built-in responsibility |
| --- | --- | --- |
| Input subscription | `AIiSubscriptionAdapter` | Bootstrap subscription identities; resolution stays with technology/Gradle integration. |
| Publication transport | `AIiPublicationAdapter` | Maven HTTP/local, Python package index, local-copy, HTTP directory, Git branch, S3-compatible storage. |
| Recursive publication finalization | `AIiPublicationFinalizationActionAdapter` | `modustro-build-record`; covers root and nested publication actions. |
| Output finalization | `AIiOutputPublicationFinalizationActionAdapter` | Extension point; no built-in action currently registered. |
| Artifact finalization | `AIiArtifactPublicationFinalizationActionAdapter` | Extension point; no built-in action currently registered. |
| Version Scope finalization | `AIiVersionScopePublicationFinalizationActionAdapter` | `modustro-remove-corresponding-snapshots`, `modustro-refresh-docs-site`. |

Bootstrap uses explicit registration; automatic external plugin discovery is not implemented. Duplicate Ids within a
category and unresolved adapter names are rejected. Root `PublicationFinalizationActionAdapter` and child
`FinalizationActionAdapter` properties resolve in the same recursive category.

Each transport/finalization adapter executes one attempt. ExecutionOrder, retry, timeout, failure policy, cancellation, and
progress are scheduler-owned. A recursive action receives configuration, complete lineage, current input URI, frozen
root payload, optional resolved target endpoint, credentials, attempt/deadline/cancellation data, progress reporter,
and a publication delegate for derived content. Higher typed contexts receive complete immutable lower result trees;
they do not invent a single input URI for a collection of unrelated outputs.

Automatic retry requires declared retry safety. Credentials resolve inside the relevant finalizer attempt, so resolution
errors obey its failure/retry policy. Cancellation cooperates with the adapter; it cannot reverse remote side effects.
Progress may be determinate (completed/total/unit) or indeterminate (message).

`requiredCompletion` waits for required work while unrelated ignored work may continue. Full output results, higher
barriers, and scheduler shutdown drain the lower graph. Ignored transport failure can still prevent scope success because
the payload was not published; ignored finalizer failure is retained without invalidating that boundary. Required
failure stops later groups at that level, and unsuccessful parents cannot activate their children.

### 11.12 S3-compatible object storage

Schema-site publishing may use the generic `s3-object-storage` adapter:

```yaml
PublicationEndpoints:
  - Id: public-schema-site
    PublicationUri: https://s3.fr-par.scw.cloud/algites-dev-defs-public/
    PublicationAdapter: s3-object-storage
    PublicationCredentialProfile: algites-modustro-schema-site-public-publication
    Configuration:
      Region: fr-par
```

The current basic credential mapping uses username as the access key and password as the secret key. Secrets never
appear in governance YAML.

### 11.13 Public governance resolution for local builds

Normal local builds do not need a checkout-specific public-defaults path. When
`ALGITES_REPOSITORY_PUBLIC_DEFAULTS_FILE` is absent, the resolver loads public defaults bundled inside the installed
GradleInit JAR and materializes them below the Gradle user-home cache, keyed by content hash. This keeps defaults aligned
with the plugin version and does not require a raw GitHub download.

External default layers, applied in this order before structural descriptors, are:

| Input | Behavior |
| --- | --- |
| `ALGITES_REPOSITORY_PUBLIC_DEFAULTS_FILE` | Explicit public file overrides the bundled copy; otherwise bundled public defaults apply. |
| `ALGITES_REPOSITORY_GOVERNED_PUBLIC_DEFAULTS_FILE` | Optional explicit governed public overlay. |
| `ALGITES_REPOSITORY_PRIVATE_DEFAULTS_FILE` | Optional explicit private defaults. |

Built-in defaults precede these layers; repository, artifact-set, and artifact descriptors follow them. An explicitly
configured file must exist. Governed/private overlays are never fetched by the public fallback. CI may explicitly
supply downloaded/pinned governance files; that is distinct from the local resolver fallback.

### 11.14 Credential preflight and invocation properties

Credential preflight recognizes only:

```text
subscription
publication
```

with these canonical properties:

```text
algites.credential.usages
algites.credential.subscription.stabilities
algites.credential.publication.stabilities
algites.credential.outputKinds
algites.credential.output
```

The plan reports `inputSubscriptions`, `publicationEndpoints`, and required credential profile/type pairs. There is no
`manage` usage.

Snapshot publication invocation overrides remain available per output kind through `modustro.publication.*` Gradle
properties and corresponding `MODUSTRO_PUBLICATION_*` environment variables. They modify only effective
`PublicationEnabled`; endpoint `ExecutionEnabled` is not rewritten. Release publication does not permit non-default portable
invocation overrides.

### 11.15 Expected outputs, completion, and Version Scope records

Before native publication tasks execute, an invocation-scoped BuildService receives serializable expected-output
manifests. Descriptor-declared enabled outputs without registered producers/tasks become incomplete results;
registered tasks whose effective publication policy is disabled are excluded. An expected-but-unexecuted output has
`completed: false`, not empty-success semantics. Scopes with no attempted native publication are not finalized merely
because their descriptors exist.

The Version Scope state model is PUBLISHING, FINALIZING, COMPLETE, and FAILED. Partial attempts remain FAILED and do
not trigger cleanup or docs refresh. A later invocation with a fresh identity may repair publication; automatic
continuation of a partially committed invocation is not implemented.

Human-readable records are written under
`build/run/publication-records/<sanitized-invocation-and-fingerprint>/version-scopes/`, using collision-resistant names
and atomic replacement where supported. COMPLETE records cannot be overwritten under one invocation identity; native
publication after a finalized scope is refused. These guards protect local invocation state, not a provider-side
transaction or release-immutability policy. Deleting local run files does not undo already published remote payloads.

### 11.16 Version Scopes spanning isolated Gradle builds

An inherited Version Scope can include multiple isolated builds. Each domain completes its output/artifact trees and
commits an immutable `artifact-results` contribution. The scope-owner domain aggregates the expected domains/artifacts
and included-domain finalization receipts before invoking the scope finalizers. Missing domains/producers, incomplete
outputs, stale identities, duplicate contributions, and conflicting plans prevent COMPLETE.

The versioned bridge reconstructs complete immutable Core results from JSON-compatible data, including recursive
finalization trees, endpoint registries, content URIs, configuration, metadata, and exception diagnostics. It does not
pass live Builder objects or Java serialization across classloaders. Credential-profile definitions/names cross the
bridge; resolved secrets do not. Credentials resolve lazily against the originating domain's directory. Custom result
metadata must be JSON-compatible rather than an arbitrary Java object.

Coordination packets are below `build/run/publication-coordination/<invocation-fingerprint>/` in `artifact-results`
and `scope-finalization` stages. Locked/atomic commits accept identical replayed bytes but reject replacement with
conflicting results. This is filesystem coordination for one invocation, not a remote distributed transaction.

Every participating domain needs the same fresh `MODUSTRO_BUILD_INVOCATION_ID` or
`-Pmodustro.build.invocationId=<fresh-id>`. Direct composite publication without a common identity fails before native
upload. The phase controller establishes a local identity and preserves a supplied CI identity across its phases/children:

```bash
bash gradle/tool/repository/modustro-phase-controller.sh --through package
bash gradle/tool/repository/modustro-phase-controller.sh --through publish
```

These are separate invocations with fresh automatically generated identities when the variable was not already supplied.
A native retry under a committed identity is rejected. An explicit later docs-only refresh may read existing receipts
without native republishing or overwriting COMPLETE scope records.

See [`../devops/build/modustro/PUBLICATIONS.md`](../devops/build/modustro/PUBLICATIONS.md) for the implementation-level
scheduler and adapter reference.

## 12. Credential profiles vs credential values

InputSubscription, PublicationEndpoint, and PublicationFinalizationAction metadata never contains passwords, tokens, certificates, or other secret values.

A subscription or publication endpoint references a non-secret profile:

```yaml
CredentialProfiles:
  example-download:
    Type: basic

InputSubscriptions:
  - InputSelector: native_product_binaries
    TechnologyKind: java
    Subscriptions:
      - Id: algites-example-java-private-release-subscription
        Visibility: private
        Stability: release
        SubscriptionUri: https://example.invalid/maven/
        SubscriptionAdapter: maven-repository
        SubscriptionCredentialProfile: example-download
```

Supported profile types are:

| Type | Required fields | Optional fields |
| --- | --- | --- |
| `basic` | `Username`, `Password` | — |
| `bearer` | `Token` | — |
| `api_key` | `ApiKey` | — |
| `certificate` | `Certificate` | `PrivateKey`, `PrivateKeyPassword` |

Actual values are supplied through the universal `ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS` JSON document or an Algites secure-store integration. See [`../devops/build/credentials/README.md`](../devops/build/credentials/README.md).

### 12.1 Value sources

| Source | `Value` contains | Materialized result |
| --- | --- | --- |
| `direct_value` | credential content | unchanged content |
| `file_content` | path to a file | UTF-8 file content |
| `secret_content` | name/key in the active secret-provider context | secret content |
| `environment_variable_content` | environment-variable name | variable content |

The `_CONTENT` suffix describes what is obtained after resolution. For example, `file_content` `Value` is a path, not literal file content.

For a normal public developer build, publication credentials are not required unless that operation publishes. The framework resolves only credentials needed for the requested `InputSubscriptions` and `OutputPublications`. Credential preflight is fail-fast when no effective declared `Artifact.TechnologyKinds` remain after applying any optional TechnologyKind selection; an empty TechnologyKind set never means "all technologies".

## 13. Licensing

Algites licensing is hierarchical and materialized into the repository root `LICENSE` and `LICENSES/` directory.

For normal public local builds, central public licensing governance is available automatically. When `ALGITES_LICENSING_PUBLIC_GOVERNANCE_DIRECTORY` is not set, the licensing resolver loads `license-definitions.yml`, the default `license-usage.yml`, and the referenced license texts from `Algites-EU/pub.gov.Algites/main` on GitHub and materializes them under the Gradle user-home cache. Set `ALGITES_LICENSING_PUBLIC_GOVERNANCE_DIRECTORY` to a local `pub.gov.Algites/licensing` directory when developing governance, testing unpublished changes, working from a deliberately materialized copy, or otherwise overriding the published fallback. The `pub.gov.Algites` repository itself continues to use its local `licensing/` directory directly. Private licensing governance has no public remote fallback and remains an explicit input.

### 13.1 `license-usage.yml`

Example:

```yaml
Licenses:
  - Id: Apache-2.0
    Enabled: true
    ContentKinds:
      - product
  - Id: CC-BY-4.0
    Enabled: true
    ContentKinds:
      - documentation
```

Fields:

- `id`: license id;
- `Enabled`: enables/disables the license at that hierarchy point;
- `ContentKinds`: optional list containing `product` and/or `documentation`.

### 13.2 `licensing/license-definitions.yml`

This optional repository-local catalog adds license definitions not already supplied by the effective governance. It maps license ids to display metadata and canonical text files. Common Algites public licenses are normally defined centrally and do not need to be copied into each repository.

```yaml
Licenses:
  - Id: Apache-2.0
    Name: Apache License 2.0
    Url: https://www.apache.org/licenses/LICENSE-2.0
    Text: texts/Apache-2.0.txt
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
modustro-artifact-manifest.yml
```

The governed v1 structure is defined by `modustro-artifact-manifest_1.yamldef.schema.json`.

For every Java JAR produced by the project, including any sources JAR when present, the manifest is embedded at:

```text
META-INF/modustro/modustro-artifact-manifest.yml
```

For Python distributions, the same TechnologyKind-neutral manifest is embedded in both distribution forms:

- wheel: `<distribution>.dist-info/META-INF/modustro/modustro-artifact-manifest.yml`;
- source distribution: `META-INF/modustro/modustro-artifact-manifest.yml` below the source-distribution root directory.

The wheel location is intentionally scoped by the distribution's `.dist-info` directory so multiple installed Python distributions do not compete for one global `META-INF/modustro` path. Future TechnologyKind adapters that define another distributable package format SHOULD embed the same logical-artifact manifest in that package using a format-appropriate metadata location.

The manifest identifies the **logical Algites artifact**, not one TechnologyKind-specific representation. Therefore it intentionally does not contain `TechnologyKinds`, Java/Python/MPS-specific coordinates, build-tool details, or documentation-tool details. The same logical artifact manifest can be embedded in all TechnologyKind outputs of that artifact.

Conceptual v1 example:

```yaml
ManifestVersion: 1
Artifact:
  RepositoryId: pub.lib.Example
  LocalArtifactId: api.core
  ArtifactCoordinateId: pub.lib.Example_api.core
  GroupId: eu.algites.lib.example
  Version: 1.0-SNAPSHOT
  SourcePath: api/core
  StructureKind: artifact
  Name: Example Core API
  Description: Shared API of the example library.
SourceMetadata:
  DescriptorHierarchy:
    - StructureKind: repository
      Path: modustro-source-repository.yml
      Sha256: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
    - StructureKind: artifact_set
      Path: api/modustro-artifact-set.yml
      Sha256: 123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0
    - StructureKind: artifact
      Path: api/core/modustro-artifact.yml
      Sha256: 23456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef01
```

Each `Sha256` is calculated from the exact bytes of the corresponding source descriptor. The ordered `DescriptorHierarchy` therefore records which repository/artifact-set/artifact descriptors governed the artifact and allows those source descriptors to be independently verified.

The manifest is deliberately **cache-stable with respect to unrelated build context**. It MUST NOT contain values such as:

- generation/build timestamps;
- Git commit IDs, branch names, tags, or dirty-working-tree state;
- CI workflow/run/job IDs;
- runner or workstation identity;
- repository upload endpoint selection or credentials;
- other data that can change while the relevant artifact sources and descriptor hierarchy remain unchanged.

A change to a descriptor in the effective hierarchy changes its SHA-256 and therefore changes the manifest. A change only to unrelated CI/Git execution context does not.

Generated artifact documentation also publishes a copy named `modustro-artifact-manifest.yml` in the logical artifact publication directory and exposes the descriptor hierarchy and hashes in the artifact page header. Documentation-specific provenance such as source ref, Git commit, and generation time remains separate documentation metadata; it is not copied into the artifact manifest.

## 15. Common Gradle lifecycle

The exact task graph is TechnologyKind-dependent. These are the common entry points exposed by the shared Algites root build.

| Task | Purpose |
| --- | --- |
| `prepareDevelopment` | Generate effective development metadata needed by supported TechnologyKinds. |
| `refreshDevelopment` | Force regeneration of effective development metadata. |
| `modustroBuild` | Build all effective or explicitly selected TechnologyKinds. |
| `validateAlgitesPublicationReadiness` | Validate readiness of the selected publication closure. Normally invoked by publication. |
| `modustroPublish` | Publish all effective or selected TechnologyKinds. Publication credentials/overlays are normally supplied only by governed automation. |
| `resolveModustroRequiredCredentials` | Resolve enabled repository endpoints and required credential profile/type pairs. |
| `printModustroPublicationPlan` | Print effective deployment/repository configuration. |
| `printModustroArtifactModel` | Print discovered artifact metadata. |
| `resolveAllModustroArtifactDirectoryMetadata` | Resolve metadata for all discovered artifact directories. |
| `generateModustroDocsSite` | Generate the aggregate documentation site. |
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

The value contains decimal digits only. It is build execution metadata, not logical artifact metadata, so it is intentionally absent from `modustro-artifact-manifest.yml`.

When documentation is generated as part of that centralized snapshot worker run, the same snapshot-instance id is propagated into the documentation and the concrete Python package version is shown there. A standalone/manual documentation run has no concrete package build to identify; in that case the documentation explicitly reports that the Python package version is not tied to a concrete package build instead of inventing `*.dev0`.

### 16.3 Licensing validation

```text
-Palgites.licensing.validationMode=strict
-Palgites.licensing.validationMode=warn
```

`strict` is the default. `warn` is used only by lifecycle contexts that explicitly permit non-blocking licensing inconsistencies, such as current centralized snapshot processing.

### 16.4 Credential-preflight properties

```text
-Palgites.credential.usages=subscription,publication
-Palgites.credential.subscription.stabilities=release,snapshot
-Palgites.credential.publication.stabilities=snapshot
-Palgites.credential.output=/path/to/required-credentials.json
```

Environment equivalents are available as `ALGITES_CREDENTIAL_USAGES`, `ALGITES_CREDENTIAL_SUBSCRIPTION_STABILITIES`, `ALGITES_CREDENTIAL_PUBLICATION_STABILITIES`, `ALGITES_CREDENTIAL_OUTPUT_KINDS`, and `ALGITES_CREDENTIAL_OUTPUT`.

Normal developer builds normally need only `subscription` usage.

### 16.5 Documentation properties

Important documentation inputs include:

```text
-Pmodustro.docs.siteRoot=docs-site
-Pmodustro.docs.publicationKind=preview|snapshot|release
-Pmodustro.docs.publicationId=<id>
-Pmodustro.docs.sourceRef=<ref>
-Pmodustro.docs.sourceCommit=<commit>
-Pmodustro.docs.generatedAt=<UTC timestamp>
-Pmodustro.docs.repositoryHomeUrl=<url>
```

The documentation system may also be given script/adapter overrides such as `modustro.docs.baseScript`, `modustro.docs.javaScript`, `modustro.docs.pythonScript`, `modustro.docs.mpsScript`. These are infrastructure override points rather than normal artifact-author settings.

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
2. Add `modustro-artifact.yml`:

```yaml
Artifact:
  Name: Example API
  TechnologyKinds: [java]
```

3. Put production sources in `src/product/java` and development/test sources in `src/develop/java` according to the adapter conventions. The standard Java TechnologyKind adapter applies the required Gradle Java-library and Maven-publication plugins automatically; add an artifact-local `build.gradle.kts` only when genuinely custom Gradle behavior is required.
4. Run metadata/model and build checks.

### 18.2 Add a multi-technology artifact

```yaml
Artifact:
  Name: Shared definitions
  TechnologyKinds: [java, python]
```

A single logical artifact/version may produce technology-specific outputs. Shared neutral source can live in a SourceType such as `yamldefs`; adapters may generate language-specific packaging input under `*.gen`.

### 18.3 Temporarily block publication

```yaml
PublicationReadiness:
  Level: none
  Cause: |
    Artifact format migration is incomplete.
    Build and tests may continue, but nothing should be published yet.
  Author: Maintainer Name
```

Local build/test remains available. Publication fails if this artifact enters the controlled publication closure.

### 18.4 Permit snapshots but block release

```yaml
PublicationReadiness:
  Level: snapshot
  Cause: Public API is not release-stable yet.
  Author: Maintainer Name
```

### 18.5 Build only selected technologies

```bash
./gradlew -Palgites.technologyKinds=java modustroBuild
```

or:

```bash
ALGITES_TECHNOLOGY_KINDS=java,python ./gradlew modustroBuild
```

### 18.6 Inspect effective metadata

```bash
./gradlew printModustroArtifactModel
./gradlew resolveAllModustroArtifactDirectoryMetadata
./gradlew printModustroPublicationPlan
```

## 19. Common failure modes

### Unknown or missing TechnologyKind

Check `Artifact.TechnologyKinds` and make sure the shared infrastructure has an adapter for the declared value.

### Publication readiness blocks publish

Read the blocking declaration(s) printed by the task. The error identifies descriptor paths and includes `author` and `cause` when present. Fix the underlying reason and raise/remove the limiting declaration; there is intentionally no readiness override.

### Missing or unresolved TechnologyKind during credential preflight

If `resolveModustroRequiredCredentials` reports that no TechnologyKinds were resolved, verify that each distributable artifact uses the current `Artifact.TechnologyKinds` declaration. Legacy keys such as `Artifact.Type` are not a TechnologyKind declaration. An explicit TechnologyKind selection only narrows declared artifact technologies; it does not create missing declarations. An accidental empty resolution is rejected rather than expanded to all technologies.

### Missing repository credentials

Run `resolveModustroRequiredCredentials` for the intended context. Confirm that endpoint `CredentialProfile` ids match the universal credential document and that the selected profile contains the type required by non-secret repository metadata.

### Licensing materialization is stale

Run:

```bash
./gradlew rebuildAlgitesLicensing
```

Review and commit the generated `LICENSE`/`LICENSES/` changes.

### Python packaging tool not found

The selected Python interpreter must provide the tools used by the adapter, currently including `python -m build` for packaging and `python -m twine` for publication. Central publication workflows install these tools; local developers must provide them when invoking the corresponding tasks locally.

### Documentation contains stale technology output

Generate through `generateModustroDocsSite` or the standard documentation workflow. The documentation publication lifecycle clears/rebuilds the selected publication and should not be emulated by manually copying old generated directories.

## 20. Reference map

Use this guide for day-to-day authoring, then consult the source of truth when needed:

- structure, naming, inheritance, output model: `specs/Algites-Development-Structure-Specification.md`
- CI/release/lane/licensing lifecycle: `specs/Algites-Development-Lifecycle-Specification.md`
- exact YAML syntax: `devops/build/yamldefs/src/product/yamldefs/*.yamldef.schema.json`
- credentials: `devops/build/credentials/README.md`
- public repository defaults: `repository/defaults/README.md`
- licensing: `licensing/README.md`
- shared Gradle implementation: `gradle/tool/`
- reusable public workflows: `.github/workflows/`

The private DevOps operator guide is maintained separately in `priv.gov.Algites` because it documents private upload/manage governance, centralized publication workers, repository service lists, and operational credentials.
