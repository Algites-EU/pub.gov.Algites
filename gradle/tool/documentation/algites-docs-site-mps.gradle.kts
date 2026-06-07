/*
 * Algites shared MPS documentation site script.
 *
 * Intended location in governance repository:
 *   gradle/tool/documentation/algites-docs-site-mps.gradle.kts
 *
 * A repository can apply only this script; it automatically applies the base
 * documentation-site script.
 */

import java.security.MessageDigest

val locAlgitesDocsBaseScript = (findProperty("algites.docs.baseScript") as String?)
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/algites-docs-site-base.gradle.kts"

apply(from = uri(locAlgitesDocsBaseScript))

data class AIcArtifactSetProject(
    val locRelativePath: String,
    val locProjectDirectory: File
)

data class AIcDiscoveredMpsArtifact(
    val locArtifactSetProjectPath: String,
    val locDescriptorPath: String,
    val locModuleName: String,
    val locModuleKind: String,
    val locModulePath: String,
    val locArtifactId: String,
    val locDocumentationPath: String,
    val locPublishable: Boolean
)

data class AIcMpsArtifactCandidate(
    val locArtifactSetProjectPath: String,
    val locDescriptorPath: String,
    val locModuleName: String,
    val locModuleKind: String,
    val locBaseModulePath: String,
    val locPublishable: Boolean
)

val locRepositoryConfigFile = layout.projectDirectory.file("algites-source-repository.yml")
val locDiscoveryOutputFile = layout.buildDirectory.file("algites/discovered-mps-artifacts.tsv")

