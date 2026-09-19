#!/usr/bin/env python3
import json
import os
from pathlib import Path
import sys
import uuid

CREDENTIAL_TYPES = {
    "basic": {
        "required": ("username", "password"),
        "optional": (),
    },
    "bearer": {
        "required": ("token",),
        "optional": (),
    },
    "api-key": {
        "required": ("apiKey",),
        "optional": (),
    },
    "certificate": {
        "required": ("certificate",),
        "optional": ("privateKey", "privateKeyPassword"),
    },
}

VALUE_SOURCES = {
    "DIRECT_VALUE",
    "FILE_CONTENT",
    "SECRET_CONTENT",
    "ENVIRONMENT_VARIABLE_CONTENT",
}


def fail(message: str) -> None:
    print(f"::error::{message}")
    raise SystemExit(1)


def parse_json_object(raw: str, label: str, allow_empty: bool = False) -> dict:
    if not raw:
        if allow_empty:
            return {}
        fail(f"{label} is empty.")
    try:
        value = json.loads(raw)
    except json.JSONDecodeError as exc:
        fail(f"{label} is not valid JSON: {exc}")
    if not isinstance(value, dict):
        fail(f"{label} must be a JSON object.")
    return value


def load_plan(path_value: str) -> dict:
    if not path_value:
        fail("Required credential plan file was not supplied.")
    path = Path(path_value)
    if not path.is_file():
        fail(f"Required credential plan file does not exist: {path}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"Cannot read required credential plan file '{path}': {exc}")
    if not isinstance(value, dict):
        fail("Required credential plan must be a JSON object.")
    return value


def materialize_value(profile_id: str, credential_type: str, field: str, item: object, secrets: dict) -> str:
    if not isinstance(item, dict):
        fail(f"Credential '{profile_id}/{credential_type}/{field}' must be an object.")
    source = item.get("source")
    reference = item.get("value")
    if source not in VALUE_SOURCES:
        fail(
            f"Credential '{profile_id}/{credential_type}/{field}' uses unsupported source '{source}'. "
            f"Supported sources: {', '.join(sorted(VALUE_SOURCES))}."
        )
    if not isinstance(reference, str):
        fail(f"Credential '{profile_id}/{credential_type}/{field}' property 'value' must be a string.")

    if source == "DIRECT_VALUE":
        return reference
    if not reference:
        fail(f"Credential '{profile_id}/{credential_type}/{field}' reference must not be empty for source '{source}'.")
    if source == "FILE_CONTENT":
        path = Path(reference)
        if not path.is_absolute():
            path = Path(os.environ.get("GITHUB_WORKSPACE", os.getcwd())) / path
        if not path.is_file():
            fail(f"Credential '{profile_id}/{credential_type}/{field}' references missing file '{path}'.")
        try:
            return path.read_text(encoding="utf-8")
        except OSError as exc:
            fail(f"Cannot read credential file '{path}': {exc}")
    if source == "SECRET_CONTENT":
        if reference not in secrets:
            fail(f"Credential '{profile_id}/{credential_type}/{field}' references unavailable GitHub secret '{reference}'.")
        value = secrets[reference]
        if value is None:
            fail(f"Credential '{profile_id}/{credential_type}/{field}' references empty GitHub secret '{reference}'.")
        return str(value)
    if source == "ENVIRONMENT_VARIABLE_CONTENT":
        value = os.environ.get(reference)
        if value is None:
            fail(
                f"Credential '{profile_id}/{credential_type}/{field}' references unavailable environment variable '{reference}'."
            )
        return value
    fail(f"Credential source '{source}' is not implemented.")
    return ""


def mask_github_value(value: str) -> None:
    if not value:
        return
    escaped = value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    print(f"::add-mask::{escaped}")


def write_github_env(name: str, value: str) -> None:
    github_env = os.environ.get("GITHUB_ENV")
    if not github_env:
        fail("GITHUB_ENV is not available.")
    delimiter = f"ALGITES_{uuid.uuid4().hex}"
    while delimiter in value:
        delimiter = f"ALGITES_{uuid.uuid4().hex}"
    with open(github_env, "a", encoding="utf-8") as target:
        target.write(f"{name}<<{delimiter}\n{value}\n{delimiter}\n")


plan = load_plan(os.environ.get("ALGITES_REQUIRED_CREDENTIALS_FILE", ""))
required = plan.get("credentials", [])
if not isinstance(required, list):
    fail("Required credential plan property 'credentials' must be an array.")

raw_credentials = os.environ.get("ALGITES_CREDENTIALS_JSON", "")
credentials = parse_json_object(raw_credentials, "ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS", allow_empty=not required)
secrets = parse_json_object(
    os.environ.get("ALGITES_CREDENTIAL_SECRETS_JSON", ""),
    "ALGITES_CREDENTIAL_SECRETS_JSON",
    allow_empty=True,
)

materialized = {}
for requirement in required:
    if not isinstance(requirement, dict):
        fail("Each required credential entry must be an object.")
    profile_id = requirement.get("profileId")
    credential_type = requirement.get("type")
    if not isinstance(profile_id, str) or not profile_id:
        fail("Required credential entry is missing profileId.")
    if credential_type not in CREDENTIAL_TYPES:
        fail(f"Required credential '{profile_id}' uses unsupported type '{credential_type}'.")

    profile = credentials.get(profile_id)
    if not isinstance(profile, dict):
        fail(f"Required credential profile '{profile_id}' is not present in ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS.")
    typed_values = profile.get(credential_type)
    if not isinstance(typed_values, dict):
        fail(f"Required credential profile '{profile_id}' does not contain type '{credential_type}'.")

    contract = CREDENTIAL_TYPES[credential_type]
    supported_fields = set(contract["required"]) | set(contract["optional"])
    unsupported_fields = set(typed_values) - supported_fields
    if unsupported_fields:
        fail(
            f"Credential profile '{profile_id}' type '{credential_type}' contains unsupported fields: "
            + ", ".join(sorted(unsupported_fields))
        )

    output_fields = {}
    for field in contract["required"]:
        if field not in typed_values:
            fail(f"Credential profile '{profile_id}' type '{credential_type}' is missing required field '{field}'.")
        value = materialize_value(profile_id, credential_type, field, typed_values[field], secrets)
        mask_github_value(value)
        output_fields[field] = {
            "source": "DIRECT_VALUE",
            "value": value,
        }
    for field in contract["optional"]:
        if field in typed_values:
            value = materialize_value(profile_id, credential_type, field, typed_values[field], secrets)
            mask_github_value(value)
            output_fields[field] = {
                "source": "DIRECT_VALUE",
                "value": value,
            }

    materialized.setdefault(profile_id, {})[credential_type] = output_fields

output = json.dumps(materialized, separators=(",", ":"), ensure_ascii=False)
write_github_env("ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS", output)
print(f"Algites credential bridge materialized {len(required)} required credential profile/type pair(s).")
