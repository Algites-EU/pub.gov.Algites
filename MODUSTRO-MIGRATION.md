# Modustro migration and generator fixes

Repository descriptors use the current modustro-* names. Settings bootstrap the
compiled Gradleinit plugin; source generation and dependencies are declared in
the descriptors instead of duplicated module Gradle scripts.

YAML and JSON canonical definitions are compared structurally, then their
documentation and origins are merged. Local references, composition and Python
wire-name mapping are covered by regressions. XML definitions use a separate
xmldefs namespace where their contracts differ.

Bootstrap/publish the updated Defs Codegen before the updated Gradleinit plugin,
then activate the governance scripts. Both standalone bootstrap scripts and
artifact coordinates are documented in their repository READMEs. Remote
publication has not been performed.

Validation: 147 Java tests and 4 Python tests passed locally across the three
repositories. Packaging phases passed; external Cloudsmith access returned HTTP
403 during a repeated preflight, so final Python dependency checks use locally
built wheels. This does not establish remote GitHub Actions success.

## Removed repository-relative paths

Descriptor files in this list were renamed to modustro-*; module build scripts
were replaced by descriptor declarations; obsolete resolver scripts were removed.

```text
gradle/tool/repository/modustro-artifact-directory-metadata-resolver.gradle.kts
gradle/tool/repository/modustro-artifact-directory-metadata-resolver-wrapper.gradle.kts
gradle/tool/repository/modustro-root-settings-discovery.gradle.kts
gradle/tool/repository/modustro-credential-values.gradle.kts
```

## Legacy source-output endpoint IDs (2026-10-04)

Builder CoreImpl now migrates legacy source-output IDs before declaration
inheritance and effective validation. For example, in the
`java.native_source_output.public.upload` cell,
`algites-java-public-release-upload` becomes
`algites-java-native-source-output-public-release-upload`.
Both legacy and canonical amendment IDs resolve to the same source endpoint;
URL, CredentialProfile and provider adapter values remain inherited. Binary
output endpoints keep their established compatibility behavior. Incorrect
visibility/stability combinations still fail validation.

This requires publishing the rebuilt Builder CoreImpl, not just replacing the
Gradle script. The supplied Builder binary bundle contains the matching modules
and an upload script, so bootstrapping does not depend on a working CI publish.
The Java dependency preflight also excludes local generated file dependencies
(such as Gradle TestKit metadata); combined resolve/verify/package builds no
longer report a missing producer dependency for those files.

Python wheel/sdist packaging now merges handwritten, generated and external
source roots in an isolated temporary build workspace. Shared namespace packages
retain all modules; conflicting files fail explicitly. A regression test covers
namespace merging, resources and conflicts, and the generator wheels were tested
with their actual packaged modules. This fix is in the root Gradle build adapter.
