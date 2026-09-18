/*
 * Algites artifact directory metadata resolver core.
 *
 * This script intentionally contains only Settings/Project compatible logic.
 * It resolves structural metadata, TechnologyKinds, inherited groupId,
 * credential profiles, version contexts, and the effective repository matrix.
 */

import java.io.File

data class AIcAlgitesVersionContext(
    val lane: String? = null,
    val revision: String? = null,
    val qualifierKind: String? = null,
    val qualifierLabel: String? = null
) {
    fun AIcMerge(aOther: AIcAlgitesVersionContext): AIcAlgitesVersionContext = AIcAlgitesVersionContext(
        lane = aOther.lane ?: lane,
        revision = aOther.revision ?: revision,
        qualifierKind = aOther.qualifierKind ?: qualifierKind,
        qualifierLabel = aOther.qualifierLabel ?: qualifierLabel
    )

    fun AIcResolvedValue(): String? {
        val locLane = lane?.takeIf { it.isNotBlank() } ?: return null
        val locRevision = revision?.takeIf { it.isNotBlank() }
        val locQualifier = qualifierLabel?.takeIf { it.isNotBlank() }
            ?: qualifierKind?.takeIf { it.isNotBlank() }
                ?.takeUnless { it.equals("RELEASE", true) || it.equals("FINAL", true) }
        val locBase = if (locRevision == null) locLane else "$locLane.$locRevision"
        return if (locQualifier == null) locBase else "$locBase-${locQualifier.uppercase()}"
    }
}

data class AIcAlgitesRepositoryEndpoint(
    val id: String,
    val url: String? = null,
    val credentialProfile: String? = null,
    val enabled: Boolean? = null
) {
    fun AIcMerge(aOther: AIcAlgitesRepositoryEndpoint): AIcAlgitesRepositoryEndpoint {
        require(id == aOther.id) { "Cannot merge repository endpoints with different ids '$id' and '${aOther.id}'." }
        return AIcAlgitesRepositoryEndpoint(
            id = id,
            url = aOther.url ?: url,
            credentialProfile = aOther.credentialProfile ?: credentialProfile,
            enabled = aOther.enabled ?: enabled
        )
    }

    fun AIcEffectiveEnabled(): Boolean = enabled ?: true
}

data class AIcAlgitesCredentialProfileDefinition(
    val id: String,
    val type: String? = null,
    val configuration: Map<String, String> = emptyMap()
) {
    fun AIcMerge(aOther: AIcAlgitesCredentialProfileDefinition): AIcAlgitesCredentialProfileDefinition {
        require(id == aOther.id) { "Cannot merge credential profiles with different ids '$id' and '${aOther.id}'." }
        return AIcAlgitesCredentialProfileDefinition(
            id = id,
            type = aOther.type ?: type,
            configuration = configuration + aOther.configuration
        )
    }
}

data class AIcAlgitesResolvedState(
    val technologyKinds: List<String>? = null,
    val groupId: String? = null,
    val repositories: Map<String, Map<String, AIcAlgitesRepositoryEndpoint>> = emptyMap(),
    val credentialProfiles: Map<String, AIcAlgitesCredentialProfileDefinition> = emptyMap(),
    val versionContext: AIcAlgitesVersionContext = AIcAlgitesVersionContext()
) {
    fun AIcMerge(aOther: AIcAlgitesResolvedState): AIcAlgitesResolvedState {
        val locRepositories = linkedMapOf<String, Map<String, AIcAlgitesRepositoryEndpoint>>()
        (repositories.keys + aOther.repositories.keys).distinct().forEach { locCell ->
            val locMerged = linkedMapOf<String, AIcAlgitesRepositoryEndpoint>()
            repositories[locCell]?.forEach { (locId, locEndpoint) -> locMerged[locId] = locEndpoint }
            aOther.repositories[locCell]?.forEach { (locId, locEndpoint) ->
                locMerged[locId] = locMerged[locId]?.AIcMerge(locEndpoint) ?: locEndpoint
            }
            locRepositories[locCell] = locMerged
        }

        val locProfiles = linkedMapOf<String, AIcAlgitesCredentialProfileDefinition>()
        credentialProfiles.forEach { (locId, locProfile) -> locProfiles[locId] = locProfile }
        aOther.credentialProfiles.forEach { (locId, locProfile) ->
            locProfiles[locId] = locProfiles[locId]?.AIcMerge(locProfile) ?: locProfile
        }

        return AIcAlgitesResolvedState(
            technologyKinds = aOther.technologyKinds ?: technologyKinds,
            groupId = aOther.groupId ?: groupId,
            repositories = locRepositories,
            credentialProfiles = locProfiles,
            versionContext = versionContext.AIcMerge(aOther.versionContext)
        )
    }
}

data class AIcAlgitesDirectoryConfig(
    val file: File,
    val structureKind: String,
    val values: Map<String, String>
)

