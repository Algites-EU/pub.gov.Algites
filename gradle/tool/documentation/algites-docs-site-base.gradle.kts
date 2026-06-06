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

val locGeneratedDocsRoot = layout.projectDirectory.dir(
    (findProperty("algites.docs.generatedRoot") as String?) ?: "docs-site/generated"
)

val locRepositoryConfigFile = layout.projectDirectory.file("algites-source-repository.yml")

extra["algitesDocsSiteRootPath"] = locDocsSiteRoot.asFile.path
extra["algitesGeneratedDocsRootPath"] = locGeneratedDocsRoot.asFile.path

if (tasks.findByName("generateAlgitesDocsRootIndex") == null) {
    tasks.register("generateAlgitesDocsRootIndex") {
        group = "algites"
        description = "Generates the stable root index page for the Algites documentation site."

        outputs.file(locDocsSiteRoot.file("index.html"))

        doLast {
            val locRepositoryId = locRepositoryConfigFile.asFile
                .takeIf { it.isFile }
                ?.readText(Charsets.UTF_8)
                ?.let { locText ->
                    Regex("""(?m)^\s*id\s*:\s*([^\s#]+)\s*$""").find(locText)?.groupValues?.get(1)
                }
                ?.trim()
                ?.removeSurrounding("\"")
                ?.removeSurrounding("'")
                ?: rootProject.name

            val locIndexFile = locDocsSiteRoot.file("index.html").asFile
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

                        .card {
                          background: white;
                          border: 1px solid #e5e7eb;
                          border-radius: 0.75rem;
                          padding: 1rem 1.25rem;
                          margin: 1rem 0;
                          box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
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
                        <h1>${locRepositoryId} Documentation</h1>
                        <p>
                          This page is the stable repository documentation entry point.
                          Generated artifact documentation is published under
                          <a href="generated/">generated/</a>.
                        </p>
                      </header>

                      <main>
                        <section class="card">
                          <h2>Generated Artifact Documentation</h2>
                          <p>
                            The content below is generated automatically. If the embedded view is not available,
                            open the generated documentation index directly:
                            <a href="generated/">generated/index.html</a>.
                          </p>
                        </section>

                        <iframe src="generated/" title="Generated artifact documentation"></iframe>
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
}

if (tasks.findByName("generateAlgitesDocsSite") == null) {
    tasks.register("generateAlgitesDocsSite") {
        group = "algites"
        description = "Generic aggregate task for repository documentation site generation."

        dependsOn("generateAlgitesDocsRootIndex")
    }
}
