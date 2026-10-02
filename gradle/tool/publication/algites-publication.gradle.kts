/*
 * Algites shared publication endpoint selection script.
 *
 * This script is an execution-adapter bridge over the Gradle-independent
 * Modustro Builder publication and ResourceEndpoint model.
 */

import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationDestinationSelection
import eu.algites.pltf.modustro.builder.model.resource.AInResourceStability
import eu.algites.pltf.modustro.builder.publication.AIcPublicationDestinationResolver
import eu.algites.pltf.modustro.builder.resource.AIcResourceEndpointMetadataBridge

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
    }
}

if (!rootProject.extra.has("algitesResolvedArtifactDirectoryMetadata")) {
    val locMetadataResolverWrapper = rootProject.file(
        "gradle/tool/repository/algites-artifact-directory-metadata-resolver-wrapper.gradle.kts"
    )
    if (locMetadataResolverWrapper.isFile) {
        apply(from = locMetadataResolverWrapper)
    } else {
        apply(from = uri(
            "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/repository/algites-artifact-directory-metadata-resolver-wrapper.gradle.kts"
        ))
    }
}

@Suppress("UNCHECKED_CAST")
fun AIcPublicationResourceEndpoints(aMetadata: Map<String, Any?>): Map<*, *> =
    aMetadata["resourceEndpoints"] as? Map<*, *> ?: emptyMap<Any, Any>()

fun AIcPublicationVisibility(aRepositoryVisibility: String): String = when (aRepositoryVisibility.trim().lowercase()) {
    "pub", "public" -> "public"
    "priv", "private" -> "private"
    else -> error("Unsupported Algites source repository visibility '$aRepositoryVisibility'.")
}

fun AIcPublicationDestinationIds(aValue: String?): List<String> = aValue
    ?.split(',')
    ?.map(String::trim)
    ?.filter(String::isNotBlank)
    ?: emptyList()

val locPublicationDestinationResolver = AIcPublicationDestinationResolver()
val locPublicationMetadataBridge = AIcResourceEndpointMetadataBridge.builtin()

val locResolvePublicationDestinations = fun(
    aMetadata: Map<String, Any?>,
    aResourceKind: String,
    aRepositoryVisibility: String,
    aStability: String?,
    aPublicationDestinationIds: List<String>
): AIcPublicationDestinationSelection {
    val locCatalog = locPublicationMetadataBridge.resolve(AIcPublicationResourceEndpoints(aMetadata))
    val locStability = aStability?.trim()?.takeIf(String::isNotBlank)?.let(AInResourceStability::fromWireValue)
    return locPublicationDestinationResolver.selectUploadDestinations(
        locCatalog,
        aResourceKind,
        AIcPublicationVisibility(aRepositoryVisibility),
        locStability,
        aPublicationDestinationIds
    )
}

rootProject.extra["algitesResolvePublicationDestinations"] = locResolvePublicationDestinations
rootProject.extra["algitesPublicationDestinationIds"] = { aValue: String? -> AIcPublicationDestinationIds(aValue) }
