/*
 * Algites shared MPS documentation site script.
 *
 * Intended location in governance repository:
 *   gradle/tool/documentation/algites-docs-site-mps.gradle.kts
 *
 * A repository can apply only this script; it automatically applies the base
 * documentation-site script.
 */

import org.gradle.api.Action
import org.gradle.api.Task
import java.security.MessageDigest

val locAlgitesDocsBaseScript = (findProperty("algites.docs.baseScript") as String?)
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/algites-docs-site-base.gradle.kts"

apply(from = uri(locAlgitesDocsBaseScript))

val locProjectRootDirectory = layout.projectDirectory.asFile

data class AIcArtifactSetProject(
    val locRelativePath: String,
    val locProjectDirectory: File
) : java.io.Serializable

data class AIcDiscoveredMpsArtifact(
    val locArtifactSetProjectPath: String,
    val locDescriptorPath: String,
    val locModuleName: String,
    val locModuleKind: String,
    val locModulePath: String,
    val locArtifactId: String,
    val locDocumentationPath: String,
    val locPublishable: Boolean
) : java.io.Serializable

data class AIcMpsArtifactCandidate(
    val locArtifactSetProjectPath: String,
    val locDescriptorPath: String,
    val locModuleName: String,
    val locModuleKind: String,
    val locBaseModulePath: String,
    val locPublishable: Boolean
) : java.io.Serializable

val locDiscoveryOutputFile = layout.buildDirectory.file("algites/discovered-mps-artifacts.tsv")
val locAlgitesDocsResolvedRepositoryName = (extra.properties["algitesDocsResolvedRepositoryName"] as String?) ?: rootProject.name

@Suppress("UNCHECKED_CAST")
val locAlgitesDocsResolvedArtifactDirectories = extra.properties["algitesDocsResolvedArtifactDirectories"] as? List<Map<String, String?>>
    ?: emptyList()


fun AIcReadMpsDocsRepositoryId(): String {
    return locAlgitesDocsResolvedRepositoryName
}

val locGeneratedDocsRoot = layout.projectDirectory.dir(
    (extra.properties["algitesPublicationDocsRootPath"] as String?)
        ?: (findProperty("algites.docs.generatedRoot") as String?)
        ?: "docs-site/generated"
)

fun String.AIcToSha256Text(): String {
    val locDigest = MessageDigest.getInstance("SHA-256")
    val locHashBytes = locDigest.digest(toByteArray(Charsets.UTF_8))
    return locHashBytes.joinToString("") { locByte -> "%02x".format(locByte) }
}

fun AIcReadXmlAttribute(aXmlText: String, aAttributeName: String): String? {
    val locRegex = Regex("""\b${Regex.escape(aAttributeName)}\s*=\s*["']([^"']+)["']""")
    return locRegex.find(aXmlText)?.groupValues?.get(1)
}

fun AIcReadRepositoryId(): String {
    return locAlgitesDocsResolvedRepositoryName
}

fun AIcReadArtifactSetProjects(): List<AIcArtifactSetProject> {
    val locMpsArtifactDirectories = locAlgitesDocsResolvedArtifactDirectories.filter { locArtifactDirectory ->
        locArtifactDirectory["type"] == "mps" && locArtifactDirectory["contentsModel"] == "self-contained"
    }

    require(locMpsArtifactDirectories.isNotEmpty()) {
        "Cannot find any resolved MPS artifact directory. Expected at least one artifact directory with type=mps and contentsModel=self-contained."
    }

    return locMpsArtifactDirectories.map { locArtifactDirectory ->
        val locRelativePath = locArtifactDirectory["path"] ?: "."
        val locProjectDirectory = File(locProjectRootDirectory, locRelativePath)
        require(locProjectDirectory.isDirectory) {
            "Resolved MPS artifact directory does not exist: ${locProjectDirectory.absolutePath}"
        }
        AIcArtifactSetProject(
            locRelativePath = locRelativePath,
            locProjectDirectory = locProjectDirectory
        )
    }
}

fun AIcReadRepositoryRole(): String {
    val locRepositoryId = AIcReadMpsDocsRepositoryId()
    val locSegments = locRepositoryId.split(".")
    require(locSegments.size >= 3) {
        "Repository id must follow <vis>.<role>.<BusinessName>[.<reposubname>]: ${locRepositoryId}"
    }
    return locSegments[1]
}

