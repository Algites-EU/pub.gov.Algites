# Modustro Gradle caching and publication review (2026-10-10)

This report covers the source snapshot `pub.gov.Algites(20261010-102640).zip` and the CI excerpts supplied for five Algites repositories. Build-cache safety must not be conflated with Gradle up-to-date checks or the configuration cache. The changes in this ZIP are focused on correctness, not on indiscriminately enabling caching.

## Observed messages and conclusions

| Diagnostic | Example task | Assessment | Safe next action |
|---|---|---|---|
| `Caching has not been enabled for the task` | `publishModustroPythonNativeBinary`, `publishModustroJavaNativeSources` | Expected. Publication tasks have external side effects, which must not be restored from the build cache. | Keep them non-cacheable; ensure idempotent publishing and explicit retry semantics. |
| `Task has not declared any outputs despite executing actions` | `publishModustroPythonNativeBinary` | Expected for an upload: the output is a remote repository state, not a stable local file. | Do not add a dummy local output merely to silence this message. |
| `Task is untracked because Gradle doesn't understand the data structures used to configure this task` | `generatePomFileForMavenJavaPublication` | Requires a focused Gradle 9.2.1 task input-model investigation. The POM contains dynamically configured dependency/licensing metadata; claiming that its output is cacheable without reproducing its input graph would be unsafe. | Inspect `--info` and the Gradle problems report after the three blocking failures are resolved. |
| `Task.upToDateWhen is false` | `generateMetadataFileForMavenJavaPublication` | Explicitly forces regeneration; not automatically a bug. | Check why publication metadata must be regenerated on each invocation before enabling up-to-date or caching. |
| `Gradle would require more information to cache this task` | `generatePythonDocsSite`, `generateJavaDocsSite`, documentation index tasks | The documentation tasks write to a shared site tree, use custom execution actions, and may update shared metadata sidecars. Caching the entire tree per task risks restoring stale or partial site state. | Split outputs into task-owned technology/artefact subtrees, move shared sidecars/indexes to a dedicated final task, then use typed, tracked input/output properties. |
| Normal compilation and test cache operations | `compileJava`, `compileTestJava`, `test`, individual Javadoc tasks | Cacheable once Gradle can accurately fingerprint the inputs and outputs. | Retain these; don't disable general build caching as a workaround. |

## Fixes implemented in the same revision

1. Python documentation generation checks `sphinx` and `autoapi` in the selected interpreter. If missing, it provisions a separate, pinned Python venv in the build directory and reuses it. This removes the dependency on a particular GitHub workflow having installed Sphinx first. No system Python packages are modified.
2. Git branch publication uses the already-authenticated `origin` only after checking that it names the repository declared by `PublicationUri`; Git error output is captured and authentication material redacted.
3. A pre-publication credentials validation task enumerates enabled native publication endpoints for the effective technology kinds and stability, and checks that each referenced profile is present and typed. It runs before publication tasks via `modustroPublicationBuildGate`. Repository-level profiles are also merged into the publication-task profile mapping as a fallback for partial bootstrap metadata.
4. Regression tests cover the Git remote match and credential redaction.

## Unresolved validation and follow-up

- **Actual CI still required:** the full Gradle 9.2.1 build has not been run in this environment. In particular, the credentials problem could originate from private governance defaults not contained in the public ZIP. The new preflight will produce an actionable failure *before* remote publication rather than silently manufacturing credentials.
- The Maven POM task and interleaved documentation writers have not been made cacheable. This is intentional until their inputs and outputs can be isolated and captured correctly.
- The Python documentation virtual environment is local task state and is not part of the documentation-site output. Recreating it when missing is safe; caching the remote package installation would require explicit reproducible toolchain modeling.
- A Git push can also fail with a branch conflict or insufficient token permissions. Capturing Git stderr and using the configured authenticated remote makes those cases observable instead of guessing from exit code 128 alone.
