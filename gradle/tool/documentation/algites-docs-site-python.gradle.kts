/*
 * Algites shared Python documentation site script.
 *
 * Intended location in governance repository:
 *   gradle/tool/documentation/algites-docs-site-python.gradle.kts
 *
 * Python API documentation is generated with Sphinx + Sphinx AutoAPI. AutoAPI
 * parses Python sources statically and therefore does not need to import the
 * documented project or install its runtime dependencies.
 */

import org.gradle.api.Action
import org.gradle.api.Task

val locAlgitesDocsBaseScript = (findProperty("algites.docs.baseScript") as String?)
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/algites-docs-site-base.gradle.kts"

apply(from = uri(locAlgitesDocsBaseScript))

val locArtifactDocsRoot = layout.projectDirectory.dir(
    (extra.properties["algitesArtifactDocsRootPath"] as String?)
        ?: (findProperty("algites.docs.artifactRoot") as String?)
        ?: "build/run/bld/algites-docs/site/generated/artifacts"
)
val locPublicationKind = (extra.properties["algitesDocsPublicationKind"] as String?) ?: "generated"
val locPublicationId = (extra.properties["algitesDocsPublicationId"] as String?) ?: "current"
val locPythonDocsRepositoryId = (extra.properties["algitesDocsResolvedRepositoryId"] as String?) ?: rootProject.name
val locPythonExecutable = (findProperty("algites.python.executable") as String?)
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?: System.getenv("ALGITES_PYTHON_EXECUTABLE")?.trim()?.takeIf { it.isNotBlank() }
    ?: "python3"
val locPythonDocsSnapshotInstanceId = (providers.gradleProperty("algites.snapshot.instanceId").orNull
    ?: providers.environmentVariable("ALGITES_SNAPSHOT_INSTANCE_ID").orNull)
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?.also { locValue ->
        if (!Regex("^[0-9]+$").matches(locValue)) {
            throw GradleException("Algites snapshot instance id must contain decimal digits only, but got '$locValue'.")
        }
    }

@Suppress("UNCHECKED_CAST")
val locAlgitesDocsResolvedArtifactDirectories =
    (extra.properties["algitesDocsResolvedArtifactDirectories"] as? List<Map<String, String?>>)
        ?: (rootProject.extra.properties["algitesDocsResolvedArtifactDirectories"] as? List<Map<String, String?>>)
        ?: emptyList()

@Suppress("UNCHECKED_CAST")
val locAlgitesDocsPublicationMetadata =
    (extra.properties["algitesDocsPublicationMetadata"] as? Map<String, String>)
        ?: (rootProject.extra.properties["algitesDocsPublicationMetadata"] as? Map<String, String>)
        ?: emptyMap()

data class AIcdPythonDocsSiteEntry(
    val locLocalArtifactId: String,
    val locArtifactCoordinateId: String,
    val locArtifactDirectory: File,
    val locSourceDirectories: List<File>,
    val locWorkingDirectory: File,
    val locArtifactPublicationDirectory: File,
    val locArtifactMetadata: Map<String, String>
) : java.io.Serializable

