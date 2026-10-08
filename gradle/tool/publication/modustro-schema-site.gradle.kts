/*
 * Modustro Builder global canonical-schema site generation and publishing planning.
 *
 * The task validates author-controlled user sidecars, stages canonical
 * definitions under their GlobalPublicationPathId, and writes a deterministic
 * publication manifest. Actual upload is delegated to the common Modustro publishing
 * scheduler and the PublicationAdapter selected by each effective PublicationEndpoint.
 */

import eu.algites.pltf.modustro.builder.gradleinit.AIcModustroPublishFilesTask
import eu.algites.pltf.modustro.builder.gradleinit.AIcModustroPublicationService
import org.gradle.api.provider.Provider
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import eu.algites.pltf.modustro.builder.publication.AIcGlobalPublicationPathValidator
import eu.algites.pltf.modustro.builder.model.execution.AIngBuildExecutionFailurePolicy_1
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayload
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayloadFile
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationStabilityConfiguration
import eu.algites.pltf.modustro.builder.model.publication.AInPublicationOutputKind
import eu.algites.pltf.modustro.builder.model.publication.AInPublicationStability
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/** Governed source-sidecar contract used by both generation and schema-site validation. */
object AIcModustroSchemaUserMetadata {
    const val SCHEMA_FIELD_NAME__GLOBAL_PUBLICATION_PATH_ID = "GlobalPublicationPathId"
    const val SCHEMA_FIELD_NAME__JSON_SCHEMA = "\$schema"
    const val SCHEMA_URI = "https://defs.dev.algites.eu/api/yamldefs/eu/algites/pltf/modustro/builder/publication/global-publication-user-metadata_1.yamldef.schema.json"
}

/**
 * Creates missing author-controlled source sidecars. Existing sidecars are never modified.
 * This task intentionally declares no Gradle outputs: source files are owned by the author,
 * not by the build cache, and every explicit invocation must inspect the current source tree.
 */
abstract class AIcGenerateMissingModustroSchemaSidecarsTask : DefaultTask() {
    @get:Input abstract val sourceKinds: ListProperty<String>
    @get:Input abstract val artifactPathsJson: Property<String>
    @get:Internal abstract val repositoryDirectory: DirectoryProperty

