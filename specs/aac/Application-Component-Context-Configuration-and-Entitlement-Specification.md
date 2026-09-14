# Application Component Context, Configuration, and Entitlement Specification

**Status:** Generic, technology-neutral Application Component specification  
**Scope:** Context identities, configuration-scopes, configuration-providers, semantic extension data, entitlement-scopes, entitlement-providers, permission grants, and runtime entitlement context  
**Audience:** Core implementers, component authors, product architects, UI authors, entitlement/configuration-provider authors, and technology-profile authors

---

# I. Purpose and Architectural Position

## I.1 Purpose

Application components execute inside a product context that may contain system-wide, organization-wide, user-specific, and workspace/project-specific information. This specification defines how those contextual inputs are represented without coupling a component to a particular filesystem layout, database, VCS implementation, license server, or UI toolkit.

The same context model is shared by configuration and entitlement evaluation, but configuration and entitlement remain separate typed concerns because their trust and enforcement semantics differ.

## I.2 Relationship to other specifications

`Application-Component-Architecture-Governance.md` defines the architectural invariants. `Application-Component-Lifecycle-and-Provisioning-Specification.md` defines when configuration and entitlement are evaluated and delivered. `Application-Component-Capability-Contract-Specification.md` defines invocation semantics and Core mediation. `Application-Component-UI-Specification.md` defines how contextual values and entitlement state are represented to users.

## I.3 Physical storage is product-owned

AAC defines logical configuration-scopes, entitlement-scopes, normalized values, provenance, policies, and extension-data envelopes. It does **not** prescribe that system configuration lives under `/etc`, user configuration under a particular home directory, workspace data in YAML, or semantic extension data under a `.aac` folder.

The Core application/product profile owns the physical mapping. A product may persist the same logical model in files, a relational database, a document database, a remote configuration service, or a combination of these.

---

# II. Context Identity and Extensible Scope Model

## II.1 Stable contextual identities

A Core invocation/configuration context may contain stable identities such as:

```text
application/product identity
system/installation identity
user identity (optional)
workspace/project identity (optional)
organization identity (optional, product/deployment-defined)
team identity (optional, product/deployment-defined)
customer identity (optional, product/deployment-defined)
customer-group identity (optional, product/deployment-defined)
tenant/environment/region identity (optional, product/deployment-defined)
provider-instance identity (when applicable)
Core-domain entity identity (when applicable)
```

A workspace/project intended to participate in portable configuration, extension data, or workspace-scoped entitlement SHOULD have a stable immutable workspace ID independent of filesystem path, repository checkout location, remote project URL, or display name.

## II.2 Configuration-scope types are extensible

AAC does **not** define a closed configuration-scope enum. It defines well-known configuration-scope types and an extensible identity mechanism.

The well-known configuration-scope types are:

```text
SYSTEM
USER
WORKSPACE
```

Products, deployments, or application profiles MAY define additional configuration-scope types such as:

```text
ORGANIZATION
TEAM
CUSTOMER
CUSTOMER_GROUP
TENANT
PROJECT_GROUP
ENVIRONMENT
REGION
```

These examples are deliberately not reserved as universal hierarchy levels. `CUSTOMER`, for example, may be useful when all projects for one customer share deployment infrastructure, URLs, credentials references, or other customer-specific configuration. `TEAM` may represent a shared engineering-team profile. `ORGANIZATION` may represent company-wide configuration. Another deployment may use none of these and define different configuration-scope types.

A concrete configuration-scope is an identity, conceptually:

```text
configuration-scope type + optional stable configuration-scope id
```

Examples:

```text
SYSTEM
USER(artur)
WORKSPACE(project-x)
CUSTOMER(acme)
CUSTOMER_GROUP(europe)
ORGANIZATION(algites)
```

The same product may use a different ordered configuration-scope chain for different workspaces or application usages.

A concrete configuration-scope instance SHOULD occur only once in one active configuration-scope chain. Multiple configuration-providers bound to that configuration-scope are resolved *inside that one chain position*; they MUST NOT cause the same configuration-scope to be interleaved at several precedence positions (for example `SYSTEM-provider-A -> WORKSPACE -> SYSTEM-provider-B`).

## II.3 Entitlement-scopes are licensing-contract identities, not customizable configuration scopes

Entitlement evaluation uses **entitlement-scopes**, not generic unnamed scopes. Concrete identities such as `USER(artur)`, `WORKSPACE(project-x)`, `CUSTOMER(acme)`, or `ORGANIZATION(algites)` may be reusable when the product can establish them as trusted entitlement subjects, but entitlement-scope acceptance is defined by the provided capability's licensing contract rather than by the configurable configuration-scope chain.

A user/deployment may define additional configuration-scope types without thereby creating new valid entitlement-scope types. A provided capability version explicitly declares which entitlement-scope type identifiers it accepts for each permission; product/runtime subject resolution and policy may restrict applicability further but cannot broaden that declared set.

## II.4 Context identity availability is contextual

Not every runtime context has every configuration-scope or entitlement-scope identity. A headless service may have no current user or workspace. A desktop project editor may have both. A customer-oriented deployment may additionally resolve customer and customer-group identities.

Components MUST NOT assume an optional contextual identity exists unless their declared requirements make that identity mandatory.

## II.5 Configuration-scope/entitlement-scope and provider transport are orthogonal

`REMOTE` is **not** a configuration-scope and is **not** an entitlement-scope. Local/remote/filesystem/HTTP/database/Git/etc. describe a configuration-provider or entitlement-provider storage/transport mechanism, not the context governed by the value or grant.

For example:

```text
SYSTEM configuration-scope
    -> local /etc-style configuration-provider

USER(artur) configuration-scope
    -> remote intranet/profile configuration-provider

WORKSPACE(project-x) configuration-scope
    -> Git-backed configuration-provider
    -> or remote project-service configuration-provider

CUSTOMER(acme) configuration-scope
    -> mounted shared-disk configuration-provider

WORKSPACE(project-x) entitlement-scope
    -> signed grant stored in the workspace
    -> or remote entitlement-provider
```

A renderer, component, or Core subsystem MUST NOT infer physical location from configuration-scope or entitlement-scope type.

# III. Scoped Configuration

## III.1 Product-defined bootstrap schema

The bootstrap structures that establish contextual configuration MUST have a **fixed schema supplied with the product**. They are not ordinary component configuration and MUST be readable before ordinary scoped configuration is resolved.

A product MUST provide at least:

```text
an embedded/bootstrap schema version
bootstrap validation logic
bootstrap discovery rules
at least one safe built-in/default configuration profile
trusted bootstrap/profile-source rules
```

A product MAY use a platform-specific installation bootstrap location (for example an installation file, registry entry, container/platform injection, or equivalent) and MAY reference trusted remote bootstrap/profile registries. The bootstrap discovery path MUST NOT recursively depend on the scoped configuration that it is responsible for constructing.

Command-line or environment values MAY be used by a product as bootstrap locators/overrides when the product explicitly supports them, but AAC does not require command-line bootstrap.

## III.2 Configuration profiles and ordered configuration-scope chains

A **configuration profile** defines how the configuration context for one product usage is constructed. It may be selected differently for different workspaces/projects.

A configuration profile defines, at minimum:

```text
profile id and version
ordered configuration-scope definitions
configuration-scope resolvers
configuration-provider bindings for each configuration-scope
configuration-provider precedence within a configuration-scope
which configuration-scopes may contribute policy
workspace/profile customization rules
```

The ordered configuration-scope chain is written from **most specific/highest ordinary-value precedence** to **least specific/lowest ordinary-value precedence**.

Example only:

```text
USER(artur)
WORKSPACE(project-x)
CUSTOMER(acme)
CUSTOMER_GROUP(europe)
ORGANIZATION(algites)
SYSTEM
```

This ordering is not an AAC universal. Another workspace may select a profile such as:

```text
WORKSPACE(project-y)
TENANT(customer-42)
USER(artur)
SYSTEM
```

or may use only the three well-known configuration-scope types.

The product supplies a default configuration profile. A trusted installation bootstrap may select, replace, constrain, or extend available profiles according to the product bootstrap schema. A workspace MAY select a named configuration profile and MAY provide configuration-scope identity hints (for example its customer identity) when allowed by the trusted bootstrap policy.

A workspace MUST NOT be able to remove or weaken mandatory configuration-scopes, trusted configuration-providers, or policy authorities imposed by a higher bootstrap authority. A product MAY permit workspace-defined configuration profiles only when its bootstrap policy says `ALLOWED`, `ALLOWED_IF_SIGNED`, or an equivalent trusted rule.

## III.3 Component-declared placement rules

A component configuration schema SHOULD declare which **configuration-scope types** are valid for each persistent property or configuration fragment.

Conceptually:

```yaml
timeout:
  type: integer
  configuration_scopes: [SYSTEM, USER, WORKSPACE, CUSTOMER, ORGANIZATION]

repository_url:
  type: string
  configuration_scopes: [WORKSPACE, CUSTOMER]

local_cache_directory:
  type: string
  configuration_scopes: [USER]
```

The names outside the well-known `SYSTEM`, `USER`, and `WORKSPACE` set are configuration-profile/deployment-defined strings rather than AAC enum members.

This declaration states where a value is meaningful. It does not grant a component direct access to configuration stores; Core remains authoritative for loading, normalization, validation, persistence, configuration-policy evaluation, authorization, and provenance.

A component MAY additionally restrict which accepted configuration-scope types are allowed to contribute policy for a property. The active configuration profile may restrict policy authority further, but MUST NOT grant a component placement that the component schema itself forbids.

## III.4 Configuration targets

Persistent configuration is attached to an explicit **configuration target**. The baseline target kinds are:

```text
COMPONENT
PROVIDER_INSTANCE
```

`COMPONENT` is configuration owned by the component as a whole and shared independently of any one provider instance. Typical examples include component-wide presentation preferences, diagnostics settings, or other plugin-global behavior.

`PROVIDER_INSTANCE` is configuration owned by one concrete Core-managed provider instance and is identified by the component identity plus the immutable provider-instance ID.

Conceptually:

```yaml
configuration_target:
  kind: COMPONENT
  component_id: com.vendor.foo
```

or:

```yaml
configuration_target:
  kind: PROVIDER_INSTANCE
  component_id: com.vendor.foo
  provider_instance_id: 92c6...immutable-guid...
```

A component SHOULD declare separate schemas for component configuration and provider-instance configuration when it uses both target kinds.

There is **no implicit inheritance** from `COMPONENT` configuration into `PROVIDER_INSTANCE` configuration in the baseline AAC model. A component may interpret a component-level property as a default for its own instances, but that is component semantics rather than Core configuration-layer inheritance.

Component version is not part of configuration-target identity. Configuration SHOULD survive a compatible component upgrade. Persistence and provenance SHOULD instead record the configuration schema identity/version and the component version that last wrote the contribution, for example:

```text
configuration_schema_id
configuration_schema_version
written_by_component_version
```

## III.5 Configuration-provider mutation and authorization

