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
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.util.Base64

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
val locAlgitesDocsResolvedRepositoryId = locAlgitesDocsResolvedMetadataProperties["repository.id"] ?: rootProject.name
val locAlgitesDocsResolvedRepositoryName = locAlgitesDocsResolvedMetadataProperties["repository.name"] ?: locAlgitesDocsResolvedRepositoryId
val locAlgitesDocsResolvedRepositoryVisibility = locAlgitesDocsResolvedMetadataProperties["repository.visibility"] ?: ""

extra["algitesDocsResolvedMetadataProperties"] = locAlgitesDocsResolvedMetadataProperties
extra["algitesDocsResolvedArtifactDirectories"] = locAlgitesDocsResolvedArtifactDirectories
extra["algitesDocsResolvedRepositoryId"] = locAlgitesDocsResolvedRepositoryId
extra["algitesDocsResolvedRepositoryName"] = locAlgitesDocsResolvedRepositoryName
extra["algitesDocsResolvedRepositoryVisibility"] = locAlgitesDocsResolvedRepositoryVisibility


val locDocsSiteRoot = layout.projectDirectory.dir(
    (findProperty("algites.docs.siteRoot") as String?) ?: "docs-site"
)

/*
 * Generated documentation is always placed below the documentation site root.
 *
 * Canonical artifact documentation is generated below:
 *   generated/artifacts/<localArtifactId>/<publicationKind>/<publicationId>/<documentationKind>/
 *
 * Publication indexes are generated below:
 *   generated/publications/<publicationKind>/<publicationId>/
 */
val locGeneratedDocsRoot = locDocsSiteRoot.dir("generated")
val locArtifactDocsRoot = locGeneratedDocsRoot.dir("artifacts")
val locPublicationsDocsRoot = locGeneratedDocsRoot.dir("publications")

val locPublicationKind = (findProperty("algites.docs.publicationKind") as String?)
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?: "preview"

val locPublicationId = (findProperty("algites.docs.publicationId") as String?)
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?: "main"

val locPublicationDocsRoot = locPublicationsDocsRoot.dir("${locPublicationKind}/${locPublicationId}")


