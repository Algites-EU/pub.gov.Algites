/*
 * Algites shared documentation site base script.
 *
 * Intended location in governance repository:
 *   gradle/tool/documentation/algites-docs-site-base.gradle.kts
 */

apply(plugin = "base")

val docsSiteRoot = file((findProperty("algites.docs.siteRoot") as String?) ?: "docs-site")
val generatedDocsRoot = File(docsSiteRoot, "generated")
val repositoryConfigFile = layout.projectDirectory.file("algites-source-repository.yml").asFile

val docsPublicationKind = ((findProperty("algites.docs.publicationKind") as String?) ?: "snapshot").trim().lowercase()
require(docsPublicationKind in setOf("preview", "snapshot", "release")) {
    "Invalid algites.docs.publicationKind='${docsPublicationKind}'. Allowed values are: preview, snapshot, release."
}

val docsPublicationIdRaw = ((findProperty("algites.docs.publicationId") as String?) ?: "local").trim().ifBlank { "local" }
val docsPublicationId = docsPublicationIdRaw
    .replace(Regex("[^A-Za-z0-9._-]+"), "-")
    .trim('-', '.', '_')
    .ifBlank { "local" }

val docsPublicationRoot = File(generatedDocsRoot, "${docsPublicationKind}/${docsPublicationId}")

extra["algitesDocsSiteRootPath"] = docsSiteRoot.path
extra["algitesGeneratedDocsRootPath"] = generatedDocsRoot.path
extra["algitesDocsPublicationKind"] = docsPublicationKind
extra["algitesDocsPublicationId"] = docsPublicationId
extra["algitesDocsPublicationRootPath"] = docsPublicationRoot.path

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

data class AIcRepositoryDocumentationMetadata(
    val id: String,
    val name: String,
    val description: String
)

fun AIcReadRepositoryDocumentationMetadata(): AIcRepositoryDocumentationMetadata {
    if (!repositoryConfigFile.isFile) {
        return AIcRepositoryDocumentationMetadata(
            id = rootProject.name,
            name = rootProject.name,
            description = ""
        )
    }

    val locYamlText = repositoryConfigFile.readText(Charsets.UTF_8)
    val locRepositoryId = AIcReadYamlScalar(locYamlText, "id") ?: rootProject.name
    val locRepositoryName = AIcReadYamlScalar(locYamlText, "name") ?: locRepositoryId
    val locRepositoryDescription = AIcReadYamlScalar(locYamlText, "description") ?: ""

    return AIcRepositoryDocumentationMetadata(
        id = locRepositoryId,
        name = locRepositoryName,
        description = locRepositoryDescription
    )
}

fun String.AIcEscapeHtml(): String {
    return replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}

fun File.AIcRelativeUrlFrom(aBaseDirectory: File): String {
    return aBaseDirectory.toPath().relativize(toPath()).toString().replace(File.separatorChar, '/')
}

tasks.register("generateAlgitesDocsRootIndex") {
    group = "algites"
    description = "Generates the stable root index page for the Algites documentation site."

    outputs.file(File(docsSiteRoot, "index.html"))

    doLast {
        val locMetadata = AIcReadRepositoryDocumentationMetadata()
        val locIndexFile = File(docsSiteRoot, "index.html")
        locIndexFile.parentFile.mkdirs()

        locIndexFile.writeText(
            """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <title>${locMetadata.name.AIcEscapeHtml()} Documentation</title>
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <style>
                body { margin: 0; font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; line-height: 1.5; color: #1f2937; background: #f9fafb; }
                header, main { max-width: 1200px; margin: 0 auto; padding: 1.5rem; }
                header { padding-top: 2rem; }
                h1 { margin: 0 0 0.5rem 0; font-size: 2rem; }
                .meta, .card { background: white; border: 1px solid #e5e7eb; border-radius: 0.75rem; padding: 1rem 1.25rem; margin: 1rem 0; box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04); }
                iframe { width: 100%; min-height: 70vh; border: 1px solid #e5e7eb; border-radius: 0.75rem; background: white; }
                a { color: #2563eb; }
                code { background: #f3f4f6; border-radius: 0.25rem; padding: 0.1rem 0.25rem; }
              </style>
            </head>
            <body>
              <header>
                <h1>${locMetadata.name.AIcEscapeHtml()}</h1>
                <div class="meta">
                  <p><strong>Repository ID:</strong> <code>${locMetadata.id.AIcEscapeHtml()}</code></p>
                  ${if (locMetadata.description.isNotBlank()) "<p>${locMetadata.description.AIcEscapeHtml()}</p>" else ""}
                  <p><strong>Current publication:</strong> <code>${docsPublicationKind.AIcEscapeHtml()}/${docsPublicationId.AIcEscapeHtml()}</code></p>
                </div>
              </header>

              <main>
                <section class="card">
                  <h2>Generated documentation</h2>
                  <p>
                    Open the generated documentation index directly:
                    <a href="generated/">generated/index.html</a>.
                  </p>
                </section>
                <iframe src="generated/" title="Generated documentation index"></iframe>
              </main>
            </body>
            </html>
            """.trimIndent(),
            Charsets.UTF_8
        )
    }
}

