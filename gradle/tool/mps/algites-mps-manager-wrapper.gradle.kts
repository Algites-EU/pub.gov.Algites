/*
 * Algites shared MPS manager wrapper script.
 *
 * Intended location in governance repository:
 *   gradle/tool/mps/algites-mps-manager-wrapper.gradle.kts
 *
 * This script applies the core MPS manager and registers regular Gradle tasks
 * for validating MPS metadata, preparing a managed MPS installation and running
 * a future headless documentation generator.
 */

import org.gradle.api.GradleException
import java.io.File
import java.net.URI

val locAlgitesMpsManagerScript = (findProperty("algites.mps.managerScript") as String?)
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/mps/algites-mps-manager.gradle.kts"

apply(from = uri(locAlgitesMpsManagerScript))

@Suppress("UNCHECKED_CAST")
val locAlgitesResolveMpsProjectMetadataMap =
    extra.properties["algitesResolveMpsProjectMetadataMap"] as? ((String) -> Map<String, String>)
        ?: rootProject.extra.properties["algitesResolveMpsProjectMetadataMap"] as? ((String) -> Map<String, String>)
        ?: throw GradleException("Algites MPS manager did not export algitesResolveMpsProjectMetadataMap.")

fun AIcReadAlgitesMpsWrapperExtra(aName: String): String {
    return extra.properties[aName]?.toString()?.takeIf { locValue -> locValue.isNotBlank() }
        ?: rootProject.extra.properties[aName]?.toString()?.takeIf { locValue -> locValue.isNotBlank() }
        ?: ""
}

fun AIcReadAlgitesMpsWrapperProperty(aName: String): String? {
    return (findProperty(aName) as String?)?.takeIf { locValue -> locValue.isNotBlank() }
        ?: System.getProperty(aName)?.takeIf { locValue -> locValue.isNotBlank() }
        ?: System.getenv(aName.uppercase().replace('.', '_').replace('-', '_'))?.takeIf { locValue -> locValue.isNotBlank() }
}

fun AIcReadAlgitesMpsDocumentationGeneratorValue(aPropertyName: String, aExtraName: String): String? {
    return AIcReadAlgitesMpsWrapperProperty(aPropertyName)
        ?: extra.properties[aExtraName]?.toString()?.takeIf { locValue -> locValue.isNotBlank() }
        ?: rootProject.extra.properties[aExtraName]?.toString()?.takeIf { locValue -> locValue.isNotBlank() }
}

fun AIcFindAlgitesMpsHomeInInstallation(aRootDirectory: File): File? {
    if (File(aRootDirectory, "bin/mps.sh").isFile || File(aRootDirectory, "bin/mps").isFile) {
        return aRootDirectory
    }

    return aRootDirectory
        .walkTopDown()
        .maxDepth(4)
        .filter { locFile -> locFile.isDirectory }
        .firstOrNull { locDirectory ->
            File(locDirectory, "bin/mps.sh").isFile || File(locDirectory, "bin/mps").isFile
        }
}

fun AIcLogAlgitesMpsMetadata(aMetadata: Map<String, String>) {
    logger.lifecycle("Algites MPS project metadata:")
    logger.lifecycle(" - project directory: ${aMetadata["algitesMpsProjectDirectory"].orEmpty()}")
    logger.lifecycle(" - migration file: ${aMetadata["algitesMpsMigrationFile"].orEmpty()}")
    logger.lifecycle(" - project.baseline.version value(s): ${aMetadata["algitesMpsBaselineVersionValues"].orEmpty().ifBlank { "<none>" }}")
    logger.lifecycle(" - project.migrated.version value(s): ${aMetadata["algitesMpsMigratedVersionValues"].orEmpty().ifBlank { "<none>" }}")
    logger.lifecycle(" - effective baseline code: ${aMetadata["algitesMpsEffectiveBaselineCode"].orEmpty().ifBlank { "<none>" }}")
    logger.lifecycle(" - effective MPS version: ${aMetadata["algitesMpsEffectiveVersion"].orEmpty().ifBlank { "<none>" }}")
    logger.lifecycle(" - effective version source: ${aMetadata["algitesMpsEffectiveVersionSource"].orEmpty().ifBlank { "<none>" }}")
}