A component or UI MUST NOT write a configuration-provider directly. Mutations are submitted to Core, which validates schema, effective policy, configuration-target ownership, current-actor authorization, and provider capability before invoking the configuration-provider write contract.

A configuration-provider exposes its technical mutation capabilities for the current concrete configuration-scope/context. Baseline capabilities SHOULD distinguish at least:

```text
READ
WRITE_VALUE
DELETE_VALUE
WRITE_POLICY
DELETE_POLICY
```

`WRITE_POLICY`/`DELETE_POLICY` are distinct because changing policy can constrain lower configuration-scopes and therefore commonly requires stronger authorization than changing an ordinary value.

Provider write capability is not sufficient authorization by itself. Core/product authorization separately determines whether the current application principal may perform a requested mutation against the selected configuration-scope, configuration-provider, component namespace, and configuration target.

A component is implicitly confined to its own configuration namespace unless an explicit Core/product capability grants broader administration authority. It MUST NOT be able to mutate another component's configuration or platform-owned settings merely because a backing provider is writable.

Mutations SHOULD be expressed as normalized logical change sets rather than serialized YAML/JSON documents. One mutation request targets one concrete configuration-scope, one configuration-provider, and one configuration target, and MAY contain multiple changes that MUST be applied atomically by a writable provider when the provider advertises atomic-write support.

Conceptually:

```yaml
configuration_scope:
  type: WORKSPACE
  id: project-x

configuration_provider: workspace-config

configuration_target:
  kind: PROVIDER_INSTANCE
  component_id: com.vendor.foo
  provider_instance_id: 92c6...

expected_revision: 184

changes:
  - property: repository_url
    operation: SET_VALUE
    value: https://example.invalid/repository

  - property: timeout
    operation: SET_POLICY
    policy_modes:
      MIN: 20
      MAX: 60
```

Writable providers SHOULD support optimistic concurrency through a provider revision, ETag, generation, or equivalent token. A stale `expected_revision` MUST yield a conflict rather than silently overwriting a concurrent change.

## III.6 Configuration-providers

Core obtains configuration contributions through typed **configuration-providers**, conceptually through an interface equivalent to:

```text
AIiConfigurationProvider
```

A configuration-provider may be local or remote and may contribute values for one or more concrete configuration-scopes when the active configuration profile binds it there.

Examples include:

```text
SYSTEM configuration-provider backed by an installation file
USER configuration-provider backed by a local user directory
USER configuration-provider backed by an intranet profile service
WORKSPACE configuration-provider backed by Git/project files
WORKSPACE configuration-provider backed by a remote project service for secrets/references
CUSTOMER configuration-provider backed by a shared disk or customer portal
ORGANIZATION configuration-provider backed by a central configuration service
```

A remote configuration-provider SHOULD expose a validated/cached snapshot when continuous network availability is not guaranteed and product policy permits offline use.

If multiple configuration-providers contribute to the same concrete configuration-scope, their precedence MUST be explicit and deterministic in the configuration profile. Equal-precedence conflicting scalar contributions MUST NOT be resolved by accidental discovery order; Core MUST report a configuration conflict unless the property schema/profile defines an explicit merge rule.

A configuration-provider priority is local to its bound concrete configuration-scope. It does not compete directly with configuration-providers bound to another configuration-scope; the configuration-scope chain determines cross-scope precedence.

## III.7 Configuration policy is represented by composable policy-modes

A configuration contribution may contain an ordinary value and/or one or more policy-modes. Baseline policy-modes are:

```text
LOCK(value)
MIN(value)
MAX(value)
IN_SET(values)
NOT_IN_SET(values)
DEFAULT(value)
```

Multiple policy-modes MAY be defined for one property at one configuration-scope, for example `MIN(20)` together with `MAX(50)`.

Policy is evaluated for the same configuration target as the property being resolved. Baseline AAC does not implicitly apply component-target policy to provider-instance-target configuration or vice versa.

The restriction modes have these baseline semantics:

```text
LOCK(x)        allowed domain becomes intersection with {x}
MIN(x)         values below x are excluded
MAX(x)         values above x are excluded
IN_SET(S)      allowed domain becomes intersection with S
NOT_IN_SET(S)  values in S are excluded
```

`DEFAULT(x)` is different: it does **not** widen or narrow the allowed domain. It supplies a policy-owned fallback candidate when no ordinary configuration value is defined at any applicable configuration-scope. Its provenance MUST remain distinguishable from an explicitly configured value and from a component-schema default.

`LOCK(x)` is both a restriction to one value and an effective forced policy value. If an ordinary contribution explicitly defines another value, the configuration is invalid rather than silently falling back.

Policy-modes MUST be compatible with the property schema/type. For example, `MIN`/`MAX` require an ordered domain and set modes require comparable normalized values.

Additional policy-modes MAY be standardized later or introduced by a product profile when their merge semantics are deterministic and machine-validatable.

## III.8 Policy resolution is monotonic; ordinary value resolution is specificity-first

For each property and configuration target Core performs policy and value resolution as separate phases.

Assume an active configuration-scope chain from most specific to least specific:

```text
USER(artur)
WORKSPACE(project-x)
CUSTOMER(acme)
CUSTOMER_GROUP(europe)
ORGANIZATION(algites)
SYSTEM
```

### III.8.1 Policy phase

Core evaluates restricting policy-modes from the **least specific** configuration-scope toward the **most specific**:

```text
SYSTEM
-> ORGANIZATION
-> CUSTOMER_GROUP
-> CUSTOMER
-> WORKSPACE
-> USER
```

Each later policy contribution may only preserve or **tighten** the effective policy. It MUST NOT broaden permissions established by a less-specific configuration-scope.