tasks.register("generateAlgitesGeneratedDocsIndex") {
    group = "algites"
    description = "Generates generated/index.html and publication-level indexes from the staged documentation tree."

    outputs.file(File(generatedDocsRoot, "index.html"))

    doLast {
        generatedDocsRoot.mkdirs()
        docsPublicationRoot.mkdirs()

        val locPublicationDirectories = listOf("preview", "snapshot", "release")
            .map { locKind -> locKind to File(generatedDocsRoot, locKind) }
            .filter { (_, locDirectory) -> locDirectory.isDirectory }

        locPublicationDirectories.forEach { (locKind, locKindDirectory) ->
            locKindDirectory.listFiles { locFile -> locFile.isDirectory }
                ?.sortedBy { locFile -> locFile.name }
                ?.forEach { locPublicationDirectory ->
                    val locArtifactIndexes = locPublicationDirectory
                        .walkTopDown()
                        .filter { locFile -> locFile.isFile && locFile.name == "index.html" && locFile.parentFile != locPublicationDirectory }
                        .map { locFile -> locFile.parentFile }
                        .distinct()
                        .sortedBy { locDirectory -> locDirectory.AIcRelativeUrlFrom(locPublicationDirectory) }
                        .toList()

                    val locPublicationIndexFile = File(locPublicationDirectory, "index.html")
                    locPublicationIndexFile.writeText(
                        """
                        <!doctype html>
                        <html lang="en">
                        <head>
                          <meta charset="utf-8">
                          <title>${locKind}/${locPublicationDirectory.name} Documentation</title>
                          <meta name="viewport" content="width=device-width, initial-scale=1">
                        </head>
                        <body>
                          <main>
                            <h1>${locKind.AIcEscapeHtml()}/${locPublicationDirectory.name.AIcEscapeHtml()}</h1>
                            <ul>
                        ${locArtifactIndexes.joinToString("\n") { locArtifactDirectory ->
                            val locUrl = locArtifactDirectory.AIcRelativeUrlFrom(locPublicationDirectory) + "/"
                            "      <li><a href=\"${locUrl.AIcEscapeHtml()}\">${locArtifactDirectory.AIcRelativeUrlFrom(locPublicationDirectory).AIcEscapeHtml()}</a></li>"
                        }}
                            </ul>
                          </main>
                        </body>
                        </html>
                        """.trimIndent(),
                        Charsets.UTF_8
                    )
                }
        }

        val locGeneratedIndexFile = File(generatedDocsRoot, "index.html")
        locGeneratedIndexFile.writeText(
            """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <title>Generated Documentation</title>
              <meta name="viewport" content="width=device-width, initial-scale=1">
            </head>
            <body>
              <main>
                <h1>Generated Documentation</h1>
                <h2>Publication sections</h2>
                <ul>
            ${locPublicationDirectories.joinToString("\n") { (locKind, locDirectory) ->
                "      <li><a href=\"${locKind}/\">${locKind.AIcEscapeHtml()}</a> (${locDirectory.listFiles { locFile -> locFile.isDirectory }?.size ?: 0})</li>"
            }}
                </ul>
                <h2>Current publication</h2>
                <p><a href="${docsPublicationKind}/${docsPublicationId}/">${docsPublicationKind.AIcEscapeHtml()}/${docsPublicationId.AIcEscapeHtml()}</a></p>
              </main>
            </body>
            </html>
            """.trimIndent(),
            Charsets.UTF_8
        )
    }
}

tasks.register("generateAlgitesDocsSite") {
    group = "algites"
    description = "Generic aggregate task for repository documentation site generation."

    dependsOn("generateAlgitesDocsRootIndex")
    dependsOn("generateAlgitesGeneratedDocsIndex")
}
