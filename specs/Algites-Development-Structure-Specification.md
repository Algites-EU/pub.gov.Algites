[[FINALLY-APPROVED]]
# Algites Repository and Artifact Naming Standard
**Version:** 1.7
**Status:** Normative
**Scope:** All repositories and build artifacts within the Algites ecosystem

## 1. Introduction

---

## 2. Namings

### 2.1. Purpose

This document defines a unified and normative standard for naming:

- Git repositories,
- Maven/Gradle artifacts (JARs),
- and related identifiers

across the Algites platform, tools, libraries, products, and customer solutions.

The goals are:

- **Semantic clarity** – names must express domain and purpose.
- **Consistency** – the same concepts are named the same way everywhere.
- **Uniqueness** – artifacts must be identifiable without collisions.
- **Scalability** – the standard must support long-term growth.
- **Governance visibility** – public vs private scope must be immediately visible where needed.

---

### 2.2. Terminology

- **Visibility** – governance scope of a repository or artifact (`pub` or `priv`).
- **Role** – technical role of a repository or artifact (`pltf`, `app`, `lib`, `tool`, `frmw`, etc.).
- **BusinessName** – PascalCase domain or product/application concept (e.g., `Modustro`, `MyGreatProduct`).
- **RepoSubname** – optional lowercase technical qualifier of a repository (e.g., `common`, `core`, `backend`).
- **Module Path** – dot-separated identifier of a module or component within a repository.
  It consists of zero or more **module path folders** followed by a mandatory **module root folder**:
  `[<folders>.]<moduleroot>` (e.g., `mymodule.blfacadeintf`, `tools.profiler`, `build.parent`).
- **Module Root Folder** – the last segment of the module path that identifies the primary module name.
- **Variant** – a suffix identifying a specialized flavor of a module (e.g., `tests`).
- **Artifact** – a build output, typically a JAR published to a Maven repository.

---

### 2.3. Repository Naming

#### 2.3.1 Canonical Form

Repositories MUST be named using the following structure:

```
<vis>.<role>.<BusinessName>[.<reposubname>]
```

Where:

- `<vis>` = `pub` | `priv` (lowercase)
- `<role>` = technical role (lowercase)
- `<BusinessName>` = PascalCase business or domain name
- `<reposubname>` = optional lowercase technical qualifier

#### 2.3.2 Case Rules

- `vis` and `role` MUST be lowercase.
- `BusinessName` MUST be PascalCase.
- All `reposubname` components MUST be lowercase.

#### 2.3.3 Examples

```
pub.pltf.Modustro
priv.app.Modustro
priv.lib.Customers.common
pub.tool.Java
priv.tool.Java
```

---

### 2.4. Artifact Naming (artifactId)

#### 2.4.1 Canonical Form

Each artifactId MUST be composed of:

```
<vis>.<role>.<BusinessName[.reposubname]>_<module.path>[-<variant>]
```

Where:

- the part before `_` identifies the **repository root**,
- the part after `_` identifies the **module within the repository**,
- `<module.path>` follows the form `[<folders>.]<moduleroot>`,
- `-<variant>` is an OPTIONAL suffix identifying a variant of the module root (e.g., `tests`),
- `_` is a mandatory separator between repository identity and module path.

#### 2.4.2 Case Rules

- `<vis>`, `<role>`, `<reposubname>`, all module path folders, module root, and `<variant>` MUST be lowercase.
- `<BusinessName>` MUST preserve PascalCase from the repository name.
- The underscore `_` MUST be used exactly once in the artifactId.
- The variant suffix, if present, MUST be appended using `-`.

#### 2.4.3 Examples

From repository:

```
pub.pltf.Modustro
```

Artifacts:

```
pub.pltf.Modustro_core
pub.pltf.Modustro_languages
pub.pltf.Modustro_mps.plugin
```

From repository:

```
priv.lib.Customers.common
```

Artifacts:

```
priv.lib.Customers.common_aaa.blfacadeintf
priv.lib.Customers.common_aaa.blfacadeintf-tests
priv.lib.Customers.common_aaa.other
```

From repositories:

```
pub.tool.Java
priv.tool.Java
```

Artifacts:

```
pub.tool.Java_build.parent
pub.tool.Java_tools.profiler

priv.tool.Java_build.parent
priv.tool.Java_tools.profiler
```

---

### 2.5. groupId Naming

#### 2.5.1 Canonical Form

groupId MUST follow:

```
eu.algites.<role>.<businessname-lc>[.<reposubname>]
```

Where `<businessname-lc>` is the lowercase form of the BusinessName.

#### 2.5.2 Case Rules

All groupId components MUST be lowercase.

#### 2.5.3 Visibility Rule

Visibility (`pub` | `priv`) MUST NOT be part of the groupId.

The groupId expresses only the **business and technical domain**, not governance.

#### 2.5.4 Examples

```
eu.algites.pltf.modustro
eu.algites.app.modustro.studio
eu.algites.lib.customers
eu.algites.lib.customers.common
eu.algites.tool.java
```

---

### 2.6. Visibility and Artifact Identity

Visibility represents **governance and distribution scope**.

#### 2.6.1 Rules

Visibility MUST:

- be part of the **repository name**,
- be part of the **artifactId**,
- influence CI/CD and publishing targets.

Visibility MUST NOT:

- be part of the **groupId**.

#### 2.6.2 Rationale

Including visibility in the artifactId provides:

- immediate visual distinction in dependency trees and logs,
- early detection of accidental use of private artifacts in public builds,
- clear auditability of JAR files by name alone.

---

### 2.7. Variant Suffix Rules

#### 2.7.1 Purpose

Variants represent specialized flavors of a module root that are published as independent artifacts,
most notably shared test artifacts. It SHOULD be used whenever products that would otherwise be
distinguished only by a build classifier require their transitive dependencies to be tracked and
resolved by Maven/Gradle.

#### 2.7.2 Reserved Variants

The following variant suffixes are RESERVED:

- `tests` – shared tests for the base module root.

Additional variants (e.g., `it`, `bench`) MAY be introduced if documented.

#### 2.7.3 Terminal Rule

Artifacts with variant suffix `-tests` MUST be terminal:

- They MUST NOT define or publish further variants.
- In particular, `*-tests-tests` MUST NOT exist.

This prevents recursive test variants and aligns with Maven idioms where `-tests` denotes a test flavor.

#### 2.7.4 Rationale

The `-tests` suffix intentionally mirrors the common Maven convention
`<artifactId>-tests-<version>.jar` used for test classifiers, while elevating it to a
first-class artifact to enable transitive sharing of test dependencies.

---

### 2.8. Mapping Rules

#### 2.8.1 From Repository to Artifact Root

