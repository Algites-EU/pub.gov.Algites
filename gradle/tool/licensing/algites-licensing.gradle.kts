/*
 * Algites hierarchical licensing resolver and repository materializer.
 *
 * Licensing is resolved from governance definitions/defaults and repository-local
 * subtree declarations. The root LICENSE and LICENSES/ directory are generated
 * artifacts derived from that model and MUST NOT be edited manually.
 */

import java.io.File
import java.net.URI
import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction


abstract class AIcMaterializeAlgitesDocumentationLicensesTask : DefaultTask() {
    @get:Input
    abstract val licenseIds: ListProperty<String>

    @get:InputDirectory
    abstract val sourceLicenseDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val targetLicenseDirectory: DirectoryProperty

    @get:OutputFile
    abstract val summaryFile: RegularFileProperty

    @TaskAction
    fun AIcMaterialize() {
        val locLicenseIds = licenseIds.get().sorted()
        val locSourceDirectory = sourceLicenseDirectory.get().asFile
        val locTargetDirectory = targetLicenseDirectory.get().asFile

        if (locTargetDirectory.exists()) locTargetDirectory.deleteRecursively()
        locTargetDirectory.mkdirs()

        locLicenseIds.forEach { locId ->
            val locSourceFile = File(locSourceDirectory, "$locId.txt")
            if (!locSourceFile.isFile) {
                throw GradleException("Missing materialized license text '${locSourceFile.path}'.")
            }
            locSourceFile.copyTo(File(locTargetDirectory, "$locId.txt"), overwrite = true)
        }

        val locSummaryFile = summaryFile.get().asFile
        locSummaryFile.parentFile.mkdirs()
        locSummaryFile.writeText(
            buildString {
                appendLine("Algites documentation licensing")
                appendLine("===============================")
                appendLine()
                appendLine("The generated documentation site contains material under the following effective DOCUMENTATION licenses:")
                if (locLicenseIds.isEmpty()) {
                    appendLine("  (none)")
                } else {
                    locLicenseIds.forEach { locId -> appendLine("  - $locId") }
                }
                appendLine()
                appendLine("Full license texts are available in LICENSES/.")
            },
            Charsets.UTF_8
        )
    }
}

data class AIcdAlgitesLicenseDefinition(
    val id: String,
    val name: String,
    val url: String?,
    val textFile: File,
    val source: String
)

data class AIcdAlgitesLicenseUsage(
    val id: String,
    val enabled: Boolean,
    val contentKinds: Set<String>?
) {
    fun AIcMerge(aOther: AIcdAlgitesLicenseUsage): AIcdAlgitesLicenseUsage {
        require(id == aOther.id) { "Cannot merge license usages with different ids '$id' and '${aOther.id}'." }
        return AIcdAlgitesLicenseUsage(
            id = id,
            enabled = aOther.enabled,
            contentKinds = aOther.contentKinds ?: contentKinds
        )
    }
}

data class AIcdAlgitesLicensingContext(
    val definitions: LinkedHashMap<String, AIcdAlgitesLicenseDefinition>,
    val usages: LinkedHashMap<String, AIcdAlgitesLicenseUsage>
)

abstract class AIcVerifyAlgitesLicensingTask : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:Input
    abstract val expectedFileHashes: MapProperty<String, String>

    @get:Input
    abstract val validationMode: Property<String>

    private fun AIcSha256(aBytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(aBytes)
            .joinToString("") { locByte -> "%02x".format(locByte.toInt() and 0xff) }

    @TaskAction
    fun AIcVerify() {
        val locRepositoryDirectory = repositoryDirectory.get().asFile
        val locExpectedFileHashes = expectedFileHashes.get()
        val locProblems = mutableListOf<String>()

        locExpectedFileHashes.forEach { (locPath, locExpectedHash) ->
            val locFile = File(locRepositoryDirectory, locPath)
            when {
                !locFile.isFile -> locProblems.add("Missing managed licensing file: $locPath")
                AIcSha256(locFile.readBytes()) != locExpectedHash ->
                    locProblems.add("Managed licensing file differs from effective licensing model: $locPath")
            }
        }

        val locCurrentManagedFiles = linkedSetOf<String>()
        val locRootLicense = File(locRepositoryDirectory, "LICENSE")
        if (locRootLicense.isFile) {
            locCurrentManagedFiles.add("LICENSE")
        }
        val locLicenseDirectory = File(locRepositoryDirectory, "LICENSES")
        if (locLicenseDirectory.isDirectory) {
            locLicenseDirectory.walkTopDown()
                .filter { locFile -> locFile.isFile }
                .forEach { locFile ->
                    locCurrentManagedFiles.add(
                        locRepositoryDirectory.toPath()
                            .relativize(locFile.toPath())
                            .toString()
                            .replace(File.separatorChar, '/')
                    )
                }
        }

        val locUnexpected = locCurrentManagedFiles - locExpectedFileHashes.keys
        locUnexpected.sorted().forEach { locPath ->
            locProblems.add("Stale managed licensing file: $locPath")
        }

        if (locProblems.isEmpty()) {
            println("Algites licensing materialization is consistent.")
            return
        }

        val locMode = validationMode.get()
        if (locMode == "warn") {
            logger.warn("Algites licensing validation warning (snapshot processing continues):")
            locProblems.forEach { locProblem -> logger.warn(" - $locProblem") }
            logger.warn("Run './gradlew rebuildAlgitesLicensing' and commit the resulting LICENSE/LICENSES changes.")
            return
        }

        throw GradleException(
            buildString {
                appendLine("Algites licensing materialization is out of date:")
                locProblems.forEach { locProblem -> appendLine(" - $locProblem") }
                append("Run './gradlew rebuildAlgitesLicensing' and commit the resulting LICENSE/LICENSES changes.")
            }
        )
    }
}

