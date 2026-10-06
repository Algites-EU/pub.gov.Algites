package eu.algites.pltf.modustro.builder.gradleinit

/*
 * Modustro Builder artifact directory metadata resolver core.
 *
 * Compiled metadata orchestration with no Gradle API dependency.
 * It resolves structural metadata, TechnologyKinds, inherited groupId, credential profiles,
 * input subscriptions, and version contexts. InputSubscriptions and OutputPublications are
 * resolved as independent, symmetric input/output concerns.
 */

import java.io.File
import java.net.URI
import java.security.MessageDigest


data class AIcdAlgitesVersion(
    val releaseLineVersion: String? = null,
    val revision: String? = null,
    val qualifierKind: String? = null
) {
    fun AIcMerge(aOther: AIcdAlgitesVersion): AIcdAlgitesVersion = AIcdAlgitesVersion(
        releaseLineVersion = aOther.releaseLineVersion ?: releaseLineVersion,
        revision = aOther.revision ?: revision,
        qualifierKind = aOther.qualifierKind ?: qualifierKind
    )

    fun AIcResolvedValue(): String? {
        val locReleaseLineVersion = releaseLineVersion?.takeIf { it.isNotBlank() } ?: return null
        val locRevision = revision?.takeIf { it.isNotBlank() }
        val locBase = if (locRevision == null) locReleaseLineVersion else "$locReleaseLineVersion.$locRevision"
        return if (qualifierKind?.equals("snapshot", true) == true) "$locBase-SNAPSHOT" else locBase
    }
}

data class AIcdAlgitesVersionBoundary(
    val version: String,
    val inclusive: Boolean
)

enum class AInModustroItemsInheritancePolicy {
    MERGE_MISSING_ITEMS,
    REMOVE_MISSING_ITEMS
}

data class AIcdModustroTechnologyKindSelection(
    val technologyKind: String,
    val buildOutputTypes: Set<String>? = null,
    val buildOutputItemsInheritancePolicy: AInModustroItemsInheritancePolicy = AInModustroItemsInheritancePolicy.MERGE_MISSING_ITEMS
) {
    fun AIcMerge(aOther: AIcdModustroTechnologyKindSelection): AIcdModustroTechnologyKindSelection {
        require(technologyKind == aOther.technologyKind) {
            "Cannot merge TechnologyKind selections '$technologyKind' and '${aOther.technologyKind}'."
        }
        val locBuildOutputTypes = when {
            aOther.buildOutputTypes == null -> buildOutputTypes
            buildOutputTypes == null -> aOther.buildOutputTypes
            aOther.buildOutputItemsInheritancePolicy == AInModustroItemsInheritancePolicy.REMOVE_MISSING_ITEMS -> aOther.buildOutputTypes
            else -> LinkedHashSet<String>().apply {
                addAll(buildOutputTypes)
                addAll(aOther.buildOutputTypes)
            }
        }
        return AIcdModustroTechnologyKindSelection(
            technologyKind = technologyKind,
            buildOutputTypes = locBuildOutputTypes,
            buildOutputItemsInheritancePolicy = AInModustroItemsInheritancePolicy.MERGE_MISSING_ITEMS
        )
    }
}

data class AIcdModustroTechnologyKindsConfig(
    val items: List<AIcdModustroTechnologyKindSelection>,
    val itemsInheritancePolicy: AInModustroItemsInheritancePolicy = AInModustroItemsInheritancePolicy.MERGE_MISSING_ITEMS
) {
    fun AIcMerge(aOther: AIcdModustroTechnologyKindsConfig): AIcdModustroTechnologyKindsConfig {
        val locMerged = linkedMapOf<String, AIcdModustroTechnologyKindSelection>()
        items.forEach { locItem -> locMerged[locItem.technologyKind] = locItem }
        if (aOther.itemsInheritancePolicy == AInModustroItemsInheritancePolicy.REMOVE_MISSING_ITEMS) {
            val locLocalKinds = aOther.items.mapTo(linkedSetOf()) { locItem -> locItem.technologyKind }
            locMerged.entries.removeIf { locEntry -> locEntry.key !in locLocalKinds }
        }
        aOther.items.forEach { locItem ->
            locMerged[locItem.technologyKind] = locMerged[locItem.technologyKind]?.AIcMerge(locItem) ?: locItem
        }
        return AIcdModustroTechnologyKindsConfig(locMerged.values.toList())
    }

    fun AIcTechnologyKinds(): List<String> = items.map { locItem -> locItem.technologyKind }

    fun AIcBuildOutputTypesByTechnologyKind(): Map<String, Set<String>> = items
        .filter { locItem -> locItem.buildOutputTypes != null }
        .associate { locItem -> locItem.technologyKind to locItem.buildOutputTypes.orEmpty() }
}

data class AIcdModustroInputSubscription(
    val id: String,
    val enabled: Boolean? = null,
    val visibility: String? = null,
    val stability: String? = null,
    val subscriptionUri: String? = null,
    val subscriptionAdapter: String? = null,
    val subscriptionCredentialProfile: String? = null,
    val subscriptionOrder: Int? = null,
    val configuration: Map<String, String> = emptyMap()
) {
    fun AIcMerge(aOther: AIcdModustroInputSubscription): AIcdModustroInputSubscription {
        require(id == aOther.id) { "Cannot merge input subscriptions with different ids '$id' and '${aOther.id}'." }
        return AIcdModustroInputSubscription(
            id = id,
            enabled = aOther.enabled ?: enabled,
            visibility = aOther.visibility ?: visibility,
            stability = aOther.stability ?: stability,
            subscriptionUri = aOther.subscriptionUri ?: subscriptionUri,
            subscriptionAdapter = aOther.subscriptionAdapter ?: subscriptionAdapter,
            subscriptionCredentialProfile = aOther.subscriptionCredentialProfile ?: subscriptionCredentialProfile,
            subscriptionOrder = aOther.subscriptionOrder ?: subscriptionOrder,
            configuration = configuration + aOther.configuration
        )
    }

    fun AIcEffectiveEnabled(): Boolean = enabled ?: true
    fun AIcEffectiveVisibility(): String = visibility ?: "public"
    fun AIcEffectiveSubscriptionOrder(): Int = subscriptionOrder ?: 0
}

data class AIcdModustroInputSubscriptionsConfiguration(
    val subscriptions: Map<String, AIcdModustroInputSubscription> = emptyMap(),
    val clearSubscriptions: Boolean = false
) {
    fun AIcMerge(aOther: AIcdModustroInputSubscriptionsConfiguration): AIcdModustroInputSubscriptionsConfiguration {
        val locSubscriptions = linkedMapOf<String, AIcdModustroInputSubscription>()
        if (!aOther.clearSubscriptions) subscriptions.forEach { (locId, locSubscription) -> locSubscriptions[locId] = locSubscription }
        aOther.subscriptions.forEach { (locId, locSubscription) ->
            locSubscriptions[locId] = locSubscriptions[locId]?.AIcMerge(locSubscription) ?: locSubscription
        }
        return AIcdModustroInputSubscriptionsConfiguration(
            subscriptions = locSubscriptions,
            clearSubscriptions = aOther.clearSubscriptions || clearSubscriptions
        )
    }
}

data class AIcdModustroPublicationEndpoint(
    val id: String,
    val executionEnabled: Boolean? = null,
    val publicationUri: String? = null,
    val publicationAdapter: String? = null,
    val publicationCredentialProfile: String? = null,
    val executionOrder: Int? = null,
    val publicationFailurePolicy: String? = null,
    val publicationRetryCount: Int? = null,
    val publicationWaitForNextAttemptMillis: Long? = null,
    val publicationAttemptTimeoutMillis: Long? = null,
    val showPublicationProgressIfPossible: Boolean? = null,
    val configuration: Map<String, String> = emptyMap(),
    val publications: List<Map<String, Any?>>? = null
) {
    fun AIcMerge(aOther: AIcdModustroPublicationEndpoint): AIcdModustroPublicationEndpoint {
        require(id == aOther.id) { "Cannot merge publication endpoints with different ids '$id' and '${aOther.id}'." }
        return AIcdModustroPublicationEndpoint(
            id = id,
            executionEnabled = aOther.executionEnabled ?: executionEnabled,
            publicationUri = aOther.publicationUri ?: publicationUri,
            publicationAdapter = aOther.publicationAdapter ?: publicationAdapter,
            publicationCredentialProfile = aOther.publicationCredentialProfile ?: publicationCredentialProfile,
            executionOrder = aOther.executionOrder ?: executionOrder,
            publicationFailurePolicy = aOther.publicationFailurePolicy ?: publicationFailurePolicy,
            publicationRetryCount = aOther.publicationRetryCount ?: publicationRetryCount,
            publicationWaitForNextAttemptMillis = aOther.publicationWaitForNextAttemptMillis ?: publicationWaitForNextAttemptMillis,
            publicationAttemptTimeoutMillis = aOther.publicationAttemptTimeoutMillis ?: publicationAttemptTimeoutMillis,
            showPublicationProgressIfPossible = aOther.showPublicationProgressIfPossible ?: showPublicationProgressIfPossible,
            configuration = configuration + aOther.configuration,
            publications = eu.algites.pltf.modustro.builder.publication.AIcPublicationConfiguration.merge(publications, aOther.publications)
        )
    }

    fun AIcEffectiveExecutionEnabled(): Boolean = executionEnabled ?: true
    fun AIcEffectiveExecutionOrder(): Int = executionOrder ?: 0
    fun AIcEffectivePublicationFailurePolicy(): String = publicationFailurePolicy ?: "FAIL_BUILD_ON_PUBLICATION_FAILURE"
    fun AIcEffectivePublicationRetryCount(): Int = publicationRetryCount ?: 0
    fun AIcEffectivePublicationWaitForNextAttemptMillis(): Long = publicationWaitForNextAttemptMillis ?: 1000L
    fun AIcEffectiveShowPublicationProgressIfPossible(): Boolean = showPublicationProgressIfPossible ?: true
}

data class AIcdModustroPublicationStabilityConfiguration(
    val publicationEnabled: Boolean? = null,
    val publicationEndpoints: Map<String, AIcdModustroPublicationEndpoint> = emptyMap(),
    val clearPublicationEndpoints: Boolean = false,
    val outputPublicationFinalizationActions: List<Map<String, Any?>>? = null
) {
    fun AIcEffectivePublicationEnabled(): Boolean = publicationEnabled ?: false

    fun AIcMerge(aOther: AIcdModustroPublicationStabilityConfiguration): AIcdModustroPublicationStabilityConfiguration {
        val locEndpoints = linkedMapOf<String, AIcdModustroPublicationEndpoint>()
        if (!aOther.clearPublicationEndpoints) publicationEndpoints.forEach { (locId, locEndpoint) -> locEndpoints[locId] = locEndpoint }
        aOther.publicationEndpoints.forEach { (locId, locEndpoint) ->
            locEndpoints[locId] = locEndpoints[locId]?.AIcMerge(locEndpoint) ?: locEndpoint
        }
        return AIcdModustroPublicationStabilityConfiguration(
            publicationEnabled = aOther.publicationEnabled ?: publicationEnabled,
            publicationEndpoints = locEndpoints,
            clearPublicationEndpoints = aOther.clearPublicationEndpoints || clearPublicationEndpoints,
            outputPublicationFinalizationActions = eu.algites.pltf.modustro.builder.publication.AIcPublicationConfiguration.merge(
                outputPublicationFinalizationActions, aOther.outputPublicationFinalizationActions)
        )
    }
}

data class AIcdModustroOutputPublicationsConfiguration(
    val snapshot: AIcdModustroPublicationStabilityConfiguration = AIcdModustroPublicationStabilityConfiguration(),
    val release: AIcdModustroPublicationStabilityConfiguration = AIcdModustroPublicationStabilityConfiguration()
) {
    fun AIcMerge(aOther: AIcdModustroOutputPublicationsConfiguration): AIcdModustroOutputPublicationsConfiguration =
        AIcdModustroOutputPublicationsConfiguration(
            snapshot = snapshot.AIcMerge(aOther.snapshot),
            release = release.AIcMerge(aOther.release)
        )
}


data class AIcdModustroPublicationFinalizationActionsByStability(
    val snapshot: List<Map<String, Any?>>? = null,
    val release: List<Map<String, Any?>>? = null
) {
    fun AIcMerge(aOther: AIcdModustroPublicationFinalizationActionsByStability): AIcdModustroPublicationFinalizationActionsByStability =
        AIcdModustroPublicationFinalizationActionsByStability(
            snapshot = eu.algites.pltf.modustro.builder.publication.AIcPublicationConfiguration.merge(snapshot, aOther.snapshot),
            release = eu.algites.pltf.modustro.builder.publication.AIcPublicationConfiguration.merge(release, aOther.release)
        )
}


data class AIcdAlgitesVersionRequirement(
    val exact: String? = null,
    val minimum: AIcdAlgitesVersionBoundary? = null,
    val maximum: AIcdAlgitesVersionBoundary? = null,
    val maximumStrict: Boolean? = null,
    val exclude: List<String> = emptyList(),
    val excludeItemsInheritancePolicy: AInModustroItemsInheritancePolicy? = null,
    val prefer: String? = null,
    val specifiedProperties: Set<String> = emptySet()
) {
    fun AIcMerge(aOther: AIcdAlgitesVersionRequirement): AIcdAlgitesVersionRequirement {
        val locExclude = if ("Exclude" in aOther.specifiedProperties) {
            when (aOther.excludeItemsInheritancePolicy ?: AInModustroItemsInheritancePolicy.MERGE_MISSING_ITEMS) {
                AInModustroItemsInheritancePolicy.MERGE_MISSING_ITEMS -> (exclude + aOther.exclude).distinct()
                AInModustroItemsInheritancePolicy.REMOVE_MISSING_ITEMS -> aOther.exclude
            }
        } else exclude
        return AIcdAlgitesVersionRequirement(
            exact = if ("Exact" in aOther.specifiedProperties) aOther.exact else exact,
            minimum = if ("Minimum" in aOther.specifiedProperties) aOther.minimum else minimum,
            maximum = if ("Maximum" in aOther.specifiedProperties) aOther.maximum else maximum,
            maximumStrict = if ("MaximumStrict" in aOther.specifiedProperties) aOther.maximumStrict else maximumStrict,
            exclude = locExclude,
            excludeItemsInheritancePolicy = null,
            prefer = if ("Prefer" in aOther.specifiedProperties) aOther.prefer else prefer,
            specifiedProperties = specifiedProperties + aOther.specifiedProperties
        )
    }
}

