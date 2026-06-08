/*
 * Deprecated compatibility adapter for the old Algites artifact metadata model.
 *
 * New code should apply:
 *   algites-artifact-directory-metadata-resolver-wrapper.gradle.kts
 *
 * This file intentionally contains no repository scanning or YAML parsing.
 */

val locAlgitesResolverWrapperScript = rootProject.file("gradle/tool/repository/algites-artifact-directory-metadata-resolver-wrapper.gradle.kts")
if (locAlgitesResolverWrapperScript.isFile) {
    apply(from = locAlgitesResolverWrapperScript)
} else {
    apply(from = uri("https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/repository/algites-artifact-directory-metadata-resolver-wrapper.gradle.kts"))
}

@Suppress("UNCHECKED_CAST")
val locAlgitesResolvedArtifactDirectories = rootProject.extra["algitesResolvedArtifactDirectories"] as List<Map<String, Any?>>

val locAlgitesArtifactMetadata = locAlgitesResolvedArtifactDirectories.map { locArtifactDirectory ->
    linkedMapOf<String, Any?>(
        "kind" to locArtifactDirectory["kind"],
        "type" to locArtifactDirectory["type"],
        "name" to locArtifactDirectory["name"],
        "description" to locArtifactDirectory["description"],
        "relativePath" to locArtifactDirectory["path"],
        "hasGradleBuild" to locArtifactDirectory["hasGradleBuild"],
        "projectPath" to locArtifactDirectory["gradleProjectPath"]
    )
}

rootProject.extra["algitesArtifactMetadata"] = locAlgitesArtifactMetadata
rootProject.extra["algitesArtifactMetadataByProjectPath"] = locAlgitesArtifactMetadata
    .groupBy { locMetadata -> locMetadata["projectPath"] as String }
rootProject.extra["algitesArtifactTypes"] = locAlgitesArtifactMetadata
    .mapNotNull { locMetadata -> locMetadata["type"]?.toString() }
    .distinct()
    .sorted()

if (tasks.findByName("printAlgitesArtifactModel") == null) {
    tasks.register("printAlgitesArtifactModel") {
        group = "algites"
        description = "Prints Algites artifact metadata discovered in this repository."

        doLast {
            println("Algites artifact metadata for ${rootProject.name}:")
            locAlgitesArtifactMetadata.forEach { locMetadata ->
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
}
