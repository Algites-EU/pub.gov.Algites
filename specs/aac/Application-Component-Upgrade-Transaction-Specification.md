# Application Component Upgrade Transaction Specification

**Status:** Draft public specification  
**Scope:** Technology-neutral package replacement, complete target-state validation, multi-component upgrade, migration coordination, retirement, and rollback semantics for AAC component systems  
**Audience:** Core implementers, package-manager authors, component/plugin authors, SDK authors, UI authors, product architects, and technology-profile authors  
**Companion documents:** `Application-Component-Architecture-Governance.md`, `Application-Component-Lifecycle-and-Provisioning-Specification.md`, `Application-Component-Capability-Contract-Specification.md`, `Application-Component-Context-Configuration-and-Entitlement-Specification.md`, `Application-Component-UI-Specification.md`

---

# I. Replacement Transaction Model

## I.1 Complete active state

An **upgrade/replacement transaction** is a requested transformation from one complete active AAC component state to another complete active AAC component state. It may replace one component or many components. A one-component upgrade is only the one-replacement case of the same model.

```text
CURRENT ACTIVE COMPONENT STATE
        |
        | one logical replacement transaction
        v
TARGET ACTIVE COMPONENT STATE
```

The stable component identity does not change merely because its package version or artifact digest changes. A replacement candidate normally has the same component ID as the component it replaces. Component addition/removal may participate only when the product models those lifecycle changes explicitly and validates the resulting complete target state.

## I.2 Single active version invariant

Within one Core-managed runtime/package-selection domain, at most one artifact version of a component identity may be active/selected. A replacement is not concurrent activation of old and new versions.

Different package versions may exist physically at the same time for download, staging, verification, diagnostics, or rollback retention. That physical coexistence has no graph semantics until Core selects/adopts one target artifact. Products that need different active versions for different workspaces/applications must isolate them into separate runtime/package-selection domains.

## I.3 Complete target-state semantics

Compatibility MUST be evaluated against the **complete target state** after every requested replacement has been applied. Intermediate states implied by an arbitrary sequential installation order need not be valid.

Example:

```text
current: A/1 consumes X/v1
         B/1 provides X/v1

target:  A/2 consumes X/v2
         B/2 provides X/v2
```

Neither `A/2 + B/1` nor `A/1 + B/2` is valid, but the transaction `{A:1->2, B:1->2}` is valid if the complete target graph is valid. Core MUST NOT reject such a transaction merely because no one-at-a-time upgrade order yields valid intermediate states.

---

# II. Side-Effect-Free Target Preflight

## II.1 Target component set

Before deactivating the current runtime or mutating authoritative persisted state, Core MUST construct the hypothetical target component set by replacing all selected component artifacts simultaneously in the current component map. Components not changed by the transaction remain in the target set.

Static descriptors and other preflight metadata SHOULD be read without executing arbitrary candidate business implementation code.

## II.2 Target active contract catalog

Core MUST rebuild the target active contract catalog from:

```text
Core/built-in canonical contracts
+ canonical contract bundles supplied by every component in the target set
```

The target catalog MUST NOT be computed by simply adding candidate bundles to the current active catalog. A contract supplied only by a replaced/removed current component is absent from the target catalog unless another target source supplies the same canonical definition. Canonical conflicts remain fatal.

## II.3 Target capability graph

Core MUST resolve the target provider/consumer graph using the same authoritative rules as ordinary activation. A valid negotiated version remains selected from:

```text
consumer supported versions
    ∩ provider supported versions
    ∩ target Core active contract catalog versions
    ∩ lifecycle-allowed versions
```

Mandatory requirements MUST resolve. Explicit provider-instance selections, qualifiers, instance restrictions, binding preferences, ambiguity policy, graph acyclicity, and other ordinary AAC rules remain in force. Preflight MUST NOT invent a weaker compatibility checker.

## II.4 Provider-instance reconciliation

Core MUST determine whether existing persistent provider instances remain valid under the target provider definitions. If a target removes or incompatibly changes a provider definition referenced by persistent instances/bindings, the transaction MUST either include an explicit supported migration/removal/reconciliation path or be rejected.

## II.5 Persisted schema compatibility

Core MUST inspect relevant component-target configuration, provider-instance-target configuration, and semantic component-extension data for every replaced/affected component. The target must either:

- read the stored schema directly;
- provide an explicit migration path; or
- use a compatible preserved snapshot/recovery mechanism allowed by policy.

If no safe path exists, Core MUST preserve the payload and reject target activation rather than silently reinterpret, truncate, or discard it.

## II.6 Diagnostics

If preflight rejects the target state, current live state MUST remain unchanged. Diagnostics SHOULD report all known blockers when practical, including:

```text
affected component
unsatisfied mandatory capability requirement
missing provider
consumer/provider/target-catalog version sets
canonical contract conflict
provider-instance incompatibility
migration/schema incompatibility
policy/entitlement condition relevant to activation
```

The user or planner MAY add further component replacements and validate again.

---

# III. Staging and Commit Boundary

## III.1 Staging is non-active

