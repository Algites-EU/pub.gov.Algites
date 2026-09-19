# Algites credential subsystem

This artifact set provides the public, bootstrap-safe credential subsystem used by Algites build infrastructure.
No secret value is stored in `pub.gov.Algites`; only profile definitions, credential contracts, resolver logic, CLI code, and operating-system store adapters are public.

## Artifacts

- `coreintf` — stable credential profile/value/provider/store API and closed credential type, field, and value-source enums.
- `coreimpl` — universal credential-document resolver, ServiceLoader store discovery, diagnostics, versioned credential codec, and persistent credential facade.
- `cli` — cross-platform interactive provisioning/status CLI.
- `winstore` — Windows Credential Manager backend through Advapi32/JNA.
- `macstore` — macOS Keychain backend through Security/CoreFoundation and JNA.
- `secretservicestore` — Freedesktop Secret Service backend through D-Bus; no `secret-tool` executable is required.

All artifacts use Java 17 and package namespace `eu.algites.tool.build.credentials`.

## Credential profiles

A credential profile is a non-secret, inherited configuration object. Its ID uses canonical lowercase dash-separated form. The profile type determines the fields required by the authentication mechanism. Repository endpoints refer only to the profile ID; they never contain secret values.

Supported v1 types are a closed enum:

| type | required fields | optional fields |
| --- | --- | --- |
| `basic` | `username`, `password` | — |
| `bearer` | `token` | — |
| `api-key` | `apiKey` | — |
| `certificate` | `certificate` | `privateKey`, `privateKeyPassword` |

The canonical Java constants are `AInCredentialType` and `AInCredentialField`. Concrete TechnologyKind repository adapters may support only a subset of these credential types. Unsupported endpoint/type combinations fail explicitly.

## Universal credential document

`ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS` contains the universal credential document. The same schema is used by local processing, provider bridges, and the final Gradle processing. The governed v1 JSON Schema is `algites-credentials_1.schema.json`.

A profile may retain values for more than one credential type. The effective non-secret `credentialProfile.type` selects the type used by a particular repository endpoint. This permits a profile type to change without destroying values retained for its previous type.

Example:

```json
{
  "algites-java-private-release-download": {
    "basic": {
      "username": {
        "source": "DIRECT_VALUE",
        "value": "algites-user"
      },
      "password": {
        "source": "SECRET_CONTENT",
        "value": "ALGITES_JAVA_PRIVATE_PASSWORD"
      }
    },
    "bearer": {
      "token": {
        "source": "ENVIRONMENT_VARIABLE_CONTENT",
        "value": "ALGITES_JAVA_PRIVATE_TOKEN"
      }
    }
  }
}
```

Every field is represented by the same pair of properties, `source` and `value`. `source` is the closed `AInCredentialValueSource` enum:

| source | meaning of `value` | resolved content |
| --- | --- | --- |
| `DIRECT_VALUE` | the credential content itself | `value` unchanged |
| `FILE_CONTENT` | path to a file | UTF-8 content of that file |
| `SECRET_CONTENT` | name/key in the current secret-provider context | content of that secret |
| `ENVIRONMENT_VARIABLE_CONTENT` | environment-variable name | content of that variable |

The `_CONTENT` suffix describes the content obtained by materialization, not the literal meaning of the `value` property. For example, a `FILE_CONTENT` value is a file path and a `SECRET_CONTENT` value is a secret name.

Materialization does not introduce another credential format. It transforms the same document: selected fields are replaced with `{ "source": "DIRECT_VALUE", "value": "..." }` entries containing the resolved content.

`_TMP_ALGITES_CREDENTIAL_SECRETS_JSON` is an optional provider-context object used by bootstrap adapters that need exact-name `SECRET_CONTENT` lookup. It is not a credential document and does not define profiles or fields. The GitHub bridge receives the complete GitHub Actions `secrets` context in this form. Local/provider-specific launchers may supply an equivalent secret context when needed.

## Local resolution and persistent storage

The universal `ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS` document is also the canonical persistent local representation. There is no second profile/type credential format. Local resolution uses this precedence:

1. a non-empty `ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS` environment variable, which acts as an explicit per-process override;
2. otherwise the universal `ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS` document stored in the highest-priority available Algites operating-system secure store.

The Gradle repository bootstrap runs before the credential Java modules of the current checkout can be built. When the environment override is absent, `algites-credential-values.gradle.kts` therefore asks an already installed `algites-credentials` CLI for the stored universal document. The executable is resolved from `ALGITES_CREDENTIAL_CLI` when that variable is set and otherwise from `algites-credentials` on `PATH`. This is a bootstrap adapter only; it does not introduce another credential schema.

