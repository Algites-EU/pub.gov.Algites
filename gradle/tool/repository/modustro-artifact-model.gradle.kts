/*
 * Deprecated compatibility adapter for the old Modustro Builder artifact metadata model.
 *
 * New code should apply:
 *   modustro-artifact-directory-metadata-resolver-wrapper.gradle.kts
 *
 * This file intentionally contains no repository scanning or YAML parsing.
 */

val locModustroResolverWrapperScript = rootProject.file("gradle/tool/repository/modustro-artifact-directory-metadata-resolver-wrapper.gradle.kts")
if (locModustroResolverWrapperScript.isFile) {
    apply(from = locModustroResolverWrapperScript)
} else {
    apply(from = uri("https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/repository/modustro-artifact-directory-metadata-resolver-wrapper.gradle.kts"))
}

@Suppress("UNCHECKED_CAST")
val locModustroResolvedArtifactDirectories = rootProject.extra["modustroResolvedArtifactDirectories"] as List<Map<String, Any?>>

val locModustroArtifactMetadata = locModustroResolvedArtifactDirectories.map { locArtifactDirectory ->
    linkedMapOf<String, Any?>(
        "structureKind" to locArtifactDirectory["structureKind"],
        "technologyKinds" to locArtifactDirectory["technologyKinds"],
        "name" to locArtifactDirectory["name"],
        "description" to locArtifactDirectory["description"],
        "variantId" to locArtifactDirectory["variantId"],
        "relativePath" to locArtifactDirectory["path"],
        "hasGradleBuild" to locArtifactDirectory["hasGradleBuild"],
        "projectPath" to locArtifactDirectory["gradleProjectPath"],
        "resourceEndpoints" to locArtifactDirectory["resourceEndpoints"],
        "dependencies" to locArtifactDirectory["dependencies"],
        "dependencyConstraints" to locArtifactDirectory["dependencyConstraints"],
        "environmentRequirements" to locArtifactDirectory["environmentRequirements"],
        "deleteSnapshotWhenReleased" to locArtifactDirectory["deleteSnapshotWhenReleased"],
        "nestedGradleSettingsBuildPolicy" to locArtifactDirectory["nestedGradleSettingsBuildPolicy"],
        "outputPublishing" to locArtifactDirectory["outputPublishing"]
    )
}

rootProject.extra["modustroArtifactMetadata"] = locModustroArtifactMetadata
rootProject.extra["modustroArtifactMetadataByProjectPath"] = locModustroArtifactMetadata
    .groupBy { locMetadata -> locMetadata["projectPath"] as String }
rootProject.extra["modustroTechnologyKinds"] = locModustroArtifactMetadata
    .flatMap { locMetadata ->
        @Suppress("UNCHECKED_CAST")
        (locMetadata["technologyKinds"] as? List<String>) ?: emptyList()
    }
    .distinct()
    .sorted()

if (tasks.findByName("printModustroArtifactModel") == null) {
    tasks.register("printModustroArtifactModel") {
        group = "modustro"
        description = "Prints Modustro Builder artifact metadata discovered in this repository."

        doLast {
            println("Modustro Builder artifact metadata for ${rootProject.name}:")
            locModustroArtifactMetadata.forEach { locMetadata ->
                println(
                    " - " +
                        locMetadata["structureKind"] +
                        " technologyKinds=" +
                        locMetadata["technologyKinds"] +
                        " path=" +
                        locMetadata["relativePath"] +
                        " gradle=" +
                        locMetadata["hasGradleBuild"]
                )
            }
        }
    }
}