val AIcAlgitesSupportedLicenseContentKinds = linkedSetOf("product", "documentation")
val AIcAlgitesLicensingIgnoredDirectoryNames = setOf(
    ".git", ".gradle", ".idea", ".mps", "run", "build", "target", "out", "output",
    "docs-site", "documentation-branch", "gh-pages", "source_gen", "source_gen.caches", "classes_gen"
)

@Suppress("UNCHECKED_CAST")
val AIcAlgitesLicensingRepositoryMetadata = rootProject.extra["algitesResolvedRepositoryMetadata"] as Map<String, Any?>
val AIcAlgitesLicensingRepositoryId = AIcAlgitesLicensingRepositoryMetadata["id"]?.toString()?.trim().orEmpty()
val AIcAlgitesLicensingRepositoryVisibility = AIcAlgitesLicensingRepositoryMetadata["visibility"]?.toString()?.trim()?.lowercase().orEmpty()

fun AIcLicensingStripYamlComment(aLine: String): String {
    var locSingle = false
    var locDouble = false
    aLine.forEachIndexed { locIndex, locCharacter ->
        when (locCharacter) {
            '\'' -> if (!locDouble) locSingle = !locSingle
            '"' -> if (!locSingle) locDouble = !locDouble
            '#' -> if (!locSingle && !locDouble) return aLine.substring(0, locIndex)
        }
    }
    return aLine
}

fun AIcLicensingUnquoteYamlScalar(aValue: String): String {
    val locValue = aValue.trim()
    if (locValue.length >= 2) {
        if ((locValue.startsWith('"') && locValue.endsWith('"')) ||
            (locValue.startsWith('\'') && locValue.endsWith('\''))) {
            return locValue.substring(1, locValue.length - 1)
        }
    }
    return locValue
}

fun AIcLicensingParseYamlStringList(aValue: String): List<String> {
    val locTrimmed = aValue.trim()
    val locContent = if (locTrimmed.startsWith("[") && locTrimmed.endsWith("]")) {
        locTrimmed.substring(1, locTrimmed.length - 1)
    } else {
        locTrimmed
    }
    if (locContent.isBlank()) return emptyList()
    return locContent.split(',')
        .map { AIcLicensingUnquoteYamlScalar(it.trim()).lowercase() }
        .filter { it.isNotBlank() }
        .distinct()
}

fun AIcLicensingReadSimpleYamlScalars(aFile: File): Map<String, String> {
    val locValues = linkedMapOf<String, String>()
    val locStack = mutableListOf<Pair<Int, String>>()
    val locListCounters = mutableMapOf<String, Int>()

    aFile.readLines(Charsets.UTF_8).forEach { locOriginalLine ->
        val locLine = AIcLicensingStripYamlComment(locOriginalLine)
        if (locLine.isBlank()) return@forEach
        val locIndent = locLine.takeWhile { it == ' ' }.length
        val locTrimmed = locLine.trim()

        while (locStack.isNotEmpty() && locStack.last().first >= locIndent) {
            locStack.removeAt(locStack.lastIndex)
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
                if (locRawValue.isEmpty()) {
                    locStack.add((locIndent + 2) to locKey)
                } else {
                    locValues[locPath] = AIcLicensingUnquoteYamlScalar(locRawValue)
                }
            } else if (locParentPath.isNotBlank()) {
                val locItem = AIcLicensingUnquoteYamlScalar(locItemText)
                val locExisting = locValues[locParentPath]
                val locItems = if (locExisting == null) mutableListOf() else AIcLicensingParseYamlStringList(locExisting).toMutableList()
                locItems.add(locItem)
                locValues[locParentPath] = "[" + locItems.joinToString(", ") + "]"
            }
            return@forEach
        }

        val locSeparator = locTrimmed.indexOf(':')
        if (locSeparator <= 0) return@forEach
        val locKey = locTrimmed.substring(0, locSeparator).trim()
        val locRawValue = locTrimmed.substring(locSeparator + 1).trim()
        val locPath = (locStack.map { it.second } + locKey).joinToString(".")
        if (locRawValue.isEmpty()) {
            locStack.add(locIndent to locKey)
        } else {
            locValues[locPath] = AIcLicensingUnquoteYamlScalar(locRawValue)
        }
    }
    return locValues
}

