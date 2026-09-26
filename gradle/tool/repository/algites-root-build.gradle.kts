/*
 * Algites generic repository build conventions.
 *
 * Public entry-point location is stable. The implementation consumes the
 * effective metadata resolved from algites-source-repository.yml and nested
 * algites-artifact.yml files.
 */

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Delete
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.authentication.http.HttpHeaderAuthentication
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
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
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

abstract class AIcGenerateAlgitesArtifactManifestTask : DefaultTask() {
    @get:Input
    abstract val repositoryId: Property<String>

    @get:Input
    abstract val localArtifactId: Property<String>

    @get:Input
    abstract val artifactCoordinateId: Property<String>

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
                appendLine("ManifestVersion: 1")
                appendLine("Artifact:")
                appendLine("  RepositoryId: ${AIcYamlScalar(repositoryId.get())}")
                appendLine("  LocalArtifactId: ${AIcYamlScalar(localArtifactId.get())}")
                appendLine("  ArtifactCoordinateId: ${AIcYamlScalar(artifactCoordinateId.get())}")
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
    @get:Input abstract val distributionName: Property<String>
    @get:Input abstract val pythonVersion: Property<String>
    @get:Input abstract val artifactDescription: Property<String>
    @get:Input abstract val licenseIds: ListProperty<String>
    @get:InputFile @get:Optional abstract val templateFile: RegularFileProperty
    @get:OutputFile abstract val outputFile: RegularFileProperty