Examples:

```text
SYSTEM MIN(10)
ORGANIZATION MIN(20)
WORKSPACE MIN(15)

=> effective MIN = 20
```

The workspace contribution cannot relax the organization restriction.

For common baseline modes:

```text
effective MIN        = maximum of all applicable MIN values
effective MAX        = minimum of all applicable MAX values
effective IN_SET     = intersection of all applicable IN_SET values
effective NOT_IN_SET = union of all applicable NOT_IN_SET values
LOCK                  = intersection with the locked singleton value
```

Constraints compose across modes. For example:

```text
ORGANIZATION: MIN(20), MAX(50)
WORKSPACE:    IN_SET([10, 30, 40, 80])

=> effective allowed values = [30, 40]
```

A more-specific configuration-scope may then tighten this further, for example `NOT_IN_SET([40])`, yielding only `30`.

If combined restrictions yield an empty/unsatisfiable domain, Core MUST report a policy conflict with provenance identifying the contributing configuration-scopes and configuration-providers.

A policy contribution that merely attempts to relax an existing restriction has no broadening effect. Core SHOULD retain provenance/diagnostics showing that the contribution was non-effective or redundant.

### III.8.2 Ordinary value phase

After effective policy is known, Core evaluates ordinary values from the **most specific** configuration-scope toward the **least specific** and selects the first explicitly defined value for the same configuration target.

The selected explicit value MUST satisfy the effective policy. If it violates policy, Core MUST report a configuration-policy violation; it MUST NOT silently ignore that explicit value and fall back to a less-specific value.

If no ordinary value is explicitly defined, Core evaluates policy `DEFAULT` candidates from most specific to least specific and chooses the first candidate permitted by the effective policy. A policy default that contradicts effective policy is a configuration/policy-definition diagnostic and is not silently presented as an explicit value.

If no applicable policy default exists, Core MAY use the component-schema default, which MUST also satisfy effective policy. If no valid value/default exists for a mandatory property, the containing component/provider instance remains unconfigured or invalid according to lifecycle rules.

## III.9 Effective configuration and provenance

Core resolves effective configuration for a concrete configuration target and current application context. For every property Core SHOULD expose enough provenance to explain the result, including:

```text
configuration target kind/id
effective value
value source kind: EXPLICIT / POLICY_LOCK / POLICY_DEFAULT / SCHEMA_DEFAULT
source configuration-scope type and id
source configuration-provider
all effective policy-modes and their provenance
shadowed ordinary contributions
non-effective/redundant policy contributions
provider mutation capabilities for the current actor/context
Core authorization for value/policy mutation
configuration-policy violation/conflict diagnostics
revision/ETag where applicable
```

Components receive normalized effective configuration; they SHOULD NOT open configuration files or query configuration services directly unless a product-specific capability explicitly delegates that responsibility.

## III.10 Component and provider-instance configuration may combine many configuration-scopes

A configuration target is not limited to a single configuration-scope. Different properties may originate from different configuration-scopes and different configuration-providers.

For example, a provider-instance target may obtain a repository URL from `WORKSPACE`, a local executable path from `USER`, a deployment endpoint from `CUSTOMER`, and a TLS restriction from `ORGANIZATION`/`SYSTEM`.

A component target may independently obtain presentation or diagnostics settings from its own applicable configuration-scopes.

Core computes effective configuration separately for each target. It MUST NOT silently copy component-target values into provider-instance-target values.

## III.11 Secrets

Secret values SHOULD be represented by secret references rather than ordinary configuration values when a secret-store facility exists. Configuration-scope/provenance applies to the reference; access to the referenced secret remains governed by the secret facility.

A `WORKSPACE` configuration-scope therefore does not imply that a secret must be stored in Git. A workspace property may be supplied by a remote project configuration-provider or may contain only a secret reference resolved elsewhere.

# IV. Project/Workspace Semantic Extension Data

## IV.1 Logical ownership

A component may own persistent semantic data associated with a Core-managed workspace/project or Core-domain entity. AAC defines this as **semantic extension data**, not as ordinary component configuration and not as runtime cache.

## IV.2 Entity-extension declarations

A component that contributes semantics or UI for Core-managed entity types SHOULD declare those contributions statically so Core can discover them without invoking arbitrary plugin code.

A declaration identifies at least:

```text
Core entity type ID
required Core-entity access (baseline: READ)
whether extension data is NONE / READ_ONLY / READ_WRITE
extension schema ID/version when extension data exists
logical UI contribution metadata when the product supports generic extension UI
```

Conceptually:

```yaml
entity_extensions:
  - entity_type_id: _AO.NodeDefinition
    core_entity_access: [READ]
    extension_data:
      access: READ_WRITE
      schema_id: com.vendor.foo.node-extension
      schema_version: 1
    ui:
      contribution: true
```

Declaring extension data does not grant authority to mutate the Core entity itself. Core-entity mutation, if supported, requires a separate explicit product/Core capability or authorization contract.

A product UI can use these declarations to discover which admitted components have something to display or edit for a concrete entity type, for example as generic extension sections or actions.

## IV.3 Core owns persistence placement

AAC deliberately does not prescribe `.aac/extensions/...` or any other filesystem path. The Core application decides how extension data is physically associated with its entity/project model.

Examples include:

```text
opaque extension envelope embedded inside the serialized Core entity
related row/table/document in a database
product-specific side structure colocated with project data
```

A file-oriented Core may intentionally embed extension envelopes directly in entity serialization so that Core structural migrations move the opaque payload with the entity even when the component is absent.

## IV.4 Logical extension envelope