fun AIcLicensingRelativePath(aDirectory: File): String {
    val locRelative = rootProject.projectDir.canonicalFile.toPath()
        .relativize(aDirectory.canonicalFile.toPath())
        .toString()
        .replace(File.separatorChar, '/')
    return locRelative.ifBlank { "." }
}

fun AIcLicensingPathSegments(aPath: String): List<String> {
    val locValue = aPath.trim().replace('\\', '/').trim('/')
    return if (locValue.isBlank() || locValue == ".") emptyList() else locValue.split('/').filter { it.isNotBlank() }
}

fun AIcLicensingDirectoryForPath(aPath: String): File {
    var locDirectory = rootProject.projectDir
    AIcLicensingPathSegments(aPath).forEach { locSegment -> locDirectory = locDirectory.resolve(locSegment) }
    return locDirectory
}

fun AIcLicensingDefinitions(aLicensingDirectory: File, aSourceLabel: String): List<AIcdAlgitesLicenseDefinition> {
    val locDefinitionsFile = aLicensingDirectory.resolve("license-definitions.yml")
    if (!locDefinitionsFile.isFile) return emptyList()
    val locValues = AIcLicensingReadSimpleYamlScalars(locDefinitionsFile)
    val locIndices = locValues.keys
        .mapNotNull { locKey -> Regex("^licenses\\.(\\d+)\\.id$").matchEntire(locKey)?.groupValues?.get(1)?.toIntOrNull() }
        .distinct()
        .sorted()

    val locResult = locIndices.map { locIndex ->
        val locPrefix = "licenses.$locIndex"
        val locId = locValues["$locPrefix.id"]?.trim()?.takeIf { it.isNotBlank() }
            ?: throw GradleException("License definition #$locIndex in '${locDefinitionsFile.path}' has no id.")
        if (!Regex("^[A-Za-z0-9.+-]+$").matches(locId)) {
            throw GradleException("License id '$locId' in '${locDefinitionsFile.path}' contains unsupported characters.")
        }
        val locName = locValues["$locPrefix.name"]?.trim()?.takeIf { it.isNotBlank() }
            ?: throw GradleException("License '$locId' in '${locDefinitionsFile.path}' has no name.")
        val locUrl = locValues["$locPrefix.url"]?.trim()?.takeIf { it.isNotBlank() }
        val locTextPath = locValues["$locPrefix.text"]?.trim()?.takeIf { it.isNotBlank() }
            ?: throw GradleException("License '$locId' in '${locDefinitionsFile.path}' has no text path.")
        val locTextFile = aLicensingDirectory.resolve(locTextPath).canonicalFile
        if (!locTextFile.isFile) {
            throw GradleException("License '$locId' in '${locDefinitionsFile.path}' references missing text file '${locTextFile.path}'.")
        }
        AIcdAlgitesLicenseDefinition(locId, locName, locUrl, locTextFile, aSourceLabel)
    }
    val locDuplicateIds = locResult.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys.sorted()
    if (locDuplicateIds.isNotEmpty()) {
        throw GradleException("Duplicate license id(s) in '${locDefinitionsFile.path}': ${locDuplicateIds.joinToString(", ")}.")
    }
    return locResult
}

fun AIcLicensingUsageFile(aFile: File): List<AIcdAlgitesLicenseUsage> {
    if (!aFile.isFile) return emptyList()
    val locValues = AIcLicensingReadSimpleYamlScalars(aFile)
    val locIndices = locValues.keys
        .mapNotNull { locKey -> Regex("^licenses\\.(\\d+)\\.id$").matchEntire(locKey)?.groupValues?.get(1)?.toIntOrNull() }
        .distinct()
        .sorted()

    val locResult = locIndices.map { locIndex ->
        val locPrefix = "licenses.$locIndex"
        val locId = locValues["$locPrefix.id"]?.trim()?.takeIf { it.isNotBlank() }
            ?: throw GradleException("License usage #$locIndex in '${aFile.path}' has no id.")
        val locEnabledRaw = locValues["$locPrefix.enabled"]?.trim()?.lowercase()
            ?: throw GradleException("License '$locId' in '${aFile.path}' must explicitly define enabled: true/false.")
        val locEnabled = locEnabledRaw.toBooleanStrictOrNull()
            ?: throw GradleException("License '$locId' in '${aFile.path}' has invalid enabled value '$locEnabledRaw'.")
        val locContentKinds = locValues["$locPrefix.contentKinds"]
            ?.let(::AIcLicensingParseYamlStringList)
            ?.toSet()
            ?.also { locKinds ->
                val locUnsupported = locKinds.filter { it !in AIcAlgitesSupportedLicenseContentKinds }
                if (locUnsupported.isNotEmpty()) {
                    throw GradleException(
                        "License '$locId' in '${aFile.path}' uses unsupported contentKinds: ${locUnsupported.joinToString(", ")}.")
                }
            }
        if (locContentKinds != null && locContentKinds.isEmpty()) {
            throw GradleException("License '$locId' in '${aFile.path}' defines an empty contentKinds list.")
        }
        AIcdAlgitesLicenseUsage(locId, locEnabled, locContentKinds)
    }
    val locDuplicateIds = locResult.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys.sorted()
    if (locDuplicateIds.isNotEmpty()) {
        throw GradleException("Duplicate license usage id(s) in '${aFile.path}': ${locDuplicateIds.joinToString(", ")}.")
    }
    return locResult
}

