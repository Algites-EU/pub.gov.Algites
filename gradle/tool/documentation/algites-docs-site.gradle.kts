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

val locAlgitesDocsBaseScript = (findProperty("algites.docs.baseScript") as String?)
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/algites-docs-site-base.gradle.kts"

val locAlgitesDocsJavaScript = (findProperty("algites.docs.javaScript") as String?)
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/algites-docs-site-java.gradle.kts"

val locAlgitesDocsMpsScript = (findProperty("algites.docs.mpsScript") as String?)
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/algites-docs-site-mps.gradle.kts"

apply(from = uri(locAlgitesDocsBaseScript))

@Suppress("UNCHECKED_CAST")
val locResolvedArtifactDirectories = extra.properties["algitesDocsResolvedArtifactDirectories"] as? List<Map<String, String?>>
    ?: emptyList()

val locResolvedDocumentationTypes = locResolvedArtifactDirectories
    .mapNotNull { locArtifactDirectory -> locArtifactDirectory["type"]?.lowercase()?.takeIf { it.isNotBlank() } }
    .toSet()

logger.lifecycle("Algites documentation artifact directory resolution:")
locResolvedArtifactDirectories.forEach { locArtifactDirectory ->
    logger.lifecycle(
        " - ${locArtifactDirectory["path"]}: " +
            "kind=${locArtifactDirectory["kind"]}, " +
            "type=${locArtifactDirectory["type"]}, " +
            "contentsModel=${locArtifactDirectory["contentsModel"]}, " +
            "version=${locArtifactDirectory["version.resolvedValue"]}"
    )
}

if ("java" in locResolvedDocumentationTypes) {
    apply(from = uri(locAlgitesDocsJavaScript))
}

if ("mps" in locResolvedDocumentationTypes) {
    apply(from = uri(locAlgitesDocsMpsScript))
}