Given repository:

```
<vis>.<role>.<BusinessName>[.<reposubname>]
```

Artifact root becomes:

```
<vis>.<role>.<BusinessName>[.<reposubname>]
```

and the artifactId is completed by appending:

```
_<module.path>[-<variant>]
```

#### 2.8.2 From BusinessName to groupId

BusinessName is normalized to lowercase:

```
Modustro -> modustro
Customers -> customers
Java -> java
```

---

### 2.9. JAR Naming

The resulting JAR file name MUST follow Maven convention:

```
<artifactId>-<version>.jar
```

Examples:

```
pub.pltf.Modustro_core-1.2.0.jar
priv.lib.Customers.common_bai.blfacadeintf-1.0.0.jar
priv.lib.Customers.common_bai.blfacadeintf-tests-1.0.0.jar
pub.tool.Java_build.parent-1.4.0.jar
```

### 2.10. Generated Documentation Location

#### 2.10.1 Purpose

Generated documentation for repository artifacts SHOULD be placed under a dedicated documentation site root.

This standard defines only the logical directory structure of generated documentation. It does not prescribe a concrete hosting mechanism. The same structure may be published using GitHub Pages, an internal web server, an artifact repository, or any other static documentation hosting solution.

#### 2.10.2 Canonical Form

Generated documentation for an artifact SHOULD use the following structure:

```text
<docs-site-root>/generated/<module.path>/<documentation-channel>/
```

Where:

- `<docs-site-root>` is the repository-local root directory for generated documentation site content,
- `generated` is the root of generated documentation, which can be potentially removed and regenerated without impacting the manually created documentation. This folder should never contain manually created documentation.
- `<module.path>` is the same module path used in the artifactId after the mandatory `_` repository separator,
- `<documentation-channel>` identifies the published documentation view.

Recommended documentation channels are:

```text
latest/
snapshots/<snapshot-id>/
releases/<version>/
```

#### 2.10.3 Examples

For repository:

```text
pub.lib.Mps
```

and artifacts:

```text
pub.lib.Mps_lang.common.base
pub.lib.Mps_sol.common.base
```

the generated documentation may be placed as:

```text
docs-site/
  generated/
    lang.common.base/
      latest/
      snapshots/main/
      releases/0.1.0/

    sol.common.base/
      latest/
      snapshots/main/
      releases/0.1.0/
```

For repository:

```text
pub.tool.Java
```

and artifact:

```text
pub.tool.Java_build.parent
```

the generated documentation may be placed as:

```text
docs-site/
  generated/
    build.parent/
      latest/
      releases/1.4.0/
```

#### 2.10.4 Repository Prefix Rule

The repository identity prefix MUST NOT be repeated in the documentation path when the documentation is stored or published within the scope of the same repository.

Therefore, this is preferred:

```text
docs-site/generated/lang.common.base/latest/
```

over:

```text
docs-site/generated/pub.lib.Mps_lang.common.base/latest/
```

The full artifactId remains available as metadata inside the generated documentation.

#### 2.10.5 Module Path Reuse

The documentation path SHOULD reuse `<module.path>` rather than inventing a separate documentation identifier.

This keeps the relationship between repository structure, artifact identity, and generated documentation deterministic:

```text
artifactId = <repository-id>_<module.path>
docs path  = <docs-site-root>/<module.path>/
```

#### 2.10.6 Artifact Kind Disambiguation

If multiple artifacts in the same repository would otherwise have the same logical module name, the module path SHOULD include a disambiguating folder segment.

For example, MPS language and solution artifacts may use:

```text
lang.common.base
sol.common.base
```

instead of both using:

```text
common.base
```

This is not a technology hierarchy in the documentation site. It is part of the artifact module path and therefore part of the artifact naming model.

#### 2.10.7 Generated Indexes

A documentation site MAY generate repository-level index pages listing all documented artifacts.

Such index pages SHOULD display human-readable module names and MAY omit redundant repository identity from visible labels, while still preserving full artifact coordinates in metadata.

Example display label:
 `lang.common.base ` 
with metadata:
 `artifactId = pub.lib.Mps_lang.common.base groupId    = <derived-groupId> `
where:
- `<derived-groupId>` is the groupId derived according to section 2.5 using the repository naming rules and the applicable domain prefix.

---

### 2.11. Repository/Artifact Roles (Recommended Set)

Common roles include:

- `pltf` – platform - Algites platform components
- `app` – application - concrete applications
- `lib` – reusable libraries
- `tool` – build and development tools
- `frmw` - framework – generic frameworks
- `lab` – laboratory - experimental or laboratory work

New roles MAY be introduced but MUST be lowercase and documented.

---

### 2.12. CI/CD Governance

CI pipelines MUST:

- infer visibility from repository name prefix (`pub.` vs `priv.`),
- enforce that artifactId starts with the same visibility prefix and follows the `_` separator rule,
- enforce variant rules, including the terminal nature of `-tests`,
- publish artifacts to the corresponding Maven repository:
  - public repo for `pub.*`,
  - private repo for `priv.*`.

groupId MUST remain identical for public and private variants within the same domain.

---

### 2.13. Migration Rule

When migrating legacy projects:

- Preserve BusinessName and structure where possible.
- Align repository names to this standard.
- ArtifactId MUST be adapted to include visibility, the `_` separator,
  and, where applicable, the `-tests` variant suffix.

---

### 2.14. Rationale

This standard balances:

- **Domain identity** (PascalCase BusinessName),
- **Technical structure** (lowercase qualifiers),
- **Practical build compatibility** (lowercase groupId),
- **Governance clarity** (visibility in repository and artifactId),
- **Human auditability** (pub/priv visible in JAR names),
- **Structural readability** (explicit `_` separator between repo and module identity),
- **Maven idioms** (use of `-tests` for test variants),
- **Module semantics** (explicit distinction between module path folders and module root).

It follows proven historical patterns used in legacy conventions such as:

```
lib.Customers.common.aaa.blfacadeintf
```

while extending them with explicit visibility and repository/module separation semantics.

---

### 2.15. Summary

- Repositories express **visibility + role + business identity**.
- Artifacts express **visibility + role + business identity + module specialization**, separated by `_`.
- `<module.path>` is composed of optional path folders and a mandatory module root.
- Optional variant suffixes (e.g., `-tests`) express specialized flavors of the module root.
- `-tests` is terminal and MUST NOT be nested.
- groupId expresses **organizational and domain namespace only**.
- Visibility is part of artifact identity, but not of groupId.
- BusinessName remains PascalCase across repos and artifacts.
- The `_` separator cleanly delineates repository identity from module identity.

This standard is normative for all Algites projects.

