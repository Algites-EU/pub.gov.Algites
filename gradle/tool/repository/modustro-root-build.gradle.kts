/*
 * Modustro Builder repository build conventions.
 *
 * Public entry-point location is stable. The implementation consumes the
 * effective metadata resolved from modustro-source-repository.yml and nested
 * modustro-artifact.yml files.
 */

import eu.algites.pltf.modustro.builder.gradleinit.AIcModustroPublishFilesTask
import eu.algites.pltf.modustro.builder.gradleinit.AIcModustroAwaitPublicationsTask
import eu.algites.pltf.modustro.builder.gradleinit.AIcModustroPublicationService
import org.gradle.api.provider.Provider
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.MutableVersionConstraint
import eu.algites.lib.common.version.AIcVersionBound
import eu.algites.lib.common.version.AIcVersionRequirement
import eu.algites.lib.common.version.AIiVersionRequirement
import eu.algites.lib.common.version.AIsVersionRequirementNormalizer
import eu.algites.lib.common.version.scheme.algites.v1.AIcAlgitesVersionTextV1
import eu.algites.lib.common.version.scheme.conversion.algites2gradle.v1.AIcAlgitesToGradleVersionConverterV1
import eu.algites.lib.common.version.scheme.conversion.algites2pep440.v1.AIcAlgitesToPep440VersionConverterV1
import eu.algites.lib.common.version.scheme.conversion.algites2pep440.v1.AIcAlgitesVersionRequirementToPep440RendererV1
import eu.algites.lib.common.version.scheme.gradle.AIcGradleVersionRequirementRenderer
import eu.algites.lib.common.version.scheme.gradle.AIcGradleVersionScheme
import eu.algites.lib.common.version.scheme.gradle.AIrGradleVersionConstraint
import eu.algites.lib.common.version.scheme.pep440.AIcPep440VersionRequirementRenderer
import eu.algites.lib.common.version.scheme.pep440.AIcPep440VersionScheme
import eu.algites.lib.common.version.scheme.pep440.AInPythonBuildPhase
import eu.algites.pltf.modustro.builder.capability.AIcBuiltinCapabilityDemandPlanner
import eu.algites.pltf.modustro.builder.model.output.AInBuildOutputProductionKind
import eu.algites.pltf.modustro.builder.model.source.AIcPreparedSourceSet
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayload
import eu.algites.pltf.modustro.builder.output.AIcBuiltinBuildOutputProducers
import eu.algites.lib.naming.convention.AIcAlgitesNamingProfiles
import eu.algites.tool.codegen.defs.AIcDefaultDefsCodegenService
import eu.algites.tool.codegen.defs.AIcCanonicalDefinitionMerger
import eu.algites.tool.codegen.defs.AIcdCanonicalDefinition
import eu.algites.tool.codegen.defs.AIcdCodeGenerationRequest
import eu.algites.tool.codegen.defs.AIcdDefinitionLoadRequest
import eu.algites.tool.codegen.defs.AInCodeGenerationTarget
import eu.algites.tool.codegen.defs.AInDefinitionSourceKind
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Delete
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.authentication.http.HttpHeaderAuthentication
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.CompletionStage

/**
 * Discovers canonical definitions and generates Java/Python sources through the reusable Defs Codegen API.
 *
 * The standard `.gen` roots are shared by generators, so this task does not claim either root as an exclusive
 * Gradle output directory. It owns only the files recorded in its artifact-local manifest state and removes stale
 * files from that ownership set on subsequent executions.
 */
abstract class AIcGenerateModustroDefinitionSourcesTask : DefaultTask() {
    @get:Input
    abstract val targetTechnologyKinds: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val rootDefinitionPackage: Property<String>

    @get:InputFiles
    abstract val sourceFiles: ConfigurableFileCollection

    @get:Internal
    abstract val artifactDirectory: DirectoryProperty

    @get:LocalState
    abstract val manifestDirectory: DirectoryProperty

    @get:Internal
    abstract val javaOutputDirectory: DirectoryProperty

    @get:Internal
    abstract val pythonOutputDirectory: DirectoryProperty

    private data class AIcdDiscoveredDefinition(
        val sourceKind: String,
        val source: String,
        val packageName: String
    )

    private data class AIcdPendingSource(
        val target: String,
        val relativePath: String,
        val source: String,
        val origin: String
    )

    private fun AIcSourceKind(aValue: String): AInDefinitionSourceKind = when (aValue) {
        "yamldefs" -> AInDefinitionSourceKind.YAMLDEFS
        "jsondefs" -> AInDefinitionSourceKind.JSONDEFS
        "xmldefs" -> AInDefinitionSourceKind.XMLDEFS
        else -> throw GradleException("Unsupported canonical definition source kind '$aValue'.")
    }

    private fun AIcTarget(aValue: String): AInCodeGenerationTarget = when (aValue) {
        "java" -> AInCodeGenerationTarget.JAVA
        "python" -> AInCodeGenerationTarget.PYTHON
        else -> throw GradleException("Unsupported definition code-generation target '$aValue'.")
    }

    private fun AIcOutputRoot(aTarget: String): File = when (aTarget) {
        "java" -> javaOutputDirectory.get().asFile
        "python" -> pythonOutputDirectory.get().asFile
        else -> throw GradleException("Unsupported definition code-generation target '$aTarget'.")
    }

    private fun AIcManifest(aTarget: String): File = File(manifestDirectory.get().asFile, "$aTarget.manifest")

    private fun AIcDefinitionFiles(aRoot: File, aSourceKind: String): List<File> {
        if (!aRoot.isDirectory) return emptyList()
        val locDefinitionSuffix = when (aSourceKind) {
            "yamldefs" -> ".yamldef.schema.json"
            "jsondefs" -> ".jsondef.schema.json"
            "xmldefs" -> ".xsd"
            else -> throw GradleException("Unsupported canonical definition source kind '$aSourceKind'.")
        }
        val locDefinitionFiles = mutableListOf<File>()
        aRoot.walkTopDown()
            .filter(File::isFile)
            .filterNot { locFile -> locFile.name.startsWith('.') }
            .forEach { locFile ->
                val locName = locFile.name
                when {
                    locName.endsWith(".meta.yml") || locName.endsWith(".meta.yaml") -> Unit
                    locName.endsWith(locDefinitionSuffix) -> locDefinitionFiles.add(locFile)
                    else -> throw GradleException(
                        "Unsupported file '${locFile.relativeTo(aRoot).invariantSeparatorsPath}' in canonical " +
                            "$aSourceKind root '$aRoot'. Expected '*$locDefinitionSuffix' definitions or '.meta.yml/.meta.yaml' sidecars."
                    )
                }
            }
        return locDefinitionFiles.sortedBy { locFile -> locFile.relativeTo(aRoot).invariantSeparatorsPath }
    }

    private fun AIcDiscoverDefinitions(): List<AIcdDiscoveredDefinition> {
        val locArtifactDirectory = artifactDirectory.get().asFile.canonicalFile
        return listOf("yamldefs", "jsondefs", "xmldefs").flatMap { locSourceKind ->
            val locRoot = File(locArtifactDirectory, "src/product/$locSourceKind").canonicalFile
            AIcDefinitionFiles(locRoot, locSourceKind).map { locInput ->
                val locParentRelativePath = locInput.parentFile
                    .relativeTo(locRoot)
                    .invariantSeparatorsPath
                    .trim('/')
                val locPackage = locParentRelativePath.replace('/', '.').ifBlank {
                    rootDefinitionPackage.orNull?.trim().orEmpty()
                }
                require(locPackage.isNotBlank()) {
                    "Canonical definition '${locInput.path}' is at the canonical source-root level and the artifact has no " +
                        "effective GroupId from which to infer its generated package."
                }
                require(Regex("^[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*$").matches(locPackage)) {
                    "Canonical definition '${locInput.path}' resolves invalid generated package '$locPackage'."
                }
                AIcdDiscoveredDefinition(
                    locSourceKind,
                    locInput.relativeTo(locArtifactDirectory).invariantSeparatorsPath,
                    locPackage
                )
            }
        }
    }

    private fun AIcWriteTarget(aTarget: String, aSources: List<AIcdPendingSource>) {
        val locOutputRoot = AIcOutputRoot(aTarget)
        val locManifest = AIcManifest(aTarget)
        if (aSources.isEmpty() && !locManifest.isFile) return
        val locExpected = aSources.map { it.relativePath.replace(File.separatorChar, '/') }.toSet()
        if (locManifest.isFile) {
            locManifest.readLines(StandardCharsets.UTF_8)
                .map(String::trim)
                .filter(String::isNotBlank)
                .filter { it !in locExpected }
                .forEach { locStaleRelativePath ->
                    val locStale = File(locOutputRoot, locStaleRelativePath).canonicalFile
                    require(locStale.toPath().startsWith(locOutputRoot.canonicalFile.toPath())) {
                        "Invalid stale generated-source path '$locStaleRelativePath'."
                    }
                    if (locStale.isFile) locStale.delete()
                }
        }
        aSources.forEach { locSource ->
            val locOutput = File(locOutputRoot, locSource.relativePath).canonicalFile
            require(locOutput.toPath().startsWith(locOutputRoot.canonicalFile.toPath())) {
                "Generated-source path '${locSource.relativePath}' escapes '$locOutputRoot'."
            }
            locOutput.parentFile.mkdirs()
            locOutput.writeText(locSource.source, StandardCharsets.UTF_8)
        }
        if (locExpected.isEmpty()) {
            if (locManifest.isFile) locManifest.delete()
        } else {
            locManifest.parentFile.mkdirs()
            locManifest.writeText(locExpected.sorted().joinToString("\n", postfix = "\n"), StandardCharsets.UTF_8)
        }
    }

    /** Discovers canonical definitions and generates native source units for the artifact TechnologyKinds. */
    @TaskAction
    fun AIcGenerate() {
        val locArtifactDirectory = artifactDirectory.get().asFile.canonicalFile
        val locTargets = targetTechnologyKinds.get()
            .map { it.trim().lowercase() }
            .filter { it in setOf("java", "python") }
            .distinct()
            .sorted()
        val locDefinitions = AIcDiscoverDefinitions()
        val locService = AIcDefaultDefsCodegenService()
        val locPending = mutableListOf<AIcdPendingSource>()
        val locRequests = linkedMapOf<Pair<String, String>, MutableList<AIcdCodeGenerationRequest>>()

        locDefinitions.forEach { locEntry ->
            val locInput = File(locArtifactDirectory, locEntry.source).canonicalFile
            locTargets.forEach { locTargetText ->
                val locTarget = AIcTarget(locTargetText)
                val locProfile = when (locTarget) {
                    AInCodeGenerationTarget.JAVA -> AIcAlgitesNamingProfiles.javaProfile()
                    AInCodeGenerationTarget.PYTHON -> AIcAlgitesNamingProfiles.pythonProfile()
                }
                val locLoadedDefinition = locService.load(
                    AIcdDefinitionLoadRequest(locInput.toPath(), AIcSourceKind(locEntry.sourceKind), locProfile)
                )
                val locDefinition = AIcdCanonicalDefinition(
                    locLoadedDefinition.identity(),
                    locLoadedDefinition.version(),
                    locLoadedDefinition.logicalName(),
                    locLoadedDefinition.kind(),
                    locLoadedDefinition.sourceKind(),
                    locEntry.source,
                    locLoadedDefinition.description(),
                    locLoadedDefinition.properties(),
                    locLoadedDefinition.enumValues()
                )
                val locRequest = AIcdCodeGenerationRequest(locDefinition, locTarget, if (locEntry.sourceKind == "xmldefs") locEntry.packageName + ".xmldefs" else locEntry.packageName, locProfile)
                val locGenerated = locService.generate(locRequest)
                val locRelativePath = locGenerated.relativePath().replace('\\', '/')
                locRequests.getOrPut(locTargetText to locRelativePath) { mutableListOf() }.add(locRequest)
            }
        }

        locRequests.forEach { (locKey, locRepresentations) ->
            val locPrimary = locRepresentations.first()
            val locDefinition = try {
                AIcCanonicalDefinitionMerger.AIcMerge(locRepresentations.map { it.definition() })
            } catch (locFailure: IllegalArgumentException) {
                throw GradleException("Incompatible canonical definitions for '${locKey.second}'.", locFailure)
            }
            val locGenerated = locService.generate(AIcdCodeGenerationRequest(
                locDefinition, locPrimary.target(), locPrimary.packageName(), locPrimary.namingProfile()
            ))
            locPending.add(AIcdPendingSource(locKey.first, locKey.second, locGenerated.source(), locDefinition.sourceResource()))
        }

        listOf("java", "python").forEach { locTarget ->
            AIcWriteTarget(locTarget, locPending.filter { it.target == locTarget })
        }
    }
}

abstract class AIcGenerateModustroArtifactManifestTask : DefaultTask() {
    @get:Input
    abstract val repositoryId: Property<String>

    @get:Input
    abstract val localArtifactId: Property<String>

    @get:Input
    abstract val artifactCoordinateId: Property<String>

    @get:Input
    @get:Optional
    abstract val variantId: Property<String>

    @get:Input
    abstract val groupId: Property<String>

    @get:Input
    abstract val artifactVersion: Property<String>

    @get:Input
    abstract val sourcePath: Property<String>

    @get:Input
    abstract val structureKind: Property<String>

    @get:Input
    abstract val artifactName: Property<String>

    @get:Input
    abstract val artifactDescription: Property<String>

    @get:Input
    abstract val descriptorHierarchy: ListProperty<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    private fun AIcYamlScalar(aValue: String): String {
        if (aValue.isBlank()) return "\"\""
        val locPlain = Regex("^[A-Za-z0-9._/+:-]+$").matches(aValue) &&
            aValue !in setOf("null", "true", "false") &&
            aValue.toDoubleOrNull() == null
        return if (locPlain) {
            aValue
        } else {
            "\"" + aValue.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        }
    }

    @TaskAction
    fun AIcGenerate() {
        val locOutputFile = outputFile.get().asFile
        locOutputFile.parentFile.mkdirs()

        val locDescriptorEntries = descriptorHierarchy.get().map { locEntry ->
            val locParts = locEntry.split('\t')
            require(locParts.size == 3) { "Invalid Modustro Builder descriptor hierarchy entry '$locEntry'." }
            locParts
        }
        require(locDescriptorEntries.isNotEmpty()) { "Modustro artifact manifest descriptor hierarchy must not be empty." }

        locOutputFile.writeText(
            buildString {
                appendLine("# yaml-language-server: \$schema=https://defs.dev.algites.eu/api/yamldefs/eu/algites/tool/build/yamldefs/modustro-artifact-manifest_1.yamldef.schema.json")
                appendLine("# \$schema: https://defs.dev.algites.eu/api/yamldefs/eu/algites/tool/build/yamldefs/modustro-artifact-manifest_1.yamldef.schema.json")
                appendLine("\$schema: https://defs.dev.algites.eu/api/yamldefs/eu/algites/tool/build/yamldefs/modustro-artifact-manifest_1.yamldef.schema.json")
                appendLine()
                appendLine("ManifestVersion: 1")
                appendLine("Artifact:")
                appendLine("  RepositoryId: ${AIcYamlScalar(repositoryId.get())}")
                appendLine("  LocalArtifactId: ${AIcYamlScalar(localArtifactId.get())}")
                appendLine("  ArtifactCoordinateId: ${AIcYamlScalar(artifactCoordinateId.get())}")
                variantId.orNull?.takeIf { it.isNotBlank() }?.let { locVariantId ->
                    appendLine("  VariantId: ${AIcYamlScalar(locVariantId)}")
                }
                appendLine("  GroupId: ${AIcYamlScalar(groupId.get())}")
                appendLine("  Version: ${AIcYamlScalar(artifactVersion.get())}")
                appendLine("  SourcePath: ${AIcYamlScalar(sourcePath.get())}")
                appendLine("  StructureKind: ${AIcYamlScalar(structureKind.get().replace('-', '_'))}")
                appendLine("  Name: ${AIcYamlScalar(artifactName.get())}")
                appendLine("  Description: ${AIcYamlScalar(artifactDescription.get())}")
                appendLine("SourceMetadata:")
                appendLine("  DescriptorHierarchy:")
                locDescriptorEntries.forEach { locParts ->
                    appendLine("    - StructureKind: ${AIcYamlScalar(locParts[0].replace('-', '_'))}")
                    appendLine("      Path: ${AIcYamlScalar(locParts[1])}")
                    appendLine("      Sha256: ${AIcYamlScalar(locParts[2])}")
                }
            },
            Charsets.UTF_8
        )
    }
}


abstract class AIcGeneratePythonProjectMetadataTask : DefaultTask() {
    @get:Input abstract val pythonExecutable: Property<String>
    @get:Input abstract val distributionName: Property<String>
    @get:Input abstract val pythonVersion: Property<String>
    @get:Input abstract val artifactDescription: Property<String>
    @get:Input abstract val licenseIds: ListProperty<String>
    @get:Input abstract val projectDependencies: ListProperty<String>
    @get:Input abstract val packageSourceRoots: ListProperty<String>
    @get:Input @get:Optional abstract val requiresPython: Property<String>
    @get:InputFile @get:Optional abstract val templateFile: RegularFileProperty
    @get:OutputFile abstract val outputFile: RegularFileProperty

    private fun AIcNativeProjectDependencies(aTemplateFile: File?): List<String> {
        if (aTemplateFile == null || !aTemplateFile.isFile) return emptyList()
        val locScript = """
            import json, pathlib, sys, tomllib
            data = tomllib.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
            project = data.get("project", {})
            unsupported = sorted(set(project) - {"dependencies"})
            if unsupported:
                raise SystemExit("pyproject.toml.tpl [project] may currently define only dependencies; unsupported keys: " + ", ".join(unsupported))
            deps = project.get("dependencies", [])
            if not isinstance(deps, list) or not all(isinstance(item, str) for item in deps):
                raise SystemExit("pyproject.toml.tpl project.dependencies must be an array of strings")
            print(json.dumps(deps))
        """.trimIndent()
        val locProcess = ProcessBuilder(pythonExecutable.get(), "-c", locScript, aTemplateFile.absolutePath)
            .redirectErrorStream(true)
            .apply { environment()["PYTHONDONTWRITEBYTECODE"] = "1" }
            .start()
        val locOutput = locProcess.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        if (locProcess.waitFor() != 0) {
            throw GradleException("Cannot read native Python project dependencies from '${aTemplateFile.path}':\n$locOutput")
        }
        val locParsed = JsonSlurper().parseText(locOutput) as? List<*> ?: return emptyList()
        return locParsed.map { it.toString() }
    }

    private fun AIcTemplateWithoutOwnedProjectTable(aTemplateText: String): String {
        if (!Regex("(?m)^\\s*\\[project]\\s*$").containsMatchIn(aTemplateText)) return aTemplateText
        return Regex("(?ms)^\\s*\\[project]\\s*$.*?(?=^\\s*\\[\\[?[^\\n]+]|\\z)")
            .replace(aTemplateText, "")
            .trim()
    }

    @TaskAction
    fun AIcGenerate() {
        val locTemplateFile = templateFile.orNull?.asFile
        val locOriginalTemplateText = if (locTemplateFile != null && locTemplateFile.isFile) {
            locTemplateFile.readText(Charsets.UTF_8)
        } else {
            ""
        }
        if (Regex("(?m)^\\s*\\[build-system]\\s*$").containsMatchIn(locOriginalTemplateText)) {
            throw GradleException("pyproject.toml.tpl must not define [build-system]; the Algites Python adapter owns the effective build backend.")
        }
        val locNativeDependencies = AIcNativeProjectDependencies(locTemplateFile)
        val locTemplateText = AIcTemplateWithoutOwnedProjectTable(locOriginalTemplateText)
        val locDescription = artifactDescription.get().replace("\"", "\\\"")
        val locLicenseIds = licenseIds.get()
        val locGenerated = buildString {
            appendLine("# Generated by Algites. Do not edit or commit this file.")
            appendLine("[build-system]")
            appendLine("requires = [\"setuptools>=77\", \"wheel\"]")
            appendLine("build-backend = \"setuptools.build_meta\"")
            appendLine()
            appendLine("[project]")
            appendLine("name = \"${distributionName.get()}\"")
            appendLine("version = \"${pythonVersion.get()}\"")
            requiresPython.orNull?.takeIf { it.isNotBlank() }?.let { locRequiresPython ->
                appendLine("requires-python = \"${locRequiresPython.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
            }
            val locProjectDependencies = (locNativeDependencies + projectDependencies.get()).filter { it.isNotBlank() }.distinct()
            if (locProjectDependencies.isNotEmpty()) {
                appendLine("dependencies = [")
                locProjectDependencies.forEach { locDependency ->
                    val locEscapedDependency = locDependency.replace("\\", "\\\\").replace("\"", "\\\"")
                    appendLine("    \"$locEscapedDependency\",")
                }
                appendLine("]")
            }
            if (locDescription.isNotBlank()) appendLine("description = \"$locDescription\"")
            if (locLicenseIds.size == 1) appendLine("license = \"${locLicenseIds.single()}\"")
            if (locLicenseIds.isNotEmpty()) {
                val locLicensePaths = locLicenseIds.joinToString(", ") { locLicenseId -> "\"LICENSES/$locLicenseId.txt\"" }
                appendLine("license-files = [$locLicensePaths]")
            }
            appendLine()
            appendLine("[tool.setuptools.packages.find]")
            val locPackageSourceRoots = packageSourceRoots.get()
                .joinToString(", ") { locRoot -> "\"${locRoot.replace("\\", "/").replace("\"", "\\\"")}\"" }
            appendLine("where = [$locPackageSourceRoots]")
            appendLine("namespaces = true")
            appendLine()
            appendLine("[tool.setuptools.package-data]")
            appendLine("\"*\" = [\"**/*\"]")
            if (locTemplateText.isNotBlank()) {
                appendLine()
                appendLine(locTemplateText.trim())
                appendLine()
            }
        }
        val locOutputFile = outputFile.get().asFile
        locOutputFile.parentFile.mkdirs()
        locOutputFile.writeText(locGenerated, Charsets.UTF_8)
    }
}


