/*
 * Modustro Builder snapshot publishing invocation overrides.
 *
 * Release publishing intentionally does not consume these values. The resolved
 * strings are exported as primitive data so no typed JVM model crosses script
 * or isolated-build classloader boundaries.
 */

val locModustroAllowedPublishingOverrides = setOf("DEFAULT", "FORCE_ON", "FORCE_OFF")

fun AIcModustroPublishingOverride(
    aPropertyName: String,
    aEnvironmentName: String
): String {
    val locValue = providers.gradleProperty(aPropertyName)
        .orElse(providers.environmentVariable(aEnvironmentName))
        .orElse("DEFAULT")
        .get()
        .trim()
        .uppercase()
    if (locValue !in locModustroAllowedPublishingOverrides) {
        throw GradleException(
            "$aPropertyName/$aEnvironmentName must be one of " +
                locModustroAllowedPublishingOverrides.sorted().joinToString(", ") + "."
        )
    }
    return locValue
}

val locModustroPublishingInvocationOverrides = linkedMapOf(
    "native_binary_output" to AIcModustroPublishingOverride(
        "modustro.publishing.nativeBinaryOutput",
        "MODUSTRO_PUBLISHING_NATIVE_BINARY_OUTPUT"
    ),
    "native_source_output" to AIcModustroPublishingOverride(
        "modustro.publishing.nativeSourceOutput",
        "MODUSTRO_PUBLISHING_NATIVE_SOURCE_OUTPUT"
    ),
    "native_documentation_output" to AIcModustroPublishingOverride(
        "modustro.publishing.nativeDocumentationOutput",
        "MODUSTRO_PUBLISHING_NATIVE_DOCUMENTATION_OUTPUT"
    ),
    "modustro_docs_site" to AIcModustroPublishingOverride(
        "modustro.publishing.modustroDocsSite",
        "MODUSTRO_PUBLISHING_MODUSTRO_DOCS_SITE"
    ),
    "schema_site" to AIcModustroPublishingOverride(
        "modustro.publishing.schemaSite",
        "MODUSTRO_PUBLISHING_SCHEMA_SITE"
    )
)

/**
 * Resolves output-level PublishingEnabled for one invocation.
 *
 * Snapshot overrides affect only PublishingEnabled. Endpoint Enabled flags are
 * deliberately outside this function. Release publishing is descriptor-only;
 * a non-default runtime override is rejected rather than silently ignored.
 */
fun AIcModustroEffectivePublishingEnabled(
    aOutputKind: String,
    aStability: String,
    aConfiguredPublishingEnabled: Boolean
): Boolean {
    val locStability = aStability.trim().lowercase()
    if (locStability == "release") {
        val locOverride = locModustroPublishingInvocationOverrides[aOutputKind]
            ?: throw GradleException("Unsupported publishing output kind '$aOutputKind'.")
        if (locOverride != "DEFAULT") {
            throw GradleException(
                "Release publishing does not permit runtime override '$locOverride' for output kind '$aOutputKind'."
            )
        }
        return aConfiguredPublishingEnabled
    }
    if (locStability != "snapshot") {
        throw GradleException("Unsupported publishing stability '$aStability'.")
    }
    return when (locModustroPublishingInvocationOverrides[aOutputKind]
        ?: throw GradleException("Unsupported publishing output kind '$aOutputKind'.")) {
        "DEFAULT" -> aConfiguredPublishingEnabled
        "FORCE_ON" -> true
        "FORCE_OFF" -> false
        else -> error("Unreachable Modustro publishing override state.")
    }
}

@Suppress("UNCHECKED_CAST")
fun AIcModustroEffectivePublishingPlan(
    aMetadata: Map<String, Any?>,
    aOutputKind: String,
    aStability: String
): Map<String, Any?> {
    val locOutputPublishing = aMetadata["outputPublishing"] as? Map<String, Any?> ?: emptyMap()
    val locOutput = locOutputPublishing[aOutputKind] as? Map<String, Any?> ?: emptyMap()
    val locStabilityKey = aStability.trim().lowercase()
    val locBranch = locOutput[locStabilityKey] as? Map<String, Any?> ?: emptyMap()
    val locConfiguredEnabled = locBranch["publishingEnabled"] as? Boolean ?: false
    val locEndpoints = (locBranch["publishingEndpoints"] as? List<Map<String, Any?>>).orEmpty()
    return linkedMapOf(
        "publishingEnabled" to AIcModustroEffectivePublishingEnabled(
            aOutputKind,
            locStabilityKey,
            locConfiguredEnabled
        ),
        "publishingEndpoints" to locEndpoints
    )
}

extra["modustroPublishingInvocationOverrides"] = locModustroPublishingInvocationOverrides.toMap()
extra["modustroEffectivePublishingEnabled"] = { aOutputKind: String, aStability: String, aConfigured: Boolean ->
    AIcModustroEffectivePublishingEnabled(aOutputKind, aStability, aConfigured)
}
extra["modustroEffectivePublishingPlan"] = { aMetadata: Map<String, Any?>, aOutputKind: String, aStability: String ->
    AIcModustroEffectivePublishingPlan(aMetadata, aOutputKind, aStability)
}

tasks.register("printModustroPublishingOverrides") {
    group = "modustro"
    description = "Prints snapshot publishing overrides and resolved effective snapshot publishing plans."
    doLast {
        locModustroPublishingInvocationOverrides.forEach { (locOutputKind, locOverride) ->
            println("$locOutputKind=$locOverride")
        }
        if (rootProject.extra.has("modustroResolvedArtifactDirectoryMetadata")) {
            @Suppress("UNCHECKED_CAST")
            val locDirectories = rootProject.extra["modustroResolvedArtifactDirectoryMetadata"] as? List<Map<String, Any?>>
                ?: emptyList()
            locDirectories.sortedBy { locMetadata -> locMetadata["path"]?.toString().orEmpty() }.forEach { locMetadata ->
                val locPath = locMetadata["path"]?.toString().orEmpty().ifBlank { "." }
                locModustroPublishingInvocationOverrides.keys.forEach { locOutputKind ->
                    val locPlan = AIcModustroEffectivePublishingPlan(locMetadata, locOutputKind, "snapshot")
                    println("$locPath:$locOutputKind=$locPlan")
                }
            }
        }
    }
}

/** Prints effective repository-level publishing enablement for all five snapshot/release output kinds. */
tasks.register("printModustroRepositoryPublishingPlan") {
    group = "modustro"
    description = "Prints effective repository-level PublishingEnabled values after invocation overrides."
    doLast {
        @Suppress("UNCHECKED_CAST")
        val locRepositoryMetadata = rootProject.extra["modustroResolvedRepositoryMetadata"] as? Map<String, Any?>
            ?: emptyMap()
        val locStability = providers.gradleProperty("modustro.publishing.query.stability")
            .orElse("snapshot")
            .get()
            .trim()
            .lowercase()
        locModustroPublishingInvocationOverrides.keys.forEach { locOutputKind ->
            val locPlan = AIcModustroEffectivePublishingPlan(locRepositoryMetadata, locOutputKind, locStability)
            println("$locOutputKind=${locPlan["publishingEnabled"]}")
        }
    }
}