data class AIcdModustroDependencyDefinition(
    val dependencyKind: String,
    val groupId: String? = null,
    val artifactId: String,
    val variantId: String? = null,
    val usages: Set<String> = emptySet(),
    val requiredBuildOutputTypes: Set<String> = emptySet(),
    val versionRequirement: AIcdAlgitesVersionRequirement? = null
) {
    fun AIcIdentity(): String = listOf(
        dependencyKind,
        groupId ?: "",
        artifactId,
        variantId ?: ""
    ).joinToString("|")

    fun AIcEffectiveUsages(): Set<String> = if (usages.isEmpty()) setOf("product_implementation") else usages

    fun AIcMerge(aOther: AIcdModustroDependencyDefinition): AIcdModustroDependencyDefinition {
        require(AIcIdentity() == aOther.AIcIdentity()) {
            "Cannot merge dependency definitions with different identities '${AIcIdentity()}' and '${aOther.AIcIdentity()}'."
        }
        return AIcdModustroDependencyDefinition(
            dependencyKind = dependencyKind,
            groupId = groupId,
            artifactId = artifactId,
            variantId = variantId,
            usages = usages + aOther.usages,
            requiredBuildOutputTypes = requiredBuildOutputTypes + aOther.requiredBuildOutputTypes,
            versionRequirement = when {
                versionRequirement == null -> aOther.versionRequirement
                aOther.versionRequirement == null -> versionRequirement
                else -> versionRequirement.AIcMerge(aOther.versionRequirement)
            }
        )
    }
}

data class AIcdModustroDependencyCollectionConfig(
    val definitions: List<AIcdModustroDependencyDefinition> = emptyList(),
    val itemsInheritancePoliciesByDependencyKind: Map<String, AInModustroItemsInheritancePolicy> = emptyMap()
)

fun AIcMergeDependencyDefinitions(
    aBase: List<AIcdModustroDependencyDefinition>,
    aOverride: List<AIcdModustroDependencyDefinition>,
    aOverridePoliciesByDependencyKind: Map<String, AInModustroItemsInheritancePolicy> = emptyMap()
): List<AIcdModustroDependencyDefinition> {
    val locMerged = linkedMapOf<String, AIcdModustroDependencyDefinition>()
    aBase.forEach { locDependency -> locMerged[locDependency.AIcIdentity()] = locDependency }

    aOverridePoliciesByDependencyKind.forEach { (locDependencyKind, locPolicy) ->
        if (locPolicy == AInModustroItemsInheritancePolicy.REMOVE_MISSING_ITEMS) {
            val locLocalIdentities = aOverride
                .filter { locDependency -> locDependency.dependencyKind == locDependencyKind }
                .mapTo(linkedSetOf()) { locDependency -> locDependency.AIcIdentity() }
            locMerged.entries.removeIf { locEntry ->
                locEntry.value.dependencyKind == locDependencyKind && locEntry.key !in locLocalIdentities
            }
        }
    }

    aOverride.forEach { locDependency ->
        val locIdentity = locDependency.AIcIdentity()
        locMerged[locIdentity] = locMerged[locIdentity]?.AIcMerge(locDependency) ?: locDependency
    }
    return locMerged.values.toList()
}

data class AIcdModustroCredentialProfileDefinition(
    val id: String,
    val type: String? = null,
    val configuration: Map<String, String> = emptyMap()
) {
    fun AIcMerge(aOther: AIcdModustroCredentialProfileDefinition): AIcdModustroCredentialProfileDefinition {
        require(id == aOther.id) { "Cannot merge credential profiles with different ids '$id' and '${aOther.id}'." }
        return AIcdModustroCredentialProfileDefinition(
            id = id,
            type = aOther.type ?: type,
            configuration = configuration + aOther.configuration
        )
    }
}

data class AIcdModustroResolvedState(
    val technologyKinds: AIcdModustroTechnologyKindsConfig? = null,
    val groupId: String? = null,
    val inputSubscriptions: Map<String, AIcdModustroInputSubscriptionsConfiguration> = emptyMap(),
    val credentialProfiles: Map<String, AIcdModustroCredentialProfileDefinition> = emptyMap(),
    val version: AIcdAlgitesVersion = AIcdAlgitesVersion(),
    val versionScopePath: String? = null,
    val artifactPublicationFinalizationActions: AIcdModustroPublicationFinalizationActionsByStability = AIcdModustroPublicationFinalizationActionsByStability(),
    val versionScopePublicationFinalizationActions: AIcdModustroPublicationFinalizationActionsByStability = AIcdModustroPublicationFinalizationActionsByStability(),
    val dependencies: List<AIcdModustroDependencyDefinition> = emptyList(),
    val dependencyItemsInheritancePoliciesByKind: Map<String, AInModustroItemsInheritancePolicy> = emptyMap(),
    val dependencyConstraints: List<AIcdModustroDependencyDefinition> = emptyList(),
    val dependencyConstraintItemsInheritancePoliciesByKind: Map<String, AInModustroItemsInheritancePolicy> = emptyMap(),
    val environmentRequirements: Map<String, AIcdAlgitesVersionRequirement> = emptyMap(),
    val nestedGradleSettingsBuildPolicy: String? = null,
    val outputPublications: Map<String, AIcdModustroOutputPublicationsConfiguration> = emptyMap()
) {
    fun AIcMerge(aOther: AIcdModustroResolvedState): AIcdModustroResolvedState {
        val locInputSubscriptions = linkedMapOf<String, AIcdModustroInputSubscriptionsConfiguration>()
        inputSubscriptions.forEach { (locKey, locConfiguration) -> locInputSubscriptions[locKey] = locConfiguration }
        aOther.inputSubscriptions.forEach { (locKey, locConfiguration) ->
            locInputSubscriptions[locKey] = locInputSubscriptions[locKey]?.AIcMerge(locConfiguration) ?: locConfiguration
        }

        val locProfiles = linkedMapOf<String, AIcdModustroCredentialProfileDefinition>()
        credentialProfiles.forEach { (locId, locProfile) -> locProfiles[locId] = locProfile }
        aOther.credentialProfiles.forEach { (locId, locProfile) ->
            locProfiles[locId] = locProfiles[locId]?.AIcMerge(locProfile) ?: locProfile
        }

        val locOutputPublications = linkedMapOf<String, AIcdModustroOutputPublicationsConfiguration>()
        outputPublications.forEach { (locKind, locConfiguration) -> locOutputPublications[locKind] = locConfiguration }
        aOther.outputPublications.forEach { (locKind, locConfiguration) ->
            locOutputPublications[locKind] = locOutputPublications[locKind]?.AIcMerge(locConfiguration) ?: locConfiguration
        }

        return AIcdModustroResolvedState(
            technologyKinds = when {
                aOther.technologyKinds == null -> technologyKinds
                technologyKinds == null -> aOther.technologyKinds
                else -> technologyKinds.AIcMerge(aOther.technologyKinds)
            },
            groupId = aOther.groupId ?: groupId,
            inputSubscriptions = locInputSubscriptions,
            credentialProfiles = locProfiles,
            version = version.AIcMerge(aOther.version),
            versionScopePath = aOther.versionScopePath ?: versionScopePath,
            artifactPublicationFinalizationActions = artifactPublicationFinalizationActions.AIcMerge(aOther.artifactPublicationFinalizationActions),
            versionScopePublicationFinalizationActions = versionScopePublicationFinalizationActions.AIcMerge(aOther.versionScopePublicationFinalizationActions),
            dependencies = AIcMergeDependencyDefinitions(
                dependencies,
                aOther.dependencies,
                aOther.dependencyItemsInheritancePoliciesByKind
            ),
            dependencyItemsInheritancePoliciesByKind = emptyMap(),
            dependencyConstraints = AIcMergeDependencyDefinitions(
                dependencyConstraints,
                aOther.dependencyConstraints,
                aOther.dependencyConstraintItemsInheritancePoliciesByKind
            ),
            dependencyConstraintItemsInheritancePoliciesByKind = emptyMap(),
            environmentRequirements = environmentRequirements + aOther.environmentRequirements,
            nestedGradleSettingsBuildPolicy = aOther.nestedGradleSettingsBuildPolicy ?: nestedGradleSettingsBuildPolicy,
            outputPublications = locOutputPublications
        )
    }
}

data class AIcdModustroDirectoryConfig(
    val file: File,
    val structureKind: String,
    val values: Map<String, String>
)

data class AIcdModustroDescriptorDigest(
    val structureKind: String,
    val path: String,
    val sha256: String
)

data class AIcdModustroArtifactDirectoryMetadata(
    val path: String,
    val structureKind: String,
    val technologyKinds: List<String>,
    val buildOutputTypesByTechnologyKind: Map<String, Set<String>>,
    val name: String,
    val description: String,
    val groupId: String?,
    val variantId: String?,
    val inputSubscriptions: Map<String, AIcdModustroInputSubscriptionsConfiguration>,
    val credentialProfiles: Map<String, AIcdModustroCredentialProfileDefinition>,
    val contentsModel: String,
    val hasGradleBuild: Boolean,
    val gradleProjectPath: String,
    val version: AIcdAlgitesVersion,
    val versionScopePath: String,
    val artifactPublicationFinalizationActions: AIcdModustroPublicationFinalizationActionsByStability,
    val versionScopePublicationFinalizationActions: AIcdModustroPublicationFinalizationActionsByStability,
    val dependencies: List<AIcdModustroDependencyDefinition>,
    val dependencyConstraints: List<AIcdModustroDependencyDefinition>,
    val environmentRequirements: Map<String, AIcdAlgitesVersionRequirement>,
    val nestedGradleSettingsBuildPolicy: String,
    val outputPublications: Map<String, AIcdModustroOutputPublicationsConfiguration>,
    val descriptorHierarchy: List<AIcdModustroDescriptorDigest>
)

data class AIcdModustroRepositoryMetadata(
    val id: String,
    val name: String,
    val visibility: String,
    val groupId: String?,
    val version: AIcdAlgitesVersion = AIcdAlgitesVersion(),
    val versionScopePath: String = ".",
    val artifactPublicationFinalizationActions: AIcdModustroPublicationFinalizationActionsByStability = AIcdModustroPublicationFinalizationActionsByStability(),
    val versionScopePublicationFinalizationActions: AIcdModustroPublicationFinalizationActionsByStability = AIcdModustroPublicationFinalizationActionsByStability(),
    val inputSubscriptions: Map<String, AIcdModustroInputSubscriptionsConfiguration>,
    val credentialProfiles: Map<String, AIcdModustroCredentialProfileDefinition>,
    val dependencies: List<AIcdModustroDependencyDefinition>,
    val dependencyConstraints: List<AIcdModustroDependencyDefinition>,
    val nestedGradleSettingsBuildPolicy: String,
    val outputPublications: Map<String, AIcdModustroOutputPublicationsConfiguration>
)

data class AIcdModustroResolutionResult(
    val repository: AIcdModustroRepositoryMetadata,
    val artifactDirectories: List<AIcdModustroArtifactDirectoryMetadata>,
    val isolatedBuildDirectories: List<String> = emptyList()
)

data class AIcdModustroDirectoryScanResult(
    val artifactDirectories: List<AIcdModustroArtifactDirectoryMetadata>,
    val isolatedBuildDirectories: List<String>
)

val AIcModustroSupportedTechnologyKinds = linkedSetOf("java", "python", "mps", "modustro")
val AIcModustroCredentialTypes = linkedSetOf("basic", "bearer", "api_key", "certificate")
val AIcModustroPublicationOutputKinds = linkedSetOf(
    "native_product_binaries", "native_product_sources", "native_product_documentation", "native_develop_sources", "native_develop_binaries", "native_develop_documentation", "modustro_docs_site", "schema_site"
)
val AIcModustroPublicationFailurePolicies = linkedSetOf(
    "FAIL_BUILD_ON_PUBLICATION_FAILURE", "IGNORE_PUBLICATION_FAILURE"
)
val AIcModustroNestedGradleSettingsBuildPolicies = linkedSetOf(
    "IGNORE_NESTED_SETTINGS", "USE_ISOLATED_BUILD_ON_NESTED_SETTINGS"
)

val AIcModustroRootIgnoredDirectoryNames = setOf(
    ".git", ".gradle", ".idea", ".mps", "_obsolete", "run", "build", "target", "out", "output",
    "docs-site", "documentation-branch", "gh-pages", "source_gen", "source_gen.caches", "classes_gen"
)

fun AIcModustroBuiltInState(): AIcdModustroResolvedState = AIcdModustroResolvedState(
    nestedGradleSettingsBuildPolicy = "IGNORE_NESTED_SETTINGS"
)

val AIcAlgitesExternalRepositoryDefaultsEnvironmentVariables = listOf(
    "ALGITES_REPOSITORY_PUBLIC_DEFAULTS_FILE",
    "ALGITES_REPOSITORY_GOVERNED_PUBLIC_DEFAULTS_FILE",
    "ALGITES_REPOSITORY_PRIVATE_DEFAULTS_FILE"
)

/* Public defaults are versioned with the plugin; no raw GitHub download is needed. */
private class AIcBundledDefaultsResource

fun AIcAlgitesLoadPublicRepositoryDefaults(): File {
    val locBytes = AIcBundledDefaultsResource::class.java.getResourceAsStream(
        "/algites-repository-defaults-public.yml"
    )?.use { it.readBytes() } ?: error("Modustro gradleinit artifact is missing bundled public repository defaults.")
    val locHash = MessageDigest.getInstance("SHA-256").digest(locBytes)
        .joinToString("") { "%02x".format(it) }
    val locGradleUserHome = System.getenv("GRADLE_USER_HOME")?.trim()?.takeIf { it.isNotBlank() }?.let(::File)
        ?: File(System.getProperty("user.home"), ".gradle")
    val locFile = locGradleUserHome.resolve("caches/algites/gradleinit/$locHash/public-defaults.yml")
    locFile.parentFile.mkdirs()
    if (!locFile.isFile || !locFile.readBytes().contentEquals(locBytes)) locFile.writeBytes(locBytes)
    return locFile.canonicalFile
}

fun AIcAlgitesExternalDefaultsState(): AIcdModustroResolvedState {
    var locState = AIcdModustroResolvedState()
    AIcAlgitesExternalRepositoryDefaultsEnvironmentVariables.forEach { locVariableName ->
        val locPath = System.getenv(locVariableName)?.trim()?.takeIf { it.isNotBlank() }
        val locFile = if (locPath != null) {
            File(locPath).also { locCandidate ->
                if (!locCandidate.isFile) {
                    error("Algites repository defaults file does not exist: '${locCandidate.path}'.")
                }
            }
        } else if (locVariableName == "ALGITES_REPOSITORY_PUBLIC_DEFAULTS_FILE") {
            AIcAlgitesLoadPublicRepositoryDefaults()
        } else {
            return@forEach
        }
        locState = locState.AIcMerge(AIcResolvedStateFromRawValues(AIcReadSimpleYamlScalars(locFile), "", locFile))
    }
    return locState
}