data class AIcAlgitesArtifactDirectoryMetadata(
    val path: String,
    val structureKind: String,
    val technologyKinds: List<String>,
    val name: String,
    val description: String,
    val groupId: String?,
    val repositories: Map<String, Map<String, AIcAlgitesRepositoryEndpoint>>,
    val credentialProfiles: Map<String, AIcAlgitesCredentialProfileDefinition>,
    val contentsModel: String,
    val hasGradleBuild: Boolean,
    val gradleProjectPath: String,
    val versionContext: AIcAlgitesVersionContext
)

data class AIcAlgitesRepositoryMetadata(
    val id: String,
    val name: String,
    val visibility: String,
    val groupId: String?,
    val repositories: Map<String, Map<String, AIcAlgitesRepositoryEndpoint>>,
    val credentialProfiles: Map<String, AIcAlgitesCredentialProfileDefinition>
)

data class AIcAlgitesResolutionResult(
    val repository: AIcAlgitesRepositoryMetadata,
    val artifactDirectories: List<AIcAlgitesArtifactDirectoryMetadata>
)

val AIcAlgitesSupportedTechnologyKinds = linkedSetOf("java", "python", "mps")
val AIcAlgitesRepositoryVisibilities = linkedSetOf("public", "private")
val AIcAlgitesRepositoryStabilities = linkedSetOf("release", "snapshot")
val AIcAlgitesRepositoryUsages = linkedSetOf("download", "upload")
val AIcAlgitesCredentialTypes = linkedSetOf("basic", "bearer", "api-key", "client-certificate")

val AIcAlgitesRootIgnoredDirectoryNames = setOf(
    ".git", ".gradle", ".idea", ".mps", "run", "build", "target", "out", "output",
    "docs-site", "documentation-branch", "gh-pages", "source_gen", "source_gen.caches", "classes_gen"
)

fun AIcAlgitesBuiltInState(): AIcAlgitesResolvedState {
    fun locEndpoint(aCell: String): Pair<String, Map<String, AIcAlgitesRepositoryEndpoint>> {
        val locSegments = aCell.split('.')
        val locId = "algites-${locSegments.joinToString("-")}"
        val locUrl = when (aCell) {
            "java.public.release.download" -> "https://repo1.maven.org/maven2"
            "java.public.snapshot.download" -> "https://dl.cloudsmith.io/public/algites/maven-snapshots-pub/maven/"
            else -> error("Unknown built-in repository cell '$aCell'.")
        }
        return aCell to linkedMapOf(locId to AIcAlgitesRepositoryEndpoint(id = locId, url = locUrl, enabled = true))
    }
    return AIcAlgitesResolvedState(
        repositories = linkedMapOf(
            locEndpoint("java.public.release.download"),
            locEndpoint("java.public.snapshot.download")
        )
    )
}

val AIcAlgitesExternalRepositoryDefaultsEnvironmentVariables = listOf(
    "ALGITES_REPOSITORY_PRIVATE_DEFAULTS_FILE",
    "ALGITES_REPOSITORY_UPLOAD_DEFAULTS_FILE"
)

fun AIcAlgitesExternalDefaultsState(): AIcAlgitesResolvedState {
    var locState = AIcAlgitesResolvedState()
    AIcAlgitesExternalRepositoryDefaultsEnvironmentVariables.forEach { locVariableName ->
        val locPath = System.getenv(locVariableName)?.trim()?.takeIf { it.isNotBlank() } ?: return@forEach
        val locFile = File(locPath)
        if (!locFile.isFile) {
            error("Algites repository defaults file does not exist: '${locFile.path}'.")
        }
        locState = locState.AIcMerge(AIcResolvedStateFromRawValues(AIcReadSimpleYamlScalars(locFile), "", locFile))
    }
    return locState
}