extra["algitesDocsSiteRootPath"] = locDocsSiteRoot.asFile.path
extra["algitesGeneratedDocsRootPath"] = locGeneratedDocsRoot.asFile.path
extra["algitesArtifactDocsRootPath"] = locArtifactDocsRoot.asFile.path
extra["algitesPublicationsDocsRootPath"] = locPublicationsDocsRoot.asFile.path
extra["algitesPublicationDocsRootPath"] = locPublicationDocsRoot.asFile.path
extra["algitesDocsPublicationKind"] = locPublicationKind
extra["algitesDocsPublicationId"] = locPublicationId

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
        "id" -> locAlgitesDocsResolvedRepositoryId
        "name" -> locAlgitesDocsResolvedRepositoryName
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
                  line-height: 1.35;
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
                  margin: 0 0 0.35rem 0;
                  font-size: 2rem;
                }

                .card {
                  background: white;
                  border: 1px solid #e5e7eb;
                  border-radius: 0.75rem;
                  padding: 0.75rem 0.9rem;
                  margin: 0.65rem 0;
                  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
                }

                iframe {
                  width: 100%;
                  min-height: 70vh;
                  border: 1px solid #e5e7eb;
                  border-radius: 0.75rem;
                  background: white;
                }

                h2 {
                  margin: 0 0 0.55rem 0;
                  font-size: 1.1rem;
                }

                ul {
                  margin: 0.35rem 0 0 0;
                  padding-left: 1.25rem;
                }

                li {
                  margin: 0.15rem 0;
                }

                a {
                  color: #2563eb;
                }

                .compact-lead {
                  margin-top: 0;
                  margin-bottom: 0.7rem;
                }

                .artifact-publication-grid {
                  display: grid;
                  grid-template-columns: minmax(22rem, 1.05fr) minmax(26rem, 1.35fr);
                  gap: 0.75rem;
                  align-items: start;
                }

                .artifact-publication-grid .card:nth-child(3) {
                  grid-column: 1;
                }

                .card-strong {
                  border-color: #d1d5db;
                }

                .coordinates {
                  grid-template-columns: minmax(7rem, 9rem) 1fr;
                }

                .compact-dl {
                  grid-template-columns: minmax(9rem, 13rem) 1fr;
                }

                .back-links {
                  display: flex;
                  flex-wrap: wrap;
                  gap: 0.75rem;
                  margin-top: 0.75rem;
                }

                @media (max-width: 820px) {
                  .artifact-publication-grid {
                    grid-template-columns: 1fr;
                  }

                  .artifact-publication-grid .card:nth-child(3) {
                    grid-column: auto;
                  }
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
                    <li><a href="publications/preview/index.html">Preview</a></li>
                    <li><a href="publications/snapshot/index.html">Snapshot</a></li>
                    <li><a href="publications/release/index.html">Release</a></li>
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
                  padding: 1.25rem 1rem;
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
    tasks.register<Exec>("generateAlgitesDocsGeneratedIndex") {
        group = "algites"
        description = "Generates the generated documentation landing page."

        outputs.file(locGeneratedDocsRoot.file("index.html"))

        commandLine(
            "python3",
            "-c",
            """
import html
import os
import pathlib
import sys

loc_repository_id = sys.argv[1]
loc_repository_name = sys.argv[2]
loc_repository_description = sys.argv[3] or None
loc_generated_docs_root = pathlib.Path(sys.argv[4]).resolve()
loc_current_publication_directory = pathlib.Path(sys.argv[5]).resolve()
loc_publication_label = sys.argv[6]
loc_index_file = pathlib.Path(sys.argv[7]).resolve()

def loc_relative_href(base_directory, target_directory):
    loc_relative_path = os.path.relpath(target_directory, base_directory).replace(os.sep, '/').strip('/')
    if not loc_relative_path:
        return 'index.html'
    return loc_relative_path + '/index.html'

loc_artifacts_href = 'artifacts/index.html'
loc_publications_href = 'publications/index.html'
loc_description_html = ''
if loc_repository_description:
    loc_description_html = '<p>' + html.escape(loc_repository_description, quote=True) + '</p>'

loc_index_file.parent.mkdir(parents=True, exist_ok=True)
loc_index_file.write_text(f'''<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>{html.escape(loc_repository_name, quote=True)} Generated Documentation</title>
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <style>
    body {{
      margin: 0;
      font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      line-height: 1.35;
      color: #1f2937;
      background: #f9fafb;
    }}

    header, main {{
      max-width: 1100px;
      margin: 0 auto;
      padding: 1rem;
    }}

    header {{
      padding-top: 1.25rem;
    }}

    h1 {{
      margin: 0 0 0.5rem 0;
      font-size: 1.65rem;
    }}

    .muted {{
      color: #6b7280;
    }}

    .card {{
      background: white;
      border: 1px solid #e5e7eb;
      border-radius: 0.75rem;
      padding: 0.75rem 0.9rem;
      margin: 0.65rem 0;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
    }}

    iframe {{
      width: 100%;
      min-height: 34vh;
      border: 1px solid #e5e7eb;
      border-radius: 0.75rem;
      background: white;
      margin-bottom: 0.85rem;
    }}

    a {{
      color: #2563eb;
    }}
  </style>
</head>
<body>
  <header>
    <h1>{html.escape(loc_repository_name, quote=True)} Generated Documentation</h1>
    <p class="muted">Repository ID: {html.escape(loc_repository_id, quote=True)}</p>
    {loc_description_html}
  </header>

  <main>
    <section class="card">
      <h2>Artifacts</h2>
      <p>
        Canonical documentation pages are organized by artifact.
        Open the artifact index directly here:
        <a href="{html.escape(loc_artifacts_href, quote=True)}">{html.escape(loc_artifacts_href, quote=True)}</a>.
      </p>
    </section>

    <iframe src="{html.escape(loc_artifacts_href, quote=True)}" title="Generated artifact documentation"></iframe>

    <section class="card">
      <h2>Publications / versions</h2>
      <p>
        Publication indexes group artifacts by publication context, such as preview branch, snapshot, or release version.
        Open the publication index directly here:
        <a href="{html.escape(loc_publications_href, quote=True)}">{html.escape(loc_publications_href, quote=True)}</a>.
      </p>
      <p class="muted">Current publication: <strong>{html.escape(loc_publication_label, quote=True)}</strong>.</p>
    </section>

    <iframe src="{html.escape(loc_publications_href, quote=True)}" title="Generated publication documentation"></iframe>

    <p><a href="../index.html">Repository documentation root</a></p>
  </main>
</body>
</html>
''', encoding='utf-8')
            """.trimIndent(),
            locAlgitesDocsRepositoryId,
            locAlgitesDocsRepositoryNameForSite,
            locAlgitesDocsRepositoryDescription ?: "",
            locGeneratedDocsRoot.asFile.absolutePath,
            locPublicationDocsRoot.asFile.absolutePath,
            locAlgitesDocsPublicationLabel,
            locGeneratedDocsRoot.file("index.html").asFile.absolutePath
        )
    }
}


