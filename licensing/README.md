# Algites public licensing governance

This directory is the public governance source for reusable license definitions and public-repository licensing defaults.

- `license-definitions.yml` defines canonical license metadata and source text files.
- `texts/` contains canonical license texts.
- `defaults/license-usage.yml` defines the public default usage state.

Repository-local `licensing/license-definitions.yml` files may define additional licenses for their subtree. A definition that reuses an already known license id must be identical to the higher-level definition; silently replacing the meaning or text of an existing id is forbidden.

Repository and subtree `license-usage.yml` files enable or disable licenses. The effective state is inherited from the repository root towards the target subtree.

## Lifecycle validation

`checkAlgitesLicensing` is always strict. Artifact lifecycle tasks use `verifyAlgitesLicensing`; its default mode is `strict`, while snapshot deployment explicitly selects `-Palgites.licensing.validationMode=warn`. A normal build or release therefore fails on stale/mismatched licensing materialization, while a snapshot reports the same problems as warnings and continues. Release automation performs the strict licensing check before creating the release tag.
