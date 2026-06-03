/*
 * Algites shared documentation site base script.
 *
 * Intended location in governance repository:
 *   gradle/tool/documentation/algites-docs-site-base.gradle.kts
 *
 * This script defines common documentation-site conventions, publication
 * parameters, and generated documentation indexes. Technology-specific scripts
 * must generate only artifact directories under the active publication root.
 */

if ((extra.properties["algitesDocsBaseApplied"] as Boolean?) != true) {
    extra["algitesDocsBaseApplied"] = true

    apply(plugin = "base")

    val docsSiteRootDirectory = layout.projectDirectory.dir(
        (findProperty("algites.docs.siteRoot") as String?) ?: "docs-site"
    )

    val generatedDocsRootDirectory = layout.projectDirectory.dir(
        (findProperty("algites.docs.generatedRoot") as String?) ?: "docs-site/generated"
    )

    val repositoryConfigFile = layout.projectDirectory.file("algites-source-repository.yml")

    val docsPublicationKind = ((findProperty("algites.docs.publicationKind") as String?) ?: "snapshot")
        .trim()
        .lowercase()

    require(docsPublicationKind in setOf("preview", "snapshot", "release")) {
        "Invalid algites.docs.publicationKind='${docsPublicationKind}'. Allowed values are: preview, snapshot, release."
    }

    fun String.AIcSanitizeDocumentationPathSegment(): String {
        val locTrimmedValue = trim()
        val locSanitizedValue = locTrimmedValue
            .replace(Regex("""[^A-Za-z0-9._-]+"""), "-")
            .trim('-', '.', '_')
        return locSanitizedValue.ifBlank { "local" }
    }

    val docsPublicationId = ((findProperty("algites.docs.publicationId") as String?) ?: "local")
        .AIcSanitizeDocumentationPathSegment()

    val publicationRootDirectory = generatedDocsRootDirectory.dir("${docsPublicationKind}/${docsPublicationId}")

    extra["algitesDocsSiteRootPath"] = docsSiteRootDirectory.asFile.path
    extra["algitesGeneratedDocsRootPath"] = generatedDocsRootDirectory.asFile.path
    extra["algitesDocsPublicationKind"] = docsPublicationKind
    extra["algitesDocsPublicationId"] = docsPublicationId
    extra["algitesDocsPublicationRootPath"] = publicationRootDirectory.asFile.path

    fun String.AIcNormalizeYamlScalar(): String {
        return trim().removeSurrounding("\"").removeSurrounding("'")
    }

    fun AIcReadRepositoryId(): String {
        val locFile = repositoryConfigFile.asFile
        if (!locFile.isFile) {
            return rootProject.name
        }

        val locMatchResult = Regex("""(?m)^\s*id\s*:\s*([^\s#]+)\s*$""").find(locFile.readText(Charsets.UTF_8))
        return locMatchResult?.groupValues?.get(1)?.AIcNormalizeYamlScalar() ?: rootProject.name
    }

    fun AIcReadGeneratedArtifactTitle(aArtifactDirectory: File): String {
        val locMetadataFile = aArtifactDirectory.resolve(".algites-docs-artifact.properties")
        if (locMetadataFile.isFile) {
            val locMetadata = java.util.Properties()
            locMetadataFile.inputStream().use { locInputStream -> locMetadata.load(locInputStream) }
            val locTitle = locMetadata.getProperty("title")
            if (!locTitle.isNullOrBlank()) {
                return locTitle
            }
        }
        return aArtifactDirectory.name
    }

    fun AIcReadGeneratedArtifactType(aArtifactDirectory: File): String? {
        val locMetadataFile = aArtifactDirectory.resolve(".algites-docs-artifact.properties")
        if (!locMetadataFile.isFile) {
            return null
        }
        val locMetadata = java.util.Properties()
        locMetadataFile.inputStream().use { locInputStream -> locMetadata.load(locInputStream) }
        return locMetadata.getProperty("type")?.takeIf { locValue -> locValue.isNotBlank() }
    }

    fun AIcRelativeHref(aBaseDirectory: File, aTargetFile: File): String {
        return aBaseDirectory.toPath()
            .relativize(aTargetFile.toPath())
            .toString()
            .replace(File.separatorChar, '/')
    }

    tasks.register("generateAlgitesDocsRootIndex") {
        group = "algites"
        description = "Generates the stable root index page for the Algites documentation site."

        outputs.file(docsSiteRootDirectory.file("index.html"))

        doLast {
            val locRepositoryId = AIcReadRepositoryId()
            val locIndexFile = docsSiteRootDirectory.file("index.html").asFile
            locIndexFile.parentFile.mkdirs()

            if (!locIndexFile.exists() || (findProperty("algites.docs.overwriteRootIndex") == "true")) {
                locIndexFile.writeText(
                    """
                    <!doctype html>
                    <html lang="en">
                    <head>
                      <meta charset="utf-8">
                      <title>${locRepositoryId} Documentation</title>
                      <meta name="viewport" content="width=device-width, initial-scale=1">
                    </head>
                    <body>
                      <main>
                        <h1>${locRepositoryId} Documentation</h1>
                        <p>This is the stable repository documentation entry point.</p>
                        <p><a href="generated/">Open generated documentation</a></p>
                      </main>
                    </body>
                    </html>
                    """.trimIndent(),
                    Charsets.UTF_8
                )
            } else {
                logger.lifecycle("Keeping existing manual documentation root index: ${locIndexFile.absolutePath}")
            }
        }
    }

    tasks.register("generateAlgitesDocsPublicationIndex") {
        group = "algites"
        description = "Generates the index for the active Algites documentation publication."

        outputs.file(publicationRootDirectory.file("index.html"))

        doLast {
            val locRepositoryId = AIcReadRepositoryId()
            val locPublicationRoot = publicationRootDirectory.asFile
            locPublicationRoot.mkdirs()

            val locArtifactDirectories = locPublicationRoot
                .walkTopDown()
                .maxDepth(2)
                .filter { locFile -> locFile.isDirectory }
                .filter { locFile -> locFile != locPublicationRoot }
                .filter { locFile -> locFile.resolve("index.html").isFile }
                .sortedBy { locFile -> locFile.relativeTo(locPublicationRoot).path.replace(File.separatorChar, '/') }
                .toList()

            val locItemsHtml = if (locArtifactDirectories.isEmpty()) {
                "      <li>No generated artifact documentation was found for this publication.</li>"
            } else {
                locArtifactDirectories.joinToString("\n") { locDirectory ->
                    val locHref = AIcRelativeHref(locPublicationRoot, locDirectory.resolve("index.html"))
                    val locTitle = AIcReadGeneratedArtifactTitle(locDirectory)
                    val locType = AIcReadGeneratedArtifactType(locDirectory)?.let { locValue -> " <small>(${locValue})</small>" } ?: ""
                    "      <li><a href=\"${locHref}\">${locTitle}</a>${locType}</li>"
                }
            }

            publicationRootDirectory.file("index.html").asFile.writeText(
                """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>${locRepositoryId} ${docsPublicationKind} ${docsPublicationId} Documentation</title>
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                </head>
                <body>
                  <main>
                    <h1>${locRepositoryId} Documentation</h1>
                    <dl>
                      <dt>Publication kind</dt>
                      <dd>${docsPublicationKind}</dd>
                      <dt>Publication ID</dt>
                      <dd>${docsPublicationId}</dd>
                    </dl>
                    <h2>Generated artifacts</h2>
                    <ul>
                ${locItemsHtml}
                    </ul>
                    <p><a href="../../index.html">All generated publications</a></p>
                  </main>
                </body>
                </html>
                """.trimIndent(),
                Charsets.UTF_8
            )
        }
    }

    tasks.register("generateAlgitesDocsGeneratedIndex") {
        group = "algites"
        description = "Generates the top-level generated documentation index."

        dependsOn("generateAlgitesDocsPublicationIndex")

        outputs.file(generatedDocsRootDirectory.file("index.html"))

        doLast {
            val locRepositoryId = AIcReadRepositoryId()
            val locGeneratedRoot = generatedDocsRootDirectory.asFile
            locGeneratedRoot.mkdirs()

            val locPublicationDirectories = locGeneratedRoot
                .walkTopDown()
                .maxDepth(3)
                .filter { locFile -> locFile.isDirectory }
                .filter { locFile -> locFile != locGeneratedRoot }
                .filter { locFile -> locFile.resolve("index.html").isFile }
                .filter { locFile -> locFile.parentFile.parentFile == locGeneratedRoot }
                .sortedBy { locFile -> locFile.relativeTo(locGeneratedRoot).path.replace(File.separatorChar, '/') }
                .toList()

            val locItemsHtml = if (locPublicationDirectories.isEmpty()) {
                "      <li>No generated documentation publications were found.</li>"
            } else {
                locPublicationDirectories.joinToString("\n") { locDirectory ->
                    val locRelativePath = locDirectory.relativeTo(locGeneratedRoot).path.replace(File.separatorChar, '/')
                    "      <li><a href=\"${locRelativePath}/\">${locRelativePath}</a></li>"
                }
            }

            generatedDocsRootDirectory.file("index.html").asFile.writeText(
                """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>Generated ${locRepositoryId} Documentation</title>
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                </head>
                <body>
                  <main>
                    <h1>Generated ${locRepositoryId} Documentation</h1>
                    <ul>
                ${locItemsHtml}
                    </ul>
                  </main>
                </body>
                </html>
                """.trimIndent(),
                Charsets.UTF_8
            )
        }
    }

    tasks.register("generateAlgitesDocsIndexes") {
        group = "algites"
        description = "Generates all common Algites documentation indexes."

        dependsOn("generateAlgitesDocsRootIndex")
        dependsOn("generateAlgitesDocsGeneratedIndex")
    }

    tasks.register("generateAlgitesDocsSite") {
        group = "algites"
        description = "Generic aggregate task for repository documentation site generation."

        dependsOn("generateAlgitesDocsIndexes")
    }
}
