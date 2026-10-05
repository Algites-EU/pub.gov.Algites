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
import eu.algites.pltf.modustro.builder.publication.AIcGlobalPublicationPathValidator
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayload
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayloadFile
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationStabilityConfiguration
import eu.algites.pltf.modustro.builder.model.publication.AInPublicationOutputKind
import eu.algites.pltf.modustro.builder.model.publication.AInPublicationStability
import java.io.File
import java.security.MessageDigest

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
val locSchemaRepositoryVisibility = ((rootProject.extra["modustroResolvedRepositoryMetadata"] as Map<*, *>)["visibility"]?.toString())
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
        if (locTrimmed.isBlank() || locTrimmed.startsWith("#") || !locTrimmed.startsWith("GlobalPublicationPathId:")) {
            null
        } else {
            locTrimmed.substringAfter(':').trim().removeSurrounding("\"").removeSurrounding("'").takeIf(String::isNotBlank)
        }
    }
    if (locValues.size != 1) {
        throw GradleException(
            "Global-publication user sidecar '$aSidecar' must define exactly one non-empty GlobalPublicationPathId."
        )
    }
    return locValues.single()
}

val locGenerateModustroSchemaSite = tasks.register("generateModustroSchemaSite") {
    group = "modustro"
    description = "Validates and stages canonical definitions for global schema-site publication."

    inputs.property("publicationState", locSchemaPublicationState)
    inputs.property("sourceKinds", locSchemaSourceKinds.sorted().joinToString(","))
    inputs.property("publicationDestinations", locSchemaPublicationDestinationIds.joinToString(","))
    outputs.dir(locSchemaSiteRoot)

    doLast {
        val locOutputRoot = locSchemaSiteRoot.asFile
        locOutputRoot.deleteRecursively()
        locOutputRoot.mkdirs()
        val locManifestRows = mutableListOf<String>()
        val locPathValidator = AIcGlobalPublicationPathValidator()
        val locSeenTargets = linkedMapOf<String, String>()
        val locSeenEndpoints = linkedMapOf<String, Map<String, Any?>>()
        var locExpectedEndpointIds: Set<String>? = null

        locSchemaArtifactDirectories
            .filter { locArtifact -> locArtifact["structureKind"]?.toString() == "artifact" }
            .forEach { locArtifact ->
                val locArtifactPath = locArtifact["path"]?.toString().orEmpty()
                val locArtifactDirectory = if (locArtifactPath.isBlank() || locArtifactPath == ".") {
                    rootProject.projectDir
                } else {
                    File(rootProject.projectDir, locArtifactPath)
                }
                val locPublicationPlan = locResolveSchemaPublicationPlan(
                    locArtifact,
                    "schema_site",
                    locSchemaPublicationStability,
                    locSchemaPublicationDestinationIds
                )
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

                locSchemaSourceKinds.sorted().forEach { locSourceKind ->
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
                                    "Canonical definition '$locDefinitionFile' declares GlobalPublicationPathId '$locPathId', " +
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
                            val locSha256 = AIcSchemaSha256(locDefinitionFile)
                            locManifestRows += listOf(
                                locSourceKind,
                                locPathId,
                                locTarget.relativeTo(locOutputRoot).invariantSeparatorsPath,
                                locSha256,
                                locSchemaPublicationState,
                                locArtifactPath,
                                locEndpointIds.joinToString(",")
                            ).joinToString("\t", transform = ::AIcSchemaManifestValue)
                        }
                }
            }

        val locManifest = locSchemaManifestFile.asFile
        locManifest.parentFile.mkdirs()
        locManifest.writeText(
            "SourceKind\tGlobalPublicationPathId\tStagedPath\tSha256\tPublicationState\tArtifactPath\tPublicationDestinations\n" +
                locManifestRows.joinToString(System.lineSeparator()) +
                if (locManifestRows.isEmpty()) "" else System.lineSeparator(),
            Charsets.UTF_8
        )

        val locEndpointSelection = locSchemaEndpointSelectionFile.asFile
        locEndpointSelection.parentFile.mkdirs()
        locEndpointSelection.writeText(
            buildString {
                appendLine("count=${locSeenEndpoints.size}")
                locSeenEndpoints.toSortedMap().values.forEachIndexed { locIndex, locEndpoint ->
                    appendLine("endpoint.${locIndex}.id=${locEndpoint["id"]?.toString().orEmpty()}")
                    appendLine("endpoint.${locIndex}.url=${locEndpoint["publicationUri"]?.toString().orEmpty()}")
                    appendLine("endpoint.${locIndex}.credentialProfile=${locEndpoint["publicationCredentialProfile"]?.toString().orEmpty()}")
                    appendLine("endpoint.${locIndex}.adapter=${locEndpoint["publicationAdapter"]?.toString().orEmpty()}")
                    appendLine("endpoint.${locIndex}.order=${locEndpoint["publicationOrder"] ?: 0}")
                    appendLine("endpoint.${locIndex}.failurePolicy=${locEndpoint["publicationFailurePolicy"] ?: "FAIL_BUILD_ON_PUBLISHING_FAILURE"}")
                }
            },
            Charsets.UTF_8
        )
        logger.lifecycle("Algites global schema site staged at: ${locOutputRoot.absolutePath}")
        logger.lifecycle("Canonical definitions staged: ${locManifestRows.size}")
    }
}

if (tasks.findByName("prepareModustroSchemaPublishing") == null) {
    tasks.register("prepareModustroSchemaPublishing") {
        group = "modustro"
        description = "Prepares the validated global schema site and requires at least one enabled PublicationEndpoint for every published artifact."
        dependsOn(locGenerateModustroSchemaSite)
        doLast {
            val locRows = locSchemaManifestFile.asFile.readLines(Charsets.UTF_8).drop(1).filter(String::isNotBlank)
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
        payloadRoot.set(locSchemaSiteRoot)
        publicationService.set(locPublicationService)
        usesService(locPublicationService)
    }
}