fun AIcResolveModustroArtifactDirectoryMetadata(
    aRepositoryRoot: File,
    aArtifactDirectoryPath: String?,
    aResolutionKind: String?,
    aRepositoryNameOverride: String?,
    aRepositoryVisibilityOverride: String?
): AIcdModustroResolutionResult {
    val locRepositoryBase = AIcResolveRepositoryMetadataBase(aRepositoryRoot, aRepositoryNameOverride, aRepositoryVisibilityOverride)
    val locInitialState = AIcModustroBuiltInState().AIcMerge(AIcAlgitesExternalDefaultsState())
    val locRootConfig = AIcFindModustroMetadataConfig(aRepositoryRoot, aRepositoryRoot)?.takeIf { it.structureKind == "repository" }
    val locRootState = if (locRootConfig == null) locInitialState else locInitialState.AIcMerge(AIcResolvedStateFromConfig(locRootConfig, aRepositoryRoot))
    AIcValidateEffectiveState(locRootState, "repository '${locRepositoryBase.id}'")

    val locRepository = AIcdModustroRepositoryMetadata(
        id = locRepositoryBase.id,
        name = locRepositoryBase.name,
        visibility = locRepositoryBase.visibility,
        groupId = locRootState.groupId ?: locRepositoryBase.groupId,
        version = locRootState.version,
        versionScopePath = locRootState.versionScopePath ?: ".",
        artifactPublicationFinalizationActions = locRootState.artifactPublicationFinalizationActions,
        versionScopePublicationFinalizationActions = locRootState.versionScopePublicationFinalizationActions,
        inputSubscriptions = locRootState.inputSubscriptions,
        credentialProfiles = locRootState.credentialProfiles,
        dependencies = locRootState.dependencies,
        dependencyConstraints = locRootState.dependencyConstraints,
        nestedGradleSettingsBuildPolicy = locRootState.nestedGradleSettingsBuildPolicy ?: "IGNORE_NESTED_SETTINGS",
        outputPublications = locRootState.outputPublications
    )

    val locNormalizedPath = aArtifactDirectoryPath?.trim()?.replace('\\', '/')?.trim('/')?.takeIf { it.isNotBlank() && it != "." }
    val locResolutionKind = aResolutionKind?.trim()?.takeIf { it.isNotBlank() } ?: "current-with-subdirs"
    if (locResolutionKind !in setOf("current-only", "current-with-subdirs")) {
        error("Unsupported Modustro artifact directory resolution kind '$locResolutionKind'. Supported values are: current-only, current-with-subdirs.")
    }

    val locScanResult = if (locResolutionKind == "current-only") {
        AIcdModustroDirectoryScanResult(
            artifactDirectories = listOf(AIcResolveSingleArtifactDirectory(aRepositoryRoot, locNormalizedPath ?: ".", locInitialState)),
            isolatedBuildDirectories = emptyList()
        )
    } else {
        AIcResolveArtifactDirectoryAndSubdirectories(aRepositoryRoot, locNormalizedPath ?: ".", locInitialState)
    }
    AIcValidateArtifactVariantCoordinateCollisions(locRepository.id, locScanResult.artifactDirectories)

    return AIcdModustroResolutionResult(locRepository, locScanResult.artifactDirectories, locScanResult.isolatedBuildDirectories)
}

fun AIcCanonicalArtifactId(aRepositoryId: String, aArtifactPath: String): String {
    val locPathDots = aArtifactPath.trim().trim('/').replace('/', '.')
    return if (locPathDots.isBlank() || locPathDots == ".") aRepositoryId else "${aRepositoryId}_$locPathDots"
}

fun AIcEffectiveArtifactId(aRepositoryId: String, aArtifactPath: String, aVariantId: String?): String {
    val locBase = AIcCanonicalArtifactId(aRepositoryId, aArtifactPath)
    return aVariantId?.takeIf { it.isNotBlank() }?.let { "$locBase-$it" } ?: locBase
}

fun AIcPythonCoordinateName(aGroupId: String?, aArtifactId: String): String {
    val locOwnerPrefix = aGroupId
        ?.split('.')
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.take(2)
        ?.joinToString("-")
        ?.lowercase()
        ?.replace(Regex("[._-]+"), "-")
        ?.trim('-')
        .orEmpty()
    val locArtifact = aArtifactId.lowercase().replace(Regex("[._-]+"), "-").trim('-')
    return listOf(locOwnerPrefix, locArtifact).filter(String::isNotEmpty).joinToString("-")
}

fun AIcValidateArtifactVariantCoordinateCollisions(
    aRepositoryId: String,
    aArtifactDirectories: List<AIcdModustroArtifactDirectoryMetadata>
) {
    val locJavaCoordinates = linkedMapOf<String, MutableList<String>>()
    val locPythonCoordinates = linkedMapOf<String, MutableList<String>>()
    aArtifactDirectories.forEach { locArtifact ->
        val locEffectiveId = AIcEffectiveArtifactId(aRepositoryId, locArtifact.path, locArtifact.variantId)
        if ("java" in locArtifact.technologyKinds) {
            val locCoordinate = "${locArtifact.groupId.orEmpty()}:$locEffectiveId"
            locJavaCoordinates.getOrPut(locCoordinate) { mutableListOf() }.add(locArtifact.path)
        }
        if ("python" in locArtifact.technologyKinds) {
            val locCoordinate = AIcPythonCoordinateName(locArtifact.groupId, locEffectiveId)
            locPythonCoordinates.getOrPut(locCoordinate) { mutableListOf() }.add(locArtifact.path)
        }
    }
    (locJavaCoordinates.filterValues { it.size > 1 }.map { (locCoordinate, locPaths) -> "Java '$locCoordinate' <- ${locPaths.joinToString(", ")}" } +
        locPythonCoordinates.filterValues { it.size > 1 }.map { (locCoordinate, locPaths) -> "Python '$locCoordinate' <- ${locPaths.joinToString(", ")}" })
        .takeIf { it.isNotEmpty() }
        ?.let { locCollisions -> error("Modustro artifact VariantId/native-coordinate collision(s): ${locCollisions.joinToString("; ")}") }
}

fun AIcResolveRepositoryMetadataBase(
    aRepositoryRoot: File,
    aRepositoryNameOverride: String?,
    aRepositoryVisibilityOverride: String?
): AIcdModustroRepositoryMetadata {
    val locRootConfig = AIcFindModustroMetadataConfig(aRepositoryRoot, aRepositoryRoot)?.takeIf { it.structureKind == "repository" }
    val locRepositoryId = locRootConfig?.values?.let { AIcFirstValue(it, "SourceRepository.Id") }
        ?.takeIf { it.isNotBlank() } ?: aRepositoryRoot.name
    val locRepositoryName = aRepositoryNameOverride?.takeIf { it.isNotBlank() }
        ?: locRootConfig?.values?.let { AIcFirstValue(it, "SourceRepository.Name") }?.takeIf { it.isNotBlank() }
        ?: locRepositoryId
    val locVisibility = aRepositoryVisibilityOverride?.takeIf { it.isNotBlank() }
        ?: locRootConfig?.values?.let { AIcFirstValue(it, "SourceRepository.Visibility") }?.takeIf { it.isNotBlank() }
        ?: AIcInferVisibilityFromRepositoryName(locRepositoryId)
    val locGroupId = locRootConfig?.values?.let { AIcFirstValue(it, "GroupId") }?.takeIf { it.isNotBlank() }
    return AIcdModustroRepositoryMetadata(
        id = locRepositoryId,
        name = locRepositoryName,
        visibility = locVisibility,
        groupId = locGroupId,
        inputSubscriptions = emptyMap(),
        credentialProfiles = emptyMap(),
        dependencies = emptyList(),
        dependencyConstraints = emptyList(),
        nestedGradleSettingsBuildPolicy = "IGNORE_NESTED_SETTINGS",
        outputPublications = emptyMap()
    )
}

fun AIcInferVisibilityFromRepositoryName(aRepositoryName: String): String = when {
    aRepositoryName.startsWith("pub.") -> "pub"
    aRepositoryName.startsWith("priv.") -> "priv"
    else -> ""
}

fun AIcResolveArtifactDirectoryAndSubdirectories(
    aRepositoryRoot: File,
    aArtifactDirectoryPath: String,
    aInitialState: AIcdModustroResolvedState
): AIcdModustroDirectoryScanResult {
    val locStartDirectory = if (aArtifactDirectoryPath == "." || aArtifactDirectoryPath.isBlank()) aRepositoryRoot else aRepositoryRoot.resolve(aArtifactDirectoryPath)
    if (!locStartDirectory.isDirectory) error("Artifact directory path '$aArtifactDirectoryPath' does not exist.")
    val locInheritedState = AIcResolveInheritedStateBeforeDirectory(aRepositoryRoot, aArtifactDirectoryPath, aInitialState)
    val locResult = mutableListOf<AIcdModustroArtifactDirectoryMetadata>()
    val locIsolatedBuildDirectories = mutableListOf<String>()

    fun locScan(aDirectory: File, aInheritedState: AIcdModustroResolvedState) {
        val locConfig = AIcFindModustroMetadataConfig(aDirectory, aRepositoryRoot)
        var locState = aInheritedState
        if (locConfig != null) {
            locState = locState.AIcMerge(AIcResolvedStateFromConfig(locConfig, aRepositoryRoot))
            AIcValidateEffectiveState(locState, "${locConfig.structureKind} '${AIcRelativePath(aRepositoryRoot, aDirectory)}'")
        }

        val locNestedSettingsBoundary =
            aDirectory.canonicalFile != locStartDirectory.canonicalFile &&
                AIcHasGradleSettings(aDirectory) &&
                locState.nestedGradleSettingsBuildPolicy == "USE_ISOLATED_BUILD_ON_NESTED_SETTINGS"
        if (locNestedSettingsBoundary) {
            locIsolatedBuildDirectories.add(AIcRelativePath(aRepositoryRoot, aDirectory))
            return
        }

        var locStop = false
        if (locConfig != null) {
            val locContentsModel = AIcContentsModel(locConfig.structureKind, locState.technologyKinds?.AIcTechnologyKinds() ?: emptyList())
            locResult.add(AIcArtifactDirectoryMetadataFromConfig(aRepositoryRoot, aDirectory, locConfig, locState, locContentsModel))
            locStop = locContentsModel == "self-contained"
        }
        if (locStop) return

        aDirectory.listFiles()?.asSequence()?.filter { it.isDirectory }
            ?.filter { locChild ->
                if (aDirectory.canonicalFile == aRepositoryRoot.canonicalFile) {
                    locChild.name !in AIcModustroRootIgnoredDirectoryNames
                } else true
            }
            ?.sortedBy { it.name }
            ?.forEach { locScan(it, locState) }
    }

    locScan(locStartDirectory, locInheritedState)
    return AIcdModustroDirectoryScanResult(locResult, locIsolatedBuildDirectories.distinct().sorted())
}

fun AIcResolveInheritedStateBeforeDirectory(
    aRepositoryRoot: File,
    aArtifactDirectoryPath: String,
    aInitialState: AIcdModustroResolvedState
): AIcdModustroResolvedState {
    val locSegments = AIcPathSegments(aArtifactDirectoryPath)
    if (locSegments.isEmpty()) return aInitialState
    var locDirectory = aRepositoryRoot
    var locState = aInitialState
    AIcFindModustroMetadataConfig(aRepositoryRoot, aRepositoryRoot)?.let { locState = locState.AIcMerge(AIcResolvedStateFromConfig(it, aRepositoryRoot)) }
    locSegments.dropLast(1).forEach { locSegment ->
        locDirectory = locDirectory.resolve(locSegment)
        if (!locDirectory.isDirectory) error("Artifact directory parent path '${locDirectory.path}' does not exist.")
        AIcFindModustroMetadataConfig(locDirectory, aRepositoryRoot)?.let { locState = locState.AIcMerge(AIcResolvedStateFromConfig(it, aRepositoryRoot)) }
    }
    return locState
}

fun AIcResolveSingleArtifactDirectory(
    aRepositoryRoot: File,
    aArtifactDirectoryPath: String,
    aInitialState: AIcdModustroResolvedState
): AIcdModustroArtifactDirectoryMetadata {
    var locDirectory = aRepositoryRoot
    var locState = aInitialState
    AIcFindModustroMetadataConfig(aRepositoryRoot, aRepositoryRoot)?.let { locState = locState.AIcMerge(AIcResolvedStateFromConfig(it, aRepositoryRoot)) }
    AIcPathSegments(aArtifactDirectoryPath).forEach { locSegment ->
        locDirectory = locDirectory.resolve(locSegment)
        if (!locDirectory.isDirectory) error("Artifact directory path '$aArtifactDirectoryPath' does not exist.")
        AIcFindModustroMetadataConfig(locDirectory, aRepositoryRoot)?.let { locState = locState.AIcMerge(AIcResolvedStateFromConfig(it, aRepositoryRoot)) }
    }
    val locConfig = AIcFindModustroMetadataConfig(locDirectory, aRepositoryRoot)
        ?: error("Directory '$aArtifactDirectoryPath' does not contain an Algites metadata file.")
    AIcValidateEffectiveState(locState, "${locConfig.structureKind} '$aArtifactDirectoryPath'")
    return AIcArtifactDirectoryMetadataFromConfig(
        aRepositoryRoot, locDirectory, locConfig, locState,
        AIcContentsModel(locConfig.structureKind, locState.technologyKinds?.AIcTechnologyKinds() ?: emptyList())
    )
}

fun AIcSha256(aFile: File): String =
    MessageDigest.getInstance("SHA-256")
        .digest(aFile.readBytes())
        .joinToString("") { locByte -> "%02x".format(locByte.toInt() and 0xff) }

fun AIcDescriptorHierarchy(aRepositoryRoot: File, aDirectory: File): List<AIcdModustroDescriptorDigest> {
    val locRoot = aRepositoryRoot.canonicalFile
    val locTarget = aDirectory.canonicalFile
    val locRelativePath = locRoot.toPath().relativize(locTarget.toPath())
    val locDirectories = mutableListOf(locRoot)
    var locCurrent = locRoot
    locRelativePath.forEach { locSegment ->
        locCurrent = File(locCurrent, locSegment.toString())
        locDirectories.add(locCurrent)
    }

    return locDirectories.mapNotNull { locDirectory ->
        AIcFindModustroMetadataConfig(locDirectory, locRoot)?.let { locConfig ->
            AIcdModustroDescriptorDigest(
                structureKind = locConfig.structureKind,
                path = AIcRelativePath(locRoot, locConfig.file),
                sha256 = AIcSha256(locConfig.file)
            )
        }
    }
}