A normalized extension-data envelope SHOULD identify at least:

```text
owner component id
extension schema id
extension schema version
written-by component version (provenance)
Core entity type id
Core entity stable id
normalized/opaque payload
```

Owner component version is provenance, not extension-data identity. Compatibility is governed primarily by the extension schema identity/version so a newer component may continue to read an older compatible extension schema.

Core MAY expose abstract exchange operations equivalent to:

```text
get extension data(entity-ref, component-id)
put extension data(entity-ref, component-id, schema-id, schema-version, payload)
remove extension data(entity-ref, component-id)
enumerate extension data(entity-ref)
```

Components access this data through Core APIs/bridges rather than by discovering the product's physical YAML/database representation.

## IV.5 Opaque preservation during Core migrations

Core migration MUST preserve unknown extension envelopes opaquely when it moves, renames, restructures, or rewrites Core entities. The component does not need to be installed for this preservation step.

Where one old Core entity maps unambiguously to one new entity, its opaque extension envelopes follow that entity.

For split/merge/deletion or otherwise ambiguous migrations, Core MUST NOT silently discard extension data. It SHOULD produce an explicit outcome such as:

```text
PRESERVED
REASSIGNED
ORPHANED
REMOVED_BY_EXPLICIT_POLICY
```

with diagnostics sufficient for later recovery or component-specific migration.

## IV.6 Component-specific semantic migration

When extension **schema semantics** themselves must change, a compatible component/migrator may perform a later component-specific migration after the Core structural migration has preserved the old payload.

This separation allows the primary Core migration to succeed without requiring every optional plugin to be installed.

## IV.7 Component absent or unavailable

A workspace may contain extension data for a component that is:

```text
not installed
not currently entitled
installed at an incompatible version
temporarily unavailable
```

Core MUST preserve that data. Generic UI may expose unavailable-extension diagnostics, but MUST NOT rewrite/drop opaque payload merely because its owning component is absent.

# V. Entitlement as Capability-Version Permission Grants

## V.1 Entitlement is not a boolean license flag

Entitlement is represented as Core-validated grants over **component-owned permissions in the namespace of a provided capability version**, rather than one boolean `licensed` state.

The minimum logical identity of an entitlement permission is:

```text
component id
+ provided capability id
+ provided capability version
+ permission id
```

The same permission string used by another capability or another capability version is a different entitlement permission unless an explicit future compatibility rule says otherwise.

A component may remain installed and active with an empty/minimal permission set and provide free, diagnostic, configuration, read-only, or degraded functionality.

## V.2 Capability-declared permission vocabulary and accepted entitlement-scopes

Permission vocabulary and accepted **entitlement-scope types** are declared for each provided capability version that uses entitlement.

Conceptually:

```yaml
provided_capabilities:
  - id: com.vendor.foo.document
    version: 1
    entitlement:
      permissions:
        view:
          implicit: true
          accepted_entitlement_scopes: [USER, WORKSPACE, ORGANIZATION]
        edit:
          accepted_entitlement_scopes: [USER, WORKSPACE, ORGANIZATION]
        export:
          accepted_entitlement_scopes: [USER, WORKSPACE]
```

Entitlement-scope types are **not the customizable ordered configuration-scope chain**. Users do not invent a new entitlement-scope type to expand how a plugin may be licensed.

AAC may define well-known entitlement-scope type identifiers such as `USER` and `WORKSPACE`; a component/vendor may additionally declare supported named licensing scopes such as `ORGANIZATION`, `CUSTOMER`, `TENANT`, or another contract-specific type. The component descriptor is authoritative for which entitlement-scope types are accepted for a given capability permission. Product policy may further restrict that set but MUST NOT silently add licensing scopes the capability contract does not accept.

For an accepted entitlement-scope type to be usable in a concrete product run, the product/runtime must also be able to establish a trusted concrete subject identity of that type. Therefore effective applicability is the intersection of:

```text
capability-declared accepted entitlement-scope types
product/runtime-supported trusted entitlement subject identities
product entitlement policy
```

The Core does not need to understand the business meaning of `view`, `edit`, `export`, or another capability-owned permission string.

## V.3 Entitlement subject identity

Every non-implicit entitlement grant MUST identify the entitlement-scope and the concrete subject to which it applies.

Conceptually:

```yaml
entitlement_scope:
  type: WORKSPACE

subject:
  id: 08a7...immutable-workspace-guid...
  display_name: Customer X Production
  attributes:
    organization:
      id: acme-42
      name: ACME Ltd.
      address: ...
```

Grant matching MUST rely on stable subject identity, scope type, issuer/trust domain, and other normative signed fields. Human-readable names, addresses, and similar attributes are signed audit/display metadata and MUST NOT replace the stable subject ID.

A `WORKSPACE` grant therefore requires an immutable Core/product-owned workspace identity discoverable from the workspace context. `USER`, `ORGANIZATION`, `CUSTOMER`, or other licensing scopes similarly require a trusted identity/enrollment mechanism defined by the product or entitlement infrastructure.

For offline/manual issuance, AAC MAY define a normalized entitlement-issuing request containing one or more requested components, their capability-version permissions, and the stable target subject identity. A single request may therefore request a plugin bundle for one entitlement-scope/subject. The issuing request may be persisted/exported with a workspace or user/organization enrollment state, but it is not itself an entitlement grant.

Conceptually:

```yaml
entitlement_request:
  format_version: 1
  request_id: ...

  requested_entitlement_scope:
    type: WORKSPACE

  subject:
    id: 08a7...immutable-workspace-guid...
    display_name: Customer X Production

  components:
    - id: com.vendor.foo
      requested_grants:
        - capability:
            id: com.vendor.foo.document
            version: 1
          permissions: [view, edit]

    - id: com.vendor.bar
      requested_grants:
        - capability:
            id: com.vendor.bar.export
            version: 2
          permissions: [export-pdf]

  generated_at: ...
```

## V.4 Entitlement evidence and grant structure

Trusted entitlement evidence SHOULD be technology-neutral at the Core model layer and MAY be represented by a signed local license document, remote subscription response, offline lease, or another verified evidence type.

A normalized signed entitlement document may conceptually contain:

```yaml
entitlement:
  format_version: 1
  entitlement_id: ...

  issuer:
    id: vendor.example

  entitlement_scope:
    type: WORKSPACE

  subject:
    id: 08a7...immutable-workspace-guid...
    display_name: Customer X Production
    attributes: ...

  components:
    - id: com.vendor.foo
      grants:
        - capability:
            id: com.vendor.foo.document
            version: 1
          permissions:
            - id: view
              valid_from: ...
              valid_until: ...
            - id: edit
              valid_from: ...
              valid_until: ...

    - id: com.vendor.bar
      grants:
        - capability:
            id: com.vendor.bar.export
            version: 2
          permissions:
            - id: export-pdf
              valid_from: ...
              valid_until: ...

  issued_at: ...
```

One entitlement document therefore MAY carry grants for any number of components, which enables a product/plugin bundle to be licensed and signed as one evidence object. The document-level issuer, entitlement-scope, subject, and signature/trust evidence apply to every component entry in that document. Components that must be licensed for a different subject or entitlement-scope belong in a different entitlement document.

Within one entitlement document, each component ID MUST be unique; within a component entry, each `(capability_id, capability_version)` grant MUST be unique; and within a capability-version grant, each permission ID MUST be unique. Duplicates inside one document are invalid rather than implicitly merged. Aggregation/union occurs across independently valid entitlement evidence/documents.

Cryptographic/evidence validation applies to the entitlement document as one signed object, while semantic applicability is evaluated per component entry. An entitlement document MAY therefore be cryptographically valid even when one referenced component is not currently installed/admitted. Core MUST preserve such unknown component entries and MUST NOT let their temporary absence invalidate otherwise applicable entries for other components in the same bundle.

When the corresponding component descriptor is available, Core MUST validate that component entry independently. A component grant that references a capability/version not declared as provided by that component, or a permission identifier not declared for that exact provided capability version, is not effective. Product policy decides whether such a semantic defect invalidates only that component entry or escalates the whole evidence document, but the baseline SHOULD isolate the defect to the affected component entry so multi-component bundles remain independently consumable. Issuer/trust policy MAY additionally restrict which component IDs or capability namespaces a given issuer is authorized to license.

The evidence-verification mechanism is pluggable through an interface equivalent to `AIiEntitlementEvidenceVerifier`. Detached Sigstore evidence is a suitable reference implementation (for example `bundle.entitlement.yml` plus `bundle.entitlement.yml.sigstore.json`) but AAC MUST NOT make Sigstore the only possible evidence technology.

## V.5 Entitlement-providers

Trusted **entitlement-providers** supply entitlement evidence. Evidence may come from:

```text
signed local license files
multi-component plugin-bundle licenses
user grants
workspace/project grants
organization/customer/tenant grants
subscription services
offline lease caches
test/development entitlement-providers
```

An entitlement-provider may be local or remote. Entitlement-provider transport and storage are orthogonal to entitlement-scope. A remote service may produce a `WORKSPACE(project-x)` grant; a locally stored signed file may produce an `ORGANIZATION(acme)` grant.

Entitlement-providers participate through a trusted bootstrap path that does not recursively depend on the entitlement result being produced. Product bootstrap structures MAY register trusted entitlement-providers, entitlement subject resolvers/enrollment sources, issuers, and trust roots using product-supplied fixed bootstrap schemas.

## V.6 Core validation remains authoritative

Core validates entitlement evidence according to product policy, including as applicable:

```text
issuer/trust
signature or equivalent tamper evidence
component identities contained in the entitlement document
provided capability id/version for each component grant
permission identifiers for that exact capability version
entitlement-scope type
stable concrete subject identity
validity interval / expiry / lease
constraints
revocation/refresh state
```

Component code MUST NOT convert an editable configuration field such as `licensed: true` into authoritative entitlement.

## V.7 Multiple grants, temporal union, and effective rights

Entitlement resolution is not ordinary configuration override resolution. A single verified entitlement document may contribute grants for several components, and valid applicable grants from all verified documents/providers normally **accumulate** rights for the current context.

Core aggregates independently for each permission identity:

```text
(component_id, capability_id, capability_version, permission_id)
```

If several valid grants cover the same permission, Core computes the effective validity from their union according to product entitlement policy. At a given instant the permission is effective when at least one applicable trusted grant covers that instant.

For example, for capability `X/v1`:

```text
ORGANIZATION grant:
    view   valid through 2027-03-31
    edit   valid through 2027-03-31

USER grant:
    view   valid through 2027-06-30
    export valid through 2027-06-30
```

then, before 2027-03-31, effective permission metadata can be reported as:

```text
X/v1 view   effective through 2027-06-30
X/v1 edit   effective through 2027-03-31
X/v1 export effective through 2027-06-30
```

Core SHOULD expose effective validity/expiry and grant provenance for each effective permission to the provider runtime and administration UI.

The ordered configuration-scope chain defined for configuration does not define entitlement precedence.

## V.8 Workspace-scoped entitlement

