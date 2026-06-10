/*
 * Algites shared MPS manager core script.
 *
 * Intended location in governance repository:
 *   gradle/tool/mps/algites-mps-manager.gradle.kts
 *
 * This script intentionally avoids registering build tasks. It resolves MPS
 * project metadata and exposes the result through Gradle extra properties so
 * that it can be used by wrapper scripts and, later, by settings scripts.
 */

import java.io.File

fun AIcReadAlgitesMpsStartParameterProperty(aName: String): String? {
    return gradle.startParameter.projectProperties[aName]
        ?.takeIf { locValue -> locValue.isNotBlank() }
        ?: System.getProperty(aName)?.takeIf { locValue -> locValue.isNotBlank() }
}

fun AIcReadAlgitesMpsEnvironmentVariableName(aPropertyName: String): String {
    return aPropertyName
        .uppercase()
        .replace('.', '_')
        .replace('-', '_')
}

fun AIcReadAlgitesMpsProperty(aName: String): String? {
    return AIcReadAlgitesMpsStartParameterProperty(aName)
        ?: extra.properties[aName]?.toString()?.takeIf { locValue -> locValue.isNotBlank() }
        ?: rootProject.extra.properties[aName]?.toString()?.takeIf { locValue -> locValue.isNotBlank() }
        ?: System.getenv(AIcReadAlgitesMpsEnvironmentVariableName(aName))?.takeIf { locValue -> locValue.isNotBlank() }
}

fun AIcReadAlgitesMpsXmlEntryValues(aXmlText: String, aKey: String): List<String> {
    val locRegex = Regex(
        """<entry\s+[^>]*\bkey\s*=\s*["']${Regex.escape(aKey)}["'][^>]*\bvalue\s*=\s*["']([^"']+)["'][^>]*/?>"""
    )
    return locRegex.findAll(aXmlText).map { locMatchResult -> locMatchResult.groupValues[1] }.toList()
}

fun AIcReadAlgitesMpsUniqueVersionCode(aValues: List<String>, aPropertyName: String): Pair<String?, String?> {
    if (aValues.isEmpty()) {
        return null to null
    }

    val locUniqueValues = aValues.distinct()
    if (locUniqueValues.size == 1) {
        return locUniqueValues.first() to null
    }

    return null to "Multiple different ${aPropertyName} values found in .mps/migration.xml: ${locUniqueValues.joinToString(", ")}"
}

fun AIcConvertAlgitesMpsBaselineCodeToVersion(aBaselineCode: String): Pair<String?, String?> {
    if (!aBaselineCode.matches(Regex("[0-9]{3,}"))) {
        return null to "Invalid MPS baseline code '${aBaselineCode}'. Expected at least three digits, for example 253."
    }

    val locYearPart = aBaselineCode.substring(0, 2).toInt()
    val locReleasePart = aBaselineCode.substring(2).toInt()

    if (locReleasePart <= 0) {
        return null to "Invalid MPS baseline code '${aBaselineCode}'. Release part must be greater than zero."
    }

    return "${2000 + locYearPart}.${locReleasePart}" to null
}

fun AIcResolveAlgitesMpsDefaultProjectDirectory(): File {
    val locConfiguredProjectDirectory = AIcReadAlgitesMpsProperty("algites.mps.project.dir")
    return if (locConfiguredProjectDirectory.isNullOrBlank()) {
        gradle.startParameter.currentDir.canonicalFile
    } else {
        File(locConfiguredProjectDirectory).canonicalFile
    }
}

fun AIcResolveAlgitesMpsCacheDirectory(): File {
    val locConfiguredCacheDirectory = AIcReadAlgitesMpsProperty("algites.mps.cache.dir")
    val locDefaultCacheDirectory = File(System.getProperty("java.io.tmpdir"), "algites/mps")
    return (locConfiguredCacheDirectory?.let { locValue -> File(locValue) } ?: locDefaultCacheDirectory).canonicalFile
}

fun AIcResolveAlgitesMpsDownloadUrl(aMpsVersion: String?): String? {
    val locExplicitUrl = AIcReadAlgitesMpsProperty("algites.mps.download.url")
    if (!locExplicitUrl.isNullOrBlank()) {
        return locExplicitUrl
    }

    if (aMpsVersion.isNullOrBlank()) {
        return null
    }

    val locTemplate = AIcReadAlgitesMpsProperty("algites.mps.download.url.template")
        ?: "https://download.jetbrains.com/mps/{version}/MPS-{version}.tar.gz"

    return locTemplate.replace("{version}", aMpsVersion)
}

