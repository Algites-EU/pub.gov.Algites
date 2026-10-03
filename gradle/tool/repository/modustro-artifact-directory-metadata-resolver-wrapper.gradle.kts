/*
 * Modustro Builder artifact directory metadata resolver Project wrapper.
 *
 * This script applies the settings-compatible resolver core, resolves the
 * repository once for Project/build usage, exposes the model through extra,
 * and registers command-line tasks.
 */

import java.io.File

val locAlgitesResolverCoreScript = rootProject.file("gradle/tool/repository/modustro-artifact-directory-metadata-resolver.gradle.kts")
if (locAlgitesResolverCoreScript.isFile) {
    apply(from = locAlgitesResolverCoreScript)
} else {
    apply(from = uri("https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/repository/modustro-artifact-directory-metadata-resolver.gradle.kts"))
}

@Suppress("UNCHECKED_CAST")
val locAlgitesResolveMetadataMap = extra["modustroResolveArtifactDirectoryMetadataMap"] as (
    File,
    String?,
    String?,
    String?,
    String?
) -> Map<String, Any?>

@Suppress("UNCHECKED_CAST")
val locAlgitesResolveMetadataText = extra["modustroResolveArtifactDirectoryMetadataText"] as (
    File,
    String?,
    String?,
    String?,
    String?,
    String?
) -> String

@Suppress("UNCHECKED_CAST")
val locAlgitesFlattenMetadata = extra["modustroFlattenArtifactDirectoryMetadata"] as (Map<String, Any?>) -> Map<String, String>

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

rootProject.extra["modustroResolvedArtifactDirectoryMetadata"] = locAlgitesResolvedMetadata
rootProject.extra["modustroResolvedArtifactDirectoryMetadataProperties"] = locAlgitesResolvedProperties
rootProject.extra["modustroResolvedRepositoryMetadata"] = locAlgitesResolvedRepository
rootProject.extra["modustroResolvedArtifactDirectories"] = locAlgitesResolvedArtifactDirectories
rootProject.extra["modustroResolvedArtifactDirectoriesByGradleProjectPath"] = locAlgitesResolvedArtifactDirectories
    .associateBy { locArtifactDirectory -> locArtifactDirectory["gradleProjectPath"]?.toString() ?: "" }
rootProject.extra["modustroResolvedArtifactDirectoriesByPath"] = locAlgitesResolvedArtifactDirectories
    .associateBy { locArtifactDirectory -> locArtifactDirectory["path"]?.toString() ?: "" }

if (tasks.findByName("resolveModustroArtifactDirectoryMetadata") == null) {
    tasks.register("resolveModustroArtifactDirectoryMetadata") {
        group = "modustro"
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

if (tasks.findByName("resolveAllModustroArtifactDirectoryMetadata") == null) {
    tasks.register("resolveAllModustroArtifactDirectoryMetadata") {
        group = "modustro"
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