fun AIcArtifactDirectoryMetadataFromConfig(
    aRepositoryRoot: File,
    aDirectory: File,
    aConfig: AIcdModustroDirectoryConfig,
    aState: AIcdModustroResolvedState,
    aContentsModel: String
): AIcdModustroArtifactDirectoryMetadata {
    val locPrefix = AIcStructureKindPrefix(aConfig.structureKind)
    val locPath = AIcRelativePath(aRepositoryRoot, aDirectory)
    val locName = AIcFirstValue(aConfig.values, "$locPrefix.Name", "$locPrefix.Id")
        ?.takeIf { it.isNotBlank() } ?: if (locPath == ".") aRepositoryRoot.name else aDirectory.name
    val locDescription = AIcFirstValue(aConfig.values, "$locPrefix.Description") ?: ""
    val locVariantId = AIcFirstValue(aConfig.values, "$locPrefix.VariantId")?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
    if (locVariantId != null && !Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$").matches(locVariantId)) {
        error("VariantId '$locVariantId' in '${aConfig.file.path}' must use lowercase dash-separated form.")
    }
    if (aState.technologyKinds?.AIcTechnologyKinds().orEmpty().isNotEmpty() && aState.groupId.isNullOrBlank()) {
        error("Artifact '${locPath}' requires an effective GroupId before building any output.")
    }
    return AIcdModustroArtifactDirectoryMetadata(
        path = locPath,
        structureKind = aConfig.structureKind,
        technologyKinds = aState.technologyKinds?.AIcTechnologyKinds() ?: emptyList(),
        buildOutputTypesByTechnologyKind = aState.technologyKinds?.AIcBuildOutputTypesByTechnologyKind().orEmpty(),
        name = locName,
        description = locDescription,
        groupId = aState.groupId,
        variantId = locVariantId,
        inputSubscriptions = aState.inputSubscriptions,
        credentialProfiles = aState.credentialProfiles,
        contentsModel = aContentsModel,
        hasGradleBuild = AIcHasGradleBuild(aDirectory),
        gradleProjectPath = AIcGradleProjectPath(aRepositoryRoot, aDirectory),
        version = aState.version,
        versionScopePath = aState.versionScopePath ?: ".",
        artifactPublicationFinalizationActions = aState.artifactPublicationFinalizationActions,
        versionScopePublicationFinalizationActions = aState.versionScopePublicationFinalizationActions,
        dependencies = aState.dependencies,
        dependencyConstraints = aState.dependencyConstraints,
        environmentRequirements = aState.environmentRequirements,
                nestedGradleSettingsBuildPolicy = aState.nestedGradleSettingsBuildPolicy ?: "IGNORE_NESTED_SETTINGS",
        outputPublications = aState.outputPublications,
        descriptorHierarchy = AIcDescriptorHierarchy(aRepositoryRoot, aDirectory)
    )
}

fun AIcFindModustroMetadataConfig(aDirectory: File, aRepositoryRoot: File): AIcdModustroDirectoryConfig? {
    val locCandidates = listOf(
        "modustro-source-repository.yml" to "repository",
        "modustro-artifact-set.yml" to "artifact-set",
        "modustro-artifact.yml" to "artifact"
    ).map { aDirectory.resolve(it.first) to it.second }.filter { it.first.isFile }
    if (locCandidates.size > 1) {
        error("Directory '${AIcRelativePath(aRepositoryRoot, aDirectory)}' contains more than one Algites metadata file: ${locCandidates.joinToString(", ") { it.first.name }}")
    }
    val locCandidate = locCandidates.singleOrNull() ?: return null
    if (locCandidate.second == "repository" && aDirectory.canonicalFile != aRepositoryRoot.canonicalFile) {
        error("Repository metadata file '${locCandidate.first.name}' is allowed only in repository root. Found in '${AIcRelativePath(aRepositoryRoot, aDirectory)}'.")
    }
    return AIcdModustroDirectoryConfig(locCandidate.first, locCandidate.second, AIcReadSimpleYamlScalars(locCandidate.first))
}

fun AIcResolvedStateFromConfig(aConfig: AIcdModustroDirectoryConfig, aRepositoryRoot: File): AIcdModustroResolvedState {
    val locPrefix = AIcStructureKindPrefix(aConfig.structureKind)
    val locBase = AIcResolvedStateFromRawValues(aConfig.values, locPrefix, aConfig.file)
    val locTechnologyKinds = when (aConfig.structureKind) {
        "artifact-set", "artifact" -> AIcTechnologyKindsFromConfig(aConfig.values, "$locPrefix.TechnologyKinds", aConfig.file)
        else -> null
    }?.also { locConfig ->
        val locUnsupported = locConfig.AIcTechnologyKinds().filter { it !in AIcModustroSupportedTechnologyKinds }
        if (locUnsupported.isNotEmpty()) error("Unsupported Algites TechnologyKind(s) in '${aConfig.file.path}': ${locUnsupported.joinToString(", ")}.")
    }
    val locVersionDeclared = aConfig.values.keys.any { locKey ->
        locKey.startsWith("$locPrefix.Version.") || locKey.startsWith("Version.")
    }
    return locBase.copy(
        technologyKinds = locTechnologyKinds,
        versionScopePath = if (locVersionDeclared) AIcRelativePath(aRepositoryRoot, aConfig.file.parentFile) else null
    )
}

fun AIcParseItemsInheritancePolicy(
    aValue: String,
    aLabel: String,
    aFile: File
): AInModustroItemsInheritancePolicy = when (aValue.trim().lowercase()) {
    "merge_missing_items" -> AInModustroItemsInheritancePolicy.MERGE_MISSING_ITEMS
    "remove_missing_items" -> AInModustroItemsInheritancePolicy.REMOVE_MISSING_ITEMS
    else -> error("$aLabel '$aValue' is unsupported in '${aFile.path}'.")
}

fun AIcTechnologyKindsFromConfig(
    aValues: Map<String, String>,
    aPrefix: String,
    aFile: File
): AIcdModustroTechnologyKindsConfig? {
    val locCompact = aValues[aPrefix]?.let(::AIcParseYamlStringList)
    if (locCompact != null) {
        return AIcdModustroTechnologyKindsConfig(
            locCompact.map { locTechnologyKind -> AIcdModustroTechnologyKindSelection(locTechnologyKind) }
        )
    }

    val locItemPrefix = "$aPrefix.Items."
    val locIndexes = aValues.keys
        .mapNotNull { locKey ->
            if (!locKey.startsWith(locItemPrefix)) return@mapNotNull null
            locKey.removePrefix(locItemPrefix).substringBefore('.').toIntOrNull()
        }
        .distinct()
        .sorted()
    if (locIndexes.isEmpty() && aValues.keys.none { locKey -> locKey.startsWith("$aPrefix.") }) return null

    val locPolicy = aValues["$aPrefix.ItemsInheritancePolicy"]
        ?.let { locValue -> AIcParseItemsInheritancePolicy(locValue, "$aPrefix.ItemsInheritancePolicy", aFile) }
        ?: AInModustroItemsInheritancePolicy.MERGE_MISSING_ITEMS
    val locItems = locIndexes.map { locIndex ->
        val locBase = "$aPrefix.Items.$locIndex"
        val locTechnologyKind = aValues["$locBase.TechnologyKind"]
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: error("$locBase.TechnologyKind is required in '${aFile.path}'.")
        val locBuildPrefix = "$locBase.BuildOutputTypes"
        val locBuildOutputDefined = aValues.keys.any { locKey -> locKey == locBuildPrefix || locKey.startsWith("$locBuildPrefix.") }
        val locBuildOutputTypes = if (!locBuildOutputDefined) {
            null
        } else {
            val locDirect = aValues[locBuildPrefix]?.let(::AIcParseYamlStringList)
            if (locDirect != null) {
                LinkedHashSet(locDirect)
            } else {
                val locBuildItemPrefix = "$locBuildPrefix.Items."
                val locBuildIndexes = aValues.keys
                    .mapNotNull { locKey ->
                        if (!locKey.startsWith(locBuildItemPrefix)) return@mapNotNull null
                        locKey.removePrefix(locBuildItemPrefix).substringBefore('.').toIntOrNull()
                    }
                    .distinct()
                    .sorted()
                LinkedHashSet(
                    locBuildIndexes.map { locBuildIndex ->
                        aValues["$locBuildItemPrefix$locBuildIndex.BuildOutputType"]
                            ?.trim()
                            ?.lowercase()
                            ?.takeIf { it.isNotBlank() }
                            ?: error("$locBuildItemPrefix$locBuildIndex.BuildOutputType is required in '${aFile.path}'.")
                    }
                )
            }
        }
        val locBuildPolicy = aValues["$locBuildPrefix.ItemsInheritancePolicy"]
            ?.let { locValue -> AIcParseItemsInheritancePolicy(locValue, "$locBuildPrefix.ItemsInheritancePolicy", aFile) }
            ?: AInModustroItemsInheritancePolicy.MERGE_MISSING_ITEMS
        AIcdModustroTechnologyKindSelection(locTechnologyKind, locBuildOutputTypes, locBuildPolicy)
    }
    val locDuplicates = locItems.groupBy { locItem -> locItem.technologyKind }.filterValues { locItemsForKind -> locItemsForKind.size > 1 }.keys
    if (locDuplicates.isNotEmpty()) {
        error("Duplicate TechnologyKind item(s) in '${aFile.path}': ${locDuplicates.joinToString(", ")}.")
    }
    return AIcdModustroTechnologyKindsConfig(locItems, locPolicy)
}

fun AIcParseVersionBoundary(aValue: String, aMinimum: Boolean, aContext: String): AIcdAlgitesVersionBoundary {
    val locValue = aValue.trim()
    if (locValue.isBlank()) error("$aContext must not be blank.")
    val locPrefixOperator = listOf(">=", "<=", ">", "<").firstOrNull { locValue.startsWith(it) }
    val locSuffixOperator = listOf(">=", "<=", ">", "<").firstOrNull { locValue.endsWith(it) }
    if (locPrefixOperator != null && locSuffixOperator != null && locValue.length > locPrefixOperator.length + locSuffixOperator.length) {
        error("$aContext must contain exactly one boundary operator.")
    }
    val locOperator = locPrefixOperator ?: locSuffixOperator ?: error("$aContext must contain a comparison operator.")
    val locVersion = if (locPrefixOperator != null) locValue.removePrefix(locOperator).trim() else locValue.removeSuffix(locOperator).trim()
    if (locVersion.isBlank()) error("$aContext is missing the version value.")
    val locValid = if (locPrefixOperator != null) {
        if (aMinimum) locOperator.startsWith(">") else locOperator.startsWith("<")
    } else {
        if (aMinimum) locOperator.startsWith("<") else locOperator.startsWith(">")
    }
    if (!locValid) error("$aContext operator '$locOperator' points in the wrong direction.")
    return AIcdAlgitesVersionBoundary(locVersion, locOperator.length == 2)
}

fun AIcVersionRequirementFromConfig(
    aValues: Map<String, String>,
    aPrefix: String,
    aFile: File,
    aContext: String
): AIcdAlgitesVersionRequirement? {
    fun locRaw(aName: String): String? = aValues["$aPrefix.$aName"]?.trim()
    fun locIsNull(aValue: String?): Boolean = aValue?.equals("null", true) == true || aValue == "~"
    val locSpecified = linkedSetOf<String>()

    val locExactRaw = locRaw("Exact")
    if (locExactRaw != null) locSpecified.add("Exact")
    val locExact = locExactRaw?.takeUnless(::locIsNull)?.takeIf { it.isNotBlank() }

    fun locBoundary(aName: String, aMinimum: Boolean): AIcdAlgitesVersionBoundary? {
        val locDirect = locRaw(aName)
        val locVersionKey = "$aPrefix.$aName.Version"
        val locInclusiveKey = "$aPrefix.$aName.Inclusive"
        if (locDirect != null || locVersionKey in aValues || locInclusiveKey in aValues) locSpecified.add(aName)
        if (locDirect != null) {
            if (locIsNull(locDirect)) return null
            if (locDirect.isNotBlank()) return AIcParseVersionBoundary(locDirect, aMinimum, "$aContext.$aName")
        }
        val locVersionRaw = aValues[locVersionKey]?.trim() ?: return null
        if (locIsNull(locVersionRaw)) return null
        val locVersion = locVersionRaw.takeIf { it.isNotBlank() } ?: return null
        val locInclusiveRaw = aValues[locInclusiveKey]
            ?: error("$aContext.$aName requires Inclusive when the structured boundary form is used.")
        if (locIsNull(locInclusiveRaw)) error("$aContext.$aName.Inclusive must not be null when Version is defined.")
        val locInclusive = AIcParseBoolean(locInclusiveRaw, "$aContext.$aName.Inclusive", aFile)
        return AIcdAlgitesVersionBoundary(locVersion, locInclusive)
    }

    val locMinimum = locBoundary("Minimum", true)
    val locMaximum = locBoundary("Maximum", false)

    val locMaximumStrictRaw = locRaw("MaximumStrict")
    if (locMaximumStrictRaw != null) locSpecified.add("MaximumStrict")
    val locMaximumStrict = locMaximumStrictRaw
        ?.takeUnless(::locIsNull)
        ?.let { AIcParseBoolean(it, "$aContext.MaximumStrict", aFile) }

    val locExcludeRaw = locRaw("Exclude")
    val locExcludeItemsRaw = aValues["$aPrefix.Exclude.Items"]?.trim()
    val locExcludePolicyRaw = aValues["$aPrefix.Exclude.ItemsInheritancePolicy"]?.trim()
    val locExcludeSpecified = locExcludeRaw != null || locExcludeItemsRaw != null || locExcludePolicyRaw != null
    if (locExcludeSpecified) locSpecified.add("Exclude")
    val locExcludePolicy: AInModustroItemsInheritancePolicy?
    val locExclude: List<String>
    if (locExcludeRaw != null) {
        if (!locIsNull(locExcludeRaw)) {
            error("$aContext.Exclude must use the structured Items form or null.")
        }
        locExcludePolicy = AInModustroItemsInheritancePolicy.REMOVE_MISSING_ITEMS
        locExclude = emptyList()
    } else if (locExcludeSpecified) {
        val locPolicyText = locExcludePolicyRaw?.takeIf { it.isNotBlank() } ?: "merge_missing_items"
        locExcludePolicy = when (locPolicyText) {
            "merge_missing_items" -> AInModustroItemsInheritancePolicy.MERGE_MISSING_ITEMS
            "remove_missing_items" -> AInModustroItemsInheritancePolicy.REMOVE_MISSING_ITEMS
            else -> error("$aContext.Exclude.ItemsInheritancePolicy '$locPolicyText' is unsupported.")
        }
        locExclude = locExcludeItemsRaw?.let(::AIcParseYamlRawStringList).orEmpty()
    } else {
        locExcludePolicy = null
        locExclude = emptyList()
    }

    val locPreferRaw = locRaw("Prefer")
    if (locPreferRaw != null) locSpecified.add("Prefer")
    val locPrefer = locPreferRaw?.takeUnless(::locIsNull)?.takeIf { it.isNotBlank() }

    if (locSpecified.isEmpty()) return null
    val locExcludeIsNonNull = locExcludeSpecified && locExcludeRaw == null
    if (locExact != null && (locMinimum != null || locMaximum != null || locMaximumStrict != null || locExcludeIsNonNull || locPrefer != null)) {
        error("$aContext.Exact cannot be combined in one declaration with non-null Minimum, Maximum, MaximumStrict, Exclude, or Prefer.")
    }
    if (locPrefer != null && locPrefer in locExclude) {
        error("$aContext.Prefer '$locPrefer' must not also be excluded.")
    }
    return AIcdAlgitesVersionRequirement(
        exact = locExact,
        minimum = locMinimum,
        maximum = locMaximum,
        maximumStrict = locMaximumStrict,
        exclude = locExclude,
        excludeItemsInheritancePolicy = locExcludePolicy,
        prefer = locPrefer,
        specifiedProperties = locSpecified
    )
}

