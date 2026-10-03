/*
 * Modustro Builder artifact directory metadata resolver core.
 *
 * This script intentionally contains only Settings/Project compatible orchestration logic.
 * It resolves structural metadata, TechnologyKinds, inherited groupId, credential profiles,
 * and version contexts while delegating ResourceEndpoint declaration merge/defaulting/validation
 * to the Gradle-independent Modustro Builder core implementation.
 */

import eu.algites.pltf.modustro.builder.model.resource.AIcgdResourceEndpoint_1
import eu.algites.pltf.modustro.builder.resource.AIcResourceEndpointMetadataBridge
import eu.algites.pltf.modustro.builder.resource.AIcResourceEndpointResolver
import java.io.File
import java.net.URI
import java.security.MessageDigest

val AIcModustroResourceEndpointResolver = AIcResourceEndpointResolver.builtin()
val AIcModustroResourceEndpointMetadataBridge = AIcResourceEndpointMetadataBridge(AIcModustroResourceEndpointResolver)

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

data class AIcdModustroPublishingEndpoint(
    val id: String,
    val enabled: Boolean? = null,
    val publishingUrl: String? = null,
    val publishingAdapter: String? = null,
    val publishingCredentialProfile: String? = null,
    val publishingOrder: Int? = null,
    val publishingFailurePolicy: String? = null,
    val publishingRetryCount: Int? = null,
    val publishingRetryDelayMillis: Long? = null,
    val publishingAttemptTimeoutMillis: Long? = null,
    val showPublishingProgressIfPossible: Boolean? = null
) {
    fun AIcMerge(aOther: AIcdModustroPublishingEndpoint): AIcdModustroPublishingEndpoint {
        require(id == aOther.id) { "Cannot merge publishing endpoints with different ids '$id' and '${aOther.id}'." }
        return AIcdModustroPublishingEndpoint(
            id = id,
            enabled = aOther.enabled ?: enabled,
            publishingUrl = aOther.publishingUrl ?: publishingUrl,
            publishingAdapter = aOther.publishingAdapter ?: publishingAdapter,
            publishingCredentialProfile = aOther.publishingCredentialProfile ?: publishingCredentialProfile,
            publishingOrder = aOther.publishingOrder ?: publishingOrder,
            publishingFailurePolicy = aOther.publishingFailurePolicy ?: publishingFailurePolicy,
            publishingRetryCount = aOther.publishingRetryCount ?: publishingRetryCount,
            publishingRetryDelayMillis = aOther.publishingRetryDelayMillis ?: publishingRetryDelayMillis,
            publishingAttemptTimeoutMillis = aOther.publishingAttemptTimeoutMillis ?: publishingAttemptTimeoutMillis,
            showPublishingProgressIfPossible = aOther.showPublishingProgressIfPossible ?: showPublishingProgressIfPossible
        )
    }

    fun AIcEffectiveEnabled(): Boolean = enabled ?: true
    fun AIcEffectivePublishingOrder(): Int = publishingOrder ?: 0
    fun AIcEffectivePublishingFailurePolicy(): String = publishingFailurePolicy ?: "FAIL_BUILD_ON_PUBLISHING_FAILURE"
    fun AIcEffectivePublishingRetryCount(): Int = publishingRetryCount ?: 0
    fun AIcEffectivePublishingRetryDelayMillis(): Long = publishingRetryDelayMillis ?: 1000L
    fun AIcEffectiveShowPublishingProgressIfPossible(): Boolean = showPublishingProgressIfPossible ?: true
}

data class AIcdModustroPublishingStabilityConfiguration(
    val publishingEnabled: Boolean? = null,
    val publishingEndpoints: Map<String, AIcdModustroPublishingEndpoint> = emptyMap()
) {
    fun AIcEffectivePublishingEnabled(): Boolean = publishingEnabled ?: false

    fun AIcMerge(aOther: AIcdModustroPublishingStabilityConfiguration): AIcdModustroPublishingStabilityConfiguration {
        val locEndpoints = linkedMapOf<String, AIcdModustroPublishingEndpoint>()
        publishingEndpoints.forEach { (locId, locEndpoint) -> locEndpoints[locId] = locEndpoint }
        aOther.publishingEndpoints.forEach { (locId, locEndpoint) ->
            locEndpoints[locId] = locEndpoints[locId]?.AIcMerge(locEndpoint) ?: locEndpoint
        }
        return AIcdModustroPublishingStabilityConfiguration(
            publishingEnabled = aOther.publishingEnabled ?: publishingEnabled,
            publishingEndpoints = locEndpoints
        )
    }
}

