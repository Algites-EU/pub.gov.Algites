/*
 * Modustro Builder canonical source-root resolver.
 *
 * Source types use one stable generation-suffix convention below
 * src/{product|develop}: <type>, <type>.gen, and <type>.extgen.
 */

import java.io.File

fun AIcModustroSourceRootRelativePaths(
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

fun AIcModustroExistingSourceRootRelativePaths(
    aProjectDirectory: File,
    aScope: String,
    aSourceType: String
): List<String> = AIcModustroSourceRootRelativePaths(aProjectDirectory, aScope, aSourceType)
    .filter { locRelativePath -> File(aProjectDirectory, locRelativePath).isDirectory }

fun AIcModustroSourceRootFiles(
    aProjectDirectory: File,
    aScope: String,
    aSourceType: String
): List<File> = AIcModustroSourceRootRelativePaths(aProjectDirectory, aScope, aSourceType)
    .map { locRelativePath -> File(aProjectDirectory, locRelativePath) }

rootProject.extra["modustroResolveSourceRootRelativePaths"] =
    { aProjectDirectory: File, aScope: String, aSourceType: String ->
        AIcModustroSourceRootRelativePaths(aProjectDirectory, aScope, aSourceType)
    }

rootProject.extra["modustroResolveExistingSourceRootRelativePaths"] =
    { aProjectDirectory: File, aScope: String, aSourceType: String ->
        AIcModustroExistingSourceRootRelativePaths(aProjectDirectory, aScope, aSourceType)
    }

rootProject.extra["modustroResolveSourceRootFiles"] =
    { aProjectDirectory: File, aScope: String, aSourceType: String ->
        AIcModustroSourceRootFiles(aProjectDirectory, aScope, aSourceType)
    }