abstract class AIcResolveJavaDependenciesTask : DefaultTask() {
    @get:Classpath
    abstract val dependencyFiles: ConfigurableFileCollection

    @TaskAction
    fun AIcResolve() {
        dependencyFiles.files
    }
}


abstract class AIcResolvePythonDependenciesTask : DefaultTask() {
    @get:Input abstract val pythonExecutable: Property<String>
    @get:Input abstract val projectPathValue: Property<String>
    @get:Internal abstract val projectDirectory: DirectoryProperty
    @get:Input abstract val dependencyDefinitions: ListProperty<String>
    @get:Input abstract val constraintDefinitions: ListProperty<String>
    @get:Input abstract val endpointDefinitions: ListProperty<String>
    @get:Input abstract val credentialBaseDirectoryPath: Property<String>
    @get:InputFile @get:Optional abstract val pyprojectTemplateFile: RegularFileProperty
    @get:OutputFile abstract val selectedPhaseFile: RegularFileProperty

    private data class AIcdEntry(
        val packageName: String,
        val preferred: String?,
        val nonStrictMaximums: String?,
        val strictMaximums: String
    )

    private fun AIcParseEntry(aValue: String): AIcdEntry {
        val locParts = aValue.split('\t')
        if (locParts.size != 4) {
            throw GradleException("Invalid Python dependency-resolution entry '$aValue'.")
        }
        return AIcdEntry(
            packageName = locParts[0],
            preferred = locParts[1].takeIf { it.isNotEmpty() },
            nonStrictMaximums = locParts[2].takeIf { it.isNotEmpty() },
            strictMaximums = locParts[3]
        )
    }

    private fun AIcSpecifier(aEntry: AIcdEntry, aPhase: AInPythonBuildPhase): String = when (aPhase) {
        AInPythonBuildPhase.PREFERRED -> aEntry.preferred ?: aEntry.nonStrictMaximums ?: aEntry.strictMaximums
        AInPythonBuildPhase.NON_STRICT_MAXIMUMS -> aEntry.nonStrictMaximums ?: aEntry.strictMaximums
        AInPythonBuildPhase.STRICT_MAXIMUMS -> aEntry.strictMaximums
    }

    private fun AIcRequirementLine(aEntry: AIcdEntry, aPhase: AInPythonBuildPhase): String =
        aEntry.packageName + AIcSpecifier(aEntry, aPhase)

    private fun AIcParseJsonObject(aRaw: String?): Map<String, Any?> {
        if (aRaw.isNullOrBlank()) return emptyMap()
        val locParsed = JsonSlurper().parseText(aRaw) as? Map<*, *> ?: return emptyMap()
        return locParsed.entries.associate { locEntry -> locEntry.key.toString() to locEntry.value }
    }

    private fun AIcCommandOutput(aArguments: List<String>): String? {
        val locCommand = mutableListOf(System.getenv("ALGITES_CREDENTIAL_CLI")?.takeIf { it.isNotBlank() } ?: "algites-credentials")
        locCommand.addAll(aArguments)
        return try {
            val locProcess = ProcessBuilder(locCommand).redirectError(ProcessBuilder.Redirect.INHERIT).start()
            val locOutput = locProcess.inputStream.bufferedReader(Charsets.UTF_8).readText()
            if (locProcess.waitFor() == 0) locOutput else null
        } catch (_: Exception) { null }
    }

    private fun AIcCredentialValue(aProfileId: String, aField: String): String? {
        val locRawDocument = System.getenv("ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS")?.takeIf { it.isNotBlank() }
            ?: AIcCommandOutput(listOf("bootstrap-document")) ?: return null
        val locDocument = AIcParseJsonObject(locRawDocument)
        val locProfile = locDocument[aProfileId] as? Map<*, *> ?: return null
        val locType = locProfile["Basic"] as? Map<*, *> ?: return null
        val locField = locType[aField] as? Map<*, *> ?: return null
        val locSource = locField["Source"]?.toString()?.lowercase() ?: return null
        val locReference = locField["Value"]?.toString() ?: return null
        return when (locSource) {
            "direct_value" -> locReference
            "file_content" -> {
                val locCandidate = File(locReference)
                val locFile = if (locCandidate.isAbsolute) locCandidate else File(credentialBaseDirectoryPath.get(), locReference)
                if (!locFile.isFile) throw GradleException("Credential '$aProfileId/basic/$aField' references missing file '${locFile.path}'.")
                locFile.readText(Charsets.UTF_8)
            }
            "environment_variable_content" -> System.getenv(locReference)
                ?: throw GradleException("Credential '$aProfileId/basic/$aField' references unavailable environment variable '$locReference'.")
            "secret_content" -> {
                val locSecrets = AIcParseJsonObject(System.getenv("_TMP_ALGITES_CREDENTIAL_SECRETS_JSON"))
                locSecrets[locReference]?.toString() ?: AIcCommandOutput(listOf("bootstrap-secret", locReference))
                ?: throw GradleException("Credential '$aProfileId/basic/$aField' references unavailable secret '$locReference'.")
            }
            else -> throw GradleException("Credential '$aProfileId/basic/$aField' uses unsupported source '$locSource'.")
        }
    }

    private fun AIcIndexUrls(): List<String> = endpointDefinitions.get().map { locDefinition ->
        val locParts = locDefinition.split('\t')
        require(locParts.size == 4) { "Invalid Python download endpoint definition '$locDefinition'." }
        val (locEndpointId, locEndpointUrl, locProfileId, locProfileType) = locParts
        if (locProfileId.isBlank()) return@map locEndpointUrl
        if (locProfileType != "basic") {
            throw GradleException("Python download endpoint '$locEndpointId' uses unsupported credential type '$locProfileType'; the current pip adapter supports 'basic'.")
        }
        val locUsername = AIcCredentialValue(locProfileId, "Username")
        val locPassword = AIcCredentialValue(locProfileId, "Password")
        if (locUsername.isNullOrEmpty() || locPassword.isNullOrEmpty()) {
            throw GradleException("Credential profile '$locProfileId' type 'basic' is not available for Python dependency resolution.")
        }
        val locUrl = URI(locEndpointUrl).toURL()
        val locEncode: (String) -> String = { locValue -> URLEncoder.encode(locValue, StandardCharsets.UTF_8).replace("+", "%20") }
        val locPort = if (locUrl.port >= 0) ":${locUrl.port}" else ""
        val locPath = locUrl.file.ifBlank { "/" }
        "${locUrl.protocol}://${locEncode(locUsername)}:${locEncode(locPassword)}@${locUrl.host}$locPort$locPath"
    }.distinct()

    private fun AIcNativeProjectDependencies(): List<String> {
        val locTemplateFile = pyprojectTemplateFile.orNull?.asFile ?: return emptyList()
        if (!locTemplateFile.isFile) return emptyList()
        val locScript = """
            import json, pathlib, sys, tomllib
            data = tomllib.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
            project = data.get("project", {})
            unsupported = sorted(set(project) - {"dependencies"})
            if unsupported:
                raise SystemExit("pyproject.toml.tpl [project] may currently define only dependencies; unsupported keys: " + ", ".join(unsupported))
            deps = project.get("dependencies", [])
            if not isinstance(deps, list) or not all(isinstance(item, str) for item in deps):
                raise SystemExit("pyproject.toml.tpl project.dependencies must be an array of strings")
            print(json.dumps(deps))
        """.trimIndent()
        val locProcess = ProcessBuilder(pythonExecutable.get(), "-c", locScript, locTemplateFile.absolutePath)
            .redirectErrorStream(true)
            .apply { environment()["PYTHONDONTWRITEBYTECODE"] = "1" }
            .start()
        val locOutput = locProcess.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        if (locProcess.waitFor() != 0) {
            throw GradleException("Cannot read native Python project dependencies from '${locTemplateFile.path}':\n$locOutput")
        }
        val locParsed = JsonSlurper().parseText(locOutput) as? List<*> ?: return emptyList()
        return locParsed.map { it.toString() }
    }

    @TaskAction
    fun AIcResolve() {
        val locDependencies = dependencyDefinitions.get().map(::AIcParseEntry)
        val locConstraints = constraintDefinitions.get().map(::AIcParseEntry)
        val locNativeDependencies = AIcNativeProjectDependencies()
        val locAll = locDependencies + locConstraints
        val locHasPreferred = locAll.any { it.preferred != null }
        val locHasNonStrictMaximums = locAll.any { it.nonStrictMaximums != null }
        val locPhases = buildList {
            if (locHasPreferred) add(AInPythonBuildPhase.PREFERRED)
            if (locHasNonStrictMaximums) add(AInPythonBuildPhase.NON_STRICT_MAXIMUMS)
            add(AInPythonBuildPhase.STRICT_MAXIMUMS)
        }
        val locOutput = selectedPhaseFile.get().asFile
        locOutput.parentFile.mkdirs()
        if (locDependencies.isEmpty() && locNativeDependencies.isEmpty()) {
            locOutput.writeText(AInPythonBuildPhase.STRICT_MAXIMUMS.name + "\n", Charsets.UTF_8)
            return
        }

        val locWorkDirectory = File(temporaryDir, "dependency-resolution")
        locWorkDirectory.mkdirs()
        var locLastFailure = ""
        locPhases.forEach { locPhase ->
            val locRequirementsFile = File(locWorkDirectory, "${locPhase.name.lowercase()}-requirements.txt")
            val locConstraintsFile = File(locWorkDirectory, "${locPhase.name.lowercase()}-constraints.txt")
            val locReportFile = File(locWorkDirectory, "${locPhase.name.lowercase()}-report.json")
            val locRequirementLines = locNativeDependencies + locDependencies.map { locEntry -> AIcRequirementLine(locEntry, locPhase) }
            locRequirementsFile.writeText(
                locRequirementLines.joinToString("\n", postfix = if (locRequirementLines.isEmpty()) "" else "\n"),
                Charsets.UTF_8
            )
            locConstraintsFile.writeText(
                locConstraints.joinToString("\n", postfix = if (locConstraints.isEmpty()) "" else "\n") { locEntry -> AIcRequirementLine(locEntry, locPhase) },
                Charsets.UTF_8
            )

            val locCommand = mutableListOf(
                pythonExecutable.get(), "-m", "pip", "install",
                "--dry-run", "--ignore-installed", "--disable-pip-version-check",
                "--report", locReportFile.absolutePath,
                "-r", locRequirementsFile.absolutePath
            )
            if (locConstraints.isNotEmpty()) {
                locCommand.addAll(listOf("-c", locConstraintsFile.absolutePath))
            }
            val locIndexUrls = AIcIndexUrls().filter { it.isNotBlank() }
            locIndexUrls.firstOrNull()?.let { locUrl -> locCommand.addAll(listOf("--index-url", locUrl)) }
            locIndexUrls.drop(1).forEach { locUrl -> locCommand.addAll(listOf("--extra-index-url", locUrl)) }
            val locProcess = ProcessBuilder(locCommand)
                .directory(projectDirectory.get().asFile)
                .redirectErrorStream(true)
                .apply { environment()["PYTHONDONTWRITEBYTECODE"] = "1" }
                .start()
            val locOutputText = locProcess.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val locExitCode = locProcess.waitFor()
            if (locExitCode == 0) {
                locOutput.writeText(locPhase.name + "\n", Charsets.UTF_8)
                logger.lifecycle("Python dependency preflight for '${projectPathValue.get()}' succeeded in phase ${locPhase.name}.")
                return
            }
            locLastFailure = locOutputText
            logger.lifecycle("Python dependency preflight for '${projectPathValue.get()}' did not resolve in phase ${locPhase.name}; trying the next applicable phase.")
        }

        throw GradleException(
            "Python dependency preflight for '${projectPathValue.get()}' failed in all applicable phases." +
                (if (locLastFailure.isBlank()) "" else "\n$locLastFailure")
        )
    }
}

abstract class AIcValidatePythonDistributionPathsTask : DefaultTask() {
    @get:Internal abstract val repositoryDirectory: DirectoryProperty
    @get:Input abstract val pythonArtifactRelativePaths: ListProperty<String>
    @get:Input abstract val pythonArtifactImportNamespaces: MapProperty<String, String>

    private fun AIcSourceKindBase(aName: String): String = aName.substringBefore('.')

    private fun AIcPythonTypeModuleName(aTypeName: String): String {
        val locMatch = Regex("^(AI[a-z]+)(.*)$").matchEntire(aTypeName)
            ?: throw GradleException("Unsupported Algites public Python type name '$aTypeName'")
        val locPrefix = locMatch.groupValues[1].lowercase()
        val locRemainder = locMatch.groupValues[2]
            .replace(Regex("(.)([A-Z][a-z]+)"), "$1_$2")
            .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .lowercase()
        return if (locRemainder.isBlank()) locPrefix else "${locPrefix}_${locRemainder}"
    }

