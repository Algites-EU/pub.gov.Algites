/*
 * Algites generic documentation site script.
 *
 * Intended location in governance repository:
 *   gradle/tool/documentation/algites-docs-site.gradle.kts
 *
 * This is the public entry point. It applies the shared base script and then
 * applies technology-specific generators according to artifact/artifact-set type.
 */

val algitesDocsBaseScript = (findProperty("algites.docs.baseScript") as String?)
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/algites-docs-site-base.gradle.kts"

val algitesDocsJavaScript = (findProperty("algites.docs.javaScript") as String?)
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/algites-docs-site-java.gradle.kts"

val algitesDocsMpsScript = (findProperty("algites.docs.mpsScript") as String?)
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/algites-docs-site-mps.gradle.kts"

apply(from = uri(algitesDocsBaseScript))

val repositoryConfigFile = layout.projectDirectory.file("algites-source-repository.yml").asFile

fun String.AIcNormalizeYamlScalar(): String {
    return trim().removeSurrounding("\"").removeSurrounding("'")
}

fun AIcReadYamlScalar(aYamlText: String, aKey: String): String? {
    return Regex("""(?m)^\s*${Regex.escape(aKey)}\s*:\s*([^#\r\n]+)\s*(?:#.*)?$""")
        .find(aYamlText)
        ?.groupValues
        ?.get(1)
        ?.AIcNormalizeYamlScalar()
        ?.takeIf { it.isNotBlank() }
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

fun AIcReadArtifactSetProjectRelativePaths(): List<String> {
    if (!repositoryConfigFile.isFile) {
        return listOf(".")
    }

    val locYamlText = repositoryConfigFile.readText(Charsets.UTF_8)
    val locCandidateKeys = listOf(
        "containedArtifactSetProjectRelativePaths",
        "containedArtifactSetRelativePaths",
        "containedProjectRelativePaths",
        "containedArtifactRelativePaths"
    )

    return locCandidateKeys
        .asSequence()
        .map { locKey -> AIcReadYamlListAfterKey(locYamlText, locKey) }
        .firstOrNull { locValues -> locValues.isNotEmpty() }
        ?.ifEmpty { listOf(".") }
        ?: listOf(".")
}

fun AIcReadExplicitArtifactType(aProjectDirectory: File): String? {
    val locArtifactSetFile = aProjectDirectory.resolve("algites-artifact-set.yml")
    if (locArtifactSetFile.isFile) {
        return AIcReadYamlScalar(locArtifactSetFile.readText(Charsets.UTF_8), "type")?.lowercase()
    }

    val locArtifactFile = aProjectDirectory.resolve("algites-artifact.yml")
    if (locArtifactFile.isFile) {
        return AIcReadYamlScalar(locArtifactFile.readText(Charsets.UTF_8), "type")?.lowercase()
    }

    return null
}

fun AIcContainsFileWithExtension(aDirectory: File, aExtensions: Set<String>): Boolean {
    return aDirectory
        .walkTopDown()
        .onEnter { locFile -> locFile.name !in setOf("build", ".gradle", "classes_gen", "source_gen", "source_gen.caches", ".git") }
        .any { locFile -> locFile.isFile && locFile.extension.lowercase() in aExtensions }
}

fun AIcLooksLikeJavaProject(aDirectory: File): Boolean {
    return aDirectory.resolve("src/main/java").isDirectory ||
        aDirectory.resolve("src/main/kotlin").isDirectory ||
        AIcContainsFileWithExtension(aDirectory, setOf("java", "kt"))
}

fun AIcResolveArtifactType(aProjectDirectory: File): String {
    val locExplicitType = AIcReadExplicitArtifactType(aProjectDirectory)
    if (locExplicitType != null) {
        return when (locExplicitType) {
            "mps" -> "mps"
            "java", "jvm" -> "java"
            else -> {
                logger.warn("Unsupported artifact type '${locExplicitType}' in ${aProjectDirectory}; using Java documentation as default.")
                "java"
            }
        }
    }

    val locLooksLikeMps = AIcContainsFileWithExtension(aProjectDirectory, setOf("mpl", "msd"))
    val locLooksLikeJava = AIcLooksLikeJavaProject(aProjectDirectory)

    return when {
        locLooksLikeMps && !locLooksLikeJava -> "mps"
        locLooksLikeJava && !locLooksLikeMps -> "java"
        locLooksLikeMps && locLooksLikeJava -> {
            logger.warn("Artifact project '${aProjectDirectory}' looks like both MPS and Java; using Java documentation as default. Add algites-artifact-set.yml or algites-artifact.yml with type: mps or type: java to make this explicit.")
            "java"
        }
        else -> {
            logger.warn("Cannot determine artifact type for '${aProjectDirectory}'; using Java documentation as default. Add algites-artifact-set.yml or algites-artifact.yml with type: mps or type: java to make this explicit.")
            "java"
        }
    }
}

val resolvedArtifactTypes = AIcReadArtifactSetProjectRelativePaths()
    .map { locRelativePath -> layout.projectDirectory.dir(locRelativePath).asFile }
    .map { locProjectDirectory -> AIcResolveArtifactType(locProjectDirectory) }
    .toSet()

logger.lifecycle("Algites documentation artifact type resolution: ${resolvedArtifactTypes.sorted().joinToString(", ")}")

if ("java" in resolvedArtifactTypes) {
    apply(from = uri(algitesDocsJavaScript))
}

if ("mps" in resolvedArtifactTypes) {
    apply(from = uri(algitesDocsMpsScript))
}
