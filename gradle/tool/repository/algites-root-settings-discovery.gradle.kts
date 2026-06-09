/*
 * Algites repository settings discovery.
 *
 * This script is a thin Settings adapter over the shared artifact directory
 * metadata resolver. The resolver itself contains the repository scanning and
 * metadata inheritance logic.
 */

import java.io.File

val locAlgitesResolverCoreScript = File(rootDir, "gradle/tool/repository/algites-artifact-directory-metadata-resolver.gradle.kts")
if (locAlgitesResolverCoreScript.isFile) {
    apply(from = locAlgitesResolverCoreScript)
} else {
    apply(from = uri("https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/repository/algites-artifact-directory-metadata-resolver.gradle.kts"))
}

@Suppress("UNCHECKED_CAST")
val locAlgitesResolveMetadataMap = extra["algitesResolveArtifactDirectoryMetadataMap"] as (
    File,
    String?,
    String?,
    String?,
    String?
) -> Map<String, Any?>

val locAlgitesResolvedMetadata = locAlgitesResolveMetadataMap(
    rootDir,
    "",
    "current-with-subdirs",
    null,
    null
)

@Suppress("UNCHECKED_CAST")
val locAlgitesRepositoryMetadata = locAlgitesResolvedMetadata["repository"] as Map<String, Any?>
@Suppress("UNCHECKED_CAST")
val locAlgitesArtifactDirectories = locAlgitesResolvedMetadata["artifactDirectories"] as List<Map<String, Any?>>

rootProject.name =
    locAlgitesRepositoryMetadata["id"]?.toString()?.takeIf { it.isNotBlank() }
        ?: locAlgitesRepositoryMetadata["name"]?.toString()?.takeIf { it.isNotBlank() }
        ?: rootDir.name

val locIncludedProjectPaths = linkedSetOf<String>()

locAlgitesArtifactDirectories
    .filter { locArtifactDirectory -> locArtifactDirectory["hasGradleBuild"] == true }
    .forEach { locArtifactDirectory ->
        val locArtifactDirectoryPath = locArtifactDirectory["path"]?.toString() ?: return@forEach
        val locGradleProjectPath = locArtifactDirectory["gradleProjectPath"]?.toString() ?: return@forEach

        if (locGradleProjectPath != ":" && locIncludedProjectPaths.add(locGradleProjectPath)) {
            include(locGradleProjectPath)
            project(locGradleProjectPath).projectDir = File(rootDir, locArtifactDirectoryPath)
        }
    }

println(
    "Algites settings discovery included " +
        locIncludedProjectPaths.size +
        " Gradle artifact project(s)."
)