    @TaskAction
    fun AIcGenerate() {
        @Suppress("UNCHECKED_CAST")
        val locArtifactPaths = JsonSlurper().parseText(artifactPathsJson.get()) as List<String>
        val locRepositoryRoot = repositoryDirectory.get().asFile
        val locRepositoryCanonicalPath = locRepositoryRoot.toPath().toRealPath()
        val locPathValidator = AIcGlobalPublicationPathValidator()
        var locCreated = 0
        var locExisting = 0
        var locDefinitions = 0

        locArtifactPaths.distinct().sorted().forEach { locArtifactPath ->
            val locArtifactRoot = if (locArtifactPath.isBlank() || locArtifactPath == ".") {
                locRepositoryRoot
            } else {
                File(locRepositoryRoot, locArtifactPath)
            }
            sourceKinds.get().sorted().forEach locSourceKindLoop@{ locKind ->
                val locSourceRoot = File(locArtifactRoot, "src/product/$locKind")
                if (!locSourceRoot.isDirectory) return@locSourceKindLoop
                locSourceRoot.walkTopDown()
                    .filter { locFile -> locFile.isFile && !locFile.name.endsWith(".meta.yml") }
                    .sortedBy { locFile -> locFile.relativeTo(locSourceRoot).invariantSeparatorsPath }
                    .forEach { locDefinition ->
                        if (Files.isSymbolicLink(locDefinition.toPath())) {
                            throw GradleException("Refusing to generate a sidecar for symlinked definition '$locDefinition'.")
                        }
                        if (!locDefinition.parentFile.toPath().toRealPath().startsWith(locRepositoryCanonicalPath)) {
                            throw GradleException("Refusing to create a sidecar outside the repository: '$locDefinition'.")
                        }
                        val locPathId = locPathValidator.validate(
                            locDefinition.relativeTo(locSourceRoot).invariantSeparatorsPath
                        )
                        val locSidecar = File(locDefinition.path + ".meta.yml")
                        locDefinitions++
                        if (Files.exists(locSidecar.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                            if (!locSidecar.isFile || Files.isSymbolicLink(locSidecar.toPath())) {
                                throw GradleException("Schema sidecar path is not a regular file: '$locSidecar'.")
                            }
                            locExisting++
                        } else {
                            val locSchema = AIcModustroSchemaUserMetadata.SCHEMA_URI
                            val locContent = buildString {
                                appendLine("# yaml-language-server: \$schema=$locSchema")
                                appendLine("# \$schema: $locSchema")
                                appendLine("${AIcModustroSchemaUserMetadata.SCHEMA_FIELD_NAME__JSON_SCHEMA}: $locSchema")
                                appendLine()
                                appendLine("${AIcModustroSchemaUserMetadata.SCHEMA_FIELD_NAME__GLOBAL_PUBLICATION_PATH_ID}: $locPathId")
                            }
                            try {
                                Files.writeString(locSidecar.toPath(), locContent, Charsets.UTF_8,
                                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
                                locCreated++
                                logger.lifecycle("Created global schema user sidecar: ${locSidecar.relativeTo(locRepositoryRoot)}")
                            } catch (locConcurrent: FileAlreadyExistsException) {
                                throw GradleException("Schema sidecar appeared during generation; refusing to overwrite: '$locSidecar'.", locConcurrent)
                            }
                        }
                    }
            }
        }
        logger.lifecycle("Global schema user sidecars: $locDefinitions definitions, $locCreated created, $locExisting already present.")
    }
}

/** Stages schemas using declared inputs and a data-only publication plan. */
abstract class AIcGenerateModustroSchemaSiteTask : DefaultTask() {
    @get:Input abstract val publicationState: Property<String>
    @get:Input abstract val sourceKinds: ListProperty<String>
    @get:Input abstract val artifactPlansJson: Property<String>
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val canonicalSources: ConfigurableFileCollection
    @get:Internal abstract val repositoryDirectory: DirectoryProperty
    @get:OutputDirectory abstract val siteRoot: DirectoryProperty

    fun AIcSchemaSha256(aFile: File): String {
        val locDigest = MessageDigest.getInstance("SHA-256")
        aFile.inputStream().use { locInput ->
            val locBuffer = ByteArray(64 * 1024)
            while (true) {
                val locRead = locInput.read(locBuffer)
                if (locRead < 0) break
                locDigest.update(locBuffer, 0, locRead)
            }
        }
        return locDigest.digest().joinToString("") { locByte -> "%02x".format(locByte) }
    }

    fun AIcSchemaManifestValue(aValue: String): String = aValue
        .replace("\\", "\\\\")
        .replace("\t", "\\t")
        .replace("\r", "\\r")
        .replace("\n", "\\n")

    fun AIcSchemaReadGlobalPublicationPathId(aSidecar: File): String {
        val locValues = aSidecar.readLines(Charsets.UTF_8).mapNotNull { locLine ->
            val locTrimmed = locLine.trim()
            if (locTrimmed.isBlank() || locTrimmed.startsWith("#") || !locTrimmed.startsWith("${AIcModustroSchemaUserMetadata.SCHEMA_FIELD_NAME__GLOBAL_PUBLICATION_PATH_ID}:")) {
                null
            } else {
                locTrimmed.substringAfter(':').trim().removeSurrounding("\"").removeSurrounding("'").takeIf(String::isNotBlank)
            }
        }
        if (locValues.size != 1) {
            throw GradleException(
                "Global-publication user sidecar '$aSidecar' must define exactly one non-empty ${AIcModustroSchemaUserMetadata.SCHEMA_FIELD_NAME__GLOBAL_PUBLICATION_PATH_ID}."
            )
        }
        return locValues.single()
    }

    @TaskAction
    fun AIcGenerate() {
        @Suppress("UNCHECKED_CAST")
        val locArtifacts = JsonSlurper().parseText(artifactPlansJson.get()) as List<Map<String, Any?>>
        val locOutputRoot = siteRoot.get().asFile
        locOutputRoot.deleteRecursively()
        locOutputRoot.mkdirs()
        val locManifestRows = mutableListOf<String>()
        val locPathValidator = AIcGlobalPublicationPathValidator()
        val locSeenTargets = linkedMapOf<String, String>()
        val locSeenEndpoints = linkedMapOf<String, Map<String, Any?>>()
        var locExpectedEndpointIds: Set<String>? = null

        locArtifacts
            .forEach { locArtifact ->
                val locArtifactPath = locArtifact["path"]?.toString().orEmpty()
                val locArtifactDirectory = if (locArtifactPath.isBlank() || locArtifactPath == ".") {
                    repositoryDirectory.get().asFile
                } else {
                    File(repositoryDirectory.get().asFile, locArtifactPath)
                }
                @Suppress("UNCHECKED_CAST")
                val locPublicationPlan = locArtifact["publicationPlan"] as Map<String, Any?>
                @Suppress("UNCHECKED_CAST")
                val locPublicationEndpoints = (locPublicationPlan["publicationEndpoints"] as? List<Map<String, Any?>>).orEmpty()
                val locEndpointIds = locPublicationEndpoints.map { locEndpoint ->
                    val locEndpointId = locEndpoint["id"]?.toString()
                        ?: throw GradleException("schema_site PublicationEndpoint is missing Id.")
                    val locPrevious = locSeenEndpoints.putIfAbsent(locEndpointId, locEndpoint)
                    if (locPrevious != null && locPrevious != locEndpoint) {
                        throw GradleException(
                            "PublicationEndpoint id '$locEndpointId' resolves to inconsistent effective endpoint definitions across artifacts."
                        )
                    }
                    locEndpointId
                }

                val locEndpointIdSet = locEndpointIds.toSet()
                val locExpected = locExpectedEndpointIds
                if (locExpected == null) {
                    locExpectedEndpointIds = locEndpointIdSet
                } else if (locExpected != locEndpointIdSet) {
                    throw GradleException(
                        "Global schema site requires one consistent schema_site PublicationEndpoint set across all artifacts; " +
                            "expected ${locExpected.sorted()} but artifact '$locArtifactPath' resolves ${locEndpointIdSet.sorted()}."
                    )
                }

                sourceKinds.get().sorted().forEach { locSourceKind ->
                    val locSourceRoot = File(locArtifactDirectory, "src/product/$locSourceKind")
                    if (!locSourceRoot.isDirectory) {
                        return@forEach
                    }
                    locSourceRoot.walkTopDown()
                        .filter { locFile -> locFile.isFile && !locFile.name.endsWith(".meta.yml") }
                        .sortedBy { locFile -> locFile.relativeTo(locSourceRoot).invariantSeparatorsPath }
                        .forEach { locDefinitionFile ->
                            val locExpectedPathId = locDefinitionFile.relativeTo(locSourceRoot).invariantSeparatorsPath
                            val locSidecar = File(locDefinitionFile.path + ".meta.yml")
                            if (!locSidecar.isFile) {
                                throw GradleException(
                                    "Canonical definition '$locDefinitionFile' is missing required user sidecar '${locSidecar.name}'."
                                )
                            }
                            val locPathId = locPathValidator.validate(AIcSchemaReadGlobalPublicationPathId(locSidecar))
                            if (locPathId != locExpectedPathId) {
                                throw GradleException(
                                    "Canonical definition '$locDefinitionFile' declares ${AIcModustroSchemaUserMetadata.SCHEMA_FIELD_NAME__GLOBAL_PUBLICATION_PATH_ID} '$locPathId', " +
                                        "but the canonical source-root-relative path is '$locExpectedPathId'."
                                )
                            }
                            val locTargetKey = "$locSourceKind/$locPathId"
                            val locPreviousSource = locSeenTargets.putIfAbsent(locTargetKey, locDefinitionFile.absolutePath)
                            if (locPreviousSource != null) {
                                throw GradleException(
                                    "Duplicate global schema publication target '$locTargetKey': '$locPreviousSource' and '$locDefinitionFile'."
                                )
                            }

                            val locTarget = File(locOutputRoot, "api/$locSourceKind/$locPathId")
                            locTarget.parentFile.mkdirs()
                            locDefinitionFile.copyTo(locTarget, overwrite = true)
                            locSidecar.copyTo(File(locTarget.path + ".meta.yml"), overwrite = true)
                            val locSha256 = AIcSchemaSha256(locDefinitionFile)
                            locManifestRows += listOf(
                                locSourceKind,
                                locPathId,
                                locTarget.relativeTo(locOutputRoot).invariantSeparatorsPath,
                                locSha256,
                                publicationState.get(),
                                locArtifactPath,
                                locEndpointIds.joinToString(",")
                            ).joinToString("\t", transform = ::AIcSchemaManifestValue)
                        }
                }
            }

        val locManifest = File(locOutputRoot, ".modustro-publishing/schema-site.tsv")
        locManifest.parentFile.mkdirs()
        locManifest.writeText(
            "SourceKind\t${AIcModustroSchemaUserMetadata.SCHEMA_FIELD_NAME__GLOBAL_PUBLICATION_PATH_ID}\tStagedPath\tSha256\tPublicationState\tArtifactPath\tPublicationDestinations\n" +
                locManifestRows.joinToString(System.lineSeparator()) +
                if (locManifestRows.isEmpty()) "" else System.lineSeparator(),
            Charsets.UTF_8
        )

        val locEndpointSelection = File(locOutputRoot, ".modustro-publishing/schema-endpoints.properties")
        locEndpointSelection.parentFile.mkdirs()
        locEndpointSelection.writeText(
            buildString {
                appendLine("count=${locSeenEndpoints.size}")
                locSeenEndpoints.toSortedMap().values.forEachIndexed { locIndex, locEndpoint ->
                    appendLine("endpoint.${locIndex}.id=${locEndpoint["id"]?.toString().orEmpty()}")
                    appendLine("endpoint.${locIndex}.url=${locEndpoint["publicationUri"]?.toString().orEmpty()}")
                    appendLine("endpoint.${locIndex}.credentialProfile=${locEndpoint["publicationCredentialProfile"]?.toString().orEmpty()}")
                    appendLine("endpoint.${locIndex}.adapter=${locEndpoint["publicationAdapter"]?.toString().orEmpty()}")
                    appendLine("endpoint.${locIndex}.executionOrder=${locEndpoint["executionOrder"] ?: 0}")
                    appendLine("endpoint.${locIndex}.executionFailurePolicy=${locEndpoint["executionFailurePolicy"] ?: AIngBuildExecutionFailurePolicy_1.FAIL_BUILD_ON_FAILURE.wireValue()}")
                }
            },
            Charsets.UTF_8
        )
        logger.lifecycle("Algites global schema site staged at: ${locOutputRoot.absolutePath}")
        logger.lifecycle("Canonical definitions staged: ${locManifestRows.size}")
    }
}

/** Validates the generated manifest without retaining the applied script. */
abstract class AIcPrepareModustroSchemaPublishingTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifestFile: RegularFileProperty

    @TaskAction
    fun AIcPrepare() {
        val locRows = manifestFile.get().asFile.readLines(Charsets.UTF_8).drop(1).filter(String::isNotBlank)
        val locMissing = locRows.mapNotNull { locRow ->
            val locParts = locRow.split('\t', limit = 7)
            if (locParts.size == 7 && locParts[6].isBlank()) locParts[1] else null
        }
        if (locMissing.isNotEmpty()) {
            throw GradleException(
                "Global schema publication has no effective schema_site PublicationEndpoint for: " +
                    locMissing.distinct().sorted().joinToString(", ")
            )
        }
    }
}

val locPublicationBridgeScript = rootProject.file("gradle/tool/publication/modustro-publication.gradle.kts")
if (locPublicationBridgeScript.isFile) {
    apply(from = locPublicationBridgeScript)
} else {
    apply(from = uri(
        "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/${System.getenv("MODUSTRO_PUBLIC_GOVERNANCE_REVISION") ?: "main"}/gradle/tool/publication/modustro-publication.gradle.kts"
    ))
}

@Suppress("UNCHECKED_CAST")
val locSchemaArtifactDirectories = rootProject.extra["modustroResolvedArtifactDirectories"] as List<Map<String, Any?>>
@Suppress("UNCHECKED_CAST")
val locResolveSchemaPublicationPlan = rootProject.extra["modustroResolvePublicationPlan"] as (
    Map<String, Any?>,
    String,
    String,
    List<String>
) -> Map<String, Any?>
val locSchemaRepositoryMetadata = rootProject.extra["modustroResolvedRepositoryMetadata"] as Map<*, *>
val locSchemaRepositoryVisibility = locSchemaRepositoryMetadata["visibility"]?.toString()
    ?: error("Algites repository visibility is unavailable for schema publication.")
val locSchemaPublicationState = ((findProperty("modustro.schemas.publicationState") as String?) ?: "draft")
    .trim()
    .lowercase()
    .also { locValue ->
        require(locValue == "draft" || locValue == "release") {
            "modustro.schemas.publicationState must be 'draft' or 'release'."
        }
    }
val locSchemaPublicationStability = if (locSchemaPublicationState == "release") "release" else "snapshot"
val locSchemaPublicationDestinationIds = (findProperty("modustro.schemas.publicationDestinations") as String?)
    ?.split(',')
    ?.map(String::trim)
    ?.filter(String::isNotBlank)
    ?: emptyList()
val locSchemaSourceKinds = ((findProperty("modustro.schemas.sourceKinds") as String?) ?: "yamldefs,jsondefs,xmldefs")
    .split(',')
    .map(String::trim)
    .filter(String::isNotBlank)
    .toSet()
    .also { locKinds ->
        val locUnsupported = locKinds - setOf("yamldefs", "jsondefs", "xmldefs")
        require(locUnsupported.isEmpty()) { "Unsupported canonical schema source kind(s): ${locUnsupported.sorted().joinToString(", ")}." }
    }
val locSchemaSiteRoot = layout.projectDirectory.dir(
    (findProperty("modustro.schemas.siteRoot") as String?) ?: "build/run/bld/modustro-schema-site/site"
)
val locSchemaManifestFile = locSchemaSiteRoot.file(".modustro-publishing/schema-site.tsv")
val locSchemaEndpointSelectionFile = locSchemaSiteRoot.file(".modustro-publishing/schema-endpoints.properties")
val locSchemaHasPublicationEndpoints = locSchemaArtifactDirectories
    .filter { locArtifact -> locArtifact["structureKind"]?.toString() == "artifact" }
    .any { locArtifact ->
        val locPlan = locResolveSchemaPublicationPlan(
            locArtifact,
            "schema_site",
            locSchemaPublicationStability,
            locSchemaPublicationDestinationIds
        )
        val locEnabled = locPlan["publicationEnabled"] as? Boolean ?: false
        @Suppress("UNCHECKED_CAST")
        val locEndpoints = (locPlan["publicationEndpoints"] as? List<Map<String, Any?>>).orEmpty()
        locEnabled && locEndpoints.isNotEmpty()
    }

if (locSchemaHasPublicationEndpoints) {
    try {
        Class.forName(
            "eu.algites.pltf.modustro.builder.publication.adapters.AIcS3SchemaPublication",
            false,
            AIcGlobalPublicationPathValidator::class.java.classLoader
        )
    } catch (locMissing: ClassNotFoundException) {
        throw GradleException(
            "Schema publication requires the deploy-sidecar-aware Builder coreimpl. " +
                "Install the updated builder bootstrap first, or publish the new builder with schema publication disabled.",
            locMissing
        )
    }
}

val locSchemaArtifactPlans = locSchemaArtifactDirectories
    .filter { it["structureKind"]?.toString() == "artifact" }
    .map { locArtifact ->
        mapOf(
            "path" to locArtifact["path"]?.toString().orEmpty(),
            "publicationPlan" to locResolveSchemaPublicationPlan(
                locArtifact, "schema_site", locSchemaPublicationStability, locSchemaPublicationDestinationIds
            )
        )
    }
val locSchemaCanonicalSources = files(locSchemaArtifactPlans.flatMap { locArtifact ->
    val locPath = locArtifact["path"]?.toString().orEmpty()
    val locDirectory = if (locPath.isBlank() || locPath == ".") rootProject.projectDir else File(rootProject.projectDir, locPath)
    locSchemaSourceKinds.sorted().map { locKind -> File(locDirectory, "src/product/$locKind") }
})
val locGenerateMissingUserSidecars = providers.gradleProperty("modustro.schemas.generateMissingUserSidecars")
    .orElse("false").get().also { locValue ->
        require(locValue == "true" || locValue == "false") {
            "modustro.schemas.generateMissingUserSidecars must be true or false."
        }
    } == "true"

val locGenerateMissingModustroSchemaSidecars = tasks.register<AIcGenerateMissingModustroSchemaSidecarsTask>(
    "generateMissingModustroSchemaSidecars"
) {
    group = "modustro"
    description = "Creates only missing canonical yamldefs/jsondefs/xmldefs source user sidecars; never overwrites existing metadata."
    sourceKinds.set(locSchemaSourceKinds.sorted())
    artifactPathsJson.set(JsonOutput.toJson(locSchemaArtifactPlans.map { it["path"]?.toString().orEmpty() }))
    repositoryDirectory.set(rootProject.layout.projectDirectory)
}

val locGenerateModustroSchemaSite = tasks.register<AIcGenerateModustroSchemaSiteTask>("generateModustroSchemaSite") {
    group = "modustro"
    description = "Validates and stages canonical definitions for global schema-site publication."
    if (locGenerateMissingUserSidecars) {
        dependsOn(locGenerateMissingModustroSchemaSidecars)
    }
    publicationState.set(locSchemaPublicationState)
    sourceKinds.set(locSchemaSourceKinds.sorted())
    artifactPlansJson.set(JsonOutput.toJson(locSchemaArtifactPlans))
    canonicalSources.from(locSchemaCanonicalSources)
    repositoryDirectory.set(rootProject.layout.projectDirectory)
    siteRoot.set(locSchemaSiteRoot)
}

if (tasks.findByName("prepareModustroSchemaPublishing") == null) {
    tasks.register<AIcPrepareModustroSchemaPublishingTask>("prepareModustroSchemaPublishing") {
        group = "modustro"
        description = "Requires an effective PublicationEndpoint for every staged canonical definition."
        dependsOn(locGenerateModustroSchemaSite)
        manifestFile.set(locSchemaManifestFile)
    }
}


if (tasks.findByName("publishModustroSchemaSite") == null) {
    val locArtifactPlans = locSchemaArtifactDirectories.filter { it["structureKind"]?.toString() == "artifact" }.map {
        locResolveSchemaPublicationPlan(it, "schema_site", locSchemaPublicationStability, locSchemaPublicationDestinationIds) to it
    }
    val locEndpointSets = locArtifactPlans.map { (locPlan, _) ->
        @Suppress("UNCHECKED_CAST")
        (locPlan["publicationEndpoints"] as? List<Map<String, Any?>>).orEmpty().mapNotNull { it["id"]?.toString() }.toSet()
    }.distinct()
    if (locSchemaHasPublicationEndpoints && locEndpointSets.size > 1) {
        throw GradleException("Global schema site resolves inconsistent PublicationEndpoint sets across artifacts.")
    }
    val locCredentialProfiles = linkedMapOf<String, Any?>()
    locArtifactPlans.forEach { (_, locArtifact) ->
        @Suppress("UNCHECKED_CAST")
        val locProfiles = locArtifact["credentialProfiles"] as? Map<String, Any?> ?: emptyMap()
        locProfiles.forEach { (locId, locProfile) ->
            val locPrevious = locCredentialProfiles.putIfAbsent(locId, locProfile)
            if (locPrevious != null && locPrevious != locProfile) {
                throw GradleException("Credential profile '$locId' resolves inconsistently across schema-site artifacts.")
            }
        }
    }
    @Suppress("UNCHECKED_CAST")
    val locPublicationService = rootProject.extra["modustroPublicationService"] as Provider<AIcModustroPublicationService>
    val locFiles = fileTree(locSchemaSiteRoot) { exclude(".git/**", ".modustro-publishing/**") }
    tasks.register<AIcModustroPublishFilesTask>("publishModustroSchemaSite") {
        group = "publishing"
        description = "Publishes the staged global schema site through the common publication scheduler."
        if (locSchemaHasPublicationEndpoints) {
            dependsOn("prepareModustroSchemaPublishing")
            payloadFiles.from(locFiles)
        }
        publicationPlanJson.set(JsonOutput.toJson(locArtifactPlans.firstOrNull()?.first ?: mapOf("publicationEnabled" to false)))
        credentialProfilesJson.set(JsonOutput.toJson(locCredentialProfiles))
        outputKind.set("SCHEMA_SITE")
        stability.set(locSchemaPublicationStability)
        artifactIdentity.set(rootProject.name)
        publicationVersion.set(locSchemaPublicationState)
        coordinates.set(if (locSchemaHasPublicationEndpoints) mapOf(
            "groupId" to (locSchemaRepositoryMetadata["groupId"]?.toString()?.takeIf(String::isNotBlank)
                ?: error("Algites effective repository GroupId is unavailable for schema publication.")),
            "artifactId" to rootProject.name,
            "technologyKind" to "modustro",
            "logicalVersion" to rootProject.version.toString(),
            "schemaAdoptUntrackedDrafts" to providers.gradleProperty("modustro.schemas.adoptUntrackedDrafts").orElse("false").get().also {
                require(it == "true" || it == "false") { "modustro.schemas.adoptUntrackedDrafts must be true or false." }
            }
        ) else emptyMap())
        payloadRoot.set(locSchemaSiteRoot)
        publicationService.set(locPublicationService)
        usesService(locPublicationService)
    }
}
