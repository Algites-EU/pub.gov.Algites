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