fun AIcDeriveModuleKind(aDescriptorFile: File): String? {
    return when (aDescriptorFile.extension.lowercase()) {
        "mpl" -> "lang"
        "msd" -> "sol"
        else -> null
    }
}

fun AIcRemoveMpsTechnicalPrefix(aModuleName: String, aModuleKind: String): String {
    val locLegacyPrefix = when (aModuleKind) {
        "lang" -> "mpslang."
        "sol" -> "mpssol."
        else -> null
    }

    if (locLegacyPrefix != null && aModuleName.startsWith(locLegacyPrefix)) {
        return aModuleName.removePrefix(locLegacyPrefix)
    }

    val locModernMarker = ".mps.${aModuleKind}."
    val locModernMarkerIndex = aModuleName.indexOf(locModernMarker)

    if (locModernMarkerIndex >= 0) {
        return aModuleName.substring(0, locModernMarkerIndex) + "." +
            aModuleName.substring(locModernMarkerIndex + locModernMarker.length)
    }

    return aModuleName
}

fun AIcStripKnownDomainRolePrefix(aNameWithoutMpsPrefix: String, aRepositoryRole: String): String {
    val locSegments = aNameWithoutMpsPrefix.split(".")
    if (locSegments.size <= 1) {
        return aNameWithoutMpsPrefix
    }

    val locRoleIndex = locSegments.indexOf(aRepositoryRole)
    if (locRoleIndex >= 0 && locRoleIndex < locSegments.lastIndex) {
        return locSegments.drop(locRoleIndex + 1).joinToString(".")
    }

    return aNameWithoutMpsPrefix
}

fun AIcDeriveBaseModulePath(
    aModuleName: String,
    aModuleKind: String,
    aRepositoryRole: String
): String {
    val locNameWithoutMpsPrefix = AIcRemoveMpsTechnicalPrefix(aModuleName, aModuleKind)
    return AIcStripKnownDomainRolePrefix(locNameWithoutMpsPrefix, aRepositoryRole)
}

fun AIcIsPublishableMpsModule(aModuleName: String, aModulePath: String): Boolean {
    val locModuleNameLowercase = aModuleName.lowercase()
    val locModulePathLowercase = aModulePath.lowercase()

    return !(
        locModuleNameLowercase.startsWith("mpslang.test") ||
        locModuleNameLowercase.startsWith("mpssol.test") ||
        ".test." in locModuleNameLowercase ||
        locModulePathLowercase.startsWith("test") ||
        locModulePathLowercase.startsWith("lang.test") ||
        locModulePathLowercase.startsWith("sol.test") ||
        ".test." in locModulePathLowercase
    )
}

fun AIcReadMpsModuleName(aDescriptorFile: File): String? {
    val locXmlText = aDescriptorFile.readText(Charsets.UTF_8)
    return AIcReadXmlAttribute(locXmlText, "namespace")
        ?: AIcReadXmlAttribute(locXmlText, "name")
        ?: aDescriptorFile.nameWithoutExtension
}

fun AIcResolveModulePathCollisions(aCandidates: List<AIcMpsArtifactCandidate>): List<Pair<AIcMpsArtifactCandidate, String>> {
    val locBasePathCounts = aCandidates.groupingBy { it.locBaseModulePath }.eachCount()

    val locResolvedCandidates = aCandidates.map { locCandidate ->
        val locResolvedModulePath = if ((locBasePathCounts[locCandidate.locBaseModulePath] ?: 0) > 1) {
            "${locCandidate.locModuleKind}.${locCandidate.locBaseModulePath}"
        } else {
            locCandidate.locBaseModulePath
        }

        locCandidate to locResolvedModulePath
    }

    val locDuplicatedResolvedModulePaths = locResolvedCandidates
        .groupBy { it.second }
        .filterValues { it.size > 1 }

    require(locDuplicatedResolvedModulePaths.isEmpty()) {
        buildString {
            appendLine("Duplicate resolved MPS modulePath value(s) detected.")
            appendLine("Each modulePath must be unique within one source repository because it is used for artifactId and documentation path derivation.")
            locDuplicatedResolvedModulePaths.forEach { (locModulePath, locConflictingCandidates) ->
                appendLine("Duplicate modulePath: ${locModulePath}")
                locConflictingCandidates.forEach { (locCandidate, _) ->
                    appendLine(" - ${locCandidate.locDescriptorPath} -> ${locCandidate.locModuleName}")
                }
            }
        }
    }

    return locResolvedCandidates
}

