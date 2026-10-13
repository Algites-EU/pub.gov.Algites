package eu.algites.pltf.modustro.builder.gradleinit

/** Safe publication-plan diagnostics. Never serializes credential definitions or credential values. */
object AIcModustroPublicationCredentialDiagnostics {
    @JvmStatic
    fun AIcProfileIds(aProfiles: Map<String, Any?>): String =
        aProfiles.keys.sorted().joinToString(", ", prefix = "[", postfix = "]")

    /** Describes effective endpoint references without emitting URIs, configuration, or secrets. */
    @JvmStatic
    fun AIcEndpoints(aPlan: Map<String, Any?>): String {
        val locEndpoints = (aPlan["publicationEndpoints"] as? List<*>).orEmpty()
        if (locEndpoints.isEmpty()) return "[]"
        return locEndpoints.mapIndexed { aIndex, aItem ->
            val locEndpoint = aItem as? Map<*, *>
            if (locEndpoint == null) return@mapIndexed "#$aIndex:invalid-endpoint"
            val locId = locEndpoint["id"] ?: locEndpoint["Id"] ?: "<missing>"
            val locAdapter = locEndpoint["publicationAdapter"] ?: locEndpoint["PublicationAdapter"] ?: "<missing>"
            val locProfile = locEndpoint["publicationCredentialProfile"] ?: locEndpoint["PublicationCredentialProfile"] ?: "<none>"
            val locEnabled = locEndpoint["executionEnabled"] ?: locEndpoint["ExecutionEnabled"] ?: true
            "#$aIndex(id=$locId, adapter=$locAdapter, profile=$locProfile, executionEnabled=$locEnabled)"
        }.joinToString(", ", prefix = "[", postfix = "]")
    }

    /** Compares the task execution snapshot with the preflight/preparation snapshot. */
    @JvmStatic
    fun AIcDescribe(
        aOutputId: String,
        aArtifactIdentity: String,
        aTechnologyKind: String,
        aOutputKind: String,
        aPlan: Map<String, Any?>,
        aProfiles: Map<String, Any?>,
        aPreparedPlan: Map<String, Any?>?,
        aPreparedProfiles: Map<String, Any?>?,
        aSiblingOutputs: List<String>
    ): String {
        val locRequestedProfiles = (aPlan["publicationEndpoints"] as? List<*>).orEmpty()
            .mapNotNull { aEndpoint ->
                val locDefinition = aEndpoint as? Map<*, *> ?: return@mapNotNull null
                (locDefinition["publicationCredentialProfile"] ?: locDefinition["PublicationCredentialProfile"])
                    ?.toString()?.takeIf { it.isNotBlank() }
            }.distinct().sorted()
        return buildString {
            appendLine("Modustro publication failure diagnostic (credential IDs only; no credential values or endpoint URIs):")
            appendLine("  outputId: $aOutputId")
            appendLine("  artifactIdentity: $aArtifactIdentity")
            appendLine("  technologyKind: $aTechnologyKind")
            appendLine("  outputKind: $aOutputKind")
            appendLine("  executionEndpoints: ${AIcEndpoints(aPlan)}")
            appendLine("  executionRequiredProfileIds: $locRequestedProfiles")
            appendLine("  executionAvailableProfileIds: ${AIcProfileIds(aProfiles)}")
            appendLine("  executionMissingProfileIds: ${locRequestedProfiles.filterNot { it in aProfiles.keys }}")
            if (aPreparedPlan == null || aPreparedProfiles == null) {
                appendLine("  preparedInput: unavailable")
            } else {
                appendLine("  preparedEndpoints: ${AIcEndpoints(aPreparedPlan)}")
                appendLine("  preparedAvailableProfileIds: ${AIcProfileIds(aPreparedProfiles)}")
                appendLine("  preparedProfileIdsMatchExecution: ${aPreparedProfiles.keys == aProfiles.keys}")
                appendLine("  preparedProfileDefinitionsMatchExecution: ${aPreparedProfiles == aProfiles}")
                appendLine("  preparedPlanMatchesExecution: ${aPreparedPlan == aPlan}")
            }
            appendLine("  preparedSiblingOutputs:")
            if (aSiblingOutputs.isEmpty()) appendLine("    (none)")
            else aSiblingOutputs.sorted().forEach { appendLine("    $it") }
        }
    }
}
