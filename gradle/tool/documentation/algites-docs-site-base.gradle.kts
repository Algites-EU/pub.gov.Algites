/*
 * Algites shared documentation site base script.
 *
 * Intended location in governance repository:
 *   gradle/tool/documentation/algites-docs-site-base.gradle.kts
 *
 * This script defines common documentation-site conventions and a stable root
 * index. Technology-specific scripts should apply this script automatically.
 */

import org.gradle.api.Action
import org.gradle.api.Task

apply(plugin = "base")

val locAlgitesRepositoryMetadataResolverScript = (findProperty("algites.docs.repositoryMetadataResolverScript") as String?)
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/repository/algites-artifact-directory-metadata-resolver-wrapper.gradle.kts"

apply(from = uri(locAlgitesRepositoryMetadataResolverScript))

@Suppress("UNCHECKED_CAST")
val locAlgitesDocsResolvedMetadata = rootProject.extra.properties["algitesResolvedArtifactDirectoryMetadata"] as? Map<String, Any?>
    ?: run {
        val locAlgitesResolveMetadataMap = extra.properties["algitesResolveArtifactDirectoryMetadataMap"] as? (
            File,
            String?,
            String?,
            String?,
            String?
        ) -> Map<String, Any?>
            ?: error("Algites artifact directory metadata resolver did not export algitesResolvedArtifactDirectoryMetadata or algitesResolveArtifactDirectoryMetadataMap.")

        locAlgitesResolveMetadataMap(
            rootProject.projectDir,
            "",
            "current-with-subdirs",
            providers.gradleProperty("repository.name").orNull,
            providers.gradleProperty("repository.visibility").orNull
        )
    }

@Suppress("UNCHECKED_CAST")
val locAlgitesDocsResolvedMetadataPropertiesRaw = rootProject.extra.properties["algitesResolvedArtifactDirectoryMetadataProperties"] as? Map<String, String>
    ?: run {
        val locAlgitesFlattenMetadata = extra.properties["algitesFlattenArtifactDirectoryMetadata"] as? (Map<String, Any?>) -> Map<String, String>
            ?: error("Algites artifact directory metadata resolver did not export algitesResolvedArtifactDirectoryMetadataProperties or algitesFlattenArtifactDirectoryMetadata.")

        locAlgitesFlattenMetadata(locAlgitesDocsResolvedMetadata)
    }

fun AIcDocsReadDottedProperties(aText: String): Map<String, String?> {
    return aText.lineSequence()
        .map { locLine -> locLine.trim() }
        .filter { locLine -> locLine.isNotBlank() && !locLine.startsWith("#") }
        .mapNotNull { locLine ->
            val locSeparatorIndex = locLine.indexOf('=')
            if (locSeparatorIndex < 0) {
                null
            } else {
                val locKey = locLine.substring(0, locSeparatorIndex).trim()
                val locValue = locLine.substring(locSeparatorIndex + 1).trim()
                locKey to locValue.takeIf { it != "null" }
            }
        }
        .toMap()
}

fun AIcDocsReadArtifactDirectories(aProperties: Map<String, String?>): List<Map<String, String?>> {
    val locCount = aProperties["artifactDirectories.count"]?.toIntOrNull() ?: 0
    return (0 until locCount).map { locIndex ->
        mapOf(
            "path" to aProperties["artifactDirectories.${locIndex}.path"],
            "kind" to aProperties["artifactDirectories.${locIndex}.kind"],
            "type" to aProperties["artifactDirectories.${locIndex}.type"],
            "name" to aProperties["artifactDirectories.${locIndex}.name"],
            "description" to aProperties["artifactDirectories.${locIndex}.description"],
            "groupId" to aProperties["artifactDirectories.${locIndex}.groupId"],
            "contentsModel" to aProperties["artifactDirectories.${locIndex}.contentsModel"],
            "hasGradleBuild" to aProperties["artifactDirectories.${locIndex}.hasGradleBuild"],
            "gradleProjectPath" to aProperties["artifactDirectories.${locIndex}.gradleProjectPath"],
            "version.lane" to aProperties["artifactDirectories.${locIndex}.version.lane"],
            "version.revision" to aProperties["artifactDirectories.${locIndex}.version.revision"],
            "version.qualifierKind" to aProperties["artifactDirectories.${locIndex}.version.qualifierKind"],
            "version.qualifierLabel" to aProperties["artifactDirectories.${locIndex}.version.qualifierLabel"],
            "version.resolvedValue" to aProperties["artifactDirectories.${locIndex}.version.resolvedValue"]
        )
    }
}

