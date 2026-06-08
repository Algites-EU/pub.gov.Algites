/*
 * Algites generic documentation site script.
 *
 * Intended location in governance repository:
 *   gradle/tool/documentation/algites-docs-site.gradle.kts
 *
 * This script is the public entry point for repository documentation generation.
 * It applies the common base script, detects artifact-set project documentation
 * type, and then applies the required technology-specific documentation scripts.
 */

val locAlgitesDocsBaseScript = (findProperty("algites.docs.baseScript") as String?)
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/algites-docs-site-base.gradle.kts"

val locAlgitesDocsJavaScript = (findProperty("algites.docs.javaScript") as String?)
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/algites-docs-site-java.gradle.kts"

val locAlgitesDocsMpsScript = (findProperty("algites.docs.mpsScript") as String?)
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/algites-docs-site-mps.gradle.kts"

apply(from = uri(locAlgitesDocsBaseScript))

data class AIcDocsArtifactSetProject(
    val locRelativePath: String,
    val locProjectDirectory: File,
    val locDocumentationType: String,
    val locDocumentationTypeReason: String
)

val locRepositoryConfigFile = layout.projectDirectory.file("algites-source-repository.yml")

fun String.AIcNormalizeYamlScalar(): String {
    return trim().removeSurrounding("\"").removeSurrounding("'")
}

fun AIcReadYamlListAfterKey(aYamlText: String, aKey: String): List<String> {
    val locLines = aYamlText.lines()
    val locResult = mutableListOf<String>()

    for (locIndex in locLines.indices) {
        val locLine = locLines[locIndex]
        val locMatch = Regex("""^(\s*)${Regex.escape(aKey)}\s*:\s*$""").find(locLine) ?: continue
        val locBaseIndent = locMatch.groupValues[1].length

        for (locSubIndex in locIndex + 1 until locLines.size) {
            val locSubLine = locLines[locSubIndex]

            if (locSubLine.isBlank()) {
                continue
            }

            val locIndent = locSubLine.takeWhile { it == ' ' }.length

            if (locIndent <= locBaseIndent) {
                break
            }

            val locItemMatch = Regex("""^\s*-\s*(.+?)\s*(?:#.*)?$""").find(locSubLine)
            if (locItemMatch != null) {
                locResult.add(locItemMatch.groupValues[1].AIcNormalizeYamlScalar())
            }
        }

        if (locResult.isNotEmpty()) {
            return locResult
        }
    }

    return emptyList()
}

fun AIcReadYamlScalarByKey(aYamlText: String, aKey: String): String? {
    return Regex("""(?m)^\s*${Regex.escape(aKey)}\s*:\s*([^#\r\n]+)\s*(?:#.*)?$""")
        .find(aYamlText)
        ?.groupValues
        ?.get(1)
        ?.AIcNormalizeYamlScalar()
        ?.takeIf { it.isNotBlank() }
}

fun AIcReadYamlScalarByPath(aYamlText: String, vararg aPath: String): String? {
    val locStack = mutableListOf<Pair<Int, String>>()

    aYamlText.lines().forEach { locRawLine ->
        val locLineWithoutComment = locRawLine.substringBefore("#")
        if (locLineWithoutComment.isBlank()) {
            return@forEach
        }

        val locIndent = locLineWithoutComment.takeWhile { it == ' ' }.length
        val locMatch = Regex("""^\s*([A-Za-z0-9_.-]+)\s*:\s*(.*?)\s*$""").find(locLineWithoutComment)
            ?: return@forEach

        val locKey = locMatch.groupValues[1]
        val locValue = locMatch.groupValues[2].AIcNormalizeYamlScalar()

        while (locStack.isNotEmpty() && locStack.last().first >= locIndent) {
            locStack.removeAt(locStack.size - 1)
        }

        val locCurrentPath = (locStack.map { it.second } + locKey)
        if (locCurrentPath == aPath.toList() && locValue.isNotBlank()) {
            return locValue
        }

        locStack.add(locIndent to locKey)
    }

    return null
}

fun AIcReadArtifactSetProjectRelativePaths(): List<String> {
    val locFile = locRepositoryConfigFile.asFile
    require(locFile.isFile) {
        "Missing repository configuration file: ${locFile.absolutePath}"
    }

    val locYamlText = locFile.readText(Charsets.UTF_8)
    val locCandidateKeys = listOf(
        "containedArtifactSetProjectRelativePaths",
        "containedArtifactSetRelativePaths",
        "containedProjectRelativePaths",
        "containedArtifactRelativePaths"
    )

    val locRelativePaths = locCandidateKeys
        .asSequence()
        .map { locKey -> AIcReadYamlListAfterKey(locYamlText, locKey) }
        .firstOrNull { locValues -> locValues.isNotEmpty() }
        ?: emptyList()

    return locRelativePaths.ifEmpty { listOf(".") }
}

