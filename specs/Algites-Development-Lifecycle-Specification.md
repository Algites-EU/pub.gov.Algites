# Algites Development Lifecycle Specification

## 1. Introduction


### 1.1. Algites CI Policy

This document defines the **standard CI behavior** for Algites repositories using the various CI implementations. By default Github is used as the CI platform, but the general approach may be the same in the case some other platform would be selected.
In the case of GitHub the GitHub Actions pipeline facility is used.

The goal is to provide a predictable, low-friction workflow:

- Keep **Gradle** as only build tool, no Maven support anymore.
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

- **Stub workflow**: a small workflow committed inside each repository (e.g. `.github/workflows/algites-ci-pub.yml` or `.github/workflows/algites-create-lane-pub.yml`),
  which triggers on push / manual dispatch and calls the central reusable workflow.
- **Central workflow**: the reusable workflow hosted centrally (e.g. in `pub.gov.Algites`) that contains the logic for:
  - deciding what to do based on branch name,
  - detecting config changes,
  - resolving Java version,
  - generating tokens,
  - running compile/tests/publishing,
  - detecting issue references for build traceability.

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

Branches containing the followiong names are treated as to be verified on push:

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

The unified build is based uniquely on Gradle in all repositories. But for every artifacts Gradle produces also the Maven artifacts to allow free usage of the generated products also from Maven buuilding system. Artifacts are published by gradle in the maven repositories.

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

The bootstrap defined in `settings.gradle.kts` is responsible only for:

- `pluginManagement.repositories`
- `dependencyResolutionManagement.repositories`

It must **not**:

- configure project logic
- define versions
- apply plugins
- contain build behavior

---

###### 2.1.5.1.5 Public vs private repository variants

Algites distinguishes two bootstrap variants:

- **Public repositories**
- **Private repositories**

The difference is **strictly additive**: private repositories extend the public bootstrap.

---

###### 2.1.5.1.6 Canonical public bootstrap (`settings.gradle.kts`)

Public repositories define only publicly accessible repositories.

```kotlin
// settings.gradle.kts (public repository)

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        algitesPublicRepositories()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        algitesPublicRepositories()
    }
}
```

---

###### 2.1.5.1.7 Canonical private bootstrap (`settings.gradle.kts`)

Private repositories extend the public bootstrap with private repositories.

```kotlin
// settings.gradle.kts (private repository)

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        algitesPublicRepository()
        algitesPrivateRepository()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        algitesPublicRepository()
        algitesPrivateRepository()
    }
}
```

---

###### 2.1.5.1.8 Repository helper functions

To avoid duplication and ensure consistency, repository definitions are expressed via helper functions defined **directly in `settings.gradle.kts`**.

These functions are intentionally simple and declarative.

####### 2.1.5.1.8.1 Public repository definition

```kotlin
fun RepositoryHandler.algitesPublicRepositories() {
     maven {
        name = "algites-public-releases"
        url = uri("https://repo1.maven.org/maven2")
        mavenContent {
            releasesOnly()
        }
    }
     maven {
        name = "algites-public-snapshots"
        url = uri("https://dl.cloudsmith.io/public/algites/maven-snapshots-pub/")
        mavenContent {
            snapshotsOnly()
        }
    }

}
```

####### 2.1.5.1.8.2 Private repository definition

```kotlin
fun RepositoryHandler.algitesPrivateRepositories() {
    maven {
        name = "algites-private-releases"
        url = uri("https://....")
        mavenContent {
            releasesOnly()
        }
    }
    maven {
        name = "algites-private-snapshots"
        url = uri("https://....")
        mavenContent {
            snapshotsOnly()
        }
    }
}
```

Private repository definitions are **never present in public repositories**.

---

###### 2.1.5.1.9 Change management and evolution

Bootstrap changes are expected to be:

- rare
- deliberate
- explicit

When repository definitions change:

- the change is applied directly to `settings.gradle.kts`
- affected repositories are updated explicitly
- automation (scripts or bulk refactoring) may be used

