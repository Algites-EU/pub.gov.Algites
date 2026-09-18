# Algites credential subsystem

This artifact set provides the public, bootstrap-safe credential subsystem used by Algites build infrastructure.
No secret value is stored in `pub.gov.Algites`; only profile definitions, credential-type contracts, resolver logic, CLI code, and operating-system store adapters are public.

## Artifacts

- `coreintf` — stable credential profile/value/provider/store API and closed credential-type model.
- `coreimpl` — environment-first resolver, ServiceLoader store discovery, diagnostics, versioned credential codec, and persistent credential facade.
- `cli` — cross-platform interactive provisioning/status CLI.
- `winstore` — Windows Credential Manager backend through Advapi32/JNA.
- `macstore` — macOS Keychain backend through Security/CoreFoundation and JNA.
- `secretservicestore` — Freedesktop Secret Service backend through D-Bus; no `secret-tool` executable is required.

All artifacts use Java 17 and package namespace `eu.algites.tool.build.credentials`.

## Credential profiles

A credential profile is a non-secret, inherited configuration object. Its ID uses canonical lowercase dash-separated form. The profile type determines both the required secret fields and the authentication mechanism available to TechnologyKind repository adapters.

Supported v1 types are a closed enum:

| type | required secret fields | optional secret fields |
| --- | --- | --- |
| `basic` | `USERNAME`, `PASSWORD` | — |
| `bearer` | `TOKEN` | — |
| `api-key` | `API_KEY` | — |
| `client-certificate` | `CERTIFICATE`, `PRIVATE_KEY` | `PRIVATE_KEY_PASSWORD` |

A profile may also contain non-secret `configuration` values. For example an `api-key` profile used through an HTTP-header adapter can declare `configuration.headerName`.

Credential types are a core storage/resolution contract. Concrete repository adapters may support only a subset of them. In the current governance implementation Java/Maven download and upload support `basic`, `bearer`, and `api-key`; Python/Twine upload supports `basic`; Python download and MPS repository adapters are not yet implemented. Unsupported endpoint/type combinations fail explicitly.

## Environment binding

Environment variable names are deterministic and include profile ID, effective credential type, and field:

```text
ALGITES_CREDENTIAL_<NORMALIZED_PROFILE_ID>_<CREDENTIAL_TYPE>_<FIELD>
```

Examples:

```text
ALGITES_CREDENTIAL_ALGITES_JAVA_PRIVATE_RELEASE_DOWNLOAD_BASIC_USERNAME
ALGITES_CREDENTIAL_ALGITES_JAVA_PRIVATE_RELEASE_DOWNLOAD_BASIC_PASSWORD
ALGITES_CREDENTIAL_ALGITES_JAVA_PRIVATE_RELEASE_DOWNLOAD_BEARER_TOKEN
ALGITES_CREDENTIAL_ALGITES_JAVA_PRIVATE_RELEASE_DOWNLOAD_CLIENT_CERTIFICATE_CERTIFICATE
ALGITES_CREDENTIAL_ALGITES_JAVA_PRIVATE_RELEASE_DOWNLOAD_CLIENT_CERTIFICATE_PRIVATE_KEY
ALGITES_CREDENTIAL_ALGITES_JAVA_PRIVATE_RELEASE_DOWNLOAD_CLIENT_CERTIFICATE_PRIVATE_KEY_PASSWORD
```

The environment provider is checked first and is the normal CI/headless binding. If no environment credential is present, the resolver checks the highest-priority available operating-system secure store.

## Persistent storage

The secure-store key is qualified by both profile ID and credential type:

```text
<profile-id>/<credential-type>
```

Changing a profile from `basic` to `bearer` therefore does not reinterpret or overwrite the previous `basic` credential. Each profile/type pair is persisted as one opaque, versioned blob, avoiding partially updated multi-field credentials.

Persistent backends are discovered through `ServiceLoader`. Each backend reports structured availability state and remediation information instead of only a boolean.

- Windows: Windows Credential Manager.
- macOS: Keychain.
- Linux desktop: `org.freedesktop.secrets` through the Freedesktop Secret Service D-Bus API. Compatible providers include GNOME Keyring, Secret-Service-capable KWallet implementations, and KeePassXC when its Secret Service integration is enabled.
- CI/headless: no OS store is required when the profile is supplied through the canonical environment variables.

If no persistent backend is available, diagnostics explain the backend state and the profile-specific environment variables that can be supplied instead.

## CLI

The CLI never prints secret values.

```text
algites-credentials set <profile> <basic|bearer|api-key|client-certificate>
algites-credentials status <profile> <basic|bearer|api-key|client-certificate>
algites-credentials remove <profile> <basic|bearer|api-key|client-certificate>
algites-credentials env <profile> <basic|bearer|api-key|client-certificate>
algites-credentials store-id
algites-credentials store-diagnostics
```

`set` requires an interactive console. Client-certificate provisioning reads certificate/private-key contents from the paths supplied interactively and stores the contents, not the source path.

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