A `WORKSPACE` entitlement grant is bound to immutable workspace identity and may authorize use/features only in that workspace. Such a grant may be VCS-portable when signed/tamper-evident and independently verifiable by Core.

The same entitlement-scope may also be backed by a remote entitlement-provider rather than a grant physically stored in the workspace.

Use entitlement and package-download authorization are distinct. A remediation service may obtain short-lived repository credentials, but possession of a workspace grant does not automatically make a package repository public.

## V.9 Entitlement does not redefine the capability graph

A consumer declares that it requires a capability contract/version; it does **not** declare the provider's commercial permission tiers.

A provider that advertises capability `X/v1` still implements the canonical operations of `X/v1`. Whether a particular operation is currently permitted is provider-owned runtime semantics based on the Core-validated effective permissions for `X/v1`.

Therefore ordinary entitlement changes SHOULD NOT require capability graph re-resolution solely because the permission set changed.

## V.10 Standard permission denial

AAC defines a standardized normalized invocation failure category equivalent to:

```text
PERMISSION_DENIED
```

A language binding may expose an exception such as `AIxPermissionDenied`. The provider may include diagnostic metadata identifying the missing permission **within the invoked capability/version namespace** and a remediation/offer hint, but the consumer is not required to understand the permission vocabulary.

Components SHOULD perform entitlement checks before business side effects whenever practical.

## V.11 Dynamic entitlement updates

Core MUST schedule/re-evaluate effective entitlement at validity boundaries and on provider refresh/revocation/context changes while a provider instance is running.

The provider instance receives an updated entitlement context through a Core-controlled lifecycle/context update path. That context SHOULD include, per provided capability version, the effective permission IDs, effective validity/expiry, relevant normalized constraints, and provenance needed for diagnostics.

Using the earlier example, when the organization `edit` grant expires on 2027-03-31 and no other applicable grant supplies `edit`, Core removes `edit` from the next effective context and delivers the update without requiring the provider to maintain its own license timer.

A provider may then immediately alter which operations/features it permits without being destroyed and recreated, unless its own implementation requires restart and declares that requirement.

# VI. Mandatory Core Invocation Mediation

## VI.1 No direct cross-component calls

A consumer component MUST NOT receive or retain a direct reference to another component's provider implementation/runtime object, even when both happen to execute in the same language, interpreter, class loader, or process.

Core supplies consumer-side capability handles/proxies. Every cross-component invocation flows through the Core invocation bridge/dispatcher.

Conceptually:

```text
consumer
  -> Core capability handle
      -> Core invocation bridge
          -> resolved provider instance endpoint
```

This rule is technology-neutral. An in-process profile may optimize the final dispatch to a local call, but it MUST preserve Core mediation semantics.

## VI.2 Why mediation is mandatory

The Core bridge is the architectural point for cross-cutting behavior including:

```text
resolved binding enforcement
contract/version identity
normalized input/output validation
invocation and parent-invocation identity
observation/tracing/redaction
runtime isolation transport
standardized errors
entitlement remediation
retry policy
timeouts/cancellation/diagnostics
```

A component MUST NOT bypass the bridge by discovering provider implementation objects, process addresses, sockets, class-loader-local instances, or other private runtime endpoints.

## VI.3 Permission remediation

When a provider returns `PERMISSION_DENIED`, Core may apply a product policy/remediation handler before exposing that failure to the consumer.

Possible remediation includes:

```text
showing entitlement/licensing UI
refreshing a remote entitlement provider
accepting or purchasing an entitlement
installing/activating a valid grant
updating the provider instance entitlement context
```

The provider component itself SHOULD NOT implement product-specific purchasing UI or repository/account workflows. It may provide normalized permission metadata, offer/remediation hints, or documentation identifiers for Core/UI to present.

## VI.4 Transparent retry after remediation

Core MAY transparently retry the original invocation after successful entitlement remediation only when retry safety is established.

The normalized permission failure SHOULD carry a retry disposition such as:

```text
SAFE_AFTER_ENTITLEMENT_CHANGE
DO_NOT_RETRY
```

`SAFE_AFTER_ENTITLEMENT_CHANGE` asserts that the permission check failed before the business operation produced externally visible side effects, or that the operation/contract is otherwise safe to repeat under the applicable idempotency semantics.

If retry safety is not established, Core MUST NOT silently repeat the invocation. It may return a standardized remediation-completed-but-retry-required result/error to the consumer/UI.

## VI.5 Consumer transparency

When remediation succeeds and safe retry succeeds, the original consumer may receive the successful operation result without observing the intermediate permission failure. When remediation is unavailable, declined, fails, or cannot be safely retried, the standardized permission failure propagates through the normal capability error model.

---

# VII. UI Requirements

## VII.1 Configuration-scope-aware UI

A generic administration UI SHOULD expose effective values together with configuration-scope/configuration-provider provenance and allowed edit targets. It SHOULD distinguish changes that affect workspace/project semantics from user-local, system, customer, organization, or other contextual settings.

For a layered property the UI may show, according to the active configuration profile:

```text
Effective value
USER(artur) contribution
WORKSPACE(project-x) contribution
CUSTOMER(acme) contribution/policy
ORGANIZATION(algites) contribution/policy
SYSTEM contribution
schema default
```

The renderer MUST NOT assume these particular custom configuration-scopes exist and MUST NOT infer filesystem paths, remote URLs, or VCS behavior from a configuration-scope type.

## VII.2 Policy, defaults, and inheritance

The UI SHOULD expose:

