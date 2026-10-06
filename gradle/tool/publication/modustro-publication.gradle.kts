/*
 * Modustro Builder shared publication-plan bridge.
 *
 * Publication targets come exclusively from the effective OutputPublications model.
 * InputSubscriptions are an independent input-resolution concern.
 */

if (!rootProject.extra.has("modustroResolvedArtifactDirectoryMetadata")) {
    rootProject.pluginManager.apply("eu.algites.pltf.modustro.builder.repository")
}

if (!rootProject.extra.has("modustroEffectivePublicationPlan")) {
    val locSourceRepositoryRoot = generateSequence(rootProject.projectDir.canonicalFile) { it.parentFile }
        .firstOrNull { locDirectory -> locDirectory.resolve("modustro-source-repository.yml").isFile }
        ?: throw GradleException(
            "Cannot locate modustro-source-repository.yml on the ancestor path of Gradle build root '${rootProject.projectDir.path}'."
        )
    val locOverridesScript = locSourceRepositoryRoot.resolve(
        "gradle/tool/repository/modustro-publication-overrides.gradle.kts"
    )
    if (locOverridesScript.isFile) {
        apply(from = locOverridesScript)
    } else {
        apply(from = uri(
            "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/${System.getenv("MODUSTRO_PUBLIC_GOVERNANCE_REVISION") ?: "main"}/gradle/tool/repository/modustro-publication-overrides.gradle.kts"
        ))
    }
}

fun AIcPublicationDestinationIds(aValue: String?): List<String> = aValue
    ?.split(',')
    ?.map(String::trim)
    ?.filter(String::isNotBlank)
    ?: emptyList()

@Suppress("UNCHECKED_CAST")
val locEffectivePublicationPlanResolver = rootProject.extra["modustroEffectivePublicationPlan"] as (
    Map<String, Any?>,
    String,
    String
) -> Map<String, Any?>

val locResolvePublicationPlan = fun(
    aMetadata: Map<String, Any?>,
    aOutputKind: String,
    aStability: String,
    aPublicationDestinationIds: List<String>
): Map<String, Any?> {
    val locTypedMetadata = if (aOutputKind in setOf("modustro_docs_site", "schema_site")) {
        aMetadata + ("publicationTechnologyKind" to "modustro")
    } else aMetadata
    val locPlan = locEffectivePublicationPlanResolver(locTypedMetadata, aOutputKind, aStability)
    val locEnabled = locPlan["publicationEnabled"] as? Boolean ?: false
    val locAllEndpoints = (locPlan["publicationEndpoints"] as? List<Map<String, Any?>>).orEmpty()
    val locEnabledEndpoints = if (locEnabled) {
        locAllEndpoints.filter { locEndpoint -> locEndpoint["executionEnabled"] as? Boolean ?: true }
    } else {
        emptyList()
    }
    val locSelectedEndpoints = if (aPublicationDestinationIds.isEmpty()) {
        locEnabledEndpoints
    } else {
        val locById = locEnabledEndpoints.associateBy { locEndpoint -> locEndpoint["id"]?.toString().orEmpty() }
        val locUnknown = aPublicationDestinationIds.filter { locId -> locId !in locById }
        if (locUnknown.isNotEmpty()) {
            throw GradleException(
                "Publication destination id(s) ${locUnknown.joinToString(", ")} do not identify enabled " +
                    "$aOutputKind/$aStability PublicationEndpoints."
            )
        }
        aPublicationDestinationIds.map { locId -> locById.getValue(locId) }
    }
    return locPlan + linkedMapOf(
        "publicationEnabled" to locEnabled,
        "publicationEndpoints" to locSelectedEndpoints,
        "publicationEndpointRegistry" to (locPlan["publicationEndpointRegistry"] ?: emptyList<Map<String, Any?>>())
    )
}

rootProject.extra["modustroResolvePublicationPlan"] = locResolvePublicationPlan
rootProject.extra["modustroPublicationDestinationIds"] = { aValue: String? -> AIcPublicationDestinationIds(aValue) }