fun AIcResolveAlgitesArtifactDirectoryMetadata(
    aRepositoryRoot: File,
    aArtifactDirectoryPath: String?,
    aResolutionKind: String?,
    aRepositoryNameOverride: String?,
    aRepositoryVisibilityOverride: String?
): AIcAlgitesResolutionResult {
    val locRepositoryBase = AIcResolveRepositoryMetadataBase(aRepositoryRoot, aRepositoryNameOverride, aRepositoryVisibilityOverride)
    val locInitialState = AIcAlgitesBuiltInState().AIcMerge(AIcAlgitesExternalDefaultsState())
    val locRootConfig = AIcFindAlgitesMetadataConfig(aRepositoryRoot, aRepositoryRoot)?.takeIf { it.structureKind == "repository" }
    val locRootState = if (locRootConfig == null) locInitialState else locInitialState.AIcMerge(AIcResolvedStateFromConfig(locRootConfig))
    AIcValidateEffectiveState(locRootState, "repository '${locRepositoryBase.id}'")

    val locRepository = AIcAlgitesRepositoryMetadata(
        id = locRepositoryBase.id,
        name = locRepositoryBase.name,
        visibility = locRepositoryBase.visibility,
        groupId = locRootState.groupId ?: locRepositoryBase.groupId,
        repositories = locRootState.repositories,
        credentialProfiles = locRootState.credentialProfiles
    )

    val locNormalizedPath = aArtifactDirectoryPath?.trim()?.replace('\\', '/')?.trim('/')?.takeIf { it.isNotBlank() && it != "." }
    val locResolutionKind = aResolutionKind?.trim()?.takeIf { it.isNotBlank() } ?: "current-with-subdirs"
    if (locResolutionKind !in setOf("current-only", "current-with-subdirs")) {
        error("Unsupported Algites artifact directory resolution kind '$locResolutionKind'. Supported values are: current-only, current-with-subdirs.")
    }

    val locArtifactDirectories = if (locResolutionKind == "current-only") {
        listOf(AIcResolveSingleArtifactDirectory(aRepositoryRoot, locNormalizedPath ?: ".", locInitialState))
    } else {
        AIcResolveArtifactDirectoryAndSubdirectories(aRepositoryRoot, locNormalizedPath ?: ".", locInitialState)
    }

    return AIcAlgitesResolutionResult(locRepository, locArtifactDirectories)
}

fun AIcResolveRepositoryMetadataBase(
    aRepositoryRoot: File,
    aRepositoryNameOverride: String?,
    aRepositoryVisibilityOverride: String?
): AIcAlgitesRepositoryMetadata {
    val locRootConfig = AIcFindAlgitesMetadataConfig(aRepositoryRoot, aRepositoryRoot)?.takeIf { it.structureKind == "repository" }
    val locRepositoryId = locRootConfig?.values?.let { AIcFirstValue(it, "sourceRepository.id", "repository.id", "id") }
        ?.takeIf { it.isNotBlank() } ?: aRepositoryRoot.name
    val locRepositoryName = aRepositoryNameOverride?.takeIf { it.isNotBlank() }
        ?: locRootConfig?.values?.let { AIcFirstValue(it, "sourceRepository.name", "repository.name", "name") }?.takeIf { it.isNotBlank() }
        ?: locRepositoryId
    val locVisibility = aRepositoryVisibilityOverride?.takeIf { it.isNotBlank() }
        ?: locRootConfig?.values?.let { AIcFirstValue(it, "sourceRepository.visibility", "repository.visibility", "visibility") }?.takeIf { it.isNotBlank() }
        ?: AIcInferVisibilityFromRepositoryName(locRepositoryId)
    val locGroupId = locRootConfig?.values?.let { AIcFirstValue(it, "groupId") }?.takeIf { it.isNotBlank() }
    return AIcAlgitesRepositoryMetadata(locRepositoryId, locRepositoryName, locVisibility, locGroupId, emptyMap(), emptyMap())
}

fun AIcInferVisibilityFromRepositoryName(aRepositoryName: String): String = when {
    aRepositoryName.startsWith("pub.") -> "pub"
    aRepositoryName.startsWith("priv.") -> "priv"
    else -> ""
}

fun AIcResolveArtifactDirectoryAndSubdirectories(
    aRepositoryRoot: File,
    aArtifactDirectoryPath: String,
    aInitialState: AIcAlgitesResolvedState
): List<AIcAlgitesArtifactDirectoryMetadata> {
    val locStartDirectory = if (aArtifactDirectoryPath == "." || aArtifactDirectoryPath.isBlank()) aRepositoryRoot else aRepositoryRoot.resolve(aArtifactDirectoryPath)
    if (!locStartDirectory.isDirectory) error("Artifact directory path '$aArtifactDirectoryPath' does not exist.")
    val locInheritedState = AIcResolveInheritedStateBeforeDirectory(aRepositoryRoot, aArtifactDirectoryPath, aInitialState)
    val locResult = mutableListOf<AIcAlgitesArtifactDirectoryMetadata>()

    fun locScan(aDirectory: File, aInheritedState: AIcAlgitesResolvedState) {
        val locConfig = AIcFindAlgitesMetadataConfig(aDirectory, aRepositoryRoot)
        var locState = aInheritedState
        var locStop = false
        if (locConfig != null) {
            locState = locState.AIcMerge(AIcResolvedStateFromConfig(locConfig))
            AIcValidateEffectiveState(locState, "${locConfig.structureKind} '${AIcRelativePath(aRepositoryRoot, aDirectory)}'")
            val locContentsModel = AIcContentsModel(locConfig.structureKind, locState.technologyKinds ?: emptyList())
            locResult.add(AIcArtifactDirectoryMetadataFromConfig(aRepositoryRoot, aDirectory, locConfig, locState, locContentsModel))
            locStop = locContentsModel == "self-contained"
        }
        if (locStop) return

        aDirectory.listFiles()?.asSequence()?.filter { it.isDirectory }
            ?.filter { locChild ->
                if (aDirectory.canonicalFile == aRepositoryRoot.canonicalFile) {
                    locChild.name !in AIcAlgitesRootIgnoredDirectoryNames
                } else {
                    true
                }
            }
            ?.sortedBy { it.name }
            ?.forEach { locScan(it, locState) }
    }

    locScan(locStartDirectory, locInheritedState)
    return locResult
}