if (tasks.findByName("generateAlgitesDocsPublicationGroupIndexes") == null) {
    tasks.register<Exec>("generateAlgitesDocsPublicationGroupIndexes") {
        group = "algites"
        description = "Generates index pages for Algites preview, snapshot, and release documentation groups."

        outputs.file(locGeneratedDocsRoot.file("preview/index.html"))
        outputs.file(locGeneratedDocsRoot.file("snapshot/index.html"))
        outputs.file(locGeneratedDocsRoot.file("release/index.html"))

        commandLine(
            "python3",
            "-c",
            """
import html
import pathlib
import sys

loc_generated_docs_root = pathlib.Path(sys.argv[1]).resolve()
loc_repository_name = sys.argv[2]

def loc_write_publication_group_index(group_directory, publication_kind, repository_name, descending):
    group_directory.mkdir(parents=True, exist_ok=True)
    loc_entries = [loc_path for loc_path in group_directory.iterdir() if loc_path.is_dir() and (loc_path / 'index.html').is_file()]
    loc_entries = sorted(loc_entries, key=lambda loc_path: loc_path.name.lower())
    if descending:
        loc_entries = list(reversed(loc_entries))

    loc_title = f'{repository_name} {publication_kind} documentation'
    loc_index_file = group_directory / 'index.html'
    if not loc_entries:
        loc_entries_html = f'<p>No {html.escape(publication_kind, quote=True)} documentation has been generated yet.</p>'
    else:
        loc_entries_html = '<ul>\n' + '\n'.join(
            f'                <li><a href="{html.escape(loc_entry.name, quote=True)}/index.html">{html.escape(loc_entry.name, quote=True)}</a></li>'
            for loc_entry in loc_entries
        ) + '\n              </ul>'

    loc_sort_text = 'Entries are sorted descending so newer or higher version identifiers appear first.' if descending else 'Entries are sorted ascending alphabetically.'

    loc_index_file.write_text(f'''<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>{html.escape(loc_title, quote=True)}</title>
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <style>
    body {{
      margin: 0;
      font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      line-height: 1.35;
      color: #1f2937;
      background: #f9fafb;
    }}

    main {{
      max-width: 900px;
      margin: 0 auto;
      padding: 2rem 1.5rem;
    }}

    h1 {{
      margin: 0 0 0.5rem 0;
    }}

    .muted {{
      color: #6b7280;
    }}

    .card {{
      background: white;
      border: 1px solid #e5e7eb;
      border-radius: 0.75rem;
      padding: 0.75rem 0.9rem;
      margin: 0.65rem 0;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
    }}

    a {{
      color: #2563eb;
    }}
  </style>
</head>
<body>
  <main>
    <h1>{html.escape(loc_title, quote=True)}</h1>
    <p class="muted">
      {html.escape(loc_sort_text, quote=True)}
    </p>

    <section class="card">
      <h2>Available {html.escape(publication_kind, quote=True)} publications</h2>
      {loc_entries_html}
    </section>

    <p><a href="../index.html">Back to generated documentation index</a></p>
  </main>
</body>
</html>
''', encoding='utf-8')

loc_write_publication_group_index(loc_generated_docs_root / 'preview', 'preview', loc_repository_name, False)
loc_write_publication_group_index(loc_generated_docs_root / 'snapshot', 'snapshot', loc_repository_name, True)
loc_write_publication_group_index(loc_generated_docs_root / 'release', 'release', loc_repository_name, True)
            """.trimIndent(),
            locGeneratedDocsRoot.asFile.absolutePath,
            locAlgitesDocsRepositoryNameForSite
        )
    }
}