fun AIcLicensingDefinitionEquivalent(aLeft: AIcdAlgitesLicenseDefinition, aRight: AIcdAlgitesLicenseDefinition): Boolean {
    if (aLeft.name != aRight.name || aLeft.url != aRight.url) return false
    return aLeft.textFile.readBytes().contentEquals(aRight.textFile.readBytes())
}

fun AIcLicensingMergeDefinitions(
    aTarget: LinkedHashMap<String, AIcdAlgitesLicenseDefinition>,
    aDefinitions: List<AIcdAlgitesLicenseDefinition>
) {
    aDefinitions.forEach { locDefinition ->
        val locExisting = aTarget[locDefinition.id]
        if (locExisting == null) {
            aTarget[locDefinition.id] = locDefinition
        } else if (!AIcLicensingDefinitionEquivalent(locExisting, locDefinition)) {
            throw GradleException(
                "Conflicting definitions for license '${locDefinition.id}'. '${locExisting.source}' and '${locDefinition.source}' " +
                    "must use identical name, URL and license text. Use a new license id for a changed license version."
            )
        }
    }
}

fun AIcLicensingMergeUsages(
    aTarget: LinkedHashMap<String, AIcdAlgitesLicenseUsage>,
    aUsages: List<AIcdAlgitesLicenseUsage>
) {
    aUsages.forEach { locUsage ->
        val locExisting = aTarget[locUsage.id]
        aTarget[locUsage.id] = if (locExisting == null) locUsage else locExisting.AIcMerge(locUsage)
    }
}

const val AIcAlgitesPublicLicensingGovernanceBaseUrl =
    "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/licensing"

fun AIcLicensingDownloadRemoteFile(aRelativePath: String, aTargetFile: File) {
    val locUrl = "$AIcAlgitesPublicLicensingGovernanceBaseUrl/$aRelativePath"
    aTargetFile.parentFile?.mkdirs()
    try {
        val locBytes = URI(locUrl).toURL().openStream().use { locInput -> locInput.readBytes() }
        aTargetFile.writeBytes(locBytes)
    } catch (locException: Exception) {
        throw GradleException(
            "Algites public licensing governance file '$locUrl' is unavailable. " +
                "Set ALGITES_LICENSING_PUBLIC_GOVERNANCE_DIRECTORY to a local pub.gov.Algites/licensing directory to override the public GitHub fallback.",
            locException
        )
    }
}

fun AIcLicensingRemotePublicGovernanceDirectory(): File {
    val locGradleUserHome = System.getenv("GRADLE_USER_HOME")?.trim()?.takeIf { it.isNotBlank() }?.let(::File)
        ?: File(System.getProperty("user.home"), ".gradle")
    val locDirectory = locGradleUserHome.resolve("caches/algites/public-governance/licensing")
    locDirectory.mkdirs()

    val locDefinitionsFile = locDirectory.resolve("license-definitions.yml")
    val locDefaultsFile = locDirectory.resolve("defaults/license-usage.yml")
    AIcLicensingDownloadRemoteFile("license-definitions.yml", locDefinitionsFile)
    AIcLicensingDownloadRemoteFile("defaults/license-usage.yml", locDefaultsFile)

    val locDefinitionValues = AIcLicensingReadSimpleYamlScalars(locDefinitionsFile)
    locDefinitionValues.keys
        .mapNotNull { locKey -> Regex("^licenses\\.(\\d+)\\.text$").matchEntire(locKey)?.groupValues?.get(1)?.toIntOrNull() }
        .distinct()
        .sorted()
        .forEach { locIndex ->
            val locTextPath = locDefinitionValues["licenses.$locIndex.text"]?.trim()?.takeIf { it.isNotBlank() }
                ?: throw GradleException("License definition #$locIndex in remote public governance has no text path.")
            if (locTextPath.startsWith("/") || locTextPath.contains("..")) {
                throw GradleException("Remote public governance license text path '$locTextPath' is not a safe relative path.")
            }
            val locTargetFile = locDirectory.resolve(locTextPath).canonicalFile
            if (!locTargetFile.toPath().startsWith(locDirectory.canonicalFile.toPath())) {
                throw GradleException("Remote public governance license text path '$locTextPath' escapes the governance directory.")
            }
            AIcLicensingDownloadRemoteFile(locTextPath, locTargetFile)
        }

    return locDirectory.canonicalFile
}

