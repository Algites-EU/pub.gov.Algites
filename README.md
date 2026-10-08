# pub.gov.Algites

Public Algites governance repository for specifications, shared DevOps/build infrastructure, schemas, public repository defaults, and public licensing governance.

## Licensing

The repository uses the Algites hierarchical licensing model. By public-governance default:

- software, executable build infrastructure, schemas, and other PRODUCT material use Apache License 2.0;
- documentation, specifications, architectural descriptions, and other DOCUMENTATION material use Creative Commons Attribution 4.0 International.

The generated root `LICENSE` summarizes the effective state and `LICENSES/` contains the complete canonical texts used anywhere in the repository. The machine-readable governance source is under `licensing/` together with any hierarchical `license-usage.yml` overrides.


## Modustro Builder

The Gradle-independent next-generation build model is developed under `devops/build/modustro/builder`. Its architecture and staged migration are specified in `specs/Modustro-Builder-Architecture-Specification.md`.


### Canonical-definition source sidecars

Create only missing `.meta.yml` user sidecars alongside discovered canonical definitions
(`yamldefs`, `jsondefs`, `xmldefs`) by explicitly running
`./gradlew generateMissingModustroSchemaSidecars`. To opt in when staging the schema
site, add `-Pmodustro.schemas.generateMissingUserSidecars=true` to
`generateModustroSchemaSite` or `publishModustroSchemaSite`. Existing sidecars are never
modified, and this does not create remote S3 deployment metadata. See
`specs/Algites-Artifact-Developer-Reference.md` §3.2 and
`devops/build/modustro/PUBLICATIONS.md`.

### Python bytecode cache in Gradle builds

Managed Python subprocesses run with `PYTHONDONTWRITEBYTECODE=1`; existing
`__pycache__`, `.pyc`, and `.pyo` resources are ignored for runtime classpath
normalization and Python package-build input tracking. See
`devops/build/modustro/PUBLICATIONS.md` for the exact scope and verification.

### Build-record finalization and private Maven repositories

Maven build-record metadata is computed from the locally materialized published
content, never by an anonymous download of the published JAR or sources JAR.
See `devops/build/modustro/PUBLICATIONS.md` for the contract and bootstrap steps.
