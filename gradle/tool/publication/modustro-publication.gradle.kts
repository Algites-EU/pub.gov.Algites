/*
 * Modustro Builder shared publishing-plan bridge.
 *
 * Publishing targets come exclusively from the effective outputPublishing model.
 * ResourceEndpoints remain available for non-publishing resource resolution only.
 */

if (!rootProject.extra.has("modustroResolvedArtifactDirectoryMetadata")) {
    val locMetadataResolverWrapper = rootProject.file(
        "gradle/tool/repository/modustro-artifact-directory-metadata-resolver-wrapper.gradle.kts"
    )
    if (locMetadataResolverWrapper.isFile) {
        apply(from = locMetadataResolverWrapper)
    } else {
        apply(from = uri(
            "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/repository/modustro-artifact-directory-metadata-resolver-wrapper.gradle.kts"
        ))
    }
}

if (!rootProject.extra.has("modustroEffectivePublishingPlan")) {
    val locSourceRepositoryRoot = generateSequence(rootProject.projectDir.canonicalFile) { it.parentFile }
        .firstOrNull { locDirectory -> locDirectory.resolve("modustro-source-repository.yml").isFile }
        ?: throw GradleException(
            "Cannot locate modustro-source-repository.yml on the ancestor path of Gradle build root '${rootProject.projectDir.path}'."
        )
    val locOverridesScript = locSourceRepositoryRoot.resolve(
        "gradle/tool/repository/modustro-publishing-overrides.gradle.kts"
    )
    if (!locOverridesScript.isFile) {
        throw GradleException("Modustro publishing override script is missing: '${locOverridesScript.path}'.")
    }
    apply(from = locOverridesScript)
}

fun AIcPublishingDestinationIds(aValue: String?): List<String> = aValue
    ?.split(',')
    ?.map(String::trim)
    ?.filter(String::isNotBlank)
    ?: emptyList()

@Suppress("UNCHECKED_CAST")
val locEffectivePublishingPlanResolver = rootProject.extra["modustroEffectivePublishingPlan"] as (
    Map<String, Any?>,
    String,
    String
) -> Map<String, Any?>

val locResolvePublishingPlan = fun(
    aMetadata: Map<String, Any?>,
    aOutputKind: String,
    aStability: String,
    aPublishingDestinationIds: List<String>
): Map<String, Any?> {
    val locPlan = locEffectivePublishingPlanResolver(aMetadata, aOutputKind, aStability)
    val locEnabled = locPlan["publishingEnabled"] as? Boolean ?: false
    val locAllEndpoints = (locPlan["publishingEndpoints"] as? List<Map<String, Any?>>).orEmpty()
    val locEnabledEndpoints = if (locEnabled) {
        locAllEndpoints.filter { locEndpoint -> locEndpoint["enabled"] as? Boolean ?: true }
    } else {
        emptyList()
    }
    val locSelectedEndpoints = if (aPublishingDestinationIds.isEmpty()) {
        locEnabledEndpoints
    } else {
        val locById = locEnabledEndpoints.associateBy { locEndpoint -> locEndpoint["id"]?.toString().orEmpty() }
        val locUnknown = aPublishingDestinationIds.filter { locId -> locId !in locById }
        if (locUnknown.isNotEmpty()) {
            throw GradleException(
                "Publishing destination id(s) ${locUnknown.joinToString(", ")} do not identify enabled " +
                    "$aOutputKind/$aStability PublishingEndpoints."
            )
        }
        aPublishingDestinationIds.map { locId -> locById.getValue(locId) }
    }
    linkedMapOf(
        "publishingEnabled" to locEnabled,
        "publishingEndpoints" to locSelectedEndpoints
    )
}

rootProject.extra["modustroResolvePublishingPlan"] = locResolvePublishingPlan
rootProject.extra["modustroPublishingDestinationIds"] = { aValue: String? -> AIcPublishingDestinationIds(aValue) }