fun AIcReadMpsDocsRepositoryId(): String {
    val locFile = layout.projectDirectory.file("algites-source-repository.yml").asFile
    require(locFile.isFile) {
        "Missing repository configuration file: ${locFile.absolutePath}"
    }

    val locMatchResult = Regex("""(?m)^\s*id\s*:\s*([^\s#]+)\s*$""").find(locFile.readText(Charsets.UTF_8))
    return requireNotNull(locMatchResult) {
        "Cannot find sourceRepository.id in: ${locFile.absolutePath}"
    }.groupValues[1].trim().removeSurrounding("\"").removeSurrounding("'")
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

fun String.AIcNormalizeYamlScalar(): String {
    return trim().removeSurrounding("\"").removeSurrounding("'")
}

fun AIcReadXmlAttribute(aXmlText: String, aAttributeName: String): String? {
    val locRegex = Regex("""\b${Regex.escape(aAttributeName)}\s*=\s*["']([^"']+)["']""")
    return locRegex.find(aXmlText)?.groupValues?.get(1)
}

fun AIcReadRepositoryId(): String {
    val locFile = locRepositoryConfigFile.asFile
    require(locFile.isFile) {
        "Missing repository configuration file: ${locFile.absolutePath}"
    }

    val locMatchResult = Regex("""(?m)^\s*id\s*:\s*([^\s#]+)\s*$""").find(locFile.readText(Charsets.UTF_8))
    return requireNotNull(locMatchResult) {
        "Cannot find sourceRepository.id in: ${locFile.absolutePath}"
    }.groupValues[1].AIcNormalizeYamlScalar()
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

fun AIcReadArtifactSetProjects(): List<AIcArtifactSetProject> {
    val locFile = locRepositoryConfigFile.asFile
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

    require(locRelativePaths.isNotEmpty()) {
        "Cannot find any contained artifact-set project paths in ${locFile.absolutePath}. Tried keys: ${locCandidateKeys.joinToString(", ")}"
    }

    return locRelativePaths.map { locRelativePath ->
        val locProjectDirectory = layout.projectDirectory.dir(locRelativePath).asFile
        require(locProjectDirectory.isDirectory) {
            "Configured artifact-set project directory does not exist: ${locProjectDirectory.absolutePath}"
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
            val locDescriptorPath = layout.projectDirectory.asFile.toPath()
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

tasks.register("validateAlgitesConfiguration") {
    group = "algites"
    description = "Validates minimal Algites source repository configuration."
    notCompatibleWithConfigurationCache("This legacy MPS discovery task still uses script helper functions at execution time.")

    inputs.file(locRepositoryConfigFile)

    doLast {
        val locRepositoryFile = locRepositoryConfigFile.asFile

        require(locRepositoryFile.isFile) {
            "Missing repository configuration file: ${locRepositoryFile.absolutePath}"
        }

        val locRepositoryId = AIcReadMpsDocsRepositoryId()
        val locArtifactSetProjects = AIcReadArtifactSetProjects()

        logger.lifecycle("Algites repository id: ${locRepositoryId}")
        logger.lifecycle("Configured artifact-set project(s):")
        locArtifactSetProjects.forEach { locArtifactSetProject ->
            logger.lifecycle(" - ${locArtifactSetProject.locRelativePath}")
        }
    }
}

tasks.register("discoverMpsArtifacts") {
    group = "algites"
    description = "Discovers MPS language/solution descriptors in configured artifact-set projects and derives Algites artifact identities."
    notCompatibleWithConfigurationCache("This legacy MPS discovery task still uses script helper functions at execution time.")

    dependsOn("validateAlgitesConfiguration")

    inputs.file(locRepositoryConfigFile)
    inputs.files(fileTree(layout.projectDirectory.asFile) {
        include("**/*.mpl")
        include("**/*.msd")
        exclude("**/build/**")
        exclude("**/.gradle/**")
        exclude("**/classes_gen/**")
        exclude("**/source_gen/**")
        exclude("**/source_gen.caches/**")
    })
    outputs.file(locDiscoveryOutputFile)

    doLast {
        val locRepositoryId = AIcReadMpsDocsRepositoryId()
        val locArtifactSetProjects = AIcReadArtifactSetProjects()
        val locArtifacts = AIcDiscoverMpsArtifacts(locRepositoryId, locArtifactSetProjects)
        val locOutputFile = locDiscoveryOutputFile.get().asFile

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

        logger.lifecycle("Discovered ${locArtifacts.size} MPS artifact(s).")
        logger.lifecycle("Discovery output: ${locOutputFile.absolutePath}")
    }
}

tasks.register("printDiscoveredMpsArtifacts") {
    group = "algites"
    description = "Prints discovered MPS artifacts to the Gradle log."
    notCompatibleWithConfigurationCache("This legacy MPS reporting task still uses script helper functions at execution time.")

    dependsOn("discoverMpsArtifacts")

    doLast {
        val locDiscoveryFile = locDiscoveryOutputFile.get().asFile
        logger.lifecycle(locDiscoveryFile.readText(Charsets.UTF_8))
    }
}

tasks.register("generateDummyMpsDocs") {
    group = "algites"
    description = "Generates dummy static documentation pages for discovered MPS artifacts."
    notCompatibleWithConfigurationCache("This legacy MPS documentation task still uses script helper functions at execution time.")

    dependsOn("discoverMpsArtifacts")
    dependsOn("generateAlgitesDocsRootIndex")

    inputs.file(locDiscoveryOutputFile)
    outputs.dir(locGeneratedDocsRoot)

    doLast {
        val locRepositoryId = AIcReadMpsDocsRepositoryId()
        val locDiscoveryFile = locDiscoveryOutputFile.get().asFile
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

            val locTargetDirectory = locGeneratedDocsRoot.dir("${locDocumentationPath}/latest").asFile
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

        val locIndexFile = locGeneratedDocsRoot.asFile.resolve("index.html")
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

        logger.lifecycle("Dummy documentation generated at: ${locGeneratedDocsRoot.asFile.absolutePath}")
        logger.lifecycle("Publishable MPS artifact(s): ${locPublishableLines.size}")
        logger.lifecycle("Skipped non-publishable MPS artifact(s): ${locLines.size - locPublishableLines.size}")
    }
}

tasks.named("generateAlgitesDocsSite") {
    dependsOn("generateDummyMpsDocs")
}
