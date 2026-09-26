# Algites Credential Bridge

GitHub Actions provider adapter for the universal Algites credential document.

The persistent/provider-independent credential document is supplied through `ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS`. Its structure is defined by `algites-credentials_1.schema.json`. The GitHub bridge does not invent credential profile names, types, or fields. A Gradle preflight first resolves the enabled repository endpoints for the current operation and writes the required profile/type pairs. The bridge then keeps only those pairs and materializes every retained field to `direct_value`.

Supported value sources are:

| source | `value` contains | materialized result |
| --- | --- | --- |
| `direct_value` | the credential content itself | the same content |
| `file_content` | a filesystem path | UTF-8 file content |
| `secret_content` | a GitHub Actions secret name | that secret's content |
| `environment_variable_content` | an environment-variable name | that variable's content |

`file_content` is therefore intentionally named after the **result of resolution**. Its `value` member is still the path used to obtain that content. The same rule applies to `secret_content` and `environment_variable_content`: `value` is the reference name, while the result is the referenced content.

The bridge receives the serialized GitHub `secrets` context through `_TMP_ALGITES_CREDENTIAL_SECRETS_JSON`. This is a trusted provider context, not a second credential-document format. It exists only so `secret_content` references can be resolved by exact secret name without enumerating or hard-coding credential profiles in workflow YAML.

The downstream Gradle process receives the same universal credential-document format through `ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS`, but only for required profile/type pairs and with all retained fields replaced by `direct_value`.

The action MUST NOT log credential contents. Materialized values are additionally registered with GitHub log masking before the reduced document is exported to subsequent steps.
