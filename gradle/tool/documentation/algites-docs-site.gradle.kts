/*
 * Algites generic documentation site script.
 *
 * Intended location in governance repository:
 *   gradle/tool/documentation/algites-docs-site.gradle.kts
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

val locAlgitesDocsBaseScript = (findProperty("algites.docs.baseScript") as String?)
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/algites-docs-site-base.gradle.kts"

val locAlgitesDocsJavaScript = (findProperty("algites.docs.javaScript") as String?)
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/algites-docs-site-java.gradle.kts"

val locAlgitesDocsPythonScript = (findProperty("algites.docs.pythonScript") as String?)
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/algites-docs-site-python.gradle.kts"

val locAlgitesDocsMpsScript = (findProperty("algites.docs.mpsScript") as String?)
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/algites-docs-site-mps.gradle.kts"

apply(from = uri(locAlgitesDocsBaseScript))

@Suppress("UNCHECKED_CAST")
val locResolvedArtifactDirectories = extra.properties["algitesDocsResolvedArtifactDirectories"] as? List<Map<String, String?>>
    ?: emptyList()

@Suppress("UNCHECKED_CAST")
val locEffectiveDocumentationKinds = extra.properties["algitesDocsEffectiveTechnologyKinds"] as? Set<String>
    ?: emptySet()

val locDocsRepositoryId = (extra.properties["algitesDocsResolvedRepositoryId"] as String?) ?: rootProject.name
val locDocsCapabilityPlanner = AIcBuiltinCapabilityDemandPlanner()
val locDocsCapabilityDemands = mutableListOf(
    AIcCapabilityDemand(
        AIcCapabilityDemandKey(
            "modustro",
            "publication_of_docs_site",
            AInModelScope.REPOSITORY,
            locDocsRepositoryId
        ),
        setOf("task:generateAlgitesDocsSite")
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
                setOf("task:generateAlgitesDocsSite")
            )
        )
    }
}
val locDocsCapabilityGraph = locDocsCapabilityPlanner.createDemandGraph(
    locDocsRepositoryId,
    emptyList(),
    locDocsCapabilityDemands
)
extra["algitesDocsCapabilityDemandIds"] = locDocsCapabilityGraph.topologicalOrder()
    .map { locDemand -> locDemand.key().toString() }
logger.lifecycle(
    "Modustro documentation capability demands: " +
        locDocsCapabilityGraph.topologicalOrder().joinToString(", ") { locDemand ->
            "${locDemand.key().technologyKind()}:${locDemand.key().capabilityId()}@${locDemand.key().scope().wireValue()}"
        }
)

logger.lifecycle("Algites documentation artifact directory resolution:")
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
