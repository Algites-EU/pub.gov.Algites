# Application Component Architecture Technical Notes

**Status:** Public technical companion / design notes  
**Companion documents:** `Application-Component-Architecture-Governance.md`, `Application-Component-Capability-Contract-Specification.md`, `Application-Component-Lifecycle-and-Provisioning-Specification.md`
**Normative relationship:** Explanatory unless a section is promoted into a formal runtime/profile specification

---

# I. Why “Component Architecture”

## I.1 Terminology

The architecture started as a plugin-system design, but the capability model is broader.

The useful unit is now:

```text
component
```

because:

- Core participates in the same capability graph;
- plugins are extension components;
- components can consume functionality from Core or other components;
- provider instances are runtime/configuration objects coordinated by Core.

Therefore **Component Architecture** is more precise than **Plugin Architecture**.

## I.2 Plugin remains a packaging/deployment concept

A plugin is still a useful term.

It means an independently installable extension package/component.

The architectural graph, however, should not treat “Core versus plugin” as the main semantic distinction.

The main distinctions are:

```text
consumer
provider definition
provider instance
capability contract
Core coordinator
```

## I.3 Applicability beyond desktop applications

The component model is not inherently a desktop-application architecture. Its essential requirements are a coordinating Core, capability contracts, Core-owned binding/lifecycle state, and a technology profile capable of isolating component runtimes and mediating calls.

It can therefore be applied to:

```text
desktop applications
web applications
server/daemon applications
CLI tools
background workers and services
headless automation hosts
```

The strongest portability is obtained when **presentation ownership remains in Core**. A component can contribute declarative presentation intent through capabilities, for example:

```text
forms
fields and validation metadata
tables and data models
commands/actions
panels/view models
notifications
```

A desktop Core may render those descriptions as Qt/PySide widgets. A web Core may render the same logical contribution as HTML/DOM/client-side views. The component does not need to own the concrete GUI toolkit or browser framework.

This is analogous to the recommended Python/PySide profile: Core owns the UI runtime and event loop, while isolated components contribute models and behavior through contracts. For a web product, Core can similarly own HTTP routing, authentication, session state, HTML/client rendering, and frontend asset policy while components provide capability-driven data and declarative UI contributions.

Arbitrary component-supplied native widgets, JavaScript bundles, routes, templates, or frontend frameworks are possible only under an explicit product/technology profile because they weaken dependency isolation and Core ownership. The general architecture does not require or assume such direct UI injection.

The same component architecture can therefore support different presentation hosts without changing its capability, instance, binding, provisioning, or entitlement model.

## I.4 Reference Application Component Library

The architecture should be implemented once in a reusable framework rather than independently in each product. The reference implementation is **Algites Application Components (AAC)**.

A Python reference package may expose modules conceptually such as:

```text
appcomponents.descriptor
appcomponents.contracts
appcomponents.catalog
appcomponents.instances
appcomponents.binding
appcomponents.resolver
appcomponents.lifecycle
appcomponents.observation
appcomponents.entitlement
appcomponents.runtime
```

The reusable library should own generic mechanics such as descriptor parsing, contract admission, stable provider-instance identity, binding resolution, DAG validation, lifecycle coordination, normalized invocation/observation envelopes, entitlement interfaces, diagnostics, and technology-profile extension points.

Products should supply adapters and product contracts rather than fork those mechanics. A Java implementation may realize the same architecture with different runtime machinery while preserving the same contract identities and governance rules.

The stable framework-owned cross-language namespace is `_AAC.*`; implementation package names are not part of that public identity.

---

# II. Capability Model Rationale

## II.1 Why capability negotiation instead of release gates

A conventional system often says:

```text
Plugin 7 requires Core >= 12.3
```

The Application Component model instead prefers:

```text
consumer supports [1,2,3]
provider supports [2,3,4]
Core active catalog knows [1,2,3]

selected = 3
```

This better reflects actual compatibility.

## II.2 Why Core must own every negotiated contract identity

Allowing two extension components to use a contract that Core has not admitted would undermine Core's role as:

- resolver;
- binding authority;
- compatibility-policy owner;
- diagnostics owner;
- runtime mediator.

It is also technically unsafe in runtimes with explicit type identity, especially Java.

Therefore:

```text
binding version =
    consumer
  ∩ provider
  ∩ Core active contract catalog
  ∩ lifecycle policy
```

## II.3 Built-in versus dynamically admitted contracts

The active catalog does not have to be identical to the contracts originally compiled into a Core release.

It may be:

```text
active contract catalog =
    built-in contract definitions
  + validated dynamically admitted contract bundles
```

This gives an older Core a controlled way to mediate a newer capability contract without allowing plugins to create private, incompatible definitions behind Core.

A dynamically supplied contract must become Core-owned before graph binding.

## II.4 Core knowledge is not Core implementation

Core may know:

```text
algites.object-store/v4
```

because it owns/admitted:

```text
contract metadata
binding types or schemas
DTO definitions
conformance identity
```

without exposing an object-store provider.

An S3 extension component may be the only provider.

## II.5 Canonical contract identity

The same:

```text
capability id + version
```

must not mean two different definitions.

If Plugin A and Plugin B both bundle `algites.object-store/v4`, Core should verify that they identify the same canonical contract artifact/definition.

If the definitions conflict, graph resolution must fail rather than silently picking one.

## II.6 Why finite support sets are important

A component cannot safely claim:

```text
>= 2
```

because future v17 is unknown.

Finite sets make support intentional and testable.

## II.7 Why capabilities can connect extension components

The desirable dependency is:

```text
Deployment consumes algites.object-store
```

not:

```text
Deployment requires S3 Plugin
```

Any conforming provider can satisfy the capability.

## II.8 Operations belong to the capability contract

A capability should be small and cohesive, but it does not need to be artificially reduced to exactly one method.

For example, a secret-store capability may naturally contain:

```text
get
put
delete
exists
```

The operation names are not global. Their full identity is:

```text
capability id + capability version + operation id
```

This lets unrelated capabilities use the same local operation name and lets plugin-supplied contracts introduce new operations without modifying a Core-global enum.

The canonical contract definition should therefore describe the operation list and the normalized request/result/error schemas used by generic tooling.

---

# III. Configuration Architecture

## III.1 Core-owned persistence

The recommended model is that extension components do not own arbitrary persistent config files.

Instead:

```text
component descriptor
    defines configuration schema

Core
    owns persistence
    validates
    versions
    migrates
    presents UI
    injects normalized configuration

component runtime
    consumes normalized configuration
```

This removes storage-format and repository-layout knowledge from component code.

## III.2 Why this matters

If every component invents its own:

```text
file path
YAML format
JSON file
registry key
SQLite database
migration strategy
```

the product cannot consistently provide:

- VCS integration;
- backup;
- migration;
- UI;
- validation;
- portability;
- auditing.

Centralized normalized configuration avoids that fragmentation.

## III.3 Proposed configuration scopes

At least two persistent configuration scopes are useful:

### Component scope

One configuration object for the component itself.

### Provider-instance scope

One configuration object per Core-created provider instance.

These are separate from capability binding configuration.

## III.4 Uniform provider-instance model

The earlier distinction between `SINGLETON` and `MULTI_INSTANCE` provider definitions is unnecessary and creates two lifecycle/binding models for the same architectural concept.

The simpler invariant is:

```text
provider definition / implementation
    -> one or more Core-managed provider instances
```

Every concrete provider used by a binding is an instance. A definition that currently has one instance is not a different type from a definition that has several.

This is useful even for apparently singleton implementations because a second independently configured use can appear later without changing the provider-definition model.

For example, one Git implementation may have:

```text
instance A: Source repository
instance B: Deployment results repository
```

with different repository URLs, credentials, branches, proxies, or other stable configuration. Different consumers or requirements bind to the appropriate instance.

## III.5 Core creates provider instances

For every provider definition:

```text
user/admin
    asks Core to create instance

Core:
    generates immutable globally unique GUID/UUID-style instance id
    stores name/description
    stores instance config
    makes instance visible to resolver

component:
    receives instance identity + normalized config
    constructs runtime implementation
```

The provider component does not invent persistent IDs on its own.

If no instance exists yet, Core creates the initial instance. Its initial display name may be `default`, but `default` is not a reserved identity or implicit binding target. The name may be changed without affecting references. When mandatory configuration is missing, the instance can remain unconfigured/non-ready; the uniform identity model does not require an invalid instance to be activatable.

## III.6 Suggested provider-instance record

Conceptually:

```yaml
id: "6dca4e9b-4d4e-4a67-9e3e-70c83aad75c1"
name: "Production S3"
description: "Primary production object store"

owner_component: "eu.algites.component.s3"
provider_definition: "s3-object-store"

configuration:
  schema: "eu.algites.s3.object-store.config"
  schema_version: 3
  value:
    endpoint: "..."
    region: "eu-central-1"
```

The exact serialization is product-specific.

## III.7 Capability binding is a separate Core object

A Core binding might conceptually be:

```yaml
consumer:
  component: "eu.algites.component.git"

capability:
  id: "algites.secrets.store"

provider_instance_id: "62c0d4c3-3209-4ba5-850e-6fcd0e758f54"
```

This record must not be confused with the Git component's private configuration.

The binding belongs to the system graph. It references the immutable provider instance ID, not a mutable display name such as `Corporate Vault` or `default`.

### Example: two Git instances

The same Git provider definition can be instantiated twice:

```yaml
provider_definition: git
instances:
  - id: "0d5a1c6d-c33b-40fb-a463-85ec378004f4"
    name: "Source repository"
    configuration:
      repository: "git@git.algites.internal:application.git"
      credentials: "corporate-git"
      branch: "main"

  - id: "aa023174-8517-4ce0-b820-26c40dc8cbc8"
    name: "Deployment results"
    configuration:
      repository: "git@github.com:algites/deployment.git"
      credentials: "github-token"
      branch: "production"
```

A source-management requirement may bind to the first instance while a deployment-result requirement binds to the second. Stable repository identity and long-lived configuration belong to the instance; per-call values such as a requested revision or local checkout path remain invocation parameters.

## III.8 Configuration UI

A component administration page can combine several Core-owned views:

```text
Component settings

Consumes:
    algites.secrets.store
        provider: Corporate Vault
        contract: v2
        binding source: workspace default

Provides:
    algites.vcs.status [v1,v2,v3]

Provider instances:
    ...
```

The provider line may be editable or read-only.

The important point is that it is visible and sourced from Core resolution state.

---

# IV. Persistent Semantic Data

## IV.1 Configuration versus semantic data

Configuration answers:

```text
How should this component behave?
```

Semantic extension data answers:

```text
What additional persistent information does this component add to the project/model?
```

Those are different.

Example:

```text
Git component config:
    remote timeout = 30 s

Git semantic extension data:
    Core object X maps to repository object Y
```

## IV.2 Why Core should persist semantic extension data

If semantic data belongs to project meaning, storing it in arbitrary component-private files creates problems:

- Core cannot preserve it when plugin absent;
- VCS behavior becomes inconsistent;
- references to Core objects become fragile;
- migrations become component-specific side effects;
- a different machine may lack the expected directory structure.

A generic Core-managed extension-data store is safer.

## IV.3 Suggested envelope

Conceptually:

```yaml
owner_component: "eu.algites.component.foo"
data_type: "core-object-mapping"
schema_version: 4

subject:
  core_reference: "..."

payload:
  ...
```

Core can preserve the envelope even without understanding the payload semantics.

## IV.4 Reference discipline

References from extension data into Core domain data should use stable Core-defined IDs/reference objects.

Do not use:

```text
file path to entity
display name
GUI row number
implementation class name
```

unless the specific product explicitly defines such a value as stable identity.

## IV.5 VCS scenario

Suppose:

```text
Machine A:
    Foo component v8
    writes schema 5

commit to Git

Machine B:
    Foo component v6
    understands schemas [2,3,4]
```

Machine B should not attempt to parse schema 5 as schema 4.

Core should:

```text
preserve schema 5
mark it unsupported by installed Foo version
block affected feature/scope
show diagnostic
```

If Machine B later installs Foo v8, the same preserved data becomes usable.

## IV.6 Component absent

If a project contains extension data and the owning component is not installed, Core should treat it as opaque but preserved.

This is analogous to retaining unknown-but-well-framed data.

Explicit user deletion is different from automatic cleanup.

## IV.7 Runtime state/cache

Rebuildable state should normally live in a different Core facility, outside project/VCS semantics.

Examples:

```text
cache
temporary index
last API response
temporary synchronization cursor
performance hints
```

If loss changes project meaning, it is not merely cache.

---

# V. Configuration and Data Migration

## V.1 Separate schema version from component version

Do not use:

```text
component version 8.4
```

as the only identifier for persisted data format.

Instead use:

```text
configuration schema id/version
data type id/schema version
```

This lets a component release leave schemas unchanged or migrate several schema generations.

## V.2 Upgrade sequence

Preferred flow:

```text
install new component package

Core reads descriptor:
    readable schema versions
    current writable version
    migration steps

Core inspects stored configuration/data

if migration required:
    stage migration
    validate
    commit

then activate
```

## V.3 Migration execution

The transformation code may be supplied by the component package.

However, Core should invoke it in a controlled migration mode and own:

```text
source snapshot
target staging
validation
commit/rollback
diagnostics
```

The component should not mutate persistent files directly during migration.

## V.4 Configuration cannot be migrated

If configuration cannot be migrated:

```text
preserve old configuration
block affected activation
offer explicit reset/reconfiguration
```

Only after explicit user action should the old configuration be discarded.

## V.5 Semantic data cannot be migrated

This is more serious than ordinary configuration.

Core should preserve the data and require one of:

```text
install compatible component
restore compatible VCS revision
provide migration-capable version
explicitly purge data
```

Automatic “reconfigure from scratch” may not be meaningful.

## V.6 Downgrade

Downgrade is the same compatibility problem in reverse.

A component version must not interpret a newer schema simply because it has the same data type ID.

Explicit readable-version declarations are required.

---

# VI. Provider Resolution

## VI.1 Binding hierarchy

Recommended order:

```text
consumer override
global/workspace binding
qualifier-based rule
sole compatible provider
platform default
user/admin selection
```

## VI.2 Why plugins should not select providers themselves

If the consumer scans providers and selects one internally:

- the UI may not know what is in use;
- configuration cannot reproduce the graph;
- VCS cannot transport the same topology;
- migration cannot reason about references;
- administrator policy can be bypassed.

Therefore Core resolves and injects the provider.

## VI.3 Provider instance versus implementation

This distinction applies universally, not only when several instances happen to exist.

Example:

```text
implementation:
    HashiCorp Vault provider

instances:
    id 62c0...  name Corporate Vault
    id 91d8...  name Test Vault
```

The resolver never binds directly to the implementation definition. It binds to a concrete provider instance ID.

If only `Corporate Vault` existed, the rule would be identical.

## VI.4 Contract negotiation after provider selection

Provider version does not rank provider quality.

Core selects provider first, then contract.

## VI.5 Provider-instance multiplicity is not consumer cardinality

A provider definition may always have several instances. That does not mean a consumer call is automatically broadcast to all of them.

`SINGLE` and `MULTIPLE` describe the **consumer requirement/binding**:

```text
SINGLE
    exactly one provider instance is bound

MULTIPLE
    a resolved collection of provider instances is bound
```

For an operation that must produce one authoritative or transactional result, `SINGLE` is normally appropriate even when many candidate instances exist.

Observer-style functionality is a natural `MULTIPLE` case. For example, a dedicated invocation-observer capability could bind both:

```text
Tracer / stdout
Tracer / vcs-log-file
```

Each tracer is a separate configured instance. The observed VCS operation itself can still remain `SINGLE`, preserving one authoritative VCS result. This avoids pretending that a tracer is a second VCS implementation merely because it observes the same operation.

## VI.6 Why the resolved instance graph is a DAG

A direct self-binding is merely the smallest invalid cycle. The stronger baseline rule is that the entire resolved extension provider-instance dependency graph is acyclic.

This does **not** require the component/provider-definition declaration graph to be acyclic. For example, A may provide X and consume Y while B provides Y and consumes X. If actual bindings resolve `A1 -> B1` and `B2 -> A2`, the runtime graph is still a DAG.

