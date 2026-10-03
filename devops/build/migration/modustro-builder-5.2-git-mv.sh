#!/usr/bin/env bash
set -euo pipefail

# Repository path renames for Modustro Builder 5.2.
# Run from the pub.gov.Algites repository root on the pre-5.2 stable baseline
# before applying the remaining content edits from the 5.2 checkpoint.

git mv -- algites-source-repository.yml modustro-source-repository.yml

git mv -- devops/build/algites-artifact-set.yml devops/build/modustro-artifact-set.yml
git mv -- devops/build/algitesbuild devops/build/modustrobuild

git mv -- devops/build/modustro/algites-artifact-set.yml \
  devops/build/modustro/modustro-artifact-set.yml
git mv -- devops/build/modustro/builder/algites-artifact-set.yml \
  devops/build/modustro/builder/modustro-artifact-set.yml
git mv -- devops/build/modustro/builder/coreintf/algites-artifact.yml \
  devops/build/modustro/builder/coreintf/modustro-artifact.yml
git mv -- devops/build/modustro/builder/coreimpl/algites-artifact.yml \
  devops/build/modustro/builder/coreimpl/modustro-artifact.yml
git mv -- devops/build/modustro/builder/structureddata/algites-artifact-set.yml \
  devops/build/modustro/builder/structureddata/modustro-artifact-set.yml
git mv -- devops/build/modustro/builder/structureddata/jackson/algites-artifact.yml \
  devops/build/modustro/builder/structureddata/jackson/modustro-artifact.yml
git mv -- devops/build/yamldefs/algites-artifact.yml \
  devops/build/yamldefs/modustro-artifact.yml

git mv -- devops/build/yamldefs/src/product/yamldefs/algites-artifact-manifest_1.yamldef.schema.json \
  devops/build/yamldefs/src/product/yamldefs/modustro-artifact-manifest_1.yamldef.schema.json
git mv -- devops/build/yamldefs/src/product/yamldefs/algites-artifact-manifest_1.yamldef.schema.json.meta.yml \
  devops/build/yamldefs/src/product/yamldefs/modustro-artifact-manifest_1.yamldef.schema.json.meta.yml
git mv -- devops/build/yamldefs/src/product/yamldefs/algites-artifact-set_1.yamldef.schema.json \
  devops/build/yamldefs/src/product/yamldefs/modustro-artifact-set_1.yamldef.schema.json
git mv -- devops/build/yamldefs/src/product/yamldefs/algites-artifact-set_1.yamldef.schema.json.meta.yml \
  devops/build/yamldefs/src/product/yamldefs/modustro-artifact-set_1.yamldef.schema.json.meta.yml
git mv -- devops/build/yamldefs/src/product/yamldefs/algites-artifact_1.yamldef.schema.json \
  devops/build/yamldefs/src/product/yamldefs/modustro-artifact_1.yamldef.schema.json
git mv -- devops/build/yamldefs/src/product/yamldefs/algites-artifact_1.yamldef.schema.json.meta.yml \
  devops/build/yamldefs/src/product/yamldefs/modustro-artifact_1.yamldef.schema.json.meta.yml
git mv -- devops/build/yamldefs/src/product/yamldefs/algites-source-repository_1.yamldef.schema.json \
  devops/build/yamldefs/src/product/yamldefs/modustro-source-repository_1.yamldef.schema.json
git mv -- devops/build/yamldefs/src/product/yamldefs/algites-source-repository_1.yamldef.schema.json.meta.yml \
  devops/build/yamldefs/src/product/yamldefs/modustro-source-repository_1.yamldef.schema.json.meta.yml

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
