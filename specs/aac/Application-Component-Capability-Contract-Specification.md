# Application Component Capability Contract Specification

**Status:** Draft public specification  
**Scope:** Capability contracts, operations, invocation identity, and generic invocation observation in application component architectures  
**Audience:** Core implementers, component/plugin authors, SDK authors, technology-profile authors, and third-party integration developers  
**Companion documents:** `Application-Component-Architecture-Governance.md`, `Application-Component-Architecture-Technical-Notes.md`, `Application-Component-Lifecycle-and-Provisioning-Specification.md`

---

# I. Purpose and Architectural Position

## I.1 Purpose

This specification defines the structure and runtime meaning of an Algites capability contract.

A capability is not merely a named interface. A canonical capability contract defines a versioned behavioral surface consisting of one or more **operations**, together with the schemas and semantics required to invoke those operations across component boundaries.

This specification also defines a generic **capability invocation observation** mechanism. Observation is intentionally modeled as another capability rather than as a product-specific debug hook.

The model is product-independent. A product profile may assign product-owned capability identifiers and reserved namespaces, but the concepts in this document are reusable across Algites products.

## I.2 Relationship to Governance

`Application-Component-Architecture-Governance.md` defines architectural invariants such as:

- Core-owned capability resolution;
- canonical contract identity;
- provider-instance binding;
- finite supported contract versions;
- provider selection before version negotiation;
- runtime dependency isolation.

This document refines the **capability contract** itself and defines how operation and observation metadata participate in that architecture. Component installation, provisioning, entitlement evaluation, activation, suspension, and unprovisioning are defined by `Application-Component-Lifecycle-and-Provisioning-Specification.md`.

## I.3 Relationship to technology profiles

This document is language-neutral.

A Java, Python, process, RPC, or other technology profile may represent the same contract differently, but MUST preserve the logical identities and semantics defined here.

For example:

```text
logical capability operation
        |
        +-- Java method on a shared interface
        +-- Python proxy method
        +-- RPC method/message pair
        +-- process IPC request
```

The technology binding is not the canonical identity. The capability contract is.

---

# II. Capability Contract Model

## II.1 Capability identity

A capability has a stable globally meaningful identifier.

Conceptually:

```yaml
capability:
  id: "algites.object-store"
  version: 3
```

Product profiles may reserve their own namespaces.

Third-party components SHOULD use identifiers from namespaces they control and MUST NOT claim a product-reserved namespace.

## II.2 Contract version

Each capability is versioned independently.

A version denotes a specific behavioral contract, including its operation set and operation semantics.

A component advertises only finite, explicitly known versions that it intentionally supports.

## II.3 Capability operations

A capability contract contains one or more **operations**.

Conceptually:

```yaml
capability:
  id: "algites.secrets.store"
  version: 2

operations:
  - id: get
    input_schema: "algites.secrets.store.get.request/v2"
    output_schema: "algites.secrets.store.get.result/v2"

  - id: put
    input_schema: "algites.secrets.store.put.request/v2"
    output_schema: "algites.secrets.store.put.result/v2"

  - id: delete
    input_schema: "algites.secrets.store.delete.request/v2"
    output_schema: "algites.secrets.store.delete.result/v2"
```

A capability MAY contain only one operation when that is the natural contract boundary.

A capability MUST NOT be forced to one operation merely to make operation identity globally unique.

## II.4 Operation identity is scoped to the capability contract

Operation identifiers are **not global identifiers**.

The canonical operation identity is the tuple:

```text
(capability id, capability version, operation id)
```

Therefore these are distinct operations:

```text
algites.vcs.repository / v1 / commit
example.database.transaction / v4 / commit
```

No global `AllKnownOperations` enum or equivalent registry is required or recommended.

The authoritative operation list belongs to each canonical capability contract definition.

## II.5 Operation IDs are stable within a contract version

An operation `id` is machine identity, not display text.

It SHOULD be concise, stable, and independent of implementation language.

For example:

```yaml
id: commit
```

A Java binding might expose:

```java
CommitResult commit(CommitRequest request);
```

but the Java method name is a technology binding of the canonical operation `id`; reflection-visible method identity is not the architecture's source of truth.

## II.6 Operation schema

