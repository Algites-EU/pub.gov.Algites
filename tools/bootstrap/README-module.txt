After building coreimpl JAR and generating its Maven POM, stage both in the same version directory and run:
python3 tools/bootstrap/prepare-coreimpl-module.py /path/to/maven/version/directory
The script generates a matching .module and checksums. Never use a .module with a different JAR.