---
[[/FINALLY-APPROVED]]

[[PROPOSAL]]

## 3. Algites Artifact and Dependency Model

This chapter defines the **model-first** structure used by Algites to describe artifacts, versioning, dependency intents, inheritance, and how these concepts are later **mapped** to build tools (Gradle/Maven). The intent is to keep the core model deterministic and transparent, with explicit resolution rules and diagnostics.

---

### 3.1. Goals and Design Principles

- **Deterministic resolution**: given the same model inputs, the resolved graph (versions, intents, outputs) must be identical.
- **Explicit inheritance paths**: distinguish *container inheritance* (contains/includes) from *parent inheritance* (policy chain).
- **Definition vs activation**: catalogs of templates/contexts may be declared broadly, but nothing is effective until activated in a concrete node context.
- **Controlled vs uncontrolled worlds**:
    - *Controlled* artifacts derive versions from a **ContainerVersionContext** (no versions written on references).
    - *Uncontrolled* artifacts carry version rules on dependency intents (ranges + preferred).
- **Early clarity, late validation**: model loading may be lazy; final validation must surface all unresolved references and contradictions.

---

### 3.2. Core Concepts and Terminology

We distinguish the artifact through **Artifact Class** which defines how the artifacts are treated in Algites source repository:
- **Controlled Artifacts** are artifacts that are controlled by the Algites rules and defined explicitly within the
  repository by custom configuration files (yaml/json).
- **Uncontrolled Artifacts** are artifacts that are not controlled by the given repository, so those are artifacts
  from other source repositories or completely other producers and sources.

In the case of the internal handling of the Artifacts, we recognize the following concepts:

- **Artifact**: a modeled buildable unit (module), producing one or more **outputs**.
- **ArtifactCoordinateId**: a stable identifier for an artifact in the Algites model (not necessarily identical to Maven GAV).
- **OutputType**: a specific output contract of an artifact (e.g., jar, parent-pom, bom, plugin-marker, etc.).
- **Output key (`outputKey`)**: `artifactCoordinateId + outputType` used to identify a dependency intent target.
- **Repository configuration**: the root of a repository model, treated as:
    - **root container** (container inheritance origin), and
    - **root parent** (parent inheritance origin).
- **Container edge** (contains/includes): expresses structural ownership/aggregation of artifacts.
- **Parent edge** (parent chain): expresses policy/build inheritance.
- **Dependency intent**: an abstract definition of a dependency with rules describing how it is used/exported.

---

### 3.3. Graph Model

#### 3.3.1 Container Graph (contains/includes)

The **container graph** captures “what is inside what” (repository → containers → artifacts). It is used to inherit:
- **ContainerVersionContext** (for controlled versioning),
- repository-/container-provided catalogs (e.g., rule template sets) when allowed.

Typical operations:
- expand containers into all contained artifacts,
- compute effective container context for each artifact,
- provide a deterministic traversal order for resolution.

#### 3.3.2 Parent Chain (policy inheritance)

The **parent chain** captures “policy inheritance” relationships.
- It is the inheritance channel for **ParentDependencyIntentSets** (baseline dependency intents).
- It can also participate in catalog propagation (e.g., rule template set catalogs), subject to merge rules.

Typical operations:
- for each artifact, compute its effective parent chain (repo-root parent included),
- merge inherited policies deterministically (weight + conflict rules).

#### 3.3.3 Combined Graph Semantics and Validity

Both graphs exist simultaneously and must remain valid:
- **No cycles** in parent chain (a parent cycle is a structural error).
- Container cycles should be forbidden as well (a container loop breaks inheritance).
- A node may be a container and also a parent; this is explicitly supported.
- When both container and parent contribute catalogs, merges must be:
    - **id-based** and deterministic,
    - **conflict-failing** when definitions disagree.

---

### 3.4. Versioning Model

#### 3.4.1 ContainerVersionContext

A **ContainerVersionContext** defines how controlled artifacts acquire versions.
- It can be **defined** at repo or artifact scope (definition layer).
- It becomes effective only when **activated** in a node context (activation layer).

Inheritance rule (normative for v1):
- ContainerVersionContext **inherits only via container edges** (repo → container → contained artifacts).
- Parent chain **does not** change controlled versions.

#### 3.4.2 Controlled vs Uncontrolled Version Resolution

- **Controlled artifacts**
    - Do not specify versions in dependency references.
    - Resolve versions via **effective ContainerVersionContext**.
- **Uncontrolled artifacts**
    - Use version rules attached to dependency intents:
        - `ranges[]` (allowed version set, potentially disjoint),
        - optional `preferred` (concrete choice inside the allowed set),
        - conflict resolution via weight and final validation policy.

Version selection must remain deterministic without querying remote metadata (v1):
- If `preferred` exists and lies in the final allowed set → choose it.
- If no valid preferred exists → the version is unresolved and handled by validation policy.

#### 3.4.3 Diagnostics (source tracing)

For every resolved artifact and dependency intent, the system should be able to report:
- which node provided the effective ContainerVersionContext (repo/container path),
- which rule(s) contributed to the allowed version set,
- which preferred version (if any) won by weight,
- and why a conflict occurred (tie, incompatible ranges, missing preferred under strict mode).

---

### 3.5. Dependency Intent Model

#### 3.5.1 DependencyIntentRule Templates (no versions)

A **DependencyIntentRuleTemplate** is a reusable “shape” (preset) describing dependency behavior **without versioning**.
Examples include:
- Maven-like compile/runtime usage shapes,
- Gradle-like configurations such as `api`, `implementation`, `compileOnly`, `runtimeOnly`,
- test-scoped shapes (develop/test sourceset).

Templates typically configure:
- `exportAs` rule (none/intent/runtime/compile),
- `importAs` rule (none/intent/runtime/compile),
- `applyOnSources` applicability (main/product | test/develop | both),
- `usageSet` set (e.g., classpath item, source processor, build tool, ....).

Each template or rule may define a `weight` (default 0).

#### 3.5.2 DependencyIntentRuleTemplateSets (catalog + merge rules)

A **DependencyIntentRuleTemplateSet** groups multiple rule templates (inline or referenced) under a unique identifier.
- It may live at repo scope, container scope, or parent scope.
- It is part of the **definition layer** (catalog).

Merge semantics:
- If a `templateSetId` appears multiple times:
    - identical definition → OK,
    - different definition → **error** (forces explicit resolution via id change or governance rule).

#### 3.5.3 DependencyIntentTemplateSets (outputKey = artifactCoordinateId + outputType)

