# Application Component UI Specification

## I. Scope

This specification defines technology-neutral administration and configuration UI semantics for Application Components. It does not prescribe a desktop or web toolkit. Core owns component-target configuration, provider-instance-target configuration, provider-instance identity, binding topology, observation topology, validation, and lifecycle state; a UI is a view/editor over that Core-owned model.

The baseline reusable implementation is Algites Application Components (AAC). AAC public framework identities use `_AAC.*` where a stable architectural identity is required. Python artifacts `aac/uiintf` and `aac/uiqt` are implementation artifacts, not capability IDs.

## II. Architectural rules

1. UI toolkit objects MUST NOT be required by `uiintf` or cross process/interpreter boundaries.
2. Extension components SHOULD describe configuration through canonical schemas and normalized metadata rather than private toolkit widgets.
3. UI edits MUST be applied through Core-owned administration APIs. A renderer MUST NOT mutate provider state, persisted bindings, or observation topology directly.
4. Binding editors MUST reference provider instances by immutable provider-instance ID, never by mutable display name.
5. An administration UI MUST be capable of representing unresolved requirements as well as resolved bindings. Otherwise first-time binding configuration would be impossible.
6. Configuration submission is not authoritative validation. Core MUST revalidate and normalize submitted values against the registered schema before persistence.
7. Products own the concrete event loop, window/shell, authentication/authorization policy, and placement of administration surfaces.
8. Configuration UI MUST preserve configuration-scope/configuration-provider/policy provenance instead of editing only an opaque merged value.
9. Entitlement UI represents Core-validated permission context and remediation actions; it MUST NOT manufacture entitlement state locally.

## III. Baseline administration model

A generic AAC administration surface SHOULD expose at least:

```text
Components
Provider Instances
Bindings / Requirements
Observation Topology
```

### III.1 Components

For each admitted component the UI can show component identity/version, provider definitions, permission metadata/accepted entitlement-scope types grouped by provided capability/version, effective entitlement state made available by Core, and diagnostic/lifecycle information.

### III.2 Provider instances

Provider-instance administration includes stable GUID, mutable name, component/provider-definition identity, provided capability and versions, state, and configuration. Creating a new instance creates a new Core-managed identity. Removing an instance MUST respect Core reference checks.

### III.3 Configuration forms

A registered provider configuration schema is converted to a normalized logical form. The baseline logical field vocabulary includes:

```text
STRING
INTEGER
NUMBER
BOOLEAN
ENUM
MULTILINE
JSON
SECRET_REFERENCE
FILE
DIRECTORY
INSTANCE_REFERENCE
CAPABILITY_REFERENCE
```

Renderers MAY support additional presentation hints. Complex values not covered by a specialized renderer MAY be edited as normalized JSON. Schema defaults, constraints and final validity remain Core/schema concerns.

### III.4 Requirements and bindings

The UI operates primarily over consumer requirements, not only the already-resolved graph. For each requirement it can show:

```text
consumer provider-instance ID/name
requirement ID
capability ID
acceptable contract versions
cardinality
mandatory/optional state
currently selected provider-instance IDs
compatible provider choices
```

Editing writes Core-owned binding preferences. Resolution remains a separate Core operation and the final resolved graph MUST satisfy the instance-DAG invariant.

### III.5 Observation topology

Observation configuration identifies an observer provider instance and one or more selectors containing capability pattern, optional versions, optional operations and PRE/POST phases. This topology is Core-owned and separate from the observer provider's own configuration.

## IV. Contextual configuration and entitlement

### IV.1 Configuration-profile-aware configuration

The logical UI model MUST NOT hard-code an `ORGANIZATION` or other closed configuration-scope enum. AAC defines well-known configuration-scope types `SYSTEM`, `USER`, and `WORKSPACE`; an active configuration profile may add types such as `CUSTOMER`, `CUSTOMER_GROUP`, `ORGANIZATION`, `TEAM`, `TENANT`, or others.

`REMOTE` is not a configuration-scope. Local/remote/filesystem/HTTP/etc. are configuration-provider provenance.

For every effective field the UI SHOULD be able to represent:

```text
effective value
value source kind: explicit / policy lock / policy default / schema default
source configuration-scope type/id
source configuration-provider
active policy-modes and provenance
shadowed contributions
allowed edit configuration-scopes
policy conflict/violation diagnostics
```

A renderer MUST NOT infer filesystem paths, VCS behavior, or network location from configuration-scope type. `WORKSPACE` configuration may be Git-backed, remote-project-service-backed, or another product-defined persistence mapping.

### IV.2 Editing layered values

The UI SHOULD expose the active configuration profile and ordered configuration-scope chain when useful for administration. It SHOULD allow a user to choose an allowed edit target configuration-scope and SHOULD provide an explicit action to remove/reset an override at that configuration-scope.

Configuration editing MUST also identify the configuration target (`COMPONENT` or a concrete immutable `PROVIDER_INSTANCE` ID) and the selected configuration-provider. Read-only providers/scopes SHOULD remain visible for provenance, but editing controls MUST be enabled only where provider mutation capability and Core authorization permit the requested value/policy mutation. A UI SHOULD submit one normalized atomic change set for a multi-property form where the selected provider supports atomic mutation.

Removing an override reveals the next effective ordinary value or default; it does not copy the inherited value into the removed layer.

A policy `DEFAULT` MUST be visibly distinguishable from an explicitly configured value. `LOCK`, `MIN`, `MAX`, `IN_SET`, and `NOT_IN_SET` restrictions SHOULD be explainable with provenance sufficient to tell the user which configuration-scope/configuration-provider imposed them.

Workspace-targeted edits SHOULD be visibly distinguishable because they may modify portable project semantics, but the UI MUST NOT assume that all `WORKSPACE` values are physically stored in VCS.

### IV.3 Entitlement state

The UI SHOULD expose effective permission sets grouped by provided capability ID/version and their provenance without becoming the authority for validity. It may display capability-supplied permission names/descriptions, entitlement-scope subject type/id, entitlement-provider source, source entitlement document/bundle and component entry, each permission's effective expiry/validity, and Core-managed remediation/upgrade actions. A bundle license containing multiple components MAY be shown as one purchased/licensed product while effective rights remain grouped by component and capability version. The UI SHOULD distinguish document-level status (issuer/signature/subject/scope) from per-component applicability so an uninstalled component entry can be shown as dormant without making the whole bundle appear invalid.

The UI MUST obtain accepted entitlement-scope types from each provided capability/version declaration and actual concrete subject identities from Core. It MUST NOT treat the user-customizable configuration-scope chain as the entitlement-scope vocabulary or allow a user to invent a licensing scope that the capability did not declare.

A component may remain active in free/degraded mode with an empty/minimal permission set.

### IV.4 Permission remediation

When the Core invocation bridge reports `PERMISSION_DENIED`, a product UI may offer login, purchase, refresh, grant acceptance, or other entitlement remediation through Core/entitlement-provider APIs. Component code does not directly own product purchase/account UI in the baseline model.

### IV.5 Core-entity extension sections

A product MAY render component-owned semantic extension data as normalized sections/actions attached to a Core-managed entity detail. Core discovers applicable components from their static entity-extension declarations keyed by Core entity type ID. The component supplies logical form/view metadata and normalized data; Core owns entity identity, authorization, and persistence mapping, and the renderer remains toolkit/product-owned. Extension-data editability MUST NOT be interpreted as authority to mutate the Core entity itself.

## V. Python AAC artifacts

### V.1 `aac/uiintf`

`uiintf` depends only on the public AAC Core interface artifact. It defines normalized DTO/view models and `AIiAacUiController`. It contains no PySide6 dependency.

### V.2 `aac/uiqt`

`uiqt` is the baseline PySide6 implementation. It provides a Core-backed controller and reusable administration widgets. The product owns `QApplication` and the Qt event loop. Extension components do not contribute arbitrary `QWidget` instances in the baseline profile.

The baseline Qt implementation renders components, provider instances, schema-driven configuration, requirements/binding preferences and observation topology. Package download/install/update is outside this UI baseline until package-management semantics are specified.

## VI. Future renderers

A web renderer or another desktop toolkit may implement the same technology-neutral contract. The same Core-owned configuration and topology semantics MUST remain authoritative regardless of renderer technology.
