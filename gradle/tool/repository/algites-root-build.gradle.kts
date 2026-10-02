/*
 * Algites generic repository build conventions.
 *
 * Public entry-point location is stable. The implementation consumes the
 * effective metadata resolved from algites-source-repository.yml and nested
 * algites-artifact.yml files.
 */

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
import eu.algites.pltf.modustro.builder.output.AIcBuiltinBuildOutputProducers
import eu.algites.lib.naming.convention.AIcAlgitesNamingProfiles
import eu.algites.tool.codegen.defs.AIcDefaultDefsCodegenService
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
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

buildscript {
    repositories {
        mavenCentral()
        maven {
            name = "algites_public_snapshots_bootstrap"
            url = uri("https://dl.cloudsmith.io/public/algites/java-snapshots-pub/maven/")
            mavenContent {
                snapshotsOnly()
            }
        }
    }
    dependencies {
        classpath("eu.algites.tool.build:pub.gov.Algites_devops.build.algitesbuild:1.0-SNAPSHOT")
        classpath("eu.algites.pltf.modustro.builder:pub.gov.Algites_devops.build.modustro.builder.coreintf:1.0-SNAPSHOT")
        classpath("eu.algites.pltf.modustro.builder:pub.gov.Algites_devops.build.modustro.builder.coreimpl:1.0-SNAPSHOT")
        classpath("eu.algites.tool.codegen:pub.tool.General_generators.code.defscodegen.coreintf:1.0-SNAPSHOT")
        classpath("eu.algites.tool.codegen:pub.tool.General_generators.code.defscodegen.coreimpl:1.0-SNAPSHOT")
        classpath("eu.algites.lib.naming:pub.lib.General_naming.convention.coreimpl:1.0-SNAPSHOT")
    }
}


/**
 * Generates configured Java/Python sources from canonical definitions through the reusable Defs Codegen API.
 *
 * The standard `.gen` roots are shared by generators, so this task does not claim either root as an exclusive
 * Gradle output directory. It owns only the files recorded in its artifact-local manifest state and removes stale
 * files from that ownership set on subsequent executions.
 */
abstract class AIcGenerateAlgitesDefinitionSourcesTask : DefaultTask() {
    @get:Input
    abstract val entries: ListProperty<String>

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

    private data class AIcdEntry(
        val sourceKind: String,
        val source: String,
        val targets: List<String>,
        val packageName: String,
        val namingProfile: String
    )

    private data class AIcdPendingSource(
        val target: String,
        val relativePath: String,
        val source: String
    )

    private fun AIcEntry(aEncoded: String): AIcdEntry {
        val locParts = aEncoded.split('\t')
        require(locParts.size == 5) { "Invalid Algites DefinitionCodeGeneration entry '$aEncoded'." }
        return AIcdEntry(
            sourceKind = locParts[0],
            source = locParts[1],
            targets = locParts[2].split(',').map(String::trim).filter(String::isNotBlank),
            packageName = locParts[3],
            namingProfile = locParts[4]
        )
    }

    private fun AIcSourceKind(aValue: String): AInDefinitionSourceKind = when (aValue) {
        "yamldefs" -> AInDefinitionSourceKind.YAMLDEFS
        "jsondefs" -> AInDefinitionSourceKind.JSONDEFS
        "xmldefs" -> AInDefinitionSourceKind.XMLDEFS
        else -> throw GradleException("Unsupported DefinitionCodeGeneration SourceKind '$aValue'.")
    }

    private fun AIcTarget(aValue: String): AInCodeGenerationTarget = when (aValue) {
        "java" -> AInCodeGenerationTarget.JAVA
        "python" -> AInCodeGenerationTarget.PYTHON
        else -> throw GradleException("Unsupported DefinitionCodeGeneration target '$aValue'.")
    }

    private fun AIcOutputRoot(aTarget: String): File = when (aTarget) {
        "java" -> javaOutputDirectory.get().asFile
        "python" -> pythonOutputDirectory.get().asFile
        else -> throw GradleException("Unsupported DefinitionCodeGeneration target '$aTarget'.")
    }

    private fun AIcManifest(aTarget: String): File = File(manifestDirectory.get().asFile, "$aTarget.manifest")

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

    /** Generates configured Java/Python source units from canonical definitions through Defs Codegen. */
    @TaskAction
    fun AIcGenerate() {
        val locArtifactDirectory = artifactDirectory.get().asFile.canonicalFile
        val locService = AIcDefaultDefsCodegenService()
        val locPending = mutableListOf<AIcdPendingSource>()
        entries.get().map(::AIcEntry).forEach { locEntry ->
            require(locEntry.namingProfile == "algites") {
                "Unsupported DefinitionCodeGeneration NamingProfile '${locEntry.namingProfile}'."
            }
            val locInput = File(locArtifactDirectory, locEntry.source).canonicalFile
            require(locInput.toPath().startsWith(locArtifactDirectory.toPath())) {
                "DefinitionCodeGeneration source '${locEntry.source}' escapes artifact '$locArtifactDirectory'."
            }
            require(locInput.isFile) { "DefinitionCodeGeneration source does not exist: '$locInput'." }
            locEntry.targets.forEach { locTargetText ->
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
                val locGenerated = locService.generate(
                    AIcdCodeGenerationRequest(locDefinition, locTarget, locEntry.packageName, locProfile)
                )
                val locRelativePath = locGenerated.relativePath().replace('\\', '/')
                val locCollision = locPending.firstOrNull { locPendingSource ->
                    locPendingSource.target == locTargetText && locPendingSource.relativePath == locRelativePath
                }
                if (locCollision != null) {
                    throw GradleException(
                        "DefinitionCodeGeneration produces duplicate $locTargetText source path '$locRelativePath'."
                    )
                }
                locPending.add(AIcdPendingSource(locTargetText, locRelativePath, locGenerated.source()))
            }
        }
        listOf("java", "python").forEach { locTarget ->
            AIcWriteTarget(locTarget, locPending.filter { it.target == locTarget })
        }
    }
}

