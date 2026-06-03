/*
 * Algites shared MPS documentation site script.
 *
 * Intended location in governance repository:
 *   gradle/tool/documentation/algites-docs-site-mps.gradle.kts
 */

import java.security.MessageDigest

val algitesDocsBaseScript = (findProperty("algites.docs.baseScript") as String?)
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/algites-docs-site-base.gradle.kts"

apply(from = uri(algitesDocsBaseScript))

data class AIcArtifactSetProject(
    val relativePath: String,
    val projectDirectory: File
)

data class AIcMpsArtifactCandidate(
    val artifactSetProjectPath: String,
    val descriptorPath: String,
    val moduleName: String,
    val moduleKind: String,
    val baseModulePath: String,
    val publishable: Boolean
)

data class AIcDiscoveredMpsArtifact(
    val artifactSetProjectPath: String,
    val descriptorPath: String,
    val moduleName: String,
    val moduleKind: String,
    val modulePath: String,
    val artifactId: String,
    val documentationPath: String,
    val publishable: Boolean
)

val mpsRepositoryConfigFile = layout.projectDirectory.file("algites-source-repository.yml").asFile
val mpsDiscoveryOutputFile = layout.buildDirectory.file("algites/discovered-mps-artifacts.tsv")
val mpsPublicationRoot = file(extra["algitesDocsPublicationRootPath"] as String)

fun String.AIcNormalizeYamlScalar(): String {
    return trim().removeSurrounding("\"").removeSurrounding("'")
}

fun String.AIcToSha256Text(): String {
    val locDigest = MessageDigest.getInstance("SHA-256")
    return locDigest.digest(toByteArray(Charsets.UTF_8)).joinToString("") { locByte -> "%02x".format(locByte) }
}