    @TaskAction
    fun AIcValidate() {
        val locRepositoryDirectory = repositoryDirectory.get().asFile
        val locPackageSourceKinds = setOf("python", "jsondefs", "yamldefs", "xmldefs", "config")
        val locForbiddenPythonResourceSuffixes = setOf("json", "yaml", "yml", "xml", "xsd", "wsdl", "properties", "ini", "cfg", "conf")
        val locPathOwners = linkedMapOf<String, MutableList<Pair<String, String>>>()
        val locArtifactFiles = linkedMapOf<String, MutableSet<String>>()
        val locProblems = mutableListOf<String>()

        fun AIcIsCanonicalSourceRootName(aName: String): Boolean {
            val locBase = AIcSourceKindBase(aName)
            return locBase in locPackageSourceKinds && (aName == locBase || aName == "$locBase.gen" || aName == "$locBase.extgen")
        }

        val locImportNamespaces = pythonArtifactImportNamespaces.get()
        pythonArtifactRelativePaths.get().sorted().forEach { locArtifactName ->
            val locProjectDirectory = if (locArtifactName == ".") locRepositoryDirectory else File(locRepositoryDirectory, locArtifactName)
            val locProductSourceDirectory = File(locProjectDirectory, "src/product")
            val locImportNamespacePath = locImportNamespaces[locArtifactName]
                ?.replace('.', '/')
                ?.trim('/')
                ?.takeIf { it.isNotBlank() }
                ?: throw GradleException("Missing Python import namespace for Algites artifact '$locArtifactName'.")
            val locLocalOwners = linkedMapOf<String, MutableList<String>>()
            if (locProductSourceDirectory.isDirectory) {
                locProductSourceDirectory.listFiles()
                    ?.filter { locDirectory -> locDirectory.isDirectory && AIcIsCanonicalSourceRootName(locDirectory.name) }
                    ?.sortedBy { locDirectory -> locDirectory.name }
                    ?.forEach { locSourceRoot ->
                        val locSourceKind = AIcSourceKindBase(locSourceRoot.name)
                        locSourceRoot.walkTopDown()
                            .filter { locFile -> locFile.isFile && "__pycache__" !in locFile.toPath().map { it.toString() } && locFile.extension.lowercase() !in setOf("pyc", "pyo") }
                            .forEach { locFile ->
                                val locRelativePath = locSourceRoot.toPath().relativize(locFile.toPath()).toString().replace(File.separatorChar, '/')
                                val locTargetPath = if (locSourceKind == "python") {
                                    locRelativePath
                                } else {
                                    "$locImportNamespacePath/$locRelativePath"
                                }
                                locLocalOwners.getOrPut(locTargetPath) { mutableListOf() }.add(locSourceRoot.name)
                                locPathOwners.getOrPut(locTargetPath) { mutableListOf() }.add(locArtifactName to locSourceRoot.name)
                                locArtifactFiles.getOrPut(locArtifactName) { linkedSetOf() }.add(locTargetPath)
                            }
                    }
            }
            locLocalOwners.toSortedMap().forEach { (locRelativePath, locRoots) ->
                if (locRoots.size > 1) {
                    locProblems.add("$locArtifactName: target Python module/resource path '$locRelativePath' is provided by multiple product source roots: ${locRoots.sorted().joinToString(", ")}")
                }
            }

            listOf("product", "develop").forEach { locScope ->
                val locScopeDirectory = File(locProjectDirectory, "src/$locScope")
                if (!locScopeDirectory.isDirectory) return@forEach
                locScopeDirectory.listFiles()?.filter(File::isDirectory)?.forEach { locSourceRoot ->
                    val locIsManuallyMaintainedSourceRoot =
                        !locSourceRoot.name.endsWith(".gen") && !locSourceRoot.name.endsWith(".extgen")
                    if (!locIsManuallyMaintainedSourceRoot) return@forEach

                    if (AIcSourceKindBase(locSourceRoot.name) == "schema") {
                        locProblems.add("$locArtifactName/src/$locScope/${locSourceRoot.name}: source kind 'schema' is not allowed; use jsondefs, yamldefs, xmldefs, or config according to semantic role")
                    }
                    if (AIcSourceKindBase(locSourceRoot.name) == "python") {
                        locSourceRoot.walkTopDown().filter(File::isFile)
                            .filter { locFile -> locFile.extension.lowercase() in locForbiddenPythonResourceSuffixes }
                            .forEach { locFile ->
                                val locRelativeFile = locRepositoryDirectory.toPath().relativize(locFile.toPath()).toString().replace(File.separatorChar, '/')
                                locProblems.add("$locRelativeFile: definition/configuration resource must be moved from the Python source root to jsondefs, yamldefs, xmldefs, or config")
                            }
                        if (locScope == "product") {
                            val locPublicTypePattern = Regex("(?m)^class\\s+(AI[a-z]+[A-Z][A-Za-z0-9_]*)\\b")
                            locSourceRoot.walkTopDown().filter { locFile -> locFile.isFile && locFile.extension.lowercase() == "py" }.forEach { locFile ->
                                val locPublicTypes = locPublicTypePattern.findAll(locFile.readText()).map { it.groupValues[1] }.toList()
                                val locRelativeFile = locRepositoryDirectory.toPath().relativize(locFile.toPath()).toString().replace(File.separatorChar, '/')
                                if (locPublicTypes.size > 1) {
                                    locProblems.add("$locRelativeFile: defines multiple main public Algites Python types: ${locPublicTypes.joinToString(", ")}")
                                } else if (locPublicTypes.size == 1) {
                                    val locExpectedFileName = "${AIcPythonTypeModuleName(locPublicTypes.single())}.py"
                                    if (locFile.name != locExpectedFileName) {
                                        locProblems.add("$locRelativeFile: public Algites Python type ${locPublicTypes.single()} must be in deterministic module '$locExpectedFileName'")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        locPathOwners.toSortedMap().forEach { (locRelativePath, locOwners) ->
            if (locOwners.map { it.first }.distinct().size > 1) {
                val locRenderedOwners = locOwners.sortedWith(compareBy({ it.first }, { it.second })).joinToString(", ") { (locArtifact, locSourceRoot) -> "$locArtifact ($locSourceRoot)" }
                locProblems.add("Python distribution path collision for '$locRelativePath': $locRenderedOwners")
            }
        }
        locArtifactFiles.toSortedMap().forEach { (locArtifact, locFiles) ->
            locFiles.filter { it.endsWith("/__init__.py") }.sorted().forEach { locInitPath ->
                val locPackageDirectory = locInitPath.removeSuffix("/__init__.py")
                val locPrefix = "$locPackageDirectory/"
                val locOtherArtifacts = locArtifactFiles.filterKeys { it != locArtifact }.filterValues { locOtherFiles -> locOtherFiles.any { it.startsWith(locPrefix) } }.keys.sorted()
                if (locOtherArtifacts.isNotEmpty()) {
                    locProblems.add("$locArtifact: '$locInitPath' turns shared package '${locPackageDirectory.replace('/', '.')}' into a regular package although it is also populated by ${locOtherArtifacts.joinToString(", ")}; shared cross-distribution package prefixes must remain PEP 420 namespace packages")
                }
            }
        }
        if (locProblems.isNotEmpty()) {
            throw GradleException(buildString {
                appendLine("Algites Python distribution/source-layout validation failed:")
                locProblems.distinct().sorted().forEach { appendLine(" - $it") }
            }.trimEnd())
        }
        println("Algites Python distribution/source-layout validation passed.")
    }
}

apply(plugin = "base")

apply(plugin = "eu.algites.pltf.modustro.builder.repository")

/* Typed script views reuse the compiled repository plugin's normalized metadata. */
@Suppress("UNCHECKED_CAST")
val modustroResolvedRepositoryMetadata = rootProject.extra["modustroResolvedRepositoryMetadata"] as Map<String, Any?>

@Suppress("UNCHECKED_CAST")
val modustroResolvedArtifactDirectoriesByGradleProjectPath = rootProject.extra["modustroResolvedArtifactDirectoriesByGradleProjectPath"] as Map<String, Map<String, Any?>>

fun modustroResolvedArtifactDirectoryForProject(aProjectPath: String): Map<String, Any?>? =
    modustroResolvedArtifactDirectoriesByGradleProjectPath[aProjectPath]

fun modustroResolvedVersionValue(aMetadata: Map<String, Any?>?): String? =
    (aMetadata?.get("version") as? Map<*, *>)?.get("resolvedValue")?.toString()
        ?.takeIf { it.isNotBlank() && it != "null" }

val locAlgitesLicensingScript = rootProject.file("gradle/tool/licensing/algites-licensing.gradle.kts")
if (locAlgitesLicensingScript.isFile) {
    apply(from = locAlgitesLicensingScript)
} else {
    apply(from = uri("https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/${System.getenv("MODUSTRO_PUBLIC_GOVERNANCE_REVISION") ?: "main"}/gradle/tool/licensing/algites-licensing.gradle.kts"))
}

@Suppress("UNCHECKED_CAST")
val locAlgitesLicensesForPathAndContentKind = rootProject.extra["algitesLicensesForPathAndContentKind"] as (String, String) -> List<Map<String, String?>>

@Suppress("UNCHECKED_CAST")
val locAlgitesResolveCredentialValue = extra["modustroResolveCredentialValue"] as (String, String, String, File) -> String?

val modustroCredentialPreflight = System.getenv("_TMP_ALGITES_CREDENTIAL_PREFLIGHT")
    ?.equals("true", ignoreCase = true) == true

val locAlgitesSourceRootResolverScript = rootProject.file("gradle/tool/repository/modustro-source-root-resolver.gradle.kts")
if (locAlgitesSourceRootResolverScript.isFile) {
    apply(from = locAlgitesSourceRootResolverScript)
} else {
    apply(from = uri("https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/${System.getenv("MODUSTRO_PUBLIC_GOVERNANCE_REVISION") ?: "main"}/gradle/tool/repository/modustro-source-root-resolver.gradle.kts"))
}

@Suppress("UNCHECKED_CAST")
val locAlgitesResolveSourceRootRelativePaths = rootProject.extra["modustroResolveSourceRootRelativePaths"] as
    (File, String, String) -> List<String>

val locAlgitesDocsSiteScript = rootProject.file("gradle/tool/documentation/modustro-docs-site.gradle.kts")
if (locAlgitesDocsSiteScript.isFile) {
    apply(from = locAlgitesDocsSiteScript)
} else {
    apply(from = uri("https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/${System.getenv("MODUSTRO_PUBLIC_GOVERNANCE_REVISION") ?: "main"}/gradle/tool/documentation/modustro-docs-site.gradle.kts"))
}

fun String.capitalizedForAlgitesName(): String =
    replaceFirstChar { locCharacter ->
        if (locCharacter.isLowerCase()) {
            locCharacter.titlecase()
        } else {
            locCharacter.toString()
        }
    }

fun modustroGradleOrEnvironmentValue(aName: String): String? =
    (providers.gradleProperty(aName).orNull
        ?: providers.environmentVariable(aName).orNull)
        ?.trim()
        ?.takeIf { it.isNotBlank() }

fun AIcModustroRunDirectoryRelativePath(aProjectDirectory: File): String {
    val locRepositoryRootPath = rootProject.projectDir.toPath().toAbsolutePath().normalize()
    val locProjectPath = aProjectDirectory.toPath().toAbsolutePath().normalize()
    require(locProjectPath.startsWith(locRepositoryRootPath)) {
        "Modustro Builder project directory '$aProjectDirectory' is outside repository root '${rootProject.projectDir}'."
    }

    val locRelativePath = locRepositoryRootPath
        .relativize(locProjectPath)
        .toString()
        .replace(File.separatorChar, '/')
        .trim('/')

    return if (locRelativePath.isBlank()) {
        "build/run"
    } else {
        "build/run/$locRelativePath/run"
    }
}

fun AIcModustroStringList(aValue: Any?): List<String> {
    return when (aValue) {
        is Iterable<*> -> aValue.mapNotNull { it?.toString()?.trim()?.lowercase()?.takeIf(String::isNotBlank) }
        null -> emptyList()
        else -> aValue.toString().split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }
    }
}

@Suppress("UNCHECKED_CAST")
fun AIcModustroExplicitBuildOutputTypes(aArtifactDirectory: Map<String, Any?>?, aTechnologyKind: String): Set<String>? {
    val locByTechnology = aArtifactDirectory?.get("buildOutputTypesByTechnologyKind") as? Map<*, *> ?: return null
    if (!locByTechnology.containsKey(aTechnologyKind)) return null
    return AIcModustroStringList(locByTechnology[aTechnologyKind]).toCollection(linkedSetOf())
}

fun AIcModustroPreparedSourceSet(aProject: Project, aTechnologyKind: String): AIcPreparedSourceSet {
    fun locRoots(aSourceType: String): List<String> =
        locAlgitesResolveSourceRootRelativePaths(aProject.projectDir, "product", aSourceType)

    return when (aTechnologyKind) {
        "java" -> {
            val locJavaRoots = locRoots("java")
            AIcPreparedSourceSet(
                "java",
                locJavaRoots.filterNot { it.endsWith(".gen") || it.endsWith(".extgen") },
                locJavaRoots.filter { it.endsWith(".gen") || it.endsWith(".extgen") },
                listOf("resources", "yamldefs", "jsondefs", "xmldefs", "config")
                    .flatMap(::locRoots)
            )
        }
        "python" -> {
            val locPythonRoots = locRoots("python")
            AIcPreparedSourceSet(
                "python",
                locPythonRoots.filterNot { it.endsWith(".gen") || it.endsWith(".extgen") },
                locPythonRoots.filter { it.endsWith(".gen") || it.endsWith(".extgen") },
                listOf("yamldefs", "jsondefs", "xmldefs", "config")
                    .flatMap(::locRoots)
            )
        }
        else -> throw GradleException("Phase-4 PreparedSourceSet adapter does not support TechnologyKind '$aTechnologyKind'.")
    }
}

data class AIcdModustroCredentialProfile(
    val id: String,
    val type: String,
    val configuration: Map<String, String>
)

data class AIcdModustroInputSubscription(
    val id: String,
    val enabled: Boolean,
    val visibility: String,
    val stability: String?,
    val subscriptionUri: String?,
    val subscriptionAdapter: String?,
    val subscriptionCredentialProfile: String?,
    val subscriptionOrder: Int,
    val configuration: Map<String, String>
)

@Suppress("UNCHECKED_CAST")
fun AIcModustroInputSubscriptions(
    aValue: Any?,
    aTechnologyKind: String,
    aInputSelector: String,
    aAllowedVisibilities: Set<String> = setOf("public", "private"),
    aAllowedStabilities: Set<String> = setOf("snapshot", "release")
): List<AIcdModustroInputSubscription> {
    val locSubscriptions = aValue as? Map<*, *> ?: return emptyList()
    val locItems = locSubscriptions["$aTechnologyKind.$aInputSelector"] as? List<*> ?: return emptyList()
    return locItems.mapNotNull { locRaw ->
        val locMap = locRaw as? Map<*, *> ?: return@mapNotNull null
        val locId = locMap["id"]?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val locEnabled = locMap["enabled"] as? Boolean ?: true
        val locVisibility = locMap["visibility"]?.toString()?.trim()?.lowercase() ?: "public"
        val locStability = locMap["stability"]?.toString()?.trim()?.lowercase()
        if (!locEnabled || locVisibility !in aAllowedVisibilities || (locStability != null && locStability !in aAllowedStabilities)) {
            return@mapNotNull null
        }
        AIcdModustroInputSubscription(
            id = locId,
            enabled = locEnabled,
            visibility = locVisibility,
            stability = locStability,
            subscriptionUri = locMap["subscriptionUri"]?.toString()?.trim()?.takeIf { it.isNotBlank() },
            subscriptionAdapter = locMap["subscriptionAdapter"]?.toString()?.trim()?.takeIf { it.isNotBlank() },
            subscriptionCredentialProfile = locMap["subscriptionCredentialProfile"]?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" },
            subscriptionOrder = (locMap["subscriptionOrder"] as? Number)?.toInt() ?: locMap["subscriptionOrder"]?.toString()?.toIntOrNull() ?: 0,
            configuration = (locMap["configuration"] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value.toString() } ?: emptyMap()
        )
    }.sortedWith(compareBy<AIcdModustroInputSubscription> { it.subscriptionOrder }.thenBy { it.id })
}

@Suppress("UNCHECKED_CAST")
fun AIcModustroCredentialProfiles(aValue: Any?): Map<String, AIcdModustroCredentialProfile> {
    val locProfiles = aValue as? Map<*, *> ?: return emptyMap()
    val locResult = linkedMapOf<String, AIcdModustroCredentialProfile>()
    locProfiles.forEach { (locRawId, locRawDefinition) ->
        val locId = locRawId?.toString() ?: return@forEach
        val locDefinition = locRawDefinition as? Map<*, *> ?: return@forEach
        val locType = locDefinition["type"]?.toString()?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return@forEach
        val locConfiguration = (locDefinition["configuration"] as? Map<*, *>)
            ?.entries
            ?.associate { locEntry -> locEntry.key.toString() to locEntry.value.toString() }
            ?: emptyMap()
        locResult[locId] = AIcdModustroCredentialProfile(locId, locType, locConfiguration)
    }
    return locResult
}

fun AIcModustroCredentialValue(aProfile: AIcdModustroCredentialProfile, aField: String): String? =
    locAlgitesResolveCredentialValue(aProfile.id, aProfile.type, aField, rootProject.projectDir)

fun AIcModustroRequireBasicCredential(aProfile: AIcdModustroCredentialProfile): Pair<String, String> {
    val locUsername = AIcModustroCredentialValue(aProfile, "Username")
    val locPassword = AIcModustroCredentialValue(aProfile, "Password")
    if (locUsername.isNullOrEmpty() || locPassword.isNullOrEmpty()) {
        throw GradleException(
            "Credential profile '${aProfile.id}' type 'basic' is not available in ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS or the local Algites secure-store credential document."
        )
    }
    return locUsername to locPassword
}

fun AIcAlgitesPythonDistributionName(aGroupId: String?, aArtifactCoordinateId: String): String {
    val locOwnerPrefix = aGroupId
        ?.split('.')
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.take(2)
        ?.joinToString("-")
        ?.lowercase()
        ?.replace(Regex("[._-]+"), "-")
        ?.trim('-')
        .orEmpty()
    val locNormalizedArtifactCoordinateId = aArtifactCoordinateId
        .lowercase()
        .replace(Regex("[._-]+"), "-")
        .trim('-')
    return listOf(locOwnerPrefix, locNormalizedArtifactCoordinateId)
        .filter(String::isNotEmpty)
        .joinToString("-")
}

fun AIcAlgitesPythonIdentifierSegment(aValue: String): String {
    val locNormalized = aValue.lowercase().replace(Regex("[^a-z0-9_]"), "_")
    val locNonEmpty = locNormalized.ifBlank { "artifact" }
    return if (locNonEmpty.first().isDigit()) "_$locNonEmpty" else locNonEmpty
}

fun AIcAlgitesPythonImportNamespace(aRepositoryId: String, aModulePath: String): String {
    val locRepositorySegments = aRepositoryId.split('.').filter { it.isNotBlank() }
    val locModuleSegments = aModulePath.split('.').filter { it.isNotBlank() }
    return (listOf("algites") + locRepositorySegments + locModuleSegments)
        .map(::AIcAlgitesPythonIdentifierSegment)
        .joinToString(".")
}

/** Provides a fresh UTC timestamp even when a previous configuration was cached. */
abstract class AIcAlgitesSnapshotTimestamp : org.gradle.api.provider.ValueSource<String, org.gradle.api.provider.ValueSourceParameters.None> {
    override fun obtain(): String = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
        .withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.now())
}

fun AIcAlgitesSnapshotInstanceId(): String {
    val locValue = (providers.gradleProperty("algites.snapshot.instanceId").orNull
        ?: providers.environmentVariable("ALGITES_SNAPSHOT_INSTANCE_ID").orNull)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return providers.of(AIcAlgitesSnapshotTimestamp::class.java) {}.get()
    if (!Regex("^[0-9]{14,20}$").matches(locValue)) {
        throw GradleException(
            "Algites snapshot instance id must be a UTC timestamp (14 to 20 decimal digits), but got '$locValue'. " +
                "Use -Palgites.snapshot.instanceId=<UTC timestamp> or ALGITES_SNAPSHOT_INSTANCE_ID."
        )
    }
    return locValue
}

fun AIcAlgitesPythonVersion(aVersion: String, aSnapshotInstanceId: String): String {
    val locSnapshotSuffix = "-SNAPSHOT"
    if (aVersion.endsWith(locSnapshotSuffix, ignoreCase = true)) {
        return aVersion.dropLast(locSnapshotSuffix.length).replace('-', '.') + ".dev$aSnapshotInstanceId"
    }
    return aVersion.replace('-', '.')
}

fun AIcAlgitesPythonSnapshotVersionPrefix(aReleaseVersion: String): String =
    aReleaseVersion.replace('-', '.') + ".dev"


fun AIcAlgitesCanonicalArtifactId(aProjectPath: String): String {
    val locPathDots = aProjectPath.removePrefix(":").replace(':', '.')
    return if (locPathDots.isBlank()) rootProject.name else "${rootProject.name}_$locPathDots"
}

fun AIcAlgitesEffectiveArtifactId(aCanonicalArtifactId: String, aVariantId: String?): String =
    aVariantId?.takeIf { it.isNotBlank() }?.let { "$aCanonicalArtifactId-$it" } ?: aCanonicalArtifactId

fun AIcAlgitesSnapshotVersionForTechnology(aReleaseVersion: String, aTechnology: String): String = when (aTechnology) {
    "java" -> "$aReleaseVersion-SNAPSHOT"
    "python" -> AIcAlgitesPythonSnapshotVersionPrefix(aReleaseVersion)
    else -> "$aReleaseVersion-SNAPSHOT"
}

fun AIcModustroDependencyDefinitions(aArtifactDirectory: Map<String, Any?>?, aPropertyName: String): List<Map<String, Any?>> =
    (aArtifactDirectory?.get(aPropertyName) as? List<Map<String, Any?>>).orEmpty()

fun AIcModustroDependencyUsageToGradleConfiguration(aUsage: String): String = when (aUsage) {
    "product_api" -> "api"
    "product_implementation" -> "implementation"
    "product_compile_only" -> "compileOnly"
    "product_compile_only_api" -> "compileOnlyApi"
    "product_runtime_only" -> "runtimeOnly"
    "product_annotation_processor" -> "annotationProcessor"
    "develop_implementation" -> "testImplementation"
    "develop_compile_only" -> "testCompileOnly"
    "develop_runtime_only" -> "testRuntimeOnly"
    "develop_annotation_processor" -> "testAnnotationProcessor"
    else -> throw GradleException("Unsupported Modustro Builder dependency Usage '$aUsage'.")
}

fun AIcModustroDependencyUsages(aDefinition: Map<String, Any?>): List<String> {
    val locUsages = AIcModustroStringList(aDefinition["usages"]).distinct()
    return if (locUsages.isEmpty()) listOf("product_implementation") else locUsages
}

fun AIcModustroRequiredBuildOutputTypes(aDefinition: Map<String, Any?>): List<String> =
    AIcModustroStringList(aDefinition["requiredBuildOutputTypes"]).distinct()

fun AIcValidatePhase2DependencyOutputs(
    aDefinition: Map<String, Any?>,
    aTechnologyKind: String,
    aContext: String
) {
    val locOutputs = AIcModustroRequiredBuildOutputTypes(aDefinition)
    if (locOutputs.isEmpty()) return
    val locDependencyKind = aDefinition["dependencyKind"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
    if (locDependencyKind != "modustro") {
        throw GradleException("$aContext may use RequiredBuildOutputTypes only with DependencyKind 'modustro' in the Phase-2 bridge.")
    }
    val locSupported = when (aTechnologyKind) {
        "java" -> setOf("java_classes_jar")
        "python" -> setOf("python_distribution")
        else -> emptySet()
    }
    val locUnsupported = locOutputs.filterNot(locSupported::contains)
    if (locUnsupported.isNotEmpty()) {
        throw GradleException(
            "$aContext requests BuildOutputType(s) ${locUnsupported.joinToString(", ")} for TechnologyKind '$aTechnologyKind'. " +
                "Phase 2 resolves only the default dependency output(s) ${locSupported.joinToString(", ")}; concrete alternative output selection is introduced with Phase 3 producers."
        )
    }
}

fun AIcModustroPythonEffectiveUsages(aDefinition: Map<String, Any?>, aContext: String): List<String> {
    val locUsages = AIcModustroDependencyUsages(aDefinition)
    locUsages.forEach { locUsage ->
        when (locUsage) {
            "product_implementation" -> logger.warn(
                "$aContext Usage 'product_implementation' is equivalent to a normal published/runtime dependency in Python; Python has no separate API/implementation package metadata."
            )
            "product_compile_only_api" -> logger.warn(
                "$aContext Usage 'product_compile_only_api' is build/source-processing-only in Python; Python has no exported compile-only API equivalent."
            )
            "product_annotation_processor", "develop_annotation_processor" -> logger.warn(
                "$aContext Usage '$locUsage' is currently a no-op for Python because the Modustro Python annotation-processing hook is not implemented yet."
            )
            "develop_compile_only", "develop_runtime_only" -> logger.warn(
                "$aContext Usage '$locUsage' is mapped to the common Python development environment because Python packaging does not distinguish this Gradle-style development role."
            )
        }
    }
    return locUsages
}

fun AIcModustroPythonParticipatesInResolution(aUsage: String): Boolean =
    aUsage !in setOf("product_annotation_processor", "develop_annotation_processor")

data class AIcdAlgitesBridgeVersionRequirement(
    val exact: String?,
    val minimum: AIcVersionBound?,
    val maximum: AIcVersionBound?,
    val maximumStrict: Boolean?,
    val excludedVersions: List<String>,
    val preferred: String?
) {
    fun AIcEffectiveMaximumStrict(): Boolean = maximum != null && (maximumStrict == null || maximumStrict)
}

@Suppress("UNCHECKED_CAST")
fun AIcAlgitesPortableVersionRequirement(aDefinition: Map<String, Any?>, aContext: String): AIcdAlgitesBridgeVersionRequirement? {
    val locRequirement = aDefinition["versionRequirement"] as? Map<String, Any?> ?: return null
    fun locBoundary(aName: String): AIcVersionBound? {
        val locBoundary = locRequirement[aName] as? Map<String, Any?> ?: return null
        val locVersion = locBoundary["version"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            ?: throw GradleException("$aContext VersionRequirement.$aName is missing Version.")
        val locInclusive = locBoundary["inclusive"] as? Boolean
            ?: throw GradleException("$aContext VersionRequirement.$aName is missing Inclusive.")
        return AIcVersionBound(locVersion, locInclusive)
    }
    return AIcdAlgitesBridgeVersionRequirement(
        exact = locRequirement["exact"]?.toString()?.takeIf { it.isNotBlank() && it != "null" },
        minimum = locBoundary("minimum"),
        maximum = locBoundary("maximum"),
        maximumStrict = locRequirement["maximumStrict"] as? Boolean,
        excludedVersions = (locRequirement["exclude"] as? List<*>)
            .orEmpty()
            .mapNotNull { locValue -> locValue?.toString()?.takeIf { it.isNotBlank() && it != "null" } },
        preferred = locRequirement["prefer"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
    )
}

fun AIcAlgitesNativeVersionText(
    aVersion: String,
    aDependencyKind: String,
    aTechnologyKind: String,
    aContext: String
): String {
    if (aDependencyKind != "modustro") return aVersion
    return when (aTechnologyKind) {
        "java" -> {
            val locResult = AIcAlgitesToGradleVersionConverterV1().convert(AIcAlgitesVersionTextV1.parse(aVersion)).value()
                ?: throw GradleException("$aContext cannot convert Modustro version '$aVersion' to a Gradle version.")
            locResult.strictly()?.takeIf { it.isNotBlank() }
                ?: locResult.require()?.takeIf { it.isNotBlank() }
                ?: throw GradleException("$aContext Modustro version '$aVersion' did not produce an exact Gradle version.")
        }
        "python" -> AIcAlgitesToPep440VersionConverterV1()
            .convert(AIcAlgitesVersionTextV1.parse(aVersion))
            .value()
            ?.text()
            ?.takeIf { it.isNotBlank() }
            ?: throw GradleException("$aContext cannot convert Modustro version '$aVersion' to a PEP 440 version.")
        else -> throw GradleException("$aContext uses unsupported TechnologyKind '$aTechnologyKind' for version comparison.")
    }
}

fun AIcAlgitesNativePortableVersionRequirement(
    aRequirement: AIcdAlgitesBridgeVersionRequirement,
    aDependencyKind: String,
    aTechnologyKind: String,
    aContext: String
): AIiVersionRequirement {
    fun locVersion(aVersion: String?): String? = aVersion?.let { locValue ->
        AIcAlgitesNativeVersionText(locValue, aDependencyKind, aTechnologyKind, aContext)
    }
    fun locBound(aBound: AIcVersionBound?): AIcVersionBound? = aBound?.let { locValue ->
        AIcVersionBound(
            AIcAlgitesNativeVersionText(locValue.versionText(), aDependencyKind, aTechnologyKind, aContext),
            locValue.inclusive()
        )
    }

    val locNativeRequirement = AIcVersionRequirement(
        locVersion(aRequirement.exact),
        locBound(aRequirement.minimum),
        locBound(aRequirement.maximum),
        aRequirement.maximumStrict,
        aRequirement.excludedVersions.mapNotNull(::locVersion),
        locVersion(aRequirement.preferred)
    )
    val locScheme = when (aTechnologyKind) {
        "java" -> AIcGradleVersionScheme.INSTANCE
        "python" -> AIcPep440VersionScheme.INSTANCE
        else -> throw GradleException("$aContext uses unsupported TechnologyKind '$aTechnologyKind' for version normalization.")
    }
    val locNormalization = try {
        AIsVersionRequirementNormalizer.normalize(locNativeRequirement, locScheme)
    } catch (locException: IllegalArgumentException) {
        throw GradleException("$aContext has incompatible effective version requirements: ${locException.message}", locException)
    }
    locNormalization.informationMessages().forEach { locMessage ->
        logger.info("$aContext $locMessage")
    }
    return locNormalization.requirement()
}

fun AIcAlgitesGradleVersionConstraint(aDefinition: Map<String, Any?>, aContext: String): AIrGradleVersionConstraint? {
    val locRequirement = AIcAlgitesPortableVersionRequirement(aDefinition, aContext) ?: return null
    val locDependencyKind = aDefinition["dependencyKind"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        ?: throw GradleException("$aContext is missing DependencyKind.")
    val locNativeRequirement = AIcAlgitesNativePortableVersionRequirement(
        locRequirement,
        locDependencyKind,
        "java",
        aContext
    )
    return AIcGradleVersionRequirementRenderer.render(locNativeRequirement)
}

fun AIcAlgitesPythonVersionRequirements(aDefinition: Map<String, Any?>, aContext: String): Map<AInPythonBuildPhase, String> {
    val locRequirement = AIcAlgitesPortableVersionRequirement(aDefinition, aContext)
        ?: return mapOf(AInPythonBuildPhase.STRICT_MAXIMUMS to "")
    val locDependencyKind = aDefinition["dependencyKind"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        ?: throw GradleException("$aContext is missing DependencyKind.")
    if (locDependencyKind == "modustro") {
        val locAlgitesRequirement = AIcVersionRequirement(
            locRequirement.exact,
            locRequirement.minimum,
            locRequirement.maximum,
            locRequirement.maximumStrict,
            locRequirement.excludedVersions,
            locRequirement.preferred
        )
        return AIcAlgitesVersionRequirementToPep440RendererV1.render(locAlgitesRequirement)
    }
    val locNativeRequirement = AIcAlgitesNativePortableVersionRequirement(
        locRequirement,
        locDependencyKind,
        "python",
        aContext
    )
    return AIcPep440VersionRequirementRenderer.render(locNativeRequirement)
}

fun AIcAlgitesPythonDependencyPackageName(
    aDefinition: Map<String, Any?>,
    aConsumerProjectPath: String
): String {
    val locDependencyKind = aDefinition["dependencyKind"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        ?: throw GradleException("Python dependency for '$aConsumerProjectPath' is missing DependencyKind.")
    val locArtifactId = aDefinition["artifactId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        ?: throw GradleException("Python dependency for '$aConsumerProjectPath' is missing ArtifactId.")
    if (locDependencyKind == "python") return locArtifactId
    if (locDependencyKind != "modustro") {
        throw GradleException("DependencyKind '$locDependencyKind' cannot participate in Python dependency resolution for '$aConsumerProjectPath'.")
    }

    val locLocalTarget = AIcAlgitesLocalDependencyTarget(aDefinition, aConsumerProjectPath)
    if (locLocalTarget != null) {
        val (locTargetProject, locTargetMetadata) = locLocalTarget
        if ("python" !in AIcModustroStringList(locTargetMetadata["technologyKinds"])) {
            throw GradleException("Modustro dependency '$locArtifactId' for '$aConsumerProjectPath' targets '${locTargetProject.path}', which does not provide TechnologyKind 'python'.")
        }
        val locTargetGroupId = locTargetMetadata["groupId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            ?: modustroResolvedRepositoryMetadata["groupId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        val locTargetVariantId = locTargetMetadata["variantId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        val locEffectiveArtifactId = AIcAlgitesEffectiveArtifactId(AIcAlgitesCanonicalArtifactId(locTargetProject.path), locTargetVariantId)
        return AIcAlgitesPythonDistributionName(locTargetGroupId, locEffectiveArtifactId)
    }

    val locGroupId = aDefinition["groupId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        ?: throw GradleException("External Modustro Python dependency '$locArtifactId' for '$aConsumerProjectPath' requires GroupId.")
    val locVariantId = aDefinition["variantId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
    return AIcAlgitesPythonDistributionName(locGroupId, AIcAlgitesEffectiveArtifactId(locArtifactId, locVariantId))
}

fun AIcAlgitesPythonResolutionEntry(
    aDefinition: Map<String, Any?>,
    aConsumerProjectPath: String,
    aContext: String
): String {
    val locPackageName = AIcAlgitesPythonDependencyPackageName(aDefinition, aConsumerProjectPath)
    val locRequirements = AIcAlgitesPythonVersionRequirements(aDefinition, aContext)
    val locPreferred = locRequirements[AInPythonBuildPhase.PREFERRED].orEmpty()
    val locNonStrict = locRequirements[AInPythonBuildPhase.NON_STRICT_MAXIMUMS].orEmpty()
    val locStrict = locRequirements[AInPythonBuildPhase.STRICT_MAXIMUMS].orEmpty()
    return listOf(locPackageName, locPreferred, locNonStrict, locStrict).joinToString("\t")
}

fun AIcApplyAlgitesGradleVersionConstraint(aTarget: MutableVersionConstraint, aConstraint: AIrGradleVersionConstraint?) {
    if (aConstraint == null) return
    aConstraint.require()?.takeIf { it.isNotBlank() }?.let(aTarget::require)
    aConstraint.strictly()?.takeIf { it.isNotBlank() }?.let(aTarget::strictly)
    aConstraint.prefer()?.takeIf { it.isNotBlank() }?.let(aTarget::prefer)
    if (aConstraint.reject().isNotEmpty()) {
        aTarget.reject(*aConstraint.reject().toTypedArray())
    }
}

fun AIcAlgitesLocalDependencyTarget(
    aDefinition: Map<String, Any?>,
    aConsumerProjectPath: String
): Pair<Project, Map<String, Any?>>? {
    val locDependencyKind = aDefinition["dependencyKind"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
    if (locDependencyKind != "modustro") return null
    val locArtifactId = aDefinition["artifactId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        ?: throw GradleException("Modustro dependency for '$aConsumerProjectPath' is missing ArtifactId.")
    val locGroupId = aDefinition["groupId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
    val locVariantId = aDefinition["variantId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
    val locMatches = modustroResolvedArtifactDirectoriesByGradleProjectPath.entries.mapNotNull { (locProjectPath, locMetadata) ->
        if (locProjectPath == ":") return@mapNotNull null
        val locCanonicalArtifactId = AIcAlgitesCanonicalArtifactId(locProjectPath)
        val locTargetGroupId = locMetadata["groupId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        val locTargetVariantId = locMetadata["variantId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        val locIdMatches = locArtifactId == locCanonicalArtifactId
        val locGroupMatches = locGroupId == null || locGroupId == locTargetGroupId
        val locVariantMatches = locVariantId == locTargetVariantId
        if (locIdMatches && locGroupMatches && locVariantMatches) {
            rootProject.findProject(locProjectPath)?.let { locProject -> locProject to locMetadata }
        } else null
    }
    if (locMatches.size > 1) {
        throw GradleException("Modustro dependency '$locArtifactId' for '$aConsumerProjectPath' matches multiple local artifacts.")
    }
    return locMatches.singleOrNull()
}

fun AIcValidateLocalAlgitesDependencyVersion(
    aDefinition: Map<String, Any?>,
    aTargetProject: Project,
    aContext: String
) {
    val locRequirement = AIcAlgitesPortableVersionRequirement(aDefinition, aContext) ?: return
    val locDependencyKind = aDefinition["dependencyKind"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        ?: throw GradleException("$aContext is missing DependencyKind.")
    val locNativeRequirement = AIcAlgitesNativePortableVersionRequirement(
        locRequirement,
        locDependencyKind,
        "java",
        aContext
    )
    val locCandidateRequirement = AIcVersionRequirement(
        aTargetProject.version.toString(),
        locNativeRequirement.minimum(),
        locNativeRequirement.maximum(),
        locNativeRequirement.maximumStrict(),
        locNativeRequirement.excludedVersionTexts(),
        locNativeRequirement.preferredVersionText()
    )
    try {
        AIsVersionRequirementNormalizer.normalize(locCandidateRequirement, AIcGradleVersionScheme.INSTANCE)
    } catch (locException: IllegalArgumentException) {
        throw GradleException(
            "$aContext does not accept local project '${aTargetProject.path}' version '${aTargetProject.version}': ${locException.message}",
            locException
        )
    }
}

/** Validates an in-repository Python dependency without asking a remote package index for its wheel. */
fun AIcValidateLocalAlgitesPythonDependencyVersion(
    aDefinition: Map<String, Any?>,
    aTargetMetadata: Map<String, Any?>,
    aTargetProjectPath: String,
    aContext: String
) {
    val locRequirement = AIcAlgitesPortableVersionRequirement(aDefinition, aContext) ?: return
    val locTargetVersion = modustroResolvedVersionValue(aTargetMetadata)
        ?: modustroResolvedVersionValue(modustroResolvedArtifactDirectoryForProject(":"))
        ?: throw GradleException("$aContext targets local Python artifact '$aTargetProjectPath' without a resolvable version.")
    if (locRequirement.exact != null && locRequirement.exact != locTargetVersion) {
        throw GradleException(
            "$aContext requires '${locRequirement.exact}', but local Python artifact '$aTargetProjectPath' has version '$locTargetVersion'."
        )
    }
    val locNativeRequirement = AIcAlgitesNativePortableVersionRequirement(
        locRequirement, "modustro", "java", aContext
    )
    val locCandidateRequirement = AIcVersionRequirement(
        locTargetVersion,
        locNativeRequirement.minimum(),
        locNativeRequirement.maximum(),
        locNativeRequirement.maximumStrict(),
        locNativeRequirement.excludedVersionTexts(),
        locNativeRequirement.preferredVersionText()
    )
    try {
        AIsVersionRequirementNormalizer.normalize(locCandidateRequirement, AIcGradleVersionScheme.INSTANCE)
    } catch (locException: IllegalArgumentException) {
        throw GradleException(
            "$aContext does not accept local Python artifact '$aTargetProjectPath' version '$locTargetVersion': ${locException.message}",
            locException
        )
    }
}

@Suppress("UNCHECKED_CAST")
fun AIcModustroEnvironmentRequirement(aArtifactDirectory: Map<String, Any?>?, aEnvironment: String): Map<String, Any?>? =
    (aArtifactDirectory?.get("environmentRequirements") as? Map<String, Map<String, Any?>>)?.get(aEnvironment.lowercase())

fun AIcModustroPreferredEnvironmentMajorVersion(aRequirement: Map<String, Any?>?, aContext: String): Int? {
    if (aRequirement == null) return null
    val locVersionText = aRequirement["prefer"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        ?: aRequirement["exact"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        ?: (aRequirement["minimum"] as? Map<*, *>)?.get("version")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        ?: return null
    val locMajor = Regex("^[0-9]+").find(locVersionText)?.value?.toIntOrNull()
        ?: throw GradleException("$aContext version '$locVersionText' does not start with a numeric major version.")
    return locMajor
}

fun AIcConfigureModustroJavaEnvironment(aProject: Project, aArtifactDirectory: Map<String, Any?>) {
    val locRequirement = AIcModustroEnvironmentRequirement(aArtifactDirectory, "java") ?: return
    val locMajor = AIcModustroPreferredEnvironmentMajorVersion(locRequirement, "Project '${aProject.path}' EnvironmentRequirements.Java") ?: return
    aProject.extensions
        .getByType(JavaPluginExtension::class.java)
        .toolchain.languageVersion.set(JavaLanguageVersion.of(locMajor))
}

fun AIcConfigureModustroJavaDependencies(aProject: Project, aArtifactDirectory: Map<String, Any?>) {
    val locConsumerTechnologyKinds = AIcModustroStringList(aArtifactDirectory["technologyKinds"]).toSet()
    if ("java" !in locConsumerTechnologyKinds) return

    val locRequiredConfigurations = linkedMapOf<String, String>()
    val locEntriesByConfiguration = linkedMapOf<String, MutableList<Pair<Map<String, Any?>, Boolean>>>()

    fun locCollect(aDefinitions: List<Map<String, Any?>>, aConstraintOnly: Boolean) {
        aDefinitions.forEachIndexed { locIndex, locDefinition ->
            val locDependencyKind = locDefinition["dependencyKind"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                ?: throw GradleException("Project '${aProject.path}' dependency entry #$locIndex is missing DependencyKind.")
            if (locDependencyKind !in setOf("modustro", "java")) return@forEachIndexed
            val locArtifactId = locDefinition["artifactId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" } ?: "<unknown>"
            val locContext = "Project '${aProject.path}' ${if (aConstraintOnly) "DependencyConstraints" else "Dependencies"}[$locIndex] '$locArtifactId'"
            if (!aConstraintOnly) AIcValidatePhase2DependencyOutputs(locDefinition, "java", locContext)

            val locUsages = AIcModustroDependencyUsages(locDefinition)
            locUsages.forEach { locUsage ->
                val locConfiguration = AIcModustroDependencyUsageToGradleConfiguration(locUsage)
                locRequiredConfigurations.putIfAbsent(locConfiguration, locUsage)
                locEntriesByConfiguration.getOrPut(locConfiguration) { mutableListOf() }.add(locDefinition to aConstraintOnly)
            }
        }
    }

    locCollect(AIcModustroDependencyDefinitions(aArtifactDirectory, "dependencies"), false)
    locCollect(AIcModustroDependencyDefinitions(aArtifactDirectory, "dependencyConstraints"), true)

    aProject.configurations.all(
        object : Action<Configuration> {
            override fun execute(locGradleConfiguration: Configuration) {
                val locEntries = locEntriesByConfiguration[locGradleConfiguration.name] ?: return
                locEntries.forEachIndexed { locIndex, (locDefinition, locConstraintOnly) ->
                    val locDependencyKind = locDefinition["dependencyKind"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                        ?: throw GradleException("Project '${aProject.path}' dependency entry #$locIndex is missing DependencyKind.")
                    val locArtifactId = locDefinition["artifactId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                        ?: throw GradleException("Project '${aProject.path}' dependency entry #$locIndex is missing ArtifactId.")
                    val locContext = "Project '${aProject.path}' ${if (locConstraintOnly) "DependencyConstraints" else "Dependencies"}[$locIndex] '$locArtifactId'"
                    val locVersionConstraint = AIcAlgitesGradleVersionConstraint(locDefinition, locContext)

                    val locLocalTarget = AIcAlgitesLocalDependencyTarget(locDefinition, aProject.path)
                    if (locLocalTarget != null) {
                        val (locTargetProject, locTargetMetadata) = locLocalTarget
                        val locTargetTechnologyKinds = AIcModustroStringList(locTargetMetadata["technologyKinds"]).toSet()
                        if ("java" !in locTargetTechnologyKinds) {
                            throw GradleException("$locContext targets local Algites artifact '${locTargetProject.path}' which does not provide TechnologyKind 'java'.")
                        }
                        AIcValidateLocalAlgitesDependencyVersion(locDefinition, locTargetProject, locContext)
                        val locProjectDependency = aProject.dependencies.project(mapOf("path" to locTargetProject.path))
                        if (locConstraintOnly) {
                            aProject.dependencies.constraints.add(locGradleConfiguration.name, locProjectDependency)
                        } else {
                            aProject.dependencies.add(locGradleConfiguration.name, locProjectDependency)
                        }

                        if (locVersionConstraint != null) {
                            val locTargetGroupId = locTargetMetadata["groupId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                                ?: modustroResolvedRepositoryMetadata["groupId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                                ?: throw GradleException("$locContext targets local Algites artifact '${locTargetProject.path}' without a resolvable GroupId for published version constraints.")
                            val locTargetVariantId = locTargetMetadata["variantId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                            val locTargetArtifactId = AIcAlgitesEffectiveArtifactId(
                                AIcAlgitesCanonicalArtifactId(locTargetProject.path),
                                locTargetVariantId
                            )
                            val locTargetNotation = "$locTargetGroupId:$locTargetArtifactId"
                            aProject.dependencies.constraints.add(
                                locGradleConfiguration.name,
                                locTargetNotation,
                                object : Action<DependencyConstraint> {
                                    override fun execute(locDependencyConstraint: DependencyConstraint) {
                                        locDependencyConstraint.version(
                                            object : Action<MutableVersionConstraint> {
                                                override fun execute(locMutableVersionConstraint: MutableVersionConstraint) {
                                                    AIcApplyAlgitesGradleVersionConstraint(
                                                        locMutableVersionConstraint,
                                                        locVersionConstraint
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            )
                        }
                        return@forEachIndexed
                    }

                    val locGroupId = locDefinition["groupId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                        ?: throw GradleException(
                            "$locContext does not resolve to a local Algites artifact and therefore requires GroupId for external resolution."
                        )
                    val locVariantId = locDefinition["variantId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                    val locEffectiveArtifactId = if (locDependencyKind == "modustro" && locVariantId != null) "$locArtifactId-$locVariantId" else locArtifactId
                    val locNotation = "$locGroupId:$locEffectiveArtifactId"
                    if (locConstraintOnly) {
                        aProject.dependencies.constraints.add(
                            locGradleConfiguration.name,
                            locNotation,
                            object : Action<DependencyConstraint> {
                                override fun execute(locDependencyConstraint: DependencyConstraint) {
                                    locDependencyConstraint.version(
                                        object : Action<MutableVersionConstraint> {
                                            override fun execute(locMutableVersionConstraint: MutableVersionConstraint) {
                                                AIcApplyAlgitesGradleVersionConstraint(
                                                    locMutableVersionConstraint,
                                                    locVersionConstraint
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        )
                    } else {
                        val locDependency = aProject.dependencies.create(locNotation)
                        if (locDependency is ExternalModuleDependency) {
                            locDependency.version(
                                object : Action<MutableVersionConstraint> {
                                    override fun execute(locMutableVersionConstraint: MutableVersionConstraint) {
                                        AIcApplyAlgitesGradleVersionConstraint(
                                            locMutableVersionConstraint,
                                            locVersionConstraint
                                        )
                                    }
                                }
                            )
                        }
                        aProject.dependencies.add(locGradleConfiguration.name, locDependency)
                    }
                }
            }
        }
    )

    aProject.afterEvaluate {
        locRequiredConfigurations.forEach { (locConfiguration, locUsage) ->
            if (aProject.configurations.findByName(locConfiguration) == null) {
                throw GradleException(
                    "Project '${aProject.path}' does not provide Gradle configuration '$locConfiguration' required by Algites Usage '$locUsage'."
                )
            }
        }
    }
}

fun requireModustroGroupForPublishing(aProjectPath: String, aProjectGroup: Any?) {
    val locGroupText = aProjectGroup?.toString()?.trim()

    if (locGroupText.isNullOrBlank() || locGroupText == "unspecified") {
        throw GradleException(
            "Project '$aProjectPath' is being published, but no Maven group could be resolved. " +
                "Define top-level groupId in modustro-source-repository.yml, modustro-artifact-set.yml, or modustro-artifact.yml."
        )
    }
}

val modustroSourceRepositoryVisibility = modustroResolvedRepositoryMetadata["visibility"]
    ?.toString()
    ?.lowercase()
    ?.takeIf { it.isNotBlank() }
    ?: throw GradleException("Algites source repository visibility could not be resolved.")

val modustroRequestedRepositoryVisibility = modustroGradleOrEnvironmentValue("ALGITES_VISIBILITY")
    ?.lowercase()
    ?.takeIf { it.isNotBlank() }
if (modustroRequestedRepositoryVisibility != null && modustroRequestedRepositoryVisibility != modustroSourceRepositoryVisibility) {
    throw GradleException(
        "Requested Algites visibility '$modustroRequestedRepositoryVisibility' does not match " +
            "source repository visibility '$modustroSourceRepositoryVisibility'."
    )
}

val modustroPublicationRepositoryVisibility = when (modustroSourceRepositoryVisibility) {
    "pub" -> "public"
    "priv" -> "private"
    else -> throw GradleException("Unsupported Algites repository visibility '$modustroSourceRepositoryVisibility'.")
}

val modustroDocsPagesBranch = modustroGradleOrEnvironmentValue("ALGITES_DOCS_PAGES_BRANCH") ?: "gh-pages"
val modustroIsCi = providers.environmentVariable("CI")
    .map { locValue -> locValue.equals("true", ignoreCase = true) }
    .orElse(false)
    .get()

val modustroRequestedTechnologyKinds = (
    modustroGradleOrEnvironmentValue("ALGITES_TECHNOLOGY_KINDS")
        ?: modustroGradleOrEnvironmentValue("algites.technologyKinds")
)
    ?.split(',')
    ?.map { it.trim().lowercase() }
    ?.filter { it.isNotBlank() }
    ?.toSet()
    ?: emptySet()

val modustroRequestedTasks = gradle.startParameter.taskNames
val modustroIsPublishRequested = modustroRequestedTasks.any { locTaskName ->
    locTaskName == "publish" ||
        locTaskName.startsWith("publish") ||
        locTaskName.contains("publish", ignoreCase = true)
}

val algitesSnapshotInstanceId = AIcAlgitesSnapshotInstanceId()

val modustroDependencyPreflight = tasks.register("modustroDependencyPreflight") {
    group = "verification"
    description = "Resolves dependency graphs for all effective technologies before compilation or packaging starts."
}

allprojects {
    val locAlgitesRunDirectoryRelativePath = AIcModustroRunDirectoryRelativePath(project.projectDir)
    extra["modustroRunDirectoryRelativePath"] = locAlgitesRunDirectoryRelativePath
    layout.buildDirectory.set(
        rootProject.layout.projectDirectory.dir("$locAlgitesRunDirectoryRelativePath/bld/gradle")
    )

    val locAlgitesArtifactDirectory = modustroResolvedArtifactDirectoryForProject(project.path)
    val locAlgitesResolvedProjectGroup = locAlgitesArtifactDirectory?.get("groupId")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        ?: modustroResolvedRepositoryMetadata["groupId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }

    if (!locAlgitesResolvedProjectGroup.isNullOrBlank()) {
        /*
         * Keep Gradle's internal component identity unique for nested Algites
         * artifacts that can legitimately share the same leaf project name
         * (for example common or v1). Maven publication coordinates are set
         * explicitly from Algites metadata below and therefore remain unchanged.
         */
        val locAlgitesCanonicalArtifactId = AIcAlgitesCanonicalArtifactId(project.path)
        val locAlgitesVariantId = locAlgitesArtifactDirectory?.get("variantId")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        val locAlgitesEffectiveArtifactId = AIcAlgitesEffectiveArtifactId(locAlgitesCanonicalArtifactId, locAlgitesVariantId)
        group = "$locAlgitesResolvedProjectGroup.__algites_gradle.$locAlgitesEffectiveArtifactId"
        extra["modustroResolvedProjectGroup"] = locAlgitesResolvedProjectGroup
    }

    version = modustroResolvedVersionValue(locAlgitesArtifactDirectory)
        ?: modustroResolvedVersionValue(modustroResolvedArtifactDirectoryForProject(":"))
        ?: "0.0.1-SNAPSHOT"

    plugins.withId("base") {
        tasks.named<Delete>("clean").configure {
            delete(rootProject.layout.projectDirectory.dir(locAlgitesRunDirectoryRelativePath))
            delete(project.layout.projectDirectory.dir("src/product/java.gen"))
            delete(project.layout.projectDirectory.dir("src/product/python.gen"))
            delete(project.layout.projectDirectory.dir("src/develop/java.gen"))
            delete(project.layout.projectDirectory.dir("src/develop/python.gen"))
        }
    }

    /*
     * Python test helpers may import modules from staged dependency directories.
     * Do not generate transient bytecode while running TestNG-based JVM tests.
     * Classpath normalization prevents existing .pyc files from changing Test
     * up-to-date and build-cache fingerprints without changing the runtime classpath.
     */
    normalization {
        runtimeClasspath {
            ignore("**/__pycache__/**")
            ignore("**/*.pyc")
            ignore("**/*.pyo")
        }
    }
    tasks.withType<Test>().configureEach {
        useTestNG()
        environment("PYTHONDONTWRITEBYTECODE", "1")
    }
    tasks.withType<Exec>().configureEach {
        environment("PYTHONDONTWRITEBYTECODE", "1")
    }
    tasks.withType<JavaExec>().configureEach {
        environment("PYTHONDONTWRITEBYTECODE", "1")
    }
}

allprojects {
    val locAlgitesArtifactDirectory = modustroResolvedArtifactDirectoryForProject(project.path)
    if (locAlgitesArtifactDirectory != null) {
        val locAlgitesTechnologyKinds = AIcModustroStringList(locAlgitesArtifactDirectory["technologyKinds"]).toSet()
        if ("java" in locAlgitesTechnologyKinds) {
            /*
             * TechnologyKind selection owns the standard Java build adapter.
             * Artifact-local Gradle files may add custom behavior, but they are
             * not required to activate ordinary Java compilation/publication.
             */
            logger.lifecycle("Modustro Java TechnologyKind adapter active for '${project.path}': java-library, maven-publish")
            pluginManager.apply("java-library")
            pluginManager.apply("maven-publish")
        }
    }
}

allprojects {
    val locAlgitesArtifactDirectory = modustroResolvedArtifactDirectoryForProject(project.path)
    if (locAlgitesArtifactDirectory != null) {
        plugins.withId("java") {
            AIcConfigureModustroJavaEnvironment(project, locAlgitesArtifactDirectory)
            AIcConfigureModustroJavaDependencies(project, locAlgitesArtifactDirectory)
            val locJavaDependencyPreflight = tasks.register<AIcResolveJavaDependenciesTask>("resolveJavaDependencies") {
                group = "verification"
                description = "Resolves the Java dependency graph for this Modustro-managed artifact before compilation starts."
                dependencyFiles.from(
                    listOf("compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath")
                        .mapNotNull { locName -> configurations.findByName(locName) }
                        .filter { locConfiguration -> locConfiguration.isCanBeResolved }
                        /* Resolve the complete graph and external downloads without consuming local project outputs.
                         * Local outputs belong to compile/package task dependencies; preflight must not pull those
                         * tasks into the resolve phase or declare their unbuilt class directories as classpath inputs.
                         * Generated file dependencies (including Gradle TestKit metadata) are local outputs too.
                         */
                        .map { locConfiguration ->
                            locConfiguration.incoming.artifactView {
                                componentFilter { locComponent ->
                                    locComponent is org.gradle.api.artifacts.component.ModuleComponentIdentifier
                                }
                            }.files.files
                        }
                )
            }
        }
    }
}

val modustroPrepareDevelopment = tasks.register("prepareDevelopment") {
    group = "modustro"
    description = "Generates effective development metadata required by supported technology kinds."
}

val modustroRefreshDevelopment = tasks.register("refreshDevelopment") {
    group = "modustro"
    description = "Forces regeneration of effective development metadata required by supported technology kinds."
}

val modustroBuild = tasks.register("modustroBuild") {
    group = "modustro"
    description = "Builds all effective or explicitly selected Algites TechnologyKinds."
}

val modustroBuiltinBuildOutputProducers = AIcBuiltinBuildOutputProducers()
val modustroBuiltinCapabilityDemandPlanner = AIcBuiltinCapabilityDemandPlanner()

modustroBuild.configure {
    dependsOn(modustroDependencyPreflight)
}

val locAlgitesPythonValidationArtifactPaths = modustroResolvedArtifactDirectoriesByGradleProjectPath.toSortedMap()
    .mapNotNull { (locProjectPath, locMetadata) ->
        if ("python" !in AIcModustroStringList(locMetadata["technologyKinds"])) {
            null
        } else {
            rootProject.findProject(locProjectPath)?.projectDir?.toPath()?.let { locProjectPathValue ->
                rootProject.projectDir.toPath().relativize(locProjectPathValue).toString().replace(File.separatorChar, '/').ifBlank { "." }
            }
        }
    }
val locAlgitesPythonValidationImportNamespaces = locAlgitesPythonValidationArtifactPaths.associateWith { locArtifactPath ->
    val locModulePath = if (locArtifactPath == ".") "" else locArtifactPath.replace('/', '.')
    AIcAlgitesPythonImportNamespace(rootProject.name, locModulePath)
}

val validateModustroPythonDistributionPaths = tasks.register<AIcValidatePythonDistributionPathsTask>("validateModustroPythonDistributionPaths") {
    group = "verification"
    description = "Validates Python product source roots, package-resource paths, and shared PEP 420 namespaces across distributions."
    repositoryDirectory.set(rootProject.layout.projectDirectory)
    pythonArtifactRelativePaths.set(locAlgitesPythonValidationArtifactPaths)
    pythonArtifactImportNamespaces.set(locAlgitesPythonValidationImportNamespaces)
}

modustroBuild.configure {
    dependsOn(validateModustroPythonDistributionPaths)
}

val modustroPublicationBuildGate = tasks.register("modustroPublicationBuildGate") {
    group = "publishing"
    description = "Requires all effective or explicitly selected Algites TechnologyKinds to build successfully before any publication task may start."
    dependsOn(modustroBuild, "validateModustroPublicationProfiles")
}

val modustroPublish = tasks.register("modustroPublish") {
    group = "publishing"
    description = "Publishes all effective or explicitly selected Algites TechnologyKinds after the common publication build gate succeeds."
    dependsOn(modustroPublicationBuildGate)
}

val modustroValidateReleaseTechnologyKinds = tasks.register("validateModustroReleaseTechnologyKinds") {
    group = "modustro"
    description = "Validates that a release TechnologyKind selection is complete unless incomplete release was explicitly allowed."

    doLast {
        val locAllowIncomplete = (
            modustroGradleOrEnvironmentValue("algites.release.allowIncompleteTechnologyKinds")
                ?: System.getenv("ALGITES_RELEASE_ALLOW_INCOMPLETE_TECHNOLOGY_KINDS")
                ?: "false"
            ).toBooleanStrictOrNull()
            ?: throw GradleException("algites.release.allowIncompleteTechnologyKinds must be true or false.")

        val locIncompleteArtifacts = mutableListOf<String>()
        modustroResolvedArtifactDirectoriesByGradleProjectPath.toSortedMap().forEach { (locProjectPath, locMetadata) ->
            val locDeclaredTechnologyKinds = AIcModustroStringList(locMetadata["technologyKinds"]).toSet()
            if (locDeclaredTechnologyKinds.isEmpty()) return@forEach

            val locSelectedTechnologyKinds = if (modustroRequestedTechnologyKinds.isEmpty()) {
                locDeclaredTechnologyKinds
            } else {
                locDeclaredTechnologyKinds.intersect(modustroRequestedTechnologyKinds)
            }
            val locMissingTechnologyKinds = locDeclaredTechnologyKinds - locSelectedTechnologyKinds
            if (locMissingTechnologyKinds.isNotEmpty()) {
                locIncompleteArtifacts += buildString {
                    append(locProjectPath)
                    append(": declared=")
                    append(locDeclaredTechnologyKinds.sorted().joinToString(","))
                    append(" selected=")
                    append(locSelectedTechnologyKinds.sorted().joinToString(",").ifBlank { "<none>" })
                    append(" missing=")
                    append(locMissingTechnologyKinds.sorted().joinToString(","))
                }
            }
        }

        if (locIncompleteArtifacts.isNotEmpty()) {
            val locMessage = buildString {
                appendLine("Incomplete TechnologyKind selection for release.")
                locIncompleteArtifacts.forEach { locEntry -> appendLine(" - $locEntry") }
                append("A release is complete by default. Explicitly allow an incomplete release only when the omitted TechnologyKinds are intentionally excluded from this release version.")
            }
            if (!locAllowIncomplete) {
                throw GradleException(locMessage)
            }
            logger.warn(locMessage)
            logger.warn("Incomplete release explicitly allowed; omitted TechnologyKinds cannot be added later under the same logical release version.")
        } else {
            logger.lifecycle("Release TechnologyKind selection is complete.")
        }
    }
}


abstract class AIcResolveModustroRequiredCredentialsTask : DefaultTask() {
    @get:Input
    abstract val planJson: Property<String>

    @get:Input
    abstract val credentialCount: Property<Int>

    @get:Input
    abstract val technologyKinds: ListProperty<String>

    @get:Optional
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun resolve() {
        val locTechnologyKinds = technologyKinds.get().map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct().sorted()
        if (locTechnologyKinds.isEmpty()) {
            throw GradleException(
                "Cannot resolve Algites repository credentials because no TechnologyKinds were resolved for this repository. " +
                    "Declare Artifact.TechnologyKinds in modustro-artifact.yml or explicitly select a valid TechnologyKind."
            )
        }

        val locJson = planJson.get()
        if (!outputFile.isPresent) {
            println(locJson)
            return
        }

        val locOutputFile = outputFile.get().asFile
        locOutputFile.parentFile?.mkdirs()
        locOutputFile.writeText(locJson + System.lineSeparator(), Charsets.UTF_8)
        println(
            "Algites credential preflight wrote ${credentialCount.get()} required credential profile/type pair(s) " +
                "to ${locOutputFile.path}."
        )
    }
}

/* Publishing invocation overrides must be available before publication tasks resolve effective plans. */
val locModustroSourceRepositoryRootForPublicationOverrides = generateSequence(rootProject.projectDir.canonicalFile) { it.parentFile }
    .firstOrNull { locDirectory -> locDirectory.resolve("modustro-source-repository.yml").isFile }
    ?: throw GradleException(
        "Cannot locate modustro-source-repository.yml on the ancestor path of Gradle build root '${rootProject.projectDir.path}'."
    )
val locModustroPublicationOverridesScript =
    locModustroSourceRepositoryRootForPublicationOverrides.resolve("gradle/tool/repository/modustro-publication-overrides.gradle.kts")
if (!rootProject.extra.has("modustroEffectivePublicationPlan")) {
    if (locModustroPublicationOverridesScript.isFile) {
        apply(from = locModustroPublicationOverridesScript)
    } else {
        apply(from = uri(
            "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/${System.getenv("MODUSTRO_PUBLIC_GOVERNANCE_REVISION") ?: "main"}/gradle/tool/repository/modustro-publication-overrides.gradle.kts"
        ))
    }
}


val locAlgitesRequiredCredentialsPlan = run {
    val locRequestedOutputKinds = (
        modustroGradleOrEnvironmentValue("algites.credential.outputKinds")
            ?: System.getenv("ALGITES_CREDENTIAL_OUTPUT_KINDS")
            ?: "native_product_sources,native_product_binaries,native_product_documentation,native_develop_sources,native_develop_binaries,native_develop_documentation,modustro_docs_site,schema_site"
        ).split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
    val locRequestedUsages = (
        modustroGradleOrEnvironmentValue("algites.credential.usages")
            ?: System.getenv("ALGITES_CREDENTIAL_USAGES")
            ?: "subscription"
        ).split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
    val locSubscriptionStabilities = (
        modustroGradleOrEnvironmentValue("algites.credential.subscription.stabilities")
            ?: System.getenv("ALGITES_CREDENTIAL_SUBSCRIPTION_STABILITIES")
            ?: "release,snapshot"
        ).split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
    val locPublicationStabilities = (
        modustroGradleOrEnvironmentValue("algites.credential.publication.stabilities")
            ?: System.getenv("ALGITES_CREDENTIAL_PUBLICATION_STABILITIES")
            ?: "release,snapshot"
        ).split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()

    val locSupportedOutputKinds = setOf(
        "native_product_binaries", "native_product_sources", "native_product_documentation",
        "native_develop_sources", "native_develop_binaries", "native_develop_documentation",
        "modustro_docs_site", "schema_site"
    )
    val locSupportedUsages = setOf("subscription", "publication")
    val locSupportedStabilities = setOf("release", "snapshot")
    if (!locSupportedOutputKinds.containsAll(locRequestedOutputKinds)) {
        throw GradleException("Unsupported credential output kind. Supported values: ${locSupportedOutputKinds.sorted().joinToString(", ")}.")
    }
    if (!locSupportedUsages.containsAll(locRequestedUsages)) {
        throw GradleException("Unsupported credential usage. Supported values: subscription, publication.")
    }
    if (!locSupportedStabilities.containsAll(locSubscriptionStabilities + locPublicationStabilities)) {
        throw GradleException("Unsupported credential stability. Supported values: release, snapshot.")
    }

    val locAllowedSubscriptionVisibilities = when (modustroSourceRepositoryVisibility) {
        "pub" -> setOf("public")
        "priv" -> setOf("public", "private")
        else -> throw GradleException("Unsupported Algites repository visibility '$modustroSourceRepositoryVisibility'.")
    }
    val locDeclaredOperationTechnologyKinds = modustroResolvedArtifactDirectoriesByGradleProjectPath.values
        .flatMap { locMetadata -> AIcModustroStringList(locMetadata["technologyKinds"]) }
        .toSet()
    val locOperationTechnologyKinds = if (modustroRequestedTechnologyKinds.isNotEmpty()) {
        locDeclaredOperationTechnologyKinds.intersect(modustroRequestedTechnologyKinds)
    } else {
        locDeclaredOperationTechnologyKinds
    }
    val locSubscriptions = linkedMapOf<String, Map<String, Any?>>()
    val locPublicationEndpoints = linkedMapOf<String, Map<String, Any?>>()
    val locCredentials = linkedMapOf<String, Map<String, String>>()

    fun AIcCollect(aMetadata: Map<String, Any?>, aScope: String) {
        val locProfiles = AIcModustroCredentialProfiles(aMetadata["credentialProfiles"])
        fun collectProfile(aId: String?) {
            if (aId.isNullOrBlank()) return
            val locProfile = locProfiles[aId]
                ?: throw GradleException("Effective configuration in '$aScope' references undefined credential profile '$aId'.")
            locCredentials["${locProfile.id}|${locProfile.type}"] = linkedMapOf("profileId" to locProfile.id, "type" to locProfile.type)
        }

        val locScopeTechnologyKinds = AIcModustroStringList(aMetadata["technologyKinds"]).toSet()
        val locTechnologyKinds = if (locScopeTechnologyKinds.isEmpty()) locOperationTechnologyKinds
            else locScopeTechnologyKinds.intersect(locOperationTechnologyKinds.ifEmpty { locScopeTechnologyKinds })

        if ("subscription" in locRequestedUsages) {
            locTechnologyKinds.sorted().forEach { locTechnology ->
                locRequestedOutputKinds.filter { it.startsWith("native_") }.sorted().forEach { locSelector ->
                    AIcModustroInputSubscriptions(
                        aMetadata["inputSubscriptions"], locTechnology, locSelector,
                        locAllowedSubscriptionVisibilities, locSubscriptionStabilities
                    ).forEach { locSubscription ->
                        collectProfile(locSubscription.subscriptionCredentialProfile)
                        locSubscriptions["$aScope|$locTechnology|$locSelector|${locSubscription.id}"] = linkedMapOf(
                            "scope" to aScope,
                            "technologyKind" to locTechnology,
                            "inputSelector" to locSelector,
                            "id" to locSubscription.id,
                            "subscriptionUri" to locSubscription.subscriptionUri,
                            "subscriptionAdapter" to locSubscription.subscriptionAdapter,
                            "stability" to locSubscription.stability
                        )
                    }
                }
            }
        }

        if ("publication" in locRequestedUsages) {
            @Suppress("UNCHECKED_CAST")
            val locResolver = rootProject.extra["modustroEffectivePublicationPlan"] as (Map<String, Any?>, String, String) -> Map<String, Any?>
            locRequestedOutputKinds.sorted().forEach { locOutputKind ->
                val locTechnologies = if (locOutputKind.startsWith("native_")) locTechnologyKinds else setOf("modustro")
                locTechnologies.forEach { locTechnology ->
                    locPublicationStabilities.sorted().forEach { locLane ->
                        val locPlan = locResolver(aMetadata + ("publicationTechnologyKind" to locTechnology), locOutputKind, locLane)
                        if (locPlan["publicationEnabled"] != true) return@forEach
                        val locRegistry = (locPlan["publicationEndpointRegistry"] as? List<*>).orEmpty()
                        locRegistry.forEach { locRaw ->
                            val locEndpoint = locRaw as? Map<*, *> ?: return@forEach
                            if (locEndpoint["executionEnabled"] == false) return@forEach
                            val locId = locEndpoint["id"]?.toString()?.takeIf { it.isNotBlank() } ?: return@forEach
                            collectProfile(locEndpoint["publicationCredentialProfile"]?.toString())
                            locPublicationEndpoints["$aScope|$locTechnology|$locOutputKind|$locLane|$locId"] = linkedMapOf(
                                "scope" to aScope,
                                "technologyKind" to locTechnology,
                                "outputKind" to locOutputKind,
                                "stability" to locLane,
                                "id" to locId,
                                "publicationUri" to locEndpoint["publicationUri"],
                                "publicationAdapter" to locEndpoint["publicationAdapter"]
                            )
                        }
                    }
                }
            }
        }
    }

    AIcCollect(modustroResolvedRepositoryMetadata, "repository")
    modustroResolvedArtifactDirectoriesByGradleProjectPath.toSortedMap().forEach { (locPath, locMetadata) ->
        AIcCollect(locMetadata, "artifact:$locPath")
    }

    val locPlan = linkedMapOf<String, Any>(
        "inputSubscriptions" to locSubscriptions.values.toList(),
        "publicationEndpoints" to locPublicationEndpoints.values.toList(),
        "credentials" to locCredentials.values.toList()
    )
    Triple(JsonOutput.toJson(locPlan), locCredentials.size, locOperationTechnologyKinds.sorted())
}

val modustroResolveRequiredCredentials = tasks.register<AIcResolveModustroRequiredCredentialsTask>("resolveModustroRequiredCredentials") {
    group = "modustro"
    description = "Resolves enabled InputSubscriptions/OutputPublications and credential profiles required by the selected operation context."
    planJson.set(locAlgitesRequiredCredentialsPlan.first)
    credentialCount.set(locAlgitesRequiredCredentialsPlan.second)
    technologyKinds.set(locAlgitesRequiredCredentialsPlan.third)
    val locOutputPath = modustroGradleOrEnvironmentValue("algites.credential.output")
        ?: System.getenv("ALGITES_CREDENTIAL_OUTPUT")
    if (!locOutputPath.isNullOrBlank()) outputFile.set(File(locOutputPath))
}

tasks.register("resolveAlgitesRequiredCredentials") {
    group = "modustro"
    description = "Delegates credential preflight to resolveModustroRequiredCredentials."
    dependsOn(modustroResolveRequiredCredentials)
}

@Suppress("UNCHECKED_CAST")
val modustroPublicationService = rootProject.extra["modustroPublicationService"] as Provider<AIcModustroPublicationService>

val locModustroSchemaSiteScript = rootProject.file("gradle/tool/publication/modustro-schema-site.gradle.kts")
if (locModustroSchemaSiteScript.isFile) {
    apply(from = locModustroSchemaSiteScript)
} else {
    apply(from = uri(
        "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/${System.getenv("MODUSTRO_PUBLIC_GOVERNANCE_REVISION") ?: "main"}/gradle/tool/publication/modustro-schema-site.gradle.kts"
    ))
}

listOf("publishModustroSchemaSite").forEach { locTaskName ->
    tasks.findByName(locTaskName)?.let { locPublishingTask ->
        modustroPublish.configure { dependsOn(locPublishingTask) }
    }
}

val modustroAwaitBackgroundPublications = tasks.register<AIcModustroAwaitPublicationsTask>("modustroAwaitBackgroundPublications") {
    group = "publishing"
    description = "Waits for best-effort publication attempts that may outlive required publishing barriers."
    publicationService.set(modustroPublicationService)
    usesService(modustroPublicationService)
}
modustroPublish.configure { finalizedBy(modustroAwaitBackgroundPublications) }
tasks.findByName("refreshModustroDocsSite")?.let { locRefresh ->
    modustroAwaitBackgroundPublications.configure { finalizedBy(locRefresh) }
    locRefresh.mustRunAfter(modustroAwaitBackgroundPublications)
    tasks.matching { it.name in setOf("generateModustroDocsRootIndex", "generateModustroDocsArtifactPublishingIndexes",
        "generateModustroDocsPublishingGroupIndexes", "generateModustroDocsGeneratedIndex", "generateModustroDocsSite") }
        .configureEach { mustRunAfter(modustroAwaitBackgroundPublications) }
}


/** Reject missing credential profiles before any artifact in the Version Scope is published. */
abstract class AIcValidateModustroPublicationProfilesTask : DefaultTask() {
    @get:Input
    abstract val validationProblems: ListProperty<String>

    @TaskAction
    fun AIcValidate() {
        val locProblems = validationProblems.get()
        if (locProblems.isNotEmpty()) {
            throw GradleException("Modustro publication credential preflight failed before publishing any outputs:\n" +
                locProblems.joinToString("\n") { " - $it" })
        }
        logger.lifecycle("Modustro publication credential preflight: active endpoint profiles are consistent.")
    }
}

val locPublicationCredentialPreflightProblems = mutableListOf<String>()
@Suppress("UNCHECKED_CAST")
val locPublicationCredentialPreflightPlanResolver = rootProject.extra["modustroEffectivePublicationPlan"]
    as (Map<String, Any?>, String, String) -> Map<String, Any?>
val locPublicationCredentialPreflightRepositoryProfiles =
    (modustroResolvedRepositoryMetadata["credentialProfiles"] as? Map<String, Any?>).orEmpty()
val locPublicationCredentialPreflightKinds = listOf("java", "python", "mps")
val locPublicationCredentialPreflightSelectors = listOf(
    "native_product_binaries", "native_product_sources", "native_product_documentation",
    "native_develop_binaries", "native_develop_sources", "native_develop_documentation")

modustroResolvedArtifactDirectoriesByGradleProjectPath.toSortedMap().forEach { (locProjectPath, locMetadata) ->
    val locArtifactProfiles = (locMetadata["credentialProfiles"] as? Map<String, Any?>).orEmpty()
    val locEffectiveProfiles = locPublicationCredentialPreflightRepositoryProfiles + locArtifactProfiles
    val locDeclaredKinds = AIcModustroStringList(locMetadata["technologyKinds"]).toSet()
    val locEffectiveKinds = if (modustroRequestedTechnologyKinds.isEmpty()) locDeclaredKinds
        else locDeclaredKinds.intersect(modustroRequestedTechnologyKinds)
    val locArtifactVersion = modustroResolvedVersionValue(locMetadata) ?: rootProject.version.toString()
    val locArtifactStability = if (locArtifactVersion.endsWith("SNAPSHOT", ignoreCase = true)) "snapshot" else "release"
    locEffectiveKinds.filter { it in locPublicationCredentialPreflightKinds }.forEach { locKind ->
        locPublicationCredentialPreflightSelectors.forEach { locSelector ->
            for (locStability in listOf(locArtifactStability)) {
                val locPlan = locPublicationCredentialPreflightPlanResolver(
                    locMetadata + ("publicationTechnologyKind" to locKind), locSelector, locStability)
                if (locPlan["publicationEnabled"] != true) continue
                val locEndpoints = (locPlan["publicationEndpoints"] as? List<*>).orEmpty()
                locEndpoints.forEach { locRaw ->
                    val locEndpoint = locRaw as? Map<*, *> ?: return@forEach
                    if (locEndpoint["executionEnabled"] == false) return@forEach
                    val locId = locEndpoint["publicationCredentialProfile"]?.toString()
                        ?.takeIf { it.isNotBlank() && it != "null" } ?: return@forEach
                    val locProfile = locEffectiveProfiles[locId] as? Map<*, *>
                    if (locProfile == null || locProfile["type"]?.toString().isNullOrBlank()) {
                        val locEndpointId = locEndpoint["id"]?.toString() ?: "<unnamed>"
                        locPublicationCredentialPreflightProblems.add(
                            "$locProjectPath [$locKind/$locSelector/$locStability] endpoint '$locEndpointId' " +
                                "references undefined/incomplete credential profile '$locId' " +
                                "(available profiles: ${locEffectiveProfiles.keys.sorted().joinToString(", ")}).")
                    }
                }
            }
        }
    }
}

val modustroValidatePublicationProfiles = tasks.register<AIcValidateModustroPublicationProfilesTask>(
    "validateModustroPublicationProfiles") {
    group = "verification"
    description = "Validates credentials for active artifact publication endpoints before any output is published."
    validationProblems.set(locPublicationCredentialPreflightProblems.distinct().sorted())
}


subprojects {
    val locAlgitesRunDirectoryRelativePath = AIcModustroRunDirectoryRelativePath(project.projectDir)
    val locAlgitesArtifactDirectory = modustroResolvedArtifactDirectoryForProject(project.path)
    val locAlgitesResolvedProjectGroup = locAlgitesArtifactDirectory?.get("groupId")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        ?: modustroResolvedRepositoryMetadata["groupId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
    val locAlgitesTechnologyKinds = AIcModustroStringList(locAlgitesArtifactDirectory?.get("technologyKinds"))
    val locEffectiveTechnologyKinds = if (modustroRequestedTechnologyKinds.isEmpty()) {
        locAlgitesTechnologyKinds.toSet()
    } else {
        locAlgitesTechnologyKinds.filter { it in modustroRequestedTechnologyKinds }.toSet()
    }

    fun locBuildOutputPlans(aTechnologyKind: String) = if (aTechnologyKind !in locAlgitesTechnologyKinds) {
        emptyList()
    } else {
        val locPreparedSourceSet = AIcModustroPreparedSourceSet(project, aTechnologyKind)
        val locExplicitBuildOutputTypes = AIcModustroExplicitBuildOutputTypes(locAlgitesArtifactDirectory, aTechnologyKind)
        if (locExplicitBuildOutputTypes == null) {
            modustroBuiltinBuildOutputProducers.createDefaultProductionPlans(aTechnologyKind, locPreparedSourceSet)
        } else {
            modustroBuiltinBuildOutputProducers.createProductionPlans(aTechnologyKind, locExplicitBuildOutputTypes, locPreparedSourceSet)
        }
    }

    val locJavaBuildOutputPlans = if ("java" in locAlgitesTechnologyKinds) locBuildOutputPlans("java") else emptyList()
    val locJavaProductionKinds = locJavaBuildOutputPlans.map { locPlan -> locPlan.productionKind() }.toSet()
    val locPythonBuildOutputPlans = if ("python" in locAlgitesTechnologyKinds) locBuildOutputPlans("python") else emptyList()
    val locPythonProductionKinds = locPythonBuildOutputPlans.map { locPlan -> locPlan.productionKind() }.toSet()
    val locBuildOutputPlans = locJavaBuildOutputPlans + locPythonBuildOutputPlans
    val locCapabilityDemandGraph = modustroBuiltinCapabilityDemandPlanner.createDemandGraph(
        locAlgitesArtifactDirectory?.get("path")?.toString()?.takeIf { it.isNotBlank() } ?: project.path,
        locBuildOutputPlans,
        emptyList()
    )

    fun locHasCapabilityDemand(aTechnologyKind: String, aCapabilityId: String): Boolean =
        locCapabilityDemandGraph.demands().any { locDemand ->
            locDemand.key().technologyKind() == aTechnologyKind && locDemand.key().capabilityId() == aCapabilityId
        }

    extra["modustroBuildCapabilityDemandIds"] = locCapabilityDemandGraph.topologicalOrder()
        .map { locDemand -> locDemand.key().toString() }

    if (locCapabilityDemandGraph.demands().isNotEmpty()) {
        logger.lifecycle(
            "Modustro capability demands for '${project.path}': " +
                locCapabilityDemandGraph.topologicalOrder().joinToString(", ") { locDemand ->
                    "${locDemand.key().technologyKind()}:${locDemand.key().capabilityId()}"
                }
        )
    }

    val locJavaSourceProcessingTask = if ("java" in locAlgitesTechnologyKinds) {
        tasks.register("processModustroJavaNativeSources") {
            group = "modustro"
            description = "Materializes the Java source_native_processing capability boundary for this artifact."
            val locPrepared = AIcModustroPreparedSourceSet(project, "java")
            inputs.files((locPrepared.nativeSourceRoots() + locPrepared.generatedSourceRoots() + locPrepared.resourceRoots()).map(project::file))
        }
    } else {
        null
    }
    val locPythonSourceProcessingTask = if ("python" in locAlgitesTechnologyKinds) {
        tasks.register("processModustroPythonNativeSources") {
            group = "modustro"
            description = "Materializes the Python source_native_processing capability boundary for this artifact."
            val locPrepared = AIcModustroPreparedSourceSet(project, "python")
            inputs.files((locPrepared.nativeSourceRoots() + locPrepared.generatedSourceRoots() + locPrepared.resourceRoots()).map(project::file))
        }
    } else {
        null
    }

    val locCanonicalDefinitionSourceKinds = listOf("yamldefs", "jsondefs", "xmldefs")
    val locCanonicalDefinitionSourceRoots = locCanonicalDefinitionSourceKinds
        .map { locSourceKind -> project.file("src/product/$locSourceKind") }
        .filter(File::isDirectory)
    val locCanonicalDefinitionSourceFiles = locCanonicalDefinitionSourceRoots.flatMap { locRoot ->
        val locSourceKind = locRoot.name
        val locDefinitionSuffix = when (locSourceKind) {
            "yamldefs" -> ".yamldef.schema.json"
            "jsondefs" -> ".jsondef.schema.json"
            "xmldefs" -> ".xsd"
            else -> throw GradleException("Unsupported canonical definition source kind '$locSourceKind'.")
        }
        buildList {
            locRoot.walkTopDown()
                .filter(File::isFile)
                .filterNot { locFile -> locFile.name.startsWith('.') }
                .forEach { locFile ->
                    when {
                        locFile.name.endsWith(".meta.yml") || locFile.name.endsWith(".meta.yaml") -> Unit
                        locFile.name.endsWith(locDefinitionSuffix) -> add(locFile)
                        else -> throw GradleException(
                            "Unsupported file '${locFile.relativeTo(locRoot).invariantSeparatorsPath}' in canonical " +
                                "$locSourceKind root '$locRoot'. Expected '*$locDefinitionSuffix' definitions or '.meta.yml/.meta.yaml' sidecars."
                        )
                    }
                }
        }
    }
    val locCanonicalDefinitionGenerationTargets = locAlgitesTechnologyKinds
        .filter { locTechnologyKind -> locTechnologyKind in setOf("java", "python") }
        .toSet()
    val locCanonicalDefinitionGenerationManifestDirectory =
        rootProject.layout.projectDirectory.dir("$locAlgitesRunDirectoryRelativePath/defscodegen")
    val locCanonicalDefinitionGenerationStaleTargets = listOf("java", "python")
        .filter { locTarget -> locCanonicalDefinitionGenerationManifestDirectory.file("$locTarget.manifest").asFile.isFile }
        .toSet()
    val locCanonicalDefinitionGenerationExecutionTargets =
        (if (locCanonicalDefinitionSourceFiles.isEmpty()) emptySet() else locCanonicalDefinitionGenerationTargets) +
            locCanonicalDefinitionGenerationStaleTargets
    val locCanonicalDefinitionGenerationTask = if (
        locCanonicalDefinitionSourceFiles.isNotEmpty() || locCanonicalDefinitionGenerationStaleTargets.isNotEmpty()
    ) {
        tasks.register<AIcGenerateModustroDefinitionSourcesTask>("generateModustroDefinitionSources") {
            group = "modustro"
            description = "Discovers canonical product definitions and generates native source types for the artifact TechnologyKinds."
            artifactDirectory.set(project.layout.projectDirectory)
            /* Referenced canonical schemas may be owned by another artifact in the same source repository. */
            sourceFiles.from(rootProject.fileTree(locModustroSourceRepositoryRootForPublicationOverrides) {
                include("**/src/product/yamldefs/**/*.yamldef.schema.json", "**/src/product/jsondefs/**/*.jsondef.schema.json")
                exclude("**/.gradle/**", "**/.git/**", "**/.run/**", "**/build/**")
            })
            manifestDirectory.set(locCanonicalDefinitionGenerationManifestDirectory)
            javaOutputDirectory.set(project.layout.projectDirectory.dir("src/product/java.gen"))
            pythonOutputDirectory.set(project.layout.projectDirectory.dir("src/product/python.gen"))
            targetTechnologyKinds.set(locCanonicalDefinitionGenerationTargets.sorted())
            locAlgitesResolvedProjectGroup?.let(rootDefinitionPackage::set)
            sourceFiles.from(locCanonicalDefinitionSourceFiles)
        }
    } else {
        null
    }
    if ("java" in locCanonicalDefinitionGenerationExecutionTargets) {
        locJavaSourceProcessingTask?.configure {
            locCanonicalDefinitionGenerationTask?.let { locTask -> dependsOn(locTask) }
        }
    }
    if ("python" in locCanonicalDefinitionGenerationExecutionTargets) {
        locPythonSourceProcessingTask?.configure {
            locCanonicalDefinitionGenerationTask?.let { locTask -> dependsOn(locTask) }
        }
    }
    if (locCanonicalDefinitionGenerationStaleTargets.isNotEmpty() && locCanonicalDefinitionGenerationTargets.isEmpty()) {
        modustroBuild.configure {
            locCanonicalDefinitionGenerationTask?.let { locTask -> dependsOn(locTask) }
        }
    }

    if ("java" in locEffectiveTechnologyKinds && locHasCapabilityDemand("java", "dependency_resolution")) {
        val locDependencyResolutionTask = tasks.named("resolveJavaDependencies")
        rootProject.tasks.named("modustroDependencyPreflight").configure {
            dependsOn(locDependencyResolutionTask)
        }
    }

    if (locJavaBuildOutputPlans.isNotEmpty()) {
        logger.lifecycle("Modustro Java BuildOutputTypes for '${project.path}': ${locJavaBuildOutputPlans.joinToString(", ") { locPlan -> locPlan.buildOutputType() }}")
    }
    if (locPythonBuildOutputPlans.isNotEmpty()) {
        logger.lifecycle("Modustro Python BuildOutputTypes for '${project.path}': ${locPythonBuildOutputPlans.joinToString(", ") { locPlan -> locPlan.buildOutputType() }}")
    }

    val locAlgitesSubprojectPathDots = project.path
        .removePrefix(":")
        .replace(':', '.')

    val locAlgitesCanonicalArtifactId = if (locAlgitesSubprojectPathDots.isBlank()) {
        rootProject.name
    } else {
        "${rootProject.name}_${locAlgitesSubprojectPathDots}"
    }
    val locAlgitesVariantId = locAlgitesArtifactDirectory?.get("variantId")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
    val locAlgitesEffectiveArtifactId = AIcAlgitesEffectiveArtifactId(locAlgitesCanonicalArtifactId, locAlgitesVariantId)
    val locAlgitesProjectVersion = project.version.toString()
    val locModustroPublicationStability = if (locAlgitesProjectVersion.endsWith("SNAPSHOT", ignoreCase = true)) "snapshot" else "release"

    @Suppress("UNCHECKED_CAST")
    fun locModustroPublicationEnabled(aOutputKind: String, aTechnologyKind: String): Boolean {
        val locResolver = rootProject.extra["modustroEffectivePublicationPlan"]
            as (Map<String, Any?>, String, String) -> Map<String, Any?>
        val locMetadata = (locAlgitesArtifactDirectory ?: emptyMap()) + ("publicationTechnologyKind" to aTechnologyKind)
        val locPlan = locResolver(locMetadata, aOutputKind, locModustroPublicationStability)
        return locPlan["publicationEnabled"] as? Boolean ?: false
    }

    val locAnyNativePublicationEnabled = locEffectiveTechnologyKinds.any { locTechnologyKind ->
        listOf(
            "native_product_binaries", "native_product_sources", "native_product_documentation",
            "native_develop_sources", "native_develop_binaries", "native_develop_documentation"
        ).any { locOutputKind -> locModustroPublicationEnabled(locOutputKind, locTechnologyKind) }
    }

    val locEffectiveInputSubscriptions = locAlgitesArtifactDirectory?.get("inputSubscriptions")
    val locEffectiveCredentialProfiles = AIcModustroCredentialProfiles(locAlgitesArtifactDirectory?.get("credentialProfiles"))

    @Suppress("UNCHECKED_CAST")
    fun locModustroPublicationPlanJson(aOutputKind: String, aTechnologyKind: String): String {
        val locResolver = rootProject.extra["modustroEffectivePublicationPlan"]
            as (Map<String, Any?>, String, String) -> Map<String, Any?>
        return JsonOutput.toJson(locResolver((locAlgitesArtifactDirectory ?: emptyMap()) + ("publicationTechnologyKind" to aTechnologyKind), aOutputKind, locModustroPublicationStability))
    }
    /* Use the scope's inherited profiles, retaining repository defaults as a safety net
     * for bootstrap metadata that provides only the artifact-local overrides. */
    val locPublicationCredentialProfilesJson = JsonOutput.toJson(
        locPublicationCredentialPreflightRepositoryProfiles +
            ((locAlgitesArtifactDirectory?.get("credentialProfiles") as? Map<String, Any?>).orEmpty()))

    val locAlgitesArtifactDirectoryPath = locAlgitesArtifactDirectory?.get("path")?.toString()?.takeIf { it.isNotBlank() } ?: "."
    val locAlgitesProductLicenses = locAlgitesLicensesForPathAndContentKind(locAlgitesArtifactDirectoryPath, "product")

    val locAlgitesLocalArtifactId = locAlgitesArtifactDirectoryPath
        .trim()
        .trim('/')
        .replace('/', '.')
        .takeIf { it.isNotBlank() && it != "." }
        ?: "."
    @Suppress("UNCHECKED_CAST")
    val locAlgitesDescriptorHierarchy = (locAlgitesArtifactDirectory?.get("descriptorHierarchy") as? List<Map<String, Any?>>)
        .orEmpty()
        .map { locDescriptor ->
            listOf(
                locDescriptor["structureKind"]?.toString().orEmpty(),
                locDescriptor["path"]?.toString().orEmpty(),
                locDescriptor["sha256"]?.toString().orEmpty()
            ).joinToString("\t")
        }
    val locModustroManifestOutputFile = layout.buildDirectory.file("modustro/manifest/modustro-artifact-manifest.yml")
    val locGenerateModustroArtifactManifest = tasks.register<AIcGenerateModustroArtifactManifestTask>("generateModustroArtifactManifest") {
        group = "modustro"
        description = "Generates the deterministic Modustro artifact manifest for this logical artifact."

        repositoryId.set(modustroResolvedRepositoryMetadata["id"]?.toString()?.takeIf { it.isNotBlank() } ?: rootProject.name)
        localArtifactId.set(locAlgitesLocalArtifactId)
        artifactCoordinateId.set(locAlgitesEffectiveArtifactId)
        locAlgitesVariantId?.let(variantId::set)
        locAlgitesArtifactDirectory?.get("groupId")?.toString()?.takeIf { it.isNotBlank() && it != "null" }?.let { locGroupId -> groupId.set(locGroupId) }
        artifactVersion.set(locAlgitesProjectVersion)
        sourcePath.set(locAlgitesArtifactDirectoryPath)
        structureKind.set(locAlgitesArtifactDirectory?.get("structureKind")?.toString() ?: "artifact")
        artifactName.set(locAlgitesArtifactDirectory?.get("name")?.toString() ?: locAlgitesEffectiveArtifactId)
        artifactDescription.set(locAlgitesArtifactDirectory?.get("description")?.toString() ?: "")
        descriptorHierarchy.set(locAlgitesDescriptorHierarchy)
        outputFile.set(locModustroManifestOutputFile)
    }

    plugins.withId("base") {
        extensions.configure<BasePluginExtension>("base") {
            archivesName.set(locAlgitesEffectiveArtifactId)
        }
    }

    if ("java" in locAlgitesTechnologyKinds) {
        plugins.withId("java") {
            val locJavaExtension = extensions.getByType(JavaPluginExtension::class.java)
            val locMainSourceSet = locJavaExtension.sourceSets.getByName("main")
            val locTestSourceSet = locJavaExtension.sourceSets.getByName("test")
            val locJavaProductRoots = locAlgitesResolveSourceRootRelativePaths(project.projectDir, "product", "java")
            val locJavaDevelopRoots = locAlgitesResolveSourceRootRelativePaths(project.projectDir, "develop", "java")
            val locProductResourceRoots = listOf("resources", "yamldefs", "jsondefs", "xmldefs", "config")
                .flatMap { locSourceType -> locAlgitesResolveSourceRootRelativePaths(project.projectDir, "product", locSourceType) }
            val locDevelopResourceRoots = listOf("resources", "yamldefs", "jsondefs", "xmldefs", "config")
                .flatMap { locSourceType -> locAlgitesResolveSourceRootRelativePaths(project.projectDir, "develop", locSourceType) }

            locMainSourceSet.java.setSrcDirs(locJavaProductRoots)
            locMainSourceSet.resources.setSrcDirs(locProductResourceRoots)
            locTestSourceSet.java.setSrcDirs(locJavaDevelopRoots)
            locTestSourceSet.resources.setSrcDirs(locDevelopResourceRoots)
            if (locHasCapabilityDemand("java", "source_native_processing")) {
                tasks.matching { locTask ->
                    locTask.name in setOf("compileJava", "processResources", "sourcesJar", "javadoc", "javadocJar")
                }.configureEach {
                    locJavaSourceProcessingTask?.let { locSourceProcessingTask -> dependsOn(locSourceProcessingTask) }
                }
            }
            if (AInBuildOutputProductionKind.JAVA_SOURCES_JAR in locJavaProductionKinds) {
                locJavaExtension.withSourcesJar()
            }
            if (AInBuildOutputProductionKind.JAVA_JAVADOC_JAR in locJavaProductionKinds) {
                locJavaExtension.withJavadocJar()
            }
            if ("java" in locEffectiveTechnologyKinds) {
                modustroBuild.configure { dependsOn(tasks.named("check")) }
                if (AInBuildOutputProductionKind.JAVA_CLASSES_JAR in locJavaProductionKinds) {
                    modustroBuild.configure { dependsOn(tasks.named("jar")) }
                }
                if (AInBuildOutputProductionKind.JAVA_SOURCES_JAR in locJavaProductionKinds) {
                    modustroBuild.configure { dependsOn(tasks.named("sourcesJar")) }
                }
                if (AInBuildOutputProductionKind.JAVA_JAVADOC_JAR in locJavaProductionKinds) {
                    modustroBuild.configure { dependsOn(tasks.named("javadocJar")) }
                }
            }

            tasks.withType(Jar::class.java).configureEach {
                dependsOn(rootProject.tasks.named("verifyAlgitesLicensing"))
                dependsOn(locGenerateModustroArtifactManifest)
                from(locGenerateModustroArtifactManifest.flatMap { it.outputFile }) {
                    into("META-INF/modustro")
                }
                locAlgitesProductLicenses.forEach { locLicense ->
                    val locLicenseId = locLicense["id"] ?: return@forEach
                    val locLicenseFile = locLicense["file"] ?: return@forEach
                    from(rootProject.file(locLicenseFile)) {
                        into("META-INF/LICENSES")
                        rename { "$locLicenseId.txt" }
                    }
                }
            }
        }

        plugins.withId("maven-publish") {
            if (modustroIsPublishRequested) {
                requireModustroGroupForPublishing(project.path, locAlgitesResolvedProjectGroup)
            }

            plugins.withId("java") {
                extensions.configure<PublishingExtension>("publishing") {
                    publications {
                        if (findByName("mavenJava") == null && components.findByName("java") != null) {
                            create<MavenPublication>("mavenJava") {
                                from(components["java"])
                            }
                        }
                    }
                }
            }

            extensions.configure<PublishingExtension>("publishing") {
                publications.withType(MavenPublication::class.java).configureEach {
                    locAlgitesResolvedProjectGroup?.let { locGroupId -> groupId = locGroupId }
                    artifactId = locAlgitesEffectiveArtifactId
                    pom {
                        licenses {
                            locAlgitesProductLicenses.forEach { locLicense ->
                                license {
                                    name.set(locLicense["name"] ?: locLicense["id"] ?: "Unknown license")
                                    locLicense["url"]?.takeIf { it.isNotBlank() }?.let { locUrl -> url.set(locUrl) }
                                    distribution.set("repo")
                                }
                            }
                        }
                    }
                }


            }
        }

        if ("java" in locEffectiveTechnologyKinds && locAnyNativePublicationEnabled) {
            plugins.withId("maven-publish") {
                listOf(
                    Triple(AInBuildOutputProductionKind.JAVA_CLASSES_JAR, "publishModustroJavaNativeBinary", "jar"),
                    Triple(AInBuildOutputProductionKind.JAVA_SOURCES_JAR, "publishModustroJavaNativeSources", "sourcesJar"),
                    Triple(AInBuildOutputProductionKind.JAVA_JAVADOC_JAR, "publishModustroJavaNativeDocumentation", "javadocJar")
                ).filter { it.first in locJavaProductionKinds }.forEach { (_, locTaskName, locArchiveTaskName) ->
                    val locOutputKind = when (locArchiveTaskName) {
                        "jar" -> "native_product_binaries"
                        "sourcesJar" -> "native_product_sources"
                        else -> "native_product_documentation"
                    }
                    val locArchive = tasks.named<Jar>(locArchiveTaskName).flatMap { it.archiveFile }
                    val locPom = layout.buildDirectory.file("publications/mavenJava/pom-default.xml")
                    val locModule = layout.buildDirectory.file("publications/mavenJava/module.json")
                    val locPlanJson = locModustroPublicationPlanJson(locOutputKind, "java")
                    val locPublish = tasks.register<AIcModustroPublishFilesTask>(locTaskName) {
                        group = "publishing"
                        description = "Publishes the Java native output through the Modustro publication scheduler."
                        dependsOn(modustroPublicationBuildGate)
                        payloadFiles.from(locArchive)
                        if (locArchiveTaskName == "jar") {
                            dependsOn("generatePomFileForMavenJavaPublication", "generateMetadataFileForMavenJavaPublication")
                            payloadFiles.from(locPom)
                            payloadFiles.from(locModule)
                            publishedFileNames.put("pom-default.xml", "$locAlgitesEffectiveArtifactId-$locAlgitesProjectVersion.pom")
                            publishedFileNames.put("module.json", "$locAlgitesEffectiveArtifactId-$locAlgitesProjectVersion.module")
                        }
                        publicationPlanJson.set(locPlanJson)
                        credentialProfilesJson.set(locPublicationCredentialProfilesJson)
                        outputKind.set(locOutputKind.uppercase())
                        stability.set(locModustroPublicationStability)
                        artifactIdentity.set(listOfNotNull(locAlgitesResolvedProjectGroup, locAlgitesEffectiveArtifactId).joinToString(":"))
                        publicationVersion.set(locAlgitesProjectVersion)
                        coordinates.set(mapOf("groupId" to (locAlgitesResolvedProjectGroup ?: ""), "artifactId" to locAlgitesEffectiveArtifactId, "version" to locAlgitesProjectVersion, "technologyKind" to "java", "classifier" to (if (locArchiveTaskName == "sourcesJar") "sources" else if (locArchiveTaskName == "javadocJar") "javadoc" else ""), "extension" to "jar"))
                        publicationService.set(modustroPublicationService)
                        usesService(modustroPublicationService)
                    }
                    modustroPublish.configure { dependsOn(locPublish) }
                }
            }
        }
    }

    if ("java" in locEffectiveTechnologyKinds) {
        plugins.withId("java") {
            val locJava = extensions.getByType<JavaPluginExtension>()
            val locDevelop = locJava.sourceSets.getByName("test")
            listOf(
                Triple("native_develop_binaries", "tests", "publishModustroJavaDevelopBinaries"),
                Triple("native_develop_sources", "test-sources", "publishModustroJavaDevelopSources"),
                Triple("native_develop_documentation", "test-javadoc", "publishModustroJavaDevelopDocumentation")
            ).forEach { (kind, classifier, publishName) ->
                val plan = locModustroPublicationPlanJson(kind, "java")
                if ((JsonSlurper().parseText(plan) as Map<*, *>)["publicationEnabled"] == true) {
                    val docs = if (kind == "native_develop_documentation") tasks.register<org.gradle.api.tasks.javadoc.Javadoc>("modustroDevelopJavadoc") {
                        source(locDevelop.allJava)
                        classpath = locDevelop.compileClasspath
                        destinationDir = layout.buildDirectory.dir("docs/develop-javadoc").get().asFile
                    } else null
                    val archive = tasks.register<Jar>("modustroDevelop" + classifier.replace("-", "_") + "Jar") {
                        archiveClassifier.set(classifier)
                        when(kind) {
                            "native_develop_binaries" -> { dependsOn("testClasses"); from(locDevelop.output) }
                            "native_develop_sources" -> from(locDevelop.allSource)
                            else -> { dependsOn(docs!!); from(docs.map { it.destinationDir }) }
                        }
                    }
                    val publish = tasks.register<AIcModustroPublishFilesTask>(publishName) {
                        group = "publishing"
                        dependsOn(modustroPublicationBuildGate, archive)
                        publicationPlanJson.set(plan)
                        credentialProfilesJson.set(locPublicationCredentialProfilesJson)
                        outputKind.set(kind.uppercase()); stability.set(locModustroPublicationStability)
                        artifactIdentity.set("${locAlgitesResolvedProjectGroup}:$locAlgitesEffectiveArtifactId")
                        publicationVersion.set(locAlgitesProjectVersion)
                        coordinates.set(mapOf("groupId" to (locAlgitesResolvedProjectGroup ?: ""), "artifactId" to locAlgitesEffectiveArtifactId, "version" to locAlgitesProjectVersion, "technologyKind" to "java", "classifier" to classifier, "extension" to "jar"))
                        payloadFiles.from(archive.flatMap { it.archiveFile })
                        publicationService.set(modustroPublicationService); usesService(modustroPublicationService)
                    }
                    modustroPublish.configure { dependsOn(publish) }
                }
            }
        }
    }

    if ("python" in locAlgitesTechnologyKinds) {
        val locPythonTemplateFile = layout.projectDirectory.file("pyproject.toml.tpl")
        val locPythonProjectFile = layout.projectDirectory.file("pyproject.toml")
        val locPythonDistributionName = AIcAlgitesPythonDistributionName(locAlgitesResolvedProjectGroup, locAlgitesEffectiveArtifactId)
        val locPythonImportNamespace = AIcAlgitesPythonImportNamespace(rootProject.name, locAlgitesSubprojectPathDots)

        val locAlgitesProjectRunDirectory = rootProject.layout.projectDirectory.dir(
            AIcModustroRunDirectoryRelativePath(project.projectDir)
        )
        val locPythonBuildProjectDirectory = locAlgitesProjectRunDirectory.dir("bld/python/project")
        val locPythonPreparedPackageResourcesDirectory = locAlgitesProjectRunDirectory.dir(
            "bld/python/source-native-processing/package-resources"
        )
        val locPythonPreparedSourceSet = AIcModustroPreparedSourceSet(project, "python")
        val locPythonImportNamespacePath = locPythonImportNamespace.replace('.', '/')
        val locStagePythonPackageResources = tasks.register<Sync>("stageModustroPythonPackageResources") {
            group = "modustro"
            description = "Stages prepared Python package resources below the derived artifact import namespace."
            duplicatesStrategy = DuplicatesStrategy.FAIL
            into(locPythonPreparedPackageResourcesDirectory)
            locPythonPreparedSourceSet.resourceRoots().forEach { locSourcePath ->
                from(project.layout.projectDirectory.dir(locSourcePath)) {
                    into(locPythonImportNamespacePath)
                }
            }
        }
        locPythonSourceProcessingTask?.configure {
            dependsOn(locStagePythonPackageResources)
        }

        val locDeletePythonDevelopmentMetadata = tasks.register("deletePythonDevelopmentMetadata") {
            group = "modustro"
            description = "Deletes generated Python development metadata for this artifact."
            doLast {
                locPythonProjectFile.asFile.delete()
            }
        }

        val locPythonProjectVersion = AIcAlgitesPythonVersion(locAlgitesProjectVersion, algitesSnapshotInstanceId)
        val locPythonArtifactDescription = locAlgitesArtifactDirectory?.get("description")?.toString() ?: ""
        val locPythonLicenseIds = locAlgitesProductLicenses.mapNotNull { locLicense ->
            locLicense["id"]?.toString()?.takeIf { it.isNotBlank() }
        }

        fun locPythonDefinitions(aPropertyName: String): List<Map<String, Any?>> =
            AIcModustroDependencyDefinitions(locAlgitesArtifactDirectory, aPropertyName)
                .filter { locDefinition -> locDefinition["dependencyKind"]?.toString() in setOf("modustro", "python") }

        val locPythonDependencyDefinitions = locPythonDefinitions("dependencies")
        val locPythonConstraintDefinitions = locPythonDefinitions("dependencyConstraints")
        locPythonDependencyDefinitions.forEachIndexed { locIndex, locDefinition ->
            val locContext = "Project '${project.path}' Dependencies[$locIndex]"
            AIcValidatePhase2DependencyOutputs(locDefinition, "python", locContext)
            AIcModustroPythonEffectiveUsages(locDefinition, locContext)
        }
        locPythonConstraintDefinitions.forEachIndexed { locIndex, locDefinition ->
            AIcModustroPythonEffectiveUsages(locDefinition, "Project '${project.path}' DependencyConstraints[$locIndex]")
        }
        /*
         * A Modustro dependency on another artifact of this source repository is
         * a local build-graph edge, not a requirement to download an already
         * published snapshot. External Python/Modustro dependencies must still
         * be resolved through the configured indexes. Keep all dependencies in
         * the generated wheel/sdist metadata regardless of the preflight route.
         */
        fun locPythonExternalPreflightEntries(
            aDefinitions: List<Map<String, Any?>>,
            aPropertyName: String
        ): List<String> = aDefinitions
            .filter { locDefinition -> AIcModustroDependencyUsages(locDefinition).any(::AIcModustroPythonParticipatesInResolution) }
            .mapIndexedNotNull { locIndex, locDefinition ->
                val locContext = "Project '${project.path}' $aPropertyName[$locIndex]"
                val locLocalTarget = AIcAlgitesLocalDependencyTarget(locDefinition, project.path)
                if (locLocalTarget != null) {
                    val (locTargetProject, locTargetMetadata) = locLocalTarget
                    /* The package-name resolver checks that the local target actually supports Python. */
                    val locPackageName = AIcAlgitesPythonDependencyPackageName(locDefinition, project.path)
                    AIcValidateLocalAlgitesPythonDependencyVersion(
                        locDefinition, locTargetMetadata, locTargetProject.path, locContext
                    )
                    logger.lifecycle(
                        "Modustro Python preflight: LOCAL ${project.path} -> ${locTargetProject.path} " +
                            "($locPackageName); remote package-index lookup skipped."
                    )
                    null
                } else {
                    /*
                     * Detect a same-repository artifact whose declared GroupId does not match
                     * its inherited effective GroupId, instead of accidentally treating it
                     * as a remote Python package. Other repositories still use normal pip
                     * resolution, including artifacts not present in this build domain.
                     */
                    if (locDefinition["dependencyKind"]?.toString() == "modustro") {
                        val locArtifactId = locDefinition["artifactId"]?.toString().orEmpty()
                        val locVariantId = locDefinition["variantId"]?.toString()?.takeUnless {
                            it.isBlank() || it == "null"
                        }
                        val locCandidates = modustroResolvedArtifactDirectoriesByGradleProjectPath
                            .filterKeys { it != ":" }
                            .filter { (locPath, locMetadata) ->
                                AIcAlgitesCanonicalArtifactId(locPath) == locArtifactId &&
                                    locMetadata["variantId"]?.toString()?.takeUnless {
                                        it.isBlank() || it == "null"
                                    } == locVariantId
                            }
                        if (locCandidates.isNotEmpty()) {
                            val locDeclaredGroup = locDefinition["groupId"]?.toString()
                            val locActualGroups = locCandidates.map { (locPath, locMetadata) ->
                                "$locPath=${locMetadata["groupId"]}"
                            }.sorted().joinToString(", ")
                            throw GradleException(
                                "$locContext references an artifact present in this build, but the local " +
                                    "Modustro dependency matcher did not accept it. " +
                                    "Declared GroupId='$locDeclaredGroup', local targets: $locActualGroups. " +
                                    "Check inherited GroupId and VariantId instead of publishing a " +
                                    "local artifact merely to satisfy Python preflight."
                            )
                        }
                    }
                    val locRemoteEntry = AIcAlgitesPythonResolutionEntry(locDefinition, project.path, locContext)
                    logger.info("Modustro Python preflight: REMOTE ${project.path}: $locRemoteEntry")
                    locRemoteEntry
                }
            }
        val locPythonResolutionDependencies = locPythonExternalPreflightEntries(
            locPythonDependencyDefinitions, "Dependencies"
        )
        val locPythonResolutionConstraints = locPythonExternalPreflightEntries(
            locPythonConstraintDefinitions, "DependencyConstraints"
        )
        val locPythonPublishedDependencies = locPythonDependencyDefinitions
            .filter { locDefinition ->
                AIcModustroDependencyUsages(locDefinition).any { locUsage ->
                    locUsage in setOf("product_api", "product_implementation", "product_runtime_only")
                }
            }
            .mapIndexed { locIndex, locDefinition ->
                val locPackageName = AIcAlgitesPythonDependencyPackageName(locDefinition, project.path)
                val locStrictSpecifier = AIcAlgitesPythonVersionRequirements(
                    locDefinition,
                    "Project '${project.path}' published Python Dependencies[$locIndex]"
                )[AInPythonBuildPhase.STRICT_MAXIMUMS].orEmpty()
                locPackageName + locStrictSpecifier
            }
        @Suppress("UNCHECKED_CAST")
        val locEnvironmentRequirements = locAlgitesArtifactDirectory?.get("environmentRequirements") as? Map<String, Map<String, Any?>>
        val locPythonEnvironmentRequirementDefinition = locEnvironmentRequirements?.get("python")
        val locPythonRequiresPython = locPythonEnvironmentRequirementDefinition?.let { locRequirement ->
            val locDefinition = mapOf<String, Any?>(
                "dependencyKind" to "python",
                "versionRequirement" to locRequirement
            )
            AIcAlgitesPythonVersionRequirements(locDefinition, "Project '${project.path}' EnvironmentRequirements.Python")[AInPythonBuildPhase.STRICT_MAXIMUMS]
                ?.takeIf { it.isNotBlank() }
        }

        val locPythonInputSubscriptions = AIcModustroInputSubscriptions(
            locEffectiveInputSubscriptions, "python", "native_product_binaries",
            if (modustroSourceRepositoryVisibility == "pub") setOf("public") else setOf("public", "private"),
            setOf("release", "snapshot")
        ).filter { it.subscriptionAdapter == "python-repository" }
        val locPythonDownloadEndpointDefinitions = locPythonInputSubscriptions.map { locSubscription ->
            val locProfileId = locSubscription.subscriptionCredentialProfile.orEmpty()
            val locProfileType = if (locProfileId.isBlank()) "" else locEffectiveCredentialProfiles[locProfileId]?.type.orEmpty()
            listOf(locSubscription.id, locSubscription.subscriptionUri.orEmpty(), locProfileId, locProfileType).joinToString("\t")
        }

        val locResolvePythonDependencies = tasks.register<AIcResolvePythonDependenciesTask>("resolvePythonDependencies") {
            group = "verification"
            description = "Runs the bounded Python dependency-resolution preflight for this Modustro-managed artifact."
            pythonExecutable.set(modustroGradleOrEnvironmentValue("ALGITES_PYTHON_EXECUTABLE") ?: "python3")
            projectPathValue.set(project.path)
            projectDirectory.set(layout.projectDirectory)
            dependencyDefinitions.set(locPythonResolutionDependencies)
            constraintDefinitions.set(locPythonResolutionConstraints)
            endpointDefinitions.set(locPythonDownloadEndpointDefinitions)
            credentialBaseDirectoryPath.set(rootProject.projectDir.absolutePath)
            if (locPythonTemplateFile.asFile.isFile) pyprojectTemplateFile.set(locPythonTemplateFile)
            selectedPhaseFile.set(layout.buildDirectory.file("algites/python/dependency-resolution-phase.txt"))
        }
        if ("python" in locEffectiveTechnologyKinds && locHasCapabilityDemand("python", "dependency_resolution")) {
            rootProject.tasks.named("modustroDependencyPreflight").configure {
                dependsOn(locResolvePythonDependencies)
            }
        }
        val locGeneratePythonProjectMetadata = tasks.register<AIcGeneratePythonProjectMetadataTask>("generatePythonProjectMetadata") {
            group = "modustro"
            description = "Generates the effective pyproject.toml for this Algites Python artifact."
            dependsOn(rootProject.tasks.named("verifyAlgitesLicensing"))
            pythonExecutable.set(modustroGradleOrEnvironmentValue("ALGITES_PYTHON_EXECUTABLE") ?: "python3")
            distributionName.set(locPythonDistributionName)
            pythonVersion.set(locPythonProjectVersion)
            artifactDescription.set(locPythonArtifactDescription)
            licenseIds.set(locPythonLicenseIds)
            projectDependencies.set(locPythonPublishedDependencies)
            packageSourceRoots.set(locAlgitesResolveSourceRootRelativePaths(project.projectDir, "product", "python"))
            if (locPythonRequiresPython != null) {
                requiresPython.set(locPythonRequiresPython)
            }
            if (locPythonTemplateFile.asFile.isFile) templateFile.set(locPythonTemplateFile)
            outputFile.set(locPythonProjectFile)
            inputs.files(locAlgitesProductLicenses.mapNotNull { it["file"]?.let(rootProject::file) })
        }

        locGeneratePythonProjectMetadata.configure {
            mustRunAfter(locDeletePythonDevelopmentMetadata)
        }

        val locRefreshPythonDevelopment = tasks.register("refreshPythonDevelopment") {
            group = "modustro"
            description = "Forces regeneration of Python development metadata for this artifact."
            dependsOn(locDeletePythonDevelopmentMetadata)
            dependsOn(locGeneratePythonProjectMetadata)
        }

        val locPythonProjectPath = project.path
        val locPythonProjectDirectory = project.projectDir
        val locPythonDistDirectory = locAlgitesProjectRunDirectory.dir("bld/python/dist")

        val locPreparePythonBuildProject = tasks.register<Sync>("preparePythonBuildProject") {
            group = "build"
            description = "Stages the Python package project in the repository build workspace."
            dependsOn(locGeneratePythonProjectMetadata)
            locPythonSourceProcessingTask?.let { locSourceProcessingTask -> dependsOn(locSourceProcessingTask) }

            duplicatesStrategy = DuplicatesStrategy.FAIL
            into(locPythonBuildProjectDirectory)
            from(project.layout.projectDirectory) {
                exclude("run/**", "build/**", ".gradle/**", ".kotlin/**", "**/__pycache__/**", "**/*.pyc", "**/*.pyo")
            }
            from(locPythonPreparedPackageResourcesDirectory) {
                into("src/product/python.gen")
            }
            locAlgitesProductLicenses.forEach { locLicense ->
                val locLicenseId = locLicense["id"] ?: return@forEach
                val locLicenseFile = locLicense["file"]?.let(rootProject::file) ?: return@forEach
                from(locLicenseFile) {
                    into("LICENSES")
                    rename { "$locLicenseId.txt" }
                }
            }
        }

        val locPythonBuildAndManifestScript = """
            import base64
            import gzip
            import hashlib
            import io
            import os
            import pathlib
            import shutil
            import subprocess
            import sys
            import tarfile
            import tempfile
            import zipfile

            project_dir = pathlib.Path(sys.argv[1]).resolve()
            output_dir = pathlib.Path(sys.argv[2]).resolve()
            manifest_file = pathlib.Path(sys.argv[3]).resolve()
            requested_outputs = {value for value in sys.argv[4].split(",") if value}
            manifest_name = "modustro-artifact-manifest.yml"
            manifest_bytes = manifest_file.read_bytes()

            if output_dir.exists():
                shutil.rmtree(output_dir)
            output_dir.mkdir(parents=True, exist_ok=True)

            build_command = [sys.executable, "-m", "build", "--outdir", str(output_dir)]
            if requested_outputs == {"wheel"}:
                build_command.append("--wheel")
            elif requested_outputs == {"sdist"}:
                build_command.append("--sdist")
            elif requested_outputs != {"wheel", "sdist"}:
                raise RuntimeError(f"Unsupported Python BuildOutput production set: {sorted(requested_outputs)}")

            # Setuptools cannot union two directories contributing modules to the same
            # namespace package: its package_dir mapping silently selects one root.
            # Merge only in a temporary build workspace and reject conflicting modules.
            import re
            import tomllib
            with tempfile.TemporaryDirectory(prefix="modustro-python-build-") as temporary_project:
                build_project = pathlib.Path(temporary_project) / "project"
                def ignore_build_outputs(directory, names):
                    # Only the project-level build directory is transient. A namespace
                    # such as eu/algites/tool/build is part of the package source.
                    return [name for name in names
                            if name == "__pycache__" or name.endswith((".egg-info", ".pyc", ".pyo"))
                            or (name == "build" and pathlib.Path(directory).resolve() == project_dir)]
                shutil.copytree(project_dir, build_project, ignore=ignore_build_outputs)
                metadata_path = build_project / "pyproject.toml"
                metadata_text = metadata_path.read_text(encoding="utf-8")
                metadata = tomllib.loads(metadata_text)
                source_roots = metadata["tool"]["setuptools"]["packages"]["find"]["where"]
                merged_root = build_project / "modustro-python-package"
                merged_root.mkdir()
                for source_root in source_roots:
                    source_path = (build_project / source_root).resolve()
                    if not source_path.is_relative_to(build_project):
                        raise RuntimeError(f"Python source root is outside the staged project: {source_root}")
                    if not source_path.is_dir():
                        continue
                    for source_file in sorted(source_path.rglob("*")):
                        if not source_file.is_file() or "__pycache__" in source_file.parts or source_file.suffix in (".pyc", ".pyo"):
                            continue
                        target = merged_root / source_file.relative_to(source_path)
                        if target.exists() and target.read_bytes() != source_file.read_bytes():
                            raise RuntimeError(f"Conflicting Python source module/resource: {source_file.relative_to(source_path)}")
                        target.parent.mkdir(parents=True, exist_ok=True)
                        shutil.copy2(source_file, target)
                metadata_text, replacements = re.subn(
                    r'(?ms)(^\[tool\.setuptools\.packages\.find\]\s*\n)(.*?)(?=^\[|\Z)',
                    lambda match: match.group(1) + re.sub(r'(?m)^where\s*=.*$', 'where = ["modustro-python-package"]', match.group(2)),
                    metadata_text,
                )
                if replacements != 1:
                    raise RuntimeError("Expected one owned Python package discovery table")
                metadata_path.write_text(metadata_text, encoding="utf-8")
                subprocess.run(build_command, cwd=build_project, check=True)

            def inject_wheel(path):
                with zipfile.ZipFile(path, "r") as source:
                    infos = source.infolist()
                    record_infos = [info for info in infos if info.filename.endswith(".dist-info/RECORD")]
                    if len(record_infos) != 1:
                        raise RuntimeError(f"Expected exactly one .dist-info/RECORD in {path.name}")
                    record_info = record_infos[0]
                    dist_info = record_info.filename.rsplit("/", 1)[0]
                    archive_manifest = f"{dist_info}/META-INF/modustro/{manifest_name}"
                    original_record = source.read(record_info.filename).decode("utf-8")
                    source_comment = source.comment

                    digest = base64.urlsafe_b64encode(hashlib.sha256(manifest_bytes).digest()).rstrip(b"=").decode("ascii")
                    record_lines = [
                        line for line in original_record.splitlines()
                        if line and not line.startswith(archive_manifest + ",")
                    ]
                    record_lines.append(f"{archive_manifest},sha256={digest},{len(manifest_bytes)}")
                    new_record = ("\n".join(record_lines) + "\n").encode("utf-8")

                    fd, temporary_name = tempfile.mkstemp(prefix=path.name + ".", suffix=".tmp", dir=path.parent)
                    os.close(fd)
                    temporary = pathlib.Path(temporary_name)
                    try:
                        with zipfile.ZipFile(temporary, "w") as target:
                            target.comment = source_comment
                            for info in infos:
                                if info.filename in {record_info.filename, archive_manifest}:
                                    continue
                                target.writestr(info, source.read(info.filename))

                            manifest_info = zipfile.ZipInfo(archive_manifest, date_time=(1980, 1, 1, 0, 0, 0))
                            manifest_info.compress_type = zipfile.ZIP_DEFLATED
                            manifest_info.external_attr = 0o100644 << 16
                            target.writestr(manifest_info, manifest_bytes)
                            target.writestr(record_info, new_record)
                        os.replace(temporary, path)
                    finally:
                        if temporary.exists():
                            temporary.unlink()

            def inject_sdist(path):
                with tarfile.open(path, "r:gz") as source:
                    members = source.getmembers()
                    roots = {member.name.split("/", 1)[0] for member in members if member.name}
                    if len(roots) != 1:
                        raise RuntimeError(f"Expected exactly one source-distribution root directory in {path.name}")
                    root = next(iter(roots))
                    archive_manifest = f"{root}/META-INF/modustro/{manifest_name}"

                    fd, temporary_name = tempfile.mkstemp(prefix=path.name + ".", suffix=".tmp", dir=path.parent)
                    os.close(fd)
                    temporary = pathlib.Path(temporary_name)
                    try:
                        with open(temporary, "wb") as raw_target:
                            with gzip.GzipFile(filename="", mode="wb", fileobj=raw_target, mtime=0) as gzip_target:
                                with tarfile.open(fileobj=gzip_target, mode="w", format=tarfile.PAX_FORMAT) as target:
                                    for member in members:
                                        if member.name == archive_manifest:
                                            continue
                                        source_file = source.extractfile(member) if member.isfile() else None
                                        target.addfile(member, source_file)

                                    manifest_info = tarfile.TarInfo(archive_manifest)
                                    manifest_info.size = len(manifest_bytes)
                                    manifest_info.mode = 0o644
                                    manifest_info.mtime = 0
                                    manifest_info.uid = 0
                                    manifest_info.gid = 0
                                    manifest_info.uname = ""
                                    manifest_info.gname = ""
                                    target.addfile(manifest_info, io.BytesIO(manifest_bytes))
                        os.replace(temporary, path)
                    finally:
                        if temporary.exists():
                            temporary.unlink()

            wheel_files = sorted(output_dir.glob("*.whl"))
            sdist_files = sorted(output_dir.glob("*.tar.gz"))
            if "wheel" in requested_outputs and not wheel_files:
                raise RuntimeError("Python build did not produce a wheel.")
            if "sdist" in requested_outputs and not sdist_files:
                raise RuntimeError("Python build did not produce a .tar.gz source distribution.")

            if "wheel" in requested_outputs:
                for wheel_file in wheel_files:
                    inject_wheel(wheel_file)
            if "sdist" in requested_outputs:
                for sdist_file in sdist_files:
                    inject_sdist(sdist_file)
        """.trimIndent()

        val locPythonBuildModes = buildList {
            if (AInBuildOutputProductionKind.PYTHON_WHEEL in locPythonProductionKinds) add("wheel")
            if (AInBuildOutputProductionKind.PYTHON_SDIST in locPythonProductionKinds) add("sdist")
        }

        val locBuildPython = tasks.register<Exec>("buildPython") {
            group = "build"
            description = "Builds Python wheel/source distribution and embeds the deterministic Modustro artifact manifest."
            dependsOn(rootProject.tasks.named("modustroDependencyPreflight"))
            dependsOn(locPreparePythonBuildProject)
            dependsOn(locGenerateModustroArtifactManifest)
            workingDir(locPythonBuildProjectDirectory)
            commandLine(
                modustroGradleOrEnvironmentValue("ALGITES_PYTHON_EXECUTABLE") ?: "python3",
                "-c",
                locPythonBuildAndManifestScript,
                locPythonBuildProjectDirectory.asFile.absolutePath,
                locPythonDistDirectory.asFile.absolutePath,
                locModustroManifestOutputFile.get().asFile.absolutePath,
                locPythonBuildModes.joinToString(",")
            )
            inputs.files(project.fileTree(locPythonBuildProjectDirectory) {
                exclude("**/__pycache__/**", "**/*.pyc", "**/*.pyo")
            })
            inputs.file(locModustroManifestOutputFile)
            outputs.dir(locPythonDistDirectory)
        }

        listOf(
            Triple(AInBuildOutputProductionKind.PYTHON_WHEEL, "publishModustroPythonNativeBinary", "*.whl"),
            Triple(AInBuildOutputProductionKind.PYTHON_SDIST, "publishModustroPythonNativeSources", "*.tar.gz")
        ).filter { it.first in locPythonProductionKinds }.forEach { (_, locTaskName, locPattern) ->
            val locOutputKind = if (locPattern == "*.whl") "native_product_binaries" else "native_product_sources"
            val locPlanJson = locModustroPublicationPlanJson(locOutputKind, "python")
            val locDistributionFiles = fileTree(locPythonDistDirectory) { include(locPattern) }
            val locPythonExecutable = modustroGradleOrEnvironmentValue("ALGITES_PYTHON_EXECUTABLE") ?: "python3"
            val locPublish = tasks.register<AIcModustroPublishFilesTask>(locTaskName) {
                group = "publishing"
                description = "Publishes the Python distribution through the Modustro publication scheduler."
                dependsOn(modustroPublicationBuildGate, locBuildPython)
                publicationPlanJson.set(locPlanJson)
                credentialProfilesJson.set(locPublicationCredentialProfilesJson)
                outputKind.set(locOutputKind.uppercase())
                stability.set(locModustroPublicationStability)
                artifactIdentity.set(listOfNotNull(locAlgitesResolvedProjectGroup, locAlgitesEffectiveArtifactId).joinToString(":"))
                publicationVersion.set(locPythonProjectVersion)
                coordinates.set(mapOf("groupId" to (locAlgitesResolvedProjectGroup ?: ""), "artifactId" to locAlgitesEffectiveArtifactId,
                    "version" to locPythonProjectVersion, "logicalVersion" to locAlgitesProjectVersion, "technologyKind" to "python", "pythonExecutable" to locPythonExecutable))
                requiresSnapshotInstance.set(true)
                snapshotInstanceId.set(algitesSnapshotInstanceId ?: "")
                payloadFiles.from(locDistributionFiles)
                publicationService.set(modustroPublicationService)
                usesService(modustroPublicationService)
            }
            if ("python" in locEffectiveTechnologyKinds && locAnyNativePublicationEnabled) {
                modustroPublish.configure { dependsOn(locPublish) }
            }
        }

        if ("python" in locEffectiveTechnologyKinds) {
            val locDevelopKinds = listOf("native_develop_binaries", "native_develop_sources")
            val locDevelopPlans = locDevelopKinds.associateWith { locModustroPublicationPlanJson(it, "python") }
            if (locDevelopPlans.values.any { (JsonSlurper().parseText(it) as Map<*, *>)["publicationEnabled"] == true }) {
                val locDevelopRoot = locAlgitesProjectRunDirectory.dir("bld/python/develop-project")
                val locDevelopDist = locAlgitesProjectRunDirectory.dir("bld/python/develop-dist")
                val locDevelopMetadata = locAlgitesProjectRunDirectory.file("bld/python/develop-pyproject.toml")
                val locDevelopName = "$locPythonDistributionName-develop"
                val locDevelopSourceRoots = locAlgitesResolveSourceRootRelativePaths(project.projectDir, "develop", "python")
                if (locDevelopSourceRoots.isEmpty()) throw GradleException("Python develop publishing requires src/develop/python source roots in ${project.path}.")
                val locDevelopDependencies = locPythonDependencyDefinitions.filter { definition ->
                    AIcModustroDependencyUsages(definition).any { it in setOf("develop_implementation", "develop_runtime_only") }
                }.mapIndexed { index, definition ->
                    AIcAlgitesPythonDependencyPackageName(definition, project.path) + AIcAlgitesPythonVersionRequirements(definition,
                        "Project '${project.path}' published Python develop Dependencies[$index]")[AInPythonBuildPhase.STRICT_MAXIMUMS].orEmpty()
                }
                val locGenerateDevelopMetadata = tasks.register<AIcGeneratePythonProjectMetadataTask>("generateModustroPythonDevelopProjectMetadata") {
                    pythonExecutable.set(modustroGradleOrEnvironmentValue("ALGITES_PYTHON_EXECUTABLE") ?: "python3")
                    distributionName.set(locDevelopName); pythonVersion.set(locPythonProjectVersion)
                    artifactDescription.set("Development outputs for $locPythonDistributionName")
                    licenseIds.set(locPythonLicenseIds)
                    projectDependencies.set((locDevelopDependencies + "$locPythonDistributionName==$locPythonProjectVersion").distinct())
                    packageSourceRoots.set(locDevelopSourceRoots)
                    if (locPythonRequiresPython != null) requiresPython.set(locPythonRequiresPython)
                    outputFile.set(locDevelopMetadata)
                }
                val locStageDevelop = tasks.register<Sync>("prepareModustroPythonDevelopBuildProject") {
                    dependsOn(locGenerateDevelopMetadata)
                    into(locDevelopRoot)
                    from(project.layout.projectDirectory) {
                        exclude("run/**", "build/**", ".gradle/**", ".kotlin/**", "**/__pycache__/**", "**/*.pyc", "**/*.pyo", "pyproject.toml", "src/product/**")
                    }
                    from(locDevelopMetadata) { rename { "pyproject.toml" } }
                    locAlgitesProductLicenses.forEach { license ->
                        val id = license["id"] ?: return@forEach
                        val file = license["file"]?.let(rootProject::file) ?: return@forEach
                        from(file) { into("LICENSES"); rename { "$id.txt" } }
                    }
                }
                val locDevelopBuild = tasks.register<Exec>("buildModustroPythonDevelop") {
                    group = "build"; dependsOn(locStageDevelop, locGenerateModustroArtifactManifest)
                    workingDir(locDevelopRoot)
                    commandLine(modustroGradleOrEnvironmentValue("ALGITES_PYTHON_EXECUTABLE") ?: "python3", "-c", locPythonBuildAndManifestScript,
                        locDevelopRoot.asFile.absolutePath, locDevelopDist.asFile.absolutePath,
                        locModustroManifestOutputFile.get().asFile.absolutePath, "wheel,sdist")
                    inputs.files(project.fileTree(locDevelopRoot) {
                        exclude("**/__pycache__/**", "**/*.pyc", "**/*.pyo")
                    })
                    inputs.file(locModustroManifestOutputFile)
                    outputs.dir(locDevelopDist)
                }
                locDevelopKinds.forEach { kind ->
                    val plan = locDevelopPlans.getValue(kind)
                    val pattern = if (kind == "native_develop_binaries") "*.whl" else "*.tar.gz"
                    val publish = tasks.register<AIcModustroPublishFilesTask>(if (kind == "native_develop_binaries") "publishModustroPythonDevelopBinaries" else "publishModustroPythonDevelopSources") {
                        group = "publishing"; dependsOn(modustroPublicationBuildGate, locDevelopBuild)
                        publicationPlanJson.set(plan); credentialProfilesJson.set(locPublicationCredentialProfilesJson)
                        outputKind.set(kind.uppercase()); stability.set(locModustroPublicationStability)
                        artifactIdentity.set("${locAlgitesResolvedProjectGroup}:$locAlgitesEffectiveArtifactId:develop")
                        publicationVersion.set(locPythonProjectVersion)
                        coordinates.set(mapOf("groupId" to (locAlgitesResolvedProjectGroup ?: ""), "artifactId" to locAlgitesEffectiveArtifactId,
                            "version" to locPythonProjectVersion, "logicalVersion" to locAlgitesProjectVersion, "technologyKind" to "python",
                            "pythonExecutable" to (modustroGradleOrEnvironmentValue("ALGITES_PYTHON_EXECUTABLE") ?: "python3")))
                        requiresSnapshotInstance.set(true); snapshotInstanceId.set(algitesSnapshotInstanceId ?: "")
                        payloadFiles.from(fileTree(locDevelopDist) { include(pattern) })
                        publicationService.set(modustroPublicationService); usesService(modustroPublicationService)
                    }
                    modustroPublish.configure { dependsOn(publish) }
                }
            }
        }

        modustroPrepareDevelopment.configure { dependsOn(locGeneratePythonProjectMetadata) }
        modustroRefreshDevelopment.configure { dependsOn(locRefreshPythonDevelopment) }
        locBuildPython.configure {
            dependsOn(rootProject.tasks.named("validateModustroPythonDistributionPaths"))
        }
        if ("python" in locEffectiveTechnologyKinds && locPythonBuildModes.isNotEmpty()) {
            modustroBuild.configure { dependsOn(locBuildPython) }
        }
    }
}


tasks.register("printModustroPublicationPlan") {
    group = "modustro"
    description = "Prints the effective Modustro Builder input-subscription and output-publication configuration."

    doLast {
        println("Modustro Builder input/publication plan for ${rootProject.name}:")
        println(" - repository visibility: $modustroSourceRepositoryVisibility")
        println(" - requested technology kinds: ${if (modustroRequestedTechnologyKinds.isEmpty()) "all effective technology kinds" else modustroRequestedTechnologyKinds.joinToString(",")}")
        println(" - docs pages branch: $modustroDocsPagesBranch")
        modustroResolvedArtifactDirectoriesByGradleProjectPath.toSortedMap().forEach { locEntry ->
            val locMetadata = locEntry.value
            println(" - ${locEntry.key}: technologyKinds=${AIcModustroStringList(locMetadata["technologyKinds"])}")
            val locInputSubscriptions = locMetadata["inputSubscriptions"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
            locInputSubscriptions.toSortedMap(compareBy { it.toString() }).forEach { (locKey, locSubscriptions) ->
                println("     InputSubscriptions[$locKey]=$locSubscriptions")
            }
            val locOutputPublications = locMetadata["outputPublications"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
            locOutputPublications.toSortedMap(compareBy { it.toString() }).forEach { (locKey, locPublications) ->
                println("     OutputPublications[$locKey]=$locPublications")
            }
        }
    }
}

tasks.register("ciHelp") {
    group = "modustro"
    description = "Prints a small marker proving that the Modustro Builder root Gradle build was detected."

    doLast {
        println("Modustro Builder root Gradle build detected: ${rootProject.name}")
    }
}


/* Modustro Builder 5.2 phase and publishing invocation adapters. */
val locModustroSourceRepositoryRootForPhases = generateSequence(rootProject.projectDir.canonicalFile) { it.parentFile }
    .firstOrNull { locDirectory -> locDirectory.resolve("modustro-source-repository.yml").isFile }
    ?: throw GradleException("Cannot locate modustro-source-repository.yml on the ancestor path of Gradle build root '${rootProject.projectDir.path}'.")
listOf("modustro-build-phase-tasks.gradle.kts").forEach { locScriptName ->
    val locScript = locModustroSourceRepositoryRootForPhases.resolve("gradle/tool/repository/$locScriptName")
    if (locScript.isFile) {
        apply(from = locScript)
    } else {
        apply(from = uri(
            "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/${System.getenv("MODUSTRO_PUBLIC_GOVERNANCE_REVISION") ?: "main"}/gradle/tool/repository/$locScriptName"
        ))
    }
}
