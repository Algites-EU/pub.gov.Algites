# Application Component Lifecycle and Provisioning Specification

**Status:** Draft public specification  
**Scope:** Technology-neutral lifecycle, provisioning, entitlement, graph eligibility, activation, suspension, unprovisioning, and uninstall semantics for application component architectures  
**Audience:** Core implementers, component/plugin authors, SDK authors, product architects, entitlement-provider authors, and technology-profile authors  
**Companion documents:** `Application-Component-Architecture-Governance.md`, `Application-Component-Architecture-Technical-Notes.md`, `Application-Component-Capability-Contract-Specification.md`

---

# I. Purpose and Architectural Position

## I.1 Purpose

This specification defines the lifecycle of components and provider instances independently of implementation language or UI technology. It separates package presence, Core-owned provisioning, configuration validity, entitlement, capability graph eligibility, runtime construction, activation, suspension, and removal.

The goal is that Core can always explain *which stage* prevents a component or provider instance from being usable.

## I.2 Relationship to Governance

`Application-Component-Architecture-Governance.md` defines the architectural invariants. This document owns the detailed lifecycle semantics. Governance documents SHOULD reference this specification instead of duplicating lifecycle state machines.

## I.3 Relationship to capability contracts

`Application-Component-Capability-Contract-Specification.md` defines what a capability invocation means after a valid binding exists. This document defines when a provider instance is eligible to become such a binding target and when new invocations may be accepted.

## I.4 Technology neutrality

A Java class-loader profile, Python interpreter profile, process/RPC profile, desktop host, web host, or headless service MAY realize lifecycle stages differently, but MUST preserve the logical ordering and invariants defined here.

---

# II. Lifecycle Domains

## II.1 Package state, persistent state, and runtime state are distinct

Core MUST distinguish at least:

```text
package/distribution state
Core-owned persistent provisioning state
runtime activation state
```

Installing a package MUST NOT imply that it is configured, entitled, resolved, or active. Removing a package MUST NOT by itself imply destructive deletion of preserved semantic data.

## II.2 Component state and provider-instance state

A component package may declare one or more provider definitions. Concrete capability bindings target provider instances, not provider definitions. Provider-instance lifecycle is therefore independently diagnosable even when all instances originate from one component package.

## II.3 Core is authoritative

Core owns or coordinates lifecycle transitions that affect persistent identity, graph topology, entitlement eligibility, activation, and destructive removal. Component code may participate through declared schemas, migration/provisioning hooks, and lifecycle callbacks, but MUST NOT silently create an alternative persistent lifecycle outside Core state.

---

# III. Baseline Lifecycle Pipeline

## III.1 Logical stages

The baseline pipeline is:

```text
PACKAGE PRESENT / INSTALLED
        |
        v
DISCOVER
        |
        v
VERIFY
        |
        v
ADMIT CONTRACTS
        |
        v
PROVISION
        |
        v
VALIDATE CONFIGURATION / DATA
        |
        v
EVALUATE ENTITLEMENT
        |
        v
RESOLVE GRAPH
        |
        v
INSTANTIATE
        |
        v
WIRE
        |
        v
READY
        |
        v
ACTIVATE
        |
        v
RUNNING
```

Shutdown/removal paths add:

```text
SUSPEND -> DEACTIVATE -> optionally UNPROVISION -> optionally UNINSTALL
```

A product may combine implementation steps internally, but diagnostics and conformance MUST preserve the distinction whenever the difference affects correctness.

## III.2 Discovery

Core reads static descriptors without executing arbitrary component business logic. Discovery identifies component identity, provider definitions, consumed/provided capabilities, contract bundles, configuration/data schemas, provisioning declarations, and entitlement requirements.

## III.3 Verification

Verification covers package integrity, signatures/trust policy where applicable, descriptor validity, runtime-profile compatibility, and other install-time safety checks. Verification failure blocks later runtime stages.

## III.4 Contract admission

Any canonical capability contract used across component boundaries MUST be admitted to the Core-owned active contract catalog before implementation classes/endpoints requiring that contract are used. Contract admission is not provider activation.

## III.5 Provisioning

Provisioning creates or reconciles Core-owned persistent state required for the component/provider definitions to participate in the system. Provisioning MAY succeed even when entitlement is currently denied.

## III.6 Validation