fun AIcValidateDiscoveredMpsArtifactUniqueness(aArtifacts: List<AIcDiscoveredMpsArtifact>) {
    val locDuplicateModulePaths = aArtifacts.groupBy { it.locModulePath }.filterValues { it.size > 1 }
    val locDuplicateArtifactIds = aArtifacts.groupBy { it.locArtifactId }.filterValues { it.size > 1 }
    val locDuplicateDocumentationPaths = aArtifacts.groupBy { it.locDocumentationPath }.filterValues { it.size > 1 }

    require(locDuplicateModulePaths.isEmpty() && locDuplicateArtifactIds.isEmpty() && locDuplicateDocumentationPaths.isEmpty()) {
        buildString {
            appendLine("Duplicate discovered MPS artifact identity value(s) detected.")

            if (locDuplicateModulePaths.isNotEmpty()) {
                appendLine("Duplicate modulePath value(s):")
                locDuplicateModulePaths.forEach { (locValue, locArtifacts) ->
                    appendLine(" - ${locValue}")
                    locArtifacts.forEach { locArtifact ->
                        appendLine("   - ${locArtifact.locDescriptorPath} -> ${locArtifact.locModuleName}")
                    }
                }
            }

            if (locDuplicateArtifactIds.isNotEmpty()) {
                appendLine("Duplicate artifactId value(s):")
                locDuplicateArtifactIds.forEach { (locValue, locArtifacts) ->
                    appendLine(" - ${locValue}")
                    locArtifacts.forEach { locArtifact ->
                        appendLine("   - ${locArtifact.locDescriptorPath} -> ${locArtifact.locModuleName}")
                    }
                }
            }

            if (locDuplicateDocumentationPaths.isNotEmpty()) {
                appendLine("Duplicate documentationPath value(s):")
                locDuplicateDocumentationPaths.forEach { (locValue, locArtifacts) ->
                    appendLine(" - ${locValue}")
                    locArtifacts.forEach { locArtifact ->
                        appendLine("   - ${locArtifact.locDescriptorPath} -> ${locArtifact.locModuleName}")
                    }
                }
            }
        }
    }
}

fun AIcDiscoverMpsArtifacts(
    aRepositoryId: String,
    aArtifactSetProjects: List<AIcArtifactSetProject>
): List<AIcDiscoveredMpsArtifact> {
    val locRepositoryRole = AIcReadRepositoryRole()

    val locCandidates = aArtifactSetProjects.flatMap { locArtifactSetProject ->
        val locDescriptorFiles = locArtifactSetProject.locProjectDirectory
            .walkTopDown()
            .filter { locFile -> locFile.isFile }
            .filter { locFile -> locFile.extension.lowercase() == "mpl" || locFile.extension.lowercase() == "msd" }
            .filter { locFile ->
                val locRelativePath = locArtifactSetProject.locProjectDirectory.toPath()
                    .relativize(locFile.toPath())
                    .toString()
                    .replace(File.separatorChar, '/')

                !(
                    "/build/" in "/${locRelativePath}" ||
                    "/.gradle/" in "/${locRelativePath}" ||
                    "/classes_gen/" in "/${locRelativePath}" ||
                    "/source_gen/" in "/${locRelativePath}" ||
                    "/source_gen.caches/" in "/${locRelativePath}"
                )
            }
            .toList()

        locDescriptorFiles.mapNotNull { locDescriptorFile ->
            val locModuleKind = AIcDeriveModuleKind(locDescriptorFile) ?: return@mapNotNull null
            val locModuleName = AIcReadMpsModuleName(locDescriptorFile) ?: return@mapNotNull null
            val locBaseModulePath = AIcDeriveBaseModulePath(locModuleName, locModuleKind, locRepositoryRole)
            val locDescriptorPath = locProjectRootDirectory.toPath()
                .relativize(locDescriptorFile.toPath())
                .toString()
                .replace(File.separatorChar, '/')

            AIcMpsArtifactCandidate(
                locArtifactSetProjectPath = locArtifactSetProject.locRelativePath,
                locDescriptorPath = locDescriptorPath,
                locModuleName = locModuleName,
                locModuleKind = locModuleKind,
                locBaseModulePath = locBaseModulePath,
                locPublishable = AIcIsPublishableMpsModule(locModuleName, locBaseModulePath)
            )
        }
    }.distinctBy {
        it.locModuleName
    }

    val locDiscoveredArtifacts = AIcResolveModulePathCollisions(locCandidates).map { (locCandidate, locModulePath) ->
        val locArtifactId = "${aRepositoryId}_${locModulePath}"
        val locDocumentationPath = locModulePath

        AIcDiscoveredMpsArtifact(
            locArtifactSetProjectPath = locCandidate.locArtifactSetProjectPath,
            locDescriptorPath = locCandidate.locDescriptorPath,
            locModuleName = locCandidate.locModuleName,
            locModuleKind = locCandidate.locModuleKind,
            locModulePath = locModulePath,
            locArtifactId = locArtifactId,
            locDocumentationPath = locDocumentationPath,
            locPublishable = locCandidate.locPublishable
        )
    }.sortedWith(
        compareBy<AIcDiscoveredMpsArtifact> { it.locArtifactSetProjectPath }
            .thenBy { it.locModuleKind }
            .thenBy { it.locModulePath }
    )

    AIcValidateDiscoveredMpsArtifactUniqueness(locDiscoveredArtifacts)

    return locDiscoveredArtifacts
}