This approach avoids hidden coupling and preserves auditability.

---

###### 2.1.5.1.10 Rationale and trade-offs

This design intentionally accepts:

- small, explicit duplication

In exchange for:

- zero bootstrap magic
- full IDE support
- deterministic behavior
- minimal onboarding friction

The design aligns with Gradle’s philosophy of **self-describing builds** and prioritizes operational simplicity.

---

###### 2.1.5.1.11 Normative summary

- Gradle bootstrap is defined explicitly in each repository
- `settings.gradle.kts` is the single source of bootstrap truth
- Public and private variants differ only additively
- No shared pre-settings bootstrap is used
- Explicit configuration is preferred over centralized indirection

This approach is considered **final and stable** for Algites.


---

##### 2.1.5.2. What Gets Executed (by Mode)

###### 2.1.5.2.1 Mode = approval

- Gradle (default): `./gradlew check`

###### 2.1.5.2.2 Mode = verification

- Gradle (default): `./gradlew test integrationTest`

###### 2.1.5.2.3 Mode = construction

- Gradle (default): `./gradlew testClasses`

###### 2.1.5.2.4 Mode = skip

Exit early after logging the decision.

---

##### 2.1.5.3. Java Version Resolution

If Java version is not provided explicitly, the workflow auto-detects it in this order:

1. `.java-version` (single integer per line, e.g. `17`)
2. `gradle.properties` containing `javaVersion=<n>`
3. fallback default (currently `17`)

---

#### 2.1.5.4. Issue references (optional but recommended)

Algites CI can automatically detect **GitHub Issue references** related to a build and display them in the
Actions **Job Summary** as clickable links.

##### 2.1.5.4.1 Branch naming convention (strict)

For feature branches, prefix the feature “slug” with an explicit **ID marker** and terminate it with an underscore:

- `feature/ID.123_some-description`
- `jvm17/feature/ID.123_some-description`
- `feature-testrun/ID.123_ci-experiment`

Cross-repository shorthand (Algites-EU only) is also supported:

- `feature/ID.pub.tool.Java-123_some-description`
- `j25/feature-testrun/ID.pub.tool.Java-123_ci-experiment`

> The `ID.` prefix is **required** in branch names to avoid accidental matches (e.g. variant branches like `jvm17/*` or `and21/*`).
> The underscore `_` acts as the delimiter that ends the issue reference.

##### 2.1.5.4.2 Commit message convention (flexible)

In commit subjects, CI detects references introduced by `#`.

Supported forms:

- `#123` (issue in the same repository)
- `#ID.123` (same as `#123`)
- `#pub.tool.Java-123` (cross-repo shorthand, Algites-EU only)
- `#ID.pub.tool.Java-123` (same as above)

##### 2.1.5.4.3 What CI does with it

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
- Keep publishing limited to publish branches (`main`, `master`, `develop`, `hotfix`, and versioned variants).
- Use the identification of the issues everywhere it is possible and reasonable to keep the transparency of the changes ión highes possible level

---

### 2.2. Specifics of the Integration With Github Actions facility

(TBD)

---

## 3. Algites Release & Upmerge Policy

This document describes how Algites repositories do **versioning, releases, and upmerges** using:
- **variant-prefixed branches** (e.g. `jvm17/...`, `and21/...`)
- **lane branches** (e.g. `*/lane/1.1`)
- **version computed from tags** (B1 policy)
- Workflows for **release** and **lane creation**

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

```properties
algites.repository.lane=1.1
```

CI enforces:
- If the branch name contains `/lane/<X>/`, then `algites.repository.lane` must equal `<X>`.

> To enforce this as a *hard rule*, protect branches (e.g. `*/lane/*`) and require the CI status check.

---

#### 3.1.3. Versioning: Computed patch

##### 3.1.3.1 Tag format (release)
Release tags MUST be:

- `v<A>.<B>.<C>-<variant>`

Example:
- `v1.1.15-jvm17`