fun AIcLicensingGovernanceDirectory(aKind: String): File? {
    val locEnvironmentName = when (aKind) {
        "public" -> "ALGITES_LICENSING_PUBLIC_GOVERNANCE_DIRECTORY"
        "private" -> "ALGITES_LICENSING_PRIVATE_GOVERNANCE_DIRECTORY"
        else -> error("Unsupported governance kind '$aKind'.")
    }
    val locEnvironmentDirectory = System.getenv(locEnvironmentName)?.trim()?.takeIf { it.isNotBlank() }?.let(::File)
    if (locEnvironmentDirectory != null) {
        if (!locEnvironmentDirectory.isDirectory) {
            throw GradleException("$locEnvironmentName points to missing directory '${locEnvironmentDirectory.path}'.")
        }
        return locEnvironmentDirectory.canonicalFile
    }

    val locLocalDirectory = rootProject.file("licensing")
    return when {
        aKind == "public" && AIcAlgitesLicensingRepositoryId == "pub.gov.Algites" && locLocalDirectory.resolve("license-definitions.yml").isFile -> locLocalDirectory.canonicalFile
        aKind == "private" && AIcAlgitesLicensingRepositoryId == "priv.gov.Algites" && locLocalDirectory.resolve("license-definitions.yml").isFile -> locLocalDirectory.canonicalFile
        aKind == "public" -> AIcLicensingRemotePublicGovernanceDirectory()
        else -> null
    }
}

val AIcAlgitesPublicLicensingGovernanceDirectory = AIcLicensingGovernanceDirectory("public")
    ?: throw GradleException("Algites public licensing governance is unavailable.")
val AIcAlgitesPrivateLicensingGovernanceDirectory = if (AIcAlgitesLicensingRepositoryVisibility == "priv") {
    AIcLicensingGovernanceDirectory("private")
        ?: throw GradleException(
            "Private repository '${AIcAlgitesLicensingRepositoryId}' requires Algites private licensing governance. " +
                "Set ALGITES_LICENSING_PRIVATE_GOVERNANCE_DIRECTORY to the priv.gov.Algites/licensing directory."
        )
} else {
    null
}

fun AIcLicensingInitialContext(): AIcdAlgitesLicensingContext {
    val locDefinitions = linkedMapOf<String, AIcdAlgitesLicenseDefinition>()
    val locUsages = linkedMapOf<String, AIcdAlgitesLicenseUsage>()

    AIcLicensingMergeDefinitions(
        locDefinitions,
        AIcLicensingDefinitions(AIcAlgitesPublicLicensingGovernanceDirectory, "public governance")
    )
    AIcLicensingMergeUsages(
        locUsages,
        AIcLicensingUsageFile(AIcAlgitesPublicLicensingGovernanceDirectory.resolve("defaults/license-usage.yml"))
    )

    AIcAlgitesPrivateLicensingGovernanceDirectory?.let { locPrivateDirectory ->
        AIcLicensingMergeDefinitions(
            locDefinitions,
            AIcLicensingDefinitions(locPrivateDirectory, "private governance")
        )
        AIcLicensingMergeUsages(
            locUsages,
            AIcLicensingUsageFile(locPrivateDirectory.resolve("defaults/license-usage.yml"))
        )
    }

    return AIcdAlgitesLicensingContext(locDefinitions, locUsages)
}

fun AIcLicensingValidateContext(aContext: AIcdAlgitesLicensingContext, aPath: String) {
    aContext.usages.values.filter { it.enabled }.forEach { locUsage ->
        if (aContext.definitions[locUsage.id] == null) {
            throw GradleException("License '${locUsage.id}' is enabled at '$aPath' but has no effective license definition.")
        }
        if (locUsage.contentKinds.isNullOrEmpty()) {
            throw GradleException(
                "License '${locUsage.id}' is enabled at '$aPath' but has no effective contentKinds. " +
                    "Define contentKinds when first enabling the license."
            )
        }
    }
}