fun AIcResolveInheritedStateBeforeDirectory(
    aRepositoryRoot: File,
    aArtifactDirectoryPath: String,
    aInitialState: AIcAlgitesResolvedState
): AIcAlgitesResolvedState {
    val locSegments = AIcPathSegments(aArtifactDirectoryPath)
    if (locSegments.isEmpty()) return aInitialState
    var locDirectory = aRepositoryRoot
    var locState = aInitialState
    AIcFindAlgitesMetadataConfig(aRepositoryRoot, aRepositoryRoot)?.let { locState = locState.AIcMerge(AIcResolvedStateFromConfig(it)) }
    locSegments.dropLast(1).forEach { locSegment ->
        locDirectory = locDirectory.resolve(locSegment)
        if (!locDirectory.isDirectory) error("Artifact directory parent path '${locDirectory.path}' does not exist.")
        AIcFindAlgitesMetadataConfig(locDirectory, aRepositoryRoot)?.let { locState = locState.AIcMerge(AIcResolvedStateFromConfig(it)) }
    }
    return locState
}

fun AIcResolveSingleArtifactDirectory(
    aRepositoryRoot: File,
    aArtifactDirectoryPath: String,
    aInitialState: AIcAlgitesResolvedState
): AIcAlgitesArtifactDirectoryMetadata {
    var locDirectory = aRepositoryRoot
    var locState = aInitialState
    AIcFindAlgitesMetadataConfig(aRepositoryRoot, aRepositoryRoot)?.let { locState = locState.AIcMerge(AIcResolvedStateFromConfig(it)) }
    AIcPathSegments(aArtifactDirectoryPath).forEach { locSegment ->
        locDirectory = locDirectory.resolve(locSegment)
        if (!locDirectory.isDirectory) error("Artifact directory path '$aArtifactDirectoryPath' does not exist.")
        AIcFindAlgitesMetadataConfig(locDirectory, aRepositoryRoot)?.let { locState = locState.AIcMerge(AIcResolvedStateFromConfig(it)) }
    }
    val locConfig = AIcFindAlgitesMetadataConfig(locDirectory, aRepositoryRoot)
        ?: error("Directory '$aArtifactDirectoryPath' does not contain an Algites metadata file.")
    AIcValidateEffectiveState(locState, "${locConfig.structureKind} '$aArtifactDirectoryPath'")
    return AIcArtifactDirectoryMetadataFromConfig(
        aRepositoryRoot, locDirectory, locConfig, locState,
        AIcContentsModel(locConfig.structureKind, locState.technologyKinds ?: emptyList())
    )
}

fun AIcArtifactDirectoryMetadataFromConfig(
    aRepositoryRoot: File,
    aDirectory: File,
    aConfig: AIcAlgitesDirectoryConfig,
    aState: AIcAlgitesResolvedState,
    aContentsModel: String
): AIcAlgitesArtifactDirectoryMetadata {
    val locPrefix = AIcStructureKindPrefix(aConfig.structureKind)
    val locPath = AIcRelativePath(aRepositoryRoot, aDirectory)
    val locName = AIcFirstValue(aConfig.values, "$locPrefix.name", "name", "$locPrefix.id", "id")
        ?.takeIf { it.isNotBlank() } ?: if (locPath == ".") aRepositoryRoot.name else aDirectory.name
    val locDescription = AIcFirstValue(aConfig.values, "$locPrefix.description", "description") ?: ""
    return AIcAlgitesArtifactDirectoryMetadata(
        path = locPath,
        structureKind = aConfig.structureKind,
        technologyKinds = aState.technologyKinds ?: emptyList(),
        name = locName,
        description = locDescription,
        groupId = aState.groupId,
        repositories = aState.repositories,
        credentialProfiles = aState.credentialProfiles,
        contentsModel = aContentsModel,
        hasGradleBuild = AIcHasGradleBuild(aDirectory),
        gradleProjectPath = AIcGradleProjectPath(aRepositoryRoot, aDirectory),
        versionContext = aState.versionContext
    )
}