A **DependencyIntentTemplateSet** defines *concrete dependency intents* by `outputKey = artifactCoordinateId + outputType`, each intent referencing:
- one or more **rule template sets** (e.g., `mavenCompile`, `gradleCompileOnly`),
- optional inline **granular rules** (non-version),
- optional **uncontrolled version rules** (`ranges[]`, `preferred`) that apply only to uncontrolled dependencies.

Important: the set itself is still a **template** until activated by an artifact context.

#### 3.5.4 Activation Layer (what is actually applied)

An artifact context decides:
- which ContainerVersionContext is active (if any override),
- which rule template catalogs are available,
- which dependency intent template sets are activated to form the effective dependency set.

Inheritance (v1 intent):
- catalogs (template sets) may merge from repo + container + parent, with id rules,
- **effective dependency intents** (baseline dependencies) flow primarily via parent chain,
- repo-root may inject baseline intents as “virtual parent defaults”.

#### Note: “Optional” and product variability are not modeled as an intent rule

Algites does **not** model Maven’s `<optional>` flag as a first-class dependency intent property.
The Maven optional flag is primarily a *transitive propagation hint* in published metadata
and does not reliably express product availability, runtime presence, or variant semantics.

Instead, Algites models variability and “optional parts” explicitly using **DependencyIntentSets**
and **intent-only exports** (e.g., PIBOM/PVBOM-style outputs). These constructs can express 
“available but not required” capabilities deterministically: they define a catalog of selectable intents,
and consumers decide what to activate in their own contexts.

---

### 3.6. Inheritance and Merge Semantics

#### 3.6.1 What inherits from Container vs Parent

Normative v1 rules:

- **Container inheritance (contains/includes)**
    - ContainerVersionContext activation and overrides,
    - optionally: rule template set catalogs (subject to merge rules).

- **Parent inheritance**
    - ParentDependencyIntentSets (baseline dependency intents),
    - optionally: rule template set catalogs (subject to merge rules),
    - build/publishing conventions that should behave “like parent policy”.

- **Repository config**
    - acts as root container and root parent for everything in the repo.

#### 3.6.2 Conflict Resolution

General strategy:
- scalar fields resolve by `weight` (higher wins),
- equal weight + differing value → **error** (forces explicit user intent),
- set-like fields either:
    - union (additive), or
    - replace-by-weight (if explicitly chosen by policy).

Version rules for uncontrolled:
- allowed set = intersection of allowed sets from all applicable rules (each allowed set may be union of intervals),
- preferred chosen by highest weight among preferred candidates that lie in the final allowed set.

#### 3.6.3 Lazy Loading and Final Validation

- The model may load catalogs and references without immediate existence checks.
- A final validation phase must:
    - ensure all referenced template sets exist,
    - ensure outputKeys can be interpreted (at least structurally),
    - ensure no unresolved versions remain under strict publish policies,
    - ensure no rule collisions remain (id collisions, weight ties, incompatible ranges).

---

### 3.7. Outputs and Publication Contracts

Artifacts may produce multiple outputs. An **OutputType** identifies *which* output contract is being referenced (as a dependency target) or published (as an artifact output).

In Algites, an OutputType is **not** a free-form string. It is a **data object** composed of:
- a **builtin output kind** (stable enum), and
- an optional **custom UID** (only when builtin kind is `CUSTOM`).

This makes output typing deterministic, tool-agnostic, and safely extensible.

---

#### 3.7.1 Artifact Output Class Data Model

**OutputType fields**
- `builtinOutputKind: AInArtifactBuiltinOutputKind`
- `customUid: String?` (required iff `builtinOutputKind == CUSTOM`)

**Canonical OutputType identifier (`outputTypeUid`)**
- If `builtinOutputKind != CUSTOM`  
  → `outputTypeUid = builtinOutputKind.uid`
- If `builtinOutputKind == CUSTOM`  
  → `outputTypeUid = customUid` (must be non-empty and validated)

**Recommended UID namespace convention**
- Builtin kinds: `builtin:<kind>` (e.g., `builtin:jar`, `builtin:bom`)
- Custom kinds: `custom:<namespaced-id>` (e.g., `custom:eu.algites.output.pibom.v1`)

The exact string format is governed by validation rules; the key requirement is global uniqueness and long-term stability.

---

#### 3.7.2 Builtin Output Kinds

`AInArtifactBuiltinOutputKind` is a stable enum defining the output kinds the system understands natively.
Each enum item MUST have:
- `uid: String` — stable identifier used for comparisons and persistence

Typical builtin kinds (illustrative, not exhaustive, builtin types use the unique id starting with "builtin:"
and the identification is interpreted as complete uid, the custom types must have the uid starting with "custom:"):

- `MAIN_JAVA_CLASSES_JAR` (`builtin:jar`) — binary library artifact
- `MAIN_JAVA_SOURCE_JAR` (`builtin:sourcesJar`) — sources artifact
- `MAIN_JAVA_DOC_JAR` (`builtin:javadocJar`) — javadoc artifact
- `MAIN_MAVEN_POM` (`builtin:mavenPom`) — standard module POM publication
 
to be later discussed if something like this is reasonable, probably not::
- `PARENT_POM` (`builtin:parentPom`) — parent-style POM (inheritance contract)
- `BOM` (`builtin:bom`) — dependency steering output (constraints/catalog)
- `PLUGIN_MARKER` (`builtin:pluginMarker`) — Gradle plugin marker publication
- `CUSTOM` (`custom:`) — extension point for non-builtin output kinds; the value is used 
                         as the UID prefix for custom identifications



Notes:
- Builtin kinds are intended to be *minimal but sufficient* for Algites v1.
- Adding new builtin kinds is a compatibility-sensitive change; prefer `CUSTOM` when possible.

---

#### 3.7.3 Custom Output Kinds

Custom output kinds are represented by:
- `builtinOutputKind = CUSTOM`
- `customUid = <non-empty, globally unique UID>`

Custom UIDs MUST be:
- globally unique within the model space
- stable across time (do not rename once published)
- validation-safe (restricted character set / pattern)

Custom outputs are especially useful for:
- project-specific BOM variants (e.g., PIBOM/PVBOM-style outputs)
- tool metadata outputs not covered by builtins
- transitional outputs during migrations

---

#### 3.7.4 OutputType Interface Contract

If implemented as an interface, the minimal contract is:

- `AIiArtifactOutputType.getBuiltinOutputKind(): AInArtifactBuiltinOutputKind`
- `AIiArtifactOutputType.getOutputTypeUid(): String`

Normative behavior:
- `getOutputTypeUid()` MUST return:
    - the builtin UID when builtin kind is not `CUSTOM`, or
    - the custom UID when builtin kind is `CUSTOM`

Implementations MUST NOT invent additional derivation rules beyond the above.

---

#### 3.7.5 How OutputType Is Used in Dependencies

