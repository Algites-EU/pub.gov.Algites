package eu.algites.tool.build.policy.base

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.getByType
import java.io.File

class AIcAlgitesPolicyBasePlugin : Plugin<Project> {

    override fun apply(aProject: Project) {

        /*
         * Build directory redirection.
         */
        aProject.layout.buildDirectory.set(
            aProject.rootProject.layout.projectDirectory.dir(
                "run/bld/gradle/${aProject.name}"
            )
        )

        /*
         * Source layout convention for Java and Gradle plugin projects.
         */
        aProject.plugins.withId("java") {
            configureSourceLayout(aProject)
        }
        aProject.plugins.withId("java-gradle-plugin") {
            configureSourceLayout(aProject)
        }

        val locMetadataFiles = findAlgitesMetadataFiles(aProject)
        val locVersionContext = readVersionContext(locMetadataFiles)
        val locComputedVersion = computeVersion(locVersionContext)

        if (aProject.version != Project.DEFAULT_VERSION &&
            aProject.version.toString() != locComputedVersion
        ) {
            error(
                """
                Algites governance violation:
                Project version is explicitly set to '${aProject.version}'
                but Algites policy requires version '$locComputedVersion'
                from Algites YAML versionContext.
                Metadata files: ${locMetadataFiles.joinToString(", ") { it.name }}.
                """.trimIndent()
            )
        }

        aProject.version = locComputedVersion
    }

    private fun configureSourceLayout(aProject: Project) {

        val locSourceSets = aProject.extensions.getByType<SourceSetContainer>()

        locSourceSets.getByName("main").java.setSrcDirs(
            listOf(
                "src/product/java",
                "src/product/javagen",
                "src/product/javaextgen",
                "src/product/kotlin"
            )
        )
        locSourceSets.getByName("main").resources.setSrcDirs(
            listOf(
                "src/product/resources",
                "src/product/config",
                "src/product/configgen",
                "src/product/configextgen",
                "src/product/loader"
            )
        )

        locSourceSets.getByName("test").java.setSrcDirs(
            listOf(
                "src/develop/java",
                "src/develop/javagen",
                "src/develop/javaextgen",
                "src/develop/kotlin"
            )
        )
        locSourceSets.getByName("test").resources.setSrcDirs(
            listOf(
                "src/develop/resources",
                "src/develop/config",
                "src/develop/configgen",
                "src/develop/configextgen",
                "src/develop/loader"
            )
        )
    }

    private fun findAlgitesMetadataFiles(aProject: Project): List<File> {

        val locCandidateNamesByScope = listOf(
            listOf(
                "algites-source-repository.yml",
                "algites-source-repository.yaml"
            ),
            listOf(
                "algites-artifact-set.yml",
                "algites-artifact-set.yaml"
            ),
            listOf(
                "algites-artifact.yml",
                "algites-artifact.yaml"
            )
        )

        val locSearchDirectories = getProjectPathDirectories(aProject)
        val locMetadataFiles = mutableListOf<File>()

        locSearchDirectories.forEach { locDirectory ->
            locCandidateNamesByScope.forEach { locCandidateNames ->
                val locMetadataFile = locCandidateNames
                    .map { File(locDirectory, it) }
                    .firstOrNull { it.isFile }

                if (locMetadataFile != null) {
                    locMetadataFiles.add(locMetadataFile)
                }
            }
        }

        if (locMetadataFiles.isEmpty()) {
            val locAllCandidateNames = locCandidateNamesByScope.flatten()
            error(
                """
                Algites governance error: missing Algites metadata YAML.
                Expected one of: ${locAllCandidateNames.joinToString(", ")}.
                Search path: ${locSearchDirectories.joinToString(" -> ") { it.path }}.
                Obsolete properties file algites-source-repository.properties is no longer supported.
                """.trimIndent()
            )
        }

        return locMetadataFiles
    }

    private fun getProjectPathDirectories(aProject: Project): List<File> {

        val locRootDirectory = aProject.rootProject.projectDir.canonicalFile
        val locProjectDirectory = aProject.projectDir.canonicalFile

        require(locProjectDirectory.toPath().startsWith(locRootDirectory.toPath())) {
            "Algites governance error: project directory '${locProjectDirectory.path}' is not inside root project directory '${locRootDirectory.path}'."
        }

        val locRelativePath = locRootDirectory.toPath().relativize(locProjectDirectory.toPath())
        val locDirectories = mutableListOf(locRootDirectory)
        var locCurrentDirectory = locRootDirectory

        locRelativePath.forEach { locPathPart ->
            locCurrentDirectory = File(locCurrentDirectory, locPathPart.toString())
            locDirectories.add(locCurrentDirectory)
        }

        return locDirectories
    }