Core validates configuration schema versions, required fields, persistent semantic-data compatibility, and migration state. An instance with incomplete configuration may remain provisioned but MUST NOT be considered a usable provider candidate.

## III.7 Entitlement evaluation

Entitlement is evaluated before final graph resolution whenever it affects provider/capability availability. Installed and provisioned but unentitled components remain visible and diagnosable; they need not be uninstalled.

## III.8 Graph resolution

Core computes provider candidates, applies compatibility, lifecycle, configuration, entitlement, and binding policy, selects provider instances, negotiates contract versions, and validates the resulting concrete extension-instance binding graph. Resolution MUST fail if the concrete graph contains any directed cycle; direct self-binding is the one-node special case of that rule.

## III.9 Runtime construction and activation

Core instantiates runtimes/endpoints only after enough static state is known to do so safely, wires Core-selected bindings, crosses a readiness barrier, then activates the runtime scope. Constructor/bootstrap code SHOULD NOT perform mandatory peer business calls before wiring/readiness is complete.

---

# IV. Lifecycle Entry Points and Bootstrap Hooks

## IV.1 Why lifecycle hooks are not ordinary business capabilities

Provisioning, migration, and early validation may occur before the ordinary capability graph is fully resolved. A component lifecycle hook MUST therefore be reachable through the stable bootstrap/lifecycle interface defined by the active technology profile, not by assuming that arbitrary business capabilities are already callable.

## IV.2 Technology-neutral logical hooks

A technology profile MAY expose logical hooks equivalent to:

```text
provision(context)
validate(context)
instantiate(context)
wire(bindings)
ready()
activate()
suspend(reason)
deactivate(reason)
unprovision(context)
```

Migration hooks are defined alongside configuration/data schema migration support. Not every component needs every hook. Static declarative schemas and defaults SHOULD be preferred when they are sufficient.

The hook list is intentionally **not** a one-to-one restatement of the complete lifecycle pipeline in III.1. Several stages are authoritative Core operations and therefore do not have an ordinary component callback. In particular, `validate(context)` concerns configuration/data validity and component-specific semantic validation; it MUST NOT be treated as an entitlement decision. Entitlement evaluation is a separate Core-authoritative stage.

### IV.2.1 Relationship between lifecycle stages and hooks

The baseline stages map to technology-neutral hooks as follows:

| Lifecycle stage | Component hook | Meaning |
| --- | --- | --- |
| `PACKAGE PRESENT / INSTALLED` | none | The component distribution is physically available to the application. Installation is package/distribution management, not runtime activation. |
| `DISCOVER` | none | Core reads static descriptors, schemas, capability declarations, provider definitions, provisioning declarations, and entitlement requirements without running arbitrary component business code. |
| `VERIFY` | none | Core verifies descriptor validity, integrity/trust policy, runtime-profile compatibility, signatures where applicable, and other package-level prerequisites. |
| `ADMIT CONTRACTS` | none | Core validates and admits canonical capability contracts into the active Core-owned contract catalog. This stage establishes contract identity; it does not instantiate providers. |
| `PROVISION` | optional `provision(context)` | Core creates or reconciles persistent Core-owned component/provider state. Declarative provisioning is preferred; a hook is used only when additional component-specific initialization is required. |
| `VALIDATE CONFIGURATION / DATA` | optional `validate(context)` | Core performs generic schema/version validation and may invoke component-specific semantic validation. This stage does **not** decide licensing/entitlement and does not select providers. |
| `EVALUATE ENTITLEMENT` | no ordinary component hook | A trusted Core entitlement facility determines whether the component, provider definition, instance, or capability is currently entitled and which Core-enforced constraints apply. |
| `RESOLVE GRAPH` | none | Core selects concrete provider instances, negotiates contract versions, validates bindings, and verifies that the resolved extension-instance graph satisfies the DAG invariant. Components do not choose their own providers here. |
| `INSTANTIATE` | optional `instantiate(context)` | The runtime representation of the component/provider instance is constructed, but normal business activity has not started. |
| `WIRE` | optional `wire(bindings)` | Core supplies the already-resolved capability bindings/proxies/handles and other wiring required by the runtime instance. Wiring does not permit the component to replace Core-selected bindings. |
| `READY` | optional `ready()` | The component performs local post-wiring readiness checks and initialization that require bindings to exist but do not require peers to already be active. Successful return means the instance is ready to cross the activation barrier. |
| `ACTIVATE` | optional `activate()` | The component begins normal operational behavior, such as accepting capability calls, starting listeners/timers, or enabling external side effects according to the technology profile. |
| `RUNNING` | none | Stable operational state after successful activation. Normal capability invocations occur here. |
| `SUSPEND` | optional `suspend(reason)` | The runtime is asked to quiesce or temporarily stop accepting new work while preserving provisioned persistent state and, where practical, enough runtime state for controlled recovery/resume policy. |
| `DEACTIVATE` | optional `deactivate(reason)` | Normal operational behavior stops and transient runtime resources are released. Deactivation does not by itself remove Core-owned persistent provisioning state or semantic data. |
| `UNPROVISION` | optional `unprovision(context)` | Core removes or reconciles provisioned persistent component/provider state after reference and data-safety checks. Unprovisioning is distinct from package uninstall and from explicit semantic-data purge. |
| `UNINSTALL` | none | The component distribution/package is removed from the application installation. Uninstall MUST NOT silently imply destructive deletion of preserved configuration or semantic data unless a separate explicit policy/action authorizes it. |

