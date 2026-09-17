/*
 * Algites repository settings discovery.
 *
 * This script is a thin Settings adapter over the shared artifact directory
 * metadata resolver. It includes discovered Gradle projects and exposes the
 * effective Java download repositories required by those projects.
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

val locJavaDownloadRepositories = linkedSetOf<Pair<String, String>>()

@Suppress("UNCHECKED_CAST")
fun AIcCollectJavaDownloadRepositories(aRepositories: Any?) {
    val locRepositories = aRepositories as? Map<String, String> ?: return
    listOf("release", "snapshot").forEach { locStability ->
        val locUrl = locRepositories["java.$locStability.download"]?.trim()?.takeIf { it.isNotBlank() }
        if (locUrl != null) {
            locJavaDownloadRepositories.add(locStability to locUrl)
        }
    }
}

AIcCollectJavaDownloadRepositories(locAlgitesRepositoryMetadata["repositories"])
locAlgitesArtifactDirectories.forEach { locArtifactDirectory ->
    AIcCollectJavaDownloadRepositories(locArtifactDirectory["repositories"])
}

val locRepositoryUser = providers.gradleProperty("ALGITES_REPO_USER").orNull
    ?: providers.environmentVariable("ALGITES_REPO_USER").orNull
val locRepositoryPassword = providers.gradleProperty("ALGITES_REPO_PASS").orNull
    ?: providers.environmentVariable("ALGITES_REPO_PASS").orNull

dependencyResolutionManagement.repositories {
    locJavaDownloadRepositories.forEachIndexed { locIndex, locEntry ->
        val locStability = locEntry.first
        val locUrl = locEntry.second
        maven {
            name = "algitesResolvedJava${locStability.replaceFirstChar { it.titlecase() }}Download${locIndex + 1}"
            url = uri(locUrl)
            mavenContent {
                if (locStability == "snapshot") {
                    snapshotsOnly()
                } else {
                    releasesOnly()
                }
            }
            if (!locRepositoryUser.isNullOrBlank() && !locRepositoryPassword.isNullOrBlank()) {
                credentials {
                    username = locRepositoryUser
                    password = locRepositoryPassword
                }
            }
        }
    }
}

println(
    "Algites settings discovery included " +
        locIncludedProjectPaths.size +
        " Gradle artifact project(s) and " +
        locJavaDownloadRepositories.size +
        " resolved Java download repository target(s)."
)
