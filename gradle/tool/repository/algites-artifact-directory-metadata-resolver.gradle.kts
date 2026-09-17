/*
 * Algites artifact directory metadata resolver core.
 *
 * This script intentionally contains only Settings/Project compatible logic.
 * It resolves structural metadata, TechnologyKinds, version contexts, and the
 * effective publication repository matrix inherited through the repository
 * directory hierarchy.
 */

import java.io.File

data class AIcAlgitesVersionContext(
    val lane: String? = null,
    val revision: String? = null,
    val qualifierKind: String? = null,
    val qualifierLabel: String? = null
) {
    fun AIcMerge(aOther: AIcAlgitesVersionContext): AIcAlgitesVersionContext {
        return AIcAlgitesVersionContext(
            lane = aOther.lane ?: lane,
            revision = aOther.revision ?: revision,
            qualifierKind = aOther.qualifierKind ?: qualifierKind,
            qualifierLabel = aOther.qualifierLabel ?: qualifierLabel
        )
    }

    fun AIcResolvedValue(): String? {
        val locLane = lane?.takeIf { it.isNotBlank() } ?: return null
        val locRevision = revision?.takeIf { it.isNotBlank() }
        val locEffectiveQualifier = qualifierLabel
            ?.takeIf { it.isNotBlank() }
            ?: qualifierKind
                ?.takeIf { it.isNotBlank() }
                ?.takeUnless { it.equals("RELEASE", ignoreCase = true) || it.equals("FINAL", ignoreCase = true) }

        val locBaseVersion = if (locRevision == null) {
            locLane
        } else {
            "$locLane.$locRevision"
        }

        return if (locEffectiveQualifier == null) {
            locBaseVersion
        } else {
            "$locBaseVersion-${locEffectiveQualifier.uppercase()}"
        }
    }
}

