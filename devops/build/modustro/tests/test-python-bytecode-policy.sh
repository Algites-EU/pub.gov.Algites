#!/usr/bin/env bash
set -euo pipefail

# Smoke-test the shared Gradle command wrapper without requiring a Gradle installation.
locTestDirectory="$(mktemp -d)"
trap 'rm -rf -- "$locTestDirectory"' EXIT

mkdir -p "$locTestDirectory/module"
printf 'value = 17\n' > "$locTestDirectory/module/sample_module.py"
cat > "$locTestDirectory/fake-gradlew" <<'INNER'
#!/usr/bin/env bash
set -euo pipefail
[[ "${PYTHONDONTWRITEBYTECODE:-}" == '1' ]] || {
  echo 'PYTHONDONTWRITEBYTECODE must be enabled for Gradle child processes.' >&2
  exit 1
}
python3 - "$PYTHON_SMOKE_TEST_DIRECTORY" <<'PY'
import pathlib
import sys

folder = pathlib.Path(sys.argv[1])
sys.path.insert(0, str(folder / 'module'))
import sample_module
assert sample_module.value == 17
assert not any((folder / 'module').rglob('*.pyc'))
assert not any((folder / 'module').rglob('__pycache__'))
PY
INNER
chmod +x "$locTestDirectory/fake-gradlew"
locScriptDirectory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
MODUSTRO_GRADLE_WRAPPER="$locTestDirectory/fake-gradlew" \
PYTHON_SMOKE_TEST_DIRECTORY="$locTestDirectory" \
  bash "$locScriptDirectory/../../../../gradle/tool/repository/modustro-gradle.sh" --version
printf 'PASS: Gradle wrapper disables Python bytecode writes.\n'
