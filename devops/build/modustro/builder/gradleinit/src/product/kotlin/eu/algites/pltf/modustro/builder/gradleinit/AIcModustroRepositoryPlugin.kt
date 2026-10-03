package eu.algites.pltf.modustro.builder.gradleinit

import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*

class AIcModustroRepositoryPlugin : Plugin<Project> {
    override fun apply(aProject: Project) = with(aProject) {
        require(aProject == rootProject) { "Modustro repository plugin must be applied to the root Project." }
        val aRuntime = gradle.extra["modustroGradleRuntime"] as AIcModustroGradleRuntime
        aRuntime.install(extensions.extraProperties)
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

        val locAlgitesResolvedMetadata = aRuntime.resolvedMetadata ?: locAlgitesResolveMetadataMap(
            aRuntime.repositoryRoot ?: rootProject.projectDir,
            "", "current-with-subdirs",
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

                val locArtifactDirectoryPath = providers.gradleProperty("directory.path").map { locPath ->
                    if (aRuntime.buildRootRelativePath == ".") locPath else
                        aRuntime.buildRootRelativePath + "/" + locPath.trim('/').takeUnless { it == "." }.orEmpty()
                }.orElse(aRuntime.buildRootRelativePath)
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
                            (aRuntime.repositoryRoot ?: rootProject.projectDir),
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
                            (aRuntime.repositoryRoot ?: rootProject.projectDir),
                            aRuntime.buildRootRelativePath,
                            "current-with-subdirs",
                            locRepositoryNameOverride.get(),
                            locRepositoryVisibilityOverride.get(),
                            locOutputKind.get()
                        )
                    )
                }
            }
        }

    }
}