fun AIcDocsEncodeMetadataValue(aValue: String?): String {
    return Base64.getUrlEncoder().withoutPadding().encodeToString((aValue ?: "").toByteArray(Charsets.UTF_8))
}

fun AIcDocsSerializeArtifactMetadata(aMetadata: Map<String, String?>): String {
    val locKeys = listOf(
        "path",
        "kind",
        "type",
        "name",
        "description",
        "groupId",
        "contentsModel",
        "hasGradleBuild",
        "gradleProjectPath",
        "version.lane",
        "version.revision",
        "version.qualifierKind",
        "version.qualifierLabel",
        "version.resolvedValue"
    )

    return locKeys.joinToString("|") { locKey ->
        "${locKey}:${AIcDocsEncodeMetadataValue(aMetadata[locKey])}"
    }
}

class AIcAlgitesDocsArtifactPublicationEntry(
    val locLocalArtifactId: String,
    val locPublicationKind: String,
    val locPublicationId: String,
    val locArtifactPublicationDirectory: File,
    val locDocumentationDirectories: List<File>,
    val locMetadata: Map<String, String?>
)

abstract class AIcGenerateAlgitesDocsArtifactPublicationIndexesTask : DefaultTask() {

    @get:Input
    abstract val repositoryId: Property<String>

    @get:Input
    abstract val repositoryName: Property<String>

    @get:Input
    abstract val artifactMetadataEntries: ListProperty<String>

    @get:OutputDirectory
    abstract val artifactDocsRoot: DirectoryProperty

    @get:OutputDirectory
    abstract val publicationsDocsRoot: DirectoryProperty