class AIcMpsSupport(
    private val locProjectRootDirectory: File,
    private val locRepositoryId: String,
    private val locResolvedArtifactDirectories: List<Map<String, String?>>
) : java.io.Serializable {

    fun AIcReadArtifactSetProjects(): List<AIcArtifactSetProject> {
        val locMpsArtifactDirectories = locResolvedArtifactDirectories.filter { locArtifactDirectory ->
            locArtifactDirectory["type"] == "mps" && locArtifactDirectory["contentsModel"] == "self-contained"
        }

        require(locMpsArtifactDirectories.isNotEmpty()) {
            "Cannot find any resolved MPS artifact directory. Expected at least one artifact directory with type=mps and contentsModel=self-contained."
        }

        return locMpsArtifactDirectories.map { locArtifactDirectory ->
            val locRelativePath = locArtifactDirectory["path"] ?: "."
            val locProjectDirectory = File(locProjectRootDirectory, locRelativePath)
            require(locProjectDirectory.isDirectory) {
                "Resolved MPS artifact directory does not exist: ${locProjectDirectory.absolutePath}"
            }
            AIcArtifactSetProject(
                locRelativePath = locRelativePath,
                locProjectDirectory = locProjectDirectory
            )
        }
    }

    fun AIcDiscoverMpsArtifacts(aArtifactSetProjects: List<AIcArtifactSetProject>): List<AIcDiscoveredMpsArtifact> {
        val locRepositoryRole = AIcReadRepositoryRole()

        val locCandidates = aArtifactSetProjects.flatMap { locArtifactSetProject ->
            val locDescriptorFiles = locArtifactSetProject.locProjectDirectory
                .walkTopDown()
                .filter { locFile -> locFile.isFile }
                .filter { locFile -> locFile.extension.lowercase() == "mpl" || locFile.extension.lowercase() == "msd" }
                .filter { locFile ->
                    val locRelativePath = locArtifactSetProject.locProjectDirectory.toPath()
                        .relativize(locFile.toPath())
                        .toString()
                        .replace(File.separatorChar, '/')

                    !("/build/" in "/${locRelativePath}" ||
                        "/.gradle/" in "/${locRelativePath}" ||
                        "/classes_gen/" in "/${locRelativePath}" ||
                        "/source_gen/" in "/${locRelativePath}" ||
                        "/source_gen.caches/" in "/${locRelativePath}")
                }
                .toList()

            locDescriptorFiles.mapNotNull { locDescriptorFile ->
                val locModuleKind = AIcDeriveModuleKind(locDescriptorFile) ?: return@mapNotNull null
                val locModuleName = AIcReadMpsModuleName(locDescriptorFile) ?: return@mapNotNull null
                val locBaseModulePath = AIcDeriveBaseModulePath(locModuleName, locModuleKind, locRepositoryRole)
                val locDescriptorPath = locProjectRootDirectory.toPath()
                    .relativize(locDescriptorFile.toPath())
                    .toString()
                    .replace(File.separatorChar, '/')

                AIcMpsArtifactCandidate(
                    locArtifactSetProjectPath = locArtifactSetProject.locRelativePath,
                    locDescriptorPath = locDescriptorPath,
                    locModuleName = locModuleName,
                    locModuleKind = locModuleKind,
                    locBaseModulePath = locBaseModulePath,
                    locPublishable = AIcIsPublishableMpsModule(locModuleName, locBaseModulePath)
                )
            }
        }.distinctBy { it.locModuleName }

        val locDiscoveredArtifacts = AIcResolveModulePathCollisions(locCandidates).map { (locCandidate, locModulePath) ->
            val locArtifactId = "${locRepositoryId}_${locModulePath}"
            val locDocumentationPath = locModulePath

            AIcDiscoveredMpsArtifact(
                locArtifactSetProjectPath = locCandidate.locArtifactSetProjectPath,
                locDescriptorPath = locCandidate.locDescriptorPath,
                locModuleName = locCandidate.locModuleName,
                locModuleKind = locCandidate.locModuleKind,
                locModulePath = locModulePath,
                locArtifactId = locArtifactId,
                locDocumentationPath = locDocumentationPath,
                locPublishable = locCandidate.locPublishable
            )
        }.sortedWith(
            compareBy<AIcDiscoveredMpsArtifact> { it.locArtifactSetProjectPath }
                .thenBy { it.locModuleKind }
                .thenBy { it.locModulePath }
        )

        AIcValidateDiscoveredMpsArtifactUniqueness(locDiscoveredArtifacts)
        return locDiscoveredArtifacts
    }

    private fun AIcReadRepositoryRole(): String {
        val locSegments = locRepositoryId.split(".")
        require(locSegments.size >= 3) {
            "Repository id must follow <vis>.<role>.<BusinessName>[.<reposubname>]: ${locRepositoryId}"
        }
        return locSegments[1]
    }

    private fun AIcDeriveModuleKind(aDescriptorFile: File): String? {
        return when (aDescriptorFile.extension.lowercase()) {
            "mpl" -> "lang"
            "msd" -> "sol"
            else -> null
        }
    }

    private fun AIcReadXmlAttribute(aXmlText: String, aAttributeName: String): String? {
        val locRegex = Regex("""\b${Regex.escape(aAttributeName)}\s*=\s*["']([^"']+)["']""")
        return locRegex.find(aXmlText)?.groupValues?.get(1)
    }

    private fun AIcReadMpsModuleName(aDescriptorFile: File): String? {
        val locXmlText = aDescriptorFile.readText(Charsets.UTF_8)
        return AIcReadXmlAttribute(locXmlText, "namespace")
            ?: AIcReadXmlAttribute(locXmlText, "name")
            ?: aDescriptorFile.nameWithoutExtension
    }

    private fun AIcDeriveBaseModulePath(aModuleName: String, aModuleKind: String, aRepositoryRole: String): String {
        val locNameWithoutMpsPrefix = AIcRemoveMpsTechnicalPrefix(aModuleName, aModuleKind)
        return AIcStripKnownDomainRolePrefix(locNameWithoutMpsPrefix, aRepositoryRole)
    }

    private fun AIcRemoveMpsTechnicalPrefix(aModuleName: String, aModuleKind: String): String {
        val locLegacyPrefix = when (aModuleKind) {
            "lang" -> "mpslang."
            "sol" -> "mpssol."
            else -> null
        }

        if (locLegacyPrefix != null && aModuleName.startsWith(locLegacyPrefix)) {
            return aModuleName.removePrefix(locLegacyPrefix)
        }

        val locModernMarker = ".mps.${aModuleKind}."
        val locModernMarkerIndex = aModuleName.indexOf(locModernMarker)

        if (locModernMarkerIndex >= 0) {
            return aModuleName.substring(0, locModernMarkerIndex) + "." +
                aModuleName.substring(locModernMarkerIndex + locModernMarker.length)
        }

        return aModuleName
    }

    private fun AIcStripKnownDomainRolePrefix(aNameWithoutMpsPrefix: String, aRepositoryRole: String): String {
        val locSegments = aNameWithoutMpsPrefix.split(".")
        if (locSegments.size <= 1) {
            return aNameWithoutMpsPrefix
        }

        val locRoleIndex = locSegments.indexOf(aRepositoryRole)
        if (locRoleIndex >= 0 && locRoleIndex < locSegments.lastIndex) {
            return locSegments.drop(locRoleIndex + 1).joinToString(".")
        }

        return aNameWithoutMpsPrefix
    }

    private fun AIcIsPublishableMpsModule(aModuleName: String, aModulePath: String): Boolean {
        val locModuleNameLowercase = aModuleName.lowercase()
        val locModulePathLowercase = aModulePath.lowercase()

        return !(locModuleNameLowercase.startsWith("mpslang.test") ||
            locModuleNameLowercase.startsWith("mpssol.test") ||
            ".test." in locModuleNameLowercase ||
            locModulePathLowercase.startsWith("test") ||
            locModulePathLowercase.startsWith("lang.test") ||
            locModulePathLowercase.startsWith("sol.test") ||
            ".test." in locModulePathLowercase)
    }

    private fun AIcResolveModulePathCollisions(aCandidates: List<AIcMpsArtifactCandidate>): List<Pair<AIcMpsArtifactCandidate, String>> {
        val locBasePathCounts = aCandidates.groupingBy { it.locBaseModulePath }.eachCount()

        val locResolvedCandidates = aCandidates.map { locCandidate ->
            val locResolvedModulePath = if ((locBasePathCounts[locCandidate.locBaseModulePath] ?: 0) > 1) {
                "${locCandidate.locModuleKind}.${locCandidate.locBaseModulePath}"
            } else {
                locCandidate.locBaseModulePath
            }

            locCandidate to locResolvedModulePath
        }

        val locDuplicatedResolvedModulePaths = locResolvedCandidates
            .groupBy { it.second }
            .filterValues { it.size > 1 }

        require(locDuplicatedResolvedModulePaths.isEmpty()) {
            buildString {
                appendLine("Duplicate resolved MPS modulePath value(s) detected.")
                appendLine("Each modulePath must be unique within one source repository because it is used for artifactId and documentation path derivation.")
                locDuplicatedResolvedModulePaths.forEach { (locModulePath, locConflictingCandidates) ->
                    appendLine("Duplicate modulePath: ${locModulePath}")
                    locConflictingCandidates.forEach { (locCandidate, _) ->
                        appendLine(" - ${locCandidate.locDescriptorPath} -> ${locCandidate.locModuleName}")
                    }
                }
            }
        }

        return locResolvedCandidates
    }

    private fun AIcValidateDiscoveredMpsArtifactUniqueness(aArtifacts: List<AIcDiscoveredMpsArtifact>) {
        val locDuplicateModulePaths = aArtifacts.groupBy { it.locModulePath }.filterValues { it.size > 1 }
        val locDuplicateArtifactIds = aArtifacts.groupBy { it.locArtifactId }.filterValues { it.size > 1 }
        val locDuplicateDocumentationPaths = aArtifacts.groupBy { it.locDocumentationPath }.filterValues { it.size > 1 }

        require(locDuplicateModulePaths.isEmpty() && locDuplicateArtifactIds.isEmpty() && locDuplicateDocumentationPaths.isEmpty()) {
            buildString {
                appendLine("Duplicate discovered MPS artifact identity value(s) detected.")
                if (locDuplicateModulePaths.isNotEmpty()) {
                    appendLine("Duplicate modulePath value(s):")
                    locDuplicateModulePaths.forEach { (locValue, locArtifacts) ->
                        appendLine(" - ${locValue}")
                        locArtifacts.forEach { locArtifact -> appendLine("   - ${locArtifact.locDescriptorPath} -> ${locArtifact.locModuleName}") }
                    }
                }
                if (locDuplicateArtifactIds.isNotEmpty()) {
                    appendLine("Duplicate artifactId value(s):")
                    locDuplicateArtifactIds.forEach { (locValue, locArtifacts) ->
                        appendLine(" - ${locValue}")
                        locArtifacts.forEach { locArtifact -> appendLine("   - ${locArtifact.locDescriptorPath} -> ${locArtifact.locModuleName}") }
                    }
                }
                if (locDuplicateDocumentationPaths.isNotEmpty()) {
                    appendLine("Duplicate documentationPath value(s):")
                    locDuplicateDocumentationPaths.forEach { (locValue, locArtifacts) ->
                        appendLine(" - ${locValue}")
                        locArtifacts.forEach { locArtifact -> appendLine("   - ${locArtifact.locDescriptorPath} -> ${locArtifact.locModuleName}") }
                    }
                }
            }
        }
    }
}

