/*
 * Modustro Builder documentation site script.
 *
 * Intended location in governance repository:
 *   gradle/tool/documentation/modustro-docs-site.gradle.kts
 *
 * This script is the public entry point for repository documentation generation.
 * It applies the common base script, uses the shared repository metadata
 * resolver output, and then applies the required technology-specific
 * documentation scripts.
 */

import eu.algites.pltf.modustro.builder.capability.AIcBuiltinCapabilityDemandPlanner
import eu.algites.pltf.modustro.builder.model.AInModelScope
import eu.algites.pltf.modustro.builder.model.capability.AIcCapabilityDemand
import eu.algites.pltf.modustro.builder.model.capability.AIcCapabilityDemandKey

val locAlgitesDocsBaseScript = (findProperty("modustro.docs.baseScript") as String?)
    ?: rootProject.file("gradle/tool/documentation/modustro-docs-site-base.gradle.kts")
        .takeIf { it.isFile }?.toURI()?.toString()
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/modustro-docs-site-base.gradle.kts"

val locAlgitesDocsJavaScript = (findProperty("modustro.docs.javaScript") as String?)
    ?: rootProject.file("gradle/tool/documentation/modustro-docs-site-java.gradle.kts")
        .takeIf { it.isFile }?.toURI()?.toString()
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/modustro-docs-site-java.gradle.kts"

val locAlgitesDocsPythonScript = (findProperty("modustro.docs.pythonScript") as String?)
    ?: rootProject.file("gradle/tool/documentation/modustro-docs-site-python.gradle.kts")
        .takeIf { it.isFile }?.toURI()?.toString()
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/modustro-docs-site-python.gradle.kts"

val locAlgitesDocsMpsScript = (findProperty("modustro.docs.mpsScript") as String?)
    ?: rootProject.file("gradle/tool/documentation/modustro-docs-site-mps.gradle.kts")
        .takeIf { it.isFile }?.toURI()?.toString()
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/modustro-docs-site-mps.gradle.kts"

apply(from = uri(locAlgitesDocsBaseScript))

@Suppress("UNCHECKED_CAST")
val locResolvedArtifactDirectories = extra.properties["modustroDocsResolvedArtifactDirectories"] as? List<Map<String, String?>>
    ?: emptyList()

@Suppress("UNCHECKED_CAST")
val locEffectiveDocumentationKinds = extra.properties["modustroDocsEffectiveTechnologyKinds"] as? Set<String>
    ?: emptySet()

val locDocsRepositoryId = (extra.properties["modustroDocsResolvedRepositoryId"] as String?) ?: rootProject.name
val locDocsCapabilityPlanner = AIcBuiltinCapabilityDemandPlanner()
val locDocsCapabilityDemands = mutableListOf(
    AIcCapabilityDemand(
        AIcCapabilityDemandKey(
            "modustro",
            "publication_of_docs_site",
            AInModelScope.REPOSITORY,
            locDocsRepositoryId
        ),
        setOf("task:generateModustroDocsSite")
    )
)
locResolvedArtifactDirectories.forEach { locArtifactDirectory ->
    val locScope = when (locArtifactDirectory["structureKind"]) {
        "repository" -> AInModelScope.REPOSITORY
        "artifact-set" -> AInModelScope.ARTIFACT_SET
        "artifact" -> AInModelScope.ARTIFACT
        else -> null
    }
    val locScopeIdentity = locArtifactDirectory["path"]?.takeIf { it.isNotBlank() } ?: locDocsRepositoryId
    if (locScope != null) {
        locDocsCapabilityDemands.add(
            AIcCapabilityDemand(
                AIcCapabilityDemandKey(
                    "modustro",
                    "docs_site_content",
                    locScope,
                    locScopeIdentity
                ),
                setOf("task:generateModustroDocsSite")
            )
        )
    }
}
val locDocsCapabilityGraph = locDocsCapabilityPlanner.createDemandGraph(
    locDocsRepositoryId,
    emptyList(),
    locDocsCapabilityDemands
)
extra["modustroDocsCapabilityDemandIds"] = locDocsCapabilityGraph.topologicalOrder()
    .map { locDemand -> locDemand.key().toString() }
logger.lifecycle(
    "Modustro documentation capability demands: " +
        locDocsCapabilityGraph.topologicalOrder().joinToString(", ") { locDemand ->
            "${locDemand.key().technologyKind()}:${locDemand.key().capabilityId()}@${locDemand.key().scope().wireValue()}"
        }
)

logger.lifecycle("Modustro documentation artifact directory resolution:")
locResolvedArtifactDirectories.forEach { locArtifactDirectory ->
    logger.lifecycle(
        " - ${locArtifactDirectory["path"]}: " +
            "structureKind=${locArtifactDirectory["structureKind"]}, " +
            "technologyKinds=${locArtifactDirectory["technologyKinds"]}, " +
            "contentsModel=${locArtifactDirectory["contentsModel"]}, " +
            "version=${locArtifactDirectory["version.resolvedValue"]}"
    )
}

if ("java" in locEffectiveDocumentationKinds) {
    apply(from = uri(locAlgitesDocsJavaScript))
}

if ("python" in locEffectiveDocumentationKinds) {
    apply(from = uri(locAlgitesDocsPythonScript))
}

if ("mps" in locEffectiveDocumentationKinds) {
    apply(from = uri(locAlgitesDocsMpsScript))
}