fun AIcValidateAlgitesMpsMetadata(aMetadata: Map<String, String>) {
    val locMetadataError = aMetadata["algitesMpsMetadataError"].orEmpty()
    if (locMetadataError.isNotBlank()) {
        throw GradleException(locMetadataError)
    }
}

fun AIcPrepareAlgitesMpsRuntime(aMetadata: Map<String, String>) {
    AIcValidateAlgitesMpsMetadata(aMetadata)

    val locMpsVersion = aMetadata["algitesMpsEffectiveVersion"].orEmpty()
    val locManagedRootDirectory = File(aMetadata["algitesMpsManagedRootDirectory"].orEmpty())
    val locDownloadUrl = aMetadata["algitesMpsDownloadUrl"].orEmpty()
    val locHomeMarkerFile = File(locManagedRootDirectory, ".algites-mps-home")
    val locCompleteMarkerFile = File(locManagedRootDirectory, ".algites-mps-installation-complete")

    if (locCompleteMarkerFile.isFile && locHomeMarkerFile.isFile) {
        val locMpsHome = File(locHomeMarkerFile.readText(Charsets.UTF_8).trim())
        if (locMpsHome.isDirectory) {
            logger.lifecycle("Managed MPS ${locMpsVersion} already available at: ${locMpsHome.absolutePath}")
            return
        }
    }

    require(locDownloadUrl.isNotBlank()) {
        "Cannot download MPS runtime because algitesMpsDownloadUrl is empty."
    }

    val locArchiveDirectory = File(locManagedRootDirectory, "archive")
    val locExtractDirectory = File(locManagedRootDirectory, "install")
    val locArchiveFile = File(locArchiveDirectory, "MPS-${locMpsVersion}.tar.gz")

    locArchiveDirectory.mkdirs()
    locExtractDirectory.mkdirs()

    if (!locArchiveFile.isFile) {
        logger.lifecycle("Downloading MPS ${locMpsVersion} from: ${locDownloadUrl}")
        URI(locDownloadUrl).toURL().openStream().use { locInputStream ->
            locArchiveFile.outputStream().use { locOutputStream ->
                locInputStream.copyTo(locOutputStream)
            }
        }
    } else {
        logger.lifecycle("Using cached MPS archive: ${locArchiveFile.absolutePath}")
    }

    logger.lifecycle("Extracting MPS ${locMpsVersion} to: ${locExtractDirectory.absolutePath}")
    locExtractDirectory.deleteRecursively()
    locExtractDirectory.mkdirs()

    project.copy {
        from(project.tarTree(project.resources.gzip(locArchiveFile)))
        into(locExtractDirectory)
    }

    val locMpsHome = AIcFindAlgitesMpsHomeInInstallation(locExtractDirectory)
        ?: throw GradleException("Cannot locate MPS home after extraction under: ${locExtractDirectory.absolutePath}")

    locHomeMarkerFile.writeText(locMpsHome.absolutePath, Charsets.UTF_8)
    locCompleteMarkerFile.writeText("MPS ${locMpsVersion}\n", Charsets.UTF_8)
    logger.lifecycle("Managed MPS ${locMpsVersion} prepared at: ${locMpsHome.absolutePath}")
}

fun AIcReadAlgitesMpsHome(aMetadata: Map<String, String>): File {
    val locManagedRootDirectory = File(aMetadata["algitesMpsManagedRootDirectory"].orEmpty())
    val locHomeMarkerFile = File(locManagedRootDirectory, ".algites-mps-home")
    require(locHomeMarkerFile.isFile) {
        "Managed MPS home marker not found: ${locHomeMarkerFile.absolutePath}"
    }

    val locMpsHome = File(locHomeMarkerFile.readText(Charsets.UTF_8).trim())
    require(locMpsHome.isDirectory) {
        "Managed MPS home does not exist: ${locMpsHome.absolutePath}"
    }
    return locMpsHome
}