    @TaskAction
    fun generate() {
        val locArtifactsRootFile = artifactDocsRoot.asFile.get()
        val locPublicationsRootFile = publicationsDocsRoot.asFile.get()
        val locRepositoryId = repositoryId.get()
        val locRepositoryName = repositoryName.get()
        val locArtifactMetadataList = artifactMetadataEntries.get().map { locEntry ->
            locDecodeArtifactMetadata(locEntry)
        }

        val locArtifactPublications = locArtifactsRootFile
            .listFiles()
            ?.filter { locFile -> locFile.isDirectory }
            ?.flatMap { locArtifactDirectory ->
                val locLocalArtifactId = locArtifactDirectory.name
                locArtifactDirectory
                    .listFiles()
                    ?.filter { locFile -> locFile.isDirectory }
                    ?.flatMap { locPublicationKindDirectory ->
                        val locPublicationKind = locPublicationKindDirectory.name
                        locPublicationKindDirectory
                            .listFiles()
                            ?.filter { locFile -> locFile.isDirectory }
                            ?.map { locPublicationIdDirectory ->
                                val locPublicationId = locPublicationIdDirectory.name
                                AIcAlgitesDocsArtifactPublicationEntry(
                                    locLocalArtifactId = locLocalArtifactId,
                                    locPublicationKind = locPublicationKind,
                                    locPublicationId = locPublicationId,
                                    locArtifactPublicationDirectory = locPublicationIdDirectory,
                                    locDocumentationDirectories = locPublicationIdDirectory
                                        .listFiles()
                                        ?.filter { locFile -> locFile.isDirectory }
                                        ?.sortedBy { locFile -> locFile.name.lowercase() }
                                        ?: emptyList(),
                                    locMetadata = locMetadataFor(locArtifactMetadataList, locLocalArtifactId)
                                )
                            }
                            ?: emptyList()
                    }
                    ?: emptyList()
            }
            ?.sortedWith(
                compareBy<AIcAlgitesDocsArtifactPublicationEntry> { it.locLocalArtifactId.lowercase() }
                    .thenBy { it.locPublicationKind.lowercase() }
                    .thenBy { it.locPublicationId.lowercase() }
            )
            ?: emptyList()

        locArtifactPublications.forEach { locArtifactPublication ->
            val locArtifactGroupId = locArtifactPublication.locMetadata["groupId"]
            val locFullArtifactId = locArtifactPublication.locMetadata["artifactId"]
                ?: "${locRepositoryId}_${locArtifactPublication.locLocalArtifactId}"

            val locMetadataRows = listOf(
                "Local artifact ID" to locArtifactPublication.locLocalArtifactId,
                "Publication kind" to locArtifactPublication.locPublicationKind,
                "Publication ID" to locArtifactPublication.locPublicationId,
                "Artifact path" to locArtifactPublication.locMetadata["path"],
                "Artifact name" to locArtifactPublication.locMetadata["name"],
                "Description" to locArtifactPublication.locMetadata["description"],
                "Kind" to locArtifactPublication.locMetadata["kind"],
                "Type" to locArtifactPublication.locMetadata["type"],
                "Contents model" to locArtifactPublication.locMetadata["contentsModel"],
                "Gradle project path" to locArtifactPublication.locMetadata["gradleProjectPath"],
                "Resolved version" to locArtifactPublication.locMetadata["version.resolvedValue"],
                "Version lane" to locArtifactPublication.locMetadata["version.lane"],
                "Version revision" to locArtifactPublication.locMetadata["version.revision"],
                "Qualifier kind" to locArtifactPublication.locMetadata["version.qualifierKind"],
                "Qualifier label" to locArtifactPublication.locMetadata["version.qualifierLabel"]
            )

            val locMetadataHtml = locMetadataRows.joinToString("\n") { (locLabel, locValue) ->
                val locDisplayValue = if (locValue.isNullOrBlank()) {
                    """<span class="muted">not specified</span>"""
                } else {
                    locEscape(locValue)
                }
                "<dt>${locEscape(locLabel)}</dt><dd>${locDisplayValue}</dd>"
            }

            val locDocsLinksHtml = if (locArtifactPublication.locDocumentationDirectories.isEmpty()) {
                "<p>No generated documentation output was found for this artifact publication.</p>"
            } else {
                "<ul>\n" + locArtifactPublication.locDocumentationDirectories.joinToString("\n") { locDocumentationDirectory ->
                    """<li><a href="${locEscape(locDocumentationDirectory.name)}/index.html">${locEscape(locDocumentationDirectory.name)}</a></li>"""
                } + "\n</ul>"
            }

            locWriteHtml(
                locArtifactPublication.locArtifactPublicationDirectory.resolve("index.html"),
                "${locArtifactPublication.locLocalArtifactId} ${locArtifactPublication.locPublicationKind}/${locArtifactPublication.locPublicationId}",
                """
                <h1>${locEscape(locArtifactPublication.locLocalArtifactId)}</h1>
                <p class="muted compact-lead">Artifact publication: <code>${locEscape(locArtifactPublication.locPublicationKind)}/${locEscape(locArtifactPublication.locPublicationId)}</code></p>

                <div class="artifact-publication-grid">
                  <section class="card card-strong">
                    <h2>Artifact coordinates</h2>
                    <dl class="coordinates">
                      <dt>Group ID</dt><dd><strong><code>${locEscape(locArtifactGroupId ?: "not specified")}</code></strong></dd>
                      <dt>Artifact ID</dt><dd><strong><code>${locEscape(locFullArtifactId)}</code></strong></dd>
                    </dl>
                  </section>

                  <section class="card">
                    <h2>Artifact metadata</h2>
                    <dl class="compact-dl">
                      ${locMetadataHtml}
                    </dl>
                  </section>

                  <section class="card">
                    <h2>Generated documentation</h2>
                    ${locDocsLinksHtml}
                  </section>
                </div>

                <nav class="back-links">
                  <a href="${locRelativeHref(locArtifactPublication.locArtifactPublicationDirectory, File(locPublicationsRootFile, "${locArtifactPublication.locPublicationKind}/${locArtifactPublication.locPublicationId}"))}">Back to publication index</a>
                  <a href="${locRelativeHref(locArtifactPublication.locArtifactPublicationDirectory, locArtifactsRootFile)}">Back to artifact index</a>
                </nav>
                """.trimIndent()
            )
        }

        val locArtifactIds = locArtifactPublications
            .map { locArtifactPublication -> locArtifactPublication.locLocalArtifactId }
            .distinct()
            .sortedBy { locLocalArtifactId -> locLocalArtifactId.lowercase() }

        val locArtifactIndexHtml = if (locArtifactIds.isEmpty()) {
            "<p>No artifact documentation has been generated yet.</p>"
        } else {
            "<ul>\n" + locArtifactIds.joinToString("\n") { locArtifactId ->
                """<li><a href="${locEscape(locArtifactId)}/index.html">${locEscape(locArtifactId)}</a></li>"""
            } + "\n</ul>"
        }

        locWriteHtml(
            locArtifactsRootFile.resolve("index.html"),
            "${locRepositoryName} artifacts",
            """
            <h1>${locEscape(locRepositoryName)} artifacts</h1>
            <p class="muted">Canonical artifact documentation index.</p>
            <section class="card">
              <h2>Artifacts</h2>
              ${locArtifactIndexHtml}
            </section>
            <p><a href="../index.html">Back to generated documentation index</a></p>
            """.trimIndent()
        )

        locArtifactIds.forEach { locArtifactId ->
            val locEntries = locArtifactPublications
                .filter { locArtifactPublication -> locArtifactPublication.locLocalArtifactId == locArtifactId }
                .sortedWith(compareBy<AIcAlgitesDocsArtifactPublicationEntry> { it.locPublicationKind.lowercase() }.thenBy { it.locPublicationId.lowercase() })

            val locEntriesHtml = "<ul>\n" + locEntries.joinToString("\n") { locArtifactPublication ->
                """<li><a href="${locEscape(locArtifactPublication.locPublicationKind)}/${locEscape(locArtifactPublication.locPublicationId)}/index.html">${locEscape(locArtifactPublication.locPublicationKind)}/${locEscape(locArtifactPublication.locPublicationId)}</a></li>"""
            } + "\n</ul>"

            locWriteHtml(
                File(locArtifactsRootFile, "${locArtifactId}/index.html"),
                "${locArtifactId} publications",
                """
                <h1>${locEscape(locArtifactId)}</h1>
                <p class="muted">All generated publications for this artifact.</p>
                <section class="card">
                  <h2>Available publications</h2>
                  ${locEntriesHtml}
                </section>
                <p><a href="../index.html">Back to artifact index</a></p>
                """.trimIndent()
            )
        }

        val locPublicationKinds = locArtifactPublications
            .map { locArtifactPublication -> locArtifactPublication.locPublicationKind }
            .distinct()
            .sortedBy { locPublicationKind -> locPublicationKind.lowercase() }

        val locPublicationKindsHtml = if (locPublicationKinds.isEmpty()) {
            "<p>No publication documentation has been generated yet.</p>"
        } else {
            "<ul>\n" + locPublicationKinds.joinToString("\n") { locPublicationKind ->
                """<li><a href="${locEscape(locPublicationKind)}/index.html">${locEscape(locPublicationKind)}</a></li>"""
            } + "\n</ul>"
        }

        locWriteHtml(
            locPublicationsRootFile.resolve("index.html"),
            "${locRepositoryName} publications",
            """
            <h1>${locEscape(locRepositoryName)} publications / versions</h1>
            <p class="muted">Browse documentation by publication context, such as preview branch, snapshot, or release version.</p>
            <section class="card">
              <h2>Publication kinds</h2>
              ${locPublicationKindsHtml}
            </section>
            <p><a href="../index.html">Back to generated documentation index</a></p>
            """.trimIndent()
        )

        locPublicationKinds.forEach { locPublicationKind ->
            val locKindEntries = locArtifactPublications.filter { locArtifactPublication ->
                locArtifactPublication.locPublicationKind == locPublicationKind
            }

            val locPublicationIds = locKindEntries
                .map { locArtifactPublication -> locArtifactPublication.locPublicationId }
                .distinct()
                .sortedWith(
                    if (locPublicationKind in setOf("snapshot", "release")) {
                        compareByDescending<String> { it }
                    } else {
                        compareBy { it.lowercase() }
                    }
                )

            val locPublicationIdsHtml = "<ul>\n" + locPublicationIds.joinToString("\n") { locPublicationId ->
                """<li><a href="${locEscape(locPublicationId)}/index.html">${locEscape(locPublicationId)}</a></li>"""
            } + "\n</ul>"

            locWriteHtml(
                File(locPublicationsRootFile, "${locPublicationKind}/index.html"),
                "${locRepositoryName} ${locPublicationKind} publications",
                """
                <h1>${locEscape(locRepositoryName)} ${locEscape(locPublicationKind)} publications</h1>
                <p class="muted">Available publication IDs for this publication kind.</p>
                <section class="card">
                  <h2>Publication IDs</h2>
                  ${locPublicationIdsHtml}
                </section>
                <p><a href="../index.html">Back to publications index</a></p>
                """.trimIndent()
            )

            locPublicationIds.forEach { locPublicationId ->
                val locPublicationDirectory = File(locPublicationsRootFile, "${locPublicationKind}/${locPublicationId}")
                val locEntries = locKindEntries
                    .filter { locArtifactPublication -> locArtifactPublication.locPublicationId == locPublicationId }
                    .sortedBy { locArtifactPublication -> locArtifactPublication.locLocalArtifactId.lowercase() }

                val locArtifactsHtml = "<ul>\n" + locEntries.joinToString("\n") { locArtifactPublication ->
                    """<li><a href="${locRelativeHref(locPublicationDirectory, locArtifactPublication.locArtifactPublicationDirectory)}">${locEscape(locArtifactPublication.locLocalArtifactId)}</a></li>"""
                } + "\n</ul>"

                locWriteHtml(
                    locPublicationDirectory.resolve("index.html"),
                    "${locRepositoryName} ${locPublicationKind}/${locPublicationId}",
                    """
                    <h1>${locEscape(locRepositoryName)} ${locEscape(locPublicationKind)}/${locEscape(locPublicationId)}</h1>
                    <p class="muted">Artifacts included in this publication context.</p>
                    <section class="card">
                      <h2>Artifacts</h2>
                      ${locArtifactsHtml}
                    </section>
                    <p><a href="../index.html">Back to ${locEscape(locPublicationKind)} publications</a></p>
                    """.trimIndent()
                )
            }
        }
    }