class AIcGeneratePythonDocsSiteAction(
    private val locPythonExecutable: String,
    private val locArtifactDocsRootFile: File,
    private val locEntries: List<AIcdPythonDocsSiteEntry>
) : Action<Task>, java.io.Serializable {

    override fun execute(aTask: Task) {
        var locGeneratedCount = 0

        locEntries.forEach { locEntry ->
            val locTargetDirectory = File(locEntry.locArtifactPublicationDirectory, "python")
            val locSphinxSourceDirectory = File(locEntry.locWorkingDirectory, "source")
            val locPythonSourceDirectories = locEntry.locSourceDirectories
                .filter { locDirectory ->
                    locDirectory.isDirectory && locDirectory.walkTopDown().any { locFile ->
                        locFile.isFile && locFile.extension.equals("py", ignoreCase = true)
                    }
                }

            locEntry.locWorkingDirectory.deleteRecursively()
            locTargetDirectory.deleteRecursively()
            locSphinxSourceDirectory.mkdirs()
            locTargetDirectory.mkdirs()

            val locTitle = locEntry.locLocalArtifactId
            val locUnderline = "=".repeat(locTitle.length.coerceAtLeast(1))
            val locIndexText = if (locPythonSourceDirectories.isEmpty()) {
                """
                $locTitle
                $locUnderline

                No Python API source files were found below ``src/product/python`` or ``src/product/python.gen`` for this artifact.
                """.trimIndent() + System.lineSeparator()
            } else {
                """
                $locTitle
                $locUnderline

                Python API reference generated from the artifact source tree.

                .. toctree::
                   :maxdepth: 2
                   :caption: API reference

                   autoapi/index
                """.trimIndent() + System.lineSeparator()
            }
            locSphinxSourceDirectory.resolve("index.rst").writeText(locIndexText, Charsets.UTF_8)

            val locAutoApiConfiguration = if (locPythonSourceDirectories.isEmpty()) {
                "extensions = ['sphinx.ext.napoleon']"
            } else {
                val locSourceList = locPythonSourceDirectories.joinToString(", ") { locDirectory ->
                    "r'${AIcPythonString(locDirectory.absolutePath)}'"
                }
                """
                extensions = ['sphinx.ext.napoleon', 'autoapi.extension']
                autoapi_type = 'python'
                autoapi_dirs = [$locSourceList]
                autoapi_root = 'autoapi'
                autoapi_add_toctree_entry = True
                autoapi_keep_files = True
                autoapi_options = ['members', 'undoc-members', 'show-inheritance', 'show-module-summary']
                """.trimIndent()
            }

            val locSphinxConfiguration = buildString {
                appendLine("project = '${AIcPythonString(locTitle)}'")
                appendLine("author = 'Algites'")
                appendLine(locAutoApiConfiguration)
                appendLine("napoleon_google_docstring = True")
                appendLine("napoleon_numpy_docstring = True")
                appendLine("html_theme = 'alabaster'")
            }
            locSphinxSourceDirectory.resolve("conf.py").writeText(
                locSphinxConfiguration,
                Charsets.UTF_8
            )

            val locProcess = ProcessBuilder(
                locPythonExecutable,
                "-m",
                "sphinx",
                "-b",
                "html",
                locSphinxSourceDirectory.absolutePath,
                locTargetDirectory.absolutePath
            )
                .directory(locEntry.locArtifactDirectory)
                .redirectErrorStream(true)
                .start()

            val locOutput = locProcess.inputStream.bufferedReader(Charsets.UTF_8).readText()
            val locExitCode = locProcess.waitFor()
            if (locOutput.isNotBlank()) {
                aTask.logger.lifecycle(locOutput.trimEnd())
            }
            check(locExitCode == 0) {
                "Python documentation generation failed for '${locEntry.locLocalArtifactId}' with exit code $locExitCode. " +
                    "Ensure Sphinx and sphinx-autoapi are installed for '$locPythonExecutable'."
            }

            if (locPythonSourceDirectories.isNotEmpty()) {
                val locAutoApiIndexFile = File(locTargetDirectory, "autoapi/index.html")
                check(locAutoApiIndexFile.isFile) {
                    "Python documentation generation for '${locEntry.locLocalArtifactId}' completed without producing " +
                        "the expected AutoAPI index '${locAutoApiIndexFile.absolutePath}'. " +
                        "Check the Sphinx AutoAPI source discovery/configuration instead of publishing an empty API site."
                }
                val locAutoApiSourceRoot = File(locSphinxSourceDirectory, "autoapi")
                val locGeneratedAutoApiSources = locAutoApiSourceRoot
                    .walkTopDown()
                    .filter { locFile -> locFile.isFile && locFile.extension.equals("rst", ignoreCase = true) }
                    .toList()
                check(locGeneratedAutoApiSources.any { locFile ->
                    locFile.relativeTo(locAutoApiSourceRoot).invariantSeparatorsPath != "index.rst"
                }) {
                    "Python documentation generation for '${locEntry.locLocalArtifactId}' produced an AutoAPI index " +
                        "but no module/package API pages. Check whether the Python source layout is discoverable by Sphinx AutoAPI."
                }
            }

            AIcWriteArtifactMetadataSidecar(locEntry.locArtifactPublicationDirectory, locEntry.locArtifactMetadata)
            locGeneratedCount++
        }

        aTask.logger.lifecycle("Python documentation generated at: ${locArtifactDocsRootFile.absolutePath}")
        aTask.logger.lifecycle("Documented Python artifact(s): $locGeneratedCount")
    }

    private fun AIcPythonString(aValue: String): String {
        return aValue.replace("\\", "\\\\").replace("'", "\\'")
    }

    private fun AIcWriteArtifactMetadataSidecar(
        aArtifactPublicationDirectory: File,
        aArtifactMetadata: Map<String, String>
    ) {
        aArtifactPublicationDirectory.mkdirs()
        val locSidecarFile = aArtifactPublicationDirectory.resolve(".algites-artifact-docs.properties")
        val locExistingMetadata = if (locSidecarFile.isFile) {
            locSidecarFile.readLines(Charsets.UTF_8)
                .mapNotNull { locLine ->
                    val locSeparatorIndex = locLine.indexOf('=')
                    if (locSeparatorIndex < 0) null else
                        locLine.substring(0, locSeparatorIndex) to locLine.substring(locSeparatorIndex + 1)
                }
                .toMap()
        } else {
            emptyMap()
        }
        val locMergedMetadata = locExistingMetadata + aArtifactMetadata

        locSidecarFile.writeText(
            locMergedMetadata.entries
                .sortedBy { it.key }
                .joinToString(System.lineSeparator()) { locEntry ->
                    "${locEntry.key}=${locEntry.value.replace(System.lineSeparator(), " ")}"
                } + System.lineSeparator(),
            Charsets.UTF_8
        )
    }
}

