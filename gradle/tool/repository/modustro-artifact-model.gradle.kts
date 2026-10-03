/*
 * Deprecated compatibility adapter for the old Modustro Builder artifact metadata model.
 *
 * New code should apply:
 *   the compiled eu.algites.pltf.modustro.builder.repository plugin
 *
 * This file intentionally contains no repository scanning or YAML parsing.
 */

rootProject.pluginManager.apply("eu.algites.pltf.modustro.builder.repository")

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