fun AIcEnvironmentRequirementsFromConfig(aValues: Map<String, String>, aFile: File): Map<String, AIcdAlgitesVersionRequirement> {
    val locResult = linkedMapOf<String, AIcdAlgitesVersionRequirement>()
    listOf("Java", "Python", "Mps").forEach { locEnvironment ->
        AIcVersionRequirementFromConfig(
            aValues,
            "EnvironmentRequirements.$locEnvironment",
            aFile,
            "${aFile.path}:EnvironmentRequirements.$locEnvironment"
        )?.let { locRequirement -> locResult[locEnvironment.lowercase()] = locRequirement }
    }
    return locResult
}

fun AIcResolvedStateFromRawValues(aValues: Map<String, String>, aPrefix: String, aFile: File): AIcdModustroResolvedState {
    val locGroupId = AIcFirstValue(aValues, "GroupId")?.takeIf { it.isNotBlank() }
    val locVersion = AIcdAlgitesVersion(
        releaseLineVersion = AIcFirstValue(aValues, "$aPrefix.Version.ReleaseLineVersion", "Version.ReleaseLineVersion")?.takeIf { it.isNotBlank() },
        revision = AIcFirstValue(aValues, "$aPrefix.Version.Revision", "Version.Revision")?.takeIf { it.isNotBlank() },
        qualifierKind = AIcFirstValue(aValues, "$aPrefix.Version.QualifierKind", "Version.QualifierKind")?.takeIf { it.isNotBlank() }
    )
    locVersion.releaseLineVersion?.let { locReleaseLineVersion ->
        if (!Regex("^[0-9]+(?:\\.[0-9]+)*$").matches(locReleaseLineVersion)) {
            error("Algites ReleaseLineVersion '$locReleaseLineVersion' in '${aFile.path}' must contain only numeric components separated by dots.")
        }
    }
    val locDependencies = AIcDependencyDefinitionsFromConfig(aValues, "Dependencies", aFile, false)
    val locDependencyConstraints = AIcDependencyDefinitionsFromConfig(aValues, "DependencyConstraints", aFile, true)
    return AIcdModustroResolvedState(
        groupId = locGroupId,
        inputSubscriptions = AIcInputSubscriptionsFromConfig(aValues, aFile),
        credentialProfiles = AIcCredentialProfilesFromConfig(aValues, aFile),
        version = locVersion,
        artifactPublicationFinalizationActions = AIcFinalizationActionsByStabilityFromConfig(
            aValues, "ArtifactPublicationFinalizationActions"),
        versionScopePublicationFinalizationActions = AIcFinalizationActionsByStabilityFromConfig(
            aValues, "VersionScopePublicationFinalizationActions"),
        dependencies = locDependencies.definitions,
        dependencyItemsInheritancePoliciesByKind = locDependencies.itemsInheritancePoliciesByDependencyKind,
        dependencyConstraints = locDependencyConstraints.definitions,
        dependencyConstraintItemsInheritancePoliciesByKind = locDependencyConstraints.itemsInheritancePoliciesByDependencyKind,
        environmentRequirements = AIcEnvironmentRequirementsFromConfig(aValues, aFile),
        nestedGradleSettingsBuildPolicy = AIcFirstValue(aValues, "NestedGradleSettingsBuildPolicy")
            ?.takeIf { it.isNotBlank() }
            ?.also { locPolicy ->
                if (locPolicy !in AIcModustroNestedGradleSettingsBuildPolicies) {
                    error("Unsupported NestedGradleSettingsBuildPolicy '$locPolicy' in '${aFile.path}'. Supported values: ${AIcModustroNestedGradleSettingsBuildPolicies.joinToString(", ")}.")
                }
            },
        outputPublications = AIcOutputPublicationsFromConfig(aValues, aFile)
    )
}

fun AIcFinalizationActionsByStabilityFromConfig(
    aValues: Map<String, String>,
    aPrefix: String
): AIcdModustroPublicationFinalizationActionsByStability {
    fun locLane(aLane: String): List<Map<String, Any?>>? {
        val locPrefix = "$aPrefix.$aLane"
        val locPresent = aValues.containsKey(locPrefix) || aValues.keys.any { locKey -> locKey.startsWith("$locPrefix.") }
        return if (locPresent) eu.algites.pltf.modustro.builder.publication.AIcPublicationConfiguration.list(aValues, locPrefix) else null
    }
    return AIcdModustroPublicationFinalizationActionsByStability(
        snapshot = locLane("Snapshot"),
        release = locLane("Release")
    )
}