The mapping above defines **logical responsibilities**, not a mandatory concrete method ABI. A technology profile MAY implement a stage entirely inside Core, by declarative metadata, by generated adapters, or by explicit callbacks, provided the ordering, ownership, and failure semantics remain equivalent.

### IV.2.2 `provision(context)`

`provision(context)` participates in the `PROVISION` stage. Provisioning is the Core-controlled creation or reconciliation of persistent state required for a component/provider definition to participate in an application scope. Typical results include Core-owned configuration records, initial provider-instance records with immutable generated IDs, schema/version state, defaults, or migration markers.

Provisioning is distinct from package installation and normally happens much less frequently than runtime startup. A component may be installed but not provisioned, or provisioned but not yet configured, entitled, resolved, or active. The hook SHOULD be omitted when the same result can be expressed declaratively.

#### IV.2.2.1 Declarative provisioning versus a provisioning hook

The `PROVISION` **stage** and the optional `provision(context)` **component callback** are different concepts. Core may execute the `PROVISION` stage even when no component code is called.

Static component metadata SHOULD describe everything that can be provisioned declaratively, including configuration schemas, schema defaults, provider definitions, and whether an initial provider instance is required by the component/product profile. Core derives a provisioning plan from that metadata and remains the authority that creates stable provider-instance IDs and commits persistent state.

A component callback is used only when additional component-specific initialization cannot reasonably be expressed by static metadata and generic Core behavior. The component descriptor MUST explicitly declare the presence of such a lifecycle callback using the mechanism defined by the active technology profile. Core MUST NOT discover lifecycle callbacks by executing component code, probing for methods, or assuming that a method named `provision` exists.

Conceptually:

```text
Core reads static component descriptor
        |
        +-- provider definitions
        +-- configuration schemas/defaults
        +-- declarative provisioning requirements
        +-- optional lifecycle-hook declarations
        |
        v
Core constructs staged provisioning plan
        |
        +-- creates missing Core-owned records
        +-- generates immutable provider-instance IDs
        +-- applies declarative defaults
        |
        v
provision hook declared?
        |
        +-- no  -> Core validates and commits staged state
        |
        +-- yes -> Core invokes provision(context) against staged state
                   -> validates permitted result/requests
                   -> commits or rolls back as one controlled operation
```

The hook therefore does **not** normally exist merely to return defaults that could have been declared in the configuration schema. It is reserved for initialization that genuinely requires component-specific logic, such as importing existing external configuration, deriving an initial value from an allowed environment probe, or requesting creation of additional framework-managed state through approved provisioning primitives.

#### IV.2.2.2 Concrete provisioning example

Consider a Git repository provider. The following is conceptual metadata; the exact descriptor syntax for declaring a lifecycle entry point is technology-profile-specific:

```yaml
provider_definitions:
  - id: git-repository
    configuration_schema: git-repository-config_1

    provisioning:
      initial_instance:
        create: true
        name: default

# Optional. Omit this entire declaration when declarative provisioning is sufficient.
lifecycle:
  hooks:
    provision:
      entry_point: provision
```

The configuration schema may declare defaults:

```yaml
id: git-repository-config
version: 1
properties:
  repository_url:
    type: string
    required: true

  branch:
    type: string
    default: main

  timeout_seconds:
    type: integer
    default: 30
```

