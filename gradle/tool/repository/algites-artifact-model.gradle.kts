/*
 * Algites artifact metadata model.
 *
 * Intended location in governance repository:
 *   gradle/tool/repository/algites-artifact-model.gradle.kts
 *
 * This script scans algites-artifact.yml and algites-artifact-set.yml files and
 * exposes the resulting metadata through rootProject.extra.
 */

fun readAlgitesYamlScalar(aYamlText: String, aBlockName: String?, aKey: String): String? {
    val locSearchText = if (aBlockName == null) {
        aYamlText
    } else {
        Regex("(?ms)^\\s*" + Regex.escape(aBlockName) + "\\s*:\\s*(.*?)(?=^\\S|\\z)")
            .find(aYamlText)
            ?.groupValues
            ?.get(1)
            ?: return null
    }

    return Regex("(?m)^\\s*" + Regex.escape(aKey) + "\\s*:\\s*([^#\\r\\n]+)")
        .find(locSearchText)
        ?.groupValues
        ?.get(1)
        ?.trim()
        ?.removeSurrounding("\"")
        ?.removeSurrounding("'")
}

fun isIgnoredAlgitesArtifactModelPath(aFile: File): Boolean {
    val locRelativePath = aFile.relativeTo(rootProject.projectDir).invariantSeparatorsPath
    val locIgnoredPathElements = setOf(
        ".git",
        ".gradle",
        ".idea",
        "build",
        "run",
        "docs-site",
        "source_gen",
        "source_gen.caches",
        "classes_gen",
        "out",
        "target"
    )

    return locRelativePath
        .split('/')
        .any { locPathElement -> locPathElement in locIgnoredPathElements }
}

fun isAlgitesArtifactModelConfigurationFile(aFile: File): Boolean =
    aFile.isFile &&
        (
            aFile.name == "algites-artifact.yml" ||
                aFile.name == "algites-artifact.yaml" ||
                aFile.name == "algites-artifact-set.yml" ||
                aFile.name == "algites-artifact-set.yaml"
            )

fun toAlgitesArtifactProjectPath(aDirectory: File): String =
    ":" + aDirectory
        .relativeTo(rootProject.projectDir)
        .invariantSeparatorsPath
        .split('/')
        .filter { locPathElement -> locPathElement.isNotBlank() }
        .joinToString(":")

val locArtifactMetadata = rootProject.projectDir
    .walkTopDown()
    .filter { locFile -> isAlgitesArtifactModelConfigurationFile(locFile) }
    .filterNot { locFile -> isIgnoredAlgitesArtifactModelPath(locFile) }
    .sortedBy { locFile -> locFile.relativeTo(rootProject.projectDir).invariantSeparatorsPath }
    .map { locConfigurationFile ->
        val locRootDirectory = locConfigurationFile.parentFile
        val locYamlText = locConfigurationFile.readText(Charsets.UTF_8)
        val locKind = if (locConfigurationFile.name.contains("artifact-set")) {
            "artifactSet"
        } else {
            "artifact"
        }
        val locType = readAlgitesYamlScalar(locYamlText, locKind, "type")
            ?: readAlgitesYamlScalar(locYamlText, null, "type")
            ?: "unknown"
        val locName = readAlgitesYamlScalar(locYamlText, locKind, "name")
            ?: readAlgitesYamlScalar(locYamlText, null, "name")
            ?: locRootDirectory.name
        val locDescription = readAlgitesYamlScalar(locYamlText, locKind, "description")
            ?: readAlgitesYamlScalar(locYamlText, null, "description")
            ?: ""
        val locHasGradleBuild = listOf(
            File(locRootDirectory, "build.gradle.kts"),
            File(locRootDirectory, "build.gradle")
        ).any { locFile -> locFile.isFile }
        val locRelativePath = locRootDirectory
            .relativeTo(rootProject.projectDir)
            .invariantSeparatorsPath
            .ifBlank { "." }

        linkedMapOf<String, Any>(
            "kind" to locKind,
            "type" to locType,
            "name" to locName,
            "description" to locDescription,
            "configurationFile" to locConfigurationFile.absolutePath,
            "rootDirectory" to locRootDirectory.absolutePath,
            "relativePath" to locRelativePath,
            "hasGradleBuild" to locHasGradleBuild,
            "projectPath" to if (locRootDirectory == rootProject.projectDir) ":" else toAlgitesArtifactProjectPath(locRootDirectory)
        )
    }
    .toList()

rootProject.extra["algitesArtifactMetadata"] = locArtifactMetadata

val locArtifactMetadataByProjectPath = locArtifactMetadata
    .groupBy { locMetadata -> locMetadata["projectPath"] as String }

rootProject.extra["algitesArtifactMetadataByProjectPath"] = locArtifactMetadataByProjectPath

val locArtifactTypes = locArtifactMetadata
    .map { locMetadata -> locMetadata["type"] as String }
    .distinct()
    .sorted()

rootProject.extra["algitesArtifactTypes"] = locArtifactTypes

tasks.register("printAlgitesArtifactModel") {
    group = "algites"
    description = "Prints Algites artifact metadata discovered in this repository."

    doLast {
        println("Algites artifact metadata for ${rootProject.name}:")
        locArtifactMetadata.forEach { locMetadata ->
            println(
                " - " +
                    locMetadata["kind"] +
                    " type=" +
                    locMetadata["type"] +
                    " path=" +
                    locMetadata["relativePath"] +
                    " gradle=" +
                    locMetadata["hasGradleBuild"]
            )
        }
    }
}