fun AIcResolveAlgitesMpsProjectMetadataMap(aProjectDirectoryPath: String): Map<String, String> {
    val locAlgitesMpsProjectDirectory = File(aProjectDirectoryPath).canonicalFile
    val locAlgitesMpsMigrationFile = File(locAlgitesMpsProjectDirectory, ".mps/migration.xml")
    val locAlgitesMpsCacheDirectory = AIcResolveAlgitesMpsCacheDirectory()

    var locAlgitesMpsBaselineCode: String? = null
    var locAlgitesMpsMigratedCode: String? = null
    var locAlgitesMpsEffectiveBaselineCode: String? = null
    var locAlgitesMpsEffectiveVersion: String? = null
    var locAlgitesMpsEffectiveVersionSource: String? = null
    var locAlgitesMpsMetadataError: String? = null
    var locAlgitesMpsBaselineValues = emptyList<String>()
    var locAlgitesMpsMigratedValues = emptyList<String>()

    if (!locAlgitesMpsMigrationFile.isFile) {
        locAlgitesMpsMetadataError = "MPS migration metadata file not found: ${locAlgitesMpsMigrationFile.absolutePath}"
    } else {
        val locMigrationXmlText = locAlgitesMpsMigrationFile.readText(Charsets.UTF_8)
        locAlgitesMpsBaselineValues = AIcReadAlgitesMpsXmlEntryValues(locMigrationXmlText, "project.baseline.version")
        locAlgitesMpsMigratedValues = AIcReadAlgitesMpsXmlEntryValues(locMigrationXmlText, "project.migrated.version")

        val (locResolvedMigratedCode, locMigratedError) = AIcReadAlgitesMpsUniqueVersionCode(
            locAlgitesMpsMigratedValues,
            "project.migrated.version"
        )
        val (locResolvedBaselineCode, locBaselineError) = AIcReadAlgitesMpsUniqueVersionCode(
            locAlgitesMpsBaselineValues,
            "project.baseline.version"
        )

        locAlgitesMpsMigratedCode = locResolvedMigratedCode
        locAlgitesMpsBaselineCode = locResolvedBaselineCode
        locAlgitesMpsMetadataError = locMigratedError ?: locBaselineError

        if (locAlgitesMpsMetadataError == null) {
            locAlgitesMpsEffectiveBaselineCode = locResolvedMigratedCode ?: locResolvedBaselineCode
            locAlgitesMpsEffectiveVersionSource = if (locResolvedMigratedCode != null) {
                "project.migrated.version"
            } else if (locResolvedBaselineCode != null) {
                "project.baseline.version"
            } else {
                null
            }

            if (locAlgitesMpsEffectiveBaselineCode == null) {
                locAlgitesMpsMetadataError = "Neither project.migrated.version nor project.baseline.version found in ${locAlgitesMpsMigrationFile.absolutePath}"
            } else {
                val (locConvertedMpsVersion, locConversionError) = AIcConvertAlgitesMpsBaselineCodeToVersion(
                    locAlgitesMpsEffectiveBaselineCode!!
                )
                locAlgitesMpsEffectiveVersion = locConvertedMpsVersion
                locAlgitesMpsMetadataError = locConversionError
            }
        }
    }

    val locAlgitesMpsManagedRootDirectory = locAlgitesMpsEffectiveVersion
        ?.let { locMpsVersion -> File(locAlgitesMpsCacheDirectory, locMpsVersion).canonicalFile }
    val locAlgitesMpsDownloadUrl = AIcResolveAlgitesMpsDownloadUrl(locAlgitesMpsEffectiveVersion)

    return linkedMapOf(
        "algitesMpsProjectDirectory" to locAlgitesMpsProjectDirectory.absolutePath,
        "algitesMpsMigrationFile" to locAlgitesMpsMigrationFile.absolutePath,
        "algitesMpsMigrationFilePresent" to locAlgitesMpsMigrationFile.isFile.toString(),
        "algitesMpsBaselineVersionValues" to locAlgitesMpsBaselineValues.joinToString(","),
        "algitesMpsMigratedVersionValues" to locAlgitesMpsMigratedValues.joinToString(","),
        "algitesMpsBaselineVersionCode" to (locAlgitesMpsBaselineCode ?: ""),
        "algitesMpsMigratedVersionCode" to (locAlgitesMpsMigratedCode ?: ""),
        "algitesMpsEffectiveBaselineCode" to (locAlgitesMpsEffectiveBaselineCode ?: ""),
        "algitesMpsEffectiveVersion" to (locAlgitesMpsEffectiveVersion ?: ""),
        "algitesMpsEffectiveVersionSource" to (locAlgitesMpsEffectiveVersionSource ?: ""),
        "algitesMpsMetadataError" to (locAlgitesMpsMetadataError ?: ""),
        "algitesMpsCacheDirectory" to locAlgitesMpsCacheDirectory.absolutePath,
        "algitesMpsManagedRootDirectory" to (locAlgitesMpsManagedRootDirectory?.absolutePath ?: ""),
        "algitesMpsDownloadUrl" to (locAlgitesMpsDownloadUrl ?: "")
    )
}

val locAlgitesMpsDefaultProjectMetadata = AIcResolveAlgitesMpsProjectMetadataMap(
    AIcResolveAlgitesMpsDefaultProjectDirectory().absolutePath
)

locAlgitesMpsDefaultProjectMetadata.forEach { (locKey, locValue) ->
    extra[locKey] = locValue
    rootProject.extra[locKey] = locValue
}

val locAlgitesResolveMpsProjectMetadataMapFunction = fun(aProjectDirectoryPath: String): Map<String, String> {
    return AIcResolveAlgitesMpsProjectMetadataMap(aProjectDirectoryPath)
}

extra["algitesResolveMpsProjectMetadataMap"] = locAlgitesResolveMpsProjectMetadataMapFunction
rootProject.extra["algitesResolveMpsProjectMetadataMap"] = locAlgitesResolveMpsProjectMetadataMapFunction