    @TaskAction
    fun AIcGenerate() {
        val locTemplateFile = templateFile.orNull?.asFile
        val locTemplateText = if (locTemplateFile != null && locTemplateFile.isFile) {
            locTemplateFile.readText(Charsets.UTF_8)
        } else {
            ""
        }
        if (Regex("(?m)^\\s*\\[project]\\s*$").containsMatchIn(locTemplateText)) {
            throw GradleException("pyproject.toml.tpl must not define [project]; Algites owns generated Python project identity and version metadata.")
        }
        if (Regex("(?m)^\\s*\\[build-system]\\s*$").containsMatchIn(locTemplateText)) {
            throw GradleException("pyproject.toml.tpl must not define [build-system]; the Algites Python adapter owns the effective build backend.")
        }

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
            if (locDescription.isNotBlank()) appendLine("description = \"$locDescription\"")
            if (locLicenseIds.size == 1) appendLine("license = \"${locLicenseIds.single()}\"")
            if (locLicenseIds.isNotEmpty()) {
                val locLicensePaths = locLicenseIds.joinToString(", ") { locLicenseId -> "\"LICENSES/$locLicenseId.txt\"" }
                appendLine("license-files = [$locLicensePaths]")
            }
            appendLine()
            appendLine("[tool.setuptools.packages.find]")
            appendLine("where = [\"src/product/python\", \"src/product/python.gen\"]")
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

abstract class AIcValidatePythonDistributionPathsTask : DefaultTask() {
    @get:Internal abstract val repositoryDirectory: DirectoryProperty
    @get:Input abstract val pythonArtifactRelativePaths: ListProperty<String>

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

        pythonArtifactRelativePaths.get().sorted().forEach { locArtifactName ->
            val locProjectDirectory = if (locArtifactName == ".") locRepositoryDirectory else File(locRepositoryDirectory, locArtifactName)
            val locProductSourceDirectory = File(locProjectDirectory, "src/product")
            val locLocalOwners = linkedMapOf<String, MutableList<String>>()
            if (locProductSourceDirectory.isDirectory) {
                locProductSourceDirectory.listFiles()
                    ?.filter { locDirectory -> locDirectory.isDirectory && AIcIsCanonicalSourceRootName(locDirectory.name) }
                    ?.sortedBy { locDirectory -> locDirectory.name }
                    ?.forEach { locSourceRoot ->
                        locSourceRoot.walkTopDown()
                            .filter { locFile -> locFile.isFile && "__pycache__" !in locFile.toPath().map { it.toString() } && locFile.extension.lowercase() !in setOf("pyc", "pyo") }
                            .forEach { locFile ->
                                val locRelativePath = locSourceRoot.toPath().relativize(locFile.toPath()).toString().replace(File.separatorChar, '/')
                                locLocalOwners.getOrPut(locRelativePath) { mutableListOf() }.add(locSourceRoot.name)
                                locPathOwners.getOrPut(locRelativePath) { mutableListOf() }.add(locArtifactName to locSourceRoot.name)
                                locArtifactFiles.getOrPut(locArtifactName) { linkedSetOf() }.add(locRelativePath)
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
                "No enabled Python ${publicationStability.get()} upload repository endpoint is configured for project '${projectPathValue.get()}'. " +
                    "Configure repositories.python.${repositoryVisibility.get()}.${publicationStability.get()}.upload in Algites metadata or its inherited defaults."
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
        is List<*> -> aValue.mapNotNull { it?.toString()?.trim()?.lowercase()?.takeIf(String::isNotBlank) }
        null -> emptyList()
        else -> aValue.toString().split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }
    }
}

data class AIcdAlgitesRepositoryEndpoint(
    val cell: String,
    val id: String,
    val url: String,
    val credentialProfile: String?,
    val usageProviderAdapter: String?
)

data class AIcdAlgitesCredentialProfile(
    val id: String,
    val type: String,
    val configuration: Map<String, String>
)

@Suppress("UNCHECKED_CAST")
fun AIcAlgitesRepositoryEndpoints(aValue: Any?, aCell: String): List<AIcdAlgitesRepositoryEndpoint> {
    val locRepositories = aValue as? Map<*, *> ?: return emptyList()
    val locItems = locRepositories[aCell] as? List<*> ?: return emptyList()
    return locItems.mapNotNull { locItem ->
        val locMap = locItem as? Map<*, *> ?: return@mapNotNull null
        val locEnabled = locMap["enabled"]?.toString()?.toBooleanStrictOrNull() ?: true
        if (!locEnabled) return@mapNotNull null
        val locId = locMap["id"]?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val locUrl = locMap["url"]?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val locCredentialProfile = locMap["credentialProfile"]?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        val locUsageProviderAdapter = locMap["usageProviderAdapter"]?.toString()?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it != "null" }
        AIcdAlgitesRepositoryEndpoint(aCell, locId, locUrl, locCredentialProfile, locUsageProviderAdapter)
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

fun AIcAlgitesPythonDistributionName(aArtifactCoordinateId: String): String {
    val locNormalized = aArtifactCoordinateId
        .lowercase()
        .replace(Regex("[._-]+"), "-")
        .trim('-')
    return "algites-$locNormalized"
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

fun AIcAlgitesSnapshotVersionForTechnology(aReleaseVersion: String, aTechnology: String): String = when (aTechnology) {
    "java" -> "$aReleaseVersion-SNAPSHOT"
    "python" -> AIcAlgitesPythonSnapshotVersionPrefix(aReleaseVersion)
    else -> "$aReleaseVersion-SNAPSHOT"
}

fun AIcAlgitesCloudsmithHeaders(aEndpoint: AIcdAlgitesRepositoryEndpoint, aProfiles: Map<String, AIcdAlgitesCredentialProfile>): Map<String, String> {
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
    val locConnection = URL(aUrl).openConnection() as HttpURLConnection
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
    val locConnection = URL(aUrl).openConnection() as HttpURLConnection
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
    aEndpoint: AIcdAlgitesRepositoryEndpoint,
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
    aEndpoint: AIcdAlgitesRepositoryEndpoint,
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
    aEndpoint: AIcdAlgitesRepositoryEndpoint,
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
        group = locAlgitesResolvedProjectGroup
    }

    version = algitesResolvedVersionValue(locAlgitesArtifactDirectory)
        ?: algitesResolvedVersionValue(algitesResolvedArtifactDirectoryForProject(":"))
        ?: "0.0.1-SNAPSHOT"

    plugins.withId("base") {
        tasks.named<Delete>("clean").configure {
            delete(rootProject.layout.projectDirectory.dir(locAlgitesRunDirectoryRelativePath))
        }
    }

    tasks.withType<Test>().configureEach {
        useTestNG()
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

val validateAlgitesPythonDistributionPaths = tasks.register<AIcValidatePythonDistributionPathsTask>("validateAlgitesPythonDistributionPaths") {
    group = "verification"
    description = "Validates Python product source roots, package-resource paths, and shared PEP 420 namespaces across distributions."
    repositoryDirectory.set(rootProject.layout.projectDirectory)
    pythonArtifactRelativePaths.set(locAlgitesPythonValidationArtifactPaths)
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
    val locRepositories = linkedMapOf<String, Map<String, String?>>()
    val locCredentials = linkedMapOf<String, Map<String, String>>()

    fun AIcCollect(aMetadata: Map<String, Any?>, aScope: String) {
        val locScopeTechnologyKinds = AIcAlgitesStringList(aMetadata["technologyKinds"]).toSet()
        val locRepositoryMap = aMetadata["repositories"] as? Map<*, *> ?: return
        val locProfiles = AIcAlgitesCredentialProfiles(aMetadata["credentialProfiles"])

        locRepositoryMap.keys.mapNotNull { it?.toString() }.sorted().forEach { locCell ->
            val locSegments = locCell.split('.')
            if (locSegments.size != 4) return@forEach
            val (locTechnology, locVisibility, locStability, locUsage) = locSegments
            if (locUsage !in locRequestedUsages) return@forEach
            if (locUsage == "download" && locVisibility !in locDownloadVisibilities) return@forEach
            if (locUsage == "upload" && locVisibility !in locUploadVisibilities) return@forEach
            if (locUsage == "manage" && locVisibility !in locManageVisibilities) return@forEach
            if (locUsage == "download" && locStability !in locDownloadStabilities) return@forEach
            if (locUsage == "upload" && locStability !in locUploadStabilities) return@forEach
            if (locUsage == "manage" && locStability !in locManageStabilities) return@forEach
            if (locUsage == "manage" && aScope == "repository") return@forEach
            if (locUsage == "manage" && aMetadata["deleteSnapshotWhenReleased"]?.toString()?.toBooleanStrictOrNull() == false) return@forEach
            if (locTechnology !in locOperationTechnologyKinds) return@forEach
            if (locScopeTechnologyKinds.isNotEmpty() && locTechnology !in locScopeTechnologyKinds) return@forEach

            AIcAlgitesRepositoryEndpoints(aMetadata["repositories"], locCell).forEach { locEndpoint ->
                val locProfileId = locEndpoint.credentialProfile
                val locProfile = if (locProfileId.isNullOrBlank()) null else locProfiles[locProfileId]
                    ?: throw GradleException(
                        "Repository endpoint '${locEndpoint.id}' references undefined credential profile '$locProfileId'."
                    )
                val locRepositoryKey = "$aScope|$locCell|${locEndpoint.id}"
                locRepositories[locRepositoryKey] = linkedMapOf(
                    "scope" to aScope,
                    "cell" to locCell,
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
        "repositories" to locRepositories.values.toList(),
        "credentials" to locCredentials.values.toList()
    )
    Triple(JsonOutput.toJson(locPlan), locCredentials.size, locOperationTechnologyKinds.sorted())
}

val algitesResolveRequiredCredentials = tasks.register<AIcResolveAlgitesRequiredCredentialsTask>("resolveAlgitesRequiredCredentials") {
    group = "algites"
    description = "Resolves enabled repository endpoints and the credential profiles required by the selected repository context."

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
    val locAlgitesArtifactDirectory = algitesResolvedArtifactDirectoryForProject(project.path)
    val locAlgitesTechnologyKinds = AIcAlgitesStringList(locAlgitesArtifactDirectory?.get("technologyKinds"))
    val locEffectiveTechnologyKinds = if (algitesRequestedTechnologyKinds.isEmpty()) {
        locAlgitesTechnologyKinds.toSet()
    } else {
        locAlgitesTechnologyKinds.filter { it in algitesRequestedTechnologyKinds }.toSet()
    }

    val locAlgitesSubprojectPathDots = project.path
        .removePrefix(":")
        .replace(':', '.')

    val locAlgitesCanonicalArtifactId = if (locAlgitesSubprojectPathDots.isBlank()) {
        rootProject.name
    } else {
        "${rootProject.name}_${locAlgitesSubprojectPathDots}"
    }
    val locAlgitesProjectVersion = project.version.toString()

    val locEffectiveRepositories = locAlgitesArtifactDirectory?.get("repositories")
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
        artifactCoordinateId.set(locAlgitesCanonicalArtifactId)
        locAlgitesArtifactDirectory?.get("groupId")?.toString()?.takeIf { it.isNotBlank() && it != "null" }?.let { locGroupId -> groupId.set(locGroupId) }
        artifactVersion.set(locAlgitesProjectVersion)
        sourcePath.set(locAlgitesArtifactDirectoryPath)
        structureKind.set(locAlgitesArtifactDirectory?.get("structureKind")?.toString() ?: "artifact")
        artifactName.set(locAlgitesArtifactDirectory?.get("name")?.toString() ?: locAlgitesCanonicalArtifactId)
        artifactDescription.set(locAlgitesArtifactDirectory?.get("description")?.toString() ?: "")
        descriptorHierarchy.set(locAlgitesDescriptorHierarchy)
        outputFile.set(locAlgitesManifestOutputFile)
    }

    plugins.withId("base") {
        extensions.configure<BasePluginExtension>("base") {
            archivesName.set(locAlgitesCanonicalArtifactId)
        }
        if ("java" in locEffectiveTechnologyKinds) {
            val locJavaBuildTask = tasks.named("build")
            algitesBuild.configure { dependsOn(locJavaBuildTask) }
        }
    }

    if ("java" in locAlgitesTechnologyKinds) {
        plugins.withId("java") {
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
                requireAlgitesGroupForPublish(project.path, project.group)
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
                    artifactId = locAlgitesCanonicalArtifactId
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
                    val locCell = "java.$algitesPublicationRepositoryVisibility.$locStability.upload"
                    val locEndpoints = AIcAlgitesRepositoryEndpoints(locEffectiveRepositories, locCell)

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
        val locPythonDistributionName = AIcAlgitesPythonDistributionName(locAlgitesCanonicalArtifactId)
        val locPythonImportNamespace = AIcAlgitesPythonImportNamespace(rootProject.name, locAlgitesSubprojectPathDots)

        val locAlgitesProjectRunDirectory = rootProject.layout.projectDirectory.dir(
            AIcAlgitesRunDirectoryRelativePath(project.projectDir)
        )
        val locPythonBuildProjectDirectory = locAlgitesProjectRunDirectory.dir("bld/python/project")

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

        val locGeneratePythonProjectMetadata = tasks.register<AIcGeneratePythonProjectMetadataTask>("generatePythonProjectMetadata") {
            group = "algites"
            description = "Generates the effective pyproject.toml for this Algites Python artifact."
            dependsOn(rootProject.tasks.named("verifyAlgitesLicensing"))
            distributionName.set(locPythonDistributionName)
            pythonVersion.set(locPythonProjectVersion)
            artifactDescription.set(locPythonArtifactDescription)
            licenseIds.set(locPythonLicenseIds)
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

            duplicatesStrategy = DuplicatesStrategy.FAIL
            into(locPythonBuildProjectDirectory)
            from(project.layout.projectDirectory) {
                exclude("run/**", "build/**", ".gradle/**", ".kotlin/**", "**/__pycache__/**", "**/*.pyc", "**/*.pyo")
            }
            listOf("jsondefs", "yamldefs", "xmldefs", "config").forEach { locSourceKind ->
                listOf("", ".gen", ".extgen").forEach { locGenerationSuffix ->
                    val locSourceDirectory = project.layout.projectDirectory.dir(
                        "src/product/$locSourceKind$locGenerationSuffix"
                    )
                    if (locSourceDirectory.asFile.isDirectory) {
                        from(locSourceDirectory) {
                            into("src/product/python")
                        }
                    }
                }
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
            manifest_name = "algites-artifact-manifest.yml"
            manifest_bytes = manifest_file.read_bytes()

            if output_dir.exists():
                shutil.rmtree(output_dir)
            output_dir.mkdir(parents=True, exist_ok=True)

            subprocess.run(
                [sys.executable, "-m", "build", "--outdir", str(output_dir)],
                cwd=project_dir,
                check=True,
            )

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
            if not wheel_files:
                raise RuntimeError("Python build did not produce a wheel.")
            if not sdist_files:
                raise RuntimeError("Python build did not produce a .tar.gz source distribution.")

            for wheel_file in wheel_files:
                inject_wheel(wheel_file)
            for sdist_file in sdist_files:
                inject_sdist(sdist_file)
        """.trimIndent()

        val locBuildPython = tasks.register<Exec>("buildPython") {
            group = "build"
            description = "Builds Python wheel/source distribution and embeds the deterministic Algites artifact manifest."
            dependsOn(locPreparePythonBuildProject)
            dependsOn(locGenerateAlgitesArtifactManifest)
            workingDir(locPythonBuildProjectDirectory)
            commandLine(
                algitesGradleOrEnvironmentValue("ALGITES_PYTHON_EXECUTABLE") ?: "python3",
                "-c",
                locPythonBuildAndManifestScript,
                locPythonBuildProjectDirectory.asFile.absolutePath,
                locPythonDistDirectory.asFile.absolutePath,
                locAlgitesManifestOutputFile.get().asFile.absolutePath
            )
            inputs.dir(locPythonBuildProjectDirectory)
            inputs.file(locAlgitesManifestOutputFile)
            outputs.dir(locPythonDistDirectory)
        }

        val locPythonPublishStability = if (locAlgitesProjectVersion.endsWith("SNAPSHOT", ignoreCase = true)) "snapshot" else "release"
        val locPythonPublishCell = "python.$algitesPublicationRepositoryVisibility.$locPythonPublishStability.upload"
        val locPythonPublishEndpoints = AIcAlgitesRepositoryEndpoints(locEffectiveRepositories, locPythonPublishCell)
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
        if ("python" in locEffectiveTechnologyKinds) {
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
            val locRepositories = locMetadata["repositories"]
            val locProfiles = AIcAlgitesCredentialProfiles(locMetadata["credentialProfiles"])
            val locGroupId = locMetadata["groupId"]?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" }
            val locArtifactId = AIcAlgitesCanonicalArtifactId(locProjectPath)

            locTechnologyKinds.sorted().forEach { locTechnology ->
                val locCell = "$locTechnology.$algitesPublicationRepositoryVisibility.snapshot.manage"
                val locEndpoints = AIcAlgitesRepositoryEndpoints(locRepositories, locCell)
                if (locEndpoints.isEmpty()) {
                    logger.lifecycle("No enabled $locTechnology snapshot manage endpoint is configured for '$locProjectPath'; cleanup is skipped for this TechnologyKind.")
                    return@forEach
                }
                val locSnapshotVersion = AIcAlgitesSnapshotVersionForTechnology(locReleaseVersion, locTechnology)
                val locSnapshotVersionIsPrefix = locTechnology == "python"
                val locPackageName = when (locTechnology) {
                    "java" -> locArtifactId
                    "python" -> AIcAlgitesPythonDistributionName(locArtifactId)
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
                    val locDeleted = when (locEndpoint.usageProviderAdapter) {
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
                        null -> throw GradleException("Manage endpoint '${locEndpoint.id}' has no usageProviderAdapter.")
                        else -> throw GradleException(
                            "Manage endpoint '${locEndpoint.id}' uses unsupported usageProviderAdapter '${locEndpoint.usageProviderAdapter}'."
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
            val locRepositories = locMetadata["repositories"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
            locRepositories.toSortedMap(compareBy { it.toString() }).forEach { (locCell, locEndpoints) ->
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