Rejecting concrete cycles simplifies activation and shutdown ordering, transaction reasoning, deadlock analysis, diagnostics, and invocation causality. If a future use case requires safe instance cycles, it should be introduced deliberately as a separately specified profile rather than being accidental baseline behavior.

## VI.7 Observation as a general capability

The observer idea generalizes cleanly if observation itself is a versioned capability.

A product-level observer provider can receive a normalized envelope such as:

```text
invocation id
phase PRE | POST
capability id/version
operation id
consumer identity
provider instance identity
normalized arguments
normalized result or failure for POST
runtime timing/correlation metadata
```

This avoids creating one parallel `...observation` interface for every business capability.

## VI.8 Why operations must be contract metadata

An observation binding may want to say:

```yaml
capability: example.vcs.repository
operations: [commit, push]
phases: [PRE, POST]
```

The strings `commit` and `push` are interpreted by looking at the canonical `example.vcs.repository` contract. There is no global list containing all operations from every capability.

This becomes especially important for a capability introduced by a third-party plugin. Once Core admits that capability's canonical contract bundle, Core also learns its operation set and can validate observation selectors without having known those operation names at build time.

## VI.9 Observer configuration versus observation binding

A tracer instance might contain:

```text
output = stdout
pretty_print = true
```

while Core-owned topology separately says:

```text
observe every capability, PRE + POST
```

or:

```text
observe example.vcs.repository/commit, POST only
```

Keeping these separate follows the same rule as normal provider binding: a component defines how an instance behaves, while Core owns where the instance participates in the system graph.

## VI.10 Read-only semantics

A generic observer should not return modified arguments, modified results, or an allow/deny decision.

Conceptually:

```text
observe(event) -> acknowledgement only
```

If an observer fails, Core records an observer diagnostic. The original operation's success or failure remains the provider's authoritative result.

An interceptor capable of modifying or vetoing calls would be a distinct architectural contract.

## VI.11 PRE and POST

Two phases are sufficient for the baseline model:

```text
PRE
POST
```

`POST` includes either a success result or structured failure, so a separate ERROR phase is unnecessary unless a future contract has a concrete need for one.

## VI.12 Recursion and sensitive data

The generic observation capability `_AAC.capability.observation` must never observe observation-delivery calls. This remains an explicit meta-capability safety rule in addition to the resolved extension-instance DAG invariant: framework observation dispatch must not recursively generate observation events for its own delivery calls.

A wildcard therefore means conceptually:

```text
all observable capability invocations
minus observation delivery itself
```

Core must also normalize and redact sensitive contract fields before dispatch. A stdout tracer should never accidentally print a password or access token merely because the observed business operation accepts one.

The detailed normative model is specified in `Application-Component-Capability-Contract-Specification.md`.

---

# VII. Resolution, Wiring, and Cycles

## VII.1 Read all descriptors first

Static descriptors let Core compute the graph without executing business logic.

## VII.2 Declarative cycles versus resolved instance cycles

The declaration:

```text
A provides X, consumes Y
B provides Y, consumes X
```

is valid because it describes capability relationships, not concrete runtime edges.

After instance selection, Core may obtain an acyclic graph such as:

```text
A1 -> B1
B2 -> A2
```

That is valid. A concrete graph such as `A1 -> B1 -> A1` is rejected before activation.

## VII.3 Staged activation

Discovery, verification, contract admission, provisioning, entitlement evaluation, resolution, instantiation, wiring, readiness, activation, suspension, and shutdown are separate lifecycle concerns. The normative stage model is defined by `Application-Component-Lifecycle-and-Provisioning-Specification.md`.

## VII.4 Construction rule

Constructors/bootstrap creation should not make mandatory business calls to peer components.

Provider objects should be constructible before the graph is operational.

## VII.5 Binding object

Conceptually a Core binding contains:

```text
consumer identity
capability id
contract version
provider instance id
invocation endpoint/reference
```

Its runtime representation is technology-specific.

---

# VIII. Dependency Isolation

## VIII.1 Why it is mandatory

Without isolation:

```text
A -> Library L v1
B -> Library L v2
```

can break an otherwise valid capability graph.

## VIII.2 Why not resolve one global package closure

Doing so would effectively embed package-manager semantics for every supported ecosystem into Core.

Isolation is a simpler architectural contract.

## VIII.3 Not a security sandbox

Class loaders and subinterpreters isolate dependencies/state but should not be sold as a hostile-code sandbox.

---

# IX. Python Runtime Notes

## IX.1 Proposed baseline: CPython 3.14+

The current preferred Python baseline is **CPython 3.14 or newer**.

Python 3.14 added the public standard-library module:

```python
concurrent.interpreters
```

which exposes multiple interpreters as an application-level facility.

Historically subinterpreters existed through lower-level APIs, but 3.14 is a much cleaner baseline for a reusable public runtime profile.

## IX.2 Private environment plus private interpreter

A private virtual environment or `site-packages` directory alone is not sufficient if all components run in one interpreter.

The proposed profile therefore combines:

```text
component-private dependency path
+
component-private interpreter
```

## IX.3 Proposed runtime structure

```mermaid
flowchart TB
    MAIN["Main CPython Interpreter<br/>Core"]
    DISP["Core Capability Dispatcher"]
    PA["Component A Interpreter"]
    PB["Component B Interpreter"]
    AE["A private site-packages<br/>Library L v1"]
    BE["B private site-packages<br/>Library L v2"]

    MAIN --> DISP
    DISP --> PA
    DISP --> PB
    PA --> AE
    PB --> BE
```

## IX.4 Interpreter isolation characteristics

Each interpreter has its own execution context, including import state and builtins.