fun AIcReadExplicitDocumentationType(aArtifactSetProjectDirectory: File): String? {
    val locConfigurationFiles = listOf(
        aArtifactSetProjectDirectory.resolve("algites-artifact-set.yml"),
        aArtifactSetProjectDirectory.resolve("algites-artifact-set.yaml"),
        aArtifactSetProjectDirectory.resolve("algites-artifact.yml"),
        aArtifactSetProjectDirectory.resolve("algites-artifact.yaml")
    )

    locConfigurationFiles
        .filter { locConfigurationFile -> locConfigurationFile.isFile }
        .forEach { locConfigurationFile ->
            val locYamlText = locConfigurationFile.readText(Charsets.UTF_8)
            val locDocumentationType =
                AIcReadYamlScalarByPath(locYamlText, "artifactSet", "type")
                    ?: AIcReadYamlScalarByPath(locYamlText, "artifact", "type")
                    ?: AIcReadYamlScalarByPath(locYamlText, "documentation", "type")
                    ?: AIcReadYamlScalarByPath(locYamlText, "artifactSet", "documentationType")
                    ?: AIcReadYamlScalarByPath(locYamlText, "artifact", "documentationType")
                    ?: AIcReadYamlScalarByKey(locYamlText, "documentationType")
                    ?: AIcReadYamlScalarByKey(locYamlText, "artifactType")
                    ?: AIcReadYamlScalarByKey(locYamlText, "type")

            if (locDocumentationType != null) {
                return locDocumentationType
            }
        }

    return null
}

fun AIcContainsFileWithExtension(aDirectory: File, aExtensions: Set<String>): Boolean {
    return aDirectory
        .walkTopDown()
        .onEnter { locFile ->
            locFile.name !in setOf("build", ".gradle", "classes_gen", "source_gen", "source_gen.caches", ".git")
        }
        .any { locFile -> locFile.isFile && locFile.extension.lowercase() in aExtensions }
}

fun AIcContainsJavaSource(aDirectory: File): Boolean {
    return aDirectory.resolve("src/main/java").isDirectory ||
        aDirectory.resolve("src/main/kotlin").isDirectory ||
        AIcContainsFileWithExtension(aDirectory, setOf("java", "kt"))
}

fun AIcResolveDocumentationType(aArtifactSetProjectDirectory: File): Pair<String, String> {
    val locExplicitType = AIcReadExplicitDocumentationType(aArtifactSetProjectDirectory)?.lowercase()

    if (locExplicitType != null) {
        return when (locExplicitType) {
            "mps" -> "mps" to "explicit algites-artifact-set.yml type"
            "java", "jvm" -> "java" to "explicit algites-artifact-set.yml type"
            else -> {
                logger.warn(
                    "Unsupported documentation type '${locExplicitType}' in ${aArtifactSetProjectDirectory.resolve("algites-artifact-set.yml").absolutePath}; using Java documentation as default."
                )
                "java" to "unsupported explicit type, defaulted to java"
            }
        }
    }

    val locLooksLikeMps = AIcContainsFileWithExtension(aArtifactSetProjectDirectory, setOf("mpl", "msd"))
    val locLooksLikeJava = AIcContainsJavaSource(aArtifactSetProjectDirectory)

    return when {
        locLooksLikeMps && !locLooksLikeJava -> "mps" to "detected MPS descriptor files"
        locLooksLikeJava && !locLooksLikeMps -> "java" to "detected Java/Kotlin source files"
        locLooksLikeMps && locLooksLikeJava -> {
            logger.warn(
                "Artifact-set project '${aArtifactSetProjectDirectory.relativeTo(layout.projectDirectory.asFile)}' looks like both MPS and Java; using Java documentation as default. Add algites-artifact-set.yml with type: mps or algites-artifact.yml with type: java to make this explicit."
            )
            "java" to "ambiguous detection, defaulted to java"
        }
        else -> {
            logger.warn(
                "Cannot determine documentation type for artifact-set project '${aArtifactSetProjectDirectory.relativeTo(layout.projectDirectory.asFile)}'; using Java documentation as default. Add algites-artifact-set.yml with type: mps or algites-artifact.yml with type: java to make this explicit."
            )
            "java" to "undetermined, defaulted to java"
        }
    }
}

fun AIcResolveDocsArtifactSetProjects(): List<AIcDocsArtifactSetProject> {
    return AIcReadArtifactSetProjectRelativePaths().map { locRelativePath ->
        val locProjectDirectory = layout.projectDirectory.dir(locRelativePath).asFile
        require(locProjectDirectory.isDirectory) {
            "Configured artifact-set project directory does not exist: ${locProjectDirectory.absolutePath}"
        }

        val (locDocumentationType, locDocumentationTypeReason) = AIcResolveDocumentationType(locProjectDirectory)
        AIcDocsArtifactSetProject(
            locRelativePath = locRelativePath,
            locProjectDirectory = locProjectDirectory,
            locDocumentationType = locDocumentationType,
            locDocumentationTypeReason = locDocumentationTypeReason
        )
    }
}

val locResolvedArtifactSetProjects = AIcResolveDocsArtifactSetProjects()
val locResolvedDocumentationTypes = locResolvedArtifactSetProjects.map { it.locDocumentationType }.toSet()

logger.lifecycle("Algites documentation artifact-set project resolution:")
locResolvedArtifactSetProjects.forEach { locArtifactSetProject ->
    logger.lifecycle(
        " - ${locArtifactSetProject.locRelativePath}: ${locArtifactSetProject.locDocumentationType} (${locArtifactSetProject.locDocumentationTypeReason})"
    )
}

if ("java" in locResolvedDocumentationTypes) {
    apply(from = uri(locAlgitesDocsJavaScript))
}

if ("mps" in locResolvedDocumentationTypes) {
    apply(from = uri(locAlgitesDocsMpsScript))
}
