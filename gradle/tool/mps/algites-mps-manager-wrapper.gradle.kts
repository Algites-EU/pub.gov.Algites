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

tasks.register("validateAlgitesMpsProjectMetadata") {
    group = "algites"
    description = "Validates MPS project migration metadata and resolves the effective MPS version."

    inputs.property("algitesMpsProjectDirectory", AIcReadAlgitesMpsWrapperExtra("algitesMpsProjectDirectory"))
    inputs.property("algitesMpsMigrationFile", AIcReadAlgitesMpsWrapperExtra("algitesMpsMigrationFile"))
    inputs.property("algitesMpsEffectiveVersion", AIcReadAlgitesMpsWrapperExtra("algitesMpsEffectiveVersion"))
    inputs.property("algitesMpsMetadataError", AIcReadAlgitesMpsWrapperExtra("algitesMpsMetadataError"))

    doLast {
        val locMetadataError = AIcReadAlgitesMpsWrapperExtra("algitesMpsMetadataError")
        val locProjectDirectory = AIcReadAlgitesMpsWrapperExtra("algitesMpsProjectDirectory")
        val locMigrationFile = AIcReadAlgitesMpsWrapperExtra("algitesMpsMigrationFile")
        val locBaselineValues = AIcReadAlgitesMpsWrapperExtra("algitesMpsBaselineVersionValues")
        val locMigratedValues = AIcReadAlgitesMpsWrapperExtra("algitesMpsMigratedVersionValues")
        val locEffectiveBaselineCode = AIcReadAlgitesMpsWrapperExtra("algitesMpsEffectiveBaselineCode")
        val locEffectiveVersion = AIcReadAlgitesMpsWrapperExtra("algitesMpsEffectiveVersion")
        val locEffectiveVersionSource = AIcReadAlgitesMpsWrapperExtra("algitesMpsEffectiveVersionSource")

        logger.lifecycle("Algites MPS project metadata:")
        logger.lifecycle(" - project directory: ${locProjectDirectory}")
        logger.lifecycle(" - migration file: ${locMigrationFile}")
        logger.lifecycle(" - project.baseline.version value(s): ${locBaselineValues.ifBlank { "<none>" }}")
        logger.lifecycle(" - project.migrated.version value(s): ${locMigratedValues.ifBlank { "<none>" }}")
        logger.lifecycle(" - effective baseline code: ${locEffectiveBaselineCode.ifBlank { "<none>" }}")
        logger.lifecycle(" - effective MPS version: ${locEffectiveVersion.ifBlank { "<none>" }}")
        logger.lifecycle(" - effective version source: ${locEffectiveVersionSource.ifBlank { "<none>" }}")

        if (locMetadataError.isNotBlank()) {
            throw GradleException(locMetadataError)
        }
    }
}

tasks.register("printAlgitesMpsConfiguration") {
    group = "algites"
    description = "Prints resolved Algites MPS runtime configuration."

    dependsOn("validateAlgitesMpsProjectMetadata")

    doLast {
        logger.lifecycle("Algites MPS runtime configuration:")
        logger.lifecycle(" - MPS version: ${AIcReadAlgitesMpsWrapperExtra("algitesMpsEffectiveVersion")}")
        logger.lifecycle(" - cache directory: ${AIcReadAlgitesMpsWrapperExtra("algitesMpsCacheDirectory")}")
        logger.lifecycle(" - managed root directory: ${AIcReadAlgitesMpsWrapperExtra("algitesMpsManagedRootDirectory")}")
        logger.lifecycle(" - download URL: ${AIcReadAlgitesMpsWrapperExtra("algitesMpsDownloadUrl")}")
    }
}

tasks.register("downloadAlgitesMpsRuntime") {
    group = "algites"
    description = "Downloads and extracts the managed MPS runtime if it is not already available."

    dependsOn("validateAlgitesMpsProjectMetadata")

    doLast {
        val locMpsVersion = AIcReadAlgitesMpsWrapperExtra("algitesMpsEffectiveVersion")
        val locManagedRootDirectory = File(AIcReadAlgitesMpsWrapperExtra("algitesMpsManagedRootDirectory"))
        val locDownloadUrl = AIcReadAlgitesMpsWrapperExtra("algitesMpsDownloadUrl")
        val locHomeMarkerFile = File(locManagedRootDirectory, ".algites-mps-home")
        val locCompleteMarkerFile = File(locManagedRootDirectory, ".algites-mps-installation-complete")

        if (locCompleteMarkerFile.isFile && locHomeMarkerFile.isFile) {
            val locMpsHome = File(locHomeMarkerFile.readText(Charsets.UTF_8).trim())
            if (locMpsHome.isDirectory) {
                logger.lifecycle("Managed MPS ${locMpsVersion} already available at: ${locMpsHome.absolutePath}")
                return@doLast
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
}

tasks.register("prepareAlgitesMpsRuntime") {
    group = "algites"
    description = "Prepares the managed MPS runtime for headless tasks."

    dependsOn("downloadAlgitesMpsRuntime")

    doLast {
        val locManagedRootDirectory = File(AIcReadAlgitesMpsWrapperExtra("algitesMpsManagedRootDirectory"))
        val locHomeMarkerFile = File(locManagedRootDirectory, ".algites-mps-home")
        require(locHomeMarkerFile.isFile) {
            "Managed MPS home marker not found: ${locHomeMarkerFile.absolutePath}"
        }

        val locMpsHome = File(locHomeMarkerFile.readText(Charsets.UTF_8).trim())
        require(locMpsHome.isDirectory) {
            "Managed MPS home does not exist: ${locMpsHome.absolutePath}"
        }

        logger.lifecycle("Prepared managed MPS home: ${locMpsHome.absolutePath}")
    }
}

tasks.register("runAlgitesMpsDocumentationGenerator") {
    group = "algites"
    description = "Runs a configured MPS documentation generator in headless mode."

    dependsOn("prepareAlgitesMpsRuntime")

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
            logger.lifecycle("No MPS documentation generator configured; skipping headless generator execution.")
            return@doLast
        }

        require(!locGeneratorCommand.isNullOrBlank()) {
            "MPS documentation generator '${locGeneratorId}' is configured, but algites.mps.documentation.generator.command is not set yet."
        }

        val locManagedRootDirectory = File(AIcReadAlgitesMpsWrapperExtra("algitesMpsManagedRootDirectory"))
        val locMpsHome = File(File(locManagedRootDirectory, ".algites-mps-home").readText(Charsets.UTF_8).trim())

        logger.lifecycle("Running MPS documentation generator:")
        logger.lifecycle(" - generator id: ${locGeneratorId}")
        logger.lifecycle(" - generator version: ${locGeneratorVersion ?: "<unspecified>"}")
        logger.lifecycle(" - MPS home: ${locMpsHome.absolutePath}")
        logger.lifecycle(" - command: ${locGeneratorCommand}")

        project.exec {
            workingDir = rootProject.projectDir
            environment("MPS_HOME", locMpsHome.absolutePath)
            commandLine(locGeneratorCommand.split(Regex("\\s+")))
        }
    }
}