fun AIcLicensingContextForPath(aPath: String, aIncludeCurrentUsage: Boolean = true): AIcdAlgitesLicensingContext {
    val locInitial = AIcLicensingInitialContext()
    val locDefinitions = LinkedHashMap(locInitial.definitions)
    val locUsages = LinkedHashMap(locInitial.usages)
    var locDirectory = rootProject.projectDir
    val locSegments = AIcLicensingPathSegments(aPath)
    val locDirectories = mutableListOf(rootProject.projectDir)
    locSegments.forEach { locSegment ->
        locDirectory = locDirectory.resolve(locSegment)
        locDirectories.add(locDirectory)
    }

    locDirectories.forEachIndexed { locIndex, locCurrentDirectory ->
        val locRelative = AIcLicensingRelativePath(locCurrentDirectory)
        val locDefinitionsDirectory = locCurrentDirectory.resolve("licensing")
        if (locDefinitionsDirectory.resolve("license-definitions.yml").isFile) {
            AIcLicensingMergeDefinitions(
                locDefinitions,
                AIcLicensingDefinitions(locDefinitionsDirectory, "repository licensing '$locRelative/licensing'")
            )
        }

        val locIsCurrent = locIndex == locDirectories.lastIndex
        val locUsageFile = locCurrentDirectory.resolve("license-usage.yml")
        if (locUsageFile.isFile && (!locIsCurrent || aIncludeCurrentUsage)) {
            AIcLicensingMergeUsages(locUsages, AIcLicensingUsageFile(locUsageFile))
        }
    }

    val locContext = AIcdAlgitesLicensingContext(locDefinitions, locUsages)
    AIcLicensingValidateContext(locContext, aPath.ifBlank { "." })
    return locContext
}

fun AIcLicensingActiveIdsByContentKind(aContext: AIcdAlgitesLicensingContext): Map<String, List<String>> {
    return AIcAlgitesSupportedLicenseContentKinds.associateWith { locKind ->
        aContext.usages.values
            .filter { locUsage -> locUsage.enabled && locUsage.contentKinds?.contains(locKind) == true }
            .map { it.id }
            .distinct()
            .sorted()
    }
}

fun AIcLicensingContextSignature(aContext: AIcdAlgitesLicensingContext): Map<String, List<String>> =
    AIcLicensingActiveIdsByContentKind(aContext)

fun AIcLicensingUsageFiles(): List<File> {
    val locFiles = mutableListOf<File>()
    rootProject.projectDir.walkTopDown()
        .onEnter { locDirectory ->
            locDirectory == rootProject.projectDir || locDirectory.name !in AIcAlgitesLicensingIgnoredDirectoryNames
        }
        .filter { locFile ->
            locFile.isFile && locFile.name == "license-usage.yml" &&
                !AIcLicensingPathSegments(AIcLicensingRelativePath(locFile.parentFile)).contains("licensing")
        }
        .forEach { locFiles.add(it) }
    return locFiles.sortedBy { AIcLicensingRelativePath(it.parentFile) }
}

@Suppress("UNCHECKED_CAST")
val AIcAlgitesLicensingArtifactDirectories = rootProject.extra["algitesResolvedArtifactDirectories"] as List<Map<String, Any?>>

fun AIcLicensingRelevantPaths(): List<String> {
    val locPaths = linkedSetOf(".")
    AIcLicensingUsageFiles().forEach { locFile -> locPaths.add(AIcLicensingRelativePath(locFile.parentFile)) }
    AIcAlgitesLicensingArtifactDirectories.forEach { locMetadata ->
        locMetadata["path"]?.toString()?.takeIf { it.isNotBlank() }?.let(locPaths::add)
    }
    return locPaths.sortedWith(compareBy<String>({ AIcLicensingPathSegments(it).size }, { it }))
}

fun AIcLicensingUsedDefinitions(): LinkedHashMap<String, AIcdAlgitesLicenseDefinition> {
    val locUsed = linkedMapOf<String, AIcdAlgitesLicenseDefinition>()
    AIcLicensingRelevantPaths().forEach { locPath ->
        val locContext = AIcLicensingContextForPath(locPath)
        locContext.usages.values.filter { it.enabled }.forEach { locUsage ->
            val locDefinition = locContext.definitions[locUsage.id]
                ?: throw GradleException("Enabled license '${locUsage.id}' at '$locPath' has no definition.")
            val locExisting = locUsed[locDefinition.id]
            if (locExisting == null) {
                locUsed[locDefinition.id] = locDefinition
            } else if (!AIcLicensingDefinitionEquivalent(locExisting, locDefinition)) {
                throw GradleException("License '${locDefinition.id}' resolves to different definitions in different repository subtrees.")
            }
        }
    }
    return LinkedHashMap(locUsed.toSortedMap())
}

fun AIcLicensingDisplayKind(aKind: String): String = aKind.uppercase()

fun AIcLicensingRenderLicenseSet(
    aBuilder: StringBuilder,
    aContext: AIcdAlgitesLicensingContext,
    aIndent: String = ""
) {
    val locByKind = AIcLicensingActiveIdsByContentKind(aContext)
    AIcAlgitesSupportedLicenseContentKinds.forEach { locKind ->
        aBuilder.appendLine("$aIndent${AIcLicensingDisplayKind(locKind)}:")
        val locIds = locByKind[locKind].orEmpty()
        if (locIds.isEmpty()) {
            aBuilder.appendLine("$aIndent  (none)")
        } else {
            locIds.forEach { locId ->
                val locDefinition = aContext.definitions.getValue(locId)
                aBuilder.appendLine("$aIndent  - ${locDefinition.name} ($locId)")
            }
        }
    }
}

