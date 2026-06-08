/*
 * Algites artifact directory metadata resolver core.
 *
 * This script intentionally contains only Settings/Project compatible logic.
 * It does not register tasks and does not use Project-only APIs such as
 * providers, layout, or tasks. Use the companion resolver wrapper when Gradle
 * tasks or command-line output are needed.
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
                ?.takeUnless { it.equals("RELEASE", ignoreCase = true) }

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
    val type: String? = null,
    val groupId: String? = null,
    val versionContext: AIcAlgitesVersionContext = AIcAlgitesVersionContext()
) {
    fun AIcMerge(aOther: AIcAlgitesResolvedState): AIcAlgitesResolvedState {
        return AIcAlgitesResolvedState(
            type = aOther.type ?: type,
            groupId = aOther.groupId ?: groupId,
            versionContext = versionContext.AIcMerge(aOther.versionContext)
        )
    }
}

data class AIcAlgitesDirectoryConfig(
    val file: File,
    val kind: String,
    val values: Map<String, String>
)

data class AIcAlgitesArtifactDirectoryMetadata(
    val path: String,
    val kind: String,
    val type: String?,
    val name: String,
    val description: String,
    val groupId: String?,
    val contentsModel: String,
    val hasGradleBuild: Boolean,
    val gradleProjectPath: String,
    val versionContext: AIcAlgitesVersionContext
)

data class AIcAlgitesRepositoryMetadata(
    val name: String,
    val visibility: String,
    val groupId: String?
)

data class AIcAlgitesResolutionResult(
    val repository: AIcAlgitesRepositoryMetadata,
    val artifactDirectories: List<AIcAlgitesArtifactDirectoryMetadata>
)

val AIcAlgitesIgnoredDirectoryNames = setOf(
    ".git",
    ".gradle",
    ".idea",
    "build",
    "run",
    "target",
    "out",
    "output",
    "docs-site",
    "documentation",
    "documentation-branch",
    "gh-pages",
    "source_gen",
    "source_gen.caches",
    "classes_gen"
)

fun AIcResolveAlgitesArtifactDirectoryMetadata(
    aRepositoryRoot: File,
    aArtifactDirectoryPath: String?,
    aResolutionKind: String?,
    aRepositoryNameOverride: String?,
    aRepositoryVisibilityOverride: String?
): AIcAlgitesResolutionResult {
    val locRepository = AIcResolveRepositoryMetadata(
        aRepositoryRoot = aRepositoryRoot,
        aRepositoryNameOverride = aRepositoryNameOverride,
        aRepositoryVisibilityOverride = aRepositoryVisibilityOverride
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
        listOf(AIcResolveSingleArtifactDirectory(aRepositoryRoot, locNormalizedPath ?: "."))
    } else if (locNormalizedPath == null) {
        AIcResolveAllArtifactDirectories(aRepositoryRoot)
    } else {
        AIcResolveArtifactDirectoryAndSubdirectories(aRepositoryRoot, locNormalizedPath)
    }

    return AIcAlgitesResolutionResult(
        repository = locRepository,
        artifactDirectories = locArtifactDirectories
    )
}

fun AIcResolveRepositoryMetadata(
    aRepositoryRoot: File,
    aRepositoryNameOverride: String?,
    aRepositoryVisibilityOverride: String?
): AIcAlgitesRepositoryMetadata {
    val locRootConfig = AIcFindAlgitesMetadataConfig(aRepositoryRoot, aRepositoryRoot)
        ?.takeIf { it.kind == "repository" }

    val locRepositoryName = aRepositoryNameOverride
        ?.takeIf { it.isNotBlank() }
        ?: locRootConfig?.values?.let {
            AIcFirstValue(
                it,
                "repository.name",
                "sourceRepository.name",
                "repository.id",
                "sourceRepository.id",
                "name",
                "id"
            )
        }?.takeIf { it.isNotBlank() }
        ?: aRepositoryRoot.name

    val locVisibility = aRepositoryVisibilityOverride
        ?.takeIf { it.isNotBlank() }
        ?: locRootConfig?.values?.let {
            AIcFirstValue(
                it,
                "repository.visibility",
                "sourceRepository.visibility",
                "visibility"
            )
        }?.takeIf { it.isNotBlank() }
        ?: AIcInferVisibilityFromRepositoryName(locRepositoryName)

    val locGroupId = locRootConfig?.values?.let {
        AIcFirstValue(
            it,
            "repository.groupId",
            "sourceRepository.groupId",
            "groupId"
        )
    }?.takeIf { it.isNotBlank() }

    return AIcAlgitesRepositoryMetadata(
        name = locRepositoryName,
        visibility = locVisibility,
        groupId = locGroupId
    )
}

fun AIcInferVisibilityFromRepositoryName(aRepositoryName: String): String {
    return when {
        aRepositoryName.startsWith("pub.") -> "pub"
        aRepositoryName.startsWith("priv.") -> "priv"
        else -> ""
    }
}

fun AIcResolveAllArtifactDirectories(aRepositoryRoot: File): List<AIcAlgitesArtifactDirectoryMetadata> {
    return AIcResolveArtifactDirectoryAndSubdirectories(
        aRepositoryRoot = aRepositoryRoot,
        aArtifactDirectoryPath = "."
    )
}

fun AIcResolveArtifactDirectoryAndSubdirectories(
    aRepositoryRoot: File,
    aArtifactDirectoryPath: String
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
        aArtifactDirectoryPath = aArtifactDirectoryPath
    )

    val locArtifactDirectories = mutableListOf<AIcAlgitesArtifactDirectoryMetadata>()

    fun AIcScanDirectory(aDirectory: File, aInheritedState: AIcAlgitesResolvedState) {
        val locConfig = AIcFindAlgitesMetadataConfig(aDirectory, aRepositoryRoot)

        var locCurrentState = aInheritedState
        var locStopScanningChildren = false

        if (locConfig != null) {
            locCurrentState = locCurrentState.AIcMerge(AIcResolvedStateFromConfig(locConfig))

            val locType = locCurrentState.type
            val locContentsModel = AIcContentsModel(
                aKind = locConfig.kind,
                aType = locType
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
    aArtifactDirectoryPath: String
): AIcAlgitesResolvedState {
    val locPathSegments = AIcPathSegments(aArtifactDirectoryPath)
    if (locPathSegments.isEmpty()) {
        return AIcAlgitesResolvedState()
    }

    var locCurrentDirectory = aRepositoryRoot
    var locCurrentState = AIcAlgitesResolvedState()

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
    aArtifactDirectoryPath: String
): AIcAlgitesArtifactDirectoryMetadata {
    var locCurrentDirectory = aRepositoryRoot
    var locCurrentState = AIcAlgitesResolvedState()

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
        aContentsModel = AIcContentsModel(locDirectoryConfig.kind, locCurrentState.type)
    )
}

fun AIcArtifactDirectoryMetadataFromConfig(
    aRepositoryRoot: File,
    aDirectory: File,
    aConfig: AIcAlgitesDirectoryConfig,
    aState: AIcAlgitesResolvedState,
    aContentsModel: String
): AIcAlgitesArtifactDirectoryMetadata {
    val locKindPrefix = AIcKindPrefix(aConfig.kind)
    val locPath = AIcRelativePath(aRepositoryRoot, aDirectory)
    val locName = AIcFirstValue(
        aConfig.values,
        "$locKindPrefix.name",
        "name",
        "$locKindPrefix.id",
        "id"
    )?.takeIf { it.isNotBlank() } ?: if (locPath == ".") aRepositoryRoot.name else aDirectory.name

    val locDescription = AIcFirstValue(
        aConfig.values,
        "$locKindPrefix.description",
        "description"
    ) ?: ""

    return AIcAlgitesArtifactDirectoryMetadata(
        path = locPath,
        kind = aConfig.kind,
        type = aState.type,
        name = locName,
        description = locDescription,
        groupId = aState.groupId,
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
        kind = locCandidate.second,
        values = AIcReadSimpleYamlScalars(locCandidate.first)
    )
}

fun AIcResolvedStateFromConfig(aConfig: AIcAlgitesDirectoryConfig): AIcAlgitesResolvedState {
    val locValues = aConfig.values
    val locKindPrefix = AIcKindPrefix(aConfig.kind)

    val locType = when (aConfig.kind) {
        "artifact-set" -> AIcFirstValue(
            locValues,
            "artifactSet.type",
            "type",
            "documentationType",
            "artifactType"
        )
        "artifact" -> AIcFirstValue(
            locValues,
            "artifact.type",
            "type",
            "documentationType",
            "artifactType"
        )
        else -> null
    }?.takeIf { it.isNotBlank() }

    val locGroupId = AIcFirstValue(
        locValues,
        "$locKindPrefix.groupId",
        "sourceRepository.groupId",
        "groupId"
    )?.takeIf { it.isNotBlank() }

    val locVersionContext = AIcAlgitesVersionContext(
        lane = AIcFirstValue(
            locValues,
            "$locKindPrefix.versionContext.lane",
            "$locKindPrefix.versionContext.releaseLine",
            "sourceRepository.versionContext.lane",
            "sourceRepository.versionContext.releaseLine",
            "versionContext.lane",
            "versionContext.releaseLine"
        )?.takeIf { it.isNotBlank() },
        revision = AIcFirstValue(
            locValues,
            "$locKindPrefix.versionContext.revision",
            "sourceRepository.versionContext.revision",
            "versionContext.revision"
        )?.takeIf { it.isNotBlank() },
        qualifierKind = AIcFirstValue(
            locValues,
            "$locKindPrefix.versionContext.qualifierKind",
            "sourceRepository.versionContext.qualifierKind",
            "versionContext.qualifierKind"
        )?.takeIf { it.isNotBlank() },
        qualifierLabel = AIcFirstValue(
            locValues,
            "$locKindPrefix.versionContext.qualifierLabel",
            "sourceRepository.versionContext.qualifierLabel",
            "versionContext.qualifierLabel"
        )?.takeIf { it.isNotBlank() }
    )

    return AIcAlgitesResolvedState(
        type = locType,
        groupId = locGroupId,
        versionContext = locVersionContext
    )
}

fun AIcKindPrefix(aKind: String): String {
    return when (aKind) {
        "repository" -> "repository"
        "artifact-set" -> "artifactSet"
        "artifact" -> "artifact"
        else -> aKind
    }
}

fun AIcContentsModel(aKind: String, aType: String?): String {
    return when {
        aKind == "repository" -> "container"
        aKind == "artifact" -> "self-contained"
        aKind == "artifact-set" && aType == "mps" -> "self-contained"
        aKind == "artifact-set" && aType == "java" -> "container"
        aKind == "artifact-set" -> "container"
        else -> "container"
    }
}

fun AIcHasGradleBuild(aDirectory: File): Boolean {
    return listOf(
        aDirectory.resolve("build.gradle.kts"),
        aDirectory.resolve("build.gradle")
    ).any { it.isFile }
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
    val locNormalizedPath = aPath.replace('\\', '/').trim('/')
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
    return aKeys.firstNotNullOfOrNull { locKey ->
        aValues[locKey]
    }
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

fun AIcToYaml(aResult: AIcAlgitesResolutionResult): String {
    return buildString {
        appendLine("repository:")
        appendLine("  name: ${AIcYamlScalar(aResult.repository.name)}")
        appendLine("  visibility: ${AIcYamlScalar(aResult.repository.visibility)}")
        appendLine("  groupId: ${AIcYamlScalar(aResult.repository.groupId)}")
        appendLine()
        appendLine("artifactDirectories:")
        appendLine("  count: ${aResult.artifactDirectories.size}")

        aResult.artifactDirectories.forEachIndexed { locIndex, locDirectory ->
            appendLine("  $locIndex:")
            appendLine("    path: ${AIcYamlScalar(locDirectory.path)}")
            appendLine("    kind: ${AIcYamlScalar(locDirectory.kind)}")
            appendLine("    type: ${AIcYamlScalar(locDirectory.type)}")
            appendLine("    name: ${AIcYamlScalar(locDirectory.name)}")
            appendLine("    description: ${AIcYamlScalar(locDirectory.description)}")
            appendLine("    groupId: ${AIcYamlScalar(locDirectory.groupId)}")
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
        appendLine("repository.name=${AIcPropertiesScalar(aResult.repository.name)}")
        appendLine("repository.visibility=${AIcPropertiesScalar(aResult.repository.visibility)}")
        appendLine("repository.groupId=${AIcPropertiesScalar(aResult.repository.groupId)}")
        appendLine("artifactDirectories.count=${aResult.artifactDirectories.size}")

        aResult.artifactDirectories.forEachIndexed { locIndex, locDirectory ->
            appendLine("artifactDirectories.$locIndex.path=${AIcPropertiesScalar(locDirectory.path)}")
            appendLine("artifactDirectories.$locIndex.kind=${AIcPropertiesScalar(locDirectory.kind)}")
            appendLine("artifactDirectories.$locIndex.type=${AIcPropertiesScalar(locDirectory.type)}")
            appendLine("artifactDirectories.$locIndex.name=${AIcPropertiesScalar(locDirectory.name)}")
            appendLine("artifactDirectories.$locIndex.description=${AIcPropertiesScalar(locDirectory.description)}")
            appendLine("artifactDirectories.$locIndex.groupId=${AIcPropertiesScalar(locDirectory.groupId)}")
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
            "name" to aResult.repository.name,
            "visibility" to aResult.repository.visibility,
            "groupId" to aResult.repository.groupId
        ),
        "artifactDirectories" to aResult.artifactDirectories.map { locDirectory ->
            linkedMapOf<String, Any?>(
                "path" to locDirectory.path,
                "kind" to locDirectory.kind,
                "type" to locDirectory.type,
                "name" to locDirectory.name,
                "description" to locDirectory.description,
                "groupId" to locDirectory.groupId,
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

    locProperties["repository.name"] = locRepository["name"]?.toString() ?: "null"
    locProperties["repository.visibility"] = locRepository["visibility"]?.toString() ?: "null"
    locProperties["repository.groupId"] = locRepository["groupId"]?.toString() ?: "null"
    locProperties["artifactDirectories.count"] = locArtifactDirectories.size.toString()

    locArtifactDirectories.forEachIndexed { locIndex, locDirectory ->
        val locVersion = locDirectory["version"] as Map<String, Any?>
        locProperties["artifactDirectories.$locIndex.path"] = locDirectory["path"]?.toString() ?: "null"
        locProperties["artifactDirectories.$locIndex.kind"] = locDirectory["kind"]?.toString() ?: "null"
        locProperties["artifactDirectories.$locIndex.type"] = locDirectory["type"]?.toString() ?: "null"
        locProperties["artifactDirectories.$locIndex.name"] = locDirectory["name"]?.toString() ?: "null"
        locProperties["artifactDirectories.$locIndex.description"] = locDirectory["description"]?.toString() ?: "null"
        locProperties["artifactDirectories.$locIndex.groupId"] = locDirectory["groupId"]?.toString() ?: "null"
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
extra["algitesFlattenArtifactDirectoryMetadata"] = fun(aResultMap: Map<String, Any?>): Map<String, String> {
    return AIcFlattenDottedProperties(aResultMap)
}