Dependency intents reference outputs via the **dependency key**:

- `outputKey = artifactCoordinateId + outputType`

This enables a dependency to target *specific* contracts, for example:
- a `PARENT_POM` output as the parent-like inheritance contract
- a `BOM` output as dependency steering (constraints) input
- a `JAR` output for normal library consumption

Normative rule:
- When an intent must distinguish contracts, it MUST specify the correct `outputType` rather than using implicit defaults.

---

#### 3.7.6 Publication Contracts and Deterministic Mapping

Publication configuration MUST map OutputTypes deterministically to build-tool publication mechanisms.

Examples (tool mapping is defined in section 3.8):
- `JAR` → Maven/Gradle main publication artifact (binary)
- `SOURCES_JAR`, `JAVADOC_JAR` → classifier artifacts / additional publications
- `MAVEN_POM`, `PARENT_POM` → POM publications with specific semantics
- `BOM` → Gradle platform/constraints and/or Maven dependencyManagement/BOM import
- `PLUGIN_MARKER` → Gradle plugin marker publication

Custom outputs:
- MUST define their mapping policy explicitly (by build policy / mapping layer)
- SHOULD fail fast if no mapping exists for the target build tool/output format

---

#### 3.7.7 Validation Rules (Normative)

1. If `builtinOutputKind != CUSTOM`
    - `customUid` MUST be absent/empty.
2. If `builtinOutputKind == CUSTOM`
    - `customUid` MUST be present and non-empty.
3. `outputTypeUid` MUST be stable and globally unique within the model.
4. Any dependency intent that references an OutputType MUST reference a resolvable output kind for the target artifact.
5. Mapping MUST fail fast if an output kind cannot be mapped to the selected build tool or publication mode (unless a policy explicitly allows ignoring it).

---

# 3.7.8 Export Intent Normalization and Transitive Stability

This section defines **default normalization** and **validation rules** for the `exportUse` behavior flags, with the goal of ensuring **stable transitive dependency semantics** and preventing accidental **version divergence** across the dependency graph.

## Motivation

In mainstream build tools (Maven/Gradle), publishing a dependency as part of a module’s API or runtime surface typically implies that its **transitive graph participates** in consumer resolution. In the Algites model this is expressed via `exportUse.intent` (dependency steering/participation in exported intent).

If a dependency were exported to consumers at `compile` or `runtime` **without** exporting its intent, downstream modules could silently re-introduce the same artifact with a different version, increasing the chance of conflicting or non-deterministic outcomes.

Therefore, Algites defines a safe default:

- **If a module exports a dependency for consumer compile/runtime, the dependency’s intent is exported as well**, unless the build policy explicitly permits otherwise (advanced/escape hatch).

## Normative Rules

### R1. Implicit intent export when exporting compile/runtime

For any resolved dependency application where:

- `exportUse.compile == true` **or** `exportUse.runtime == true`

then the effective value must satisfy:

- `exportUse.intent == true`

unless an explicit override is permitted by the active Build Policy (see R3).

> In other words: **compile/runtime export implies intent export** by default.

### R2. Steering-only export remains valid

A dependency may be exported as intent only:

- `exportUse.intent == true`
- `exportUse.compile == false`
- `exportUse.runtime == false`

This represents **dependency steering without classpath exposure** (e.g., generating/merging constraint outputs or variant BOMs without forcing the dependency onto consumer classpaths).

### R3. Explicit intent disablement is restricted

For `usage = classpathItem` (the default classpath channel), the following combination is **invalid** by default:

- (`exportUse.compile == true` or `exportUse.runtime == true`) **and** `exportUse.intent == false`

Rationale: it enables untracked re-introduction of the same artifact with a different version downstream, reducing determinism.

A Build Policy may optionally allow this combination as an advanced escape hatch; if allowed, it should additionally require one or more of:

- **explicit locking** of the application (to prevent downstream overrides),
- **explicit conflict strategy** (deterministic choice rules),
- or **explicit version constraints** at the same or higher weight layer.

### R4. Behavior composition does not require explicit intent in data

Because the model supports inheritance (unspecified values are inherited, otherwise default to `false`), the default intent implication in R1 may be applied during **normalization**:

- If `exportUse.intent` is unspecified after merging layers, it is treated as `false` initially.
- Then R1 is applied to produce an **effective** `exportUse.intent` value.

This keeps authoring concise while preserving deterministic behavior.

## Guidance

- Prefer **R1 defaults** for all classpath-exposed exports (compile/runtime).
- Use **R2 steering-only** exports for “variant/BOM-style” guidance where the dependency should not automatically appear on consumer classpaths.
- Only consider violating R3 under an explicit Build Policy designed to preserve determinism.

---

### 3.8. Mapping to Build Tools

#### 3.8.1 Gradle mapping

- Intent rule templates map to Gradle configurations:
    - `api`, `implementation`, `compileOnly`, `runtimeOnly`,
    - test variants: `testImplementation`, `testRuntimeOnly`, etc.
- Dependency steering maps to:
    - `platform(...)` / `enforcedPlatform(...)`,
    - Gradle constraints and dependency locking strategies (when used).

#### 3.8.2 Maven mapping

- Intent rules map to Maven scopes:
    - compile/runtime/provided/test (and/or plugin-related constructs),
- Dependency steering maps to `dependencyManagement` (including BOM imports),
- Parent chain mapping (v1): internal-only; external parent import is deferred.

#### Maven `<optional>`: mapping-only, not a semantic source of truth

Maven’s `<optional>true</optional>` is treated as a **mapping artifact** only. In Maven, `optional`
mainly means: “do not propagate this dependency transitively to downstream consumers.” 
It does **not** define a product variant, does not guarantee runtime availability, 
and does not express deterministic “feature selection”.

Therefore, Algites does not use Maven optional as the semantic basis for “optional components”. 
Instead, optionality/variability is represented via:
- **DependencyIntentSets** (explicit activation sets), and
- **intent-only exports** (PIBOM/PVBOM-style outputs) that provide 
- a selectable catalog of intents.

If Maven optional emission is needed for compatibility with Maven consumers, it may be generated
as a **best-effort metadata hint** by the Maven mapping layer. Such emission must not change
the Algites semantic model and must not be relied upon as the primary expression of product composition.

Normative rule (v1): Algites MUST NOT require Maven `<optional>` to preserve correctness 
of the resolved model; optionality MUST be expressed via intent sets / intent-only exports,
not via Maven optional.

#### 3.8.3 Known semantic mismatches

- Gradle distinguishes API exposure vs internal usage more explicitly (`api` vs `implementation`).
- Maven’s scope system is coarser; mapping requires careful interpretation of “export”.
- Source processing (annotation processors / kapt) has tool-specific behavior.