fun AIcLicensingExpectedLicenseSummary(): String {
    val locRootContext = AIcLicensingContextForPath(".")
    return buildString {
        appendLine("Algites repository licensing summary")
        appendLine("===================================")
        appendLine()
        appendLine("This file is generated from Algites licensing governance and the hierarchical license-usage.yml declarations in this repository.")
        appendLine("Do not edit it manually. Run './gradlew rebuildAlgitesLicensing' after changing licensing declarations or definitions.")
        appendLine()
        appendLine("Repository root effective licensing")
        appendLine("-----------------------------------")
        AIcLicensingRenderLicenseSet(this, locRootContext)

        val locExceptions = AIcLicensingUsageFiles()
            .map { locFile -> AIcLicensingRelativePath(locFile.parentFile) }
            .filter { it != "." }
            .mapNotNull { locPath ->
                val locCurrent = AIcLicensingContextForPath(locPath)
                val locParentPath = AIcLicensingPathSegments(locPath).dropLast(1).joinToString("/").ifBlank { "." }
                val locParent = AIcLicensingContextForPath(locParentPath)
                if (AIcLicensingContextSignature(locCurrent) == AIcLicensingContextSignature(locParent)) null else locPath to locCurrent
            }

        if (locExceptions.isNotEmpty()) {
            appendLine()
            appendLine("Subtree exceptions")
            appendLine("------------------")
            locExceptions.forEach { (locPath, locContext) ->
                appendLine(locPath)
                AIcLicensingRenderLicenseSet(this, locContext, "  ")
                appendLine()
            }
        }

        appendLine()
        appendLine("Full license texts")
        appendLine("------------------")
        appendLine("The LICENSES/ directory contains the canonical text of every license that is enabled anywhere in this repository.")
        appendLine("LICENSES/ is managed by the Algites licensing tasks and must not be edited manually.")
        appendLine()
        appendLine("The machine-readable source of truth is the public/private governance licensing data together with repository-local licensing/license-definitions.yml and license-usage.yml files.")
    }.replace("\r\n", "\n")
}

fun AIcLicensingExpectedFiles(): Map<String, ByteArray> {
    val locExpected = linkedMapOf<String, ByteArray>()
    locExpected["LICENSE"] = AIcLicensingExpectedLicenseSummary().toByteArray(Charsets.UTF_8)
    AIcLicensingUsedDefinitions().forEach { (locId, locDefinition) ->
        locExpected["LICENSES/$locId.txt"] = locDefinition.textFile.readBytes()
    }
    return locExpected
}