tasks.register("validateAlgitesMpsProjectMetadata") {
    group = "algites"
    description = "Validates MPS project migration metadata and resolves the effective MPS version."

    inputs.property("algitesMpsProjectDirectory", AIcReadAlgitesMpsWrapperExtra("algitesMpsProjectDirectory"))
    inputs.property("algitesMpsMigrationFile", AIcReadAlgitesMpsWrapperExtra("algitesMpsMigrationFile"))
    inputs.property("algitesMpsEffectiveVersion", AIcReadAlgitesMpsWrapperExtra("algitesMpsEffectiveVersion"))
    inputs.property("algitesMpsMetadataError", AIcReadAlgitesMpsWrapperExtra("algitesMpsMetadataError"))

    doLast {
        val locMetadata = locAlgitesResolveMpsProjectMetadataMap(AIcReadAlgitesMpsWrapperExtra("algitesMpsProjectDirectory"))
        AIcLogAlgitesMpsMetadata(locMetadata)
        AIcValidateAlgitesMpsMetadata(locMetadata)
    }
}

tasks.register("printAlgitesMpsConfiguration") {
    group = "algites"
    description = "Prints resolved Algites MPS runtime configuration."

    dependsOn("validateAlgitesMpsProjectMetadata")

    doLast {
        val locMetadata = locAlgitesResolveMpsProjectMetadataMap(AIcReadAlgitesMpsWrapperExtra("algitesMpsProjectDirectory"))
        logger.lifecycle("Algites MPS runtime configuration:")
        logger.lifecycle(" - MPS version: ${locMetadata["algitesMpsEffectiveVersion"].orEmpty()}")
        logger.lifecycle(" - cache directory: ${locMetadata["algitesMpsCacheDirectory"].orEmpty()}")
        logger.lifecycle(" - managed root directory: ${locMetadata["algitesMpsManagedRootDirectory"].orEmpty()}")
        logger.lifecycle(" - download URL: ${locMetadata["algitesMpsDownloadUrl"].orEmpty()}")
    }
}

tasks.register("downloadAlgitesMpsRuntime") {
    group = "algites"
    description = "Downloads and extracts the managed MPS runtime if it is not already available."

    dependsOn("validateAlgitesMpsProjectMetadata")

    doLast {
        val locMetadata = locAlgitesResolveMpsProjectMetadataMap(AIcReadAlgitesMpsWrapperExtra("algitesMpsProjectDirectory"))
        AIcPrepareAlgitesMpsRuntime(locMetadata)
    }
}

tasks.register("prepareAlgitesMpsRuntime") {
    group = "algites"
    description = "Prepares the managed MPS runtime for headless tasks."

    dependsOn("downloadAlgitesMpsRuntime")

    doLast {
        val locMetadata = locAlgitesResolveMpsProjectMetadataMap(AIcReadAlgitesMpsWrapperExtra("algitesMpsProjectDirectory"))
        val locMpsHome = AIcReadAlgitesMpsHome(locMetadata)
        logger.lifecycle("Prepared managed MPS home: ${locMpsHome.absolutePath}")
    }
}

fun AIcRegisterAlgitesMpsProjectMetadataValidationTask(aTaskName: String, aProjectDirectoryPath: String): Any {
    return tasks.register(aTaskName) {
        group = "algites"
        description = "Validates MPS project migration metadata for ${aProjectDirectoryPath}."

        inputs.property("algitesMpsProjectDirectory", aProjectDirectoryPath)

        doLast {
            val locMetadata = locAlgitesResolveMpsProjectMetadataMap(aProjectDirectoryPath)
            AIcLogAlgitesMpsMetadata(locMetadata)
            AIcValidateAlgitesMpsMetadata(locMetadata)
        }
    }
}