fun AIcReadXmlAttribute(aXmlText: String, aAttributeName: String): String? {
    return Regex("""\b${Regex.escape(aAttributeName)}\s*=\s*["']([^"']+)["']""")
        .find(aXmlText)
        ?.groupValues
        ?.get(1)
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

fun AIcReadRepositoryIdForMpsDocs(): String {
    require(mpsRepositoryConfigFile.isFile) {
        "Missing repository configuration file: ${mpsRepositoryConfigFile.absolutePath}"
    }

    val locYamlText = mpsRepositoryConfigFile.readText(Charsets.UTF_8)
    return requireNotNull(Regex("""(?m)^\s*id\s*:\s*([^\s#]+)\s*$""").find(locYamlText)) {
        "Cannot find sourceRepository.id in: ${mpsRepositoryConfigFile.absolutePath}"
    }.groupValues[1].AIcNormalizeYamlScalar()
}

fun AIcReadRepositoryRoleForMpsDocs(): String {
    val locRepositoryId = AIcReadRepositoryIdForMpsDocs()
    val locSegments = locRepositoryId.split(".")
    require(locSegments.size >= 3) {
        "Repository id must follow <vis>.<role>.<BusinessName>[.<reposubname>]: ${locRepositoryId}"
    }
    return locSegments[1]
}

fun AIcReadArtifactSetProjectsForMpsDocs(): List<AIcArtifactSetProject> {
    require(mpsRepositoryConfigFile.isFile) {
        "Missing repository configuration file: ${mpsRepositoryConfigFile.absolutePath}"
    }

    val locYamlText = mpsRepositoryConfigFile.readText(Charsets.UTF_8)
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

    val locEffectiveRelativePaths = locRelativePaths.ifEmpty { listOf(".") }

    return locEffectiveRelativePaths.map { locRelativePath ->
        val locProjectDirectory = layout.projectDirectory.dir(locRelativePath).asFile
        require(locProjectDirectory.isDirectory) {
            "Configured artifact-set project directory does not exist: ${locProjectDirectory.absolutePath}"
        }
        AIcArtifactSetProject(
            relativePath = locRelativePath,
            projectDirectory = locProjectDirectory
        )
    }
}

fun AIcDeriveMpsModuleKind(aDescriptorFile: File): String? {
    return when (aDescriptorFile.extension.lowercase()) {
        "mpl" -> "lang"
        "msd" -> "sol"
        else -> null
    }
}

fun AIcReadMpsModuleName(aDescriptorFile: File): String? {
    val locXmlText = aDescriptorFile.readText(Charsets.UTF_8)
    return AIcReadXmlAttribute(locXmlText, "namespace")
        ?: AIcReadXmlAttribute(locXmlText, "name")
        ?: aDescriptorFile.nameWithoutExtension
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
    val locRoleIndex = locSegments.indexOf(aRepositoryRole)
    return if (locRoleIndex >= 0 && locRoleIndex < locSegments.lastIndex) {
        locSegments.drop(locRoleIndex + 1).joinToString(".")
    } else {
        aNameWithoutMpsPrefix
    }
}

fun AIcDeriveBaseMpsModulePath(aModuleName: String, aModuleKind: String, aRepositoryRole: String): String {
    val locNameWithoutMpsPrefix = AIcRemoveMpsTechnicalPrefix(aModuleName, aModuleKind)
    return AIcStripKnownDomainRolePrefix(locNameWithoutMpsPrefix, aRepositoryRole)
}

fun AIcIsPublishableMpsModule(aModuleName: String, aModulePath: String): Boolean {
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

fun AIcResolveMpsModulePathCollisions(aCandidates: List<AIcMpsArtifactCandidate>): List<Pair<AIcMpsArtifactCandidate, String>> {
    val locBasePathCounts = aCandidates.groupingBy { it.baseModulePath }.eachCount()
    val locResolvedCandidates = aCandidates.map { locCandidate ->
        val locResolvedModulePath = if ((locBasePathCounts[locCandidate.baseModulePath] ?: 0) > 1) {
            "${locCandidate.moduleKind}.${locCandidate.baseModulePath}"
        } else {
            locCandidate.baseModulePath
        }
        locCandidate to locResolvedModulePath
    }

    val locDuplicatedResolvedModulePaths = locResolvedCandidates.groupBy { it.second }.filterValues { it.size > 1 }
    require(locDuplicatedResolvedModulePaths.isEmpty()) {
        buildString {
            appendLine("Duplicate resolved MPS modulePath value(s) detected.")
            locDuplicatedResolvedModulePaths.forEach { (locModulePath, locConflictingCandidates) ->
                appendLine("Duplicate modulePath: ${locModulePath}")
                locConflictingCandidates.forEach { (locCandidate, _) ->
                    appendLine(" - ${locCandidate.descriptorPath} -> ${locCandidate.moduleName}")
                }
            }
        }
    }

    return locResolvedCandidates
}

fun AIcValidateDiscoveredMpsArtifactUniqueness(aArtifacts: List<AIcDiscoveredMpsArtifact>) {
    val locDuplicateModulePaths = aArtifacts.groupBy { it.modulePath }.filterValues { it.size > 1 }
    val locDuplicateArtifactIds = aArtifacts.groupBy { it.artifactId }.filterValues { it.size > 1 }
    val locDuplicateDocumentationPaths = aArtifacts.groupBy { it.documentationPath }.filterValues { it.size > 1 }

    require(locDuplicateModulePaths.isEmpty() && locDuplicateArtifactIds.isEmpty() && locDuplicateDocumentationPaths.isEmpty()) {
        "Duplicate discovered MPS artifact identity value(s) detected."
    }
}

fun AIcDiscoverMpsArtifacts(aRepositoryId: String, aArtifactSetProjects: List<AIcArtifactSetProject>): List<AIcDiscoveredMpsArtifact> {
    val locRepositoryRole = AIcReadRepositoryRoleForMpsDocs()
    val locCandidates = aArtifactSetProjects.flatMap { locArtifactSetProject ->
        locArtifactSetProject.projectDirectory
            .walkTopDown()
            .onEnter { locFile ->
                locFile.name !in setOf("build", ".gradle", "classes_gen", "source_gen", "source_gen.caches", ".git")
            }
            .filter { locFile -> locFile.isFile && locFile.extension.lowercase() in setOf("mpl", "msd") }
            .mapNotNull { locDescriptorFile ->
                val locModuleKind = AIcDeriveMpsModuleKind(locDescriptorFile) ?: return@mapNotNull null
                val locModuleName = AIcReadMpsModuleName(locDescriptorFile) ?: return@mapNotNull null
                val locBaseModulePath = AIcDeriveBaseMpsModulePath(locModuleName, locModuleKind, locRepositoryRole)
                val locDescriptorPath = layout.projectDirectory.asFile.toPath()
                    .relativize(locDescriptorFile.toPath())
                    .toString()
                    .replace(File.separatorChar, '/')

                AIcMpsArtifactCandidate(
                    artifactSetProjectPath = locArtifactSetProject.relativePath,
                    descriptorPath = locDescriptorPath,
                    moduleName = locModuleName,
                    moduleKind = locModuleKind,
                    baseModulePath = locBaseModulePath,
                    publishable = AIcIsPublishableMpsModule(locModuleName, locBaseModulePath)
                )
            }
            .toList()
    }.distinctBy { it.moduleName }

    val locDiscoveredArtifacts = AIcResolveMpsModulePathCollisions(locCandidates).map { (locCandidate, locModulePath) ->
        AIcDiscoveredMpsArtifact(
            artifactSetProjectPath = locCandidate.artifactSetProjectPath,
            descriptorPath = locCandidate.descriptorPath,
            moduleName = locCandidate.moduleName,
            moduleKind = locCandidate.moduleKind,
            modulePath = locModulePath,
            artifactId = "${aRepositoryId}_${locModulePath}",
            documentationPath = locModulePath,
            publishable = locCandidate.publishable
        )
    }.sortedWith(compareBy<AIcDiscoveredMpsArtifact> { it.artifactSetProjectPath }.thenBy { it.moduleKind }.thenBy { it.modulePath })

    AIcValidateDiscoveredMpsArtifactUniqueness(locDiscoveredArtifacts)
    return locDiscoveredArtifacts
}

tasks.register("validateAlgitesMpsDocsConfiguration") {
    group = "algites"
    description = "Validates minimal Algites MPS documentation configuration."

    inputs.file(mpsRepositoryConfigFile)

    doLast {
        val locRepositoryId = AIcReadRepositoryIdForMpsDocs()
        val locArtifactSetProjects = AIcReadArtifactSetProjectsForMpsDocs()

        logger.lifecycle("Algites repository id: ${locRepositoryId}")
        logger.lifecycle("Configured artifact-set project(s):")
        locArtifactSetProjects.forEach { locArtifactSetProject ->
            logger.lifecycle(" - ${locArtifactSetProject.relativePath}")
        }
    }
}

tasks.register("discoverMpsArtifacts") {
    group = "algites"
    description = "Discovers MPS language/solution descriptors and derives Algites artifact identities."

    dependsOn("validateAlgitesMpsDocsConfiguration")
    inputs.file(mpsRepositoryConfigFile)
    outputs.file(mpsDiscoveryOutputFile)

    doLast {
        val locRepositoryId = AIcReadRepositoryIdForMpsDocs()
        val locArtifactSetProjects = AIcReadArtifactSetProjectsForMpsDocs()
        val locArtifacts = AIcDiscoverMpsArtifacts(locRepositoryId, locArtifactSetProjects)
        val locOutputFile = mpsDiscoveryOutputFile.get().asFile

        locOutputFile.parentFile.mkdirs()
        locOutputFile.writeText(
            buildString {
                appendLine("artifactSetProjectPath\tdescriptorPath\tmoduleKind\tmodulePath\tartifactId\tdocumentationPath\tpublishable\tmoduleName")
                locArtifacts.forEach { locArtifact ->
                    appendLine(
                        listOf(
                            locArtifact.artifactSetProjectPath,
                            locArtifact.descriptorPath,
                            locArtifact.moduleKind,
                            locArtifact.modulePath,
                            locArtifact.artifactId,
                            locArtifact.documentationPath,
                            locArtifact.publishable.toString(),
                            locArtifact.moduleName
                        ).joinToString("\t")
                    )
                }
            },
            Charsets.UTF_8
        )

        logger.lifecycle("Discovered ${locArtifacts.size} MPS artifact(s).")
        logger.lifecycle("Discovery output: ${locOutputFile.absolutePath}")
    }
}

tasks.register("printDiscoveredMpsArtifacts") {
    group = "algites"
    description = "Prints discovered MPS artifacts to the Gradle log."

    dependsOn("discoverMpsArtifacts")

    doLast {
        logger.lifecycle(mpsDiscoveryOutputFile.get().asFile.readText(Charsets.UTF_8))
    }
}

tasks.register("generateMpsDocsSite") {
    group = "algites"
    description = "Generates placeholder static documentation pages for discovered MPS artifacts."

    dependsOn("discoverMpsArtifacts")
    inputs.file(mpsDiscoveryOutputFile)
    outputs.dir(mpsPublicationRoot)

    doLast {
        val locDiscoveryFile = mpsDiscoveryOutputFile.get().asFile
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
            val locContentHash = locLine.AIcToSha256Text()

            val locTargetDirectory = File(mpsPublicationRoot, locDocumentationPath)
            locTargetDirectory.deleteRecursively()
            locTargetDirectory.mkdirs()

            File(locTargetDirectory, "index.html").writeText(
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

        logger.lifecycle("MPS documentation staged at: ${mpsPublicationRoot.absolutePath}")
        logger.lifecycle("Publishable MPS artifact(s): ${locPublishableLines.size}")
        logger.lifecycle("Skipped non-publishable MPS artifact(s): ${locLines.size - locPublishableLines.size}")
    }
}

tasks.register("generateDummyMpsDocs") {
    group = "algites"
    description = "Compatibility alias for generateMpsDocsSite."
    dependsOn("generateMpsDocsSite")
}

tasks.named("generateAlgitesGeneratedDocsIndex") {
    mustRunAfter("generateMpsDocsSite")
}

tasks.named("generateAlgitesDocsSite") {
    dependsOn("generateMpsDocsSite")
}
