/*
 * Algites canonical source-root resolver.
 *
 * Source types use one stable generation-suffix convention below
 * src/{product|develop}: <type>, <type>.gen, and <type>.extgen.
 */

import java.io.File

fun AIcAlgitesSourceRootRelativePaths(
    aProjectDirectory: File,
    aScope: String,
    aSourceType: String
): List<String> {
    require(aScope in setOf("product", "develop")) {
        "Unsupported Algites source scope '$aScope'."
    }
    require(aSourceType.isNotBlank() && '/' !in aSourceType && '\\' !in aSourceType) {
        "Invalid Algites source type '$aSourceType'."
    }

    return listOf("", ".gen", ".extgen")
        .map { locSuffix -> "src/$aScope/$aSourceType$locSuffix" }
}

fun AIcAlgitesExistingSourceRootRelativePaths(
    aProjectDirectory: File,
    aScope: String,
    aSourceType: String
): List<String> = AIcAlgitesSourceRootRelativePaths(aProjectDirectory, aScope, aSourceType)
    .filter { locRelativePath -> File(aProjectDirectory, locRelativePath).isDirectory }

fun AIcAlgitesSourceRootFiles(
    aProjectDirectory: File,
    aScope: String,
    aSourceType: String
): List<File> = AIcAlgitesSourceRootRelativePaths(aProjectDirectory, aScope, aSourceType)
    .map { locRelativePath -> File(aProjectDirectory, locRelativePath) }

rootProject.extra["algitesResolveSourceRootRelativePaths"] =
    { aProjectDirectory: File, aScope: String, aSourceType: String ->
        AIcAlgitesSourceRootRelativePaths(aProjectDirectory, aScope, aSourceType)
    }

rootProject.extra["algitesResolveExistingSourceRootRelativePaths"] =
    { aProjectDirectory: File, aScope: String, aSourceType: String ->
        AIcAlgitesExistingSourceRootRelativePaths(aProjectDirectory, aScope, aSourceType)
    }

rootProject.extra["algitesResolveSourceRootFiles"] =
    { aProjectDirectory: File, aScope: String, aSourceType: String ->
        AIcAlgitesSourceRootFiles(aProjectDirectory, aScope, aSourceType)
    }