fun AIcFindAlgitesMetadataConfig(aDirectory: File, aRepositoryRoot: File): AIcAlgitesDirectoryConfig? {
    val locCandidates = listOf(
        "algites-source-repository.yml" to "repository", "algites-source-repository.yaml" to "repository",
        "algites-artifact-set.yml" to "artifact-set", "algites-artifact-set.yaml" to "artifact-set",
        "algites-artifact.yml" to "artifact", "algites-artifact.yaml" to "artifact"
    ).map { aDirectory.resolve(it.first) to it.second }.filter { it.first.isFile }
    if (locCandidates.size > 1) {
        error("Directory '${AIcRelativePath(aRepositoryRoot, aDirectory)}' contains more than one Algites metadata file: ${locCandidates.joinToString(", ") { it.first.name }}")
    }
    val locCandidate = locCandidates.singleOrNull() ?: return null
    if (locCandidate.second == "repository" && aDirectory.canonicalFile != aRepositoryRoot.canonicalFile) {
        error("Repository metadata file '${locCandidate.first.name}' is allowed only in repository root. Found in '${AIcRelativePath(aRepositoryRoot, aDirectory)}'.")
    }
    return AIcAlgitesDirectoryConfig(locCandidate.first, locCandidate.second, AIcReadSimpleYamlScalars(locCandidate.first))
}

fun AIcResolvedStateFromConfig(aConfig: AIcAlgitesDirectoryConfig): AIcAlgitesResolvedState {
    val locPrefix = AIcStructureKindPrefix(aConfig.structureKind)
    val locBase = AIcResolvedStateFromRawValues(aConfig.values, locPrefix, aConfig.file)
    val locTechnologyKinds = when (aConfig.structureKind) {
        "artifact-set", "artifact" -> AIcFirstValue(aConfig.values, "$locPrefix.technologyKinds", "technologyKinds")?.let(::AIcParseYamlStringList)
        else -> null
    }?.also { locKinds ->
        val locUnsupported = locKinds.filter { it !in AIcAlgitesSupportedTechnologyKinds }
        if (locUnsupported.isNotEmpty()) error("Unsupported Algites TechnologyKind(s) in '${aConfig.file.path}': ${locUnsupported.joinToString(", ")}.")
    }
    return locBase.copy(technologyKinds = locTechnologyKinds)
}

fun AIcResolvedStateFromRawValues(aValues: Map<String, String>, aPrefix: String, aFile: File): AIcAlgitesResolvedState {
    val locGroupId = AIcFirstValue(aValues, "groupId")?.takeIf { it.isNotBlank() }
    val locVersionContext = AIcAlgitesVersionContext(
        lane = AIcFirstValue(aValues, "$aPrefix.versionContext.lane", "$aPrefix.versionContext.releaseLine", "versionContext.lane", "versionContext.releaseLine")?.takeIf { it.isNotBlank() },
        revision = AIcFirstValue(aValues, "$aPrefix.versionContext.revision", "versionContext.revision")?.takeIf { it.isNotBlank() },
        qualifierKind = AIcFirstValue(aValues, "$aPrefix.versionContext.qualifierKind", "versionContext.qualifierKind")?.takeIf { it.isNotBlank() },
        qualifierLabel = AIcFirstValue(aValues, "$aPrefix.versionContext.qualifierLabel", "versionContext.qualifierLabel")?.takeIf { it.isNotBlank() }
    )
    return AIcAlgitesResolvedState(
        groupId = locGroupId,
        repositories = AIcRepositoryOverridesFromConfig(aValues, aPrefix, aFile),
        credentialProfiles = AIcCredentialProfilesFromConfig(aValues, aFile),
        versionContext = locVersionContext
    )
}

fun AIcRepositoryOverridesFromConfig(
    aValues: Map<String, String>,
    aPrefix: String,
    aFile: File
): Map<String, Map<String, AIcAlgitesRepositoryEndpoint>> {
    val locPrefixes = listOf("$aPrefix.repositories.", "repositories.").filter { !it.startsWith(".repositories") }
    data class AIcBuilder(var id: String? = null, var url: String? = null, var credentialProfile: String? = null, var enabled: Boolean? = null)
    val locBuilders = linkedMapOf<Pair<String, String>, AIcBuilder>()

    aValues.forEach { (locKey, locRawValue) ->
        val locPrefix = locPrefixes.firstOrNull { locKey.startsWith(it) } ?: return@forEach
        val locSegments = locKey.removePrefix(locPrefix).split('.')
        if (locSegments.size < 6) return@forEach
        val locTechnology = locSegments[0]
        val locVisibility = locSegments[1]
        val locStability = locSegments[2]
        val locUsage = locSegments[3]
        val locIndex = locSegments[4]
        val locProperty = locSegments.drop(5).joinToString(".")
        if (locIndex.toIntOrNull() == null) return@forEach
        AIcValidateRepositoryCell(locTechnology, locVisibility, locStability, locUsage, locKey)
        val locCell = "$locTechnology.$locVisibility.$locStability.$locUsage"
        val locBuilder = locBuilders.getOrPut(locCell to locIndex) { AIcBuilder() }
        when (locProperty) {
            "id" -> locBuilder.id = locRawValue.trim()
            "url" -> locBuilder.url = locRawValue.trim().takeIf { it.isNotBlank() }
            "credentialProfile" -> locBuilder.credentialProfile = locRawValue.trim().takeIf { it.isNotBlank() }
            "enabled" -> locBuilder.enabled = AIcParseBoolean(locRawValue, "repository endpoint enabled", aFile)
        }
    }

    val locResult = linkedMapOf<String, MutableMap<String, AIcAlgitesRepositoryEndpoint>>()
    locBuilders.forEach { (locKey, locBuilder) ->
        val locCell = locKey.first
        val locId = locBuilder.id?.takeIf { it.isNotBlank() }
            ?: error("Repository endpoint in '${aFile.path}' cell '$locCell' is missing required id.")
        AIcValidateRepositoryEndpointId(locId, locCell, aFile)
        locResult.getOrPut(locCell) { linkedMapOf() }[locId] = AIcAlgitesRepositoryEndpoint(
            id = locId,
            url = locBuilder.url,
            credentialProfile = locBuilder.credentialProfile,
            enabled = locBuilder.enabled
        )
    }
    return locResult
}