That allows two component interpreters to import different versions of a pure-Python package without sharing one `sys.modules` namespace.

The interpreters still inhabit one OS process, so this is not a security boundary.

## IX.5 Core active contract catalog representation in Python

The Python Core should own the canonical schema/binding definition for every capability version in its active catalog.

The active catalog may include:

```text
built-in contract definitions
+
validated contract bundles supplied by extension packages
```

A newly admitted contract may provide:

- canonical DTO/schema definitions;
- proxy/dispatcher metadata;
- generated or loadable Python bindings;
- conformance identity.

A component may declare support for a newer capability version, but that version cannot be selected until Core has admitted the corresponding canonical contract definition.

Unlike Java, Python does not require all isolated interpreters to share one `Class` object. Nevertheless, they must still operate from the same Core-owned canonical contract definition and dispatch semantics.

## IX.6 Consumer-side proxy## IX.6 Consumer-side proxy

Cross-interpreter components should not exchange arbitrary implementation objects.

Consumer interpreter:

```text
ObjectStoreV3Proxy
```

Core dispatcher:

```text
selected provider instance
selected contract version
```

Provider interpreter:

```text
S3ObjectStoreV3 implementation
```

## IX.7 Core-provided context

Conceptually:

```python
class ComponentContext:
    def capability(self, capability_id: str):
        ...
```

For `SINGLE` consumption, the returned proxy is already bound to the provider selected by Core.

A component should not receive unrestricted discovery authority merely to choose another provider.

## IX.8 Configuration delivery

Core can pass a normalized configuration object into the component interpreter at bootstrap.

The component does not need to know the physical persistence format.

Provider instances can be instantiated with:

```text
instance id
name
description
normalized instance config
resolved consumed-capability proxies
```

## IX.9 Data delivery

Core can deliver only extension-data schemas the installed component has declared readable.

Unsupported newer data remains outside the component interpreter and is preserved by Core.

## IX.10 Cross-interpreter data

Capability contracts should prefer predictable transferable/serializable data.

Good candidates include:

```text
None
booleans
numbers
strings
bytes
structured immutable records
explicit DTO serialization
```

Arbitrary implementation objects are a poor contract boundary.

## IX.11 Native extension limitation

This remains the major constraint.

Not every CPython native extension supports use from multiple interpreters.

A formal profile should classify dependencies roughly as:

```text
pure Python:
    normally acceptable

standard-library/runtime extension:
    validated with supported CPython baseline

third-party native extension:
    explicit multi-interpreter validation required

known incompatible:
    rejected for this profile
```

## IX.12 Conformance for native dependencies

Tests should create multiple isolated component interpreters and repeatedly import/use/destroy native-extension users to detect:

```text
process-global state leakage
crashes
interpreter destruction problems
unsupported multiple-interpreter assumptions
```

## IX.13 GUI recommendation

Keep PySide/Qt in the Core interpreter for the baseline profile.

Extension components contribute:

```text
commands
form descriptions
panel models
data models
callbacks
notifications
```

through capabilities.

Arbitrary private `QWidget` objects across interpreters should require a dedicated future profile.

## IX.14 Memory/performance

Subinterpreters are lighter than full processes in many cases but not free.

The profile should benchmark:

```text
startup
per-component interpreter memory
duplicate module memory
cross-interpreter dispatch cost
shutdown/restart
```

before publishing hard operational expectations.

---

# X. Java Runtime Notes

## X.1 One extension-component class loader per component

The natural Java model is a dedicated class loader for each extension component.

The contract layer is separate from plugin-private implementation libraries:

```mermaid
flowchart TB
    PLATFORM["JDK Platform"]
    BUILTIN["Core Built-in Contract Layer"]
    ACTIVE["Core-owned Active Contract ClassLoader"]
    CORE["Core Runtime"]
    PA["Component A ClassLoader"]
    PB["Component B ClassLoader"]
    AE["A implementation + private deps<br/>L v1"]
    BE["B implementation + private deps<br/>L v2"]
    CB["Dynamically admitted contract JARs"]

    PLATFORM --> BUILTIN
    BUILTIN --> ACTIVE
    CB --> ACTIVE

    ACTIVE --> CORE
    ACTIVE --> PA
    ACTIVE --> PB

    PA --> AE
    PB --> BE
```

The exact loader hierarchy may vary, but every capability type used across component boundaries must resolve from one Core-owned shared contract domain.

## X.2 Java runtime type identity

In Java, a class or interface is determined at runtime by:

```text
binary class name
+
defining ClassLoader
```

Therefore:

```text
PluginAClassLoader -> Interface_v4
PluginBClassLoader -> Interface_v4
```

creates two distinct runtime types if each loader defines its own copy.

Even byte-for-byte identical class files are not interchangeable when defined by different class loaders.

This is the technical reason a cross-component capability contract cannot remain private to each component.

## X.3 Parent-first delegation is the desired baseline

The normal Java class-loader delegation model is parent-first:

```text
loadClass(name):
    1. return already loaded class if present
    2. ask parent
    3. if parent cannot load it, use findClass()
```

Algites should preserve parent-first behavior for protected shared contract namespaces.

For a contract already present in the Core-owned active contract layer:

```text
Component loader asks for VcsStatusV3
->
parent has VcsStatusV3
    ->
the shared Core-owned VcsStatusV3 Class object is used
```

Plugin-private dependency namespaces may use a different child/private policy when required, but shared Algites contract packages must never be shadowed.

## X.4 Why plugin-local fallback is insufficient for cross-component contracts

Suppose an old Core contains only:

```text
Interface_v1
Interface_v2
Interface_v3
```