Algites mapping must be documented per OutputType and intent rule preset.

---

### 3.9. Repository Structure and Build Integration

Practical repository expectations (v1):
- Repository config provides:
    - default ContainerVersionContext (if the repo is controlled),
    - baseline dependency intents (if desired),
    - catalogs of rule templates and dependency intent templates.

Build integration:
- settings.gradle.kts: project structure and inclusion rules.
- build.gradle.kts: build behavior, publishing definitions, and mapping of model resolution to tool configuration.
- CI: supplies publishing targets (download/upload endpoints) via environment/properties consistent with Algites conventions.

---

### 3.10. Artifact Roles

This chapter defines the logical classification of artifacts used within the Algites ecosystem.
Artifact types are **cummulateable**; each type has a clearly defined role, but one artifact can play multiple roles at the same time
The distinction between artifact roles is fundamental for general better organization of large projects.

#### 3.10.1 General Artifact Classification

All Algites repositories and projects contain controlled artifacts (in every repository) of the following high-level roles:

- **Core Artifact**
- **Aggregator Artifact**
- **Policy Artifact**
- **BOM Artifact**

Each artifact role:
- has a distinct purpose,
- participates differently in dependency resolution and build execution,
- and is subject to different governance and lifecycle rules.
- but one artifact can play more roles at the same time.

Artifact role can be identifiable by its structure and declared role, not only by naming conventions alone.

```mermaid
flowchart TD
    A[Artifact] --> P[Production Artifact]
    A --> G[Aggregator Artifact]
    A --> L[Policy Artifact]
```

---

#### 3.10.2 Core Artifacts

Core artifacts represent deliverables that contain executable or consumable functionality.

##### 3.10.2.1 Definition

A Production Artifact is an artifact that:
- contains source code and/or resources,
- produces a runtime or compile-time deliverable (e.g. JAR),
- is intended to be consumed by other artifacts or applications.

##### 3.10.2.2 Characteristics

Production artifacts:
- usually does NOT define concrete dependency versions directly.
- it usually does rely on inherited or imported version policy.

##### 3.10.2.3 Maven Representation

In Maven, a Production Artifact:
- uses a policy parent POM or imports a policy BOM,
- declares dependencies without hardcoded versions,
- since the Maven is not the supported Algites build system, the artifact is
  to Maven related as the maven compatible output only.

##### 3.10.2.4 Gradle Representation

In Gradle, a Core Artifact:
- applies a policy Gradle plugin,
- declares dependencies without versions,
- MUST NOT read version definition files directly.

```mermaid
flowchart LR
    PP[Policy Parent / BOM] --> PA[Production Artifact]
    PP -->|version rules| PA
```

---

#### 3.10.3 Aggregator Artifacts

Aggregator artifacts provide structural grouping and coordinated build execution.

##### 3.10.3.1 Definition

An Aggregator Artifact is an artifact that:
- exists to group multiple artifacts,
- it usually does not represent a deliverable on its own,
- orchestrates build execution across its children.
- the aggregator artifact can be identical with the policy artifact and fulfill both roles at the same

##### 3.10.3.2 Characteristics

Aggregator artifacts:
- usually contain no source code.
- can define dependency versions.
- it usually does not introduce a build policy.
- it usually is not used as dependency targets.

##### 3.10.3.3 Maven Representation

In Maven, an Aggregator Artifact:
- has `packaging = pom`,
- lists child modules via `<modules>`,
- is not usually intended to be referenced as a parent for policy purposes,munless plays the policy artifact role.

##### 3.10.3.4 Gradle Representation

In Gradle, an Aggregator Artifact:
- is represented by a composite build or multi-project root,
- declares included projects in `settings.gradle(.kts)`,
- delegates build logic to included modules.

```mermaid
flowchart TD
    AGG[Aggregator] --> M1[Module A]
    AGG --> M2[Module B]
    AGG --> M3[Module C]
```

---

#### 3.10.4 Policy Artifacts

Policy artifacts define shared structural, versioning, and build rules.

##### 3.10.4.1 Definition

A Policy Artifact is an artifact that:
- defines version and/or build policy,
- usually produces no runtime functionality,
- it usually serves as a governance contract for dependent artifacts.

Policy artifacts are **a highly recommended place** where version definitions are allowed.
The policy artifact can be identical with the aggregator artifact and fulfill both roles

##### 3.10.4.2 Responsibilities

Policy artifacts:
- define dependency versions symbolically,
- define plugin versions symbolically,
- provide enforcement and validation rules,
- ensure consistency across the artifact graph.
- can produce automatically a corresponding policy background BOM artifact 
  (see the builtin artifact output types)

##### 3.10.4.3 Maven Representation

In Maven, a Policy Artifact plays the role of:
- a **policy parent POM** (for inheritance),
- as well as **policy BOM** (for import-based usage).

##### 3.10.4.4 Gradle Representation

In Gradle, a Policy Artifact produces:
- a **policy Gradle plugin**.

The policy plugin:
- applies dependency constraints or platforms,
- injects shared repositories and build conventions,
- enforces version and structural rules.

```mermaid
flowchart LR
    PDA[Policy Definition Artifact]
    PDA --> MP[Maven Policy Parent POM & BOM]
    PDA --> GP[Gradle Policy Plugin]
```

---

#### 3.10.5 BOM Artifacts

BOM artifacts (Bill Of Materials) are **POM-packaged** artifacts whose primary purpose is to provide **version alignment** to consumers. They are published to the Maven repository alongside production artifacts and may be consumed by both Maven and Gradle builds.

This section standardizes Algites BOM terminology, responsibilities, generation rules, and usage patterns. It also clarifies why different BOM categories exist and what problems each category solves.

> **Key principle:** A “BOM” is not one thing. In Algites we distinguish three BOM categories with different semantics and lifecycle:
>
> - **Policy Background BOM (PBBOM)** — generated, hierarchical, background version alignment for *framework-independent* external components forced by the product policy.
> - **Product Interface BOM (PIBOM)** — curated, library/product catalog of the product’s own modules.
> - **Product Variant BOM (PVBOM)** — curated, opinionated platform/stack BOM combining multiple options into one specific product variant.

---

##### 3.10.5.1 Definitions and scope

###### 3.10.5.1.1 What a BOM is (in Algites)

A BOM is an artifact usually with:

- `packaging = pom`
- a `dependencyManagement` section listing dependency coordinates with pinned versions
- **no compiled code**
- intended consumption via Maven **import scope** (or equivalent mechanisms in Gradle)

A BOM **should not add dependencies** by itself. It only defines versions to be used *if* the consumer declares the dependency without a version.