class AIcValidateAlgitesConfigurationAction(
    private val locRepositoryId: String,
    private val locProjectRootDirectory: File,
    private val locResolvedArtifactDirectories: List<Map<String, String?>>
) : Action<Task>, java.io.Serializable {
    override fun execute(aTask: Task) {
        val locArtifactSetProjects = AIcMpsSupport(locProjectRootDirectory, locRepositoryId, locResolvedArtifactDirectories).AIcReadArtifactSetProjects()
        aTask.logger.lifecycle("Algites repository id: ${locRepositoryId}")
        aTask.logger.lifecycle("Configured artifact-set project(s):")
        locArtifactSetProjects.forEach { locArtifactSetProject ->
            aTask.logger.lifecycle(" - ${locArtifactSetProject.locRelativePath}")
        }
    }
}

class AIcDiscoverMpsArtifactsAction(
    private val locRepositoryId: String,
    private val locProjectRootDirectory: File,
    private val locResolvedArtifactDirectories: List<Map<String, String?>>,
    private val locOutputFile: File
) : Action<Task>, java.io.Serializable {
    override fun execute(aTask: Task) {
        val locSupport = AIcMpsSupport(locProjectRootDirectory, locRepositoryId, locResolvedArtifactDirectories)
        val locArtifactSetProjects = locSupport.AIcReadArtifactSetProjects()
        val locArtifacts = locSupport.AIcDiscoverMpsArtifacts(locArtifactSetProjects)

        locOutputFile.parentFile.mkdirs()
        locOutputFile.writeText(
            buildString {
                appendLine("artifactSetProjectPath\tdescriptorPath\tmoduleKind\tmodulePath\tartifactId\tdocumentationPath\tpublishable\tmoduleName")
                locArtifacts.forEach { locArtifact ->
                    appendLine(
                        listOf(
                            locArtifact.locArtifactSetProjectPath,
                            locArtifact.locDescriptorPath,
                            locArtifact.locModuleKind,
                            locArtifact.locModulePath,
                            locArtifact.locArtifactId,
                            locArtifact.locDocumentationPath,
                            locArtifact.locPublishable.toString(),
                            locArtifact.locModuleName
                        ).joinToString("\t")
                    )
                }
            },
            Charsets.UTF_8
        )

        aTask.logger.lifecycle("Discovered ${locArtifacts.size} MPS artifact(s).")
        aTask.logger.lifecycle("Discovery output: ${locOutputFile.absolutePath}")
    }
}