    private fun locDecodeArtifactMetadata(aEntry: String): Map<String, String?> {
        return aEntry.split("|")
            .mapNotNull { locToken ->
                val locSeparatorIndex = locToken.indexOf(':')
                if (locSeparatorIndex < 0) {
                    null
                } else {
                    val locKey = locToken.substring(0, locSeparatorIndex)
                    val locEncodedValue = locToken.substring(locSeparatorIndex + 1)
                    val locValue = String(Base64.getUrlDecoder().decode(locEncodedValue), Charsets.UTF_8)
                    locKey to locValue.takeIf { it.isNotBlank() }
                }
            }
            .toMap()
    }

    private fun locMetadataFor(aArtifactMetadataList: List<Map<String, String?>>, aLocalArtifactId: String): Map<String, String?> {
        return aArtifactMetadataList.firstOrNull { locArtifactDirectory ->
            val locPathId = locArtifactDirectory["path"]?.trim('/', '.')?.replace("/", ".")
            val locGradleId = locArtifactDirectory["gradleProjectPath"]?.trim(':')?.replace(":", ".")
            val locName = locArtifactDirectory["name"]
            aLocalArtifactId == locPathId || aLocalArtifactId == locGradleId || aLocalArtifactId == locName
        } ?: emptyMap()
    }