Each operation SHOULD define, where applicable:

- input/request schema;
- output/result schema;
- structured error schema or error taxonomy;
- nullability/optionality rules;
- ordering guarantees;
- concurrency/threading expectations;
- cancellation semantics;
- timeout/blocking semantics;
- transaction semantics;
- idempotency semantics;
- resource ownership/lifetime;
- sensitive/redacted fields;
- compatibility expectations.

A contract MAY use shared DTO/schema definitions across several operations.

## II.7 Operation-set changes and capability versions

Adding, removing, renaming, or behaviorally changing an operation may require a new capability contract version.

A change is breaking whenever an existing consumer that correctly implements the old contract cannot safely use the new definition under the same version identity.

The same `(capability id, version, operation id)` MUST NOT silently acquire incompatible semantics.

## II.8 Canonical contract definition

The canonical contract bundle for a capability version MUST provide enough information for Core to know its operation surface before runtime binding.

At minimum Core must be able to establish:

```text
capability id
contract version
operation ids
canonical contract identity
technology binding metadata required by the active runtime profile
```

Where generic invocation/observation is supported, Core MUST also have normalized input/output schema information sufficient to produce the normalized invocation representation defined in this specification.

---

# III. Invocation Model

## III.1 Invocation identity

A capability invocation is an execution of one canonical operation against one resolved provider binding.

Conceptually:

```text
consumer
    -> capability id/version
        -> operation id
            -> selected provider instance
```

Each invocation SHOULD receive a Core-generated correlation identifier:

```text
invocation_id
```

The identifier is runtime correlation metadata and is not a persistent provider or domain identity.

## III.2 Invocation target must preserve the resolved instance DAG

A capability invocation is valid only through a binding that belongs to the Core-resolved acyclic extension provider-instance graph.

Conceptually:

```text
consumer instance A1 -> capability X -> provider instance A1    INVALID
consumer instance A1 -> capability X -> provider instance B1    potentially valid
```

Two distinct instances of the same implementation are distinct graph nodes and may bind to one another when doing so does not create a cycle elsewhere in the graph. The lifecycle specification owns the normative DAG validation rules.

## III.3 One authoritative result for SINGLE bindings

For a `SINGLE` consumer requirement, one provider instance is authoritative for the invocation.

Observation does not change this rule.

For example:

```text
consumer
    -> VCS/main-repository
        -> commit(...)
            -> one authoritative CommitResult
```

Attaching ten observers does not create ten VCS providers and does not create ten competing commit results.

## III.4 MULTIPLE capability semantics

A capability may intentionally define `MULTIPLE` consumer binding semantics where several provider instances are meaningful recipients.

Examples include:

```text
notifications
observation
tracing
auditing
metrics/event sinks
```

The exact aggregation, ordering, and failure semantics MUST belong to the capability contract.

## III.5 Nested invocations

An invocation may cause another capability invocation.

Core SHOULD make nested invocation relationships observable through correlation metadata where the runtime profile can provide them.

Conceptually:

```yaml
invocation_id: "..."
parent_invocation_id: "..."
```

This is useful for tracing a logical call graph without exposing implementation-private stack frames.

---

# IV. Normalized Invocation Representation

## IV.1 Purpose

Cross-technology tracing, auditing, diagnostics, and generic tooling must not depend on arbitrary Java objects, Python objects, private implementation classes, or process-local pointers.

Core therefore needs a technology-neutral normalized representation for generic invocation metadata.

## IV.2 Baseline normalized values

A baseline normalized representation SHOULD support at least:

```text
null
boolean
integer
decimal/string-encoded numeric where precision requires it
string
bytes
list
map/record
schema-identified structured value
```

A technology profile MAY define an efficient binary representation as long as its logical meaning is equivalent.

## IV.3 Contract-driven normalization

Normalization MUST be driven by the canonical capability contract, not by arbitrary reflection over provider implementation objects.

This allows Core to know which fields are:

- part of the public contract;
- optional;
- sensitive;
- structured DTO members;
- safe to expose to generic tooling.

## IV.4 Sensitive data

Operation schemas MUST be able to identify fields that must not be exposed verbatim through generic observation.

Conceptually:

```yaml
fields:
  token:
    type: string
    sensitive: true
```