fun AIcPythonDocsLocalArtifactId(aPath: String?): String {
    return aPath?.trim()?.trim('/')?.replace('/', '.')?.takeIf { it.isNotBlank() } ?: "."
}

fun AIcPythonDocsArtifactCoordinateId(aLocalArtifactId: String): String {
    return if (aLocalArtifactId == ".") locPythonDocsRepositoryId else "${locPythonDocsRepositoryId}_${aLocalArtifactId}"
}

fun AIcPythonDocsDistributionName(aArtifactCoordinateId: String): String {
    val locNormalized = aArtifactCoordinateId
        .lowercase()
        .replace(Regex("[._-]+"), "-")
        .trim('-')
    return "eu-algites-$locNormalized"
}

fun AIcPythonDocsIdentifierSegment(aValue: String): String {
    val locNormalized = aValue.lowercase().replace(Regex("[^a-z0-9_]"), "_")
    val locNonEmpty = locNormalized.ifBlank { "artifact" }
    return if (locNonEmpty.first().isDigit()) "_$locNonEmpty" else locNonEmpty
}

fun AIcPythonDocsImportNamespace(aLocalArtifactId: String): String {
    val locRepositorySegments = locPythonDocsRepositoryId.split('.').filter { it.isNotBlank() }
    val locModuleSegments = if (aLocalArtifactId == ".") emptyList() else aLocalArtifactId.split('.').filter { it.isNotBlank() }
    return (listOf("algites") + locRepositorySegments + locModuleSegments)
        .map(::AIcPythonDocsIdentifierSegment)
        .joinToString(".")
}

fun AIcPythonDocsVersion(aVersion: String, aSnapshotInstanceId: String? = null): String {
    val locSnapshotSuffix = "-SNAPSHOT"
    if (aVersion.endsWith(locSnapshotSuffix, ignoreCase = true)) {
        if (aSnapshotInstanceId == null) {
            return "not tied to a concrete package build"
        }
        return aVersion.dropLast(locSnapshotSuffix.length).replace('-', '.') + ".dev$aSnapshotInstanceId"
    }
    return aVersion.replace('-', '.')
}

