/*
 * Algites global canonical-schema site generation and publication planning.
 *
 * The task validates author-controlled user sidecars, stages canonical
 * definitions under their GlobalPublicationPathId, and writes a deterministic
 * publication manifest. Provider-specific upload is deliberately outside this
 * script and consumes the manifest plus effective ResourceEndpoint metadata.
 */

import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationDestinationSelection
import eu.algites.pltf.modustro.builder.model.resource.AIcResourceEndpointDefinition
import eu.algites.pltf.modustro.builder.publication.AIcGlobalPublicationPathValidator
import eu.algites.pltf.modustro.builder.publication.AIcPublicationDestinationResolver
import eu.algites.pltf.modustro.builder.publication.AIcgdGlobalPublicationUserMetadata_1
import eu.algites.pltf.modustro.builder.structureddata.jackson.AIcJacksonStructuredDataLoader
import java.io.File
import java.security.MessageDigest

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
        classpath("eu.algites.pltf.modustro.builder:pub.gov.Algites_devops.build.modustro.builder.structureddata.jackson:1.0-SNAPSHOT")
    }
}

val locPublicationBridgeScript = rootProject.file("gradle/tool/publication/algites-publication.gradle.kts")
if (locPublicationBridgeScript.isFile) {
    apply(from = locPublicationBridgeScript)
} else {
    apply(from = uri(
        "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/publication/algites-publication.gradle.kts"
    ))
}

@Suppress("UNCHECKED_CAST")
val locSchemaArtifactDirectories = rootProject.extra["algitesResolvedArtifactDirectories"] as List<Map<String, Any?>>
@Suppress("UNCHECKED_CAST")
val locResolveSchemaPublicationDestinations = rootProject.extra["algitesResolvePublicationDestinations"] as (
    Map<String, Any?>,
    String,
    String,
    String?,
    List<String>
) -> AIcPublicationDestinationSelection

val locSchemaRepositoryVisibility = ((rootProject.extra["algitesResolvedRepositoryMetadata"] as Map<*, *>)["visibility"]?.toString())
    ?: error("Algites repository visibility is unavailable for schema publication.")
val locSchemaPublicationState = ((findProperty("algites.schemas.publicationState") as String?) ?: "draft")
    .trim()
    .lowercase()
    .also { locValue ->
        require(locValue == "draft" || locValue == "release") {
            "algites.schemas.publicationState must be 'draft' or 'release'."
        }
    }
val locSchemaPublicationDestinationIds = (findProperty("algites.schemas.publicationDestinations") as String?)
    ?.split(',')
    ?.map(String::trim)
    ?.filter(String::isNotBlank)
    ?: emptyList()
val locSchemaSourceKinds = ((findProperty("algites.schemas.sourceKinds") as String?) ?: "yamldefs,jsondefs,xmldefs")
    .split(',')
    .map(String::trim)
    .filter(String::isNotBlank)
    .toSet()
    .also { locKinds ->
        val locUnsupported = locKinds - setOf("yamldefs", "jsondefs", "xmldefs")
        require(locUnsupported.isEmpty()) { "Unsupported canonical schema source kind(s): ${locUnsupported.sorted().joinToString(", ")}." }
    }
val locSchemaSiteRoot = layout.projectDirectory.dir(
    (findProperty("algites.schemas.siteRoot") as String?) ?: "build/run/bld/algites-schema-site/site"
)
val locSchemaManifestFile = locSchemaSiteRoot.file(".algites-publication/schema-site.tsv")
val locSchemaEndpointSelectionFile = locSchemaSiteRoot.file(".algites-publication/schema-endpoints.properties")

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

val locGenerateAlgitesSchemaSite = tasks.register("generateAlgitesSchemaSite") {
    group = "algites"
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
        val locYamlLoader = AIcJacksonStructuredDataLoader.yaml()
        val locSeenTargets = linkedMapOf<String, String>()
        val locSeenEndpoints = linkedMapOf<String, AIcResourceEndpointDefinition>()

        locSchemaArtifactDirectories
            .filter { locArtifact -> locArtifact["structureKind"]?.toString() == "artifact" }
            .forEach { locArtifact ->
                val locArtifactPath = locArtifact["path"]?.toString().orEmpty()
                val locArtifactDirectory = if (locArtifactPath.isBlank() || locArtifactPath == ".") {
                    rootProject.projectDir
                } else {
                    File(rootProject.projectDir, locArtifactPath)
                }
                val locSelection = locResolveSchemaPublicationDestinations(
                    locArtifact,
                    "schema_site",
                    locSchemaRepositoryVisibility,
                    null,
                    locSchemaPublicationDestinationIds
                )
                val locEndpointIds = locSelection.endpoints().map { locEndpoint ->
                    val locPrevious = locSeenEndpoints.putIfAbsent(locEndpoint.id(), locEndpoint)
                    if (locPrevious != null && (
                        locPrevious.url() != locEndpoint.url()
                            || locPrevious.credentialProfile() != locEndpoint.credentialProfile()
                            || locPrevious.resourceEndpointProviderAdapter() != locEndpoint.resourceEndpointProviderAdapter()
                    )) {
                        throw GradleException(
                            "ResourceEndpoint id '${locEndpoint.id()}' resolves to inconsistent effective endpoint definitions across artifacts."
                        )
                    }
                    locEndpoint.id()
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
                            val locUserMetadata = try {
                                locYamlLoader.load(locSidecar.toPath(), AIcgdGlobalPublicationUserMetadata_1::class.java)
                            } catch (locException: Exception) {
                                throw GradleException("Cannot load global-publication user sidecar '$locSidecar'.", locException)
                            }
                            val locPathId = locPathValidator.validate(locUserMetadata.globalPublicationPathId())
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
                    appendLine("endpoint.${locIndex}.id=${locEndpoint.id()}")
                    appendLine("endpoint.${locIndex}.url=${locEndpoint.url()}")
                    appendLine("endpoint.${locIndex}.credentialProfile=${locEndpoint.credentialProfile().orEmpty()}")
                    appendLine("endpoint.${locIndex}.providerAdapter=${locEndpoint.resourceEndpointProviderAdapter().orEmpty()}")
                }
            },
            Charsets.UTF_8
        )
        logger.lifecycle("Algites global schema site staged at: ${locOutputRoot.absolutePath}")
        logger.lifecycle("Canonical definitions staged: ${locManifestRows.size}")
    }
}

if (tasks.findByName("prepareAlgitesSchemaPublication") == null) {
    tasks.register("prepareAlgitesSchemaPublication") {
        group = "algites"
        description = "Prepares the validated global schema site and requires at least one upload destination for every published artifact."
        dependsOn(locGenerateAlgitesSchemaSite)
        doLast {
            val locRows = locSchemaManifestFile.asFile.readLines(Charsets.UTF_8).drop(1).filter(String::isNotBlank)
            val locMissing = locRows.mapNotNull { locRow ->
                val locParts = locRow.split('\t', limit = 7)
                if (locParts.size == 7 && locParts[6].isBlank()) locParts[1] else null
            }
            if (locMissing.isNotEmpty()) {
                throw GradleException(
                    "Global schema publication has no effective schema_site upload ResourceEndpoint for: " +
                        locMissing.distinct().sorted().joinToString(", ")
                )
            }
        }
    }
}
