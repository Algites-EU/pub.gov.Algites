/*
 * Algites shared documentation site base script.
 *
 * Intended location in governance repository:
 *   gradle/tool/documentation/algites-docs-site-base.gradle.kts
 *
 * This script defines common documentation-site conventions and a stable root
 * index. Technology-specific scripts should apply this script automatically.
 */

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

apply(plugin = "base")


abstract class AIcGenerateAlgitesDocsRootIndexTask : DefaultTask() {

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val repositoryConfigFile: RegularFileProperty

    @get:Input
    abstract val fallbackRepositoryId: Property<String>

    @get:Input
    @get:Optional
    abstract val configuredRepositoryHomeUrl: Property<String>

    @get:OutputFile
    abstract val indexFile: RegularFileProperty

    @TaskAction
    fun AIcGenerateIndex() {
        val locIndexFile = indexFile.get().asFile
        locIndexFile.parentFile.mkdirs()

        if (locIndexFile.exists()) {
            logger.lifecycle("Keeping existing documentation root index: ${locIndexFile.absolutePath}")
            return
        }

        val locRepositoryConfigFile = repositoryConfigFile.asFile.orNull
        val locRepositoryId = AIcReadRepositoryScalar(locRepositoryConfigFile, "id") ?: fallbackRepositoryId.get()
        val locRepositoryHomeUrl = configuredRepositoryHomeUrl.orNull
            ?: AIcReadRepositoryScalar(locRepositoryConfigFile, "homeUrl")
            ?: AIcReadRepositoryScalar(locRepositoryConfigFile, "repositoryUrl")
            ?: AIcReadRepositoryScalar(locRepositoryConfigFile, "url")
            ?: "https://github.com/Algites-EU/${locRepositoryId}"

        locIndexFile.writeText(
            """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <title>Algites ${locRepositoryId.AIcHtmlEscape()} Repository Documentation</title>
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
                <h1>Algites <strong>${locRepositoryId.AIcHtmlEscape()}</strong> Repository Documentation</h1>
                <p>
                  This page is the stable repository documentation entry point.
                  Generated artifact documentation is published under
                  <a href="generated/">generated/</a>.
                </p>
                <p>
                  ${locRepositoryId.AIcHtmlEscape()} repository home is <a href="${locRepositoryHomeUrl.AIcHtmlEscape()}">
            here
            </a>.
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
    }

    private fun AIcReadRepositoryScalar(aRepositoryConfigFile: File?, aKey: String): String? {
        if (aRepositoryConfigFile == null || !aRepositoryConfigFile.isFile) {
            return null
        }
        return Regex("""(?m)^\s*${Regex.escape(aKey)}\s*:\s*(.+?)\s*(?:#.*)?$""")
            .find(aRepositoryConfigFile.readText(Charsets.UTF_8))
            ?.groupValues
            ?.get(1)
            ?.AIcNormalizeYamlScalar()
            ?.takeIf { it.isNotBlank() }
    }

    private fun String.AIcNormalizeYamlScalar(): String {
        return trim().removeSurrounding("\"").removeSurrounding("'")
    }

    private fun String.AIcHtmlEscape(): String {
        return replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}

abstract class AIcGenerateAlgitesDocsGeneratedIndexTask : DefaultTask() {

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val repositoryConfigFile: RegularFileProperty

    @get:Input
    abstract val fallbackRepositoryId: Property<String>

    @get:Input
    @get:Optional
    abstract val publicationKind: Property<String>

    @get:Input
    @get:Optional
    abstract val publicationId: Property<String>

    @get:Input
    abstract val currentPublicationHref: Property<String>

    @get:OutputFile
    abstract val indexFile: RegularFileProperty

    @TaskAction
    fun AIcGenerateIndex() {
        val locRepositoryConfigFile = repositoryConfigFile.asFile.orNull
        val locRepositoryId = AIcReadRepositoryScalar(locRepositoryConfigFile, "id") ?: fallbackRepositoryId.get()
        val locRepositoryName = AIcReadRepositoryScalar(locRepositoryConfigFile, "name") ?: locRepositoryId
        val locRepositoryDescription = AIcReadRepositoryScalar(locRepositoryConfigFile, "description")
        val locPublicationKind = publicationKind.orNull
        val locPublicationId = publicationId.orNull
        val locCurrentPublicationHref = currentPublicationHref.get()
        val locPublicationLabel = if (locPublicationKind != null && locPublicationId != null) {
            "${locPublicationKind}/${locPublicationId}"
        } else {
            "generated"
        }

        val locIndexFile = indexFile.get().asFile
        locIndexFile.parentFile.mkdirs()
        locIndexFile.writeText(
            """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <title>${locRepositoryName.AIcHtmlEscape()} Generated Documentation</title>
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
                <h1>${locRepositoryName.AIcHtmlEscape()} Generated Documentation</h1>
                <p class="muted">Repository ID: ${locRepositoryId.AIcHtmlEscape()}</p>
                ${locRepositoryDescription?.let { "<p>${it.AIcHtmlEscape()}</p>" } ?: ""}
              </header>

              <main>
                <section class="card">
                  <h2>Documentation sections</h2>
                  <ul class="nav-list">
                    <li><a href="preview/index.html">Preview</a></li>
                    <li><a href="snapshot/index.html">Snapshot</a></li>
                    <li><a href="release/index.html">Release</a></li>
                  </ul>
                </section>

                <section class="card">
                  <h2>Current generated documentation</h2>
                  <p>
                    Current publication: <strong>${locPublicationLabel.AIcHtmlEscape()}</strong>.
                    Open it directly here:
                    <a href="${locCurrentPublicationHref.AIcHtmlEscape()}">${locCurrentPublicationHref.AIcHtmlEscape()}</a>.
                  </p>
                </section>

                <iframe src="${locCurrentPublicationHref.AIcHtmlEscape()}" title="Current generated artifact documentation"></iframe>

                <p><a href="../index.html">Repository documentation root</a></p>
              </main>
            </body>
            </html>
            """.trimIndent(),
            Charsets.UTF_8
        )
    }

    private fun AIcReadRepositoryScalar(aRepositoryConfigFile: File?, aKey: String): String? {
        if (aRepositoryConfigFile == null || !aRepositoryConfigFile.isFile) {
            return null
        }
        return Regex("""(?m)^\s*${Regex.escape(aKey)}\s*:\s*(.+?)\s*(?:#.*)?$""")
            .find(aRepositoryConfigFile.readText(Charsets.UTF_8))
            ?.groupValues
            ?.get(1)
            ?.AIcNormalizeYamlScalar()
            ?.takeIf { it.isNotBlank() }
    }

    private fun String.AIcNormalizeYamlScalar(): String {
        return trim().removeSurrounding("\"").removeSurrounding("'")
    }

    private fun String.AIcHtmlEscape(): String {
        return replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}

abstract class AIcGenerateAlgitesDocsPublicationGroupIndexesTask : DefaultTask() {

    @get:Internal
    abstract val generatedDocsRootDirectory: DirectoryProperty

    @get:Input
    abstract val repositoryName: Property<String>

    @get:OutputFile
    abstract val previewIndexFile: RegularFileProperty

    @get:OutputFile
    abstract val snapshotIndexFile: RegularFileProperty

    @get:OutputFile
    abstract val releaseIndexFile: RegularFileProperty

    @TaskAction
    fun AIcGenerateIndexes() {
        AIcWritePublicationGroupIndex(
            File(generatedDocsRootDirectory.get().asFile, "preview"),
            "preview",
            repositoryName.get(),
            false
        )
        AIcWritePublicationGroupIndex(
            File(generatedDocsRootDirectory.get().asFile, "snapshot"),
            "snapshot",
            repositoryName.get(),
            true
        )
        AIcWritePublicationGroupIndex(
            File(generatedDocsRootDirectory.get().asFile, "release"),
            "release",
            repositoryName.get(),
            true
        )
    }

    private fun AIcWritePublicationGroupIndex(
        aGroupDirectory: File,
        aPublicationKind: String,
        aRepositoryName: String,
        aDescending: Boolean
    ) {
        aGroupDirectory.mkdirs()

        val locEntries = aGroupDirectory
            .listFiles()
            ?.filter { locFile -> locFile.isDirectory && locFile.resolve("index.html").isFile }
            ?.sortedBy { locFile -> locFile.name.lowercase() }
            ?: emptyList()

        val locSortedEntries = if (aDescending) {
            locEntries.asReversed()
        } else {
            locEntries
        }

        val locTitle = "${aRepositoryName} ${aPublicationKind} documentation"
        val locIndexFile = aGroupDirectory.resolve("index.html")

        locIndexFile.writeText(
            """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <title>${locTitle.AIcHtmlEscape()}</title>
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <style>
                body {
                  margin: 0;
                  font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                  line-height: 1.5;
                  color: #1f2937;
                  background: #f9fafb;
                }

                main {
                  max-width: 900px;
                  margin: 0 auto;
                  padding: 2rem 1.5rem;
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

                li {
                  margin: 0.35rem 0;
                }

                a {
                  color: #2563eb;
                }
              </style>
            </head>
            <body>
              <main>
                <h1>${locTitle.AIcHtmlEscape()}</h1>
                <p class="muted">
                  ${
                      if (aDescending) {
                          "Entries are sorted descending so newer or higher version identifiers appear first."
                      } else {
                          "Entries are sorted ascending alphabetically."
                      }
                  }
                </p>

                <section class="card">
                  <h2>Available ${aPublicationKind.AIcHtmlEscape()} publications</h2>
                  ${
                      if (locSortedEntries.isEmpty()) {
                          "<p>No ${aPublicationKind.AIcHtmlEscape()} documentation has been generated yet.</p>"
                      } else {
                          "<ul>\n" + locSortedEntries.joinToString("\n") { locDirectory ->
                              "                <li><a href=\"${locDirectory.name.AIcHtmlEscape()}/index.html\">${locDirectory.name.AIcHtmlEscape()}</a></li>"
                          } + "\n              </ul>"
                      }
                  }
                </section>

                <p><a href="../index.html">Back to generated documentation index</a></p>
              </main>
            </body>
            </html>
            """.trimIndent(),
            Charsets.UTF_8
        )
    }

    private fun String.AIcHtmlEscape(): String {
        return replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}

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
        "index.html"
    } else {
        "${locRelativePath}/index.html"
    }
}


fun AIcDocsWritePublicationGroupIndex(
    aGroupDirectory: File,
    aPublicationKind: String,
    aRepositoryName: String,
    aDescending: Boolean
) {
    aGroupDirectory.mkdirs()

    val locEntries = aGroupDirectory
        .listFiles()
        ?.filter { locFile -> locFile.isDirectory && locFile.resolve("index.html").isFile }
        ?.sortedBy { locFile -> locFile.name.lowercase() }
        ?: emptyList()

    val locSortedEntries = if (aDescending) {
        locEntries.asReversed()
    } else {
        locEntries
    }

    val locTitle = "${aRepositoryName} ${aPublicationKind} documentation"
    val locIndexFile = aGroupDirectory.resolve("index.html")

    locIndexFile.writeText(
        """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <title>${locTitle.AIcDocsHtmlEscape()}</title>
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <style>
            body {
              margin: 0;
              font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
              line-height: 1.5;
              color: #1f2937;
              background: #f9fafb;
            }

            main {
              max-width: 900px;
              margin: 0 auto;
              padding: 2rem 1.5rem;
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

            li {
              margin: 0.35rem 0;
            }

            a {
              color: #2563eb;
            }
          </style>
        </head>
        <body>
          <main>
            <h1>${locTitle.AIcDocsHtmlEscape()}</h1>
            <p class="muted">
              ${
                  if (aDescending) {
                      "Entries are sorted descending so newer or higher version identifiers appear first."
                  } else {
                      "Entries are sorted ascending alphabetically."
                  }
              }
            </p>

            <section class="card">
              <h2>Available ${aPublicationKind.AIcDocsHtmlEscape()} publications</h2>
              ${
                  if (locSortedEntries.isEmpty()) {
                      "<p>No ${aPublicationKind.AIcDocsHtmlEscape()} documentation has been generated yet.</p>"
                  } else {
                      "<ul>\n" + locSortedEntries.joinToString("\n") { locDirectory ->
                          "                <li><a href=\"${locDirectory.name.AIcDocsHtmlEscape()}/index.html\">${locDirectory.name.AIcDocsHtmlEscape()}</a></li>"
                      } + "\n              </ul>"
                  }
              }
            </section>

            <p><a href="../index.html">Back to generated documentation index</a></p>
          </main>
        </body>
        </html>
        """.trimIndent(),
        Charsets.UTF_8
    )
}

if (tasks.findByName("generateAlgitesDocsRootIndex") == null) {
    tasks.register<AIcGenerateAlgitesDocsRootIndexTask>("generateAlgitesDocsRootIndex") {
        group = "algites"
        description = "Generates the stable root index page for the Algites documentation site only if it does not already exist."

        repositoryConfigFile.set(locRepositoryConfigFile)
        fallbackRepositoryId.set(rootProject.name)
        val locConfiguredRepositoryHomeUrl = (findProperty("algites.docs.repositoryHomeUrl") as String?)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (locConfiguredRepositoryHomeUrl != null) {
            configuredRepositoryHomeUrl.set(locConfiguredRepositoryHomeUrl)
        }
        indexFile.set(locDocsSiteRoot.file("index.html"))
    }
}

val locCurrentPublicationHref = AIcDocsRelativeHref(
    locGeneratedDocsRoot.asFile,
    locPublicationDocsRoot.asFile
)

if (tasks.findByName("generateAlgitesDocsGeneratedIndex") == null) {
    tasks.register<AIcGenerateAlgitesDocsGeneratedIndexTask>("generateAlgitesDocsGeneratedIndex") {
        group = "algites"
        description = "Generates the generated documentation landing page."

        repositoryConfigFile.set(locRepositoryConfigFile)
        fallbackRepositoryId.set(rootProject.name)
        if (locPublicationKind != null) {
            publicationKind.set(locPublicationKind)
        }
        if (locPublicationId != null) {
            publicationId.set(locPublicationId)
        }
        currentPublicationHref.set(locCurrentPublicationHref)
        indexFile.set(locGeneratedDocsRoot.file("index.html"))
    }
}


val locDocsRepositoryIdForGroupIndexes = AIcDocsReadRepositoryScalar("id") ?: rootProject.name
val locDocsRepositoryNameForGroupIndexes = AIcDocsReadRepositoryScalar("name") ?: locDocsRepositoryIdForGroupIndexes

if (tasks.findByName("generateAlgitesDocsPublicationGroupIndexes") == null) {
    tasks.register<AIcGenerateAlgitesDocsPublicationGroupIndexesTask>("generateAlgitesDocsPublicationGroupIndexes") {
        group = "algites"
        description = "Generates index pages for Algites preview, snapshot, and release documentation groups."

        generatedDocsRootDirectory.set(locGeneratedDocsRoot)
        repositoryName.set(locDocsRepositoryNameForGroupIndexes)
        previewIndexFile.set(locGeneratedDocsRoot.file("preview/index.html"))
        snapshotIndexFile.set(locGeneratedDocsRoot.file("snapshot/index.html"))
        releaseIndexFile.set(locGeneratedDocsRoot.file("release/index.html"))
    }
}

if (tasks.findByName("generateAlgitesDocsSite") == null) {
    tasks.register("generateAlgitesDocsSite") {
        group = "algites"
        description = "Generic aggregate task for repository documentation site generation."

        dependsOn("generateAlgitesDocsRootIndex")
        dependsOn("generateAlgitesDocsPublicationGroupIndexes")
        dependsOn("generateAlgitesDocsGeneratedIndex")
    }
}


afterEvaluate {
    val locGeneratedIndexTask = tasks.findByName("generateAlgitesDocsGeneratedIndex")
    val locPublicationGroupIndexesTask = tasks.findByName("generateAlgitesDocsPublicationGroupIndexes")

    listOf("generateJavaDocsSite", "generateDummyMpsDocs")
        .mapNotNull { locTaskName -> tasks.findByName(locTaskName) }
        .forEach { locDocumentationTask ->
            locGeneratedIndexTask?.mustRunAfter(locDocumentationTask)
            locPublicationGroupIndexesTask?.mustRunAfter(locDocumentationTask)
        }
}