Core MUST apply redaction before handing a normalized event to an observer that is not explicitly authorized for sensitive data.

The default generic representation SHOULD therefore contain:

```yaml
token: "<redacted>"
```

rather than the secret.

Redaction is a Core responsibility because generic observer plugins must not be trusted to redact values after receiving them.

---

# V. Generic Capability Invocation Observation

## V.1 Observation is a capability

Generic observation is modeled as a normal versioned capability.

The logical capability is referred to in this document as:

```text
capability observation
```

A product profile assigns the concrete capability identifier.

For the Algites Orchestrator profile, the intended built-in identifier is:

```text
_AAC.capability.observation
```

The observation capability has its own independent contract version.

Conceptually:

```yaml
provides:
  _AAC.capability.observation:
    versions: [1]
```

## V.2 Why observation is not per-capability

Generic observation MUST NOT require a parallel observation capability for every business capability.

Do not require patterns such as:

```text
vcs.status.observation
vcs.commit.observation
configuration.change-plan.observation
...
```

A generic tracer, auditor, or metrics collector needs a common invocation envelope, not a duplicate interface tree.

Domain-specific event capabilities MAY still exist where a product intentionally defines semantic events rather than generic invocation observation.

## V.3 Observation phases

Version 1 defines two logical phases:

```text
PRE
POST
```

`PRE` means Core has resolved the invocation and emits the observation event before the provider operation begins.

`POST` means the provider operation has completed with either success or failure and Core emits the resulting outcome.

A separate `ERROR` phase is not required in the baseline model; provider failure is represented by a `POST` event containing a failure outcome.

## V.4 PRE event

A PRE event SHOULD contain at least:

```yaml
invocation_id: "..."
parent_invocation_id: "..."   # optional
phase: PRE

capability:
  id: "example.vcs.repository"
  version: 1

operation:
  id: commit

consumer:
  component_id: "..."
  logical_scope_id: "..."     # optional

provider:
  component_id: "..."
  provider_definition_id: "..."
  instance_id: "..."

arguments:
  ... normalized and redacted contract values ...

timestamp: "..."
```

The exact serialized field names may be formalized by a schema specification, but the semantic information above is baseline contract material.

## V.5 POST event

A POST event SHOULD contain the same invocation identity and routing context plus the operation outcome.

Success example:

```yaml
invocation_id: "..."
phase: POST

capability:
  id: "example.vcs.repository"
  version: 1

operation:
  id: commit

arguments:
  ...

outcome:
  success: true
  result:
    ... normalized and redacted result ...
  error: null

duration_ms: 37
timestamp: "..."
```

Failure example:

```yaml
outcome:
  success: false
  result: null
  error:
    contract_error_id: "..."
    message: "..."
    details:
      ... normalized and redacted values ...
```

Technology-private stack traces MUST NOT be assumed to be part of the portable contract. A product may expose privileged diagnostic metadata separately.

## V.6 Observer operation

The observation provider exposes an operation conceptually equivalent to:

```text
observe(ObservationEvent) -> ObservationAck
```

`ObservationAck` confirms delivery/processing status only.

It MUST NOT contain:

```text
replacement arguments
replacement result
allow/deny decision
transaction decision
```

An observer is read-only with respect to the observed invocation.

## V.7 Observer failure isolation

Failure of an observer MUST NOT by default change the result, transaction status, or success/failure state of the observed provider invocation.

For example:

```text
VCS commit succeeds
Tracer/file fails because disk is full
```

The VCS commit remains successful. Core records an observer-delivery diagnostic separately.

A future policy profile may define stricter audit-delivery requirements, but such behavior must be explicit and must not be confused with baseline observation semantics.

## V.8 Observer versus interceptor

An observer reads an invocation; it does not control it.

Any future mechanism that may:

- rewrite arguments;
- veto execution;
- replace results;
- convert success into failure;

is an **interceptor/policy** mechanism and requires a separate capability contract with explicit transaction and ordering semantics.

It MUST NOT be added implicitly to the observation capability.

---

# VI. Observation Binding

## VI.1 Binding is Core-owned topology

Observer instance configuration answers:

```text
How does this observer behave?
```

Observation binding answers:

```text
Which invocations does this observer receive?
```

These are separate concerns.

For example, an observer instance may contain:

```yaml
id: "4b8e6d8d-6c7f-4de7-8e2f-..."
name: "stdout"
configuration:
  output: stdout
  pretty_print: true
```

while Core-owned topology contains:

```yaml
observer_instance_id: "4b8e6d8d-6c7f-4de7-8e2f-..."
observes:
  - capability: "example.vcs.repository"
    operations: [commit, push]
    phases: [PRE, POST]
```

The observer plugin MUST NOT silently invent or persist hidden observation targets outside Core-owned binding state.

## VI.2 Selector structure

A baseline observation selector has the conceptual structure:

```text
capability selector
optional finite version selector
optional operation selector
phase selector
```

Conceptually:

```yaml
observes:
  - capability: "example.vcs.repository"
    versions: [1, 2]
    operations: [commit, push]
    phases: [PRE, POST]
```

## VI.3 Operation selectors reference the capability contract

The `operations` field does **not** reference a global operation registry.

Every operation ID is interpreted in the scope of the selector's capability and selected capability version(s).

Thus:

```yaml
capability: "example.vcs.repository"
operations: [commit]
```

means:

```text
operation `commit` defined by example.vcs.repository
```

not every operation named `commit` in the system.

## VI.4 Omitted operations

If `operations` is omitted, the selector applies to every operation of the selected capability/version set.

Therefore:

```yaml
- capability: "example.vcs.repository"
  phases: [PRE, POST]
```

is equivalent to observing all operations of that capability.

## VI.5 Capability wildcard

A product MAY support a capability wildcard for generic tracers:

```yaml
- capability: "*"
  phases: [PRE, POST]
```

This means all **observable** admitted capability invocations, subject to mandatory exclusions and security policy.

Namespace/prefix selectors such as:

```text
example.vcs.*
```

MAY be supported by the binding schema if the product can define deterministic matching rules. They are convenience selectors, not capability identities.

## VI.6 Version selection

For semantic observers that interpret specific DTO fields, explicit finite contract versions SHOULD be declared.

Generic structural tracers MAY be allowed to omit the version selector and observe any currently admitted version that Core can normalize.

An observer MUST NOT infer semantics of a contract version it has not declared or otherwise been defined to understand.

## VI.7 Selector validation

When a selector names a concrete capability/version/operation, Core SHOULD validate it against the active or install-time contract catalog.

Invalid example:

```yaml
capability: "example.vcs.repository"
versions: [1]
operations: [nonexistent-operation]
```

Core should report a configuration error rather than silently ignore the unknown operation.

For wildcard selectors, matching is evaluated against the admitted runtime catalog.

## VI.8 Observation-delivery exclusion

The observation capability itself MUST NOT be observable through its own generic observation mechanism. This is an explicit meta-capability rule in addition to the resolved-instance DAG invariant; framework observation dispatch must not recursively generate observation events for its own delivery calls.

Otherwise:

```text
observation delivery
    -> observation invocation
        -> observation delivery
            -> ...
```

would recurse indefinitely.

Therefore the wildcard set is always conceptually:

```text
all observable capabilities
minus the observation capability itself
```

## VI.9 Additional exclusions

A product MAY exclude internal bootstrap, contract-admission, recovery, or security-sensitive operations from generic observation.

Such exclusions MUST be explicit product/runtime policy and SHOULD be diagnosable to administrators.

---

# VII. Runtime Dispatch Semantics

## VII.1 Baseline sequence

For an observed successful invocation, the logical sequence is:

```text
1. resolve consumer binding and operation
2. normalize/redact PRE arguments
3. emit PRE observation to matching observer instances
4. invoke the authoritative provider instance
5. capture success result
6. normalize/redact POST result
7. emit POST observation to matching observer instances
8. return the authoritative provider result to the consumer
```

For provider failure:

```text
1. ... PRE as above
2. provider fails
3. normalize/redact structured failure
4. emit POST observation with success=false
5. propagate the provider failure according to the original capability contract
```

Observer delivery failure is handled separately from provider failure.

## VII.2 Multiple observers

Several provider instances may provide the observation capability:

```text
Tracer / stdout
Tracer / vcs-log-file
Audit / default
Metrics / debug
```

Core may bind all matching instances under the observation capability's `MULTIPLE` semantics.

## VII.3 Ordering

Unless the observation contract version explicitly guarantees ordering, observers MUST NOT depend on a stable ordering relative to other observers.

A product implementation MAY invoke observers sequentially or concurrently.

The PRE/POST relationship is defined relative to the observed provider invocation, not as a global ordering among observer instances.

## VII.4 Delivery synchronization

A runtime profile may support synchronous or asynchronous observer delivery.

However:

- PRE event creation occurs logically before the provider operation;
- POST event creation occurs after the provider operation outcome exists;
- asynchronous delivery MUST preserve the event's phase and timestamps;
- observer backlog MUST NOT silently mutate the observed operation's result.

If a product exposes selectable delivery policy, that policy belongs to Core/runtime configuration and must be visible to diagnostics.

## VII.5 Timeouts and backpressure

Observer processing can be slow or unavailable.

The runtime SHOULD define:

- observer invocation timeout;
- queue/backpressure policy for asynchronous delivery;
- diagnostic/drop policy;
- shutdown flushing behavior where applicable.

These operational policies MUST remain separate from the semantic result of the observed operation unless a stricter explicitly documented product profile says otherwise.

## VII.6 Reentrancy

Delivery of the observation capability itself is never observed.

If an observer implementation performs ordinary capability calls while handling an event, those calls are normal invocations. To prevent accidental self-recursion, Core SHOULD suppress delivery of nested observations back to the same observer instance while that instance is processing the parent observation, unless an explicitly safe profile defines otherwise.

Other observer instances may still receive those nested invocations according to their bindings.

---

# VIII. Technology Binding Requirements

## VIII.1 Java

A Java capability contract may map operations to methods on a shared Core-owned contract interface.

Example:

```java
public interface VcsRepositoryV1 {
    CommitResult commit(CommitRequest request);
    PushResult push(PushRequest request);
}
```

The canonical contract metadata must still identify:

```text
example.vcs.repository / 1 / commit
example.vcs.repository / 1 / push
```

The observer MUST receive normalized contract data, not arbitrary provider-private Java objects.

Shared request/result DTOs follow the Core-owned class-loader identity rules defined in the Java runtime notes/profile.

## VIII.2 Python

A Python capability may expose consumer-local proxy methods corresponding to canonical operation IDs.

The proxy/dispatcher maps the logical operation to the selected provider interpreter.

Observation data should cross interpreter boundaries in normalized transferable form rather than by sharing arbitrary implementation objects.

## VIII.3 Process/RPC profiles

A process-isolated profile may map capability/operation identity directly to an IPC/RPC method identifier.

The same operation tuple remains authoritative regardless of wire encoding.

---

# IX. Descriptor and Contract-Bundle Considerations

## IX.1 Static operation discovery

All operation identities required for mandatory graph/runtime preparation MUST be knowable from static contract metadata without executing arbitrary provider business logic.

Core should not need to instantiate a component merely to ask which methods a capability has.

## IX.2 Contract-bundle admission

When an extension component introduces a capability contract unknown to the original Core build, its canonical contract bundle may define new operation IDs.

The flow is:

```text
plugin package contains contract bundle
    -> Core discovers contract
    -> Core validates/adopts canonical capability version
    -> operation set becomes known to active contract catalog
    -> binding and observation selectors may reference those operations
```

This permits third-party components to communicate through new capability contracts without requiring a global Core release containing every future operation name.

## IX.3 Unknown-at-build-time does not mean unknown-at-binding-time

A capability and its operations may be unknown when the Core binary was built.

They MUST NOT remain semantically unknown to Core at the point where Core establishes a governed cross-component binding.

Core must first admit enough canonical contract information to mediate the binding and technology runtime safely.

---

# X. AAC Framework Contract Profile

## X.1 Reserved framework namespace

Algites Application Components reserves:

```text
_AAC.*
```

for stable framework-owned cross-language contracts and identities. Products using AAC MUST use a separate namespace for product-specific business capabilities. Third-party components MUST use their own stable namespace.

## X.2 `_AAC.capability.observation/v1`

The framework generic observation contract is:

```text
_AAC.capability.observation / 1
```

with a provider operation conceptually named `observe` and Core-owned observation bindings selecting which invocations each observer instance receives.

## X.3 Generic tracing plugin example

A diagnostic component may declare an observer provider definition with several Core-managed instances:

```text
instance GUID A
name: stdout
configuration:
    output: stdout

instance GUID B
name: vcs-log-file
configuration:
    output: file
    path: ...
```

The corresponding Core-owned observation bindings may select all capabilities for the first instance and a product-specific VCS capability namespace for the second. The observer implementation remains reusable because it processes the normalized observation envelope rather than product-private runtime objects.

Product-specific namespaces such as Algites Orchestrator `_AO.*` belong in separate product profiles.

# XI. Security and Privacy Requirements

## XI.1 Least exposure

Generic observation is powerful and can expose operation arguments and results across component boundaries.

Core MUST treat observer attachment as a privileged topology/configuration action according to product security policy.

## XI.2 Redaction before dispatch

Sensitive contract fields MUST be redacted or omitted before observation dispatch unless an explicitly privileged observer context is authorized to receive them.

## XI.3 No private implementation leakage

Observation MUST NOT expose:

- provider-private implementation objects;
- raw memory identities;
- private dependency types;
- arbitrary reflection dumps;
- secrets merely because they occur in implementation state.

Only canonical contract data and explicitly allowed runtime metadata belong in the generic envelope.

## XI.4 Diagnostic metadata

A product may expose additional privileged diagnostic fields, but such fields should be separately classified and must not silently become part of the portable capability contract.

---

# XII. Conformance Requirements

## XII.1 Capability-contract tests

Conformance tooling SHOULD test:

- unique operation IDs within each capability/version;
- stable capability/version identity;
- valid request/result schemas;
- declared error semantics;
- sensitive-field metadata;
- technology-binding completeness;
- canonical contract digest/identity consistency.

## XII.2 Observation-selector tests

Test at least:

- one capability / all operations;
- one capability / selected operations;
- selected finite versions;
- PRE only;
- POST only;
- PRE + POST;
- wildcard capability selector;
- invalid operation selector rejection;
- exclusion of observation capability itself.

## XII.3 Runtime observation tests

Test at least:

- PRE arrives before provider execution logically begins;
- POST success contains normalized result;
- POST failure contains normalized error;
- observer failure does not alter provider result;
- several observer instances can receive one invocation;
- sensitive values are redacted;
- observation delivery does not recursively observe itself;
- nested capability calls carry correlation where supported;
- provider remains singular/authoritative for a `SINGLE` business capability.

## XII.4 Dynamic contract tests

Test a plugin-supplied capability unknown to the original Core build:

```text
plugin A supplies canonical capability X/v1 with operations [foo, bar]
plugin B consumes X/v1
observer selects X/foo
```

Core must:

1. admit X/v1;
2. know `foo` and `bar` from the canonical contract;
3. resolve the provider/consumer binding;
4. validate the observation selector;
5. deliver normalized PRE/POST events for `foo`.

No global Core operation enum should need modification.

---

# XIII. Architectural Invariants

1. **A capability version defines one or more canonical operations.**
2. **Operation IDs are scoped to capability/version, not globally.**
3. **The canonical operation identity is `(capability id, version, operation id)`.**
4. **Operation schemas belong to the capability contract.**
5. **Core can admit new capability contracts and operation sets supplied by extension components.**
6. **Generic observation is a normal independently versioned capability.**
7. **Observation bindings are Core-owned topology, separate from observer-instance configuration.**
8. **Observation selectors may filter by capability, version, operation, and PRE/POST phase.**
9. **Operation selectors are interpreted through the selected capability contract, never a global operation enum.**
10. **Observers are read-only and do not alter the authoritative invocation result.**
11. **Observer failure is isolated from observed-provider success/failure by default.**
12. **The observation capability never observes itself.**
13. **Sensitive contract values are redacted before generic observer dispatch unless explicitly authorized.**
14. **Generic observation uses canonical normalized contract data, not private implementation objects.**
15. **Product-specific capability namespaces, such as Orchestrator `_AO.*`, are profiles of the general model rather than changes to the general architecture.**