##### 3.1.3.2 Snapshot version (CI)
For lane `<A>.<B>` and variant `<variant>`:
- Find max released `C` from tags `vA.B.*-variant`
- Compute `nextC = maxC + 1` (or `0` if no tags exist)
- CI uses snapshot version:

- `<A>.<B>.<nextC>-<variant>-SNAPSHOT`

Example:
- last tag: `v1.1.15-jvm17`
- CI snapshot: `1.1.16-jvm17-SNAPSHOT`

This eliminates constant conflicts during upmerge (because `C` is not stored in repo files).

---

#### 3.1.4. CI: when/what runs

The unified CI workflow computes the snapshot version and then runs Gradle


#### 3.1.5. Release process (manual action)

Releases are **explicit** and start from the GitHub UI:

- **Actions → Algites Universal Release → Run workflow**
- Choose the branch, typically:
  - `jvm17/lane/1.1` (or any other lane branch)

The release workflow will:
1) validate lane consistency (`algites.repository.lane` vs branch lane)
2) compute next `C` from tags
3) create a new tag `vA.B.C-variant`
4) publish artifacts (Gradle or Maven, according to selection / override)

##### 3.1.5.1 Automated tag naming
Tag is computed automatically. You do **not** type it manually.

---

#### 3.1.6. Upmerge support

After a successful release, you can upmerge the resulting changes to higher lanes (same major).

The release workflow supports:
- `upmerge_mode=none` → do nothing
- `upmerge_mode=next` → upmerge to next minor lane if it exists (e.g. `1.1` → `1.2`)
- `upmerge_mode=all-higher` → upmerge to all higher lanes that exist (within the same major)
- `upmerge_mode=explicit` → upmerge only to branches you list

##### 3.1.6.1 “Preview only” (copy/paste workflow)
Set `upmerge_preview_only=true` to only print a **single CSV line** with compatible branches into the Action Summary, e.g.:

```
jvm17/lane/1.2,jvm17/lane/1.3
```

You can copy that line and re-run the release with `upmerge_mode=explicit` and paste it into `upmerge_targets`.

##### 3.1.6.2 What the upmerge does technically
Upmerge is attempted via:
- creating a temporary branch from the target lane
- cherry-picking commits from the previous release tag (or only HEAD if no previous tag exists)
- opening a PR to the target lane

If cherry-pick conflicts, the workflow skips that target (manual resolution is required).

---

#### 3.1.7. Creating a new lane (automation)

To start a new minor lane across variants, use:

- **Actions → Algites Create Lane (All Variants) → Run workflow**

Inputs:
- `source_lane`: e.g. `1.1`
- `new_lane`: e.g. `1.2`
- `variants`: `auto` (discover existing variants with the source lane) or `explicit`

The CI workflow:
- creates `*/lane/<new_lane>` from `*/lane/<source_lane>`
- updates `algites.repository.lane=<new_lane>` in the new branch
- pushes the new branch

---

#### 3.1.8. Practical notes

##### 3.1.8.1 “How do I release the next lane after upmerge?”
Upmerge only transfers commits. A release of that next lane is still:
- a deliberate manual action: run the Release workflow on `*/lane/1.2`.

##### 3.1.8.2 “What if I need different versions for different artifacts?”
This model assumes **one version per repository**.
If different artifacts must have independent version lifecycles, split them into separate repositories.

---

#### 3.1.9. Counterpoints (why this might bite you)

- **Tag-driven versioning** is great for avoiding merge conflicts, but you lose the “look at sources and see exact patch version” feeling. You’ll rely more on tags/releases and CI summaries.
- **Cherry-pick upmerge** is pragmatic, but it can silently skip targets on conflicts unless you watch the summary carefully.
- **One version per repo** simplifies governance, but it can force repo splits earlier than you’d like (more repos, more wiring).

If any of these are deal-breakers for a particular repo, you can still fall back to “version in a single file” (with more merge conflicts) or maintain separate version tracks per module (with more complexity).

### 3.2 Github Actions specific Policy

(TBD)

---



**© Algites**