```text
active configuration profile
ordered configuration-scope chain
configuration-provider provenance
explicit vs inherited value
POLICY_LOCK vs POLICY_DEFAULT vs SCHEMA_DEFAULT provenance
effective MIN/MAX/IN_SET/NOT_IN_SET/LOCK restrictions
which configuration-scopes may be edited
configuration-policy conflicts/violations
```

Removing an override at one configuration-scope SHOULD reveal the next effective ordinary contribution or default rather than copying an inherited value into that configuration-scope.

A value produced by `DEFAULT` policy MUST be visibly distinguishable from a value explicitly configured by a user/project/administrator.

## VII.3 Entitlement UI

The UI SHOULD be able to show:

```text
effective permissions grouped by provided capability id/version
permission display metadata supplied by the component/capability contract
entitlement-scope subject provenance
entitlement-provider/grant provenance
per-permission effective expiry/validity
missing permission diagnostics
available Core-managed remediation/upgrade actions
```

The UI does not become the authority for entitlement validity.

## VII.4 Core-entity extension UI

A product MAY allow a component to contribute normalized logical UI sections for extension data attached to Core-managed entities. The component receives stable Core entity/context identity and normalized extension data; the renderer remains product-owned and the component does not inject arbitrary toolkit widgets in the baseline model.

# VIII. Package-Management Integration Principles

## VIII.1 Workspace requirements and extension data

A workspace may contain component-owned extension data or component requirements even when the corresponding component is absent locally. Core MUST preserve such data and diagnose the missing/incompatible component before mutable use of the affected semantics.

## VIII.2 Automatic installation is policy-driven

Automatic package download/install/upgrade is never implied merely by the presence of extension data. A future package-management layer may perform automatic remediation only when all applicable conditions permit it, including:

```text
component/version/schema compatibility
product/user automatic-update policy
valid entitlement where required
repository/download authorization
artifact verification/trust policy
```

## VIII.3 Package origin/provenance

Package management SHOULD persist the logical source from which a component was obtained so future version checks can use the same source. The source may be represented by URIs such as `file:` or `https:` or by a typed repository descriptor.

The persisted installation record SHOULD distinguish:

```text
update/source repository URI
exact downloaded artifact URI
artifact digest
verified signer/publisher identity
installed component/version/build identity
```

A source URI is provenance, not trust. Every newly downloaded artifact is verified independently, and bytes MUST be verified again immediately before atomic installation/admission.

---

# IX. Conformance Invariants

1. Configuration and entitlement may reuse contextual identities but use separate typed concepts: **configuration-scopes/configuration-providers** and **entitlement-scopes/entitlement-providers**.
2. Configuration-scope types are extensible and profile/deployment-defined; `SYSTEM`, `USER`, and `WORKSPACE` are well-known configuration-scope types. Entitlement-scope types are a separate licensing contract: capability declarations define which entitlement-scope type identifiers are accepted, and end users cannot invent additional accepted licensing scopes at runtime.
3. `REMOTE` is neither a configuration-scope nor an entitlement-scope; local/remote/filesystem/HTTP/database/Git/etc. describe configuration-provider or entitlement-provider storage/transport.
4. Configuration bootstrap structures have fixed product-supplied schemas and are resolved through a non-recursive trusted bootstrap path before ordinary scoped configuration.
5. An active configuration profile defines the ordered configuration-scope chain, configuration-scope resolvers, configuration-provider bindings/precedence, and permitted policy authority for the current product usage/workspace.
6. A lower bootstrap authority may select or extend profiles only as permitted by higher trusted bootstrap policy and MUST NOT remove/relax mandatory trusted configuration-scopes, configuration-providers, or policy authorities.
7. Components declare the configuration-scope types in which their configuration properties are meaningful; entitlement permissions and accepted entitlement-scope types are declared per provided capability version.
8. Configuration policy is monotonic: more-specific policy may tighten but MUST NOT broaden restrictions already established by less-specific policy.
9. Baseline policy-modes are `LOCK`, `MIN`, `MAX`, `IN_SET`, `NOT_IN_SET`, and `DEFAULT`; multiple policy-modes may apply to one property.
10. `DEFAULT` is a provenance-aware fallback and not a restriction. `LOCK` restricts the property to one value and therefore acts as a forced policy value.
11. Ordinary configuration values resolve from most-specific to least-specific configuration-scope only after effective policy is known; an explicit value violating policy is an error and MUST NOT be silently skipped.
12. Core retains configuration-scope/configuration-provider/policy provenance sufficient to explain every effective value and conflict.
13. Core owns validation and temporal aggregation of entitlement evidence, including multi-component entitlement documents; components interpret only the Core-validated effective permission strings/constraints for their own provided capability versions.
14. Entitlement is a capability-version permission/constraint set, not merely a boolean licensed flag; permission identity includes component, capability ID/version, and permission ID, and valid grants normally accumulate rather than use configuration override precedence.
15. A component may remain active with an empty/minimal entitlement set and provide free/degraded functionality.
16. Consumer requirements do not encode another provider's commercial permission tiers.
17. Permission denial is a runtime invocation outcome and does not by itself invalidate the capability binding.
18. All cross-component invocations pass through a Core-owned capability bridge/handle, including in-process invocations.
19. Core may remediate `PERMISSION_DENIED` centrally, but transparent retry requires explicit retry safety.
20. AAC does not dictate physical storage of semantic extension data; the Core product owns the mapping.
21. Unknown extension payloads are preserved opaquely across Core storage/entity migrations whenever their subject can be preserved.
22. Workspace-scoped entitlement is bound to stable workspace identity and may be VCS-portable only when its grant is tamper-evident/validated.
23. Package source/provenance is distinct from artifact trust and entitlement-to-use.
