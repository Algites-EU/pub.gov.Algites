/*
 * Algites repository settings discovery.
 *
 * Intended location in governance repository:
 *   gradle/tool/repository/algites-root-settings-discovery.gradle.kts
 *
 * This script is meant to be applied from settings.gradle.kts after the
 * repository-specific pluginManagement and dependencyResolutionManagement
 * blocks have been declared.
 */

fun readAlgitesRepositoryId(aRootDirectory: File): String {
    val locRepositoryConfigurationFile = listOf(
        File(aRootDirectory, "algites-source-repository.yml"),
        File(aRootDirectory, "algites-source-repository.yaml")
    ).firstOrNull { locFile -> locFile.isFile }

    if (locRepositoryConfigurationFile != null) {
        val locYamlText = locRepositoryConfigurationFile.readText(Charsets.UTF_8)
        val locRepositoryBlockText = Regex(
            pattern = "(?ms)^\\s*(?:sourceRepository|repository)\\s*:\\s*(.*?)(?=^\\S|\\z)"
        ).find(locYamlText)?.groupValues?.get(1) ?: locYamlText

        val locRepositoryId = Regex("(?m)^\\s*id\\s*:\\s*([^#\\r\\n]+)")
            .find(locRepositoryBlockText)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.removeSurrounding("\"")
            ?.removeSurrounding("'")

        if (!locRepositoryId.isNullOrBlank()) {
            return locRepositoryId
        }
    }

    return aRootDirectory.name
}

fun isIgnoredAlgitesDiscoveryPath(aFile: File): Boolean {
    val locRelativePath = aFile.relativeTo(rootDir).invariantSeparatorsPath
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

fun isAlgitesArtifactConfigurationFile(aFile: File): Boolean =
    aFile.isFile &&
        (
            aFile.name == "algites-artifact.yml" ||
                aFile.name == "algites-artifact.yaml" ||
                aFile.name == "algites-artifact-set.yml" ||
                aFile.name == "algites-artifact-set.yaml"
            )

fun toAlgitesGradleProjectPath(aDirectory: File): String =
    ":" + aDirectory
        .relativeTo(rootDir)
        .invariantSeparatorsPath
        .split('/')
        .filter { locPathElement -> locPathElement.isNotBlank() }
        .joinToString(":")

rootProject.name = readAlgitesRepositoryId(rootDir)

val locArtifactConfigurationFiles = rootDir
    .walkTopDown()
    .filter { locFile -> isAlgitesArtifactConfigurationFile(locFile) }
    .filterNot { locFile -> isIgnoredAlgitesDiscoveryPath(locFile) }
    .sortedBy { locFile -> locFile.relativeTo(rootDir).invariantSeparatorsPath }
    .toList()

val locIncludedProjectPaths = linkedSetOf<String>()

locArtifactConfigurationFiles.forEach { locArtifactConfigurationFile ->
    val locArtifactRootDirectory = locArtifactConfigurationFile.parentFile
    val locBuildFile = listOf(
        File(locArtifactRootDirectory, "build.gradle.kts"),
        File(locArtifactRootDirectory, "build.gradle")
    ).firstOrNull { locFile -> locFile.isFile }

    if (locArtifactRootDirectory != rootDir && locBuildFile != null) {
        val locProjectPath = toAlgitesGradleProjectPath(locArtifactRootDirectory)
        if (locIncludedProjectPaths.add(locProjectPath)) {
            include(locProjectPath)
            project(locProjectPath).projectDir = locArtifactRootDirectory
        }
    }
}

println(
    "Algites settings discovery included " +
        locIncludedProjectPaths.size +
        " Gradle artifact project(s)."
)