val locAlgitesDocsResolvedMetadataProperties = locAlgitesDocsResolvedMetadataPropertiesRaw
    .mapValues { locEntry -> locEntry.value.takeIf { it != "null" } }
val locAlgitesDocsResolvedArtifactDirectories = AIcDocsReadArtifactDirectories(locAlgitesDocsResolvedMetadataProperties)
val locAlgitesDocsResolvedRepositoryName = locAlgitesDocsResolvedMetadataProperties["repository.name"] ?: rootProject.name
val locAlgitesDocsResolvedRepositoryVisibility = locAlgitesDocsResolvedMetadataProperties["repository.visibility"] ?: ""

extra["algitesDocsResolvedMetadataProperties"] = locAlgitesDocsResolvedMetadataProperties
extra["algitesDocsResolvedArtifactDirectories"] = locAlgitesDocsResolvedArtifactDirectories
extra["algitesDocsResolvedRepositoryName"] = locAlgitesDocsResolvedRepositoryName
extra["algitesDocsResolvedRepositoryVisibility"] = locAlgitesDocsResolvedRepositoryVisibility


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
    return when (aKey) {
        "id", "name" -> locAlgitesDocsResolvedRepositoryName
        "visibility" -> locAlgitesDocsResolvedRepositoryVisibility.takeIf { it.isNotBlank() }
        else -> null
    }
}

val locAlgitesDocsRepositoryId = AIcDocsReadRepositoryScalar("id") ?: locAlgitesDocsResolvedRepositoryName
val locAlgitesDocsRepositoryNameForSite = AIcDocsReadRepositoryScalar("name") ?: locAlgitesDocsRepositoryId
val locAlgitesDocsRepositoryDescription = AIcDocsReadRepositoryScalar("description")
val locAlgitesDocsRepositoryHomeUrl = (findProperty("algites.docs.repositoryHomeUrl") as String?)
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?: AIcDocsReadRepositoryScalar("homeUrl")
    ?: AIcDocsReadRepositoryScalar("repositoryUrl")
    ?: AIcDocsReadRepositoryScalar("url")
    ?: "https://github.com/Algites-EU/${locAlgitesDocsRepositoryId}"