data class AIcAlgitesResolvedState(
    val technologyKinds: List<String>? = null,
    val groupId: String? = null,
    val repositories: Map<String, String> = emptyMap(),
    val versionContext: AIcAlgitesVersionContext = AIcAlgitesVersionContext()
) {
    fun AIcMerge(aOther: AIcAlgitesResolvedState): AIcAlgitesResolvedState {
        return AIcAlgitesResolvedState(
            technologyKinds = aOther.technologyKinds ?: technologyKinds,
            groupId = aOther.groupId ?: groupId,
            repositories = repositories + aOther.repositories,
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
    val repositories: Map<String, String>,
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
    val repositories: Map<String, String>
)

data class AIcAlgitesResolutionResult(
    val repository: AIcAlgitesRepositoryMetadata,
    val artifactDirectories: List<AIcAlgitesArtifactDirectoryMetadata>
)

val AIcAlgitesSupportedTechnologyKinds = linkedSetOf("java", "python", "mps")
val AIcAlgitesRepositoryStabilities = linkedSetOf("release", "snapshot")
val AIcAlgitesRepositoryUsages = linkedSetOf("download", "upload")

val AIcAlgitesIgnoredDirectoryNames = setOf(
    ".git",
    ".gradle",
    ".idea",
    ".mps",
    "build",
    "run",
    "target",
    "out",
    "output",
    "docs-site",
    "documentation-branch",
    "gh-pages",
    "source_gen",
    "source_gen.caches",
    "classes_gen"
)

fun AIcAlgitesBuiltInRepositoryDefaults(aVisibility: String): Map<String, String> {
    return when (aVisibility.lowercase()) {
        "pub" -> linkedMapOf(
            "java.release.download" to "https://repo1.maven.org/maven2",
            "java.snapshot.download" to "https://dl.cloudsmith.io/public/algites/maven-snapshots-pub/maven/"
        )
        else -> emptyMap()
    }
}

fun AIcResolveAlgitesArtifactDirectoryMetadata(
    aRepositoryRoot: File,
    aArtifactDirectoryPath: String?,
    aResolutionKind: String?,
    aRepositoryNameOverride: String?,
    aRepositoryVisibilityOverride: String?
): AIcAlgitesResolutionResult {
    val locRepositoryBase = AIcResolveRepositoryMetadataBase(
        aRepositoryRoot = aRepositoryRoot,
        aRepositoryNameOverride = aRepositoryNameOverride,
        aRepositoryVisibilityOverride = aRepositoryVisibilityOverride
    )

    val locInitialState = AIcAlgitesResolvedState(
        repositories = AIcAlgitesBuiltInRepositoryDefaults(locRepositoryBase.visibility)
    )

    val locRootConfig = AIcFindAlgitesMetadataConfig(aRepositoryRoot, aRepositoryRoot)
        ?.takeIf { it.structureKind == "repository" }
    val locRootState = if (locRootConfig == null) {
        locInitialState
    } else {
        locInitialState.AIcMerge(AIcResolvedStateFromConfig(locRootConfig))
    }

    val locRepository = AIcAlgitesRepositoryMetadata(
        id = locRepositoryBase.id,
        name = locRepositoryBase.name,
        visibility = locRepositoryBase.visibility,
        groupId = locRepositoryBase.groupId,
        repositories = locRootState.repositories
    )

    val locNormalizedPath = aArtifactDirectoryPath
        ?.trim()
        ?.replace('\\', '/')
        ?.trim('/')
        ?.takeIf { it.isNotBlank() && it != "." }

    val locResolutionKind = aResolutionKind
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: "current-with-subdirs"

    if (locResolutionKind !in setOf("current-only", "current-with-subdirs")) {
        error(
            "Unsupported Algites artifact directory resolution kind '$locResolutionKind'. " +
                "Supported values are: current-only, current-with-subdirs."
        )
    }

    val locArtifactDirectories = if (locResolutionKind == "current-only") {
        listOf(
            AIcResolveSingleArtifactDirectory(
                aRepositoryRoot = aRepositoryRoot,
                aArtifactDirectoryPath = locNormalizedPath ?: ".",
                aInitialState = locInitialState
            )
        )
    } else if (locNormalizedPath == null) {
        AIcResolveArtifactDirectoryAndSubdirectories(
            aRepositoryRoot = aRepositoryRoot,
            aArtifactDirectoryPath = ".",
            aInitialState = locInitialState
        )
    } else {
        AIcResolveArtifactDirectoryAndSubdirectories(
            aRepositoryRoot = aRepositoryRoot,
            aArtifactDirectoryPath = locNormalizedPath,
            aInitialState = locInitialState
        )
    }

    return AIcAlgitesResolutionResult(
        repository = locRepository,
        artifactDirectories = locArtifactDirectories
    )
}

fun AIcResolveRepositoryMetadataBase(
    aRepositoryRoot: File,
    aRepositoryNameOverride: String?,
    aRepositoryVisibilityOverride: String?
): AIcAlgitesRepositoryMetadata {
    val locRootConfig = AIcFindAlgitesMetadataConfig(aRepositoryRoot, aRepositoryRoot)
        ?.takeIf { it.structureKind == "repository" }

    val locRepositoryId = locRootConfig?.values?.let {
        AIcFirstValue(it, "sourceRepository.id", "repository.id", "id")
    }?.takeIf { it.isNotBlank() }
        ?: aRepositoryRoot.name

    val locRepositoryName = aRepositoryNameOverride
        ?.takeIf { it.isNotBlank() }
        ?: locRootConfig?.values?.let {
            AIcFirstValue(it, "sourceRepository.name", "repository.name", "name")
        }?.takeIf { it.isNotBlank() }
        ?: locRepositoryId

    val locVisibility = aRepositoryVisibilityOverride
        ?.takeIf { it.isNotBlank() }
        ?: locRootConfig?.values?.let {
            AIcFirstValue(it, "sourceRepository.visibility", "repository.visibility", "visibility")
        }?.takeIf { it.isNotBlank() }
        ?: AIcInferVisibilityFromRepositoryName(locRepositoryId)

    val locGroupId = locRootConfig?.values?.let {
        AIcFirstValue(it, "sourceRepository.groupId", "repository.groupId", "groupId")
    }?.takeIf { it.isNotBlank() }

    return AIcAlgitesRepositoryMetadata(
        id = locRepositoryId,
        name = locRepositoryName,
        visibility = locVisibility,
        groupId = locGroupId,
        repositories = emptyMap()
    )
}

fun AIcInferVisibilityFromRepositoryName(aRepositoryName: String): String {
    return when {
        aRepositoryName.startsWith("pub.") -> "pub"
        aRepositoryName.startsWith("priv.") -> "priv"
        else -> ""
    }
}

fun AIcResolveArtifactDirectoryAndSubdirectories(
    aRepositoryRoot: File,
    aArtifactDirectoryPath: String,
    aInitialState: AIcAlgitesResolvedState
): List<AIcAlgitesArtifactDirectoryMetadata> {
    val locStartDirectory = if (aArtifactDirectoryPath == "." || aArtifactDirectoryPath.isBlank()) {
        aRepositoryRoot
    } else {
        aRepositoryRoot.resolve(aArtifactDirectoryPath)
    }

    if (!locStartDirectory.isDirectory) {
        error("Artifact directory path '$aArtifactDirectoryPath' does not exist.")
    }

    val locInheritedState = AIcResolveInheritedStateBeforeDirectory(
        aRepositoryRoot = aRepositoryRoot,
        aArtifactDirectoryPath = aArtifactDirectoryPath,
        aInitialState = aInitialState
    )

    val locArtifactDirectories = mutableListOf<AIcAlgitesArtifactDirectoryMetadata>()

    fun AIcScanDirectory(aDirectory: File, aInheritedState: AIcAlgitesResolvedState) {
        val locConfig = AIcFindAlgitesMetadataConfig(aDirectory, aRepositoryRoot)

        var locCurrentState = aInheritedState
        var locStopScanningChildren = false

        if (locConfig != null) {
            locCurrentState = locCurrentState.AIcMerge(AIcResolvedStateFromConfig(locConfig))

            val locContentsModel = AIcContentsModel(
                aStructureKind = locConfig.structureKind,
                aTechnologyKinds = locCurrentState.technologyKinds ?: emptyList()
            )

            locArtifactDirectories.add(
                AIcArtifactDirectoryMetadataFromConfig(
                    aRepositoryRoot = aRepositoryRoot,
                    aDirectory = aDirectory,
                    aConfig = locConfig,
                    aState = locCurrentState,
                    aContentsModel = locContentsModel
                )
            )

            locStopScanningChildren = locContentsModel == "self-contained"
        }

        if (locStopScanningChildren) {
            return
        }

        aDirectory.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory }
            ?.filter { it.name !in AIcAlgitesIgnoredDirectoryNames }
            ?.sortedBy { it.name }
            ?.forEach { locChildDirectory ->
                AIcScanDirectory(locChildDirectory, locCurrentState)
            }
    }

    AIcScanDirectory(locStartDirectory, locInheritedState)

    return locArtifactDirectories
}

fun AIcResolveInheritedStateBeforeDirectory(
    aRepositoryRoot: File,
    aArtifactDirectoryPath: String,
    aInitialState: AIcAlgitesResolvedState
): AIcAlgitesResolvedState {
    val locPathSegments = AIcPathSegments(aArtifactDirectoryPath)
    if (locPathSegments.isEmpty()) {
        return aInitialState
    }

    var locCurrentDirectory = aRepositoryRoot
    var locCurrentState = aInitialState

    AIcFindAlgitesMetadataConfig(aRepositoryRoot, aRepositoryRoot)?.let { locRootConfig ->
        locCurrentState = locCurrentState.AIcMerge(AIcResolvedStateFromConfig(locRootConfig))
    }

    locPathSegments.dropLast(1).forEach { locPathSegment ->
        locCurrentDirectory = locCurrentDirectory.resolve(locPathSegment)

        if (!locCurrentDirectory.isDirectory) {
            error("Artifact directory parent path '${locCurrentDirectory.path}' does not exist.")
        }

        AIcFindAlgitesMetadataConfig(locCurrentDirectory, aRepositoryRoot)?.let { locConfig ->
            locCurrentState = locCurrentState.AIcMerge(AIcResolvedStateFromConfig(locConfig))
        }
    }

    return locCurrentState
}

fun AIcResolveSingleArtifactDirectory(
    aRepositoryRoot: File,
    aArtifactDirectoryPath: String,
    aInitialState: AIcAlgitesResolvedState
): AIcAlgitesArtifactDirectoryMetadata {
    var locCurrentDirectory = aRepositoryRoot
    var locCurrentState = aInitialState

    AIcFindAlgitesMetadataConfig(aRepositoryRoot, aRepositoryRoot)?.let { locRootConfig ->
        locCurrentState = locCurrentState.AIcMerge(AIcResolvedStateFromConfig(locRootConfig))
    }

    AIcPathSegments(aArtifactDirectoryPath).forEach { locPathSegment ->
        locCurrentDirectory = locCurrentDirectory.resolve(locPathSegment)

        if (!locCurrentDirectory.isDirectory) {
            error("Artifact directory path '$aArtifactDirectoryPath' does not exist.")
        }

        val locConfig = AIcFindAlgitesMetadataConfig(locCurrentDirectory, aRepositoryRoot)
        if (locConfig != null) {
            locCurrentState = locCurrentState.AIcMerge(AIcResolvedStateFromConfig(locConfig))
        }
    }

    val locDirectoryConfig = AIcFindAlgitesMetadataConfig(locCurrentDirectory, aRepositoryRoot)
        ?: error("Directory '$aArtifactDirectoryPath' does not contain an Algites metadata file.")

    return AIcArtifactDirectoryMetadataFromConfig(
        aRepositoryRoot = aRepositoryRoot,
        aDirectory = locCurrentDirectory,
        aConfig = locDirectoryConfig,
        aState = locCurrentState,
        aContentsModel = AIcContentsModel(locDirectoryConfig.structureKind, locCurrentState.technologyKinds ?: emptyList())
    )
}

fun AIcArtifactDirectoryMetadataFromConfig(
    aRepositoryRoot: File,
    aDirectory: File,
    aConfig: AIcAlgitesDirectoryConfig,
    aState: AIcAlgitesResolvedState,
    aContentsModel: String
): AIcAlgitesArtifactDirectoryMetadata {
    val locStructureKindPrefix = AIcStructureKindPrefix(aConfig.structureKind)
    val locPath = AIcRelativePath(aRepositoryRoot, aDirectory)
    val locName = AIcFirstValue(
        aConfig.values,
        "$locStructureKindPrefix.name",
        "name",
        "$locStructureKindPrefix.id",
        "id"
    )?.takeIf { it.isNotBlank() } ?: if (locPath == ".") aRepositoryRoot.name else aDirectory.name

    val locDescription = AIcFirstValue(
        aConfig.values,
        "$locStructureKindPrefix.description",
        "description"
    ) ?: ""

    return AIcAlgitesArtifactDirectoryMetadata(
        path = locPath,
        structureKind = aConfig.structureKind,
        technologyKinds = aState.technologyKinds ?: emptyList(),
        name = locName,
        description = locDescription,
        groupId = aState.groupId,
        repositories = aState.repositories,
        contentsModel = aContentsModel,
        hasGradleBuild = AIcHasGradleBuild(aDirectory),
        gradleProjectPath = AIcGradleProjectPath(aRepositoryRoot, aDirectory),
        versionContext = aState.versionContext
    )
}

fun AIcFindAlgitesMetadataConfig(
    aDirectory: File,
    aRepositoryRoot: File
): AIcAlgitesDirectoryConfig? {
    val locCandidates = listOf(
        "algites-source-repository.yml" to "repository",
        "algites-source-repository.yaml" to "repository",
        "algites-artifact-set.yml" to "artifact-set",
        "algites-artifact-set.yaml" to "artifact-set",
        "algites-artifact.yml" to "artifact",
        "algites-artifact.yaml" to "artifact"
    ).map { locCandidate ->
        aDirectory.resolve(locCandidate.first) to locCandidate.second
    }.filter { locCandidate ->
        locCandidate.first.isFile
    }

    if (locCandidates.size > 1) {
        error(
            "Directory '${AIcRelativePath(aRepositoryRoot, aDirectory)}' contains more than one Algites metadata file: " +
                locCandidates.joinToString(", ") { it.first.name }
        )
    }

    val locCandidate = locCandidates.singleOrNull() ?: return null

    if (locCandidate.second == "repository" && aDirectory.canonicalFile != aRepositoryRoot.canonicalFile) {
        error(
            "Repository metadata file '${locCandidate.first.name}' is allowed only in repository root. " +
                "Found in '${AIcRelativePath(aRepositoryRoot, aDirectory)}'."
        )
    }

    return AIcAlgitesDirectoryConfig(
        file = locCandidate.first,
        structureKind = locCandidate.second,
        values = AIcReadSimpleYamlScalars(locCandidate.first)
    )
}

fun AIcResolvedStateFromConfig(aConfig: AIcAlgitesDirectoryConfig): AIcAlgitesResolvedState {
    val locValues = aConfig.values
    val locStructureKindPrefix = AIcStructureKindPrefix(aConfig.structureKind)

    val locTechnologyKinds = when (aConfig.structureKind) {
        "artifact-set", "artifact" -> AIcFirstValue(
            locValues,
            "$locStructureKindPrefix.technologyKinds",
            "technologyKinds"
        )?.let { AIcParseYamlStringList(it) }
        else -> null
    }?.also { locResolvedTechnologyKinds ->
        val locUnsupportedTechnologyKinds = locResolvedTechnologyKinds.filter { it !in AIcAlgitesSupportedTechnologyKinds }
        if (locUnsupportedTechnologyKinds.isNotEmpty()) {
            error(
                "Unsupported Algites TechnologyKind(s) in '${aConfig.file.path}': " +
                    locUnsupportedTechnologyKinds.joinToString(", ") +
                    ". Supported technology kinds are: " +
                    AIcAlgitesSupportedTechnologyKinds.joinToString(", ") + "."
            )
        }
    }

    val locGroupId = AIcFirstValue(
        locValues,
        "$locStructureKindPrefix.groupId",
        "sourceRepository.groupId",
        "groupId"
    )?.takeIf { it.isNotBlank() }

    val locRepositoryOverrides = AIcRepositoryOverridesFromConfig(
        aValues = locValues,
        aPrefix = locStructureKindPrefix
    )

    val locVersionContext = AIcAlgitesVersionContext(
        lane = AIcFirstValue(
            locValues,
            "$locStructureKindPrefix.versionContext.lane",
            "$locStructureKindPrefix.versionContext.releaseLine",
            "versionContext.lane",
            "versionContext.releaseLine"
        )?.takeIf { it.isNotBlank() },
        revision = AIcFirstValue(
            locValues,
            "$locStructureKindPrefix.versionContext.revision",
            "versionContext.revision"
        )?.takeIf { it.isNotBlank() },
        qualifierKind = AIcFirstValue(
            locValues,
            "$locStructureKindPrefix.versionContext.qualifierKind",
            "versionContext.qualifierKind"
        )?.takeIf { it.isNotBlank() },
        qualifierLabel = AIcFirstValue(
            locValues,
            "$locStructureKindPrefix.versionContext.qualifierLabel",
            "versionContext.qualifierLabel"
        )?.takeIf { it.isNotBlank() }
    )

    return AIcAlgitesResolvedState(
        technologyKinds = locTechnologyKinds,
        groupId = locGroupId,
        repositories = locRepositoryOverrides,
        versionContext = locVersionContext
    )
}

fun AIcRepositoryOverridesFromConfig(
    aValues: Map<String, String>,
    aPrefix: String
): Map<String, String> {
    val locPrefixes = listOf("$aPrefix.repositories.", "repositories.")
    val locResult = linkedMapOf<String, String>()

    aValues.forEach { locEntry ->
        val locMatchingPrefix = locPrefixes.firstOrNull { locEntry.key.startsWith(it) } ?: return@forEach
        val locCell = locEntry.key.removePrefix(locMatchingPrefix)
        val locSegments = locCell.split('.')

        if (locSegments.size != 3) {
            error(
                "Invalid repository matrix key '${locEntry.key}' in Algites metadata. " +
                    "Expected <technology-kind>.<release|snapshot>.<download|upload>."
            )
        }

        val locTechnologyKind = locSegments[0]
        val locStability = locSegments[1]
        val locUsage = locSegments[2]

        if (locTechnologyKind !in AIcAlgitesSupportedTechnologyKinds) {
            error("Unsupported TechnologyKind '$locTechnologyKind' in repository matrix key '${locEntry.key}'.")
        }
        if (locStability !in AIcAlgitesRepositoryStabilities) {
            error("Unsupported repository stability '$locStability' in repository matrix key '${locEntry.key}'.")
        }
        if (locUsage !in AIcAlgitesRepositoryUsages) {
            error("Unsupported repository usage '$locUsage' in repository matrix key '${locEntry.key}'.")
        }

        val locValue = locEntry.value.trim()
        if (locValue.isNotBlank()) {
            locResult["$locTechnologyKind.$locStability.$locUsage"] = locValue
        }
    }

    return locResult
}

fun AIcStructureKindPrefix(aStructureKind: String): String {
    return when (aStructureKind) {
        "repository" -> "sourceRepository"
        "artifact-set" -> "artifactSet"
        "artifact" -> "artifact"
        else -> aStructureKind
    }
}

fun AIcContentsModel(aStructureKind: String, aTechnologyKinds: List<String>): String {
    return when {
        aStructureKind == "repository" -> "container"
        aStructureKind == "artifact" -> "self-contained"
        aStructureKind == "artifact-set" && aTechnologyKinds == listOf("mps") -> "self-contained"
        aStructureKind == "artifact-set" -> "container"
        else -> "container"
    }
}

fun AIcHasGradleBuild(aDirectory: File): Boolean {
    return aDirectory.resolve("build.gradle.kts").isFile || aDirectory.resolve("build.gradle").isFile
}

fun AIcGradleProjectPath(aRepositoryRoot: File, aDirectory: File): String {
    val locRelativePath = AIcRelativePath(aRepositoryRoot, aDirectory)
    return if (locRelativePath == ".") {
        ":"
    } else {
        ":" + locRelativePath.split('/').filter { it.isNotBlank() }.joinToString(":")
    }
}

fun AIcPathSegments(aPath: String): List<String> {
    val locNormalizedPath = aPath.trim().replace('\\', '/').trim('/')
    return if (locNormalizedPath.isBlank() || locNormalizedPath == ".") {
        emptyList()
    } else {
        locNormalizedPath.split('/').filter { it.isNotBlank() && it != "." }
    }
}

fun AIcRelativePath(aRepositoryRoot: File, aDirectory: File): String {
    val locRootPath = aRepositoryRoot.canonicalFile.toPath()
    val locDirectoryPath = aDirectory.canonicalFile.toPath()
    val locRelativePath = locRootPath.relativize(locDirectoryPath).toString().replace(File.separatorChar, '/')
    return locRelativePath.ifBlank { "." }
}

fun AIcFirstValue(
    aValues: Map<String, String>,
    vararg aKeys: String
): String? {
    return aKeys.firstNotNullOfOrNull { locKey -> aValues[locKey] }
}

fun AIcReadSimpleYamlScalars(aFile: File): Map<String, String> {
    val locValues = linkedMapOf<String, String>()
    val locStack = mutableListOf<Pair<Int, String>>()

    aFile.readLines(Charsets.UTF_8).forEach { locOriginalLine ->
        val locLineWithoutComment = AIcStripYamlComment(locOriginalLine)
        if (locLineWithoutComment.isBlank()) {
            return@forEach
        }

        val locIndent = locLineWithoutComment.takeWhile { it == ' ' }.length
        val locTrimmedLine = locLineWithoutComment.trim()

        if (locTrimmedLine.startsWith("- ")) {
            val locPath = locStack.joinToString(".") { it.second }
            if (locPath.isNotBlank()) {
                val locItem = AIcUnquoteYamlScalar(locTrimmedLine.removePrefix("- ").trim())
                val locExisting = locValues[locPath]
                val locItems = if (locExisting == null) {
                    mutableListOf()
                } else {
                    AIcParseYamlStringList(locExisting).toMutableList()
                }
                locItems.add(locItem)
                locValues[locPath] = "[" + locItems.joinToString(", ") + "]"
            }
            return@forEach
        }

        val locSeparatorIndex = locTrimmedLine.indexOf(':')
        if (locSeparatorIndex <= 0) {
            return@forEach
        }

        val locKey = locTrimmedLine.substring(0, locSeparatorIndex).trim()
        val locRawValue = locTrimmedLine.substring(locSeparatorIndex + 1).trim()

        while (locStack.isNotEmpty() && locStack.last().first >= locIndent) {
            locStack.removeAt(locStack.lastIndex)
        }

        val locPath = (locStack.map { it.second } + locKey).joinToString(".")

        if (locRawValue.isEmpty()) {
            locStack.add(locIndent to locKey)
        } else {
            locValues[locPath] = AIcUnquoteYamlScalar(locRawValue)
        }
    }

    return locValues
}

fun AIcParseYamlStringList(aValue: String): List<String> {
    val locTrimmed = aValue.trim()
    val locContent = if (locTrimmed.startsWith("[") && locTrimmed.endsWith("]")) {
        locTrimmed.substring(1, locTrimmed.length - 1)
    } else {
        locTrimmed
    }

    if (locContent.isBlank()) {
        return emptyList()
    }

    return locContent
        .split(',')
        .map { AIcUnquoteYamlScalar(it.trim()).lowercase() }
        .filter { it.isNotBlank() }
        .distinct()
}

fun AIcStripYamlComment(aLine: String): String {
    var locSingleQuoted = false
    var locDoubleQuoted = false

    aLine.forEachIndexed { locIndex, locCharacter ->
        when (locCharacter) {
            '\'' -> if (!locDoubleQuoted) locSingleQuoted = !locSingleQuoted
            '"' -> if (!locSingleQuoted) locDoubleQuoted = !locDoubleQuoted
            '#' -> {
                if (!locSingleQuoted && !locDoubleQuoted) {
                    val locPreviousCharacter = aLine.getOrNull(locIndex - 1)
                    if (locIndex == 0 || locPreviousCharacter?.isWhitespace() == true) {
                        return aLine.substring(0, locIndex)
                    }
                }
            }
        }
    }

    return aLine
}

fun AIcUnquoteYamlScalar(aValue: String): String {
    val locValue = aValue.trim()
    return if (
        (locValue.startsWith("\"") && locValue.endsWith("\"")) ||
        (locValue.startsWith("'") && locValue.endsWith("'"))
    ) {
        locValue.substring(1, locValue.length - 1)
    } else {
        locValue
    }
}

fun AIcYamlScalar(aValue: String?): String {
    if (aValue == null) {
        return "null"
    }

    val locNeedsQuoting = aValue.isBlank() ||
        aValue.any { it == ':' || it == '#' || it == '"' || it == '\'' || it.isWhitespace() } ||
        aValue == "null" ||
        aValue == "true" ||
        aValue == "false" ||
        aValue.toDoubleOrNull() != null

    return if (locNeedsQuoting) {
        "\"" + aValue.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    } else {
        aValue
    }
}

fun AIcPropertiesScalar(aValue: String?): String {
    return aValue ?: "null"
}

fun StringBuilder.AIcAppendRepositoriesYaml(aIndent: String, aRepositories: Map<String, String>) {
    appendLine("${aIndent}repositories:")
    if (aRepositories.isEmpty()) {
        appendLine("${aIndent}  {}")
        return
    }

    aRepositories.keys
        .map { it.substringBefore('.') }
        .distinct()
        .sorted()
        .forEach { locKind ->
            appendLine("${aIndent}  $locKind:")
            AIcAlgitesRepositoryStabilities.forEach { locStability ->
                val locCells = AIcAlgitesRepositoryUsages.mapNotNull { locUsage ->
                    val locKey = "$locKind.$locStability.$locUsage"
                    aRepositories[locKey]?.let { locValue -> locUsage to locValue }
                }
                if (locCells.isNotEmpty()) {
                    appendLine("${aIndent}    $locStability:")
                    locCells.forEach { locCell ->
                        appendLine("${aIndent}      ${locCell.first}: ${AIcYamlScalar(locCell.second)}")
                    }
                }
            }
        }
}

fun AIcToYaml(aResult: AIcAlgitesResolutionResult): String {
    return buildString {
        appendLine("repository:")
        appendLine("  id: ${AIcYamlScalar(aResult.repository.id)}")
        appendLine("  name: ${AIcYamlScalar(aResult.repository.name)}")
        appendLine("  visibility: ${AIcYamlScalar(aResult.repository.visibility)}")
        appendLine("  groupId: ${AIcYamlScalar(aResult.repository.groupId)}")
        AIcAppendRepositoriesYaml("  ", aResult.repository.repositories)
        appendLine()
        appendLine("artifactDirectories:")
        appendLine("  count: ${aResult.artifactDirectories.size}")

        aResult.artifactDirectories.forEachIndexed { locIndex, locDirectory ->
            appendLine("  $locIndex:")
            appendLine("    path: ${AIcYamlScalar(locDirectory.path)}")
            appendLine("    structureKind: ${AIcYamlScalar(locDirectory.structureKind)}")
            appendLine("    technologyKinds: [${locDirectory.technologyKinds.joinToString(", ") { AIcYamlScalar(it) }}]")
            appendLine("    name: ${AIcYamlScalar(locDirectory.name)}")
            appendLine("    description: ${AIcYamlScalar(locDirectory.description)}")
            appendLine("    groupId: ${AIcYamlScalar(locDirectory.groupId)}")
            AIcAppendRepositoriesYaml("    ", locDirectory.repositories)
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
}

fun AIcToDottedProperties(aResult: AIcAlgitesResolutionResult): String {
    return buildString {
        appendLine("repository.id=${AIcPropertiesScalar(aResult.repository.id)}")
        appendLine("repository.name=${AIcPropertiesScalar(aResult.repository.name)}")
        appendLine("repository.visibility=${AIcPropertiesScalar(aResult.repository.visibility)}")
        appendLine("repository.groupId=${AIcPropertiesScalar(aResult.repository.groupId)}")
        aResult.repository.repositories.toSortedMap().forEach { locEntry ->
            appendLine("repository.repositories.${locEntry.key}=${locEntry.value}")
        }
        appendLine("artifactDirectories.count=${aResult.artifactDirectories.size}")

        aResult.artifactDirectories.forEachIndexed { locIndex, locDirectory ->
            appendLine("artifactDirectories.$locIndex.path=${AIcPropertiesScalar(locDirectory.path)}")
            appendLine("artifactDirectories.$locIndex.structureKind=${AIcPropertiesScalar(locDirectory.structureKind)}")
            appendLine("artifactDirectories.$locIndex.technologyKinds=${locDirectory.technologyKinds.joinToString(",")}")
            appendLine("artifactDirectories.$locIndex.name=${AIcPropertiesScalar(locDirectory.name)}")
            appendLine("artifactDirectories.$locIndex.description=${AIcPropertiesScalar(locDirectory.description)}")
            appendLine("artifactDirectories.$locIndex.groupId=${AIcPropertiesScalar(locDirectory.groupId)}")
            locDirectory.repositories.toSortedMap().forEach { locEntry ->
                appendLine("artifactDirectories.$locIndex.repositories.${locEntry.key}=${locEntry.value}")
            }
            appendLine("artifactDirectories.$locIndex.contentsModel=${AIcPropertiesScalar(locDirectory.contentsModel)}")
            appendLine("artifactDirectories.$locIndex.hasGradleBuild=${locDirectory.hasGradleBuild}")
            appendLine("artifactDirectories.$locIndex.gradleProjectPath=${AIcPropertiesScalar(locDirectory.gradleProjectPath)}")
            appendLine("artifactDirectories.$locIndex.version.lane=${AIcPropertiesScalar(locDirectory.versionContext.lane)}")
            appendLine("artifactDirectories.$locIndex.version.revision=${AIcPropertiesScalar(locDirectory.versionContext.revision)}")
            appendLine("artifactDirectories.$locIndex.version.qualifierKind=${AIcPropertiesScalar(locDirectory.versionContext.qualifierKind)}")
            appendLine("artifactDirectories.$locIndex.version.qualifierLabel=${AIcPropertiesScalar(locDirectory.versionContext.qualifierLabel)}")
            appendLine("artifactDirectories.$locIndex.version.resolvedValue=${AIcPropertiesScalar(locDirectory.versionContext.AIcResolvedValue())}")
        }
    }
}

fun AIcFormatOutput(
    aResult: AIcAlgitesResolutionResult,
    aOutputKind: String
): String {
    return when (aOutputKind) {
        "yml", "yaml" -> AIcToYaml(aResult)
        "dotted-properties" -> AIcToDottedProperties(aResult)
        else -> error(
            "Unsupported Algites artifact directory output kind '$aOutputKind'. " +
                "Supported values are: yml, yaml, dotted-properties."
        )
    }
}

fun AIcToMap(aResult: AIcAlgitesResolutionResult): Map<String, Any?> {
    return linkedMapOf(
        "repository" to linkedMapOf(
            "id" to aResult.repository.id,
            "name" to aResult.repository.name,
            "visibility" to aResult.repository.visibility,
            "groupId" to aResult.repository.groupId,
            "repositories" to aResult.repository.repositories
        ),
        "artifactDirectories" to aResult.artifactDirectories.map { locDirectory ->
            linkedMapOf<String, Any?>(
                "path" to locDirectory.path,
                "structureKind" to locDirectory.structureKind,
                "technologyKinds" to locDirectory.technologyKinds,
                "name" to locDirectory.name,
                "description" to locDirectory.description,
                "groupId" to locDirectory.groupId,
                "repositories" to locDirectory.repositories,
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
}

@Suppress("UNCHECKED_CAST")
fun AIcFlattenDottedProperties(aResultMap: Map<String, Any?>): Map<String, String> {
    val locProperties = linkedMapOf<String, String>()
    val locRepository = aResultMap["repository"] as Map<String, Any?>
    val locArtifactDirectories = aResultMap["artifactDirectories"] as List<Map<String, Any?>>

    locProperties["repository.id"] = locRepository["id"]?.toString() ?: "null"
    locProperties["repository.name"] = locRepository["name"]?.toString() ?: "null"
    locProperties["repository.visibility"] = locRepository["visibility"]?.toString() ?: "null"
    locProperties["repository.groupId"] = locRepository["groupId"]?.toString() ?: "null"
    (locRepository["repositories"] as? Map<String, String>)?.forEach { locEntry ->
        locProperties["repository.repositories.${locEntry.key}"] = locEntry.value
    }
    locProperties["artifactDirectories.count"] = locArtifactDirectories.size.toString()

    locArtifactDirectories.forEachIndexed { locIndex, locDirectory ->
        val locVersion = locDirectory["version"] as Map<String, Any?>
        val locTechnologyKinds = locDirectory["technologyKinds"] as? List<*> ?: emptyList<Any?>()
        locProperties["artifactDirectories.$locIndex.path"] = locDirectory["path"]?.toString() ?: "null"
        locProperties["artifactDirectories.$locIndex.structureKind"] = locDirectory["structureKind"]?.toString() ?: "null"
        locProperties["artifactDirectories.$locIndex.technologyKinds"] = locTechnologyKinds.joinToString(",")
        locProperties["artifactDirectories.$locIndex.name"] = locDirectory["name"]?.toString() ?: "null"
        locProperties["artifactDirectories.$locIndex.description"] = locDirectory["description"]?.toString() ?: "null"
        locProperties["artifactDirectories.$locIndex.groupId"] = locDirectory["groupId"]?.toString() ?: "null"
        (locDirectory["repositories"] as? Map<String, String>)?.forEach { locEntry ->
            locProperties["artifactDirectories.$locIndex.repositories.${locEntry.key}"] = locEntry.value
        }
        locProperties["artifactDirectories.$locIndex.contentsModel"] = locDirectory["contentsModel"]?.toString() ?: "null"
        locProperties["artifactDirectories.$locIndex.hasGradleBuild"] = locDirectory["hasGradleBuild"]?.toString() ?: "null"
        locProperties["artifactDirectories.$locIndex.gradleProjectPath"] = locDirectory["gradleProjectPath"]?.toString() ?: "null"
        locProperties["artifactDirectories.$locIndex.version.lane"] = locVersion["lane"]?.toString() ?: "null"
        locProperties["artifactDirectories.$locIndex.version.revision"] = locVersion["revision"]?.toString() ?: "null"
        locProperties["artifactDirectories.$locIndex.version.qualifierKind"] = locVersion["qualifierKind"]?.toString() ?: "null"
        locProperties["artifactDirectories.$locIndex.version.qualifierLabel"] = locVersion["qualifierLabel"]?.toString() ?: "null"
        locProperties["artifactDirectories.$locIndex.version.resolvedValue"] = locVersion["resolvedValue"]?.toString() ?: "null"
    }

    return locProperties
}

extra["algitesResolveArtifactDirectoryMetadata"] = ::AIcResolveAlgitesArtifactDirectoryMetadata
extra["algitesResolveArtifactDirectoryMetadataMap"] = fun(
    aRepositoryRoot: File,
    aArtifactDirectoryPath: String?,
    aResolutionKind: String?,
    aRepositoryNameOverride: String?,
    aRepositoryVisibilityOverride: String?
): Map<String, Any?> {
    return AIcToMap(
        AIcResolveAlgitesArtifactDirectoryMetadata(
            aRepositoryRoot = aRepositoryRoot,
            aArtifactDirectoryPath = aArtifactDirectoryPath,
            aResolutionKind = aResolutionKind,
            aRepositoryNameOverride = aRepositoryNameOverride,
            aRepositoryVisibilityOverride = aRepositoryVisibilityOverride
        )
    )
}
extra["algitesResolveArtifactDirectoryMetadataText"] = fun(
    aRepositoryRoot: File,
    aArtifactDirectoryPath: String?,
    aResolutionKind: String?,
    aRepositoryNameOverride: String?,
    aRepositoryVisibilityOverride: String?,
    aOutputKind: String?
): String {
    return AIcFormatOutput(
        AIcResolveAlgitesArtifactDirectoryMetadata(
            aRepositoryRoot = aRepositoryRoot,
            aArtifactDirectoryPath = aArtifactDirectoryPath,
            aResolutionKind = aResolutionKind,
            aRepositoryNameOverride = aRepositoryNameOverride,
            aRepositoryVisibilityOverride = aRepositoryVisibilityOverride
        ),
        aOutputKind?.takeIf { it.isNotBlank() } ?: "yaml"
    )
}
extra["algitesFlattenArtifactDirectoryMetadata"] = ::AIcFlattenDottedProperties