fun AIcRegisterAlgitesMpsDocumentationGeneratorTask(aTaskName: String, aProjectDirectoryPath: String): Any {
    return tasks.register(aTaskName) {
        group = "algites"
        description = "Runs a configured MPS documentation generator for ${aProjectDirectoryPath}."

        inputs.property("algitesMpsProjectDirectory", aProjectDirectoryPath)

        doLast {
            val locGeneratorId = AIcReadAlgitesMpsDocumentationGeneratorValue(
                "algites.mps.documentation.generator.id",
                "algitesMpsDocumentationGeneratorId"
            )
            val locGeneratorVersion = AIcReadAlgitesMpsDocumentationGeneratorValue(
                "algites.mps.documentation.generator.version",
                "algitesMpsDocumentationGeneratorVersion"
            )
            val locGeneratorCommand = AIcReadAlgitesMpsDocumentationGeneratorValue(
                "algites.mps.documentation.generator.command",
                "algitesMpsDocumentationGeneratorCommand"
            )

            if (locGeneratorId.isNullOrBlank()) {
                logger.lifecycle("No MPS documentation generator configured; skipping headless generator execution for ${aProjectDirectoryPath}.")
                return@doLast
            }

            require(!locGeneratorCommand.isNullOrBlank()) {
                "MPS documentation generator '${locGeneratorId}' is configured, but algites.mps.documentation.generator.command is not set yet."
            }

            val locMetadata = locAlgitesResolveMpsProjectMetadataMap(aProjectDirectoryPath)
            AIcLogAlgitesMpsMetadata(locMetadata)
            AIcPrepareAlgitesMpsRuntime(locMetadata)
            val locMpsHome = AIcReadAlgitesMpsHome(locMetadata)
            val locProjectDirectory = File(aProjectDirectoryPath).canonicalFile

            logger.lifecycle("Running MPS documentation generator:")
            logger.lifecycle(" - generator id: ${locGeneratorId}")
            logger.lifecycle(" - generator version: ${locGeneratorVersion ?: "<unspecified>"}")
            logger.lifecycle(" - MPS home: ${locMpsHome.absolutePath}")
            logger.lifecycle(" - project directory: ${locProjectDirectory.absolutePath}")
            logger.lifecycle(" - command: ${locGeneratorCommand}")

            val locGeneratorCommandParts = locGeneratorCommand
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }

            require(locGeneratorCommandParts.isNotEmpty()) {
                "MPS documentation generator '${locGeneratorId}' has an empty command."
            }

            val locProcessBuilder = ProcessBuilder(locGeneratorCommandParts)
            locProcessBuilder.directory(locProjectDirectory)
            locProcessBuilder.environment()["MPS_HOME"] = locMpsHome.absolutePath
            locProcessBuilder.environment()["ALGITES_MPS_PROJECT_DIR"] = locProjectDirectory.absolutePath
            locProcessBuilder.inheritIO()

            val locProcess = locProcessBuilder.start()
            val locExitCode = locProcess.waitFor()

            require(locExitCode == 0) {
                "MPS documentation generator '${locGeneratorId}' failed with exit code ${locExitCode}."
            }
        }
    }
}

tasks.register("runAlgitesMpsDocumentationGenerator") {
    group = "algites"
    description = "Runs a configured MPS documentation generator in headless mode for the default MPS project directory."

    dependsOn(
        AIcRegisterAlgitesMpsDocumentationGeneratorTask(
            "runAlgitesMpsDocumentationGeneratorDefault",
            AIcReadAlgitesMpsWrapperExtra("algitesMpsProjectDirectory")
        )
    )
}

extra["algitesRegisterMpsProjectMetadataValidationTask"] = fun(aTaskName: String, aProjectDirectoryPath: String): Any {
    return AIcRegisterAlgitesMpsProjectMetadataValidationTask(aTaskName, aProjectDirectoryPath)
}

extra["algitesRegisterMpsDocumentationGeneratorTask"] = fun(aTaskName: String, aProjectDirectoryPath: String): Any {
    return AIcRegisterAlgitesMpsDocumentationGeneratorTask(aTaskName, aProjectDirectoryPath)
}
