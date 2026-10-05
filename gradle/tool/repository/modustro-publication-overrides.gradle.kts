/*
 * Modustro Builder snapshot publication invocation overrides.
 *
 * Release publication intentionally does not consume these values. The resolved
 * strings are exported as primitive data so no typed JVM model crosses script
 * or isolated-build classloader boundaries.
 */

val locModustroAllowedPublicationOverrides = setOf("DEFAULT", "FORCE_ON", "FORCE_OFF")

fun AIcModustroPublicationOverride(
    aPropertyName: String,
    aEnvironmentName: String
): String {
    val locValue = providers.gradleProperty(aPropertyName)
        .orElse(providers.environmentVariable(aEnvironmentName))
        .orElse("DEFAULT")
        .get()
        .trim()
        .uppercase()
    if (locValue !in locModustroAllowedPublicationOverrides) {
        throw GradleException(
            "$aPropertyName/$aEnvironmentName must be one of " +
                locModustroAllowedPublicationOverrides.sorted().joinToString(", ") + "."
        )
    }
    return locValue
}

val locModustroPublicationInvocationOverrides = linkedMapOf(
    "native_product_binaries" to AIcModustroPublicationOverride(
        "modustro.publication.nativeProductBinaries", "MODUSTRO_PUBLICATION_NATIVE_PRODUCT_BINARIES"),
    "native_product_sources" to AIcModustroPublicationOverride(
        "modustro.publication.nativeProductSources", "MODUSTRO_PUBLICATION_NATIVE_PRODUCT_SOURCES"),
    "native_product_documentation" to AIcModustroPublicationOverride(
        "modustro.publication.nativeProductDocumentation", "MODUSTRO_PUBLICATION_NATIVE_PRODUCT_DOCUMENTATION"),
    "native_develop_sources" to AIcModustroPublicationOverride(
        "modustro.publication.nativeDevelopSources", "MODUSTRO_PUBLICATION_NATIVE_DEVELOP_SOURCES"),
    "native_develop_binaries" to AIcModustroPublicationOverride(
        "modustro.publication.nativeDevelopBinaries", "MODUSTRO_PUBLICATION_NATIVE_DEVELOP_BINARIES"),
    "native_develop_documentation" to AIcModustroPublicationOverride(
        "modustro.publication.nativeDevelopDocumentation", "MODUSTRO_PUBLICATION_NATIVE_DEVELOP_DOCUMENTATION"),
    "modustro_docs_site" to AIcModustroPublicationOverride(
        "modustro.publication.modustroDocsSite", "MODUSTRO_PUBLICATION_MODUSTRO_DOCS_SITE"),
    "schema_site" to AIcModustroPublicationOverride(
        "modustro.publication.schemaSite", "MODUSTRO_PUBLICATION_SCHEMA_SITE")
)

/** Resolves output-level PublicationEnabled for one invocation. */
fun AIcModustroEffectivePublicationEnabled(
    aOutputKind: String,
    aStability: String,
    aConfiguredPublicationEnabled: Boolean
): Boolean {
    val locStability = aStability.trim().lowercase()
    val locOverride = locModustroPublicationInvocationOverrides[aOutputKind]
        ?: throw GradleException("Unsupported publication output kind '$aOutputKind'.")
    if (locStability == "release") {
        if (locOverride != "DEFAULT") {
            throw GradleException(
                "Release publication does not permit runtime override '$locOverride' for output kind '$aOutputKind'."
            )
        }
        return aConfiguredPublicationEnabled
    }
    if (locStability != "snapshot") {
        throw GradleException("Unsupported publication stability '$aStability'.")
    }
    return when (locOverride) {
        "DEFAULT" -> aConfiguredPublicationEnabled
        "FORCE_ON" -> true
        "FORCE_OFF" -> false
        else -> error("Unreachable Modustro publication override state.")
    }
}