val locPythonDocsEntries = locAlgitesDocsResolvedArtifactDirectories
    .filter { locArtifactDirectory ->
        locArtifactDirectory["technologyKinds"]
            ?.split(',')
            ?.map { it.trim().lowercase() }
            ?.contains("python") == true
    }
    .filter { locArtifactDirectory -> locArtifactDirectory["structureKind"] != "repository" }
    .map { locArtifactDirectory ->
        val locRelativePath = locArtifactDirectory["path"]?.trim()?.trim('/') ?: ""
        val locArtifactDirectoryFile = if (locRelativePath.isBlank()) {
            rootProject.projectDir
        } else {
            File(rootProject.projectDir, locRelativePath)
        }
        val locLocalArtifactId = AIcPythonDocsLocalArtifactId(locArtifactDirectory["path"])
        val locArtifactCoordinateId = AIcPythonDocsArtifactCoordinateId(locLocalArtifactId)
        val locVersion = locArtifactDirectory["version.resolvedValue"] ?: ""
        val locPublicationDirectory = File(
            locArtifactDocsRoot.asFile,
            "${locLocalArtifactId}/${locPublicationKind}/${locPublicationId}"
        )
        val locArtifactRunRelativePath = if (locRelativePath.isBlank() || locRelativePath == ".") {
            "build/run"
        } else {
            "build/run/$locRelativePath/run"
        }
        val locWorkingDirectory = File(rootProject.projectDir, "$locArtifactRunRelativePath/bld/algites-docs/python")
        val locSourceDirectories = listOf(
            File(locArtifactDirectoryFile, "src/product/python"),
            File(locArtifactDirectoryFile, "src/product/python.gen")
        )
        val locArtifactMetadata = mapOf(
            "localArtifactId" to locLocalArtifactId,
            "artifactId" to locArtifactCoordinateId,
            "groupId" to (locArtifactDirectory["groupId"] ?: ""),
            "path" to (locArtifactDirectory["path"] ?: ""),
            "name" to (locArtifactDirectory["name"] ?: locLocalArtifactId),
            "description" to (locArtifactDirectory["description"] ?: ""),
            "structureKind" to (locArtifactDirectory["structureKind"] ?: "artifact"),
            "technologyKinds" to (locArtifactDirectory["technologyKinds"] ?: "python"),
            "contentsModel" to (locArtifactDirectory["contentsModel"] ?: ""),
            "gradleProjectPath" to (locArtifactDirectory["gradleProjectPath"] ?: ""),
            "version.resolvedValue" to locVersion,
            "version.lane" to (locArtifactDirectory["version.lane"] ?: ""),
            "version.revision" to (locArtifactDirectory["version.revision"] ?: ""),
            "version.qualifierKind" to (locArtifactDirectory["version.qualifierKind"] ?: ""),
            "python.distributionName" to AIcPythonDocsDistributionName(locArtifactCoordinateId),
            "python.importNamespace" to AIcPythonDocsImportNamespace(locLocalArtifactId),
            "python.version" to AIcPythonDocsVersion(locVersion, locPythonDocsSnapshotInstanceId)
        ) + locAlgitesDocsPublicationMetadata

        AIcdPythonDocsSiteEntry(
            locLocalArtifactId = locLocalArtifactId,
            locArtifactCoordinateId = locArtifactCoordinateId,
            locArtifactDirectory = locArtifactDirectoryFile,
            locSourceDirectories = locSourceDirectories,
            locWorkingDirectory = locWorkingDirectory,
            locArtifactPublicationDirectory = locPublicationDirectory,
            locArtifactMetadata = locArtifactMetadata
        )
    }

val locGeneratePythonDocsSite = tasks.register("generatePythonDocsSite") {
    group = "algites"
    description = "Generates and stages Python API documentation into the Algites documentation site."

    dependsOn("prepareAlgitesDocsPublication")
    dependsOn("generateAlgitesDocsRootIndex")

    locPythonDocsEntries.forEach { locEntry ->
        locEntry.locSourceDirectories.forEach { locSourceDirectory ->
            inputs.files(project.fileTree(locSourceDirectory))
        }
    }
    inputs.property("pythonExecutable", locPythonExecutable)
    inputs.property("snapshotInstanceId", locPythonDocsSnapshotInstanceId ?: "")
    outputs.dir(locArtifactDocsRoot)

    doLast(
        AIcGeneratePythonDocsSiteAction(
            locPythonExecutable,
            locArtifactDocsRoot.asFile,
            locPythonDocsEntries
        )
    )
}

locGeneratePythonDocsSite.configure {
    tasks.findByName("generateJavaDocsSite")?.let { locJavaDocsTask -> mustRunAfter(locJavaDocsTask) }
}

@Suppress("UNCHECKED_CAST")
(rootProject.extra.properties["algitesDocsTechnologyTaskNames"] as? MutableSet<String>)
    ?.add("generatePythonDocsSite")

tasks.named("generateAlgitesDocsSite") {
    dependsOn(locGeneratePythonDocsSite)
}