and Plugin A bundles `Interface_v4`.

If Plugin A's private loader simply falls back to its local `Interface_v4`, Plugin A can potentially load a private implementation:

```java
final class ProviderA implements InterfaceV4 {
    ...
}
```

But Plugin B doing the same obtains a different runtime `InterfaceV4` type.

Therefore:

```text
PluginA::InterfaceV4 != PluginB::InterfaceV4
```

and a provider object from A cannot safely be passed to B as B's private `InterfaceV4`.

This local fallback can make a class loadable **inside one plugin**, but it does not create cross-component interoperability.

## X.5 Contract JARs may be distributed with a component package

Where a version number is encoded in a physical file or artifact name, these specifications use an underscore before the complete version suffix, for example `algites-vcs-status_4.jar` or `git-repository-config_1.yml`, rather than forms such as `algites-vcs-status-v4.jar`. This naming convention makes the version suffix visually and mechanically separable from the logical base name. It applies to physical artifact/file naming; logical contract identity remains represented by separate capability/schema ID and version fields, and explanatory shorthand such as `X/v4` may still be used when discussing a logical contract version.

A new component may need to support a capability contract newer than the Core's built-in contracts.

Its distribution may therefore include the canonical contract JAR, for example:

```text
git-component-package/
    descriptor.yml

    contracts/
        algites-vcs-status_4.jar

    implementation/
        git-component.jar

    lib/
        jgit-...
        other-private-dependency-...
```

The important rule is:

> the contract JAR is distributed *with* the component, but it is not treated as a private plugin dependency when used for cross-component binding.

Before any implementation class that relies on that contract is loaded, Core must decide whether to admit the contract bundle into the shared active contract layer.

## X.6 Contract admission before component class-loader creation

Because all descriptors are discovered before activation, Core can collect required/supplied contract bundles first.

Recommended sequence:

```text
1. read all component descriptors
2. discover contract bundles
3. validate capability id/version identity
4. validate integrity/trust policy
5. deduplicate identical canonical bundles
6. reject conflicting definitions
7. build/finalize Core Active Contract ClassLoader
8. create component class loaders with Active Contract ClassLoader as parent
9. resolve graph
10. load selected implementation classes
```

A Core implementation may realize the active contract domain with one loader, a loader layer, or an equivalent mechanism.

The architectural requirement is one shared defining domain for cross-component contract classes.

## X.7 Built-in contracts plus dynamically admitted contracts

An old Core may start with:

```text
built-in:
    VcsStatusV1
    VcsStatusV2
    VcsStatusV3
```

A newer component supplies canonical:

```text
VcsStatusV4 contract JAR
```

If the Core bootstrap/runtime understands generic contract admission, it can add v4 to the **active** contract layer before component class loaders are created.

The Core then effectively knows/mediates:

```text
V1
V2
V3
V4
```

even though the old Core binary did not originally contain a V4 implementation.

Core still does not necessarily provide the capability itself.

## X.8 Same contract version supplied by multiple components

Plugin A and Plugin B may both package the same canonical v4 contract JAR for self-contained distribution.

Core should treat this as duplicate supply of one contract, not two contract identities.

Conceptually:

```text
A supplies capability X/v4, digest D
B supplies capability X/v4, digest D
    -> admit once

A supplies capability X/v4, digest D1
B supplies capability X/v4, digest D2
    -> contract-definition conflict
```

The exact identity mechanism may use:

- signed artifact coordinates;
- immutable contract artifact ID;
- content digest;
- namespace authority metadata;
- a combination of these.

## X.9 Compile-time API and distribution packaging are separate questions

A Java component may compile against an Algites contract artifact as:

```text
compileOnly / provided
```

from the perspective of its **implementation JAR**.

However, a self-contained component distribution may still package the canonical contract artifact separately under a contract-bundle area so an older compatible Core can admit it dynamically.

Thus:

```text
implementation runtime class path:
    does not privately define shared contract classes

component distribution:
    may carry canonical contract JARs for Core admission
```

This resolves the apparent contradiction between `provided` compilation and forward-compatible distribution.

## X.10 Protected shared namespaces

Algites should reserve protected shared namespaces, for example:

```text
eu.algites.bootstrap.*
eu.algites.capability.*
eu.algites.contract.*
```

A component-private class loader MUST NOT define a private replacement for a class belonging to a protected contract namespace used in cross-component communication.

If such a class is required:

```text
it must already be in the active contract layer
or
its canonical contract bundle must be admitted there first
```

## X.11 The old-Core loading problem

Suppose a new component contains:

```java
final class StatusV3Provider implements VcsStatusV3 { ... }
final class StatusV4Provider implements VcsStatusV4 { ... }
```

If an old Core cannot or does not admit v4, it may still run the component through v3 **provided it never loads a class whose linkage requires VcsStatusV4**.

The robust rule is:

> Any class that must be loadable on an older Core must avoid symbolic references to contract types that are not present in that Core's active contract layer.

Do not create one bootstrap class:

```java
final class GitComponent
        implements VcsStatusV3, VcsStatusV4 {
    ...
}
```

because loading/linking that class requires both interfaces.

## X.12 Version-specific implementation classes

Implementation classes should be separated by contract version or compatible generation.

Descriptor concept:

```text
algites.vcs.status/v3
    implementation:
        ...StatusV3Provider

algites.vcs.status/v4
    implementation:
        ...StatusV4Provider
```

If v4 was not admitted:

```text
Core selects v3
loads StatusV3Provider
does not load StatusV4Provider
```

If v4 was admitted and negotiated:

```text
Core selects v4
loads StatusV4Provider
```

Version-specific classes may live in separate internal JARs/modules for stronger linkage isolation.

## X.13 Bootstrap classes must reference only stable bootstrap contracts

The Java bootstrap entry point must not directly reference every capability interface shipped anywhere in the component package.

It should depend only on the small stable bootstrap API and static descriptor model.

That lets Core inspect/admit contracts and resolve versions before touching version-specific provider classes.

## X.14 Shared DTO types

All types appearing in a shared capability interface signature must also resolve through the same Core-owned active contract domain, unless they are explicitly safe JDK/platform types.

Bad:

```java
ThirdPartyPrivateType status(...);
```

Good:

```java
AlgitesRepositoryStatus status(AlgitesRepositoryRef ref);
```

where both DTOs belong to the admitted canonical contract bundle.

## X.15 Core-supplied Java bindings

Core resolves:

```text
Consumer B
    consumes algites.vcs.status/v4

Provider A
    provides algites.vcs.status/v4
```

After v4 is present in the active shared contract layer:

```text
Provider A concrete implementation
    loaded by A's private class loader
    implements shared VcsStatusV4

Consumer B
    loaded by B's private class loader
    sees the exact same shared VcsStatusV4 Class object
```

Core can therefore inject the provider reference through the shared interface.

B does not need visibility of A's implementation class or private dependencies.

## X.16 Suggested Java component context

Conceptually:

```java
public interface ComponentContext {
    <T> T capability(CapabilityKey<T> key);
    ComponentConfiguration configuration();
}
```

The `CapabilityKey<T>` and shared contract interface `T` must themselves resolve from Core-owned shared contract/bootstrap layers.

Core supplies the already-resolved provider.

The consumer does not choose a provider by scanning component class loaders.

## X.17 Java provider instances

Every Java provider definition is used through Core-managed provider instances. Several instances may expose separate runtime objects implementing the same shared contract:

```text
S3Provider(instance-id-A, prod-config)   implements ObjectStoreV4
S3Provider(instance-id-B, backup-config) implements ObjectStoreV4
```

Core creates each instance using its stable Core-generated ID and separate normalized configuration, then binds consumers to the selected instance ID. A provider with only one instance follows exactly the same path.

## X.18 Java declaration cycles and instance-DAG resolution

Java components may declare mutually complementary capability requirements:

```text
A provides X, consumes Y
B provides Y, consumes X
```

Core still discovers both descriptors and may create both component class loaders. The resolver, however, MUST NOT wire concrete provider objects so that the extension-instance graph becomes cyclic.

A valid resolution may bind `A1 -> B1/Y` and independently `B2 -> A2/X`. An attempted wiring `A1 -> B1/Y` plus `B1 -> A1/X` is rejected before the ready/activation barrier.

This keeps class-loader construction independent from runtime dependency cycles and makes the resolved Java invocation topology a DAG.

## X.19 Parent-first versus child-first for private libraries

Protected contract namespaces are always parent-first.

Plugin-private libraries may use child-first/private resolution if needed so:

```text
Component A -> Library L v1
Component B -> Library L v2
Core        -> Library L v3
```

can coexist.

Conceptually:

```text
if class belongs to protected Algites contract/bootstrap namespace:
    parent/shared domain only
else:
    component-private policy may prefer private loader
```

## X.20 Native/JNI caveat

Class-loader isolation does not automatically isolate native libraries.

Incompatible JNI dependencies may still require:

```text
restricted combinations
renamed native libraries
or process isolation
```

A formal Java profile must define this explicitly.

## X.21 JPMS

JPMS may complement class-loader isolation but does not replace:

```text
Core active contract catalog
shared contract type identity
provider resolution
configuration ownership
lifecycle
```

## X.22 Why this matches the general governance model

The Java runtime behavior explains the language-neutral invariant directly:

```text
a capability contract may be distributed by an extension component
but
it must become Core-owned/shared before cross-component binding
```

Without that step, two plugins can carry the same interface name but still have incompatible runtime types.

Thus Java class-loader semantics are not merely an implementation detail; they are a concrete example of why the general Core-owned active contract-domain rule exists.

---

# XI. Cross-Technology Binding Model

## XI.1 Common conceptual binding

Every binding has:

```text
consumer identity
capability id
selected provider instance
selected contract version
canonical Core-owned contract identity
Core binding metadata
runtime invocation endpoint
```

## XI.2 Java

The runtime endpoint can often be a direct reference implementing a shared parent-loaded Java interface.

## XI.3 Python

The endpoint is more likely a consumer-local proxy dispatching through Core into the provider interpreter.

## XI.4 Why Core owns the binding configuration

Provider assignment affects:

```text
topology
configuration reproducibility
VCS portability
diagnostics
permissions
contract selection
runtime creation
```

Therefore it cannot be a private consumer setting.

---

# XII. UI Notes

## XII.1 Component administration

A useful generic UI can show:

```text
Component identity/version
Component configuration schema/version

Consumes:
    capability
    selected provider instance
    selected contract version
    binding scope/source

Provides:
    provider definitions
    provider instances
    stable instance ids/names
    active instance state

Persistent data:
    data types
    schema versions
    compatibility state
```

## XII.2 Editing provider binding

The same view may make the resolved provider:

```text
editable
read-only
admin-only
policy-locked
```

depending on product rules.

The representation should not imply that the binding is stored in the component's own configuration.

---

# XIII. Process Isolation as an Alternative Runtime Profile

## XIII.1 Benefits

Separate processes improve isolation for:

