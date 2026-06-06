/*
 * Algites shared documentation site base script.
 *
 * Intended location in governance repository:
 *   gradle/tool/documentation/algites-docs-site-base.gradle.kts
 *
 * This script defines common documentation-site conventions and a stable root
 * index. Technology-specific scripts should apply this script automatically.
 */

apply(plugin = "base")

val locDocsSiteRoot = layout.projectDirectory.dir(
    (findProperty("algites.docs.siteRoot") as String?) ?: "docs-site"
)

/*
 * Generated documentation is always placed below the documentation site root.
 * Technology-specific scripts should generate into locPublicationDocsRoot, not
 * directly into locGeneratedDocsRoot.
 */
val locGeneratedDocsRoot = locDocsSiteRoot.dir("generated")

val locPublicationKind = (findProperty("algites.docs.publicationKind") as String?)
    ?.trim()
    ?.takeIf { it.isNotBlank() }

val locPublicationId = (findProperty("algites.docs.publicationId") as String?)
    ?.trim()
    ?.takeIf { it.isNotBlank() }

val locPublicationDocsRoot = if (locPublicationKind != null && locPublicationId != null) {
    locGeneratedDocsRoot.dir("${locPublicationKind}/${locPublicationId}")
} else {
    locGeneratedDocsRoot
}

val locRepositoryConfigFile = layout.projectDirectory.file("algites-source-repository.yml")

extra["algitesDocsSiteRootPath"] = locDocsSiteRoot.asFile.path
extra["algitesGeneratedDocsRootPath"] = locGeneratedDocsRoot.asFile.path
extra["algitesPublicationDocsRootPath"] = locPublicationDocsRoot.asFile.path

fun String.AIcDocsNormalizeYamlScalar(): String {
    return trim().removeSurrounding("\"").removeSurrounding("'")
}

fun String.AIcDocsHtmlEscape(): String {
    return replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}

fun AIcDocsReadRepositoryScalar(aKey: String): String? {
    val locFile = locRepositoryConfigFile.asFile
    if (!locFile.isFile) {
        return null
    }

    val locYamlText = locFile.readText(Charsets.UTF_8)
    return Regex("""(?m)^\s*${Regex.escape(aKey)}\s*:\s*(.+?)\s*(?:#.*)?$""")
        .find(locYamlText)
        ?.groupValues
        ?.get(1)
        ?.AIcDocsNormalizeYamlScalar()
        ?.takeIf { it.isNotBlank() }
}

fun AIcDocsRelativeHref(aBaseDirectory: File, aTargetDirectory: File): String {
    val locRelativePath = aBaseDirectory.toPath()
        .relativize(aTargetDirectory.toPath())
        .toString()
        .replace(File.separatorChar, '/')
        .trim('/')

    return if (locRelativePath.isBlank()) {
        "./"
    } else {
        "${locRelativePath}/"
    }
}

if (tasks.findByName("generateAlgitesDocsRootIndex") == null) {
    tasks.register("generateAlgitesDocsRootIndex") {
        group = "algites"
        description = "Generates the stable root index page for the Algites documentation site."

        outputs.file(locDocsSiteRoot.file("index.html"))

        doLast {
            val locRepositoryId = AIcDocsReadRepositoryScalar("id") ?: rootProject.name
            val locRepositoryName = AIcDocsReadRepositoryScalar("name") ?: locRepositoryId
            val locRepositoryDescription = AIcDocsReadRepositoryScalar("description")

            val locPublicationKind = (findProperty("algites.docs.publicationKind") as String?)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val locPublicationId = (findProperty("algites.docs.publicationId") as String?)
                ?.trim()
                ?.takeIf { it.isNotBlank() }

            val locCurrentPublicationDirectory = locPublicationDocsRoot.asFile

            val locCurrentPublicationHref = AIcDocsRelativeHref(
                locDocsSiteRoot.asFile,
                locCurrentPublicationDirectory
            )

            val locIndexFile = locDocsSiteRoot.file("index.html").asFile
            locIndexFile.parentFile.mkdirs()

            if (findProperty("algites.docs.keepManualRootIndex") == "true" && locIndexFile.exists()) {
                logger.lifecycle("Keeping existing manual documentation root index: ${locIndexFile.absolutePath}")
                return@doLast
            }

            val locPublicationLabel = if (locPublicationKind != null && locPublicationId != null) {
                "${locPublicationKind}/${locPublicationId}"
            } else {
                "generated"
            }

            locIndexFile.writeText(
                """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>${locRepositoryName.AIcDocsHtmlEscape()} Documentation</title>
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <style>
                    body {
                      margin: 0;
                      font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                      line-height: 1.5;
                      color: #1f2937;
                      background: #f9fafb;
                    }

                    header, main {
                      max-width: 1100px;
                      margin: 0 auto;
                      padding: 1.5rem;
                    }

                    header {
                      padding-top: 2rem;
                    }

                    h1 {
                      margin: 0 0 0.5rem 0;
                      font-size: 2rem;
                    }

                    .muted {
                      color: #6b7280;
                    }

                    .card {
                      background: white;
                      border: 1px solid #e5e7eb;
                      border-radius: 0.75rem;
                      padding: 1rem 1.25rem;
                      margin: 1rem 0;
                      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
                    }

                    .nav-list {
                      display: flex;
                      flex-wrap: wrap;
                      gap: 0.75rem;
                      padding: 0;
                      list-style: none;
                    }

                    .nav-list a {
                      display: inline-block;
                      padding: 0.5rem 0.75rem;
                      border: 1px solid #d1d5db;
                      border-radius: 0.5rem;
                      background: #f9fafb;
                    }

                    iframe {
                      width: 100%;
                      min-height: 70vh;
                      border: 1px solid #e5e7eb;
                      border-radius: 0.75rem;
                      background: white;
                    }

                    a {
                      color: #2563eb;
                    }
                  </style>
                </head>
                <body>
                  <header>
                    <h1>${locRepositoryName.AIcDocsHtmlEscape()} Documentation</h1>
                    <p class="muted">Repository ID: ${locRepositoryId.AIcDocsHtmlEscape()}</p>
                    ${locRepositoryDescription?.let { "<p>${it.AIcDocsHtmlEscape()}</p>" } ?: ""}
                  </header>

                  <main>
                    <section class="card">
                      <h2>Documentation sections</h2>
                      <ul class="nav-list">
                        <li><a href="generated/preview/">Preview</a></li>
                        <li><a href="generated/snapshot/">Snapshot</a></li>
                        <li><a href="generated/release/">Release</a></li>
                      </ul>
                    </section>

                    <section class="card">
                      <h2>Current generated documentation</h2>
                      <p>
                        Current publication: <strong>${locPublicationLabel.AIcDocsHtmlEscape()}</strong>.
                        Open it directly here:
                        <a href="${locCurrentPublicationHref.AIcDocsHtmlEscape()}">${locCurrentPublicationHref.AIcDocsHtmlEscape()}</a>.
                      </p>
                    </section>

                    <iframe src="${locCurrentPublicationHref.AIcDocsHtmlEscape()}" title="Current generated artifact documentation"></iframe>
                  </main>
                </body>
                </html>
                """.trimIndent(),
                Charsets.UTF_8
            )
        }
    }
}

if (tasks.findByName("generateAlgitesDocsSite") == null) {
    tasks.register("generateAlgitesDocsSite") {
        group = "algites"
        description = "Generic aggregate task for repository documentation site generation."

        dependsOn("generateAlgitesDocsRootIndex")
    }
}