class AIcPrintDiscoveredMpsArtifactsAction(
    private val locDiscoveryFile: File
) : Action<Task>, java.io.Serializable {
    override fun execute(aTask: Task) {
        aTask.logger.lifecycle(locDiscoveryFile.readText(Charsets.UTF_8))
    }
}

class AIcGenerateDummyMpsDocsAction(
    private val locRepositoryId: String,
    private val locDiscoveryFile: File,
    private val locGeneratedDocsRootFile: File
) : Action<Task>, java.io.Serializable {
    override fun execute(aTask: Task) {
        require(locDiscoveryFile.isFile) {
            "Missing discovery output file: ${locDiscoveryFile.absolutePath}"
        }

        val locLines = locDiscoveryFile.readLines(Charsets.UTF_8).drop(1).filter { it.isNotBlank() }
        val locPublishableLines = locLines.filter { locLine ->
            val locColumns = locLine.split("\t")
            locColumns.size >= 8 && locColumns[6].toBoolean()
        }

        locPublishableLines.forEach { locLine ->
            val locColumns = locLine.split("\t")
            require(locColumns.size >= 8) {
                "Invalid discovery line: ${locLine}"
            }

            val locArtifactSetProjectPath = locColumns[0]
            val locDescriptorPath = locColumns[1]
            val locModuleKind = locColumns[2]
            val locModulePath = locColumns[3]
            val locArtifactId = locColumns[4]
            val locDocumentationPath = locColumns[5]
            val locModuleName = locColumns[7]
            val locContentHash = AIcToSha256Text(locLine)

            val locTargetDirectory = File(locGeneratedDocsRootFile, "${locDocumentationPath}/latest")
            locTargetDirectory.deleteRecursively()
            locTargetDirectory.mkdirs()

            locTargetDirectory.resolve("index.html").writeText(
                """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>${locModulePath}</title>
                </head>
                <body>
                  <main>
                    <h1>${locModulePath}</h1>
                    <dl>
                      <dt>Artifact-set project path</dt>
                      <dd>${locArtifactSetProjectPath}</dd>
                      <dt>Descriptor path</dt>
                      <dd>${locDescriptorPath}</dd>
                      <dt>Module kind</dt>
                      <dd>${locModuleKind}</dd>
                      <dt>MPS module name</dt>
                      <dd>${locModuleName}</dd>
                      <dt>Artifact ID</dt>
                      <dd>${locArtifactId}</dd>
                      <dt>Documentation path</dt>
                      <dd>${locDocumentationPath}</dd>
                      <dt>Discovery hash</dt>
                      <dd>${locContentHash}</dd>
                    </dl>
                  </main>
                </body>
                </html>
                """.trimIndent(),
                Charsets.UTF_8
            )
        }

        val locIndexFile = File(locGeneratedDocsRootFile, "index.html")
        locIndexFile.parentFile.mkdirs()
        locIndexFile.writeText(
            """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <title>Generated ${locRepositoryId} MPS Documentation</title>
            </head>
            <body>
              <main>
                <h1>Generated ${locRepositoryId} MPS Documentation</h1>
                <ul>
            ${
                locPublishableLines.joinToString("\n") { locLine ->
                    val locColumns = locLine.split("\t")
                    val locModulePath = locColumns[3]
                    val locDocumentationPath = locColumns[5]
                    "      <li><a href=\"${locDocumentationPath}/latest/index.html\">${locModulePath}</a></li>"
                }
            }
                </ul>
              </main>
            </body>
            </html>
            """.trimIndent(),
            Charsets.UTF_8
        )

        aTask.logger.lifecycle("Dummy documentation generated at: ${locGeneratedDocsRootFile.absolutePath}")
        aTask.logger.lifecycle("Publishable MPS artifact(s): ${locPublishableLines.size}")
        aTask.logger.lifecycle("Skipped non-publishable MPS artifact(s): ${locLines.size - locPublishableLines.size}")
    }

    private fun AIcToSha256Text(aText: String): String {
        val locDigest = MessageDigest.getInstance("SHA-256")
        val locHashBytes = locDigest.digest(aText.toByteArray(Charsets.UTF_8))
        return locHashBytes.joinToString("") { locByte -> "%02x".format(locByte) }
    }
}