```text
native dependencies
crashes
memory corruption
mixed languages
stronger security boundaries
```

## XIII.2 Costs

They add:

```text
memory
startup
IPC
serialization
lifecycle complexity
```

## XIII.3 Baseline position

Process isolation remains a valid future profile when interpreter/class-loader isolation cannot support required libraries.

---

# XIV. Lifecycle and Provisioning Notes

The normative lifecycle is defined by `Application-Component-Lifecycle-and-Provisioning-Specification.md`. These notes retain only the design rationale:

- package installation is not the same as provisioning;
- provisioning is not the same as entitlement;
- entitlement is not the same as compatibility;
- graph resolution is not the same as activation;
- suspension/deactivation should be diagnosable separately from contract incompatibility;
- persistent configuration and semantic data may outlive the installed or entitled component version and therefore must remain Core-managed;
- an entitlement change should not silently corrupt an in-flight transaction; Core should apply the lifecycle policy at a defined invocation boundary.

This separation lets UI and diagnostics explain *why* a component is unavailable rather than reporting one generic "plugin failed" state.

---

# XV. Relationship to Established Technologies

## XV.1 OSGi

OSGi is relevant for capabilities, requirements, and wiring.

Algites intentionally keeps the baseline resolver narrower and handles private library dependencies through runtime isolation.

## XV.2 COM-style contracts

COM demonstrates the value of explicit interface contracts rather than relying solely on implementation version.

## XV.3 Protocol version negotiation

Selecting a mutually supported known contract resembles protocol negotiation.

## XV.4 Java class loaders

Java class loaders provide a mature mechanism for:

```text
shared parent API
+
private child implementation/dependencies
```

## XV.5 Python subinterpreters

CPython 3.14's public multiple-interpreter API makes interpreter isolation a practical candidate for a formal Algites Python component profile.

---

# XVI. Proposed Conformance Tests

## XVI.1 Cross-component graph tests

Test:

- Core provider;
- plugin provider;
- plugin-to-plugin chain;
- declarative component-level cycle with acyclic instance resolution;
- global provider binding;
- consumer override;
- ambiguous `SINGLE`;
- `MULTIPLE`;
- provider instance deletion protection;
- binding persistence by immutable instance ID across instance rename;
- initial `default` instance name does not act as identity;
- Core active-contract-catalog ceiling;
- dynamically admitted contract bundle;
- duplicate identical contract bundle;
- conflicting definitions of the same capability/version;
- retired contract;
- optional missing capability.

## XVI.2 Configuration tests

Test:

- component configuration;
- instance configuration;
- normalized injection;
- config schema migration;
- missing migration;
- reconfiguration path;
- secret reference handling.

## XVI.3 Persistent data tests

Test:

- supported schema;
- component absent;
- stored schema newer than installed component;
- migration;
- downgrade;
- VCS round-trip;
- opaque preservation;
- reference integrity.

## XVI.4 Python profile tests

Test:

- separate interpreter per component;
- conflicting private pure-Python dependencies;
- cross-interpreter proxies;
- configuration injection;
- instance creation;
- resolved-instance cycle rejection;
- interpreter restart;
- native extension certification.

## XVI.5 Java profile tests

Test:

- built-in contract layer plus dynamically admitted contract JAR;
- common Core-owned active contract class loader;
- separate component class loaders;
- conflicting private library versions;
- provider object through shared interface;
- old Core + new component using old interface class;
- ensure v4 implementation class is not loaded when old Core selects v3;
- reject plugin-private shadow copy of shared API;
- rejection of cyclic provider-instance wiring;
- one and multiple provider instances through the same runtime path;
- provider-instance binding by stable ID;
- JNI/native restrictions.

---

# XVII. Technology References

## XVII.1 Python

Relevant CPython documentation:

- Python 3.14 `concurrent.interpreters`:  
  https://docs.python.org/3.14/library/concurrent.interpreters.html
- Python 3.14 release notes:  
  https://docs.python.org/3.14/whatsnew/3.14.html

Important points for this architecture:

- `concurrent.interpreters` was added in Python 3.14;
- interpreters are isolated execution contexts with independent import state;
- they remain in one OS process;
- not every third-party extension supports multiple interpreters.

## XVII.2 Java

Relevant Java documentation:

- Java SE `ClassLoader`:  
  https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/ClassLoader.html
- JVM Specification, Loading/Linking/Initializing:  
  https://docs.oracle.com/javase/specs/jvms/se26/html/jvms-5.html

Important points:

- class identity includes the defining class loader;
- custom class loaders have explicit parents and ordinary delegation semantics;
- linking includes direct superinterfaces of a loaded class;
- symbolic reference resolution may be lazy or eager;
- therefore classes intended to load on an old Core must avoid symbolic references to unavailable newer shared APIs.

---

# XVIII. Open Formalization Work

## XVIII.1 Candidate public specifications

The architecture would benefit from separate specifications for:

- component descriptor schema;
- capability contract schema (now initially formalized in `Application-Component-Capability-Contract-Specification.md`);
- Core active contract catalog and dynamic contract-bundle admission;
- configuration schema and normalized representation;
- provider-instance schema;
- binding schema;
- extension-data envelope;
- migration protocol;
- Python runtime profile;
- Java runtime profile;
- process runtime profile;
- UI capability catalog.

## XVIII.2 Promotion rule

A technical mechanism should become a formal public runtime/profile commitment when:

1. it has been implemented or experimentally validated;
2. compatibility implications are understood;
3. conformance tests exist;
4. third-party authors can reasonably rely on it;
5. Algites is prepared to support the published behavior.