data class AIcdModustroOutputPublishingConfiguration(
    val snapshot: AIcdModustroPublishingStabilityConfiguration = AIcdModustroPublishingStabilityConfiguration(),
    val release: AIcdModustroPublishingStabilityConfiguration = AIcdModustroPublishingStabilityConfiguration()
) {
    fun AIcMerge(aOther: AIcdModustroOutputPublishingConfiguration): AIcdModustroOutputPublishingConfiguration =
        AIcdModustroOutputPublishingConfiguration(
            snapshot = snapshot.AIcMerge(aOther.snapshot),
            release = release.AIcMerge(aOther.release)
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
    val resourceEndpoints: List<AIcgdResourceEndpoint_1> = emptyList(),
    val credentialProfiles: Map<String, AIcdModustroCredentialProfileDefinition> = emptyMap(),
    val version: AIcdAlgitesVersion = AIcdAlgitesVersion(),
    val dependencies: List<AIcdModustroDependencyDefinition> = emptyList(),
    val dependencyItemsInheritancePoliciesByKind: Map<String, AInModustroItemsInheritancePolicy> = emptyMap(),
    val dependencyConstraints: List<AIcdModustroDependencyDefinition> = emptyList(),
    val dependencyConstraintItemsInheritancePoliciesByKind: Map<String, AInModustroItemsInheritancePolicy> = emptyMap(),
    val environmentRequirements: Map<String, AIcdAlgitesVersionRequirement> = emptyMap(),
    val deleteSnapshotWhenReleased: Boolean? = null,
    val nestedGradleSettingsBuildPolicy: String? = null,
    val outputPublishing: Map<String, AIcdModustroOutputPublishingConfiguration> = emptyMap()
) {
    fun AIcMerge(aOther: AIcdModustroResolvedState): AIcdModustroResolvedState {
        val locResourceEndpoints = AIcModustroResourceEndpointResolver.mergeDeclarations(
            resourceEndpoints,
            aOther.resourceEndpoints
        )

        val locProfiles = linkedMapOf<String, AIcdModustroCredentialProfileDefinition>()
        credentialProfiles.forEach { (locId, locProfile) -> locProfiles[locId] = locProfile }
        aOther.credentialProfiles.forEach { (locId, locProfile) ->
            locProfiles[locId] = locProfiles[locId]?.AIcMerge(locProfile) ?: locProfile
        }

        val locOutputPublishing = linkedMapOf<String, AIcdModustroOutputPublishingConfiguration>()
        outputPublishing.forEach { (locKind, locConfiguration) -> locOutputPublishing[locKind] = locConfiguration }
        aOther.outputPublishing.forEach { (locKind, locConfiguration) ->
            locOutputPublishing[locKind] = locOutputPublishing[locKind]?.AIcMerge(locConfiguration) ?: locConfiguration
        }

        return AIcdModustroResolvedState(
            technologyKinds = when {
                aOther.technologyKinds == null -> technologyKinds
                technologyKinds == null -> aOther.technologyKinds
                else -> technologyKinds.AIcMerge(aOther.technologyKinds)
            },
            groupId = aOther.groupId ?: groupId,
            resourceEndpoints = locResourceEndpoints,
            credentialProfiles = locProfiles,
            version = version.AIcMerge(aOther.version),
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
            deleteSnapshotWhenReleased = aOther.deleteSnapshotWhenReleased ?: deleteSnapshotWhenReleased,
            nestedGradleSettingsBuildPolicy = aOther.nestedGradleSettingsBuildPolicy ?: nestedGradleSettingsBuildPolicy,
            outputPublishing = locOutputPublishing
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
    val resourceEndpoints: List<AIcgdResourceEndpoint_1>,
    val credentialProfiles: Map<String, AIcdModustroCredentialProfileDefinition>,
    val contentsModel: String,
    val hasGradleBuild: Boolean,
    val gradleProjectPath: String,
    val version: AIcdAlgitesVersion,
    val dependencies: List<AIcdModustroDependencyDefinition>,
    val dependencyConstraints: List<AIcdModustroDependencyDefinition>,
    val environmentRequirements: Map<String, AIcdAlgitesVersionRequirement>,
    val deleteSnapshotWhenReleased: Boolean,
    val nestedGradleSettingsBuildPolicy: String,
    val outputPublishing: Map<String, AIcdModustroOutputPublishingConfiguration>,
    val descriptorHierarchy: List<AIcdModustroDescriptorDigest>
)

data class AIcdModustroRepositoryMetadata(
    val id: String,
    val name: String,
    val visibility: String,
    val groupId: String?,
    val resourceEndpoints: List<AIcgdResourceEndpoint_1>,
    val credentialProfiles: Map<String, AIcdModustroCredentialProfileDefinition>,
    val dependencies: List<AIcdModustroDependencyDefinition>,
    val dependencyConstraints: List<AIcdModustroDependencyDefinition>,
    val deleteSnapshotWhenReleased: Boolean,
    val nestedGradleSettingsBuildPolicy: String,
    val outputPublishing: Map<String, AIcdModustroOutputPublishingConfiguration>
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
val AIcModustroPublishingOutputKinds = linkedSetOf(
    "native_binary_output", "native_source_output", "native_documentation_output", "modustro_docs_site", "schema_site"
)
val AIcModustroPublishingFailurePolicies = linkedSetOf(
    "FAIL_BUILD_ON_PUBLISHING_FAILURE", "IGNORE_PUBLISHING_FAILURE"
)
val AIcModustroNestedGradleSettingsBuildPolicies = linkedSetOf(
    "IGNORE_NESTED_SETTINGS", "USE_ISOLATED_BUILD_ON_NESTED_SETTINGS"
)

val AIcModustroRootIgnoredDirectoryNames = setOf(
    ".git", ".gradle", ".idea", ".mps", "_obsolete", "run", "build", "target", "out", "output",
    "docs-site", "documentation-branch", "gh-pages", "source_gen", "source_gen.caches", "classes_gen"
)

fun AIcModustroBuiltInState(): AIcdModustroResolvedState = AIcdModustroResolvedState(
    deleteSnapshotWhenReleased = true,
    nestedGradleSettingsBuildPolicy = "IGNORE_NESTED_SETTINGS"
)

val AIcAlgitesExternalRepositoryDefaultsEnvironmentVariables = listOf(
    "ALGITES_REPOSITORY_PUBLIC_DEFAULTS_FILE",
    "ALGITES_REPOSITORY_GOVERNED_PUBLIC_DEFAULTS_FILE",
    "ALGITES_REPOSITORY_PRIVATE_DEFAULTS_FILE"
)

val AIcAlgitesPublicRepositoryDefaultsUrl =
    "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/repository/defaults/algites-repository-download-defaults-public.yml"

fun AIcAlgitesDownloadPublicRepositoryDefaults(): File {
    val locGradleUserHome = System.getenv("GRADLE_USER_HOME")?.trim()?.takeIf { it.isNotBlank() }?.let(::File)
        ?: File(System.getProperty("user.home"), ".gradle")
    val locFile = locGradleUserHome.resolve(
        "caches/algites/public-governance/repository/defaults/algites-repository-download-defaults-public.yml"
    )
    locFile.parentFile.mkdirs()
    try {
        val locBytes = URI(AIcAlgitesPublicRepositoryDefaultsUrl).toURL().openStream().use { locInput -> locInput.readBytes() }
        locFile.writeBytes(locBytes)
    } catch (locException: Exception) {
        throw IllegalStateException(
            "Algites public repository defaults are unavailable from '$AIcAlgitesPublicRepositoryDefaultsUrl'. " +
                "Set ALGITES_REPOSITORY_PUBLIC_DEFAULTS_FILE to a local defaults file to override the public GitHub fallback.",
            locException
        )
    }
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
            AIcAlgitesDownloadPublicRepositoryDefaults()
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
    val locRootState = if (locRootConfig == null) locInitialState else locInitialState.AIcMerge(AIcResolvedStateFromConfig(locRootConfig))
    AIcValidateEffectiveState(locRootState, "repository '${locRepositoryBase.id}'")

    val locRepository = AIcdModustroRepositoryMetadata(
        id = locRepositoryBase.id,
        name = locRepositoryBase.name,
        visibility = locRepositoryBase.visibility,
        groupId = locRootState.groupId ?: locRepositoryBase.groupId,
        resourceEndpoints = locRootState.resourceEndpoints,
        credentialProfiles = locRootState.credentialProfiles,
        dependencies = locRootState.dependencies,
        dependencyConstraints = locRootState.dependencyConstraints,
        deleteSnapshotWhenReleased = locRootState.deleteSnapshotWhenReleased ?: true,
        nestedGradleSettingsBuildPolicy = locRootState.nestedGradleSettingsBuildPolicy ?: "IGNORE_NESTED_SETTINGS",
        outputPublishing = locRootState.outputPublishing
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
        locRepositoryId, locRepositoryName, locVisibility, locGroupId, emptyList(), emptyMap(),
        emptyList(), emptyList(), true, "IGNORE_NESTED_SETTINGS", emptyMap()
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
            locState = locState.AIcMerge(AIcResolvedStateFromConfig(locConfig))
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
    AIcFindModustroMetadataConfig(aRepositoryRoot, aRepositoryRoot)?.let { locState = locState.AIcMerge(AIcResolvedStateFromConfig(it)) }
    locSegments.dropLast(1).forEach { locSegment ->
        locDirectory = locDirectory.resolve(locSegment)
        if (!locDirectory.isDirectory) error("Artifact directory parent path '${locDirectory.path}' does not exist.")
        AIcFindModustroMetadataConfig(locDirectory, aRepositoryRoot)?.let { locState = locState.AIcMerge(AIcResolvedStateFromConfig(it)) }
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
    AIcFindModustroMetadataConfig(aRepositoryRoot, aRepositoryRoot)?.let { locState = locState.AIcMerge(AIcResolvedStateFromConfig(it)) }
    AIcPathSegments(aArtifactDirectoryPath).forEach { locSegment ->
        locDirectory = locDirectory.resolve(locSegment)
        if (!locDirectory.isDirectory) error("Artifact directory path '$aArtifactDirectoryPath' does not exist.")
        AIcFindModustroMetadataConfig(locDirectory, aRepositoryRoot)?.let { locState = locState.AIcMerge(AIcResolvedStateFromConfig(it)) }
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
    return AIcdModustroArtifactDirectoryMetadata(
        path = locPath,
        structureKind = aConfig.structureKind,
        technologyKinds = aState.technologyKinds?.AIcTechnologyKinds() ?: emptyList(),
        buildOutputTypesByTechnologyKind = aState.technologyKinds?.AIcBuildOutputTypesByTechnologyKind().orEmpty(),
        name = locName,
        description = locDescription,
        groupId = aState.groupId,
        variantId = locVariantId,
        resourceEndpoints = aState.resourceEndpoints,
        credentialProfiles = aState.credentialProfiles,
        contentsModel = aContentsModel,
        hasGradleBuild = AIcHasGradleBuild(aDirectory),
        gradleProjectPath = AIcGradleProjectPath(aRepositoryRoot, aDirectory),
        version = aState.version,
        dependencies = aState.dependencies,
        dependencyConstraints = aState.dependencyConstraints,
        environmentRequirements = aState.environmentRequirements,
        deleteSnapshotWhenReleased = aState.deleteSnapshotWhenReleased ?: true,
        nestedGradleSettingsBuildPolicy = aState.nestedGradleSettingsBuildPolicy ?: "IGNORE_NESTED_SETTINGS",
        outputPublishing = aState.outputPublishing,
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

fun AIcResolvedStateFromConfig(aConfig: AIcdModustroDirectoryConfig): AIcdModustroResolvedState {
    val locPrefix = AIcStructureKindPrefix(aConfig.structureKind)
    val locBase = AIcResolvedStateFromRawValues(aConfig.values, locPrefix, aConfig.file)
    val locTechnologyKinds = when (aConfig.structureKind) {
        "artifact-set", "artifact" -> AIcTechnologyKindsFromConfig(aConfig.values, "$locPrefix.TechnologyKinds", aConfig.file)
        else -> null
    }?.also { locConfig ->
        val locUnsupported = locConfig.AIcTechnologyKinds().filter { it !in AIcModustroSupportedTechnologyKinds }
        if (locUnsupported.isNotEmpty()) error("Unsupported Algites TechnologyKind(s) in '${aConfig.file.path}': ${locUnsupported.joinToString(", ")}.")
    }
    return locBase.copy(technologyKinds = locTechnologyKinds)
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
        resourceEndpoints = AIcResourceEndpointsFromConfig(aValues, aPrefix, aFile),
        credentialProfiles = AIcCredentialProfilesFromConfig(aValues, aFile),
        version = locVersion,
        dependencies = locDependencies.definitions,
        dependencyItemsInheritancePoliciesByKind = locDependencies.itemsInheritancePoliciesByDependencyKind,
        dependencyConstraints = locDependencyConstraints.definitions,
        dependencyConstraintItemsInheritancePoliciesByKind = locDependencyConstraints.itemsInheritancePoliciesByDependencyKind,
        environmentRequirements = AIcEnvironmentRequirementsFromConfig(aValues, aFile),
        deleteSnapshotWhenReleased = AIcFirstValue(aValues, "DeleteSnapshotWhenReleased")
            ?.takeIf { it.isNotBlank() }
            ?.let { AIcParseBoolean(it, "DeleteSnapshotWhenReleased", aFile) },
        nestedGradleSettingsBuildPolicy = AIcFirstValue(aValues, "NestedGradleSettingsBuildPolicy")
            ?.takeIf { it.isNotBlank() }
            ?.also { locPolicy ->
                if (locPolicy !in AIcModustroNestedGradleSettingsBuildPolicies) {
                    error("Unsupported NestedGradleSettingsBuildPolicy '$locPolicy' in '${aFile.path}'. Supported values: ${AIcModustroNestedGradleSettingsBuildPolicies.joinToString(", ")}.")
                }
            },
        outputPublishing = AIcOutputPublishingFromConfig(aValues, aFile)
    )
}

fun AIcOutputPublishingFromConfig(
    aValues: Map<String, String>,
    aFile: File
): Map<String, AIcdModustroOutputPublishingConfiguration> {
    val locResult = linkedMapOf<String, AIcdModustroOutputPublishingConfiguration>()
    AIcModustroPublishingOutputKinds.forEach { locOutputKind ->
        fun locStability(aStability: String): AIcdModustroPublishingStabilityConfiguration {
            val locPrefix = "$locOutputKind.$aStability"
            val locPublishingEnabled = aValues["$locPrefix.PublishingEnabled"]?.let { AIcParseBoolean(it, "$locPrefix.PublishingEnabled", aFile) }
            data class AIcdBuilder(
                var id: String? = null, var enabled: Boolean? = null, var publishingUrl: String? = null,
                var publishingAdapter: String? = null, var publishingCredentialProfile: String? = null,
                var publishingOrder: Int? = null, var publishingFailurePolicy: String? = null,
                var publishingRetryCount: Int? = null, var publishingRetryDelayMillis: Long? = null,
                var publishingAttemptTimeoutMillis: Long? = null, var showPublishingProgressIfPossible: Boolean? = null
            )
            val locBuilders = linkedMapOf<String, AIcdBuilder>()
            val locEndpointPrefix = "$locPrefix.PublishingEndpoints."
            aValues.forEach { (locKey, locValue) ->
                if (!locKey.startsWith(locEndpointPrefix)) return@forEach
                val locSegments = locKey.removePrefix(locEndpointPrefix).split('.')
                if (locSegments.size < 2 || locSegments[0].toIntOrNull() == null) return@forEach
                val locBuilder = locBuilders.getOrPut(locSegments[0]) { AIcdBuilder() }
                when (locSegments.drop(1).joinToString(".")) {
                    "Id" -> locBuilder.id = locValue.trim()
                    "Enabled" -> locBuilder.enabled = AIcParseBoolean(locValue, locKey, aFile)
                    "PublishingUrl" -> locBuilder.publishingUrl = locValue.trim().takeIf { it.isNotBlank() }
                    "PublishingAdapter" -> locBuilder.publishingAdapter = locValue.trim().takeIf { it.isNotBlank() }
                    "PublishingCredentialProfile" -> locBuilder.publishingCredentialProfile = locValue.trim().takeIf { it.isNotBlank() }
                    "PublishingOrder" -> locBuilder.publishingOrder = locValue.trim().toIntOrNull() ?: error("$locKey in '${aFile.path}' must be an integer.")
                    "PublishingFailurePolicy" -> locBuilder.publishingFailurePolicy = locValue.trim().also { locPolicy -> if (locPolicy !in AIcModustroPublishingFailurePolicies) error("$locKey in '${aFile.path}' uses unsupported policy '$locPolicy'.") }
                    "PublishingRetryCount" -> locBuilder.publishingRetryCount = locValue.trim().toIntOrNull()?.also { if (it < 0) error("$locKey in '${aFile.path}' must be non-negative.") } ?: error("$locKey in '${aFile.path}' must be a non-negative integer.")
                    "PublishingRetryDelayMillis" -> locBuilder.publishingRetryDelayMillis = locValue.trim().toLongOrNull()?.also { if (it < 0L) error("$locKey in '${aFile.path}' must be non-negative.") } ?: error("$locKey in '${aFile.path}' must be a non-negative integer.")
                    "PublishingAttemptTimeoutMillis" -> locBuilder.publishingAttemptTimeoutMillis = locValue.trim().toLongOrNull()?.also { if (it <= 0L) error("$locKey in '${aFile.path}' must be positive.") } ?: error("$locKey in '${aFile.path}' must be a positive integer.")
                    "ShowPublishingProgressIfPossible" -> locBuilder.showPublishingProgressIfPossible = AIcParseBoolean(locValue, locKey, aFile)
                }
            }
            val locEndpoints = linkedMapOf<String, AIcdModustroPublishingEndpoint>()
            locBuilders.toSortedMap(compareBy { it.toInt() }).values.forEach { locBuilder ->
                val locId = locBuilder.id?.takeIf { it.isNotBlank() } ?: error("Publishing endpoint in '${aFile.path}' under '$locPrefix' is missing required Id.")
                if (!Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$").matches(locId)) error("Publishing endpoint Id '$locId' in '${aFile.path}' must use lowercase dash-separated form.")
                if (locEndpoints.containsKey(locId)) error("Duplicate publishing endpoint Id '$locId' in '${aFile.path}' under '$locPrefix'.")
                locBuilder.publishingUrl?.let { locUrl ->
                    val locUri = try { URI(locUrl) } catch (locException: Exception) {
                        error("PublishingUrl '$locUrl' for endpoint '$locId' in '${aFile.path}' is not a valid URI: ${locException.message}")
                    }
                    if (locUri.scheme.isNullOrBlank()) {
                        error("PublishingUrl '$locUrl' for endpoint '$locId' in '${aFile.path}' must be an absolute URI with a scheme.")
                    }
                }
                locEndpoints[locId] = AIcdModustroPublishingEndpoint(
                    id=locId, enabled=locBuilder.enabled, publishingUrl=locBuilder.publishingUrl,
                    publishingAdapter=locBuilder.publishingAdapter, publishingCredentialProfile=locBuilder.publishingCredentialProfile,
                    publishingOrder=locBuilder.publishingOrder, publishingFailurePolicy=locBuilder.publishingFailurePolicy,
                    publishingRetryCount=locBuilder.publishingRetryCount, publishingRetryDelayMillis=locBuilder.publishingRetryDelayMillis,
                    publishingAttemptTimeoutMillis=locBuilder.publishingAttemptTimeoutMillis,
                    showPublishingProgressIfPossible=locBuilder.showPublishingProgressIfPossible
                )
            }
            return AIcdModustroPublishingStabilityConfiguration(locPublishingEnabled, locEndpoints)
        }
        val locSnapshot=locStability("Snapshot"); val locRelease=locStability("Release")
        if (locSnapshot.publishingEnabled != null || locSnapshot.publishingEndpoints.isNotEmpty() || locRelease.publishingEnabled != null || locRelease.publishingEndpoints.isNotEmpty()) {
            locResult[locOutputKind]=AIcdModustroOutputPublishingConfiguration(locSnapshot,locRelease)
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


fun AIcResourceEndpointOverridesFromConfig(
    aValues: Map<String, String>,
    aPrefix: String,
    aFile: File
): List<AIcgdResourceEndpoint_1> {
    val locPrefixes = listOf("$aPrefix.ResourceEndpoints.", "ResourceEndpoints.").filter { !it.startsWith(".ResourceEndpoints") }
    data class AIcdBuilder(
        var id: String? = null,
        var url: String? = null,
        var credentialProfile: String? = null,
        var enabled: Boolean? = null,
        var stability: String? = null,
        var resourceEndpointProviderAdapter: String? = null
    )
    val locBuilders = linkedMapOf<Pair<String, String>, AIcdBuilder>()

    aValues.forEach { (locKey, locRawValue) ->
        val locPrefix = locPrefixes.firstOrNull { locKey.startsWith(it) } ?: return@forEach
        val locSegments = locKey.removePrefix(locPrefix).split('.')
        if (locSegments.size < 6) return@forEach
        val locTechnology = locSegments[0].lowercase()
        val locResourceKind = locSegments[1].lowercase()
        val locVisibility = locSegments[2].lowercase()
        val locAction = locSegments[3].lowercase()
        val locIndex = locSegments[4]
        val locProperty = locSegments.drop(5).joinToString(".")
        if (locIndex.toIntOrNull() == null) return@forEach
        val locCell = "$locTechnology.$locResourceKind.$locVisibility.$locAction"
        val locBuilder = locBuilders.getOrPut(locCell to locIndex) { AIcdBuilder() }
        when (locProperty) {
            "Id" -> locBuilder.id = locRawValue.trim()
            "Url" -> locBuilder.url = locRawValue.trim().takeIf { it.isNotBlank() }
            "CredentialProfile" -> locBuilder.credentialProfile = locRawValue.trim().takeIf { it.isNotBlank() }
            "Enabled" -> locBuilder.enabled = AIcParseBoolean(locRawValue, "resource endpoint enabled", aFile)
            "Stability" -> locBuilder.stability = locRawValue.trim().lowercase().takeIf { it.isNotBlank() }
            "ResourceEndpointProviderAdapter" -> locBuilder.resourceEndpointProviderAdapter = locRawValue.trim().lowercase().takeIf { it.isNotBlank() }
        }
    }

    return locBuilders.map { (locKey, locBuilder) ->
        val locCell = locKey.first
        val locSegments = locCell.split('.')
        val locId = locBuilder.id?.takeIf { it.isNotBlank() }
            ?: error("ResourceEndpoint in '${aFile.path}' cell '$locCell' is missing required Id.")
        try {
            AIcModustroResourceEndpointMetadataBridge.declaration(
                locSegments[0],
                locSegments[1],
                locSegments[2],
                locSegments[3],
                locId,
                locBuilder.url,
                locBuilder.credentialProfile,
                locBuilder.enabled,
                locBuilder.stability,
                locBuilder.resourceEndpointProviderAdapter
            )
        } catch (locException: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid ResourceEndpoint '$locId' in '${aFile.path}' cell '$locCell': ${locException.message}", locException)
        }
    }
}

fun AIcResourceEndpointsFromConfig(
    aValues: Map<String, String>,
    aPrefix: String,
    aFile: File
): List<AIcgdResourceEndpoint_1> =
    AIcResourceEndpointOverridesFromConfig(aValues, aPrefix, aFile)

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

    val locCatalog = try {
        AIcModustroResourceEndpointResolver.resolve(aState.resourceEndpoints)
    } catch (locException: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid effective ResourceEndpoints in $aContext: ${locException.message}", locException)
    }
    locCatalog.all().forEach { locEndpoint ->
        val locProfileId = locEndpoint.credentialProfile()
        if (!locProfileId.isNullOrBlank()) {
            val locProfile = aState.credentialProfiles[locProfileId]
                ?: error("ResourceEndpoint '${locEndpoint.id()}' in $aContext references undefined credential profile '$locProfileId'.")
            if (locProfile.type.isNullOrBlank()) {
                error("Credential profile '$locProfileId' referenced by '${locEndpoint.id()}' in $aContext has no type after inheritance.")
            }
        }
    }

    aState.outputPublishing.forEach { (locOutputKind, locConfiguration) ->
        listOf("Snapshot" to locConfiguration.snapshot, "Release" to locConfiguration.release).forEach { (locStability, locPublishing) ->
            locPublishing.publishingEndpoints.values.forEach { locEndpoint ->
                if (locEndpoint.AIcEffectiveEnabled() && locPublishing.publishingEnabled == true) {
                    if (locEndpoint.publishingUrl.isNullOrBlank()) error("Enabled publishing endpoint '${locEndpoint.id}' in $aContext $locOutputKind.$locStability has no PublishingUrl after inheritance.")
                    if (locEndpoint.publishingAdapter.isNullOrBlank()) error("Enabled publishing endpoint '${locEndpoint.id}' in $aContext $locOutputKind.$locStability has no PublishingAdapter after inheritance.")
                }
                val locProfileId = locEndpoint.publishingCredentialProfile
                if (!locProfileId.isNullOrBlank()) {
                    val locProfile = aState.credentialProfiles[locProfileId]
                        ?: error("Publishing endpoint '${locEndpoint.id}' in $aContext references undefined credential profile '$locProfileId'.")
                    if (locProfile.type.isNullOrBlank()) {
                        error("Credential profile '$locProfileId' referenced by publishing endpoint '${locEndpoint.id}' in $aContext has no type after inheritance.")
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

fun AIcResourceEndpointMapForOutput(aResourceEndpoints: List<AIcgdResourceEndpoint_1>): Map<String, Any?> {
    val locCatalog = AIcModustroResourceEndpointResolver.resolve(aResourceEndpoints)
    return locCatalog.all()
        .groupBy { locEndpoint -> locEndpoint.cell() }
        .toSortedMap()
        .mapValues { (_, locEndpoints) ->
            locEndpoints.map { locEndpoint ->
                linkedMapOf<String, Any?>(
                    "id" to locEndpoint.id(),
                    "url" to locEndpoint.url().toString(),
                    "credentialProfile" to locEndpoint.credentialProfile(),
                    "enabled" to locEndpoint.enabled(),
                    "stability" to locEndpoint.stability()?.wireValue(),
                    "resourceEndpointProviderAdapter" to locEndpoint.resourceEndpointProviderAdapter()
                )
            }
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


fun AIcPublishingEndpointMapForOutput(aEndpoint: AIcdModustroPublishingEndpoint): Map<String, Any?> = linkedMapOf(
    "id" to aEndpoint.id,
    "enabled" to aEndpoint.AIcEffectiveEnabled(),
    "publishingUrl" to aEndpoint.publishingUrl,
    "publishingAdapter" to aEndpoint.publishingAdapter,
    "publishingCredentialProfile" to aEndpoint.publishingCredentialProfile,
    "publishingOrder" to aEndpoint.AIcEffectivePublishingOrder(),
    "publishingFailurePolicy" to aEndpoint.AIcEffectivePublishingFailurePolicy(),
    "publishingRetryCount" to aEndpoint.AIcEffectivePublishingRetryCount(),
    "publishingRetryDelayMillis" to aEndpoint.AIcEffectivePublishingRetryDelayMillis(),
    "publishingAttemptTimeoutMillis" to aEndpoint.publishingAttemptTimeoutMillis,
    "showPublishingProgressIfPossible" to aEndpoint.AIcEffectiveShowPublishingProgressIfPossible()
)

fun AIcOutputPublishingMapForOutput(aPublishing: Map<String, AIcdModustroOutputPublishingConfiguration>): Map<String, Any?> =
    aPublishing.toSortedMap().mapValues { (_, locConfiguration) ->
        linkedMapOf(
            "snapshot" to linkedMapOf(
                "publishingEnabled" to locConfiguration.snapshot.AIcEffectivePublishingEnabled(),
                "publishingEndpoints" to locConfiguration.snapshot.publishingEndpoints.values.map(::AIcPublishingEndpointMapForOutput)
            ),
            "release" to linkedMapOf(
                "publishingEnabled" to locConfiguration.release.AIcEffectivePublishingEnabled(),
                "publishingEndpoints" to locConfiguration.release.publishingEndpoints.values.map(::AIcPublishingEndpointMapForOutput)
            )
        )
    }

fun AIcEnvironmentRequirementsMapForOutput(aRequirements: Map<String, AIcdAlgitesVersionRequirement>): Map<String, Any?> =
    aRequirements.toSortedMap().mapValues { (_, locRequirement) -> AIcVersionRequirementMapForOutput(locRequirement) }

fun AIcToMap(aResult: AIcdModustroResolutionResult): Map<String, Any?> = linkedMapOf(
    "repository" to linkedMapOf(
        "id" to aResult.repository.id,
        "name" to aResult.repository.name,
        "visibility" to aResult.repository.visibility,
        "groupId" to aResult.repository.groupId,
        "resourceEndpoints" to AIcResourceEndpointMapForOutput(aResult.repository.resourceEndpoints),
        "credentialProfiles" to AIcCredentialProfilesMapForOutput(aResult.repository.credentialProfiles),
        "dependencies" to aResult.repository.dependencies.map(::AIcDependencyMapForOutput),
        "dependencyConstraints" to aResult.repository.dependencyConstraints.map(::AIcDependencyMapForOutput),
        "deleteSnapshotWhenReleased" to aResult.repository.deleteSnapshotWhenReleased,
        "nestedGradleSettingsBuildPolicy" to aResult.repository.nestedGradleSettingsBuildPolicy,
        "outputPublishing" to AIcOutputPublishingMapForOutput(aResult.repository.outputPublishing)
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
            "resourceEndpoints" to AIcResourceEndpointMapForOutput(locDirectory.resourceEndpoints),
            "credentialProfiles" to AIcCredentialProfilesMapForOutput(locDirectory.credentialProfiles),
            "dependencies" to locDirectory.dependencies.map(::AIcDependencyMapForOutput),
            "dependencyConstraints" to locDirectory.dependencyConstraints.map(::AIcDependencyMapForOutput),
            "environmentRequirements" to AIcEnvironmentRequirementsMapForOutput(locDirectory.environmentRequirements),
            "deleteSnapshotWhenReleased" to locDirectory.deleteSnapshotWhenReleased,
            "nestedGradleSettingsBuildPolicy" to locDirectory.nestedGradleSettingsBuildPolicy,
            "outputPublishing" to AIcOutputPublishingMapForOutput(locDirectory.outputPublishing),
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
    appendLine("  ResourceEndpoints: ${AIcYamlScalar(AIcResourceEndpointMapForOutput(aResult.repository.resourceEndpoints).toString())}")
    appendLine("  CredentialProfiles: ${AIcYamlScalar(AIcCredentialProfilesMapForOutput(aResult.repository.credentialProfiles).toString())}")
    appendLine("  Dependencies: ${AIcYamlScalar(aResult.repository.dependencies.map(::AIcDependencyMapForOutput).toString())}")
    appendLine("  DependencyConstraints: ${AIcYamlScalar(aResult.repository.dependencyConstraints.map(::AIcDependencyMapForOutput).toString())}")
    appendLine("  DeleteSnapshotWhenReleased: ${aResult.repository.deleteSnapshotWhenReleased}")
    appendLine("  NestedGradleSettingsBuildPolicy: ${AIcYamlScalar(aResult.repository.nestedGradleSettingsBuildPolicy)}")
    appendLine("  OutputPublishing: ${AIcYamlScalar(AIcOutputPublishingMapForOutput(aResult.repository.outputPublishing).toString())}")
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
        appendLine("    ResourceEndpoints: ${AIcYamlScalar(AIcResourceEndpointMapForOutput(locDirectory.resourceEndpoints).toString())}")
        appendLine("    CredentialProfiles: ${AIcYamlScalar(AIcCredentialProfilesMapForOutput(locDirectory.credentialProfiles).toString())}")
        appendLine("    Dependencies: ${AIcYamlScalar(locDirectory.dependencies.map(::AIcDependencyMapForOutput).toString())}")
        appendLine("    DependencyConstraints: ${AIcYamlScalar(locDirectory.dependencyConstraints.map(::AIcDependencyMapForOutput).toString())}")
        appendLine("    EnvironmentRequirements: ${AIcYamlScalar(AIcEnvironmentRequirementsMapForOutput(locDirectory.environmentRequirements).toString())}")
        appendLine("    DeleteSnapshotWhenReleased: ${locDirectory.deleteSnapshotWhenReleased}")
        appendLine("    NestedGradleSettingsBuildPolicy: ${AIcYamlScalar(locDirectory.nestedGradleSettingsBuildPolicy)}")
        appendLine("    OutputPublishing: ${AIcYamlScalar(AIcOutputPublishingMapForOutput(locDirectory.outputPublishing).toString())}")
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

extra["modustroResolveArtifactDirectoryMetadata"] = ::AIcResolveModustroArtifactDirectoryMetadata
extra["modustroResolveArtifactDirectoryMetadataMap"] = fun(
    aRepositoryRoot: File,
    aArtifactDirectoryPath: String?,
    aResolutionKind: String?,
    aRepositoryNameOverride: String?,
    aRepositoryVisibilityOverride: String?
): Map<String, Any?> = AIcToMap(AIcResolveModustroArtifactDirectoryMetadata(aRepositoryRoot, aArtifactDirectoryPath, aResolutionKind, aRepositoryNameOverride, aRepositoryVisibilityOverride))
extra["modustroResolveArtifactDirectoryMetadataText"] = fun(
    aRepositoryRoot: File,
    aArtifactDirectoryPath: String?,
    aResolutionKind: String?,
    aRepositoryNameOverride: String?,
    aRepositoryVisibilityOverride: String?,
    aOutputKind: String?
): String = AIcFormatOutput(
    AIcResolveModustroArtifactDirectoryMetadata(aRepositoryRoot, aArtifactDirectoryPath, aResolutionKind, aRepositoryNameOverride, aRepositoryVisibilityOverride),
    aOutputKind?.takeIf { it.isNotBlank() } ?: "yaml"
)
extra["modustroFlattenArtifactDirectoryMetadata"] = ::AIcFlattenDottedProperties
