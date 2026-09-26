# Algites build YAML definitions

This artifact owns the versioned machine-readable schemas for Algites repository and artifact YAML metadata.

The controlled schema sources live in:

```text
src/product/yamldefs/
```

Schema contract versions are part of the file name from the first version (`_1`, `_2`, ...).

The artifact supports multiple technology kinds:

```yaml
artifact:
  technologyKinds: [java, python]
```

The same schema files are therefore packaged for both ecosystems without copying the controlled source files.

Current derived publication identities are:

```text
Java groupId:    eu.algites.gov.pub
Java artifactId: pub.gov.Algites_devops.build.yamldefs

Python distribution:
algites-pub-gov-algites-devops-build-yamldefs

Python import namespace:
algites.pub.gov.algites.devops.build.yamldefs
```

The Python package tree below `src/product/python.gen` is generated during the build and must not be committed.


## Artifact manifest schema

`algites-artifact-manifest_1.schema.json` defines the deterministic TechnologyKind-neutral `algites-artifact-manifest.yml` embedded into distributed Algites artifacts and published with generated artifact documentation. It contains logical artifact identity plus SHA-256 hashes of the effective source descriptor hierarchy, but excludes timestamps, Git identities, CI run identifiers, credentials, and other execution-context values that would change independently of the governed artifact sources.

## Credential document schema

`algites-credentials_1.schema.json` defines the provider-independent `ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS` document, including the closed credential types, type-specific fields, and the `direct_value`, `file_content`, `secret_content`, and `environment_variable_content` value-source contract.

## Licensing schemas

`algites-license-definitions_1.schema.json` defines canonical license definitions stored in `licensing/license-definitions.yml`.

`algites-license-usage_1.schema.json` defines hierarchical `license-usage.yml` usage files with the `product` and `documentation` content kinds and explicit `enabled` state.