@Suppress("UNCHECKED_CAST")
fun AIcModustroEffectivePublicationPlan(
    aMetadata: Map<String, Any?>,
    aOutputKind: String,
    aStability: String
): Map<String, Any?> {
    val locOutputPublications = aMetadata["outputPublications"] as? Map<String, Any?> ?: emptyMap()
    val locTechnologyKind = aMetadata["publicationTechnologyKind"]?.toString()?.trim()?.takeIf { it.isNotBlank() }
        ?: throw GradleException("Effective publication plan for '$aOutputKind' requires TechnologyKind.")
    val locKey = "$locTechnologyKind.$aOutputKind"
    val locOutput = locOutputPublications[locKey] as? Map<String, Any?> ?: emptyMap()
    val locStabilityKey = aStability.trim().lowercase()
    val locBranch = locOutput[locStabilityKey] as? Map<String, Any?> ?: emptyMap()
    val locConfiguredEnabled = locBranch["publicationEnabled"] as? Boolean ?: false
    val locEndpoints = (locBranch["publicationEndpoints"] as? List<Map<String, Any?>>).orEmpty()

    val locRegistryById = linkedMapOf<String, Map<String, Any?>>()
    listOf("snapshot", "release").forEach { locLane ->
        val locLaneMap = locOutput[locLane] as? Map<String, Any?> ?: emptyMap()
        (locLaneMap["publicationEndpoints"] as? List<Map<String, Any?>>).orEmpty().forEach { locEndpoint ->
            val locId = locEndpoint["id"]?.toString()?.takeIf { it.isNotBlank() } ?: return@forEach
            locRegistryById[locId] = locEndpoint
        }
    }

    return linkedMapOf(
        "publicationEnabled" to AIcModustroEffectivePublicationEnabled(
            aOutputKind, locStabilityKey, locConfiguredEnabled),
        "publicationEndpoints" to locEndpoints,
        "publicationEndpointRegistry" to locRegistryById.values.toList()
    )
}

extra["modustroPublicationInvocationOverrides"] = locModustroPublicationInvocationOverrides.toMap()
extra["modustroEffectivePublicationEnabled"] = { aOutputKind: String, aStability: String, aConfigured: Boolean ->
    AIcModustroEffectivePublicationEnabled(aOutputKind, aStability, aConfigured)
}
extra["modustroEffectivePublicationPlan"] = { aMetadata: Map<String, Any?>, aOutputKind: String, aStability: String ->
    AIcModustroEffectivePublicationPlan(aMetadata, aOutputKind, aStability)
}

tasks.register("printModustroPublicationOverrides") {
    group = "modustro"
    description = "Prints snapshot publication overrides and resolved effective snapshot publication plans."
    doLast {
        locModustroPublicationInvocationOverrides.forEach { (locOutputKind, locOverride) ->
            println("$locOutputKind=$locOverride")
        }
        if (rootProject.extra.has("modustroResolvedArtifactDirectoryMetadata")) {
            @Suppress("UNCHECKED_CAST")
            val locDirectories = rootProject.extra["modustroResolvedArtifactDirectoryMetadata"] as? List<Map<String, Any?>>
                ?: emptyList()
            locDirectories.sortedBy { locMetadata -> locMetadata["path"]?.toString().orEmpty() }.forEach { locMetadata ->
                val locPath = locMetadata["path"]?.toString().orEmpty().ifBlank { "." }
                val locTechnologyKinds = (locMetadata["technologyKinds"] as? List<*>).orEmpty().mapNotNull { it?.toString() }
                locTechnologyKinds.forEach { locTechnologyKind ->
                    locModustroPublicationInvocationOverrides.keys.forEach { locOutputKind ->
                        val locPlan = AIcModustroEffectivePublicationPlan(
                            locMetadata + ("publicationTechnologyKind" to locTechnologyKind), locOutputKind, "snapshot")
                        println("$locPath:$locTechnologyKind:$locOutputKind=$locPlan")
                    }
                }
            }
        }
    }
}

tasks.register("printModustroRepositoryPublicationPlan") {
    group = "modustro"
    description = "Prints effective repository-level PublicationEnabled values after invocation overrides."
    doLast {
        @Suppress("UNCHECKED_CAST")
        val locRepositoryMetadata = rootProject.extra["modustroResolvedRepositoryMetadata"] as? Map<String, Any?>
            ?: emptyMap()
        val locStability = providers.gradleProperty("modustro.publication.query.stability")
            .orElse("snapshot").get().trim().lowercase()
        val locTechnologyKinds = (locRepositoryMetadata["technologyKinds"] as? List<*>).orEmpty().mapNotNull { it?.toString() }
        locTechnologyKinds.forEach { locTechnologyKind ->
            locModustroPublicationInvocationOverrides.keys.forEach { locOutputKind ->
                val locPlan = AIcModustroEffectivePublicationPlan(
                    locRepositoryMetadata + ("publicationTechnologyKind" to locTechnologyKind), locOutputKind, locStability)
                println("$locTechnologyKind:$locOutputKind=${locPlan["publicationEnabled"]}")
            }
        }
    }
}