On first provisioning Core can therefore stage an instance such as:

```yaml
id: "798852dd-..."       # generated by Core
name: default             # mutable display name, not identity
provider_definition: git-repository
configuration:
  branch: main
  timeout_seconds: 30
```

Because `repository_url` is still missing, the instance may remain conceptually:

```text
PROVISIONED_UNCONFIGURED
```

and is not yet an eligible provider candidate. Core does not need to call component code to obtain the `main` or `30` defaults because those values are already declarative.

If the descriptor does **not** declare a provisioning hook, Core completes provisioning using the staged declarative state only. If it **does** declare one, Core invokes the technology-profile entry point after preparing the staged state. The hook receives a restricted provisioning context and may request only operations allowed by that context. It MUST NOT invent hidden provider-instance identities, directly write Core persistence, or bypass schema/reference validation.

For example, a Git component might use a provisioning hook to detect and propose import of a pre-existing repository configuration from an explicitly permitted external source. Core would validate that proposal and decide what state to commit. Merely supplying schema defaults is not a sufficient reason to require the hook.

#### IV.2.2.3 Reconciliation on later provisioning passes

Provisioning is not necessarily a one-time create operation. When a component version, schema, product profile, or required framework state changes, Core may run a reconciliation pass that compares the desired provisioned state with the current persisted state. Reconciliation MAY create newly required framework-owned records or execute explicitly defined migrations, but it MUST preserve user-controlled configuration/data unless an explicit migration or user-approved destructive operation says otherwise.

### IV.2.3 `validate(context)`

`validate(context)` participates in `VALIDATE CONFIGURATION / DATA`. Core first performs generic validation that it can derive from schemas and stored version metadata. The optional hook is for additional component-specific semantic checks that cannot be expressed by the generic schema alone.

Validation answers questions such as whether required configuration/data is structurally and semantically usable by the installed component version. It MUST NOT:

- decide whether commercial/product entitlement is valid;
- choose or replace provider bindings;
- activate runtime behavior;
- silently mutate authoritative persistent state outside an explicit migration/provisioning transaction.

Entitlement is deliberately evaluated in the separate `EVALUATE ENTITLEMENT` stage after configuration/data validity is known.

### IV.2.4 `instantiate(context)`

`instantiate(context)` participates in `INSTANTIATE`. It creates the technology-specific runtime object, interpreter endpoint, process endpoint, service object, or equivalent implementation for a provisioned provider/component instance.

Instantiation MUST NOT be treated as activation. The resulting runtime may exist before bindings are injected and before normal capability calls are permitted. Constructors/factories SHOULD avoid mandatory peer business calls.

### IV.2.5 `wire(bindings)`

`wire(bindings)` participates in `WIRE`. Core supplies the concrete bindings already selected during `RESOLVE GRAPH`, together with the negotiated contract versions and technology-specific invocation handles/proxies.

The component consumes the supplied topology; it does not perform provider discovery or substitute a different provider behind Core's back. Any persistent topology change requires a new Core-mediated resolution.

### IV.2.6 `ready()`

`ready()` participates in the `READY` barrier. It allows the runtime to verify that local initialization and wiring are complete before activation. Typical work includes validating that mandatory injected bindings are present, building local in-memory structures derived from normalized configuration, or preparing resources that do not expose the component as operational yet.

`ready()` MUST NOT require a dependency to already be in `ACTIVE/RUNNING` merely to complete wiring readiness. Normal peer business activity begins only after the relevant readiness/activation policy allows it.

### IV.2.7 `activate()`

`activate()` performs the transition from `READY` to operational service. Depending on the technology/profile, activation may start listeners, schedulers, watchers, background tasks, external subscriptions, or otherwise permit normal capability invocation.

Successful activation places the runtime in `RUNNING`. Failure remains an activation failure and MUST NOT be reported as successful graph resolution or successful provisioning.

### IV.2.8 `suspend(reason)`

`suspend(reason)` requests a controlled temporary quiescence, for example because entitlement was lost, an administrative operation requires maintenance, or a runtime dependency became unavailable under product policy.

Suspension SHOULD stop admission of new work and allow in-flight work to reach a policy-defined safe boundary when practical. It does not delete persistent component/provider state. A product profile defines whether and how a suspended runtime may later resume without full re-instantiation.

### IV.2.9 `deactivate(reason)`