abstract class AIcGenerateAlgitesArtifactManifestTask : DefaultTask() {
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
    @get:Optional
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
            require(locParts.size == 3) { "Invalid Algites descriptor hierarchy entry '$locEntry'." }
            locParts
        }
        require(locDescriptorEntries.isNotEmpty()) { "Algites artifact manifest descriptor hierarchy must not be empty." }

        locOutputFile.writeText(
            buildString {
                appendLine("# yaml-language-server: \$schema=https://defs.dev.algites.eu/api/yamldefs/eu/algites/tool/build/yamldefs/algites-artifact-manifest_1.yamldef.schema.json")
                appendLine("# \$schema: https://defs.dev.algites.eu/api/yamldefs/eu/algites/tool/build/yamldefs/algites-artifact-manifest_1.yamldef.schema.json")
                appendLine("\$schema: https://defs.dev.algites.eu/api/yamldefs/eu/algites/tool/build/yamldefs/algites-artifact-manifest_1.yamldef.schema.json")
                appendLine()
                appendLine("ManifestVersion: 1")
                appendLine("Artifact:")
                appendLine("  RepositoryId: ${AIcYamlScalar(repositoryId.get())}")
                appendLine("  LocalArtifactId: ${AIcYamlScalar(localArtifactId.get())}")
                appendLine("  ArtifactCoordinateId: ${AIcYamlScalar(artifactCoordinateId.get())}")
                variantId.orNull?.takeIf { it.isNotBlank() }?.let { locVariantId ->
                    appendLine("  VariantId: ${AIcYamlScalar(locVariantId)}")
                }
                groupId.orNull?.takeIf { it.isNotBlank() }?.let { locGroupId ->
                    appendLine("  GroupId: ${AIcYamlScalar(locGroupId)}")
                }
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

abstract class AIcPublishPythonTask : DefaultTask() {
    @get:Input abstract val pythonExecutable: Property<String>
    @get:Input abstract val projectPathValue: Property<String>
    @get:Input abstract val projectVersionValue: Property<String>
    @get:Input abstract val publicationStability: Property<String>
    @get:Input abstract val repositoryVisibility: Property<String>
    @get:Input @get:Optional abstract val snapshotInstanceId: Property<String>
    @get:Input abstract val endpointDefinitions: ListProperty<String>
    @get:Input abstract val credentialBaseDirectoryPath: Property<String>
    @get:InputDirectory abstract val distributionDirectory: DirectoryProperty

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

    private fun AIcCredentialValue(aProfileId: String, aCredentialType: String, aField: String): String? {
        val locRawDocument = System.getenv("ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS")?.takeIf { it.isNotBlank() }
            ?: AIcCommandOutput(listOf("bootstrap-document")) ?: return null
        val locDocument = AIcParseJsonObject(locRawDocument)
        val locProfile = locDocument[aProfileId] as? Map<*, *> ?: return null
        val locTypeProperty = when (aCredentialType) {
            "basic" -> "Basic"
            "bearer" -> "Bearer"
            "api_key" -> "ApiKey"
            "certificate" -> "Certificate"
            else -> return null
        }
        val locType = locProfile[locTypeProperty] as? Map<*, *> ?: return null
        val locField = locType[aField] as? Map<*, *> ?: return null
        val locSource = locField["Source"]?.toString()?.lowercase() ?: return null
        val locReference = locField["Value"]?.toString() ?: return null
        return when (locSource) {
            "direct_value" -> locReference
            "file_content" -> {
                val locCandidate = File(locReference)
                val locFile = if (locCandidate.isAbsolute) locCandidate else File(credentialBaseDirectoryPath.get(), locReference)
                if (!locFile.isFile) throw GradleException("Credential '$aProfileId/$aCredentialType/$aField' references missing file '${locFile.path}'.")
                locFile.readText(Charsets.UTF_8)
            }
            "environment_variable_content" -> System.getenv(locReference)
                ?: throw GradleException("Credential '$aProfileId/$aCredentialType/$aField' references unavailable environment variable '$locReference'.")
            "secret_content" -> {
                val locSecrets = AIcParseJsonObject(System.getenv("_TMP_ALGITES_CREDENTIAL_SECRETS_JSON"))
                locSecrets[locReference]?.toString() ?: AIcCommandOutput(listOf("bootstrap-secret", locReference))
                ?: throw GradleException("Credential '$aProfileId/$aCredentialType/$aField' references unavailable secret '$locReference'.")
            }
            else -> throw GradleException("Credential '$aProfileId/$aCredentialType/$aField' uses unsupported source '$locSource'.")
        }
    }

    @TaskAction
    fun AIcPublish() {
        if (projectVersionValue.get().endsWith("SNAPSHOT", ignoreCase = true) && snapshotInstanceId.orNull.isNullOrBlank()) {
            throw GradleException("Publishing a Python snapshot requires an immutable snapshot instance id. Set -Palgites.snapshot.instanceId=<UTC timestamp> or ALGITES_SNAPSHOT_INSTANCE_ID. Normal local builds may omit it and use .dev0 as development metadata only.")
        }
        if (endpointDefinitions.get().isEmpty()) {
            throw GradleException(
                "No enabled Python native_build_output ${publicationStability.get()} upload ResourceEndpoint is configured for project '${projectPathValue.get()}'. " +
                    "Configure ResourceEndpoints.python.native_build_output.${repositoryVisibility.get()}.upload with Stability=${publicationStability.get()} in Algites metadata or its inherited defaults."
            )
        }
        val locDistributionFiles = distributionDirectory.get().asFile.listFiles()?.filter(File::isFile)?.sortedBy(File::getName) ?: emptyList()
        if (locDistributionFiles.isEmpty()) throw GradleException("No Python distribution files were produced for project '${projectPathValue.get()}'.")

        endpointDefinitions.get().forEach { locDefinition ->
            val locParts = locDefinition.split('\t')
            require(locParts.size == 4) { "Invalid Python publication endpoint definition '$locDefinition'." }
            val (locEndpointId, locEndpointUrl, locProfileIdRaw, locProfileTypeRaw) = locParts
            val locCommand = mutableListOf(pythonExecutable.get(), "-m", "twine", "upload", "--repository-url", locEndpointUrl)
            if (locProfileIdRaw.isNotBlank()) {
                if (locProfileTypeRaw.isBlank()) {
                    throw GradleException("Repository endpoint '$locEndpointId' references undefined credential profile '$locProfileIdRaw'.")
                }
                if (locProfileTypeRaw != "basic") {
                    throw GradleException("Python upload endpoint '$locEndpointId' uses credential type '$locProfileTypeRaw'. The current Twine adapter supports 'basic'. Define a TechnologyKind-specific adapter before using another type.")
                }
                val locUsername = AIcCredentialValue(locProfileIdRaw, "basic", "Username")
                val locPassword = AIcCredentialValue(locProfileIdRaw, "basic", "Password")
                if (locUsername.isNullOrEmpty() || locPassword.isNullOrEmpty()) {
                    throw GradleException("Credential profile '$locProfileIdRaw' type 'basic' is not available in ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS or the local Algites secure-store credential document.")
                }
                locCommand.addAll(listOf("--username", locUsername, "--password", locPassword))
            }
            locCommand.addAll(locDistributionFiles.map(File::getAbsolutePath))
            val locExitValue = ProcessBuilder(locCommand).directory(distributionDirectory.get().asFile).inheritIO().start().waitFor()
            if (locExitValue != 0) throw GradleException("Python upload command failed for endpoint '$locEndpointId' with exit code $locExitValue.")
        }
    }
}

apply(plugin = "base")

val locAlgitesResolverWrapperScript = rootProject.file("gradle/tool/repository/algites-artifact-directory-metadata-resolver-wrapper.gradle.kts")
if (locAlgitesResolverWrapperScript.isFile) {
    apply(from = locAlgitesResolverWrapperScript)
} else {
    apply(from = uri("https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/repository/algites-artifact-directory-metadata-resolver-wrapper.gradle.kts"))
}

val locAlgitesCredentialValuesScript = rootProject.file("gradle/tool/repository/algites-credential-values.gradle.kts")
if (locAlgitesCredentialValuesScript.isFile) {
    apply(from = locAlgitesCredentialValuesScript)
} else {
    apply(from = uri("https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/repository/algites-credential-values.gradle.kts"))
}

val locAlgitesLicensingScript = rootProject.file("gradle/tool/licensing/algites-licensing.gradle.kts")
if (locAlgitesLicensingScript.isFile) {
    apply(from = locAlgitesLicensingScript)
} else {
    apply(from = uri("https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/licensing/algites-licensing.gradle.kts"))
}

@Suppress("UNCHECKED_CAST")
val locAlgitesLicensesForPathAndContentKind = rootProject.extra["algitesLicensesForPathAndContentKind"] as (String, String) -> List<Map<String, String?>>

@Suppress("UNCHECKED_CAST")
val locAlgitesResolveCredentialValue = extra["algitesResolveCredentialValue"] as (String, String, String, File) -> String?

val algitesCredentialPreflight = System.getenv("_TMP_ALGITES_CREDENTIAL_PREFLIGHT")
    ?.equals("true", ignoreCase = true) == true

val locAlgitesSourceRootResolverScript = rootProject.file("gradle/tool/repository/algites-source-root-resolver.gradle.kts")
if (locAlgitesSourceRootResolverScript.isFile) {
    apply(from = locAlgitesSourceRootResolverScript)
} else {
    apply(from = uri("https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/repository/algites-source-root-resolver.gradle.kts"))
}

@Suppress("UNCHECKED_CAST")
val locAlgitesResolveSourceRootRelativePaths = rootProject.extra["algitesResolveSourceRootRelativePaths"] as
    (File, String, String) -> List<String>

val locAlgitesDocsSiteScript = rootProject.file("gradle/tool/documentation/algites-docs-site.gradle.kts")
if (locAlgitesDocsSiteScript.isFile) {
    apply(from = locAlgitesDocsSiteScript)
} else {
    apply(from = uri("https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/algites-docs-site.gradle.kts"))
}

fun String.capitalizedForAlgitesName(): String =
    replaceFirstChar { locCharacter ->
        if (locCharacter.isLowerCase()) {
            locCharacter.titlecase()
        } else {
            locCharacter.toString()
        }
    }

fun algitesGradleOrEnvironmentValue(aName: String): String? =
    (providers.gradleProperty(aName).orNull
        ?: providers.environmentVariable(aName).orNull)
        ?.trim()
        ?.takeIf { it.isNotBlank() }

fun AIcAlgitesRunDirectoryRelativePath(aProjectDirectory: File): String {
    val locRepositoryRootPath = rootProject.projectDir.toPath().toAbsolutePath().normalize()
    val locProjectPath = aProjectDirectory.toPath().toAbsolutePath().normalize()
    require(locProjectPath.startsWith(locRepositoryRootPath)) {
        "Algites project directory '$aProjectDirectory' is outside repository root '${rootProject.projectDir}'."
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

fun AIcAlgitesStringList(aValue: Any?): List<String> {
    return when (aValue) {
        is Iterable<*> -> aValue.mapNotNull { it?.toString()?.trim()?.lowercase()?.takeIf(String::isNotBlank) }
        null -> emptyList()
        else -> aValue.toString().split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }
    }
}

@Suppress("UNCHECKED_CAST")
fun AIcAlgitesExplicitBuildOutputTypes(aArtifactDirectory: Map<String, Any?>?, aTechnologyKind: String): Set<String>? {
    val locByTechnology = aArtifactDirectory?.get("buildOutputTypesByTechnologyKind") as? Map<*, *> ?: return null
    if (!locByTechnology.containsKey(aTechnologyKind)) return null
    return AIcAlgitesStringList(locByTechnology[aTechnologyKind]).toCollection(linkedSetOf())
}

fun AIcAlgitesPreparedSourceSet(aProject: Project, aTechnologyKind: String): AIcPreparedSourceSet {
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

data class AIcdAlgitesResourceEndpoint(
    val cell: String,
    val id: String,
    val url: String,
    val credentialProfile: String?,
    val stability: String?,
    val resourceEndpointProviderAdapter: String?
)

data class AIcdAlgitesCredentialProfile(
    val id: String,
    val type: String,
    val configuration: Map<String, String>
)

@Suppress("UNCHECKED_CAST")
fun AIcAlgitesResourceEndpoints(aValue: Any?, aCell: String, aStability: String? = null): List<AIcdAlgitesResourceEndpoint> {
    val locResourceEndpoints = aValue as? Map<*, *> ?: return emptyList()
    val locItems = locResourceEndpoints[aCell] as? List<*> ?: return emptyList()
    return locItems.mapNotNull { locItem ->
        val locMap = locItem as? Map<*, *> ?: return@mapNotNull null
        val locEnabled = locMap["enabled"]?.toString()?.toBooleanStrictOrNull() ?: true
        if (!locEnabled) return@mapNotNull null
        val locId = locMap["id"]?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val locUrl = locMap["url"]?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val locCredentialProfile = locMap["credentialProfile"]?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        val locStability = locMap["stability"]?.toString()?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it != "null" }
        if (aStability != null && locStability != aStability) return@mapNotNull null
        val locResourceEndpointProviderAdapter = locMap["resourceEndpointProviderAdapter"]?.toString()?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it != "null" }
        AIcdAlgitesResourceEndpoint(aCell, locId, locUrl, locCredentialProfile, locStability, locResourceEndpointProviderAdapter)
    }
}

@Suppress("UNCHECKED_CAST")
fun AIcAlgitesCredentialProfiles(aValue: Any?): Map<String, AIcdAlgitesCredentialProfile> {
    val locProfiles = aValue as? Map<*, *> ?: return emptyMap()
    val locResult = linkedMapOf<String, AIcdAlgitesCredentialProfile>()
    locProfiles.forEach { (locRawId, locRawDefinition) ->
        val locId = locRawId?.toString() ?: return@forEach
        val locDefinition = locRawDefinition as? Map<*, *> ?: return@forEach
        val locType = locDefinition["type"]?.toString()?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return@forEach
        val locConfiguration = (locDefinition["configuration"] as? Map<*, *>)
            ?.entries
            ?.associate { locEntry -> locEntry.key.toString() to locEntry.value.toString() }
            ?: emptyMap()
        locResult[locId] = AIcdAlgitesCredentialProfile(locId, locType, locConfiguration)
    }
    return locResult
}

fun AIcAlgitesCredentialValue(aProfile: AIcdAlgitesCredentialProfile, aField: String): String? =
    locAlgitesResolveCredentialValue(aProfile.id, aProfile.type, aField, rootProject.projectDir)

fun AIcAlgitesRequireBasicCredential(aProfile: AIcdAlgitesCredentialProfile): Pair<String, String> {
    val locUsername = AIcAlgitesCredentialValue(aProfile, "Username")
    val locPassword = AIcAlgitesCredentialValue(aProfile, "Password")
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

fun AIcAlgitesSnapshotInstanceId(): String? {
    val locValue = (providers.gradleProperty("algites.snapshot.instanceId").orNull
        ?: providers.environmentVariable("ALGITES_SNAPSHOT_INSTANCE_ID").orNull)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return null
    if (!Regex("^[0-9]+$").matches(locValue)) {
        throw GradleException(
            "Algites snapshot instance id must contain decimal digits only, but got '$locValue'. " +
                "Use -Palgites.snapshot.instanceId=<UTC timestamp> or ALGITES_SNAPSHOT_INSTANCE_ID."
        )
    }
    return locValue
}

fun AIcAlgitesPythonVersion(aVersion: String, aSnapshotInstanceId: String? = null): String {
    val locSnapshotSuffix = "-SNAPSHOT"
    if (aVersion.endsWith(locSnapshotSuffix, ignoreCase = true)) {
        val locInstanceId = aSnapshotInstanceId ?: "0"
        return aVersion.dropLast(locSnapshotSuffix.length).replace('-', '.') + ".dev$locInstanceId"
    }
    return aVersion.replace('-', '.')
}

fun AIcAlgitesPythonSnapshotVersionPrefix(aReleaseVersion: String): String =
    AIcAlgitesPythonVersion("$aReleaseVersion-SNAPSHOT", "0").dropLast(1)


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

fun AIcAlgitesCloudsmithHeaders(aEndpoint: AIcdAlgitesResourceEndpoint, aProfiles: Map<String, AIcdAlgitesCredentialProfile>): Map<String, String> {
    val locProfileId = aEndpoint.credentialProfile
        ?: throw GradleException("Cloudsmith manage endpoint '${aEndpoint.id}' requires a credentialProfile.")
    val locProfile = aProfiles[locProfileId]
        ?: throw GradleException("Cloudsmith manage endpoint '${aEndpoint.id}' references undefined credential profile '$locProfileId'.")
    return when (locProfile.type) {
        "api_key" -> {
            val locApiKey = AIcAlgitesCredentialValue(locProfile, "ApiKey")
                ?: throw GradleException("Credential profile '$locProfileId' does not provide required apiKey.")
            val locHeaderName = locProfile.configuration["headerName"]?.takeIf { it.isNotBlank() } ?: "Authorization"
            val locPrefix = locProfile.configuration["headerValuePrefix"] ?: "token "
            mapOf(locHeaderName to "$locPrefix$locApiKey")
        }
        "bearer" -> {
            val locToken = AIcAlgitesCredentialValue(locProfile, "Token")
                ?: throw GradleException("Credential profile '$locProfileId' does not provide required token.")
            mapOf("Authorization" to "Bearer $locToken")
        }
        else -> throw GradleException(
            "Cloudsmith manage endpoint '${aEndpoint.id}' uses credential type '${locProfile.type}'. " +
                "The Cloudsmith management adapter supports 'api_key' and 'bearer'."
        )
    }
}

fun AIcAlgitesHttpRequest(aMethod: String, aUrl: String, aHeaders: Map<String, String>): Pair<Int, String> {
    val locConnection = URI(aUrl).toURL().openConnection() as HttpURLConnection
    locConnection.requestMethod = aMethod
    locConnection.connectTimeout = 30_000
    locConnection.readTimeout = 60_000
    locConnection.instanceFollowRedirects = true
    locConnection.setRequestProperty("Accept", "application/json")
    aHeaders.forEach { (locName, locValue) -> locConnection.setRequestProperty(locName, locValue) }
    return try {
        val locStatus = locConnection.responseCode
        val locStream = if (locStatus in 200..299) locConnection.inputStream else locConnection.errorStream
        val locBody = locStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        locStatus to locBody
    } finally {
        locConnection.disconnect()
    }
}

fun AIcAlgitesHttpJsonRequest(
    aMethod: String,
    aUrl: String,
    aHeaders: Map<String, String>,
    aBody: String
): Pair<Int, String> {
    val locConnection = URI(aUrl).toURL().openConnection() as HttpURLConnection
    locConnection.requestMethod = aMethod
    locConnection.connectTimeout = 30_000
    locConnection.readTimeout = 60_000
    locConnection.instanceFollowRedirects = true
    locConnection.doOutput = true
    locConnection.setRequestProperty("Accept", "application/json")
    locConnection.setRequestProperty("Content-Type", "application/json")
    aHeaders.forEach { (locName, locValue) -> locConnection.setRequestProperty(locName, locValue) }
    return try {
        locConnection.outputStream.use { locStream ->
            locStream.write(aBody.toByteArray(Charsets.UTF_8))
        }
        val locStatus = locConnection.responseCode
        val locStream = if (locStatus in 200..299) locConnection.inputStream else locConnection.errorStream
        val locResponseBody = locStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        locStatus to locResponseBody
    } finally {
        locConnection.disconnect()
    }
}

fun AIcAlgitesUrlPathSegment(aValue: String): String =
    URLEncoder.encode(aValue, StandardCharsets.UTF_8.toString()).replace("+", "%20")

fun AIcAlgitesRepsyLoginUrl(aEndpointUrl: String): String {
    val locMarker = "/api/"
    val locMarkerIndex = aEndpointUrl.indexOf(locMarker)
    if (locMarkerIndex < 0) {
        throw GradleException(
            "Repsy manage URL '$aEndpointUrl' must contain '/api/' so the adapter can derive the Repsy authentication endpoint."
        )
    }
    return aEndpointUrl.substring(0, locMarkerIndex).trimEnd('/') + "/api/auth/login"
}

@Suppress("UNCHECKED_CAST")
fun AIcAlgitesRepsyHeaders(
    aEndpoint: AIcdAlgitesResourceEndpoint,
    aProfiles: Map<String, AIcdAlgitesCredentialProfile>
): Map<String, String> {
    val locProfileId = aEndpoint.credentialProfile
        ?: throw GradleException("Repsy manage endpoint '${aEndpoint.id}' requires a credentialProfile.")
    val locProfile = aProfiles[locProfileId]
        ?: throw GradleException("Repsy manage endpoint '${aEndpoint.id}' references undefined credential profile '$locProfileId'.")
    val locToken = when (locProfile.type) {
        "bearer" -> AIcAlgitesCredentialValue(locProfile, "Token")
            ?: throw GradleException("Credential profile '$locProfileId' does not provide required token.")
        "basic" -> {
            val (locUsername, locPassword) = AIcAlgitesRequireBasicCredential(locProfile)
            val locLoginBody = JsonOutput.toJson(mapOf("username" to locUsername, "password" to locPassword))
            val (locStatus, locBody) = AIcAlgitesHttpJsonRequest(
                "POST",
                AIcAlgitesRepsyLoginUrl(aEndpoint.url),
                emptyMap(),
                locLoginBody
            )
            if (locStatus !in 200..299) {
                throw GradleException(
                    "Repsy authentication failed for endpoint '${aEndpoint.id}' with HTTP $locStatus: $locBody"
                )
            }
            val locParsed = JsonSlurper().parseText(locBody) as? Map<*, *>
            val locData = locParsed?.get("data") as? Map<*, *>
            locData?.get("token")?.toString()?.takeIf { it.isNotBlank() }
                ?: throw GradleException("Repsy authentication response for endpoint '${aEndpoint.id}' did not contain data.token.")
        }
        else -> throw GradleException(
            "Repsy manage endpoint '${aEndpoint.id}' uses credential type '${locProfile.type}'. " +
                "The Repsy management adapter supports 'basic' and 'bearer'."
        )
    }
    return mapOf("Authorization" to "Bearer $locToken")
}

fun AIcAlgitesDeleteRepsySnapshot(
    aEndpoint: AIcdAlgitesResourceEndpoint,
    aProfiles: Map<String, AIcdAlgitesCredentialProfile>,
    aPackageName: String,
    aVersion: String,
    aFormat: String,
    aGroupId: String?
): Int {
    val locBaseUrl = aEndpoint.url.trimEnd('/')
    val locExpectedSuffix = when (aFormat.lowercase()) {
        "maven" -> "/api/mvn/artifacts/"
        "python" -> "/api/pypi/packages/"
        else -> throw GradleException(
            "Repsy management adapter does not support package format '$aFormat' for endpoint '${aEndpoint.id}'."
        )
    }
    if (!locBaseUrl.contains(locExpectedSuffix)) {
        throw GradleException(
            "Repsy manage endpoint '${aEndpoint.id}' URL '$locBaseUrl' must identify the configured repository using " +
                "'$locExpectedSuffix<repoName>' for format '$aFormat'."
        )
    }
    val locDeleteUrl = when (aFormat.lowercase()) {
        "maven" -> {
            val locGroupId = aGroupId?.takeIf { it.isNotBlank() }
                ?: throw GradleException("Repsy Maven snapshot cleanup for '$aPackageName' requires an effective groupId.")
            "$locBaseUrl/${AIcAlgitesUrlPathSegment(locGroupId)}/${AIcAlgitesUrlPathSegment(aPackageName)}/versions/${AIcAlgitesUrlPathSegment(aVersion)}"
        }
        "python" ->
            "$locBaseUrl/${AIcAlgitesUrlPathSegment(aPackageName)}/releases/${AIcAlgitesUrlPathSegment(aVersion)}"
        else -> error("unreachable")
    }
    val locHeaders = AIcAlgitesRepsyHeaders(aEndpoint, aProfiles)
    val (locStatus, locBody) = AIcAlgitesHttpRequest("DELETE", locDeleteUrl, locHeaders)
    return when {
        locStatus in 200..299 -> 1
        locStatus == 404 -> 0
        else -> throw GradleException(
            "Repsy package deletion failed for '$aPackageName/$aVersion' at endpoint '${aEndpoint.id}' " +
                "with HTTP $locStatus: $locBody"
        )
    }
}

@Suppress("UNCHECKED_CAST")
fun AIcAlgitesDeleteCloudsmithSnapshot(
    aEndpoint: AIcdAlgitesResourceEndpoint,
    aProfiles: Map<String, AIcdAlgitesCredentialProfile>,
    aPackageName: String,
    aVersionSelector: String,
    aVersionPrefix: Boolean,
    aFormat: String
): Int {
    val locBaseUrl = aEndpoint.url.trimEnd('/') + "/"
    val locHeaders = AIcAlgitesCloudsmithHeaders(aEndpoint, aProfiles)
    val locVersionQuery = if (aVersionPrefix) {
        "version:^$aVersionSelector"
    } else {
        "version:^${aVersionSelector}\$"
    }
    val locQuery = "name:^${aPackageName}\$ AND $locVersionQuery AND format:$aFormat"
    val locEncodedQuery = URLEncoder.encode(locQuery, StandardCharsets.UTF_8.toString())
    val locMatches = mutableListOf<Map<*, *>>()
    var locPage = 1
    while (true) {
        val locListUrl = "${locBaseUrl}?page_size=500&page=$locPage&query=$locEncodedQuery"
        val (locStatus, locBody) = AIcAlgitesHttpRequest("GET", locListUrl, locHeaders)
        if (locStatus !in 200..299) {
            throw GradleException("Cloudsmith package lookup failed for endpoint '${aEndpoint.id}' with HTTP $locStatus: $locBody")
        }
        val locParsed = JsonSlurper().parseText(locBody)
        val locItems = when (locParsed) {
            is List<*> -> locParsed
            is Map<*, *> -> (locParsed["results"] as? List<*>) ?: (locParsed["data"] as? List<*>) ?: emptyList<Any?>()
            else -> emptyList<Any?>()
        }
        locMatches += locItems.mapNotNull { it as? Map<*, *> }.filter { locItem ->
            val locVersion = locItem["version"]?.toString() ?: return@filter false
            locItem["name"]?.toString() == aPackageName &&
                (if (aVersionPrefix) locVersion.startsWith(aVersionSelector) else locVersion == aVersionSelector) &&
                locItem["format"]?.toString()?.equals(aFormat, ignoreCase = true) == true
        }
        if (locItems.size < 500) break
        locPage++
    }

    var locDeleted = 0
    locMatches.forEach { locItem ->
        val locVersion = locItem["version"]?.toString() ?: aVersionSelector
        val locIdentifier = locItem["slug_perm"]?.toString()?.takeIf { it.isNotBlank() }
            ?: locItem["identifier_perm"]?.toString()?.takeIf { it.isNotBlank() }
            ?: throw GradleException("Cloudsmith package '$aPackageName/$locVersion' did not expose a permanent identifier.")
        val (locDeleteStatus, locDeleteBody) = AIcAlgitesHttpRequest("DELETE", "$locBaseUrl$locIdentifier/", locHeaders)
        if (locDeleteStatus !in setOf(204, 404)) {
            throw GradleException(
                "Cloudsmith package deletion failed for '$aPackageName/$locVersion' at endpoint '${aEndpoint.id}' " +
                    "with HTTP $locDeleteStatus: $locDeleteBody"
            )
        }
        if (locDeleteStatus == 204) locDeleted++
    }
    return locDeleted
}

@Suppress("UNCHECKED_CAST")
val algitesResolvedRepositoryMetadata = rootProject.extra["algitesResolvedRepositoryMetadata"] as Map<String, Any?>

@Suppress("UNCHECKED_CAST")
val algitesResolvedArtifactDirectoriesByGradleProjectPath =
    rootProject.extra["algitesResolvedArtifactDirectoriesByGradleProjectPath"] as Map<String, Map<String, Any?>>

fun algitesResolvedArtifactDirectoryForProject(aProjectPath: String): Map<String, Any?>? {
    return algitesResolvedArtifactDirectoriesByGradleProjectPath[aProjectPath]
}

@Suppress("UNCHECKED_CAST")
fun algitesResolvedVersionValue(aArtifactDirectory: Map<String, Any?>?): String? {
    val locVersion = aArtifactDirectory?.get("version") as? Map<String, Any?>
    return locVersion?.get("resolvedValue")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
}


@Suppress("UNCHECKED_CAST")
fun AIcAlgitesDependencyDefinitions(aArtifactDirectory: Map<String, Any?>?, aPropertyName: String): List<Map<String, Any?>> =
    (aArtifactDirectory?.get(aPropertyName) as? List<Map<String, Any?>>).orEmpty()

fun AIcAlgitesDependencyUsageToGradleConfiguration(aUsage: String): String = when (aUsage) {
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

fun AIcAlgitesDependencyUsages(aDefinition: Map<String, Any?>): List<String> {
    val locUsages = AIcAlgitesStringList(aDefinition["usages"]).distinct()
    return if (locUsages.isEmpty()) listOf("product_implementation") else locUsages
}

fun AIcAlgitesRequiredBuildOutputTypes(aDefinition: Map<String, Any?>): List<String> =
    AIcAlgitesStringList(aDefinition["requiredBuildOutputTypes"]).distinct()

fun AIcValidatePhase2DependencyOutputs(
    aDefinition: Map<String, Any?>,
    aTechnologyKind: String,
    aContext: String
) {
    val locOutputs = AIcAlgitesRequiredBuildOutputTypes(aDefinition)
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

fun AIcAlgitesPythonEffectiveUsages(aDefinition: Map<String, Any?>, aContext: String): List<String> {
    val locUsages = AIcAlgitesDependencyUsages(aDefinition)
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

fun AIcAlgitesPythonParticipatesInResolution(aUsage: String): Boolean =
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
        if ("python" !in AIcAlgitesStringList(locTargetMetadata["technologyKinds"])) {
            throw GradleException("Modustro dependency '$locArtifactId' for '$aConsumerProjectPath' targets '${locTargetProject.path}', which does not provide TechnologyKind 'python'.")
        }
        val locTargetGroupId = locTargetMetadata["groupId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            ?: algitesResolvedRepositoryMetadata["groupId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
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
    val locMatches = algitesResolvedArtifactDirectoriesByGradleProjectPath.entries.mapNotNull { (locProjectPath, locMetadata) ->
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

@Suppress("UNCHECKED_CAST")
fun AIcAlgitesEnvironmentRequirement(aArtifactDirectory: Map<String, Any?>?, aEnvironment: String): Map<String, Any?>? =
    (aArtifactDirectory?.get("environmentRequirements") as? Map<String, Map<String, Any?>>)?.get(aEnvironment.lowercase())

fun AIcAlgitesPreferredEnvironmentMajorVersion(aRequirement: Map<String, Any?>?, aContext: String): Int? {
    if (aRequirement == null) return null
    val locVersionText = aRequirement["prefer"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        ?: aRequirement["exact"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        ?: (aRequirement["minimum"] as? Map<*, *>)?.get("version")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        ?: return null
    val locMajor = Regex("^[0-9]+").find(locVersionText)?.value?.toIntOrNull()
        ?: throw GradleException("$aContext version '$locVersionText' does not start with a numeric major version.")
    return locMajor
}

fun AIcConfigureAlgitesJavaEnvironment(aProject: Project, aArtifactDirectory: Map<String, Any?>) {
    val locRequirement = AIcAlgitesEnvironmentRequirement(aArtifactDirectory, "java") ?: return
    val locMajor = AIcAlgitesPreferredEnvironmentMajorVersion(locRequirement, "Project '${aProject.path}' EnvironmentRequirements.Java") ?: return
    aProject.extensions
        .getByType(JavaPluginExtension::class.java)
        .toolchain.languageVersion.set(JavaLanguageVersion.of(locMajor))
}

fun AIcConfigureAlgitesJavaDependencies(aProject: Project, aArtifactDirectory: Map<String, Any?>) {
    val locConsumerTechnologyKinds = AIcAlgitesStringList(aArtifactDirectory["technologyKinds"]).toSet()
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

            val locUsages = AIcAlgitesDependencyUsages(locDefinition)
            locUsages.forEach { locUsage ->
                val locConfiguration = AIcAlgitesDependencyUsageToGradleConfiguration(locUsage)
                locRequiredConfigurations.putIfAbsent(locConfiguration, locUsage)
                locEntriesByConfiguration.getOrPut(locConfiguration) { mutableListOf() }.add(locDefinition to aConstraintOnly)
            }
        }
    }

    locCollect(AIcAlgitesDependencyDefinitions(aArtifactDirectory, "dependencies"), false)
    locCollect(AIcAlgitesDependencyDefinitions(aArtifactDirectory, "dependencyConstraints"), true)

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
                        val locTargetTechnologyKinds = AIcAlgitesStringList(locTargetMetadata["technologyKinds"]).toSet()
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

fun requireAlgitesGroupForPublish(aProjectPath: String, aProjectGroup: Any?) {
    val locGroupText = aProjectGroup?.toString()?.trim()

    if (locGroupText.isNullOrBlank() || locGroupText == "unspecified") {
        throw GradleException(
            "Project '$aProjectPath' is being published, but no Maven group could be resolved. " +
                "Define top-level groupId in algites-source-repository.yml, algites-artifact-set.yml, or algites-artifact.yml."
        )
    }
}

val algitesRepositoryVisibility = algitesResolvedRepositoryMetadata["visibility"]
    ?.toString()
    ?.lowercase()
    ?.takeIf { it.isNotBlank() }
    ?: throw GradleException("Algites source repository visibility could not be resolved.")

val algitesRequestedRepositoryVisibility = algitesGradleOrEnvironmentValue("ALGITES_VISIBILITY")
    ?.lowercase()
    ?.takeIf { it.isNotBlank() }
if (algitesRequestedRepositoryVisibility != null && algitesRequestedRepositoryVisibility != algitesRepositoryVisibility) {
    throw GradleException(
        "Requested Algites visibility '$algitesRequestedRepositoryVisibility' does not match " +
            "source repository visibility '$algitesRepositoryVisibility'."
    )
}

val algitesPublicationRepositoryVisibility = when (algitesRepositoryVisibility) {
    "pub" -> "public"
    "priv" -> "private"
    else -> throw GradleException("Unsupported Algites repository visibility '$algitesRepositoryVisibility'.")
}

val algitesDocsPagesBranch = algitesGradleOrEnvironmentValue("ALGITES_DOCS_PAGES_BRANCH") ?: "gh-pages"
val algitesIsCi = providers.environmentVariable("CI")
    .map { locValue -> locValue.equals("true", ignoreCase = true) }
    .orElse(false)
    .get()

val algitesRequestedTechnologyKinds = (
    algitesGradleOrEnvironmentValue("ALGITES_TECHNOLOGY_KINDS")
        ?: algitesGradleOrEnvironmentValue("algites.technologyKinds")
)
    ?.split(',')
    ?.map { it.trim().lowercase() }
    ?.filter { it.isNotBlank() }
    ?.toSet()
    ?: emptySet()

val algitesRequestedTasks = gradle.startParameter.taskNames
val algitesIsPublishRequested = algitesRequestedTasks.any { locTaskName ->
    locTaskName == "publish" ||
        locTaskName.startsWith("publish") ||
        locTaskName.contains("publish", ignoreCase = true)
}

val algitesSnapshotInstanceId = AIcAlgitesSnapshotInstanceId()

val algitesDependencyPreflight = tasks.register("algitesDependencyPreflight") {
    group = "verification"
    description = "Resolves dependency graphs for all effective technologies before compilation or packaging starts."
}

allprojects {
    val locAlgitesRunDirectoryRelativePath = AIcAlgitesRunDirectoryRelativePath(project.projectDir)
    extra["algitesRunDirectoryRelativePath"] = locAlgitesRunDirectoryRelativePath
    layout.buildDirectory.set(
        rootProject.layout.projectDirectory.dir("$locAlgitesRunDirectoryRelativePath/bld/gradle")
    )

    val locAlgitesArtifactDirectory = algitesResolvedArtifactDirectoryForProject(project.path)
    val locAlgitesResolvedProjectGroup = locAlgitesArtifactDirectory?.get("groupId")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        ?: algitesResolvedRepositoryMetadata["groupId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }

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
        extra["algitesResolvedProjectGroup"] = locAlgitesResolvedProjectGroup
    }

    version = algitesResolvedVersionValue(locAlgitesArtifactDirectory)
        ?: algitesResolvedVersionValue(algitesResolvedArtifactDirectoryForProject(":"))
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

    tasks.withType<Test>().configureEach {
        useTestNG()
    }
}

allprojects {
    val locAlgitesArtifactDirectory = algitesResolvedArtifactDirectoryForProject(project.path)
    if (locAlgitesArtifactDirectory != null) {
        val locAlgitesTechnologyKinds = AIcAlgitesStringList(locAlgitesArtifactDirectory["technologyKinds"]).toSet()
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
    val locAlgitesArtifactDirectory = algitesResolvedArtifactDirectoryForProject(project.path)
    if (locAlgitesArtifactDirectory != null) {
        plugins.withId("java") {
            AIcConfigureAlgitesJavaEnvironment(project, locAlgitesArtifactDirectory)
            AIcConfigureAlgitesJavaDependencies(project, locAlgitesArtifactDirectory)
            val locJavaDependencyPreflight = tasks.register<AIcResolveJavaDependenciesTask>("resolveJavaDependencies") {
                group = "verification"
                description = "Resolves the Java dependency graph for this Algites artifact before compilation starts."
                dependencyFiles.from(
                    listOf("compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath")
                        .mapNotNull { locName -> configurations.findByName(locName) }
                        .filter { locConfiguration -> locConfiguration.isCanBeResolved }
                )
            }
        }
    }
}

val algitesPrepareDevelopment = tasks.register("prepareDevelopment") {
    group = "algites"
    description = "Generates effective development metadata required by supported technology kinds."
}

val algitesRefreshDevelopment = tasks.register("refreshDevelopment") {
    group = "algites"
    description = "Forces regeneration of effective development metadata required by supported technology kinds."
}

val algitesBuild = tasks.register("algitesBuild") {
    group = "algites"
    description = "Builds all effective or explicitly selected Algites TechnologyKinds."
}

val algitesBuiltinBuildOutputProducers = AIcBuiltinBuildOutputProducers()
val algitesBuiltinCapabilityDemandPlanner = AIcBuiltinCapabilityDemandPlanner()

algitesBuild.configure {
    dependsOn(algitesDependencyPreflight)
}

val locAlgitesPythonValidationArtifactPaths = algitesResolvedArtifactDirectoriesByGradleProjectPath.toSortedMap()
    .mapNotNull { (locProjectPath, locMetadata) ->
        if ("python" !in AIcAlgitesStringList(locMetadata["technologyKinds"])) {
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

val validateAlgitesPythonDistributionPaths = tasks.register<AIcValidatePythonDistributionPathsTask>("validateAlgitesPythonDistributionPaths") {
    group = "verification"
    description = "Validates Python product source roots, package-resource paths, and shared PEP 420 namespaces across distributions."
    repositoryDirectory.set(rootProject.layout.projectDirectory)
    pythonArtifactRelativePaths.set(locAlgitesPythonValidationArtifactPaths)
    pythonArtifactImportNamespaces.set(locAlgitesPythonValidationImportNamespaces)
}

algitesBuild.configure {
    dependsOn(validateAlgitesPythonDistributionPaths)
}

val algitesPublicationBuildGate = tasks.register("algitesPublicationBuildGate") {
    group = "publishing"
    description = "Requires all effective or explicitly selected Algites TechnologyKinds to build successfully before any publication task may start."
    dependsOn(algitesBuild)
}

val algitesPublish = tasks.register("algitesPublish") {
    group = "publishing"
    description = "Publishes all effective or explicitly selected Algites TechnologyKinds after the common publication build gate succeeds."
    dependsOn(algitesPublicationBuildGate)
}

val algitesValidateReleaseTechnologyKinds = tasks.register("validateAlgitesReleaseTechnologyKinds") {
    group = "algites"
    description = "Validates that a release TechnologyKind selection is complete unless incomplete release was explicitly allowed."

    doLast {
        val locAllowIncomplete = (
            algitesGradleOrEnvironmentValue("algites.release.allowIncompleteTechnologyKinds")
                ?: System.getenv("ALGITES_RELEASE_ALLOW_INCOMPLETE_TECHNOLOGY_KINDS")
                ?: "false"
            ).toBooleanStrictOrNull()
            ?: throw GradleException("algites.release.allowIncompleteTechnologyKinds must be true or false.")

        val locIncompleteArtifacts = mutableListOf<String>()
        algitesResolvedArtifactDirectoriesByGradleProjectPath.toSortedMap().forEach { (locProjectPath, locMetadata) ->
            val locDeclaredTechnologyKinds = AIcAlgitesStringList(locMetadata["technologyKinds"]).toSet()
            if (locDeclaredTechnologyKinds.isEmpty()) return@forEach

            val locSelectedTechnologyKinds = if (algitesRequestedTechnologyKinds.isEmpty()) {
                locDeclaredTechnologyKinds
            } else {
                locDeclaredTechnologyKinds.intersect(algitesRequestedTechnologyKinds)
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


abstract class AIcResolveAlgitesRequiredCredentialsTask : DefaultTask() {
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
                    "Declare Artifact.TechnologyKinds in algites-artifact.yml or explicitly select a valid TechnologyKind."
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

val locAlgitesRequiredCredentialsPlan = run {
    val locRequestedUsages = (
        algitesGradleOrEnvironmentValue("algites.credential.usages")
            ?: System.getenv("ALGITES_CREDENTIAL_USAGES")
            ?: "download"
        ).split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
    val locDownloadStabilities = (
        algitesGradleOrEnvironmentValue("algites.credential.download.stabilities")
            ?: System.getenv("ALGITES_CREDENTIAL_DOWNLOAD_STABILITIES")
            ?: "release,snapshot"
        ).split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
    val locUploadStabilities = (
        algitesGradleOrEnvironmentValue("algites.credential.upload.stabilities")
            ?: System.getenv("ALGITES_CREDENTIAL_UPLOAD_STABILITIES")
            ?: "release,snapshot"
        ).split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
    val locManageStabilities = (
        algitesGradleOrEnvironmentValue("algites.credential.manage.stabilities")
            ?: System.getenv("ALGITES_CREDENTIAL_MANAGE_STABILITIES")
            ?: "release,snapshot"
        ).split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()

    val locSupportedUsages = setOf("download", "upload", "manage")
    val locSupportedStabilities = setOf("release", "snapshot")
    if (!locSupportedUsages.containsAll(locRequestedUsages)) {
        throw GradleException("Unsupported credential usage. Supported values: download, upload, manage.")
    }
    if (!locSupportedStabilities.containsAll(locDownloadStabilities + locUploadStabilities + locManageStabilities)) {
        throw GradleException("Unsupported credential stability. Supported values: release, snapshot.")
    }

    val locDownloadVisibilities = when (algitesRepositoryVisibility) {
        "pub" -> setOf("public")
        "priv" -> setOf("public", "private")
        else -> throw GradleException("Unsupported Algites repository visibility '$algitesRepositoryVisibility'.")
    }
    val locUploadVisibilities = setOf(algitesPublicationRepositoryVisibility)
    val locManageVisibilities = setOf(algitesPublicationRepositoryVisibility)
    val locDeclaredOperationTechnologyKinds = algitesResolvedArtifactDirectoriesByGradleProjectPath.values
        .flatMap { locMetadata -> AIcAlgitesStringList(locMetadata["technologyKinds"]) }
        .toSet()
    val locOperationTechnologyKinds = if (algitesRequestedTechnologyKinds.isNotEmpty()) {
        locDeclaredOperationTechnologyKinds.intersect(algitesRequestedTechnologyKinds)
    } else {
        locDeclaredOperationTechnologyKinds
    }
    val locResourceEndpoints = linkedMapOf<String, Map<String, String?>>()
    val locCredentials = linkedMapOf<String, Map<String, String>>()

    fun AIcCollect(aMetadata: Map<String, Any?>, aScope: String) {
        val locScopeTechnologyKinds = AIcAlgitesStringList(aMetadata["technologyKinds"]).toSet()
        val locResourceEndpointMap = aMetadata["resourceEndpoints"] as? Map<*, *> ?: return
        val locProfiles = AIcAlgitesCredentialProfiles(aMetadata["credentialProfiles"])

        locResourceEndpointMap.keys.mapNotNull { it?.toString() }.sorted().forEach resourceEndpointCellLoop@ { locCell ->
            val locSegments = locCell.split('.')
            if (locSegments.size != 4) return@resourceEndpointCellLoop
            val (locTechnology, locResourceKind, locVisibility, locAction) = locSegments
            if (locResourceKind != "native_build_output") return@resourceEndpointCellLoop
            if (locAction !in locRequestedUsages) return@resourceEndpointCellLoop
            if (locAction == "download" && locVisibility !in locDownloadVisibilities) return@resourceEndpointCellLoop
            if (locAction == "upload" && locVisibility !in locUploadVisibilities) return@resourceEndpointCellLoop
            if (locAction == "manage" && locVisibility !in locManageVisibilities) return@resourceEndpointCellLoop
            if (locAction == "manage" && aScope == "repository") return@resourceEndpointCellLoop
            if (locAction == "manage" && aMetadata["deleteSnapshotWhenReleased"]?.toString()?.toBooleanStrictOrNull() == false) return@resourceEndpointCellLoop
            if (locTechnology !in locOperationTechnologyKinds) return@resourceEndpointCellLoop
            if (locScopeTechnologyKinds.isNotEmpty() && locTechnology !in locScopeTechnologyKinds) return@resourceEndpointCellLoop

            AIcAlgitesResourceEndpoints(aMetadata["resourceEndpoints"], locCell).forEach resourceEndpointLoop@ { locEndpoint ->
                val locStability = locEndpoint.stability ?: return@resourceEndpointLoop
                if (locAction == "download" && locStability !in locDownloadStabilities) return@resourceEndpointLoop
                if (locAction == "upload" && locStability !in locUploadStabilities) return@resourceEndpointLoop
                if (locAction == "manage" && locStability !in locManageStabilities) return@resourceEndpointLoop
                val locProfileId = locEndpoint.credentialProfile
                val locProfile = if (locProfileId.isNullOrBlank()) null else locProfiles[locProfileId]
                    ?: throw GradleException(
                        "ResourceEndpoint '${locEndpoint.id}' references undefined credential profile '$locProfileId'."
                    )
                val locEndpointKey = "$aScope|$locCell|$locStability|${locEndpoint.id}"
                locResourceEndpoints[locEndpointKey] = linkedMapOf(
                    "scope" to aScope,
                    "cell" to locCell,
                    "stability" to locStability,
                    "id" to locEndpoint.id,
                    "credentialProfile" to locProfileId,
                    "credentialType" to locProfile?.type
                )
                if (locProfile != null) {
                    val locCredentialKey = "${locProfile.id}|${locProfile.type}"
                    locCredentials[locCredentialKey] = linkedMapOf(
                        "profileId" to locProfile.id,
                        "type" to locProfile.type
                    )
                }
            }
        }
    }

    AIcCollect(algitesResolvedRepositoryMetadata, "repository")
    algitesResolvedArtifactDirectoriesByGradleProjectPath.toSortedMap().forEach { (locPath, locMetadata) ->
        AIcCollect(locMetadata, "artifact:$locPath")
    }

    val locPlan = linkedMapOf<String, Any>(
        "resourceEndpoints" to locResourceEndpoints.values.toList(),
        "credentials" to locCredentials.values.toList()
    )
    Triple(JsonOutput.toJson(locPlan), locCredentials.size, locOperationTechnologyKinds.sorted())
}

val algitesResolveRequiredCredentials = tasks.register<AIcResolveAlgitesRequiredCredentialsTask>("resolveAlgitesRequiredCredentials") {
    group = "algites"
    description = "Resolves enabled ResourceEndpoints and the credential profiles required by the selected operation context."

    planJson.set(locAlgitesRequiredCredentialsPlan.first)
    credentialCount.set(locAlgitesRequiredCredentialsPlan.second)
    technologyKinds.set(locAlgitesRequiredCredentialsPlan.third)

    val locOutputPath = algitesGradleOrEnvironmentValue("algites.credential.output")
        ?: System.getenv("ALGITES_CREDENTIAL_OUTPUT")
    if (!locOutputPath.isNullOrBlank()) {
        outputFile.set(File(locOutputPath))
    }
}

subprojects {
    val locAlgitesRunDirectoryRelativePath = AIcAlgitesRunDirectoryRelativePath(project.projectDir)
    val locAlgitesArtifactDirectory = algitesResolvedArtifactDirectoryForProject(project.path)
    val locAlgitesResolvedProjectGroup = locAlgitesArtifactDirectory?.get("groupId")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        ?: algitesResolvedRepositoryMetadata["groupId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
    val locAlgitesTechnologyKinds = AIcAlgitesStringList(locAlgitesArtifactDirectory?.get("technologyKinds"))
    val locEffectiveTechnologyKinds = if (algitesRequestedTechnologyKinds.isEmpty()) {
        locAlgitesTechnologyKinds.toSet()
    } else {
        locAlgitesTechnologyKinds.filter { it in algitesRequestedTechnologyKinds }.toSet()
    }

    fun locBuildOutputPlans(aTechnologyKind: String) = if (aTechnologyKind !in locAlgitesTechnologyKinds) {
        emptyList()
    } else {
        val locPreparedSourceSet = AIcAlgitesPreparedSourceSet(project, aTechnologyKind)
        val locExplicitBuildOutputTypes = AIcAlgitesExplicitBuildOutputTypes(locAlgitesArtifactDirectory, aTechnologyKind)
        if (locExplicitBuildOutputTypes == null) {
            algitesBuiltinBuildOutputProducers.createDefaultProductionPlans(aTechnologyKind, locPreparedSourceSet)
        } else {
            algitesBuiltinBuildOutputProducers.createProductionPlans(aTechnologyKind, locExplicitBuildOutputTypes, locPreparedSourceSet)
        }
    }

    val locJavaBuildOutputPlans = if ("java" in locAlgitesTechnologyKinds) locBuildOutputPlans("java") else emptyList()
    val locJavaProductionKinds = locJavaBuildOutputPlans.map { locPlan -> locPlan.productionKind() }.toSet()
    val locPythonBuildOutputPlans = if ("python" in locAlgitesTechnologyKinds) locBuildOutputPlans("python") else emptyList()
    val locPythonProductionKinds = locPythonBuildOutputPlans.map { locPlan -> locPlan.productionKind() }.toSet()
    val locBuildOutputPlans = locJavaBuildOutputPlans + locPythonBuildOutputPlans
    val locCapabilityDemandGraph = algitesBuiltinCapabilityDemandPlanner.createDemandGraph(
        locAlgitesArtifactDirectory?.get("path")?.toString()?.takeIf { it.isNotBlank() } ?: project.path,
        locBuildOutputPlans,
        emptyList()
    )

    fun locHasCapabilityDemand(aTechnologyKind: String, aCapabilityId: String): Boolean =
        locCapabilityDemandGraph.demands().any { locDemand ->
            locDemand.key().technologyKind() == aTechnologyKind && locDemand.key().capabilityId() == aCapabilityId
        }

    extra["algitesBuildCapabilityDemandIds"] = locCapabilityDemandGraph.topologicalOrder()
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
        tasks.register("processAlgitesJavaNativeSources") {
            group = "algites"
            description = "Materializes the Java source_native_processing capability boundary for this artifact."
            val locPrepared = AIcAlgitesPreparedSourceSet(project, "java")
            inputs.files((locPrepared.nativeSourceRoots() + locPrepared.generatedSourceRoots() + locPrepared.resourceRoots()).map(project::file))
        }
    } else {
        null
    }
    val locPythonSourceProcessingTask = if ("python" in locAlgitesTechnologyKinds) {
        tasks.register("processAlgitesPythonNativeSources") {
            group = "algites"
            description = "Materializes the Python source_native_processing capability boundary for this artifact."
            val locPrepared = AIcAlgitesPreparedSourceSet(project, "python")
            inputs.files((locPrepared.nativeSourceRoots() + locPrepared.generatedSourceRoots() + locPrepared.resourceRoots()).map(project::file))
        }
    } else {
        null
    }

    @Suppress("UNCHECKED_CAST")
    val locDefinitionCodeGeneration = (locAlgitesArtifactDirectory?.get("definitionCodeGeneration") as? List<Map<String, Any?>>).orEmpty()
    val locDefinitionCodeGenerationTargets = locDefinitionCodeGeneration
        .flatMap { locEntry -> AIcAlgitesStringList(locEntry["targets"]) }
        .toSet()
    val locUnsupportedDefinitionCodeGenerationTargets = locDefinitionCodeGenerationTargets - locAlgitesTechnologyKinds.toSet()
    if (locUnsupportedDefinitionCodeGenerationTargets.isNotEmpty()) {
        throw GradleException(
            "Artifact '${project.path}' DefinitionCodeGeneration targets ${locUnsupportedDefinitionCodeGenerationTargets.sorted()} " +
                "but those TechnologyKinds are not selected by the artifact."
        )
    }
    val locDefinitionCodeGenerationManifestDirectory =
        rootProject.layout.projectDirectory.dir("$locAlgitesRunDirectoryRelativePath/defscodegen")
    val locDefinitionCodeGenerationStaleTargets = listOf("java", "python")
        .filter { locTarget -> locDefinitionCodeGenerationManifestDirectory.file("$locTarget.manifest").asFile.isFile }
        .toSet()
    val locDefinitionCodeGenerationExecutionTargets =
        locDefinitionCodeGenerationTargets + locDefinitionCodeGenerationStaleTargets
    val locDefinitionCodeGenerationTask = if (locDefinitionCodeGeneration.isNotEmpty() || locDefinitionCodeGenerationStaleTargets.isNotEmpty()) {
        tasks.register<AIcGenerateAlgitesDefinitionSourcesTask>("generateAlgitesDefinitionSources") {
            group = "algites"
            description = "Generates configured native source types from canonical yamldefs/jsondefs/xmldefs definitions."
            artifactDirectory.set(project.layout.projectDirectory)
            manifestDirectory.set(locDefinitionCodeGenerationManifestDirectory)
            javaOutputDirectory.set(project.layout.projectDirectory.dir("src/product/java.gen"))
            pythonOutputDirectory.set(project.layout.projectDirectory.dir("src/product/python.gen"))
            entries.set(locDefinitionCodeGeneration.mapIndexed { locIndex, locEntry ->
                val locSourceKind = locEntry["sourceKind"]?.toString()?.trim()?.lowercase()
                    ?: throw GradleException("Artifact '${project.path}' DefinitionCodeGeneration[$locIndex] is missing sourceKind.")
                val locSource = locEntry["source"]?.toString()?.trim()
                    ?: throw GradleException("Artifact '${project.path}' DefinitionCodeGeneration[$locIndex] is missing source.")
                val locTargets = AIcAlgitesStringList(locEntry["targets"])
                val locPackage = locEntry["package"]?.toString()?.trim()
                    ?: throw GradleException("Artifact '${project.path}' DefinitionCodeGeneration[$locIndex] is missing package.")
                val locNamingProfile = locEntry["namingProfile"]?.toString()?.trim()?.lowercase() ?: "algites"
                listOf(locSourceKind, locSource, locTargets.joinToString(","), locPackage, locNamingProfile).joinToString("\t")
            })
            sourceFiles.from(locDefinitionCodeGeneration.map { locEntry ->
                project.file(locEntry["source"]?.toString() ?: "")
            })
        }
    } else {
        null
    }
    if ("java" in locDefinitionCodeGenerationExecutionTargets) {
        locJavaSourceProcessingTask?.configure {
            locDefinitionCodeGenerationTask?.let { locTask -> dependsOn(locTask) }
        }
    }
    if ("python" in locDefinitionCodeGenerationExecutionTargets) {
        locPythonSourceProcessingTask?.configure {
            locDefinitionCodeGenerationTask?.let { locTask -> dependsOn(locTask) }
        }
    }

    if ("java" in locEffectiveTechnologyKinds && locHasCapabilityDemand("java", "dependency_resolution")) {
        tasks.matching { locTask -> locTask.name == "resolveJavaDependencies" }.configureEach {
            val locDependencyResolutionTask = this
            rootProject.tasks.named("algitesDependencyPreflight").configure {
                dependsOn(locDependencyResolutionTask)
            }
        }
    }
    if ("python" in locEffectiveTechnologyKinds && locHasCapabilityDemand("python", "dependency_resolution")) {
        tasks.matching { locTask -> locTask.name == "resolvePythonDependencies" }.configureEach {
            val locDependencyResolutionTask = this
            rootProject.tasks.named("algitesDependencyPreflight").configure {
                dependsOn(locDependencyResolutionTask)
            }
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

    val locEffectiveResourceEndpoints = locAlgitesArtifactDirectory?.get("resourceEndpoints")
    val locEffectiveCredentialProfiles = AIcAlgitesCredentialProfiles(locAlgitesArtifactDirectory?.get("credentialProfiles"))

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
    val locAlgitesManifestOutputFile = layout.buildDirectory.file("algites/manifest/algites-artifact-manifest.yml")
    val locGenerateAlgitesArtifactManifest = tasks.register<AIcGenerateAlgitesArtifactManifestTask>("generateAlgitesArtifactManifest") {
        group = "algites"
        description = "Generates the deterministic Algites artifact manifest for this logical artifact."

        repositoryId.set(algitesResolvedRepositoryMetadata["id"]?.toString()?.takeIf { it.isNotBlank() } ?: rootProject.name)
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
        outputFile.set(locAlgitesManifestOutputFile)
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
                algitesBuild.configure { dependsOn(tasks.named("check")) }
                if (AInBuildOutputProductionKind.JAVA_CLASSES_JAR in locJavaProductionKinds) {
                    algitesBuild.configure { dependsOn(tasks.named("jar")) }
                }
                if (AInBuildOutputProductionKind.JAVA_SOURCES_JAR in locJavaProductionKinds) {
                    algitesBuild.configure { dependsOn(tasks.named("sourcesJar")) }
                }
                if (AInBuildOutputProductionKind.JAVA_JAVADOC_JAR in locJavaProductionKinds) {
                    algitesBuild.configure { dependsOn(tasks.named("javadocJar")) }
                }
            }

            tasks.withType(Jar::class.java).configureEach {
                dependsOn(rootProject.tasks.named("verifyAlgitesLicensing"))
                dependsOn(locGenerateAlgitesArtifactManifest)
                from(locGenerateAlgitesArtifactManifest.flatMap { it.outputFile }) {
                    into("META-INF/algites")
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
            if (algitesIsPublishRequested) {
                requireAlgitesGroupForPublish(project.path, locAlgitesResolvedProjectGroup)
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

                repositories {
                    val locIsSnapshot = locAlgitesProjectVersion.endsWith("SNAPSHOT", ignoreCase = true)
                    val locStability = if (locIsSnapshot) "snapshot" else "release"
                    val locCell = "java.native_build_output.$algitesPublicationRepositoryVisibility.upload"
                    val locEndpoints = AIcAlgitesResourceEndpoints(locEffectiveResourceEndpoints, locCell, locStability)

                    locEndpoints.forEach { locEndpoint ->
                        maven {
                            name = locEndpoint.id.replace('-', '_')
                            url = uri(locEndpoint.url)
                            val locProfileId = locEndpoint.credentialProfile
                            if (!locProfileId.isNullOrBlank() && !algitesCredentialPreflight) {
                                val locProfile = locEffectiveCredentialProfiles[locProfileId]
                                    ?: throw GradleException(
                                        "Repository endpoint '${locEndpoint.id}' references undefined credential profile '$locProfileId'."
                                    )
                                when (locProfile.type) {
                                    "basic" -> {
                                        val locCredential = AIcAlgitesRequireBasicCredential(locProfile)
                                        credentials {
                                            username = locCredential.first
                                            password = locCredential.second
                                        }
                                    }
                                    "bearer" -> {
                                        val locToken = AIcAlgitesCredentialValue(locProfile, "Token")
                                            ?: throw GradleException(
                                                "Credential profile '${locProfile.id}' type 'bearer' is not available in ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS or the local Algites secure-store credential document."
                                            )
                                        credentials(HttpHeaderCredentials::class) {
                                            name = "Authorization"
                                            value = "Bearer $locToken"
                                        }
                                        authentication { create<HttpHeaderAuthentication>("header") }
                                    }
                                    "api_key" -> {
                                        val locApiKey = AIcAlgitesCredentialValue(locProfile, "ApiKey")
                                            ?: throw GradleException(
                                                "Credential profile '${locProfile.id}' type 'api_key' is not available in ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS or the local Algites secure-store credential document."
                                            )
                                        val locHeaderName = locProfile.configuration["headerName"]?.takeIf { it.isNotBlank() }
                                            ?: throw GradleException(
                                                "Credential profile '${locProfile.id}' type 'api_key' requires configuration.headerName for Java/Maven publication."
                                            )
                                        credentials(HttpHeaderCredentials::class) {
                                            name = locHeaderName
                                            value = locApiKey
                                        }
                                        authentication { create<HttpHeaderAuthentication>("header") }
                                    }
                                    else -> throw GradleException(
                                        "Java/Maven upload endpoint '${locEndpoint.id}' uses credential type '${locProfile.type}', " +
                                            "which is not supported by the Java/Maven repository adapter."
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if ("java" in locEffectiveTechnologyKinds) {
            plugins.withId("maven-publish") {
                val locJavaPublishTask = tasks.named("publish")
                locJavaPublishTask.configure { dependsOn(algitesPublicationBuildGate) }
                algitesPublish.configure { dependsOn(locJavaPublishTask) }
            }
        }
    }

    if ("python" in locAlgitesTechnologyKinds) {
        val locPythonTemplateFile = layout.projectDirectory.file("pyproject.toml.tpl")
        val locPythonProjectFile = layout.projectDirectory.file("pyproject.toml")
        val locPythonDistributionName = AIcAlgitesPythonDistributionName(locAlgitesResolvedProjectGroup, locAlgitesEffectiveArtifactId)
        val locPythonImportNamespace = AIcAlgitesPythonImportNamespace(rootProject.name, locAlgitesSubprojectPathDots)

        val locAlgitesProjectRunDirectory = rootProject.layout.projectDirectory.dir(
            AIcAlgitesRunDirectoryRelativePath(project.projectDir)
        )
        val locPythonBuildProjectDirectory = locAlgitesProjectRunDirectory.dir("bld/python/project")
        val locPythonPreparedPackageResourcesDirectory = locAlgitesProjectRunDirectory.dir(
            "bld/python/source-native-processing/package-resources"
        )
        val locPythonPreparedSourceSet = AIcAlgitesPreparedSourceSet(project, "python")
        val locPythonImportNamespacePath = locPythonImportNamespace.replace('.', '/')
        val locStagePythonPackageResources = tasks.register<Sync>("stageAlgitesPythonPackageResources") {
            group = "algites"
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
            group = "algites"
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
            AIcAlgitesDependencyDefinitions(locAlgitesArtifactDirectory, aPropertyName)
                .filter { locDefinition -> locDefinition["dependencyKind"]?.toString() in setOf("modustro", "python") }

        val locPythonDependencyDefinitions = locPythonDefinitions("dependencies")
        val locPythonConstraintDefinitions = locPythonDefinitions("dependencyConstraints")
        locPythonDependencyDefinitions.forEachIndexed { locIndex, locDefinition ->
            val locContext = "Project '${project.path}' Dependencies[$locIndex]"
            AIcValidatePhase2DependencyOutputs(locDefinition, "python", locContext)
            AIcAlgitesPythonEffectiveUsages(locDefinition, locContext)
        }
        locPythonConstraintDefinitions.forEachIndexed { locIndex, locDefinition ->
            AIcAlgitesPythonEffectiveUsages(locDefinition, "Project '${project.path}' DependencyConstraints[$locIndex]")
        }
        val locPythonResolutionDependencies = locPythonDependencyDefinitions
            .filter { locDefinition -> AIcAlgitesDependencyUsages(locDefinition).any(::AIcAlgitesPythonParticipatesInResolution) }
            .mapIndexed { locIndex, locDefinition ->
                AIcAlgitesPythonResolutionEntry(locDefinition, project.path, "Project '${project.path}' Dependencies[$locIndex]")
            }
        val locPythonResolutionConstraints = locPythonConstraintDefinitions
            .filter { locDefinition -> AIcAlgitesDependencyUsages(locDefinition).any(::AIcAlgitesPythonParticipatesInResolution) }
            .mapIndexed { locIndex, locDefinition ->
                AIcAlgitesPythonResolutionEntry(locDefinition, project.path, "Project '${project.path}' DependencyConstraints[$locIndex]")
            }
        val locPythonPublishedDependencies = locPythonDependencyDefinitions
            .filter { locDefinition ->
                AIcAlgitesDependencyUsages(locDefinition).any { locUsage ->
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

        val locPythonDownloadEndpoints = listOf(
            "python.native_build_output.public.download",
            "python.native_build_output.private.download"
        ).flatMap { locCell -> AIcAlgitesResourceEndpoints(locEffectiveResourceEndpoints, locCell) }
        val locPythonDownloadEndpointDefinitions = locPythonDownloadEndpoints.map { locEndpoint ->
            val locProfileId = locEndpoint.credentialProfile.orEmpty()
            val locProfileType = if (locProfileId.isBlank()) "" else locEffectiveCredentialProfiles[locProfileId]?.type.orEmpty()
            listOf(locEndpoint.id, locEndpoint.url, locProfileId, locProfileType).joinToString("\t")
        }

        val locResolvePythonDependencies = tasks.register<AIcResolvePythonDependenciesTask>("resolvePythonDependencies") {
            group = "verification"
            description = "Runs the bounded Python dependency-resolution preflight for this Algites artifact."
            pythonExecutable.set(algitesGradleOrEnvironmentValue("ALGITES_PYTHON_EXECUTABLE") ?: "python3")
            projectPathValue.set(project.path)
            projectDirectory.set(layout.projectDirectory)
            dependencyDefinitions.set(locPythonResolutionDependencies)
            constraintDefinitions.set(locPythonResolutionConstraints)
            endpointDefinitions.set(locPythonDownloadEndpointDefinitions)
            credentialBaseDirectoryPath.set(rootProject.projectDir.absolutePath)
            if (locPythonTemplateFile.asFile.isFile) pyprojectTemplateFile.set(locPythonTemplateFile)
            selectedPhaseFile.set(layout.buildDirectory.file("algites/python/dependency-resolution-phase.txt"))
        }
        val locGeneratePythonProjectMetadata = tasks.register<AIcGeneratePythonProjectMetadataTask>("generatePythonProjectMetadata") {
            group = "algites"
            description = "Generates the effective pyproject.toml for this Algites Python artifact."
            dependsOn(rootProject.tasks.named("verifyAlgitesLicensing"))
            pythonExecutable.set(algitesGradleOrEnvironmentValue("ALGITES_PYTHON_EXECUTABLE") ?: "python3")
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
            group = "algites"
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
            manifest_name = "algites-artifact-manifest.yml"
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

            subprocess.run(build_command, cwd=project_dir, check=True)

            def inject_wheel(path):
                with zipfile.ZipFile(path, "r") as source:
                    infos = source.infolist()
                    record_infos = [info for info in infos if info.filename.endswith(".dist-info/RECORD")]
                    if len(record_infos) != 1:
                        raise RuntimeError(f"Expected exactly one .dist-info/RECORD in {path.name}")
                    record_info = record_infos[0]
                    dist_info = record_info.filename.rsplit("/", 1)[0]
                    archive_manifest = f"{dist_info}/META-INF/algites/{manifest_name}"
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
                    archive_manifest = f"{root}/META-INF/algites/{manifest_name}"

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
            description = "Builds Python wheel/source distribution and embeds the deterministic Algites artifact manifest."
            dependsOn(rootProject.tasks.named("algitesDependencyPreflight"))
            dependsOn(locPreparePythonBuildProject)
            dependsOn(locGenerateAlgitesArtifactManifest)
            workingDir(locPythonBuildProjectDirectory)
            commandLine(
                algitesGradleOrEnvironmentValue("ALGITES_PYTHON_EXECUTABLE") ?: "python3",
                "-c",
                locPythonBuildAndManifestScript,
                locPythonBuildProjectDirectory.asFile.absolutePath,
                locPythonDistDirectory.asFile.absolutePath,
                locAlgitesManifestOutputFile.get().asFile.absolutePath,
                locPythonBuildModes.joinToString(",")
            )
            inputs.dir(locPythonBuildProjectDirectory)
            inputs.file(locAlgitesManifestOutputFile)
            outputs.dir(locPythonDistDirectory)
        }

        val locPythonPublishStability = if (locAlgitesProjectVersion.endsWith("SNAPSHOT", ignoreCase = true)) "snapshot" else "release"
        val locPythonPublishCell = "python.native_build_output.$algitesPublicationRepositoryVisibility.upload"
        val locPythonPublishEndpoints = AIcAlgitesResourceEndpoints(locEffectiveResourceEndpoints, locPythonPublishCell, locPythonPublishStability)
        val locPythonPublishEndpointDefinitions = locPythonPublishEndpoints.map { locEndpoint ->
            val locProfileId = locEndpoint.credentialProfile.orEmpty()
            val locProfileType = if (locProfileId.isBlank()) "" else locEffectiveCredentialProfiles[locProfileId]?.type.orEmpty()
            listOf(locEndpoint.id, locEndpoint.url, locProfileId, locProfileType).joinToString("\t")
        }

        val locPublishPython = tasks.register<AIcPublishPythonTask>("publishPython") {
            group = "publishing"
            description = "Publishes Python distributions for this Algites artifact."
            dependsOn(algitesPublicationBuildGate)
            dependsOn(locBuildPython)
            pythonExecutable.set(algitesGradleOrEnvironmentValue("ALGITES_PYTHON_EXECUTABLE") ?: "python3")
            projectPathValue.set(locPythonProjectPath)
            projectVersionValue.set(locAlgitesProjectVersion)
            publicationStability.set(locPythonPublishStability)
            repositoryVisibility.set(algitesPublicationRepositoryVisibility)
            algitesSnapshotInstanceId?.let(snapshotInstanceId::set)
            endpointDefinitions.set(locPythonPublishEndpointDefinitions)
            credentialBaseDirectoryPath.set(rootProject.projectDir.absolutePath)
            distributionDirectory.set(locPythonDistDirectory)
        }

        algitesPrepareDevelopment.configure { dependsOn(locGeneratePythonProjectMetadata) }
        algitesRefreshDevelopment.configure { dependsOn(locRefreshPythonDevelopment) }
        locBuildPython.configure {
            dependsOn(rootProject.tasks.named("validateAlgitesPythonDistributionPaths"))
        }
        if ("python" in locEffectiveTechnologyKinds && locPythonBuildModes.isNotEmpty()) {
            algitesBuild.configure { dependsOn(locBuildPython) }
            algitesPublish.configure { dependsOn(locPublishPython) }
        }
    }
}


val algitesDeleteReleasedSnapshots = tasks.register("algitesDeleteReleasedSnapshots") {
    group = "publishing"
    description = "Deletes snapshot packages corresponding to a successfully published release according to effective Algites lifecycle policy."

    doLast {
        var locConfiguredTargets = 0
        var locDeletedPackages = 0
        algitesResolvedArtifactDirectoriesByGradleProjectPath.toSortedMap().forEach { (locProjectPath, locMetadata) ->
            val locDeclaredTechnologyKinds = AIcAlgitesStringList(locMetadata["technologyKinds"]).toSet()
            val locTechnologyKinds = if (algitesRequestedTechnologyKinds.isEmpty()) {
                locDeclaredTechnologyKinds
            } else {
                locDeclaredTechnologyKinds.intersect(algitesRequestedTechnologyKinds)
            }
            if (locTechnologyKinds.isEmpty()) return@forEach
            val locDeleteEnabled = locMetadata["deleteSnapshotWhenReleased"]?.toString()?.toBooleanStrictOrNull() ?: true
            if (!locDeleteEnabled) {
                logger.lifecycle("Skipping released-snapshot cleanup for '$locProjectPath': deleteSnapshotWhenReleased=false.")
                return@forEach
            }
            val locProject = rootProject.findProject(locProjectPath)
                ?: throw GradleException("Resolved Algites artifact project '$locProjectPath' is not present in the Gradle build.")
            val locReleaseVersion = algitesGradleOrEnvironmentValue("algites.cleanup.releaseVersion")
                ?: System.getenv("ALGITES_CLEANUP_RELEASE_VERSION")
                ?: locProject.version.toString()
            if (locReleaseVersion.endsWith("SNAPSHOT", ignoreCase = true)) {
                throw GradleException("Released-snapshot cleanup requires a release version, but '$locProjectPath' resolved '$locReleaseVersion'.")
            }
            val locResourceEndpoints = locMetadata["resourceEndpoints"]
            val locProfiles = AIcAlgitesCredentialProfiles(locMetadata["credentialProfiles"])
            val locGroupId = locMetadata["groupId"]?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" }
            val locArtifactBaseId = AIcAlgitesCanonicalArtifactId(locProjectPath)
            val locVariantId = locMetadata["variantId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            val locArtifactId = AIcAlgitesEffectiveArtifactId(locArtifactBaseId, locVariantId)

            locTechnologyKinds.sorted().forEach { locTechnology ->
                val locCell = "$locTechnology.native_build_output.$algitesPublicationRepositoryVisibility.manage"
                val locEndpoints = AIcAlgitesResourceEndpoints(locResourceEndpoints, locCell, "snapshot")
                if (locEndpoints.isEmpty()) {
                    logger.lifecycle("No enabled $locTechnology snapshot manage endpoint is configured for '$locProjectPath'; cleanup is skipped for this TechnologyKind.")
                    return@forEach
                }
                val locSnapshotVersion = AIcAlgitesSnapshotVersionForTechnology(locReleaseVersion, locTechnology)
                val locSnapshotVersionIsPrefix = locTechnology == "python"
                val locPackageName = when (locTechnology) {
                    "java" -> locArtifactId
                    "python" -> AIcAlgitesPythonDistributionName(locGroupId, locArtifactId)
                    else -> throw GradleException(
                        "Released-snapshot cleanup for TechnologyKind '$locTechnology' has no management package-coordinate adapter yet."
                    )
                }
                val locFormat = when (locTechnology) {
                    "java" -> "maven"
                    "python" -> "python"
                    else -> locTechnology
                }

                locEndpoints.forEach { locEndpoint ->
                    locConfiguredTargets++
                    val locDeleted = when (locEndpoint.resourceEndpointProviderAdapter) {
                        "cloudsmith" -> AIcAlgitesDeleteCloudsmithSnapshot(
                            locEndpoint,
                            locProfiles,
                            locPackageName,
                            locSnapshotVersion,
                            locSnapshotVersionIsPrefix,
                            locFormat
                        )
                        "repsy" -> {
                            if (locSnapshotVersionIsPrefix) {
                                logger.warn(
                                    "Repsy released-snapshot cleanup currently supports exact versions only; " +
                                        "timestamped Python snapshot series '$locSnapshotVersion*' for '$locProjectPath' must be cleaned manually " +
                                        "until the Repsy adapter has a confirmed release-list API endpoint."
                                )
                                0
                            } else {
                                AIcAlgitesDeleteRepsySnapshot(
                                    locEndpoint,
                                    locProfiles,
                                    locPackageName,
                                    locSnapshotVersion,
                                    locFormat,
                                    locGroupId
                                )
                            }
                        }
                        null -> throw GradleException("Manage endpoint '${locEndpoint.id}' has no ResourceEndpointProviderAdapter.")
                        else -> throw GradleException(
                            "Manage endpoint '${locEndpoint.id}' uses unsupported ResourceEndpointProviderAdapter '${locEndpoint.resourceEndpointProviderAdapter}'."
                        )
                    }
                    locDeletedPackages += locDeleted
                    logger.lifecycle(
                        "Released-snapshot cleanup endpoint '${locEndpoint.id}': package=$locPackageName " +
                            "versionSelector=${locSnapshotVersion}${if (locSnapshotVersionIsPrefix) "*" else ""} deleted=$locDeleted"
                    )
                }
            }
        }
        logger.lifecycle(
            "Algites released-snapshot cleanup completed: configuredTargets=$locConfiguredTargets deletedPackages=$locDeletedPackages"
        )
    }
}

tasks.register("printAlgitesDeploymentPlan") {
    group = "algites"
    description = "Prints the effective Algites deployment configuration."

    doLast {
        println("Algites deployment plan for ${rootProject.name}:")
        println(" - repository visibility: $algitesRepositoryVisibility")
        println(" - requested technology kinds: ${if (algitesRequestedTechnologyKinds.isEmpty()) "all effective technology kinds" else algitesRequestedTechnologyKinds.joinToString(",")}")
        println(" - docs pages branch: $algitesDocsPagesBranch")

        algitesResolvedArtifactDirectoriesByGradleProjectPath.toSortedMap().forEach { locEntry ->
            val locMetadata = locEntry.value
            println(" - ${locEntry.key}: technologyKinds=${AIcAlgitesStringList(locMetadata["technologyKinds"])}")
            val locResourceEndpoints = locMetadata["resourceEndpoints"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
            locResourceEndpoints.toSortedMap(compareBy { it.toString() }).forEach { (locCell, locEndpoints) ->
                println("     $locCell=$locEndpoints")
            }
        }
    }
}

tasks.register("ciHelp") {
    group = "algites"
    description = "Prints a small marker proving that the Algites root Gradle build was detected."

    doLast {
        println("Algites root Gradle build detected: ${rootProject.name}")
    }
}