fun AIcCredentialProfilesFromConfig(
    aValues: Map<String, String>,
    aFile: File
): Map<String, AIcAlgitesCredentialProfileDefinition> {
    data class AIcBuilder(var type: String? = null, val configuration: MutableMap<String, String> = linkedMapOf())
    val locBuilders = linkedMapOf<String, AIcBuilder>()
    aValues.forEach { (locKey, locValue) ->
        if (!locKey.startsWith("credentialProfiles.")) return@forEach
        val locSegments = locKey.removePrefix("credentialProfiles.").split('.')
        if (locSegments.size < 2) return@forEach
        val locId = locSegments[0]
        val locBuilder = locBuilders.getOrPut(locId) { AIcBuilder() }
        when {
            locSegments[1] == "type" -> {
                val locType = locValue.trim().lowercase()
                if (locType !in AIcAlgitesCredentialTypes) {
                    error("Unsupported credential profile type '$locType' in '${aFile.path}'. Supported types: ${AIcAlgitesCredentialTypes.joinToString(", ")}.")
                }
                locBuilder.type = locType
            }
            locSegments[1] == "configuration" && locSegments.size >= 3 -> {
                locBuilder.configuration[locSegments.drop(2).joinToString(".")] = locValue
            }
        }
    }
    return locBuilders.mapValues { (locId, locBuilder) ->
        if (!Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$").matches(locId)) {
            error("Credential profile id '$locId' in '${aFile.path}' must use canonical lowercase dash-separated form.")
        }
        AIcAlgitesCredentialProfileDefinition(locId, locBuilder.type, locBuilder.configuration)
    }
}

fun AIcValidateRepositoryCell(aTechnology: String, aVisibility: String, aStability: String, aUsage: String, aKey: String) {
    if (aTechnology !in AIcAlgitesSupportedTechnologyKinds) error("Unsupported TechnologyKind '$aTechnology' in repository matrix key '$aKey'.")
    if (aVisibility !in AIcAlgitesRepositoryVisibilities) error("Unsupported repository visibility '$aVisibility' in repository matrix key '$aKey'.")
    if (aStability !in AIcAlgitesRepositoryStabilities) error("Unsupported repository stability '$aStability' in repository matrix key '$aKey'.")
    if (aUsage !in AIcAlgitesRepositoryUsages) error("Unsupported repository usage '$aUsage' in repository matrix key '$aKey'.")
}

fun AIcValidateRepositoryEndpointId(aId: String, aCell: String, aFile: File) {
    if (!Regex("^algites-[a-z0-9]+(?:-[a-z0-9]+)*$").matches(aId)) {
        error("Repository endpoint id '$aId' in '${aFile.path}' must start with 'algites-' and use lowercase dash-separated form.")
    }
    val locSuffix = "-" + aCell.replace('.', '-')
    if (!aId.endsWith(locSuffix) && aId != "algites-${aCell.replace('.', '-')}") {
        error("Repository endpoint id '$aId' in '${aFile.path}' must encode its matrix dimensions and end with '$locSuffix'.")
    }
}