###### 3.10.5.1.2 What a BOM is not

A BOM is **not**:

- a build convention carrier (that is the role of a parent POM)
- a plugin configuration carrier (pluginManagement belongs to parent POMs)
- a “complete build setup” (CI/build tooling is handled elsewhere)
- a replacement for product documentation or curated compatibility matrices (except where explicitly modeled as PVBOM)
- hence, in the new artifact concept the BOM role can be unified with oter roles, if necessary, the users are free to choose the project structure they find useful.

###### 3.10.5.1.3 Maven compatibility boundary

Maven semantics impose important constraints:

- `dependencyManagement` is applied to dependencies declared in the consuming project.
- `<scope>test</scope>` dependencies are **not transitive** and should not be relied on for sharing test dependencies.
- Version alignment is a **consumer-side** mechanism: consumers can still override versions explicitly (which may break compatibility). BOMs reduce risk; they do not technically prevent misuse.

---

##### 3.10.5.2 BOM categories (Algites standard)

###### 3.10.5.2.1 Policy Background BOM (PBBOM)

#### Purpose

The **Policy Background BOM (PBBOM)** provides a **shared version background** for components that must remain *framework-independent* (e.g., plugins, adapters, generic interfaces), yet must be developed against a consistent ecosystem background to avoid classpath conflicts when later integrated into a product/framework runtime.

Typical use-case:

- A component does **not** depend on Spring (or any specific framework) at compile time.
- The component will later be loaded/used within a Spring-based application (or another framework runtime).
- To avoid runtime conflicts (logging, annotations, bytecode libs, Jakarta/Javax split, etc.), the component is built against the same **background alignment** as the target platform.

#### Characteristics

- **Generated automatically** from a Artifact fultilling the policy role.
- **Hierarchical** (imports parent PBBs) to keep background alignment consistent across inherited policies.
- Contains only **background** dependencies (no “product module catalog”).
- Intended primarily for **component developers** and integration layers.

#### Hierarchy rule (normative)

If policy `Y` inherits from policy `X`, then:

- `PBBOM-Y` **MUST import** `PBBOM-X` (exact aligned version), and
- `PBBOM-Y` **MAY add** additional background constraints specific to policy `Y`.

This creates a deterministic, layered background alignment while keeping the policy inheritance semantics explicit.

#### Maven usage example

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>eu.algites.policy</groupId>
      <artifactId>algites-pbbom-y</artifactId>
      <version>Y_VERSION</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

#### Gradle usage example

In Gradle, you typically consume the corresponding alignment through a version catalog or a platform import. When publishing to Maven repos, Gradle projects can still import BOMs via dependency constraints (platform).

```kotlin
dependencies {
  implementation(platform("eu.algites.policy:algites-pbbom-y:Y_VERSION"))
}
```

#### What belongs into PBBOM (guideline)

Include “background” libraries that commonly cause classpath/version conflicts across ecosystems, for example:

- logging APIs and bridges
- annotations libraries
- bytecode / proxy libs
- jakarta/jaxb/jaxrs stacks (where relevant)
- JSON core libs (if used broadly as infrastructure)
- common utility libraries that should be aligned across the runtime

Exclude:

- product module catalogs (those belong to PIBOM)
- opinionated application stacks (those belong to PVBOM)

---

###### 3.10.5.2.2 Product Interface BOM (PIBOM)

#### Purpose

The **Product Interface BOM (PIBOM)** is the **product/library curated catalog BOM**. It enumerates the product’s own published modules so that consumers can declare them without specifying versions, while remaining neutral about external stacks.

Example: A Spring Framework-style BOM enumerating Spring modules.

#### Characteristics

- **Curated manually** (or semi-automated with curated inputs).
- Enumerates **product artifacts** (the modules that the product provides).
- Typically **non-hierarchical** as a BOM (no implicit inheritance). It may be versioned as part of the product release.
- Does not attempt to define a full application stack; it stays within the product boundary.

#### Why PIBOM is not auto-generated from Policy Artifacts

A Policy Artifact typically defines version decisions and generation parameters. PIBOM needs an explicit, curated decision of:

- which artifacts are considered “public product modules”
- which modules are stable to advertise to consumers
- which optional modules are included/excluded

This is product ownership, not policy ownership.

#### Maven usage example

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>eu.algites.product</groupId>
      <artifactId>product-pibom</artifactId>
      <version>PRODUCT_VERSION</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

#### Product dependency declaration example

After importing PIBOM, consumers can declare product modules without versions:

```xml
<dependency>
  <groupId>eu.algites.product</groupId>
  <artifactId>product-core</artifactId>
</dependency>
```

#### What belongs into PIBOM (guideline)

Include:

- all public modules of the product that are intended for direct consumption
- versions for these modules (aligned to the product release)

Exclude:

- unrelated ecosystem stacks (Hibernate/JOOQ/etc.) unless they are product modules
- build plugins, plugin configuration, profiles (that belongs to parent POM)
- transitive “background alignment” beyond what is needed for the product’s own modules (that belongs to PBBOM/PVBOM depending on intent)

---

###### 3.10.5.2.3 Product Variant BOM (PVBOM)

#### Purpose

The **Product Variant BOM (PVBOM)** defines an **opinionated, compatible platform stack assembling one specific product variant**. It is a “ready-to-use” variant that combines multiple products/frameworks into a coherent set of versions.

Example: Spring Boot style BOM that aligns Spring + Hibernate + Jackson + embedded container + logging stack.

#### Characteristics

- **Curated manually** (product/platform decision).
- May **import multiple BOMs** (PIBOM/PBBOM/other third-party BOMs) or may materialize constraints directly.
- Intended for application teams who want a “known-good stack” with minimal version decision overhead.

#### Why PVBOM is not auto-generated from Policy Artifacts

A PVBOM is a policy decision plus a product/platform decision:

- It selects a variant (e.g., Hibernate vs iBatis vs jOOQ)
- It defines compatibility expectations across ecosystems
- It becomes a published “platform contract”

This needs explicit ownership and review; automatic generation from a generic policy file is insufficient.

#### Maven usage example

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>eu.algites.platform</groupId>
      <artifactId>platform-pvbom-hibernate</artifactId>
      <version>PLATFORM_VERSION</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

#### Variant example

Different variants are different PVBs:

- `platform-pvbom-hibernate`
- `platform-pvbom-ibatis`
- reminding that variants are a deliberate contract and must be versioned and communicated.

---

##### 3.10.5.3 Relationship to Parent POMs

###### 3.10.5.3.1 Parent POM role

Parent POMs carry build and project conventions:

- pluginManagement
- properties (often including versions)
- profiles and build defaults
- organization-level metadata

