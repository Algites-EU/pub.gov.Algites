# Publication defaults diagnosis (2026-10-08)

The supplied source repositories do NOT contain `FailurePolicy` in any production YAML.
Both public and private repository defaults declare `ExecutionFailurePolicy`.

The Gradle settings plugin reads `/algites-repository-defaults-public.yml` **bundled
inside the `gradleinit` JAR**, not necessarily the YAML currently checked out.
The environment variables `ALGITES_REPOSITORY_PUBLIC_DEFAULTS_FILE`,
`ALGITES_REPOSITORY_GOVERNED_PUBLIC_DEFAULTS_FILE`, and
`ALGITES_REPOSITORY_PRIVATE_DEFAULTS_FILE` may override or supplement those defaults.

The changes in this checkpoint preserve strict rejection of `FailurePolicy` and
improve source diagnostics, reporting the relevant environment variable and actual
file path when invalid defaults are encountered. No backward compatibility alias
is introduced.

## Before rebuilding, inspect the effective source

```bash
printenv | grep '^ALGITES_REPOSITORY_.*DEFAULTS_FILE=' || true
find "$HOME/.gradle/caches/algites/gradleinit" -name public-defaults.yml -print \
  -exec grep -nE 'FailurePolicy|ExecutionFailurePolicy' '{}' ';'
```

Rebuild and upload the **gradleinit JAR/POM/MODULE as one consistent Maven snapshot**
if the bundled defaults in the resolved plugin JAR are outdated. The last
published `gradleinit` JAR may continue to be selected despite updating source
files in the working tree. Verify the published plugin and its transitive
`builder.coreimpl` dependency versions before testing.

No actual bootstrap JAR was built or uploaded by this checkpoint.