For `SECRET_CONTENT`, an explicitly supplied `_TMP_ALGITES_CREDENTIAL_SECRETS_JSON` provider context is checked first. If the key is absent there, local Java resolution and the Gradle bootstrap resolve the named secret from the same Algites operating-system secure store. Consequently `_TMP_ALGITES_CREDENTIAL_SECRETS_JSON` is normally unnecessary for a local build.

A local user therefore stores only the credential profiles and named secrets required by the operations they actually execute. Upload/signing credentials are not required for an ordinary download-only build. Because one document may contain multiple typed entries under one profile, a later profile-type change does not reinterpret or destroy the values retained for another type.

Persistent backends are discovered through `ServiceLoader`. Each backend reports structured availability state and remediation information instead of only a boolean.

- Windows: Windows Credential Manager.
- macOS: Keychain.
- Linux desktop: `org.freedesktop.secrets` through the Freedesktop Secret Service D-Bus API. Compatible providers include GNOME Keyring, Secret-Service-capable KWallet implementations, and KeePassXC when its Secret Service integration is enabled.

The legacy deterministic per-field environment names remain available as a CLI convenience for constructing `ENVIRONMENT_VARIABLE_CONTENT` references, but they are not the primary credential transport and are not automatically enumerated by the standard resolver.

## GitHub Actions materialization

GitHub Actions uses the same `ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS` document. A trusted first-party bridge narrows and materializes it before the actual build/publish processing:

```text
Gradle credential preflight
        -> enabled repositories in the requested context
        -> required profile/type union
        -> trusted GitHub credential bridge
        -> filter ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS to the required profile/type pairs
        -> materialize DIRECT_VALUE / FILE_CONTENT / SECRET_CONTENT / ENVIRONMENT_VARIABLE_CONTENT
        -> same ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS format with retained fields as DIRECT_VALUE
        -> actual Gradle processing
```

The preflight task is `resolveAlgitesRequiredCredentials`. It runs with `_TMP_ALGITES_CREDENTIAL_PREFLIGHT=true`, so repository metadata can be evaluated before repository authentication is required. Publication workflows request download and the appropriate upload contexts; post-release maintenance resolves `manage` credentials separately so deletion capability is not exposed to the publication phase. Ordinary CI requests download only.

The trusted bridge may receive the complete GitHub `secrets` context because filtering is its purpose. Only the credential profiles required by the preflight plan are forwarded to the actual Gradle processing. Secret values are never logged.

## CLI

Normal management/status commands do not print secret values. Two internal bootstrap commands write credential content to standard output so Gradle can capture it directly in memory; they are intended only for the repository bootstrap integration.

```text
algites-credentials set <profile> <basic|bearer|api-key|certificate>
algites-credentials status <profile> <basic|bearer|api-key|certificate>
algites-credentials remove <profile> <basic|bearer|api-key|certificate>
algites-credentials env <profile> <basic|bearer|api-key|certificate>
algites-credentials document-set [<json-file>|-]
algites-credentials document-status
algites-credentials document-remove
algites-credentials secret-set <secret-id>
algites-credentials secret-status <secret-id>
algites-credentials secret-remove <secret-id>
algites-credentials store-id
algites-credentials store-diagnostics
```

`document-set` stores exactly the universal `ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS` JSON document. With no filename, or with `-`, it reads the document from standard input, so generation and provisioning can be piped directly into the secure store without retaining a plaintext file.

`set` is a convenience editor for one profile/type entry of that same stored document. Values entered through `set` are stored as `DIRECT_VALUE`; entries for other types and profiles remain intact. Certificate provisioning requires the certificate file; a private-key file and private-key password are optional. Their contents are stored as `DIRECT_VALUE` in the universal document.

`env` prints deterministic environment-variable names for the fields of the profile. They are convenience names that can be referenced through `ENVIRONMENT_VARIABLE_CONTENT`; they are not a requirement of the universal document format.

`secret-set`, `secret-status`, and `secret-remove` manage named values in the local secure store for `SECRET_CONTENT` references. Named secrets use a separate internal namespace from the stored universal credential document.

The internal commands `bootstrap-document` and `bootstrap-secret <secret-id>` are used by Gradle Settings when secure-store lookup is required. They intentionally emit the requested content to standard output for direct process capture and are not interactive user-facing export commands.

## Repository binding

Repository endpoint definitions never contain secret values. They refer to a profile by ID:

```yaml
credentialProfiles:
  algites-java-private-release-download:
    type: basic

artifact:
  repositories:
    java:
      private:
        release:
          download:
            - id: algites-java-private-release-download
              url: https://example.invalid/maven/
              credentialProfile: algites-java-private-release-download
```

`credentialProfiles` and repository endpoint lists are inherited independently. A descendant may change only the profile type while keeping the inherited repository URL and endpoint state unchanged.
