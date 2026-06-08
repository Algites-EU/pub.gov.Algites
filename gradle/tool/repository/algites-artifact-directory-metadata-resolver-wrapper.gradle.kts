/*
 * Algites artifact directory metadata resolver Project wrapper.
 *
 * This script applies the settings-compatible resolver core, resolves the
 * repository once for Project/build usage, exposes the model through extra,
 * and registers command-line tasks.
 */

import java.io.File

val locAlgitesResolverCoreScript = rootProject.file("gradle/tool/repository/algites-artifact-directory-metadata-resolver.gradle.kts")
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

@Suppress("UNCHECKED_CAST")
val locAlgitesResolveMetadataText = extra["algitesResolveArtifactDirectoryMetadataText"] as (
    File,
    String?,
    String?,
    String?,
    String?,
    String?
) -> String

@Suppress("UNCHECKED_CAST")
val locAlgitesFlattenMetadata = extra["algitesFlattenArtifactDirectoryMetadata"] as (Map<String, Any?>) -> Map<String, String>

val locAlgitesResolvedMetadata = locAlgitesResolveMetadataMap(
    rootProject.projectDir,
    "",
    "current-with-subdirs",
    providers.gradleProperty("repository.name").orNull,
    providers.gradleProperty("repository.visibility").orNull
)
val locAlgitesResolvedProperties = locAlgitesFlattenMetadata(locAlgitesResolvedMetadata)

@Suppress("UNCHECKED_CAST")
val locAlgitesResolvedRepository = locAlgitesResolvedMetadata["repository"] as Map<String, Any?>
@Suppress("UNCHECKED_CAST")
val locAlgitesResolvedArtifactDirectories = locAlgitesResolvedMetadata["artifactDirectories"] as List<Map<String, Any?>>

rootProject.extra["algitesResolvedArtifactDirectoryMetadata"] = locAlgitesResolvedMetadata
rootProject.extra["algitesResolvedArtifactDirectoryMetadataProperties"] = locAlgitesResolvedProperties
rootProject.extra["algitesResolvedRepositoryMetadata"] = locAlgitesResolvedRepository
rootProject.extra["algitesResolvedArtifactDirectories"] = locAlgitesResolvedArtifactDirectories
rootProject.extra["algitesResolvedArtifactDirectoriesByGradleProjectPath"] = locAlgitesResolvedArtifactDirectories
    .associateBy { locArtifactDirectory -> locArtifactDirectory["gradleProjectPath"]?.toString() ?: "" }
rootProject.extra["algitesResolvedArtifactDirectoriesByPath"] = locAlgitesResolvedArtifactDirectories
    .associateBy { locArtifactDirectory -> locArtifactDirectory["path"]?.toString() ?: "" }

if (tasks.findByName("resolveAlgitesArtifactDirectoryMetadata") == null) {
    tasks.register("resolveAlgitesArtifactDirectoryMetadata") {
        group = "algites"
        description = "Resolves Algites artifact directory metadata."

        val locArtifactDirectoryPath = providers.gradleProperty("directory.path").orElse("")
        val locResolutionKind = providers.gradleProperty("resolution.kind").orElse("current-with-subdirs")
        val locOutputKind = providers.gradleProperty("output.kind").orElse("yaml")
        val locRepositoryNameOverride = providers.gradleProperty("repository.name").orElse("")
        val locRepositoryVisibilityOverride = providers.gradleProperty("repository.visibility").orElse("")

        inputs.dir(layout.projectDirectory)
        inputs.property("directory.path", locArtifactDirectoryPath)
        inputs.property("resolution.kind", locResolutionKind)
        inputs.property("output.kind", locOutputKind)
        inputs.property("repository.name", locRepositoryNameOverride)
        inputs.property("repository.visibility", locRepositoryVisibilityOverride)

        doLast {
            print(
                locAlgitesResolveMetadataText(
                    rootProject.projectDir,
                    locArtifactDirectoryPath.get(),
                    locResolutionKind.get(),
                    locRepositoryNameOverride.get(),
                    locRepositoryVisibilityOverride.get(),
                    locOutputKind.get()
                )
            )
        }
    }
}

if (tasks.findByName("resolveAllAlgitesArtifactDirectoryMetadata") == null) {
    tasks.register("resolveAllAlgitesArtifactDirectoryMetadata") {
        group = "algites"
        description = "Resolves all Algites artifact directory metadata."

        val locOutputKind = providers.gradleProperty("output.kind").orElse("yaml")
        val locRepositoryNameOverride = providers.gradleProperty("repository.name").orElse("")
        val locRepositoryVisibilityOverride = providers.gradleProperty("repository.visibility").orElse("")

        inputs.dir(layout.projectDirectory)
        inputs.property("output.kind", locOutputKind)
        inputs.property("repository.name", locRepositoryNameOverride)
        inputs.property("repository.visibility", locRepositoryVisibilityOverride)

        doLast {
            print(
                locAlgitesResolveMetadataText(
                    rootProject.projectDir,
                    "",
                    "current-with-subdirs",
                    locRepositoryNameOverride.get(),
                    locRepositoryVisibilityOverride.get(),
                    locOutputKind.get()
                )
            )
        }
    }
}