`deactivate(reason)` stops normal runtime behavior and releases transient operational resources. It may close listeners, stop timers/workers, disconnect runtime clients, flush non-authoritative buffers, and relinquish runtime handles.

Deactivation is not unprovisioning: Core-owned persistent configuration, provider-instance identity, bindings/configuration records, and preserved semantic data remain unless a later explicit lifecycle operation changes them.

### IV.2.10 `unprovision(context)`

`unprovision(context)` participates in `UNPROVISION`. It is the controlled inverse of provisioning for a particular application scope: Core removes or reconciles persistent state that existed because the component/provider was provisioned.

Before destructive changes, Core MUST check bindings, references, semantic-data ownership, and product retention policy. Unprovisioning MUST NOT silently purge authoritative semantic/project data merely because a package or provider instance is being removed. Package uninstall is a separate operation that may occur before or after preserved data is explicitly handled according to policy.

## IV.3 Core-mediated provisioning context

A provisioning hook MUST receive a Core-controlled context rather than unrestricted authority over Core persistence. The context may expose the component/provider namespace, staged configuration/data, diagnostics, and approved mutation primitives. Core remains responsible for validation, commit/rollback, stable identity, and reference integrity.

A hook MUST NOT create hidden provider-instance IDs, persist private binding topology, or bypass Core-owned configuration/data stores.

## IV.4 Entitlement is not a component self-check hook

Ordinary component implementation code MUST NOT be the authority for a lifecycle decision by exposing only:

```text
isLicensed() -> boolean
```

The component declares entitlement requirements; a trusted Core entitlement facility evaluates them. The component may receive the resulting effective entitlement/constraints as lifecycle context after Core has made the decision.

## IV.5 Pluggable entitlement evaluator

The entitlement facility MAY itself be implemented through a versioned capability/service contract so different products can use local licenses, subscriptions, enterprise servers, or test providers. Because entitlement participates before ordinary graph activation, the evaluator MUST be available through a trusted bootstrap/pre-resolution path and MUST NOT recursively depend on an entitlement decision that it is responsible for producing.

## IV.6 Hook failure semantics

Lifecycle hook failures MUST be attributed to the relevant lifecycle stage. Core SHOULD execute state-changing hooks transactionally, against staging state, or with an explicit recovery protocol. A failed hook MUST NOT leave Core claiming that a later lifecycle stage completed successfully.

---

# V. Provisioning Model

## V.1 Core-owned provisioning state

Provisioning MAY create:

- component-level configuration records;
- provider-instance records;
- configuration/data schema state;
- default values declared by schemas;
- migration markers;
- Core-owned binding placeholders or diagnostics.

A component MUST NOT create hidden persistent provider instances outside Core.

## V.2 Initial provider instance

When an enabled provider definition has no explicit instance and its component descriptor or the active product profile requires an initial instance, Core creates one using a Core-generated globally unique immutable ID.

The initial display name MAY be:

```text
default
```

but the name has no identity or binding semantics and MAY be changed. All persistent references use the instance ID.

## V.3 Unconfigured state

Provisioning an instance does not guarantee readiness. If mandatory configuration is missing, Core may keep the instance in a state conceptually equivalent to:

```text
PROVISIONED_UNCONFIGURED
```

Such an instance is visible and editable but is not an eligible provider candidate.

## V.4 Provisioning idempotence and recovery

Provisioning and migration steps SHOULD be idempotent or transactionally/recoverably orchestrated by Core. A failed provisioning attempt MUST NOT leave an instance appearing ready when persistent state is incomplete.

## V.5 Unprovisioning

Unprovisioning is a potentially destructive Core-owned operation. Core MUST detect bindings and persistent references to affected instances and data before destructive removal. Package uninstall and data purge are distinct actions.

---

# VI. Entitlement and Licensing

## VI.1 Separation from technical compatibility

A technically compatible component may be unentitled, and an entitled component may be technically incompatible. Core MUST represent those outcomes separately.

## VI.2 Declarative requirements

Components declare entitlement requirements in static metadata. They do not become the authority for deciding their own commercial validity through an ordinary `isLicensed()` implementation callback.

Conceptually:

```yaml
entitlements:
  - id: component-use
    entitlement: vendor.product.feature
    scope: component
```

## VI.3 Core-authoritative evaluation

Core evaluates entitlement through a trusted entitlement facility. The concrete source may be:

```text
local signed license
subscription service
enterprise license server
offline entitlement cache/lease
test/development provider
```

The entitlement source may itself be pluggable, but it MUST participate in a privileged bootstrap path that does not recursively depend on the entitlement decision it is responsible for producing.

## VI.4 Decision states

A baseline entitlement decision SHOULD distinguish at least:

```text
ALLOWED
DENIED
UNKNOWN
EXPIRED
ERROR
```

The decision MAY carry validity/lease metadata, reason codes, and standardized constraints.

## VI.5 Entitlement scopes

Entitlement MAY apply to:

```text
component
provider definition
provider instance
complete capability contract
```

Entitlement SHOULD NOT silently enable only selected operations of an otherwise advertised capability version. If functionality is independently licensed, it SHOULD normally be represented as a separate capability so that a resolved capability contract remains complete and trustworthy.

## VI.6 Constraints

Entitlement may produce constraints such as maximum active instances or product-defined resource limits. A constraint that Core is expected to enforce MUST have standardized semantics known to Core or a product-profile extension owned by Core. Opaque constraints interpreted only by untrusted component code are not sufficient for Core-level enforcement.

## VI.7 Graph eligibility

A provider instance is an eligible candidate only when all applicable conditions are satisfied. Conceptually:

```text
verified
+ contracts admitted
+ provisioned
+ configuration/data valid
+ entitlement allowed
+ lifecycle allowed
+ contract-version intersection
= eligible candidate
```

## VI.8 Dynamic revalidation

Entitlement can change while a component is installed or running. Core MUST support policy-driven revalidation.

When entitlement becomes invalid, Core SHOULD normally:

1. prevent new affected invocations once the invalid state becomes authoritative;
2. avoid arbitrarily corrupting an already-running transaction;
3. transition affected provider instances toward suspension/deactivation at a defined safe boundary;
4. preserve persistent configuration/data unless explicit unprovisioning/purge is requested;
5. expose a clear diagnostic state.

Security-critical policy MAY define stronger immediate revocation behavior.

---

# VII. Binding and Graph-Safety Rules

## VII.1 Resolved extension-instance graph is acyclic

Concrete Core-resolved bindings among extension provider/runtime instances MUST form a directed acyclic graph. Core MUST reject a proposed resolution if any binding would introduce a directed cycle.

A direct self-binding is the degenerate one-node cycle and is therefore invalid automatically.

```text
A1 -> A1                    INVALID
A1 -> B1                    potentially valid
A1 -> B1 -> A1              INVALID
A1 -> B1 -> A2              potentially valid
```

## VII.2 Declarative cycles remain legal

Component/provider-definition declarations are not the runtime instance graph. A component A may declare `provides X / consumes Y` while component B declares `provides Y / consumes X`. This is allowed.

If instance selection produces:

```text
A1 -> B1
B2 -> A2
```

the concrete graph is acyclic and valid. If it produces `A1 -> B1 -> A1`, resolution fails.

## VII.3 Cycle validation and diagnostics

DAG validation MUST occur before normal runtime activation. Core SHOULD report:

- the concrete provider-instance IDs and names participating in the cycle;
- the capability requirement corresponding to each edge;
- the binding source or selection rule that chose each edge;
- enough information for an administrator to select a different provider instance.

Products MAY later define an explicit advanced profile that permits carefully constrained cycles, but such behavior is outside the baseline specification.

## VII.4 Foundational Core facilities

A product may expose foundational Core-owned services during bootstrap/pre-resolution. Such services are not ordinary extension provider-instance nodes in the extension-instance DAG. A product/runtime profile MUST explicitly define this bootstrap boundary rather than implicitly bypassing cycle checks.

## VII.5 Observation-delivery exclusion remains necessary

Generic observation MUST exclude `_AAC.capability.observation` delivery calls themselves. This remains an explicit meta-capability safety rule and prevents observation from recursively generating observation events through the framework's own dispatch path.

## VII.6 Binding changes

Persistent topology changes are Core-owned. A component MUST NOT silently replace its provider binding in response to runtime state, entitlement failure, or local discovery. Core may re-resolve according to explicit policy, MUST preserve the DAG invariant, and must expose the reason/source of the new binding.

# VIII. Runtime States and Transitions

## VIII.1 Recommended diagnosable states