Candidate artifacts MAY be downloaded, verified, unpacked, promoted to an immutable installed/staging area, and statically inspected before commit. None of those operations make the candidate an active component participant.

## III.2 Migration staging

Required component-owned data migrations SHOULD be computed/staged before the live cutover when possible. Components MAY supply transformation code, but Core owns invocation, validation, persistence, concurrency control, commit, and rollback. Components MUST NOT mutate Core-owned persistent stores directly as an upgrade side channel.

## III.3 Logical atomicity

Implementation steps may be sequential, but the replacement transaction is logically atomic from the managed system perspective. A typical commit sequence is:

```text
deactivate/suspend affected runtime
commit/reconcile required persisted state
admit target descriptors/contracts/schemas
switch active package selections
reconcile provider instances/bindings
construct/wire target runtime
activate complete target graph
verify target readiness
commit transaction
```

No partial intermediate component set becomes the authoritative steady state.

---

# IV. Rollback and Artifact Retirement

## IV.1 Transaction-wide rollback

If any post-boundary step fails, Core MUST attempt restoration of the previous complete valid state. Depending on what changed, rollback includes as applicable:

```text
active package selections and package lifecycle
configuration values and policy modes
semantic component-extension payloads
admitted descriptors/contracts/schemas
provider-instance definitions/reconciliation
Core-owned bindings/topology
runtime instances, wiring, and activation
```

Package-only rollback is insufficient whenever dependent state changed.

## IV.2 Superseded artifacts

Core SHOULD retain the previous active artifact until the replacement is known to have committed successfully. After successful replacement, the superseded artifact is non-active and SHOULD be moved/marked `obsolete` according to product retention policy. It remains excluded from ordinary automatic selection.

A rollback/recovery action MAY restore an obsolete artifact to an installable/selected state after its compatibility with the restored persisted/runtime state has been established.

## IV.3 Rollback failure

If rollback cannot restore a valid previous state, Core MUST report both the original transaction failure and rollback failure as a high-severity diagnostic. It MUST NOT falsely report the old state as healthy. Product-specific recovery/safe-mode behavior MAY then apply.

---

# V. Package Planning and Automatic Updates

## V.1 Explicit multi-component transactions

A user/API MUST be able to request a replacement set containing multiple component target versions when the product exposes upgrade administration. This is sufficient to resolve dependency transitions that cannot be performed one component at a time.

## V.2 Optional automatic solver

AAC does not require Core to search the package-version combination space automatically. A package planner MAY propose additional replacements needed to make a requested upgrade valid. Any proposal MUST still pass through the same authoritative complete target-state validator and transaction path.

## V.3 Automatic update policy

`AUTO_COMPATIBLE`, `AUTO_LOCKED`, or product-specific automatic modes MUST NOT bypass target-state validation. Automatic selection of a candidate does not imply permission to commit it if the complete target component state is invalid.

---

# VI. UI and Administrative Requirements

A package/replacement UI SHOULD:

- show downloaded, installed/staged, active, and obsolete package state distinctly;
- expose artifact digest/provenance/verification and requirement/lock status;
- permit selecting several replacements into one transaction;
- provide an explicit target-state validation action before commit;
- show all known affected components and incompatibility reasons;
- show transaction progress and commit/rollback result;
- avoid presenting physically staged candidates as concurrently active;
- surface optional solver suggestions without bypassing validation.

The UI does not become the authority for compatibility or lifecycle state.

---

# VII. Conformance Requirements

Conformance suites SHOULD cover at least:

- one-component replacement through the general transaction primitive;
- two-or-more-component replacement where intermediate states are invalid but target state is valid;
- rejection of an invalid target without live-state mutation;
- target active contract-catalog rebuild and removal of contracts supplied only by a replaced component;
- canonical target contract conflict rejection;
- mandatory provider/version mismatch diagnostics;
- provider-instance reconciliation failure;
- configuration and semantic extension-data migration success;
- failure after persisted migration followed by complete rollback;
- successful retirement of superseded artifacts;
- rollback restoration of the previous artifact/data/runtime state;
- rollback failure reporting;
- one-active-version-per-component enforcement.

---

# VIII. Architectural Invariants

1. **At most one artifact version of a component identity is active/selected in one Core-managed runtime/package-selection domain.**
2. **Physical staging coexistence does not imply concurrent runtime activation.**
3. **A component upgrade is a complete-state replacement transaction; single-component upgrade is only a special case.**
4. **One transaction may replace multiple components, and arbitrary sequential intermediate states need not be valid.**
5. **The target active contract catalog is rebuilt from the complete target component set.**
6. **Target capability/provider/consumer resolution uses the ordinary authoritative AAC resolver/rules.**
7. **Preflight occurs before mutation of authoritative live state wherever static/declarative validation permits.**
8. **Required configuration and semantic extension-data migrations participate in the same Core-owned transaction.**
9. **Post-boundary failure triggers transaction-wide rollback; package-only rollback is not sufficient after dependent state changed.**
10. **Superseded artifacts are non-active after commit and are retained/retired according to explicit rollback/retention policy.**
11. **Automatic package planning/upgrade uses the same target-state validator as manual replacement.**