val locAlgitesDocsPublicationLabel = if (locPublicationKind != null && locPublicationId != null) {
    "${locPublicationKind}/${locPublicationId}"
} else {
    "generated"
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


class AIcGenerateAlgitesDocsRootIndexAction(
    private val locRepositoryId: String,
    private val locRepositoryHomeUrl: String,
    private val locIndexFile: File
) : Action<Task>, java.io.Serializable {

    override fun execute(aTask: Task) {
        locIndexFile.parentFile.mkdirs()

        if (locIndexFile.exists()) {
            aTask.logger.lifecycle("Keeping existing documentation root index: ${locIndexFile.absolutePath}")
            return
        }

        locIndexFile.writeText(
            """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <title>Algites ${AIcDocsHtmlEscape(locRepositoryId)} Repository Documentation</title>
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
                <h1>Algites <strong>${AIcDocsHtmlEscape(locRepositoryId)}</strong> Repository Documentation</h1>
                <p>
                  This page is the stable repository documentation entry point.
                  Generated artifact documentation is published under
                  <a href="generated/">generated/</a>.
                </p>
                <p>
                  ${AIcDocsHtmlEscape(locRepositoryId)} repository home is <a href="${AIcDocsHtmlEscape(locRepositoryHomeUrl)}">
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

    private fun AIcDocsHtmlEscape(aText: String): String {
        return aText.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}

class AIcGenerateAlgitesDocsGeneratedIndexAction(
    private val locRepositoryId: String,
    private val locRepositoryName: String,
    private val locRepositoryDescription: String?,
    private val locGeneratedDocsRootFile: File,
    private val locCurrentPublicationDirectory: File,
    private val locPublicationLabel: String,
    private val locIndexFile: File
) : Action<Task>, java.io.Serializable {

    override fun execute(aTask: Task) {
        val locCurrentPublicationHref = AIcDocsRelativeHref(locGeneratedDocsRootFile, locCurrentPublicationDirectory)

        locIndexFile.parentFile.mkdirs()
        locIndexFile.writeText(
            """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <title>${AIcDocsHtmlEscape(locRepositoryName)} Generated Documentation</title>
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
                <h1>${AIcDocsHtmlEscape(locRepositoryName)} Generated Documentation</h1>
                <p class="muted">Repository ID: ${AIcDocsHtmlEscape(locRepositoryId)}</p>
                ${locRepositoryDescription?.let { "<p>${AIcDocsHtmlEscape(it)}</p>" } ?: ""}
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
                    Current publication: <strong>${AIcDocsHtmlEscape(locPublicationLabel)}</strong>.
                    Open it directly here:
                    <a href="${AIcDocsHtmlEscape(locCurrentPublicationHref)}">${AIcDocsHtmlEscape(locCurrentPublicationHref)}</a>.
                  </p>
                </section>

                <iframe src="${AIcDocsHtmlEscape(locCurrentPublicationHref)}" title="Current generated artifact documentation"></iframe>

                <p><a href="../index.html">Repository documentation root</a></p>
              </main>
            </body>
            </html>
            """.trimIndent(),
            Charsets.UTF_8
        )
    }

    private fun AIcDocsRelativeHref(aBaseDirectory: File, aTargetDirectory: File): String {
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

    private fun AIcDocsHtmlEscape(aText: String): String {
        return aText.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}

class AIcGenerateAlgitesDocsPublicationGroupIndexesAction(
    private val locGeneratedDocsRootFile: File,
    private val locRepositoryName: String
) : Action<Task>, java.io.Serializable {

    override fun execute(aTask: Task) {
        AIcDocsWritePublicationGroupIndex(File(locGeneratedDocsRootFile, "preview"), "preview", locRepositoryName, false)
        AIcDocsWritePublicationGroupIndex(File(locGeneratedDocsRootFile, "snapshot"), "snapshot", locRepositoryName, true)
        AIcDocsWritePublicationGroupIndex(File(locGeneratedDocsRootFile, "release"), "release", locRepositoryName, true)
    }

    private fun AIcDocsWritePublicationGroupIndex(
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
              <title>${AIcDocsHtmlEscape(locTitle)}</title>
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

                h1 {
                  margin: 0 0 0.5rem 0;
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

                a {
                  color: #2563eb;
                }
              </style>
            </head>
            <body>
              <main>
                <h1>${AIcDocsHtmlEscape(locTitle)}</h1>
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
                  <h2>Available ${AIcDocsHtmlEscape(aPublicationKind)} publications</h2>
                  ${
                      if (locSortedEntries.isEmpty()) {
                          "<p>No ${AIcDocsHtmlEscape(aPublicationKind)} documentation has been generated yet.</p>"
                      } else {
                          "<ul>\n" + locSortedEntries.joinToString("\n") { locDirectory ->
                              "                <li><a href=\"${AIcDocsHtmlEscape(locDirectory.name)}/index.html\">${AIcDocsHtmlEscape(locDirectory.name)}</a></li>"
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

    private fun AIcDocsHtmlEscape(aText: String): String {
        return aText.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}

if (tasks.findByName("generateAlgitesDocsRootIndex") == null) {
    tasks.register("generateAlgitesDocsRootIndex") {
        group = "algites"
        description = "Generates the stable root index page for the Algites documentation site only if it does not already exist."

        outputs.file(locDocsSiteRoot.file("index.html"))

        doLast(
            AIcGenerateAlgitesDocsRootIndexAction(
                locAlgitesDocsRepositoryId,
                locAlgitesDocsRepositoryHomeUrl,
                locDocsSiteRoot.file("index.html").asFile
            )
        )
    }
}

if (tasks.findByName("generateAlgitesDocsGeneratedIndex") == null) {
    tasks.register("generateAlgitesDocsGeneratedIndex") {
        group = "algites"
        description = "Generates the generated documentation landing page."

        outputs.file(locGeneratedDocsRoot.file("index.html"))

        doLast(
            AIcGenerateAlgitesDocsGeneratedIndexAction(
                locAlgitesDocsRepositoryId,
                locAlgitesDocsRepositoryNameForSite,
                locAlgitesDocsRepositoryDescription,
                locGeneratedDocsRoot.asFile,
                locPublicationDocsRoot.asFile,
                locAlgitesDocsPublicationLabel,
                locGeneratedDocsRoot.file("index.html").asFile
            )
        )
    }
}


if (tasks.findByName("generateAlgitesDocsPublicationGroupIndexes") == null) {
    tasks.register("generateAlgitesDocsPublicationGroupIndexes") {
        group = "algites"
        description = "Generates index pages for Algites preview, snapshot, and release documentation groups."

        outputs.file(locGeneratedDocsRoot.file("preview/index.html"))
        outputs.file(locGeneratedDocsRoot.file("snapshot/index.html"))
        outputs.file(locGeneratedDocsRoot.file("release/index.html"))

        doLast(
            AIcGenerateAlgitesDocsPublicationGroupIndexesAction(
                locGeneratedDocsRoot.asFile,
                locAlgitesDocsRepositoryNameForSite
            )
        )
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