Products MAY choose different serialized names, but administration/runtime diagnostics SHOULD distinguish states equivalent to:

```text
INSTALLED
VERIFIED
PROVISIONED_UNCONFIGURED
PROVISIONED
UNENTITLED
RESOLVED
INSTANTIATED
WIRED
READY
ACTIVE
SUSPENDED
FAILED
DEACTIVATED
```

A single opaque `enabled/disabled` flag is insufficient as the sole lifecycle model.

## VIII.2 Suspension

Suspension means a previously activatable/active scope is temporarily unavailable because of entitlement, policy, dependency, health, or administrative state while persistent identity/configuration is retained.

## VIII.3 Deactivation

Deactivation stops normal runtime participation without necessarily unprovisioning persistent state. Re-activation may therefore be possible without recreating identities.

## VIII.4 Failure attribution

Core SHOULD attribute failure to the stage that failed:

```text
verification
contract admission
provisioning/migration
configuration validation
entitlement
resolution
instantiation
wiring
readiness
activation
runtime
```

This is essential for actionable diagnostics.

---

# IX. Upgrade, Downgrade, and Migration

## IX.1 Upgrade flow

Component upgrade may require configuration/data migration before activation. Core SHOULD stage, validate, and commit migrations before exposing the new runtime as active.

## IX.2 Downgrade

Downgrade is the same compatibility problem in reverse. Newer stored schemas MUST NOT be silently interpreted by an older component that does not declare them readable.

## IX.3 Entitlement changes are not migrations

Losing entitlement does not authorize destructive configuration or semantic-data migration/deletion. Persistent state remains preserved unless a separate explicit lifecycle operation requires modification.

---

# X. Uninstall and Data Preservation

## X.1 Uninstall is not purge

Removing a component package may leave Core-owned configuration and semantic extension data preserved so that:

- VCS/project meaning is not silently lost;
- reinstalling a compatible component can restore functionality;
- a newer/older machine can preserve data it cannot currently interpret.

## X.2 Explicit purge

Permanent deletion of component-owned semantic data or provider-instance configuration SHOULD require an explicit Core-mediated purge/unprovision operation with reference checks and diagnostics.

---

# XI. UI and Administrative Requirements

A Core administration UI SHOULD be able to expose separately:

```text
package installed/present
verification state
provisioning state
configuration validity
entitlement state and reason
provider instances and stable IDs
resolved bindings
selected contract versions
runtime activation state
last failure stage
```

This model applies equally to desktop, web, CLI, and headless administration surfaces.

---

# XII. Conformance Requirements

Conformance suites SHOULD cover at least:

- installation without activation;
- provisioning with generated stable provider-instance GUIDs;
- initial instance with mutable display name;
- unconfigured instance excluded from provider candidates;
- allowed/denied/expired/unknown entitlement;
- entitlement-dependent graph resolution;
- entitlement loss while running;
- preservation of in-flight operation semantics under ordinary entitlement expiry;
- direct self-binding rejection as a one-node cycle;
- same implementation with A -> B binding allowed;
- concrete provider-instance cycle rejection and diagnostic cycle paths;
- observation-delivery exclusion;
- failed provisioning rollback/recovery;
- upgrade/downgrade migration;
- uninstall without semantic-data purge;
- explicit purge/reference protection.

---

# XIII. Architectural Invariants

1. **Installation, provisioning, entitlement, resolution, and activation are different states.**
2. **Core owns persistent provider-instance identity and lifecycle topology.**
3. **Provisioning may exist without entitlement or activation.**
4. **An unconfigured or unentitled instance is not an eligible provider candidate.**
5. **Components declare entitlement requirements; Core is authoritative for entitlement enforcement.**
6. **Independently licensed functionality should be a separate capability rather than a partially disabled operation set inside one advertised capability contract.**
7. **A provider-instance-scoped requirement never binds to that same provider instance.**
8. **Distinct instances of the same implementation may bind to one another.**
9. **The resolved extension provider-instance binding graph is a DAG; declarative component/provider-definition cycles remain allowed.**
10. **`_AAC.capability.observation` delivery calls remain excluded from generic observation as a dedicated meta-capability safety rule.**
11. **Entitlement loss does not imply data deletion.**
12. **Uninstall does not imply purge.**
13. **Core should attribute lifecycle failures to the stage in which they occur.**
14. **The lifecycle model is technology- and presentation-host-independent.**