fun AIcValidateEffectiveState(aState: AIcAlgitesResolvedState, aContext: String) {
    aState.repositories.forEach { (locCell, locEndpoints) ->
        locEndpoints.values.forEach { locEndpoint ->
            if (locEndpoint.AIcEffectiveEnabled() && locEndpoint.url.isNullOrBlank()) {
                error("Enabled repository endpoint '${locEndpoint.id}' in $aContext cell '$locCell' has no URL after inheritance.")
            }
            val locProfileId = locEndpoint.credentialProfile
            if (!locProfileId.isNullOrBlank()) {
                val locProfile = aState.credentialProfiles[locProfileId]
                    ?: error("Repository endpoint '${locEndpoint.id}' in $aContext references undefined credential profile '$locProfileId'.")
                if (locProfile.type.isNullOrBlank()) {
                    error("Credential profile '$locProfileId' referenced by '${locEndpoint.id}' in $aContext has no type after inheritance.")
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
    "repository" -> "sourceRepository"
    "artifact-set" -> "artifactSet"
    "artifact" -> "artifact"
    else -> aStructureKind
}

fun AIcContentsModel(aStructureKind: String, aTechnologyKinds: List<String>): String = when {
    aStructureKind == "repository" -> "container"
    aStructureKind == "artifact" -> "self-contained"
    aStructureKind == "artifact-set" && aTechnologyKinds == listOf("mps") -> "self-contained"
    else -> "container"
}

fun AIcHasGradleBuild(aDirectory: File): Boolean = aDirectory.resolve("build.gradle.kts").isFile || aDirectory.resolve("build.gradle").isFile
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

    aFile.readLines(Charsets.UTF_8).forEach { locOriginalLine ->
        val locLine = AIcStripYamlComment(locOriginalLine)
        if (locLine.isBlank()) return@forEach
        val locIndent = locLine.takeWhile { it == ' ' }.length
        val locTrimmed = locLine.trim()

        while (locStack.isNotEmpty() && locStack.last().first >= locIndent) locStack.removeAt(locStack.lastIndex)

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
                else locValues[locPath] = AIcUnquoteYamlScalar(locRawValue)
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
        else locValues[locPath] = AIcUnquoteYamlScalar(locRawValue)
    }
    return locValues
}

fun AIcParseYamlStringList(aValue: String): List<String> {
    val locTrimmed = aValue.trim()
    val locContent = if (locTrimmed.startsWith("[") && locTrimmed.endsWith("]")) locTrimmed.substring(1, locTrimmed.length - 1) else locTrimmed
    if (locContent.isBlank()) return emptyList()
    return locContent.split(',').map { AIcUnquoteYamlScalar(it.trim()).lowercase() }.filter { it.isNotBlank() }.distinct()
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

fun AIcRepositoryMapForOutput(aRepositories: Map<String, Map<String, AIcAlgitesRepositoryEndpoint>>): Map<String, Any?> =
    aRepositories.toSortedMap().mapValues { (_, locEndpoints) ->
        locEndpoints.values.map { locEndpoint ->
            linkedMapOf<String, Any?>(
                "id" to locEndpoint.id,
                "url" to locEndpoint.url,
                "credentialProfile" to locEndpoint.credentialProfile,
                "enabled" to locEndpoint.AIcEffectiveEnabled()
            )
        }
    }

fun AIcCredentialProfilesMapForOutput(aProfiles: Map<String, AIcAlgitesCredentialProfileDefinition>): Map<String, Any?> =
    aProfiles.toSortedMap().mapValues { (_, locProfile) ->
        linkedMapOf<String, Any?>(
            "type" to locProfile.type,
            "configuration" to locProfile.configuration.toSortedMap()
        )
    }

fun AIcToMap(aResult: AIcAlgitesResolutionResult): Map<String, Any?> = linkedMapOf(
    "repository" to linkedMapOf(
        "id" to aResult.repository.id,
        "name" to aResult.repository.name,
        "visibility" to aResult.repository.visibility,
        "groupId" to aResult.repository.groupId,
        "repositories" to AIcRepositoryMapForOutput(aResult.repository.repositories),
        "credentialProfiles" to AIcCredentialProfilesMapForOutput(aResult.repository.credentialProfiles)
    ),
    "artifactDirectories" to aResult.artifactDirectories.map { locDirectory ->
        linkedMapOf<String, Any?>(
            "path" to locDirectory.path,
            "structureKind" to locDirectory.structureKind,
            "technologyKinds" to locDirectory.technologyKinds,
            "name" to locDirectory.name,
            "description" to locDirectory.description,
            "groupId" to locDirectory.groupId,
            "repositories" to AIcRepositoryMapForOutput(locDirectory.repositories),
            "credentialProfiles" to AIcCredentialProfilesMapForOutput(locDirectory.credentialProfiles),
            "contentsModel" to locDirectory.contentsModel,
            "hasGradleBuild" to locDirectory.hasGradleBuild,
            "gradleProjectPath" to locDirectory.gradleProjectPath,
            "version" to linkedMapOf(
                "lane" to locDirectory.versionContext.lane,
                "revision" to locDirectory.versionContext.revision,
                "qualifierKind" to locDirectory.versionContext.qualifierKind,
                "qualifierLabel" to locDirectory.versionContext.qualifierLabel,
                "resolvedValue" to locDirectory.versionContext.AIcResolvedValue()
            )
        )
    }
)

fun AIcToYaml(aResult: AIcAlgitesResolutionResult): String = buildString {
    appendLine("repository:")
    appendLine("  id: ${AIcYamlScalar(aResult.repository.id)}")
    appendLine("  name: ${AIcYamlScalar(aResult.repository.name)}")
    appendLine("  visibility: ${AIcYamlScalar(aResult.repository.visibility)}")
    appendLine("  groupId: ${AIcYamlScalar(aResult.repository.groupId)}")
    appendLine("  repositories: ${AIcYamlScalar(AIcRepositoryMapForOutput(aResult.repository.repositories).toString())}")
    appendLine("  credentialProfiles: ${AIcYamlScalar(AIcCredentialProfilesMapForOutput(aResult.repository.credentialProfiles).toString())}")
    appendLine("artifactDirectories:")
    aResult.artifactDirectories.forEach { locDirectory ->
        appendLine("  - path: ${AIcYamlScalar(locDirectory.path)}")
        appendLine("    structureKind: ${AIcYamlScalar(locDirectory.structureKind)}")
        appendLine("    technologyKinds: [${locDirectory.technologyKinds.joinToString(", ")}]")
        appendLine("    name: ${AIcYamlScalar(locDirectory.name)}")
        appendLine("    description: ${AIcYamlScalar(locDirectory.description)}")
        appendLine("    groupId: ${AIcYamlScalar(locDirectory.groupId)}")
        appendLine("    repositories: ${AIcYamlScalar(AIcRepositoryMapForOutput(locDirectory.repositories).toString())}")
        appendLine("    credentialProfiles: ${AIcYamlScalar(AIcCredentialProfilesMapForOutput(locDirectory.credentialProfiles).toString())}")
        appendLine("    contentsModel: ${AIcYamlScalar(locDirectory.contentsModel)}")
        appendLine("    hasGradleBuild: ${locDirectory.hasGradleBuild}")
        appendLine("    gradleProjectPath: ${AIcYamlScalar(locDirectory.gradleProjectPath)}")
        appendLine("    version:")
        appendLine("      lane: ${AIcYamlScalar(locDirectory.versionContext.lane)}")
        appendLine("      revision: ${AIcYamlScalar(locDirectory.versionContext.revision)}")
        appendLine("      qualifierKind: ${AIcYamlScalar(locDirectory.versionContext.qualifierKind)}")
        appendLine("      qualifierLabel: ${AIcYamlScalar(locDirectory.versionContext.qualifierLabel)}")
        appendLine("      resolvedValue: ${AIcYamlScalar(locDirectory.versionContext.AIcResolvedValue())}")
    }
}

fun AIcFlattenDottedProperties(aResultMap: Map<String, Any?>): Map<String, String> {
    val locResult = linkedMapOf<String, String>()
    fun locFlatten(aPrefix: String, aValue: Any?) {
        when (aValue) {
            is Map<*, *> -> aValue.forEach { (locKey, locValue) -> locFlatten(if (aPrefix.isBlank()) locKey.toString() else "$aPrefix.${locKey}", locValue) }
            is List<*> -> aValue.forEachIndexed { locIndex, locValue -> locFlatten("$aPrefix.$locIndex", locValue) }
            null -> locResult[aPrefix] = "null"
            else -> locResult[aPrefix] = aValue.toString()
        }
    }
    locFlatten("", aResultMap)
    return locResult
}

fun AIcFormatOutput(aResult: AIcAlgitesResolutionResult, aOutputKind: String): String = when (aOutputKind) {
    "yml", "yaml" -> AIcToYaml(aResult)
    "dotted-properties" -> AIcFlattenDottedProperties(AIcToMap(aResult)).entries.joinToString("\n", postfix = "\n") { "${it.key}=${it.value}" }
    else -> error("Unsupported Algites artifact directory output kind '$aOutputKind'. Supported values are: yml, yaml, dotted-properties.")
}

extra["algitesResolveArtifactDirectoryMetadata"] = ::AIcResolveAlgitesArtifactDirectoryMetadata
extra["algitesResolveArtifactDirectoryMetadataMap"] = fun(
    aRepositoryRoot: File,
    aArtifactDirectoryPath: String?,
    aResolutionKind: String?,
    aRepositoryNameOverride: String?,
    aRepositoryVisibilityOverride: String?
): Map<String, Any?> = AIcToMap(AIcResolveAlgitesArtifactDirectoryMetadata(aRepositoryRoot, aArtifactDirectoryPath, aResolutionKind, aRepositoryNameOverride, aRepositoryVisibilityOverride))
extra["algitesResolveArtifactDirectoryMetadataText"] = fun(
    aRepositoryRoot: File,
    aArtifactDirectoryPath: String?,
    aResolutionKind: String?,
    aRepositoryNameOverride: String?,
    aRepositoryVisibilityOverride: String?,
    aOutputKind: String?
): String = AIcFormatOutput(
    AIcResolveAlgitesArtifactDirectoryMetadata(aRepositoryRoot, aArtifactDirectoryPath, aResolutionKind, aRepositoryNameOverride, aRepositoryVisibilityOverride),
    aOutputKind?.takeIf { it.isNotBlank() } ?: "yaml"
)
extra["algitesFlattenArtifactDirectoryMetadata"] = ::AIcFlattenDottedProperties