They are applied via Maven’s `<parent>` mechanism (inheritance). Only one parent can be used directly, which is why BOM import exists.

###### 3.10.5.3.2 Why BOM and parent POM can share dependencyManagement

It is common that a parent POM and a BOM contain **similar** `dependencyManagement`. However, the artifacts serve different consumers:

- Parent POM: internal project inheritance (build conventions)
- BOM: external or flexible consumption (version alignment without inheritance)

Algites allows generating both where appropriate, but the category determines intended usage:

- PBBOM: generated background alignment (BOM import)
- PIBOM: curated product module catalog
- PVBOM: curated platform stack

###### 3.10.5.3.3 Importing a parent POM as a BOM

Maven permits importing any POM via `<scope>import</scope>` and will effectively use its `dependencyManagement`. While this may work mechanically, Algites usually treats this as a **non-standard** and **discouraged** practice for published contracts, because it couples version alignment to an artifact whose primary role is build convention.

**Normative recommendation:** publish a BOM (PBBOM/PIBOM/PVBOM) for version alignment use-cases, and keep parent POMs for inheritance.

(Internal exceptions may exist for purely private builds, but should be documented explicitly as such.)

---

##### 3.10.5.4 Generation rules

###### 3.10.5.4.1 Policy Background BOM generation (PBBOM)

- PBBOM artifacts **MUST** be generated from Policy Artifacts.
- PBBOM generation is deterministic:
    - inputs: `algites-artifact.properties`
    - outputs: `*-pbbom.pom` (and optionally metadata)
- If policy `Y` inherits from policy `X`:
    - `PBBOM-Y` **MUST import** `PBBOM-X`.

###### 3.10.5.4.2 PIBOM and PVBOM creation

- PIBOM and PVBOM artifacts are **manual/curated** by design.
- Automation may assist (e.g., scanning published modules), but the final curated list and variant definition is owned by the product/platform maintainers.

---

##### 3.10.5.5 Versioning and compatibility expectations

###### 3.10.5.5.1 Version alignment intent

- BOM version indicates a compatibility set.
- If a consumer overrides versions (explicit `<version>`), compatibility may be broken.
- Policy aims to reduce risk, not enforce it at runtime.

###### 3.10.5.5.2 When to bump BOM versions

- PBBOM: whenever background constraints change (including upgrades of background libraries)
- PIBOM: aligned with product releases and public module changes
- PVBOM: aligned with platform releases and variant changes

---

##### 3.10.5.6 Usage guidelines (Do / Don’t)

###### 3.10.5.6.1 Do

- Use **PBBOM** when developing framework-independent components that must remain compatible with a target runtime background.
- Use **PIBOM** when consuming a product/library’s modules without pinning per-module versions.
- Use **PVBOM** when you want a complete, opinionated platform stack with minimal version decisions.

###### 3.10.5.6.2 Don’t

- Don’t treat PIBOM as a platform stack BOM (keep it product-scoped).
- Don’t overload PBBOM with product module catalogs.
- Don’t rely on transitive test dependencies; if shared tests are needed, publish explicit `*-tests` artifacts.

---

##### 3.10.5.7 Practical notes for Gradle-only builds with Maven consumers

###### 3.10.5.7.1 Publishing to Maven repositories

When building with Gradle as the single authoritative build tool:

- Gradle publishes standard Maven artifacts:
    - `jar` (where applicable)
    - `pom` with correct dependency scopes
- Maven consumers can use the published artifacts normally.
- Test dependencies are not part of published POM contracts (by ecosystem design). Shared tests must be modeled explicitly via dedicated `*-tests` artifacts if needed.

###### 3.10.5.7.2 Policy artifacts vs production artifacts

- Policy artifacts produce/host PBBOM outputs and policy parents.
- Production artifacts consume the selected policy via BOM import (PBBOM/PVBOM) and/or parent inheritance (internal conventions).
- Keep policy and production concerns separated to minimize coupling and to make compatibility management explicit.

---

##### 3.10.5.8 Appendix: Naming suggestions (non-normative)

This appendix proposes consistent naming patterns; projects may adjust to existing Algites naming standards.

- Policy Background BOM:
    - `algites-<policy>-pbbom`
- Product Interface BOM:
    - `algites-<product>-pibom`
- Platform Variant BOM:
    - `algites-<platform>-pvbom-<variant>`

Examples:

- `algites-core-pbbom`
- `algites-spring-pibom`
- `algites-platform-pvbom-hibernate`

---

### 3.11. Mermaid Diagrams (Normative Reference)

#### 3.11.1 Container vs Parent inheritance overview

```mermaid
flowchart TD
  R[Repo Config<br/>(root container + root parent)]
  C1[Container Artifact A1]
  B1[Artifact B1]
  B2[Artifact B2]
  P1[Parent Artifact A2]

  R -->|contains| C1
  C1 -->|contains| B1
  C1 -->|contains| B2

  B1 -->|parent| P1

  VC[ContainerVersionContext]
  RT[RuleTemplate catalog]
  DI[ParentDependencyIntentSets]

  R --> VC
  VC -->|inherits via contains| C1
  C1 -->|inherits via contains| B1
  C1 -->|inherits via contains| B2

  R --> RT
  RT -->|merge allowed| C1
  RT -->|merge allowed| P1
  RT -->|merge allowed| B1

  R --> DI
  DI -->|inherits via parent| P1
  P1 -->|inherits via parent| B1
```

#### 3.11.2 Resolution pipeline (high level)

```mermaid
flowchart LR
  A[Load definitions<br/>(repo + artifacts)] --> B[Merge catalogs<br/>(template sets)]
  B --> C[Compute container contexts<br/>(ContainerVersionContext)]
  C --> D[Compute parent contexts<br/>(baseline intents)]
  D --> E[Activate intents<br/>(apply templates + rules)]
  E --> F[Resolve versions<br/>(controlled via VC;<br/>uncontrolled via ranges/preferred)]
  F --> G[Final validation<br/>(conflicts, unresolved, cycles)]
  G --> H[Generate build-tool outputs<br/>(Gradle/Maven mapping)]
```

### 3.12. Migration Notes (temporary; will be removed)

This section is intentionally placed at the end and is **temporary**.

- Previous models that relied on **ArtifactKind** (policy/bom/aggregator/etc.) should be migrated by:
    - replacing “kind-driven behavior” with explicit **ContainerVersionContext** + **DependencyIntent** activation,
    - moving BOM/policy logic into **OutputType** + intent rule templates for dependency steering.
- Aggregator artifacts remain possible, but are no longer mandatory just to express policy inheritance:
    - repo-root and containers can supply catalogs,
    - parent edges define baseline dependency intents.


[[/PROPOSAL]]

