#!/usr/bin/env bash
set -euo pipefail

# File/directory renames introduced after the complete 5.2 checkpoint 6.
# Run from the repository root on the checkpoint-6-complete tree before applying
# the remaining content edits from the points-1-to-5 checkpoint.

git mv -- devops/build/algitesbuild devops/build/modustrobuild

git mv -- devops/build/yamldefs/src/product/yamldefs/algites-artifact-manifest_1.yamldef.schema.json \
  devops/build/yamldefs/src/product/yamldefs/modustro-artifact-manifest_1.yamldef.schema.json
git mv -- devops/build/yamldefs/src/product/yamldefs/algites-artifact-manifest_1.yamldef.schema.json.meta.yml \
  devops/build/yamldefs/src/product/yamldefs/modustro-artifact-manifest_1.yamldef.schema.json.meta.yml

git mv -- gradle/tool/repository/algites-artifact-directory-metadata-resolver-wrapper.gradle.kts \
  gradle/tool/repository/modustro-artifact-directory-metadata-resolver-wrapper.gradle.kts
git mv -- gradle/tool/repository/algites-artifact-directory-metadata-resolver.gradle.kts \
  gradle/tool/repository/modustro-artifact-directory-metadata-resolver.gradle.kts
git mv -- gradle/tool/repository/algites-artifact-model.gradle.kts \
  gradle/tool/repository/modustro-artifact-model.gradle.kts
git mv -- gradle/tool/repository/algites-credential-values.gradle.kts \
  gradle/tool/repository/modustro-credential-values.gradle.kts
git mv -- gradle/tool/repository/algites-root-build.gradle.kts \
  gradle/tool/repository/modustro-root-build.gradle.kts
git mv -- gradle/tool/repository/algites-root-settings-discovery.gradle.kts \
  gradle/tool/repository/modustro-root-settings-discovery.gradle.kts
git mv -- gradle/tool/repository/algites-source-root-resolver.gradle.kts \
  gradle/tool/repository/modustro-source-root-resolver.gradle.kts

git mv -- gradle/tool/publication/algites-publication.gradle.kts \
  gradle/tool/publication/modustro-publication.gradle.kts
git mv -- gradle/tool/publication/algites-schema-site.gradle.kts \
  gradle/tool/publication/modustro-schema-site.gradle.kts

git mv -- gradle/tool/documentation/algites-docs-site.gradle.kts \
  gradle/tool/documentation/modustro-docs-site.gradle.kts
git mv -- gradle/tool/documentation/algites-docs-site-base.gradle.kts \
  gradle/tool/documentation/modustro-docs-site-base.gradle.kts
git mv -- gradle/tool/documentation/algites-docs-site-java.gradle.kts \
  gradle/tool/documentation/modustro-docs-site-java.gradle.kts
git mv -- gradle/tool/documentation/algites-docs-site-python.gradle.kts \
  gradle/tool/documentation/modustro-docs-site-python.gradle.kts
git mv -- gradle/tool/documentation/algites-docs-site-mps.gradle.kts \
  gradle/tool/documentation/modustro-docs-site-mps.gradle.kts