tasks.register("validateAlgitesConfiguration") {
    group = "algites"
    description = "Validates minimal Algites source repository configuration."

    inputs.property("algitesDocsResolvedRepositoryName", locAlgitesDocsResolvedRepositoryName)
    inputs.property("algitesDocsResolvedArtifactDirectories", locAlgitesDocsResolvedArtifactDirectories.toString())

    doLast(
        AIcValidateAlgitesConfigurationAction(
            locAlgitesDocsResolvedRepositoryName,
            locProjectRootDirectory,
            locAlgitesDocsResolvedArtifactDirectories
        )
    )
}

tasks.register("discoverMpsArtifacts") {
    group = "algites"
    description = "Discovers MPS language/solution descriptors in configured artifact-set projects and derives Algites artifact identities."

    dependsOn("validateAlgitesConfiguration")

    inputs.property("algitesDocsResolvedRepositoryName", locAlgitesDocsResolvedRepositoryName)
    inputs.property("algitesDocsResolvedArtifactDirectories", locAlgitesDocsResolvedArtifactDirectories.toString())
    inputs.files(fileTree(locProjectRootDirectory) {
        include("**/*.mpl")
        include("**/*.msd")
        exclude("**/build/**")
        exclude("**/.gradle/**")
        exclude("**/classes_gen/**")
        exclude("**/source_gen/**")
        exclude("**/source_gen.caches/**")
    })
    outputs.file(locDiscoveryOutputFile)

    doLast(
        AIcDiscoverMpsArtifactsAction(
            locAlgitesDocsResolvedRepositoryName,
            locProjectRootDirectory,
            locAlgitesDocsResolvedArtifactDirectories,
            locDiscoveryOutputFile.get().asFile
        )
    )
}

tasks.register("printDiscoveredMpsArtifacts") {
    group = "algites"
    description = "Prints discovered MPS artifacts to the Gradle log."

    dependsOn("discoverMpsArtifacts")

    doLast(
        AIcPrintDiscoveredMpsArtifactsAction(
            locDiscoveryOutputFile.get().asFile
        )
    )
}

tasks.register("generateDummyMpsDocs") {
    group = "algites"
    description = "Generates dummy static documentation pages for discovered MPS artifacts."

    dependsOn("discoverMpsArtifacts")
    dependsOn("generateAlgitesDocsRootIndex")

    inputs.file(locDiscoveryOutputFile)
    outputs.dir(locGeneratedDocsRoot)

    doLast(
        AIcGenerateDummyMpsDocsAction(
            locAlgitesDocsResolvedRepositoryName,
            locDiscoveryOutputFile.get().asFile,
            locGeneratedDocsRoot.asFile
        )
    )
}

tasks.named("generateAlgitesDocsSite") {
    dependsOn("generateDummyMpsDocs")
}