fun AIcOutputPublicationsFromConfig(
    aRawValues: Map<String, String>,
    aFile: File
): Map<String, AIcdModustroOutputPublicationsConfiguration> {
    val aValues = eu.algites.pltf.modustro.builder.publication.AIcPublicationConfiguration.expand(aRawValues)
    val locResult = linkedMapOf<String, AIcdModustroOutputPublicationsConfiguration>()
    AIcModustroPublicationOutputKinds.flatMap { kind -> listOf(kind) + AIcModustroSupportedTechnologyKinds.map { "$it.$kind" } }.forEach { locOutputKind ->
        fun locStability(aStability: String): AIcdModustroPublicationStabilityConfiguration {
            val locPrefix = "$locOutputKind.$aStability"
            val locPublicationEnabled = aValues["$locPrefix.PublicationEnabled"]?.let { AIcParseBoolean(it, "$locPrefix.PublicationEnabled", aFile) }
            data class AIcdBuilder(
                var id: String? = null, var executionEnabled: Boolean? = null, var publicationUri: String? = null,
                var publicationAdapter: String? = null, var publicationCredentialProfile: String? = null,
                var executionOrder: Int? = null, var publicationFailurePolicy: String? = null,
                var publicationRetryCount: Int? = null, var publicationWaitForNextAttemptMillis: Long? = null,
                var publicationAttemptTimeoutMillis: Long? = null, var showPublicationProgressIfPossible: Boolean? = null,
                val configuration: MutableMap<String, String> = linkedMapOf()
            )
            val locBuilders = linkedMapOf<String, AIcdBuilder>()
            val locEndpointPrefix = "$locPrefix.PublicationEndpoints."
            aValues.forEach { (locKey, locValue) ->
                if (!locKey.startsWith(locEndpointPrefix)) return@forEach
                val locSegments = locKey.removePrefix(locEndpointPrefix).split('.')
                if (locSegments.size < 2 || locSegments[0].toIntOrNull() == null) return@forEach
                val locBuilder = locBuilders.getOrPut(locSegments[0]) { AIcdBuilder() }
                val locProperty = locSegments.drop(1).joinToString(".")
                if (locProperty.startsWith("Configuration.")) {
                    locBuilder.configuration[locProperty.removePrefix("Configuration.")] = locValue
                    return@forEach
                }
                when (locProperty) {
                    "Id" -> locBuilder.id = locValue.trim()
                    "Enabled", "Order", "PublicationOrder" -> error("$locKey in '${aFile.path}' uses an obsolete publication endpoint field; use ExecutionEnabled or ExecutionOrder.")
                    "ExecutionEnabled" -> locBuilder.executionEnabled = AIcParseBoolean(locValue, locKey, aFile)
                    "PublicationUri" -> locBuilder.publicationUri = locValue.trim().takeIf { it.isNotBlank() }
                    "PublicationAdapter" -> locBuilder.publicationAdapter = locValue.trim().takeIf { it.isNotBlank() }
                    "PublicationCredentialProfile" -> locBuilder.publicationCredentialProfile = locValue.trim().takeIf { it.isNotBlank() }
                    "ExecutionOrder" -> locBuilder.executionOrder = locValue.trim().toIntOrNull() ?: error("$locKey in '${aFile.path}' must be an integer.")
                    "PublicationFailurePolicy" -> locBuilder.publicationFailurePolicy = locValue.trim().also { locPolicy -> if (locPolicy !in AIcModustroPublicationFailurePolicies) error("$locKey in '${aFile.path}' uses unsupported policy '$locPolicy'.") }
                    "PublicationRetryCount" -> locBuilder.publicationRetryCount = locValue.trim().toIntOrNull()?.also { if (it < 0) error("$locKey in '${aFile.path}' must be non-negative.") } ?: error("$locKey in '${aFile.path}' must be a non-negative integer.")
                    "PublicationWaitForNextAttemptMillis" -> locBuilder.publicationWaitForNextAttemptMillis = locValue.trim().toLongOrNull()?.also { if (it < 0L) error("$locKey in '${aFile.path}' must be non-negative.") } ?: error("$locKey in '${aFile.path}' must be a non-negative integer.")
                    "PublicationAttemptTimeoutMillis" -> locBuilder.publicationAttemptTimeoutMillis = locValue.trim().toLongOrNull()?.also { if (it <= 0L) error("$locKey in '${aFile.path}' must be positive.") } ?: error("$locKey in '${aFile.path}' must be a positive integer.")
                    "ShowPublicationProgressIfPossible" -> locBuilder.showPublicationProgressIfPossible = AIcParseBoolean(locValue, locKey, aFile)
                }
            }
            val locEndpoints = linkedMapOf<String, AIcdModustroPublicationEndpoint>()
            locBuilders.toSortedMap(compareBy { it.toInt() }).values.forEach { locBuilder ->
                val locId = locBuilder.id?.takeIf { it.isNotBlank() } ?: error("Publication endpoint in '${aFile.path}' under '$locPrefix' is missing required Id.")
                if (!Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$").matches(locId)) error("Publication endpoint Id '$locId' in '${aFile.path}' must use lowercase dash-separated form.")
                if (locEndpoints.containsKey(locId)) error("Duplicate publication endpoint Id '$locId' in '${aFile.path}' under '$locPrefix'.")
                locBuilder.publicationUri?.let { locUrl ->
                    val locUri = try { URI(locUrl) } catch (locException: Exception) {
                        error("PublicationUri '$locUrl' for endpoint '$locId' in '${aFile.path}' is not a valid URI: ${locException.message}")
                    }
                    if (locUri.scheme.isNullOrBlank()) {
                        error("PublicationUri '$locUrl' for endpoint '$locId' in '${aFile.path}' must be an absolute URI with a scheme.")
                    }
                }
                locEndpoints[locId] = AIcdModustroPublicationEndpoint(
                    id=locId, executionEnabled=locBuilder.executionEnabled, publicationUri=locBuilder.publicationUri,
                    publicationAdapter=locBuilder.publicationAdapter, publicationCredentialProfile=locBuilder.publicationCredentialProfile,
                    executionOrder=locBuilder.executionOrder, publicationFailurePolicy=locBuilder.publicationFailurePolicy,
                    publicationRetryCount=locBuilder.publicationRetryCount, publicationWaitForNextAttemptMillis=locBuilder.publicationWaitForNextAttemptMillis,
                    publicationAttemptTimeoutMillis=locBuilder.publicationAttemptTimeoutMillis,
                    showPublicationProgressIfPossible=locBuilder.showPublicationProgressIfPossible,
                    configuration = locBuilder.configuration.toMap(),
                    publications = eu.algites.pltf.modustro.builder.publication.AIcPublicationConfiguration.list(aValues, "$locPrefix.PublicationEndpoints." + locBuilders.entries.first { it.value === locBuilder }.key + ".Publications").takeIf { aValues.keys.any { key -> key.startsWith("$locPrefix.PublicationEndpoints." + locBuilders.entries.first { it.value === locBuilder }.key + ".Publications") } }
                )
            }
            val locOutputFinalizationPrefix = "$locPrefix.OutputPublicationFinalizationActions"
            val locOutputFinalizationPresent = aValues.containsKey(locOutputFinalizationPrefix) ||
                aValues.keys.any { locKey -> locKey.startsWith("$locOutputFinalizationPrefix.") }
            val locOutputFinalizationActions = if (locOutputFinalizationPresent)
                eu.algites.pltf.modustro.builder.publication.AIcPublicationConfiguration.list(aValues, locOutputFinalizationPrefix) else null
            return AIcdModustroPublicationStabilityConfiguration(
                locPublicationEnabled, locEndpoints, aValues["$locPrefix.PublicationEndpoints"] == "[]", locOutputFinalizationActions)
        }
        val locSnapshot=locStability("Snapshot"); val locRelease=locStability("Release")
        if (locSnapshot.publicationEnabled != null || locSnapshot.publicationEndpoints.isNotEmpty() || locSnapshot.clearPublicationEndpoints || locSnapshot.outputPublicationFinalizationActions != null ||
            locRelease.publicationEnabled != null || locRelease.publicationEndpoints.isNotEmpty() || locRelease.clearPublicationEndpoints || locRelease.outputPublicationFinalizationActions != null) {
            locResult[locOutputKind]=AIcdModustroOutputPublicationsConfiguration(locSnapshot,locRelease)
        }
    }
    return locResult
}

fun AIcDependencyDefinitionsFromConfig(
    aValues: Map<String, String>,
    aPropertyName: String,
    aFile: File,
    aConstraint: Boolean
): AIcdModustroDependencyCollectionConfig {
    val locSupportedKinds = setOf("modustro", "java", "python")
    val locSupportedUsages = setOf(
        "product_api", "product_implementation", "product_compile_only", "product_compile_only_api",
        "product_runtime_only", "product_annotation_processor",
        "develop_implementation", "develop_compile_only", "develop_runtime_only", "develop_annotation_processor"
    )
    val locGroupIndices = aValues.keys.mapNotNull { locKey ->
        Regex("^${Regex.escape(aPropertyName)}\\.(\\d+)\\.DependencyKind$").matchEntire(locKey)?.groupValues?.get(1)?.toIntOrNull()
    }.distinct().sorted()
    val locResult = mutableListOf<AIcdModustroDependencyDefinition>()
    val locPolicies = linkedMapOf<String, AInModustroItemsInheritancePolicy>()

    locGroupIndices.forEach { locGroupIndex ->
        val locGroupPrefix = "$aPropertyName.$locGroupIndex"
        val locDependencyKind = aValues["$locGroupPrefix.DependencyKind"]?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
            ?: error("${aFile.path}:$aPropertyName[$locGroupIndex] is missing required DependencyKind.")
        if (locDependencyKind !in locSupportedKinds) {
            error("${aFile.path}:$aPropertyName[$locGroupIndex] uses unsupported DependencyKind '$locDependencyKind'.")
        }
        if (locDependencyKind in locPolicies) {
            error("${aFile.path}:$aPropertyName declares DependencyKind '$locDependencyKind' more than once; combine its Items into one group.")
        }
        val locPolicyText = aValues["$locGroupPrefix.ItemsInheritancePolicy"]
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "merge_missing_items"
        val locPolicy = when (locPolicyText) {
            "merge_missing_items" -> AInModustroItemsInheritancePolicy.MERGE_MISSING_ITEMS
            "remove_missing_items" -> AInModustroItemsInheritancePolicy.REMOVE_MISSING_ITEMS
            else -> error("${aFile.path}:$aPropertyName[$locGroupIndex].ItemsInheritancePolicy '$locPolicyText' is unsupported.")
        }
        locPolicies[locDependencyKind] = locPolicy
        val locItemIndices = aValues.keys.mapNotNull { locKey ->
            Regex("^${Regex.escape(locGroupPrefix)}\\.Items\\.(\\d+)\\.").find(locKey)?.groupValues?.get(1)?.toIntOrNull()
        }.distinct().sorted()
        if (locItemIndices.isEmpty()) {
            val locItemsRaw = aValues["$locGroupPrefix.Items"]?.trim()
            if (locItemsRaw == null || AIcParseYamlRawStringList(locItemsRaw).isNotEmpty()) {
                error("${aFile.path}:$aPropertyName[$locGroupIndex] must define Items as a sequence; an explicit empty Items list is allowed.")
            }
        }
        locItemIndices.forEach { locItemIndex ->
            val locItemPrefix = "$locGroupPrefix.Items.$locItemIndex"
            val locContext = "${aFile.path}:$aPropertyName[$locGroupIndex].Items[$locItemIndex]"
            val locArtifactId = aValues["$locItemPrefix.ArtifactId"]?.trim()?.takeIf { it.isNotBlank() }
                ?: error("$locContext is missing required ArtifactId.")
            val locGroupId = aValues["$locItemPrefix.GroupId"]?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", true) }
            val locVariantId = aValues["$locItemPrefix.VariantId"]?.trim()?.lowercase()?.takeIf { it.isNotBlank() && !it.equals("null", true) }
            if (locDependencyKind == "java" && locGroupId == null) {
                error("$locContext is a native Java dependency and requires GroupId.")
            }
            if (locDependencyKind != "modustro" && locVariantId != null) {
                error("$locContext may declare VariantId only for DependencyKind 'modustro'.")
            }
            if (locVariantId != null && !Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$").matches(locVariantId)) {
                error("$locContext VariantId '$locVariantId' must use lowercase dash-separated form.")
            }

            val locUsagesRaw = aValues["$locItemPrefix.Usages"]
            val locUsages = locUsagesRaw?.let(::AIcParseYamlStringList)?.toCollection(linkedSetOf()).orEmpty()
            locUsages.forEach { locUsage ->
                if (locUsage !in locSupportedUsages) {
                    error("$locContext uses unsupported Usage '$locUsage'. Supported values: ${locSupportedUsages.sorted().joinToString(", ")}.")
                }
            }

            val locRequiredBuildOutputTypes = if (aConstraint) {
                if (aValues["$locItemPrefix.RequiredBuildOutputTypes"] != null) {
                    error("$locContext is a DependencyConstraint and must not define RequiredBuildOutputTypes.")
                }
                emptySet()
            } else {
                aValues["$locItemPrefix.RequiredBuildOutputTypes"]
                    ?.let(::AIcParseYamlStringList)
                    ?.onEach { locOutputType ->
                        if (!Regex("^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$").matches(locOutputType)) {
                            error("$locContext RequiredBuildOutputType '$locOutputType' must use lowercase underscore-separated form.")
                        }
                    }
                    ?.toCollection(linkedSetOf())
                    .orEmpty()
            }

            val locVersionRequirement = AIcVersionRequirementFromConfig(
                aValues,
                "$locItemPrefix.VersionRequirement",
                aFile,
                "$locContext.VersionRequirement"
            )
            if (aConstraint && locVersionRequirement == null) {
                error("$locContext is a DependencyConstraint and must define VersionRequirement.")
            }
            if (locDependencyKind == "modustro" && locVersionRequirement != null) {
                val locAlgitesVersionPattern = Regex("^[0-9]+(?:\\.[0-9]+)+(?:-SNAPSHOT|-rc[0-9]+)?$", RegexOption.IGNORE_CASE)
                val locVersionValues = buildList {
                    locVersionRequirement.exact?.let(::add)
                    locVersionRequirement.minimum?.version?.let(::add)
                    locVersionRequirement.maximum?.version?.let(::add)
                    addAll(locVersionRequirement.exclude)
                    locVersionRequirement.prefer?.let(::add)
                }
                locVersionValues.forEach { locVersionValue ->
                    if (!locAlgitesVersionPattern.matches(locVersionValue)) {
                        error("$locContext Modustro VersionRequirement value '$locVersionValue' must use canonical Algites v1 version text such as '1.3.2' or '1.3.2-SNAPSHOT'.")
                    }
                }
            }
            locResult.add(
                AIcdModustroDependencyDefinition(
                    dependencyKind = locDependencyKind,
                    groupId = locGroupId,
                    artifactId = locArtifactId,
                    variantId = locVariantId,
                    usages = locUsages,
                    requiredBuildOutputTypes = locRequiredBuildOutputTypes,
                    versionRequirement = locVersionRequirement
                )
            )
        }
    }
    return AIcdModustroDependencyCollectionConfig(locResult, locPolicies)
}


fun AIcInputSubscriptionsFromConfig(
    aValues: Map<String, String>,
    aFile: File
): Map<String, AIcdModustroInputSubscriptionsConfiguration> {
    val locConcreteSelectors = linkedSetOf(
        "native_product_sources", "native_product_binaries", "native_product_documentation",
        "native_develop_sources", "native_develop_binaries", "native_develop_documentation",
        "modustro_docs_site", "schema_site"
    )
    fun locExpand(aSelector: String): List<String> = when (aSelector) {
        "native_outputs" -> listOf(
            "native_product_sources", "native_product_binaries", "native_product_documentation",
            "native_develop_sources", "native_develop_binaries", "native_develop_documentation"
        )
        "native_product_outputs" -> listOf("native_product_sources", "native_product_binaries", "native_product_documentation")
        "native_develop_outputs" -> listOf("native_develop_sources", "native_develop_binaries", "native_develop_documentation")
        "native_sources" -> listOf("native_product_sources", "native_develop_sources")
        "native_binaries" -> listOf("native_product_binaries", "native_develop_binaries")
        "native_documentation" -> listOf("native_product_documentation", "native_develop_documentation")
        in locConcreteSelectors -> listOf(aSelector)
        else -> error("Unsupported InputSelector '$aSelector' in '${aFile.path}'.")
    }
    fun locRank(aSelector: String): Int = when {
        aSelector == "native_outputs" -> 0
        aSelector in locConcreteSelectors -> 2
        else -> 1
    }

    val locIndices = aValues.keys.mapNotNull { locKey ->
        Regex("^InputSubscriptions\\.(\\d+)\\.InputSelector$").matchEntire(locKey)?.groupValues?.get(1)?.toIntOrNull()
    }.distinct().sortedWith(compareBy<Int> { locIndex ->
        locRank(aValues["InputSubscriptions.$locIndex.InputSelector"]?.trim().orEmpty())
    }.thenBy { it })

    val locResult = linkedMapOf<String, AIcdModustroInputSubscriptionsConfiguration>()
    locIndices.forEach { locIndex ->
        val locPrefix = "InputSubscriptions.$locIndex"
        val locSelector = aValues["$locPrefix.InputSelector"]?.trim()?.takeIf { it.isNotBlank() }
            ?: error("$locPrefix in '${aFile.path}' is missing required InputSelector.")
        val locTechnologyKind = aValues["$locPrefix.TechnologyKind"]?.trim()?.takeIf { it.isNotBlank() }
            ?: error("$locPrefix in '${aFile.path}' is missing required TechnologyKind.")
        if (locTechnologyKind !in AIcModustroSupportedTechnologyKinds) {
            error("$locPrefix.TechnologyKind '$locTechnologyKind' in '${aFile.path}' is unsupported.")
        }
        val locSubscriptionIndices = aValues.keys.mapNotNull { locKey ->
            Regex("^${Regex.escape(locPrefix)}\\.Subscriptions\\.(\\d+)\\.Id$").matchEntire(locKey)?.groupValues?.get(1)?.toIntOrNull()
        }.distinct().sorted()
        val locClear = aValues["$locPrefix.Subscriptions"] == "[]"
        val locSubscriptions = linkedMapOf<String, AIcdModustroInputSubscription>()
        locSubscriptionIndices.forEach { locSubscriptionIndex ->
            val locSubscriptionPrefix = "$locPrefix.Subscriptions.$locSubscriptionIndex"
            val locId = aValues["$locSubscriptionPrefix.Id"]?.trim()?.takeIf { it.isNotBlank() }
                ?: error("$locSubscriptionPrefix in '${aFile.path}' is missing required Id.")
            if (!Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$").matches(locId)) {
                error("Input subscription Id '$locId' in '${aFile.path}' must use lowercase dash-separated form.")
            }
            val locEnabled = aValues["$locSubscriptionPrefix.Enabled"]?.let { AIcParseBoolean(it, "$locSubscriptionPrefix.Enabled", aFile) }
            val locVisibility = aValues["$locSubscriptionPrefix.Visibility"]?.trim()?.takeIf { it.isNotBlank() }
            if (locVisibility != null && locVisibility !in setOf("public", "private")) {
                error("$locSubscriptionPrefix.Visibility '$locVisibility' in '${aFile.path}' must be public or private.")
            }
            val locStability = aValues["$locSubscriptionPrefix.Stability"]?.trim()?.takeIf { it.isNotBlank() }
            if (locStability != null && locStability !in setOf("snapshot", "release")) {
                error("$locSubscriptionPrefix.Stability '$locStability' in '${aFile.path}' must be snapshot or release.")
            }
            val locUri = aValues["$locSubscriptionPrefix.SubscriptionUri"]?.trim()?.takeIf { it.isNotBlank() }
            locUri?.let { locValue ->
                val locParsed = try { URI(locValue) } catch (locException: Exception) {
                    error("SubscriptionUri '$locValue' for '$locId' in '${aFile.path}' is invalid: ${locException.message}")
                }
                if (locParsed.scheme.isNullOrBlank()) error("SubscriptionUri '$locValue' for '$locId' must be absolute.")
            }
            val locConfigurationPrefix = "$locSubscriptionPrefix.Configuration."
            val locConfiguration = aValues.filterKeys { it.startsWith(locConfigurationPrefix) }
                .mapKeys { it.key.removePrefix(locConfigurationPrefix) }
            val locSubscription = AIcdModustroInputSubscription(
                id = locId,
                enabled = locEnabled,
                visibility = locVisibility,
                stability = locStability,
                subscriptionUri = locUri,
                subscriptionAdapter = aValues["$locSubscriptionPrefix.SubscriptionAdapter"]?.trim()?.takeIf { it.isNotBlank() },
                subscriptionCredentialProfile = aValues["$locSubscriptionPrefix.SubscriptionCredentialProfile"]?.trim()?.takeIf { it.isNotBlank() },
                subscriptionOrder = aValues["$locSubscriptionPrefix.SubscriptionOrder"]?.trim()?.toIntOrNull(),
                configuration = locConfiguration
            )
            locSubscriptions[locId] = locSubscriptions[locId]?.AIcMerge(locSubscription) ?: locSubscription
        }
        locExpand(locSelector).forEach { locConcreteSelector ->
            val locKey = "$locTechnologyKind.$locConcreteSelector"
            val locLocal = AIcdModustroInputSubscriptionsConfiguration(locSubscriptions, locClear)
            locResult[locKey] = locResult[locKey]?.AIcMerge(locLocal) ?: locLocal
        }
    }
    return locResult
}

fun AIcCredentialProfilesFromConfig(
    aValues: Map<String, String>,
    aFile: File
): Map<String, AIcdModustroCredentialProfileDefinition> {
    data class AIcdBuilder(var type: String? = null, val configuration: MutableMap<String, String> = linkedMapOf())
    val locBuilders = linkedMapOf<String, AIcdBuilder>()
    aValues.forEach { (locKey, locValue) ->
        if (!locKey.startsWith("CredentialProfiles.")) return@forEach
        val locSegments = locKey.removePrefix("CredentialProfiles.").split('.')
        if (locSegments.size < 2) return@forEach
        val locId = locSegments[0]
        val locBuilder = locBuilders.getOrPut(locId) { AIcdBuilder() }
        when {
            locSegments[1] == "Type" -> {
                val locType = locValue.trim().lowercase()
                if (locType !in AIcModustroCredentialTypes) {
                    error("Unsupported credential profile type '$locType' in '${aFile.path}'. Supported types: ${AIcModustroCredentialTypes.joinToString(", ")}.")
                }
                locBuilder.type = locType
            }
            locSegments[1] == "Configuration" && locSegments.size >= 3 -> {
                locBuilder.configuration[locSegments.drop(2).joinToString(".")] = locValue
            }
        }
    }
    return locBuilders.mapValues { (locId, locBuilder) ->
        if (!Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$").matches(locId)) {
            error("Credential profile id '$locId' in '${aFile.path}' must use canonical lowercase dash-separated form.")
        }
        AIcdModustroCredentialProfileDefinition(locId, locBuilder.type, locBuilder.configuration)
    }
}


fun AIcValidateEffectiveState(aState: AIcdModustroResolvedState, aContext: String) {
    (aState.dependencies + aState.dependencyConstraints).forEach { locDependency ->
        val locRequirement = locDependency.versionRequirement ?: return@forEach
        if (locRequirement.maximumStrict != null && locRequirement.maximum == null) {
            error("$aContext dependency '${locDependency.artifactId}' has MaximumStrict without an effective Maximum after inheritance.")
        }
        if (locRequirement.exact != null && locRequirement.exact in locRequirement.exclude) {
            error("$aContext dependency '${locDependency.artifactId}' excludes its effective Exact version '${locRequirement.exact}'.")
        }
    }

    aState.inputSubscriptions.forEach { (locInputKey, locConfiguration) ->
        locConfiguration.subscriptions.values.forEach { locSubscription ->
            if (locSubscription.AIcEffectiveEnabled()) {
                if (locSubscription.subscriptionUri.isNullOrBlank()) {
                    error("Enabled input subscription '${locSubscription.id}' in $aContext $locInputKey has no SubscriptionUri after inheritance.")
                }
                if (locSubscription.subscriptionAdapter.isNullOrBlank()) {
                    error("Enabled input subscription '${locSubscription.id}' in $aContext $locInputKey has no SubscriptionAdapter after inheritance.")
                }
            }
            val locProfileId = locSubscription.subscriptionCredentialProfile
            if (!locProfileId.isNullOrBlank()) {
                val locProfile = aState.credentialProfiles[locProfileId]
                    ?: error("Input subscription '${locSubscription.id}' in $aContext references undefined credential profile '$locProfileId'.")
                if (locProfile.type.isNullOrBlank()) {
                    error("Credential profile '$locProfileId' referenced by input subscription '${locSubscription.id}' in $aContext has no type after inheritance.")
                }
            }
        }
    }

    aState.outputPublications.forEach { (locOutputKind, locConfiguration) ->
        listOf("Snapshot" to locConfiguration.snapshot, "Release" to locConfiguration.release).forEach { (locStability, locPublishing) ->
            locPublishing.publicationEndpoints.values.forEach { locEndpoint ->
                if (locEndpoint.AIcEffectiveExecutionEnabled() && locPublishing.publicationEnabled == true) {
                    if (locEndpoint.publicationUri.isNullOrBlank()) error("Enabled publication endpoint '${locEndpoint.id}' in $aContext $locOutputKind.$locStability has no PublicationUri after inheritance.")
                    if (locEndpoint.publicationAdapter.isNullOrBlank()) error("Enabled publication endpoint '${locEndpoint.id}' in $aContext $locOutputKind.$locStability has no PublicationAdapter after inheritance.")
                }
                val locProfileId = locEndpoint.publicationCredentialProfile
                if (!locProfileId.isNullOrBlank()) {
                    val locProfile = aState.credentialProfiles[locProfileId]
                        ?: error("Publication endpoint '${locEndpoint.id}' in $aContext references undefined credential profile '$locProfileId'.")
                    if (locProfile.type.isNullOrBlank()) {
                        error("Credential profile '$locProfileId' referenced by publication endpoint '${locEndpoint.id}' in $aContext has no type after inheritance.")
                    }
                }
            }
        }
    }
}

fun AIcParseBoolean(aValue: String, aLabel: String, aFile: File): Boolean = when (aValue.trim().lowercase()) {
    "true" -> true
    "false" -> false
    else -> error("Invalid boolean '$aValue' for $aLabel in '${aFile.path}'.")
}

fun AIcStructureKindPrefix(aStructureKind: String): String = when (aStructureKind) {
    "repository" -> "SourceRepository"
    "artifact-set" -> "ArtifactSet"
    "artifact" -> "Artifact"
    else -> aStructureKind
}

fun AIcContentsModel(aStructureKind: String, aTechnologyKinds: List<String>): String = when {
    aStructureKind == "repository" -> "container"
    aStructureKind == "artifact" -> "self-contained"
    aStructureKind == "artifact-set" && aTechnologyKinds == listOf("mps") -> "self-contained"
    else -> "container"
}

fun AIcHasGradleBuild(aDirectory: File): Boolean = aDirectory.resolve("build.gradle.kts").isFile || aDirectory.resolve("build.gradle").isFile
fun AIcHasGradleSettings(aDirectory: File): Boolean = aDirectory.resolve("settings.gradle.kts").isFile || aDirectory.resolve("settings.gradle").isFile
fun AIcGradleProjectPath(aRepositoryRoot: File, aDirectory: File): String {
    val locRelative = AIcRelativePath(aRepositoryRoot, aDirectory)
    return if (locRelative == ".") ":" else ":" + locRelative.split('/').filter { it.isNotBlank() }.joinToString(":")
}
fun AIcPathSegments(aPath: String): List<String> {
    val loc = aPath.trim().replace('\\', '/').trim('/')
    return if (loc.isBlank() || loc == ".") emptyList() else loc.split('/').filter { it.isNotBlank() && it != "." }
}
fun AIcRelativePath(aRepositoryRoot: File, aDirectory: File): String {
    val loc = aRepositoryRoot.canonicalFile.toPath().relativize(aDirectory.canonicalFile.toPath()).toString().replace(File.separatorChar, '/')
    return loc.ifBlank { "." }
}
fun AIcFirstValue(aValues: Map<String, String>, vararg aKeys: String): String? = aKeys.firstNotNullOfOrNull { aValues[it] }

fun AIcReadSimpleYamlScalars(aFile: File): Map<String, String> {
    val locValues = linkedMapOf<String, String>()
    val locStack = mutableListOf<Pair<Int, String>>()
    val locListCounters = mutableMapOf<String, Int>()

    fun locStoreValue(aPath: String, aRawValue: String) {
        val locTrimmed = aRawValue.trim()
        if (locTrimmed.startsWith("{") && locTrimmed.endsWith("}")) {
            AIcParseYamlFlowMap(locTrimmed).forEach { (locKey, locValue) ->
                locStoreValue("$aPath.$locKey", locValue)
            }
        } else {
            locValues[aPath] = AIcUnquoteYamlScalar(locTrimmed)
        }
    }

    aFile.readLines(Charsets.UTF_8).forEach { locOriginalLine ->
        val locLine = AIcStripYamlComment(locOriginalLine)
        if (locLine.isBlank()) return@forEach
        val locIndent = locLine.takeWhile { it == ' ' }.length
        val locTrimmed = locLine.trim()

        if (locTrimmed.startsWith("- ")) {
            /*
             * YAML permits indentationless sequences, for example:
             *
             * TechnologyKinds:
             * - python
             *
             * Keep a pending container at the same indentation level, but remove a previous
             * list-item frame at that level so sibling items do not become nested items.
             */
            while (
                locStack.isNotEmpty() &&
                (locStack.last().first > locIndent ||
                    locStack.last().first == locIndent && locStack.last().second.toIntOrNull() != null)
            ) {
                locStack.removeAt(locStack.lastIndex)
            }
        } else {
            while (locStack.isNotEmpty() && locStack.last().first >= locIndent) locStack.removeAt(locStack.lastIndex)
        }

        if (locTrimmed.startsWith("- ")) {
            val locParentPath = locStack.joinToString(".") { it.second }
            val locItemText = locTrimmed.removePrefix("- ").trim()
            val locSeparator = locItemText.indexOf(':')
            if (locSeparator > 0) {
                val locIndex = locListCounters.getOrDefault(locParentPath, 0)
                locListCounters[locParentPath] = locIndex + 1
                locStack.add(locIndent to locIndex.toString())
                val locKey = locItemText.substring(0, locSeparator).trim()
                val locRawValue = locItemText.substring(locSeparator + 1).trim()
                val locPath = (locStack.map { it.second } + locKey).joinToString(".")
                if (locRawValue.isEmpty()) locStack.add((locIndent + 2) to locKey)
                else locStoreValue(locPath, locRawValue)
            } else {
                if (locParentPath.isNotBlank()) {
                    val locItem = AIcUnquoteYamlScalar(locItemText)
                    val locExisting = locValues[locParentPath]
                    val locItems = if (locExisting == null) mutableListOf() else AIcParseYamlStringList(locExisting).toMutableList()
                    locItems.add(locItem)
                    locValues[locParentPath] = "[" + locItems.joinToString(", ") + "]"
                }
            }
            return@forEach
        }

        val locSeparator = locTrimmed.indexOf(':')
        if (locSeparator <= 0) return@forEach
        val locKey = locTrimmed.substring(0, locSeparator).trim()
        val locRawValue = locTrimmed.substring(locSeparator + 1).trim()
        val locPath = (locStack.map { it.second } + locKey).joinToString(".")
        if (locRawValue.isEmpty()) locStack.add(locIndent to locKey)
        else locStoreValue(locPath, locRawValue)
    }
    return locValues
}


fun AIcParseYamlFlowMap(aValue: String): Map<String, String> {
    val locTrimmed = aValue.trim()
    require(locTrimmed.startsWith("{") && locTrimmed.endsWith("}")) {
        "YAML flow map must be enclosed in braces: '$aValue'."
    }
    val locContent = locTrimmed.substring(1, locTrimmed.length - 1).trim()
    if (locContent.isBlank()) return emptyMap()

    fun locSplitTopLevel(aText: String, aSeparator: Char): List<String> {
        val locParts = mutableListOf<String>()
        var locStart = 0
        var locQuote: Char? = null
        var locEscape = false
        var locDepth = 0
        aText.forEachIndexed { locIndex, locCharacter ->
            if (locEscape) {
                locEscape = false
                return@forEachIndexed
            }
            if (locQuote != null) {
                if (locQuote == '"' && locCharacter == '\\') {
                    locEscape = true
                } else if (locCharacter == locQuote) {
                    locQuote = null
                }
                return@forEachIndexed
            }
            when (locCharacter) {
                '\'', '"' -> locQuote = locCharacter
                '[', '{', '(' -> locDepth++
                ']', '}', ')' -> locDepth--
                aSeparator -> if (locDepth == 0) {
                    locParts.add(aText.substring(locStart, locIndex).trim())
                    locStart = locIndex + 1
                }
            }
        }
        require(locQuote == null && locDepth == 0) {
            "Malformed YAML flow value '$aText'."
        }
        locParts.add(aText.substring(locStart).trim())
        return locParts.filter { locPart -> locPart.isNotBlank() }
    }

    return locSplitTopLevel(locContent, ',').associate { locEntry ->
        val locParts = locSplitTopLevel(locEntry, ':')
        require(locParts.size >= 2) { "Malformed YAML flow-map entry '$locEntry'." }
        val locKey = AIcUnquoteYamlScalar(locParts.first().trim())
        val locValue = locEntry.substringAfter(':').trim()
        require(locKey.isNotBlank()) { "YAML flow-map entry '$locEntry' has a blank key." }
        locKey to AIcUnquoteYamlScalar(locValue)
    }
}

fun AIcParseYamlStringList(aValue: String): List<String> {
    val locTrimmed = aValue.trim()
    val locContent = if (locTrimmed.startsWith("[") && locTrimmed.endsWith("]")) locTrimmed.substring(1, locTrimmed.length - 1) else locTrimmed
    if (locContent.isBlank()) return emptyList()
    return locContent.split(',').map { AIcUnquoteYamlScalar(it.trim()).lowercase() }.filter { it.isNotBlank() }.distinct()
}

fun AIcParseYamlRawStringList(aValue: String): List<String> {
    val locTrimmed = aValue.trim()
    val locContent = if (locTrimmed.startsWith("[") && locTrimmed.endsWith("]")) locTrimmed.substring(1, locTrimmed.length - 1) else locTrimmed
    if (locContent.isBlank()) return emptyList()
    return locContent.split(',').map { AIcUnquoteYamlScalar(it.trim()) }.filter { it.isNotBlank() }.distinct()
}

fun AIcStripYamlComment(aLine: String): String {
    var locSingle = false
    var locDouble = false
    aLine.forEachIndexed { locIndex, locCharacter ->
        when (locCharacter) {
            '\'' -> if (!locDouble) locSingle = !locSingle
            '"' -> if (!locSingle) locDouble = !locDouble
            '#' -> if (!locSingle && !locDouble) {
                val locPrevious = aLine.getOrNull(locIndex - 1)
                if (locIndex == 0 || locPrevious?.isWhitespace() == true) return aLine.substring(0, locIndex)
            }
        }
    }
    return aLine
}

fun AIcUnquoteYamlScalar(aValue: String): String {
    val loc = aValue.trim()
    return if ((loc.startsWith("\"") && loc.endsWith("\"")) || (loc.startsWith("'") && loc.endsWith("'"))) loc.substring(1, loc.length - 1) else loc
}

fun AIcYamlScalar(aValue: String?): String {
    if (aValue == null) return "null"
    val locNeeds = aValue.isBlank() || aValue.any { it == ':' || it == '#' || it == '"' || it == '\'' || it.isWhitespace() } || aValue in setOf("null", "true", "false") || aValue.toDoubleOrNull() != null
    return if (locNeeds) "\"" + aValue.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" else aValue
}

fun AIcInputSubscriptionsMapForOutput(
    aInputSubscriptions: Map<String, AIcdModustroInputSubscriptionsConfiguration>
): Map<String, Any?> = aInputSubscriptions.toSortedMap().mapValues { (_, locConfiguration) ->
    locConfiguration.subscriptions.values
        .sortedWith(compareBy<AIcdModustroInputSubscription> { it.AIcEffectiveSubscriptionOrder() }.thenBy { it.id })
        .map { locSubscription ->
            linkedMapOf<String, Any?>(
                "id" to locSubscription.id,
                "enabled" to locSubscription.AIcEffectiveEnabled(),
                "visibility" to locSubscription.AIcEffectiveVisibility(),
                "stability" to locSubscription.stability,
                "subscriptionUri" to locSubscription.subscriptionUri,
                "subscriptionAdapter" to locSubscription.subscriptionAdapter,
                "subscriptionCredentialProfile" to locSubscription.subscriptionCredentialProfile,
                "subscriptionOrder" to locSubscription.AIcEffectiveSubscriptionOrder(),
                "configuration" to locSubscription.configuration.toSortedMap()
            )
        }
}

fun AIcCredentialProfilesMapForOutput(aProfiles: Map<String, AIcdModustroCredentialProfileDefinition>): Map<String, Any?> =
    aProfiles.toSortedMap().mapValues { (_, locProfile) ->
        linkedMapOf<String, Any?>(
            "type" to locProfile.type,
            "configuration" to locProfile.configuration.toSortedMap()
        )
    }

fun AIcVersionRequirementMapForOutput(aRequirement: AIcdAlgitesVersionRequirement): Map<String, Any?> = linkedMapOf(
    "exact" to aRequirement.exact,
    "minimum" to aRequirement.minimum?.let { locBoundary -> linkedMapOf("version" to locBoundary.version, "inclusive" to locBoundary.inclusive) },
    "maximum" to aRequirement.maximum?.let { locBoundary -> linkedMapOf("version" to locBoundary.version, "inclusive" to locBoundary.inclusive) },
    "maximumStrict" to aRequirement.maximumStrict,
    "exclude" to aRequirement.exclude,
    "prefer" to aRequirement.prefer
)

fun AIcDependencyMapForOutput(aDependency: AIcdModustroDependencyDefinition): Map<String, Any?> = linkedMapOf(
    "dependencyKind" to aDependency.dependencyKind,
    "groupId" to aDependency.groupId,
    "artifactId" to aDependency.artifactId,
    "variantId" to aDependency.variantId,
    "usages" to aDependency.AIcEffectiveUsages().toList(),
    "requiredBuildOutputTypes" to aDependency.requiredBuildOutputTypes.toList(),
    "versionRequirement" to aDependency.versionRequirement?.let(::AIcVersionRequirementMapForOutput)
)


fun AIcPublicationEndpointMapForOutput(aEndpoint: AIcdModustroPublicationEndpoint): Map<String, Any?> = linkedMapOf(
    "id" to aEndpoint.id,
    "executionEnabled" to aEndpoint.AIcEffectiveExecutionEnabled(),
    "publicationUri" to aEndpoint.publicationUri,
    "publicationAdapter" to aEndpoint.publicationAdapter,
    "publicationCredentialProfile" to aEndpoint.publicationCredentialProfile,
    "executionOrder" to aEndpoint.AIcEffectiveExecutionOrder(),
    "publicationFailurePolicy" to aEndpoint.AIcEffectivePublicationFailurePolicy(),
    "publicationRetryCount" to aEndpoint.AIcEffectivePublicationRetryCount(),
    "publicationWaitForNextAttemptMillis" to aEndpoint.AIcEffectivePublicationWaitForNextAttemptMillis(),
    "publicationAttemptTimeoutMillis" to aEndpoint.publicationAttemptTimeoutMillis,
    "configuration" to aEndpoint.configuration.toSortedMap(),
    "publications" to aEndpoint.publications,
    "showPublicationProgressIfPossible" to aEndpoint.AIcEffectiveShowPublicationProgressIfPossible()
)

fun AIcOutputPublicationsMapForOutput(aPublishing: Map<String, AIcdModustroOutputPublicationsConfiguration>): Map<String, Any?> =
    aPublishing.toSortedMap().mapValues { (_, locConfiguration) ->
        linkedMapOf(
            "snapshot" to linkedMapOf(
                "publicationEnabled" to locConfiguration.snapshot.AIcEffectivePublicationEnabled(),
                "publicationEndpoints" to locConfiguration.snapshot.publicationEndpoints.values.map(::AIcPublicationEndpointMapForOutput),
                "outputPublicationFinalizationActions" to (locConfiguration.snapshot.outputPublicationFinalizationActions ?: emptyList())
            ),
            "release" to linkedMapOf(
                "publicationEnabled" to locConfiguration.release.AIcEffectivePublicationEnabled(),
                "publicationEndpoints" to locConfiguration.release.publicationEndpoints.values.map(::AIcPublicationEndpointMapForOutput),
                "outputPublicationFinalizationActions" to (locConfiguration.release.outputPublicationFinalizationActions ?: emptyList())
            )
        )
    }


fun AIcFinalizationActionsByStabilityMapForOutput(aActions: AIcdModustroPublicationFinalizationActionsByStability): Map<String, Any?> = linkedMapOf(
    "snapshot" to (aActions.snapshot ?: emptyList()),
    "release" to (aActions.release ?: emptyList())
)

fun AIcEnvironmentRequirementsMapForOutput(aRequirements: Map<String, AIcdAlgitesVersionRequirement>): Map<String, Any?> =
    aRequirements.toSortedMap().mapValues { (_, locRequirement) -> AIcVersionRequirementMapForOutput(locRequirement) }

fun AIcToMap(aResult: AIcdModustroResolutionResult): Map<String, Any?> = linkedMapOf(
    "isolatedBuildDirectories" to aResult.isolatedBuildDirectories,
    "repository" to linkedMapOf(
        "id" to aResult.repository.id,
        "name" to aResult.repository.name,
        "visibility" to aResult.repository.visibility,
        "groupId" to aResult.repository.groupId,
        "version" to linkedMapOf(
            "releaseLineVersion" to aResult.repository.version.releaseLineVersion,
            "revision" to aResult.repository.version.revision,
            "qualifierKind" to aResult.repository.version.qualifierKind,
            "resolvedValue" to aResult.repository.version.AIcResolvedValue()
        ),
        "versionScopePath" to aResult.repository.versionScopePath,
        "artifactPublicationFinalizationActions" to AIcFinalizationActionsByStabilityMapForOutput(aResult.repository.artifactPublicationFinalizationActions),
        "versionScopePublicationFinalizationActions" to AIcFinalizationActionsByStabilityMapForOutput(aResult.repository.versionScopePublicationFinalizationActions),
        "inputSubscriptions" to AIcInputSubscriptionsMapForOutput(aResult.repository.inputSubscriptions),
        "credentialProfiles" to AIcCredentialProfilesMapForOutput(aResult.repository.credentialProfiles),
        "dependencies" to aResult.repository.dependencies.map(::AIcDependencyMapForOutput),
        "dependencyConstraints" to aResult.repository.dependencyConstraints.map(::AIcDependencyMapForOutput),
        "nestedGradleSettingsBuildPolicy" to aResult.repository.nestedGradleSettingsBuildPolicy,
        "outputPublications" to AIcOutputPublicationsMapForOutput(aResult.repository.outputPublications)
    ),
    "artifactDirectories" to aResult.artifactDirectories.map { locDirectory ->
        linkedMapOf<String, Any?>(
            "path" to locDirectory.path,
            "structureKind" to locDirectory.structureKind,
            "technologyKinds" to locDirectory.technologyKinds,
            "buildOutputTypesByTechnologyKind" to locDirectory.buildOutputTypesByTechnologyKind.toSortedMap(),
            "name" to locDirectory.name,
            "description" to locDirectory.description,
            "groupId" to locDirectory.groupId,
            "variantId" to locDirectory.variantId,
            "versionScopePath" to locDirectory.versionScopePath,
            "artifactPublicationFinalizationActions" to AIcFinalizationActionsByStabilityMapForOutput(locDirectory.artifactPublicationFinalizationActions),
            "versionScopePublicationFinalizationActions" to AIcFinalizationActionsByStabilityMapForOutput(locDirectory.versionScopePublicationFinalizationActions),
            "inputSubscriptions" to AIcInputSubscriptionsMapForOutput(locDirectory.inputSubscriptions),
            "credentialProfiles" to AIcCredentialProfilesMapForOutput(locDirectory.credentialProfiles),
            "dependencies" to locDirectory.dependencies.map(::AIcDependencyMapForOutput),
            "dependencyConstraints" to locDirectory.dependencyConstraints.map(::AIcDependencyMapForOutput),
            "environmentRequirements" to AIcEnvironmentRequirementsMapForOutput(locDirectory.environmentRequirements),
            "nestedGradleSettingsBuildPolicy" to locDirectory.nestedGradleSettingsBuildPolicy,
            "outputPublications" to AIcOutputPublicationsMapForOutput(locDirectory.outputPublications),
            "contentsModel" to locDirectory.contentsModel,
            "hasGradleBuild" to locDirectory.hasGradleBuild,
            "gradleProjectPath" to locDirectory.gradleProjectPath,
            "descriptorHierarchy" to locDirectory.descriptorHierarchy.map { locDescriptor ->
                linkedMapOf<String, Any?>(
                    "structureKind" to locDescriptor.structureKind,
                    "path" to locDescriptor.path,
                    "sha256" to locDescriptor.sha256
                )
            },
            "version" to linkedMapOf(
                "releaseLineVersion" to locDirectory.version.releaseLineVersion,
                "revision" to locDirectory.version.revision,
                "qualifierKind" to locDirectory.version.qualifierKind,
                "resolvedValue" to locDirectory.version.AIcResolvedValue()
            )
        )
    }
)

fun AIcToYaml(aResult: AIcdModustroResolutionResult): String = buildString {
    fun locStructureKind(aValue: String): String = aValue.replace('-', '_')
    appendLine("Repository:")
    appendLine("  Id: ${AIcYamlScalar(aResult.repository.id)}")
    appendLine("  Name: ${AIcYamlScalar(aResult.repository.name)}")
    appendLine("  Visibility: ${AIcYamlScalar(aResult.repository.visibility)}")
    appendLine("  GroupId: ${AIcYamlScalar(aResult.repository.groupId)}")
    appendLine("  InputSubscriptions: ${AIcYamlScalar(AIcInputSubscriptionsMapForOutput(aResult.repository.inputSubscriptions).toString())}")
    appendLine("  CredentialProfiles: ${AIcYamlScalar(AIcCredentialProfilesMapForOutput(aResult.repository.credentialProfiles).toString())}")
    appendLine("  Dependencies: ${AIcYamlScalar(aResult.repository.dependencies.map(::AIcDependencyMapForOutput).toString())}")
    appendLine("  DependencyConstraints: ${AIcYamlScalar(aResult.repository.dependencyConstraints.map(::AIcDependencyMapForOutput).toString())}")
    appendLine("  NestedGradleSettingsBuildPolicy: ${AIcYamlScalar(aResult.repository.nestedGradleSettingsBuildPolicy)}")
    appendLine("  OutputPublications: ${AIcYamlScalar(AIcOutputPublicationsMapForOutput(aResult.repository.outputPublications).toString())}")
    appendLine("ArtifactDirectories:")
    aResult.artifactDirectories.forEach { locDirectory ->
        appendLine("  - Path: ${AIcYamlScalar(locDirectory.path)}")
        appendLine("    StructureKind: ${AIcYamlScalar(locStructureKind(locDirectory.structureKind))}")
        appendLine("    TechnologyKinds: [${locDirectory.technologyKinds.joinToString(", ")}]")
        appendLine("    BuildOutputTypesByTechnologyKind: ${AIcYamlScalar(locDirectory.buildOutputTypesByTechnologyKind.toString())}")
        appendLine("    Name: ${AIcYamlScalar(locDirectory.name)}")
        appendLine("    Description: ${AIcYamlScalar(locDirectory.description)}")
        appendLine("    GroupId: ${AIcYamlScalar(locDirectory.groupId)}")
        appendLine("    VariantId: ${AIcYamlScalar(locDirectory.variantId)}")
        appendLine("    InputSubscriptions: ${AIcYamlScalar(AIcInputSubscriptionsMapForOutput(locDirectory.inputSubscriptions).toString())}")
        appendLine("    CredentialProfiles: ${AIcYamlScalar(AIcCredentialProfilesMapForOutput(locDirectory.credentialProfiles).toString())}")
        appendLine("    Dependencies: ${AIcYamlScalar(locDirectory.dependencies.map(::AIcDependencyMapForOutput).toString())}")
        appendLine("    DependencyConstraints: ${AIcYamlScalar(locDirectory.dependencyConstraints.map(::AIcDependencyMapForOutput).toString())}")
        appendLine("    EnvironmentRequirements: ${AIcYamlScalar(AIcEnvironmentRequirementsMapForOutput(locDirectory.environmentRequirements).toString())}")
        appendLine("    NestedGradleSettingsBuildPolicy: ${AIcYamlScalar(locDirectory.nestedGradleSettingsBuildPolicy)}")
        appendLine("    OutputPublications: ${AIcYamlScalar(AIcOutputPublicationsMapForOutput(locDirectory.outputPublications).toString())}")
        appendLine("    ContentsModel: ${AIcYamlScalar(locDirectory.contentsModel.replace('-', '_'))}")
        appendLine("    HasGradleBuild: ${locDirectory.hasGradleBuild}")
        appendLine("    GradleProjectPath: ${AIcYamlScalar(locDirectory.gradleProjectPath)}")
        appendLine("    DescriptorHierarchy:")
        locDirectory.descriptorHierarchy.forEach { locDescriptor ->
            appendLine("      - StructureKind: ${AIcYamlScalar(locStructureKind(locDescriptor.structureKind))}")
            appendLine("        Path: ${AIcYamlScalar(locDescriptor.path)}")
            appendLine("        Sha256: ${AIcYamlScalar(locDescriptor.sha256)}")
        }
        appendLine("    Version:")
        appendLine("      ReleaseLineVersion: ${AIcYamlScalar(locDirectory.version.releaseLineVersion)}")
        appendLine("      Revision: ${AIcYamlScalar(locDirectory.version.revision)}")
        appendLine("      QualifierKind: ${AIcYamlScalar(locDirectory.version.qualifierKind)}")
        appendLine("      ResolvedValue: ${AIcYamlScalar(locDirectory.version.AIcResolvedValue())}")
    }
}

fun AIcFlattenDottedProperties(aResultMap: Map<String, Any?>): Map<String, String> {
    val locResult = linkedMapOf<String, String>()
    fun locFlatten(aPrefix: String, aValue: Any?) {
        when (aValue) {
            is Map<*, *> -> aValue.forEach { (locKey, locValue) -> locFlatten(if (aPrefix.isBlank()) locKey.toString() else "$aPrefix.${locKey}", locValue) }
            is List<*> -> {
                if (aPrefix.isNotBlank()) {
                    locResult["$aPrefix.count"] = aValue.size.toString()
                    if (aValue.all { locValue -> locValue == null || locValue !is Map<*, *> && locValue !is List<*> }) {
                        locResult[aPrefix] = aValue.joinToString(",") { locValue -> locValue?.toString() ?: "null" }
                    }
                }
                aValue.forEachIndexed { locIndex, locValue -> locFlatten("$aPrefix.$locIndex", locValue) }
            }
            null -> locResult[aPrefix] = "null"
            else -> locResult[aPrefix] = aValue.toString()
        }
    }
    locFlatten("", aResultMap)
    return locResult
}

fun AIcFormatOutput(aResult: AIcdModustroResolutionResult, aOutputKind: String): String = when (aOutputKind) {
    "yml", "yaml" -> AIcToYaml(aResult)
    "dotted-properties" -> AIcFlattenDottedProperties(AIcToMap(aResult)).entries.joinToString("\n", postfix = "\n") { "${it.key}=${it.value}" }
    else -> error("Unsupported Modustro artifact directory output kind '$aOutputKind'. Supported values are: yml, yaml, dotted-properties.")
}