    private fun locEscape(aText: String?): String {
        return (aText ?: "")
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun locRelativeHref(aBaseDirectory: File, aTargetDirectory: File): String {
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

    private fun locWriteHtml(aFile: File, aTitle: String, aBody: String) {
        aFile.parentFile.mkdirs()
        aFile.writeText(
            """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <title>${locEscape(aTitle)}</title>
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
                  max-width: 1050px;
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

                dl {
                  display: grid;
                  grid-template-columns: minmax(10rem, 18rem) 1fr;
                  gap: 0.22rem 0.75rem;
                }

                dt {
                  font-weight: 650;
                  color: #374151;
                }

                dd {
                  margin: 0;
                }

                code {
                  background: #f3f4f6;
                  border-radius: 0.25rem;
                  padding: 0.1rem 0.25rem;
                }

                a {
                  color: #2563eb;
                }
              </style>
            </head>
            <body>
              <main>
                ${aBody}
              </main>
            </body>
            </html>
            """.trimIndent(),
            Charsets.UTF_8
        )
    }
}


if (tasks.findByName("generateAlgitesDocsArtifactPublicationIndexes") == null) {
    tasks.register<AIcGenerateAlgitesDocsArtifactPublicationIndexesTask>("generateAlgitesDocsArtifactPublicationIndexes") {
        group = "algites"
        description = "Generates artifact publication pages and publication index pages from canonical artifact documentation."

        artifactDocsRoot.set(locArtifactDocsRoot)
        publicationsDocsRoot.set(locPublicationsDocsRoot)
        repositoryId.set(locAlgitesDocsRepositoryId)
        repositoryName.set(locAlgitesDocsRepositoryNameForSite)
        artifactMetadataEntries.set(
            locAlgitesDocsResolvedArtifactDirectories.map { locArtifactDirectory ->
                AIcDocsSerializeArtifactMetadata(locArtifactDirectory)
            }
        )
    }
}


if (tasks.findByName("generateAlgitesDocsSite") == null) {
    tasks.register("generateAlgitesDocsSite") {
        group = "algites"
        description = "Generic aggregate task for repository documentation site generation."

        dependsOn("generateAlgitesDocsRootIndex")
        dependsOn("generateAlgitesDocsPublicationGroupIndexes")
        dependsOn("generateAlgitesDocsArtifactPublicationIndexes")
        dependsOn("generateAlgitesDocsGeneratedIndex")
    }
}


afterEvaluate {
    val locGeneratedIndexTask = tasks.findByName("generateAlgitesDocsGeneratedIndex")
    val locPublicationGroupIndexesTask = tasks.findByName("generateAlgitesDocsPublicationGroupIndexes")
    val locArtifactPublicationIndexesTask = tasks.findByName("generateAlgitesDocsArtifactPublicationIndexes")

    listOf("generateJavaDocsSite", "generateDummyMpsDocs", "generateMpsDocs")
        .mapNotNull { locTaskName -> tasks.findByName(locTaskName) }
        .forEach { locDocumentationTask ->
            locGeneratedIndexTask?.mustRunAfter(locDocumentationTask)
            locPublicationGroupIndexesTask?.mustRunAfter(locDocumentationTask)
            locArtifactPublicationIndexesTask?.mustRunAfter(locDocumentationTask)
        }

    if (locArtifactPublicationIndexesTask != null) {
        locGeneratedIndexTask?.mustRunAfter(locArtifactPublicationIndexesTask)
    }
}