fun AIcLicensingSha256(aBytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(aBytes)
        .joinToString("") { locByte -> "%02x".format(locByte.toInt() and 0xff) }

val AIcAlgitesLicensingExpectedFileHashes = AIcLicensingExpectedFiles()
    .mapValues { (_, locBytes) -> AIcLicensingSha256(locBytes) }

fun AIcLicensingCurrentManagedFiles(): Set<String> {
    val locFiles = linkedSetOf<String>()
    if (rootProject.file("LICENSE").isFile) locFiles.add("LICENSE")
    val locLicenseDirectory = rootProject.file("LICENSES")
    if (locLicenseDirectory.isDirectory) {
        locLicenseDirectory.walkTopDown().filter { it.isFile }.forEach { locFile ->
            locFiles.add(rootProject.projectDir.toPath().relativize(locFile.toPath()).toString().replace(File.separatorChar, '/'))
        }
    }
    return locFiles
}

fun AIcLicensingCheckRepositoryMaterialization(): List<String> {
    val locExpected = AIcLicensingExpectedFiles()
    val locProblems = mutableListOf<String>()

    locExpected.forEach { (locPath, locBytes) ->
        val locFile = rootProject.file(locPath)
        when {
            !locFile.isFile -> locProblems.add("Missing managed licensing file: $locPath")
            !locFile.readBytes().contentEquals(locBytes) -> locProblems.add("Managed licensing file differs from effective licensing model: $locPath")
        }
    }

    val locUnexpected = AIcLicensingCurrentManagedFiles() - locExpected.keys
    locUnexpected.sorted().forEach { locPath -> locProblems.add("Stale managed licensing file: $locPath") }
    return locProblems
}

val rebuildAlgitesLicensing = tasks.register("rebuildAlgitesLicensing") {
    group = "algites"
    description = "Rebuilds the root LICENSE summary and managed LICENSES/ directory from effective Algites licensing declarations."

    doLast {
        val locExpected = AIcLicensingExpectedFiles()
        val locLicenseDirectory = rootProject.file("LICENSES")
        if (locLicenseDirectory.exists()) locLicenseDirectory.deleteRecursively()

        locExpected.forEach { (locPath, locBytes) ->
            val locFile = rootProject.file(locPath)
            locFile.parentFile?.mkdirs()
            locFile.writeBytes(locBytes)
        }
        println("Rebuilt Algites licensing materialization with ${locExpected.keys.count { it.startsWith("LICENSES/") }} license text file(s).")
    }
}

fun AIcLicensingProblemsMessage(aProblems: List<String>): String =
    buildString {
        appendLine("Algites licensing materialization is out of date:")
        aProblems.forEach { locProblem -> appendLine(" - $locProblem") }
        append("Run './gradlew rebuildAlgitesLicensing' and commit the resulting LICENSE/LICENSES changes.")
    }

val AIcAlgitesLicensingValidationMode = providers.gradleProperty("algites.licensing.validationMode")
    .orNull
    ?.trim()
    ?.lowercase()
    ?.takeIf { it.isNotBlank() }
    ?: "strict"

if (AIcAlgitesLicensingValidationMode !in setOf("strict", "warn")) {
    throw GradleException(
        "Unsupported algites.licensing.validationMode '$AIcAlgitesLicensingValidationMode'. " +
            "Supported values: strict, warn."
    )
}

val checkAlgitesLicensing = tasks.register("checkAlgitesLicensing") {
    group = "verification"
    description = "Strictly checks root LICENSE/LICENSES against the effective hierarchical Algites licensing model."

    doLast {
        val locProblems = AIcLicensingCheckRepositoryMaterialization()
        if (locProblems.isNotEmpty()) {
            throw GradleException(AIcLicensingProblemsMessage(locProblems))
        }
        println("Algites licensing materialization is consistent.")
    }
}

val verifyAlgitesLicensing = tasks.register<AIcVerifyAlgitesLicensingTask>("verifyAlgitesLicensing") {
    group = "verification"
    description = "Checks Algites licensing for lifecycle processing; strict by default and warning-only when explicitly requested for snapshot processing."
    repositoryDirectory.set(rootProject.layout.projectDirectory)
    expectedFileHashes.set(AIcAlgitesLicensingExpectedFileHashes)
    validationMode.set(AIcAlgitesLicensingValidationMode)
}

tasks.matching {
    it.name == "build" || it.name == "check" || it.name == "algitesBuild" || it.name == "algitesPublish"
}.configureEach {
    dependsOn(verifyAlgitesLicensing)
}

val locAlgitesDocumentationLicenseIds = linkedSetOf<String>().also { locLicenseIds ->
    AIcLicensingRelevantPaths().forEach { locPath ->
        AIcLicensingActiveIdsByContentKind(AIcLicensingContextForPath(locPath))["documentation"]
            .orEmpty()
            .forEach(locLicenseIds::add)
    }
}.toList().sorted()

val locAlgitesDocumentationSiteRoot = rootProject.file(
    (rootProject.findProperty("algites.docs.siteRoot") as String?) ?: "docs-site"
)

val materializeAlgitesDocumentationLicenses = tasks.register<AIcMaterializeAlgitesDocumentationLicensesTask>("materializeAlgitesDocumentationLicenses") {
    group = "documentation"
    description = "Materializes all effective DOCUMENTATION license texts into the generated documentation site."
    dependsOn(verifyAlgitesLicensing)

    licenseIds.set(locAlgitesDocumentationLicenseIds)
    sourceLicenseDirectory.set(rootProject.layout.projectDirectory.dir("LICENSES"))
    targetLicenseDirectory.fileValue(File(locAlgitesDocumentationSiteRoot, "LICENSES"))
    summaryFile.fileValue(File(locAlgitesDocumentationSiteRoot, "LICENSE"))
}

tasks.matching { it.name == "generateAlgitesDocsSite" }.configureEach {
    dependsOn(materializeAlgitesDocumentationLicenses)
}

rootProject.extra["algitesLicensesForPathAndContentKind"] = { aPath: String, aContentKind: String ->
    val locKind = aContentKind.trim().lowercase()
    if (locKind !in AIcAlgitesSupportedLicenseContentKinds) {
        throw GradleException("Unsupported Algites license content kind '$aContentKind'.")
    }
    val locContext = AIcLicensingContextForPath(aPath)
    locContext.usages.values
        .filter { locUsage -> locUsage.enabled && locUsage.contentKinds?.contains(locKind) == true }
        .map { locUsage ->
            val locDefinition = locContext.definitions.getValue(locUsage.id)
            linkedMapOf<String, String?>(
                "id" to locDefinition.id,
                "name" to locDefinition.name,
                "url" to locDefinition.url,
                "file" to rootProject.file("LICENSES/${locDefinition.id}.txt").absolutePath
            )
        }
        .sortedBy { locDefinition -> locDefinition["id"] }
}

rootProject.extra["algitesLicensingRepositoryVisibility"] = AIcAlgitesLicensingRepositoryVisibility
rootProject.extra["algitesLicensingPublicGovernanceDirectory"] = AIcAlgitesPublicLicensingGovernanceDirectory.absolutePath
rootProject.extra["algitesLicensingPrivateGovernanceDirectory"] = AIcAlgitesPrivateLicensingGovernanceDirectory?.absolutePath