    private fun readVersionContext(aMetadataFiles: List<File>): AIcVersionContext {

        val locYamlValues = readMergedSimpleYamlValues(aMetadataFiles)
        val locMetadataFileNames = aMetadataFiles.joinToString(", ") { it.name }

        val locLane = locYamlValues["versionContext.lane"]
            ?: error("Missing YAML value: versionContext.lane in merged Algites metadata: $locMetadataFileNames")

        val locRevision = locYamlValues["versionContext.revision"]
            ?: error("Missing YAML value: versionContext.revision in merged Algites metadata: $locMetadataFileNames")

        val locQualifierLabel = locYamlValues["versionContext.qualifierLabel"]
            ?: ""

        require(!locLane.endsWith(".x")) {
            "Algites governance error: versionContext.lane must use the new lane format, for example '1.1', not '$locLane'."
        }

        require(locLane.matches(Regex("^[0-9]+\\.[0-9]+$"))) {
            "Algites governance error: versionContext.lane must have format '<major>.<minor>', for example '1.1'. Actual value: '$locLane'."
        }

        require(locRevision.matches(Regex("^[0-9]+$"))) {
            "Algites governance error: versionContext.revision must be a non-negative integer. Actual value: '$locRevision'."
        }

        require(locQualifierLabel.isEmpty() || locQualifierLabel.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._-]*$"))) {
            "Algites governance error: versionContext.qualifierLabel contains invalid characters. Actual value: '$locQualifierLabel'."
        }

        return AIcVersionContext(
            locLane,
            locRevision,
            locQualifierLabel
        )
    }

    private fun computeVersion(aVersionContext: AIcVersionContext): String {

        val locBaseVersion = "${aVersionContext.lane}.${aVersionContext.revision}"

        return if (aVersionContext.qualifierLabel.isBlank()) {
            locBaseVersion
        } else {
            "$locBaseVersion-${aVersionContext.qualifierLabel}"
        }
    }

    private fun readMergedSimpleYamlValues(aFiles: List<File>): Map<String, String> {

        val locMergedValues = linkedMapOf<String, String>()

        aFiles.forEach { locFile ->
            locMergedValues.putAll(readSimpleYamlValues(locFile))
        }

        return locMergedValues
    }

    private fun readSimpleYamlValues(aFile: File): Map<String, String> {

        val locValues = linkedMapOf<String, String>()
        val locPathByIndent = sortedMapOf<Int, String>()

        aFile.readLines().forEach { aLine ->
            val locWithoutComment = stripYamlComment(aLine)
            if (locWithoutComment.isBlank()) {
                return@forEach
            }

            val locIndent = locWithoutComment.indexOfFirst { !it.isWhitespace() }
            if (locIndent < 0) {
                return@forEach
            }

            val locTrimmedLine = locWithoutComment.trim()
            val locSeparatorIndex = locTrimmedLine.indexOf(':')
            if (locSeparatorIndex <= 0) {
                return@forEach
            }

            val locKey = locTrimmedLine.substring(0, locSeparatorIndex).trim()
            val locRawValue = locTrimmedLine.substring(locSeparatorIndex + 1).trim()

            locPathByIndent.keys
                .filter { it >= locIndent }
                .toList()
                .forEach { locPathByIndent.remove(it) }

            val locParentPath = locPathByIndent.entries
                .lastOrNull { it.key < locIndent }
                ?.value

            val locCurrentPath = if (locParentPath == null) {
                locKey
            } else {
                "$locParentPath.$locKey"
            }

            if (locRawValue.isEmpty()) {
                locPathByIndent[locIndent] = locCurrentPath
            } else {
                locValues[locCurrentPath] = unquoteYamlScalar(locRawValue)
            }
        }

        return locValues
    }

    private fun stripYamlComment(aLine: String): String {

        var locInsideSingleQuote = false
        var locInsideDoubleQuote = false

        aLine.forEachIndexed { aIndex, aCharacter ->
            when (aCharacter) {
                '\'' -> {
                    if (!locInsideDoubleQuote) {
                        locInsideSingleQuote = !locInsideSingleQuote
                    }
                }
                '"' -> {
                    if (!locInsideSingleQuote) {
                        locInsideDoubleQuote = !locInsideDoubleQuote
                    }
                }
                '#' -> {
                    if (!locInsideSingleQuote && !locInsideDoubleQuote) {
                        return aLine.substring(0, aIndex)
                    }
                }
            }
        }

        return aLine
    }

    private fun unquoteYamlScalar(aValue: String): String {

        val locTrimmedValue = aValue.trim()

        return if ((locTrimmedValue.startsWith("\"") && locTrimmedValue.endsWith("\"")) ||
            (locTrimmedValue.startsWith("'") && locTrimmedValue.endsWith("'"))
        ) {
            locTrimmedValue.substring(1, locTrimmedValue.length - 1)
        } else {
            locTrimmedValue
        }
    }

    private data class AIcVersionContext(
        val lane: String,
        val revision: String,
        val qualifierLabel: String
    )
}
