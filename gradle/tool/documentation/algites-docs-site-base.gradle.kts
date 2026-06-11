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
 * Technology-specific scripts should generate into locPublicationDocsRoot, not
 * directly into locGeneratedDocsRoot.
 */
val locGeneratedDocsRoot = locDocsSiteRoot.dir("generated")
val locArtifactDocsRoot = locGeneratedDocsRoot.dir("artifacts")
val locPublicationsDocsRoot = locGeneratedDocsRoot.dir("publications")

val locPublicationKind = (findProperty("algites.docs.publicationKind") as String?)
    ?.trim()
    ?.takeIf { it.isNotBlank() }

val locPublicationId = (findProperty("algites.docs.publicationId") as String?)
    ?.trim()
    ?.takeIf { it.isNotBlank() }

val locPublicationDocsRoot = if (locPublicationKind != null && locPublicationId != null) {
    locPublicationsDocsRoot.dir("${locPublicationKind}/${locPublicationId}")
} else {
    locPublicationsDocsRoot
}


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


abstract class AIcGenerateAlgitesDocsArtifactPublicationIndexesTask : DefaultTask() {
    @get:Input
    abstract val repositoryId: Property<String>

    @get:Input
    abstract val repositoryName: Property<String>

    @get:Input
    abstract val artifactMetadataEntries: ListProperty<String>

    @get:OutputDirectory
    abstract val generatedDocsRoot: DirectoryProperty

    @get:OutputDirectory
    abstract val artifactDocsRoot: DirectoryProperty

    @get:OutputDirectory
    abstract val publicationsDocsRoot: DirectoryProperty

    @TaskAction
    fun generate() {
        val locArtifactRootFile = artifactDocsRoot.get().asFile
        val locPublicationsRootFile = publicationsDocsRoot.get().asFile
        val locRepositoryId = repositoryId.get()
        val locRepositoryName = repositoryName.get()

        locArtifactRootFile.mkdirs()
        locPublicationsRootFile.mkdirs()

        val locMetadataByLocalArtifactId = artifactMetadataEntries.get()
            .mapNotNull { locLine ->
                val locParts = locLine.split('\t')
                if (locParts.size < 15) {
                    null
                } else {
                    val locMap = mapOf(
                        "localArtifactId" to locParts[0],
                        "artifactId" to locParts[1],
                        "groupId" to locParts[2],
                        "path" to locParts[3],
                        "name" to locParts[4],
                        "description" to locParts[5],
                        "kind" to locParts[6],
                        "type" to locParts[7],
                        "contentsModel" to locParts[8],
                        "gradleProjectPath" to locParts[9],
                        "version.resolvedValue" to locParts[10],
                        "version.lane" to locParts[11],
                        "version.revision" to locParts[12],
                        "version.qualifierKind" to locParts[13],
                        "version.qualifierLabel" to locParts[14]
                    )
                    locParts[0] to locMap
                }
            }
            .toMap()

        data class LocalArtifactPublication(
            val localArtifactId: String,
            val publicationKind: String,
            val publicationId: String,
            val directory: File,
            val metadata: Map<String, String>
        )

        fun readSidecar(aDirectory: File): Map<String, String> {
            val locSidecarFile = aDirectory.resolve(".algites-artifact-docs.properties")
            if (!locSidecarFile.isFile) {
                return emptyMap()
            }

            return locSidecarFile.readLines(Charsets.UTF_8)
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .mapNotNull { locLine ->
                    val locSeparatorIndex = locLine.indexOf('=')
                    if (locSeparatorIndex < 0) {
                        null
                    } else {
                        locLine.substring(0, locSeparatorIndex) to locLine.substring(locSeparatorIndex + 1)
                    }
                }
                .toMap()
        }

        fun html(aText: String?): String {
            return (aText ?: "")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
        }

        fun valueOrDash(aValue: String?): String = aValue?.takeIf { it.isNotBlank() } ?: "—"

        fun relativeHref(aBaseDirectory: File, aTargetDirectory: File): String {
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

        fun writeArtifactPublicationIndex(aPublication: LocalArtifactPublication) {
            val locMetadata = aPublication.metadata
            val locIndexFile = aPublication.directory.resolve("index.html")
            val locLocalArtifactId = aPublication.localArtifactId
            val locGroupId = valueOrDash(locMetadata["groupId"])
            val locArtifactId = valueOrDash(
                locMetadata["artifactId"]?.takeIf { it.isNotBlank() }
                    ?: "${locRepositoryId}_${locLocalArtifactId}"
            )
            val locVersion = valueOrDash(locMetadata["version.resolvedValue"])

            val locDocumentationLinks = listOf("javadoc" to "Javadoc", "mpsdoc" to "MPS documentation")
                .filter { (locDirectoryName, _) -> aPublication.directory.resolve(locDirectoryName).isDirectory }
                .joinToString("\n") { (locDirectoryName, locLabel) ->
                    """<li><a href="${html(locDirectoryName)}/index.html">${html(locLabel)}</a></li>"""
                }
                .ifBlank { "<li>No generated documentation output was found for this artifact publication yet.</li>" }

            locIndexFile.writeText(
                """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>${html(locLocalArtifactId)} ${html(aPublication.publicationKind)}/${html(aPublication.publicationId)}</title>
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <style>
                    body { margin: 0; font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; line-height: 1.35; color: #1f2937; background: #f9fafb; }
                    main { max-width: 1180px; margin: 0 auto; padding: 1rem; }
                    h1 { margin: 0 0 0.5rem 0; font-size: 1.55rem; }
                    h2 { margin: 0 0 0.55rem 0; font-size: 1.05rem; }
                    .muted { color: #6b7280; }
                    .layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(18rem, 24rem); gap: 0.85rem; align-items: start; }
                    .main-column { display: flex; flex-direction: column; gap: 0.85rem; min-width: 0; }
                    .metadata-column { min-width: 0; }
                    .card { background: white; border: 1px solid #e5e7eb; border-radius: 0.65rem; padding: 0.8rem 0.95rem; box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04); }
                    .coordinates, .metadata { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 0.25rem 0.75rem; align-items: baseline; }
                    .metadata { gap: 0.25rem 0.65rem; font-size: 0.9rem; }
                    .coordinates dt, .metadata dt { font-weight: 650; color: #374151; }
                    .coordinates dd, .metadata dd { margin: 0; min-width: 0; overflow-wrap: anywhere; }
                    .coordinates dd { font-weight: 650; }
                    ul { margin: 0.25rem 0 0 1.2rem; padding: 0; }
                    li { margin: 0.2rem 0; }
                    a { color: #2563eb; }
                    @media (max-width: 820px) { .layout { display: block; } .metadata-column { margin-top: 0.85rem; } }
                  </style>
                </head>
                <body>
                  <main>
                    <h1>${html(locLocalArtifactId)}</h1>
                    <p class="muted">Publication: <strong>${html(aPublication.publicationKind)}/${html(aPublication.publicationId)}</strong></p>
                    <div class="layout">
                      <div class="main-column">
                        <section class="card">
                          <h2>Artifact coordinates</h2>
                          <dl class="coordinates">
                            <dt>Group ID</dt><dd>${html(locGroupId)}</dd>
                            <dt>Artifact ID</dt><dd>${html(locArtifactId)}</dd>
                            <dt>Version</dt><dd>${html(locVersion)}</dd>
                          </dl>
                        </section>
                        <section class="card">
                          <h2>Generated documentation</h2>
                          <ul>${locDocumentationLinks}</ul>
                        </section>
                      </div>
                      <aside class="metadata-column">
                        <section class="card">
                          <h2>Metadata</h2>
                          <dl class="metadata">
                            <dt>Local artifact ID</dt><dd>${html(locLocalArtifactId)}</dd>
                            <dt>Name</dt><dd>${html(valueOrDash(locMetadata["name"]))}</dd>
                            <dt>Description</dt><dd>${html(valueOrDash(locMetadata["description"]))}</dd>
                            <dt>Kind</dt><dd>${html(valueOrDash(locMetadata["kind"]))}</dd>
                            <dt>Type</dt><dd>${html(valueOrDash(locMetadata["type"]))}</dd>
                            <dt>Contents model</dt><dd>${html(valueOrDash(locMetadata["contentsModel"]))}</dd>
                            <dt>Source path</dt><dd>${html(valueOrDash(locMetadata["path"]))}</dd>
                            <dt>Gradle project path</dt><dd>${html(valueOrDash(locMetadata["gradleProjectPath"]))}</dd>
                            <dt>Version lane</dt><dd>${html(valueOrDash(locMetadata["version.lane"]))}</dd>
                            <dt>Version revision</dt><dd>${html(valueOrDash(locMetadata["version.revision"]))}</dd>
                            <dt>Qualifier kind</dt><dd>${html(valueOrDash(locMetadata["version.qualifierKind"]))}</dd>
                            <dt>Qualifier label</dt><dd>${html(valueOrDash(locMetadata["version.qualifierLabel"]))}</dd>
                          </dl>
                        </section>
                      </aside>
                    </div>
                    <p><a href="${html(relativeHref(aPublication.directory, locPublicationsRootFile.resolve(aPublication.publicationKind).resolve(aPublication.publicationId)))}">Back to publication index</a></p>
                  </main>
                </body>
                </html>
                """.trimIndent(),
                Charsets.UTF_8
            )
        }

        val locPublications = locArtifactRootFile
            .listFiles()
            ?.filter { it.isDirectory }
            ?.flatMap { locArtifactDirectory ->
                locArtifactDirectory.listFiles()
                    ?.filter { it.isDirectory }
                    ?.flatMap { locPublicationKindDirectory ->
                        locPublicationKindDirectory.listFiles()
                            ?.filter { it.isDirectory }
                            ?.map { locPublicationIdDirectory ->
                                val locLocalArtifactId = locArtifactDirectory.name
                                val locSidecarMetadata = readSidecar(locPublicationIdDirectory)
                                val locBaseMetadata = locMetadataByLocalArtifactId[locLocalArtifactId] ?: emptyMap()
                                LocalArtifactPublication(
                                    localArtifactId = locLocalArtifactId,
                                    publicationKind = locPublicationKindDirectory.name,
                                    publicationId = locPublicationIdDirectory.name,
                                    directory = locPublicationIdDirectory,
                                    metadata = locBaseMetadata + locSidecarMetadata
                                )
                            }
                            ?: emptyList()
                    }
                    ?: emptyList()
            }
            ?.sortedWith(compareBy<LocalArtifactPublication> { it.publicationKind }.thenBy { it.publicationId }.thenBy { it.localArtifactId })
            ?: emptyList()

        locPublications.forEach { writeArtifactPublicationIndex(it) }

        locArtifactRootFile.resolve("index.html").writeText(
            """
            <!doctype html>
            <html lang="en"><head><meta charset="utf-8"><title>${html(locRepositoryName)} artifacts</title><meta name="viewport" content="width=device-width, initial-scale=1"><style>body{margin:0;font-family:system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;line-height:1.35;color:#1f2937;background:#f9fafb}main{max-width:980px;margin:0 auto;padding:1rem}.card{background:white;border:1px solid #e5e7eb;border-radius:.65rem;padding:.8rem .95rem;margin:.75rem 0}li{margin:.25rem 0}a{color:#2563eb}</style></head>
            <body><main><h1>${html(locRepositoryName)} artifacts</h1><section class="card">${
                if (locPublications.isEmpty()) {
                    "<p>No artifact documentation has been generated yet.</p>"
                } else {
                    "<ul>\n" + locPublications.groupBy { it.localArtifactId }.toSortedMap().entries.joinToString("\n") { (locLocalArtifactId, locArtifactPublications) ->
                        "<li><strong>${html(locLocalArtifactId)}</strong><ul>" + locArtifactPublications.joinToString("") { locPublication ->
                            """<li><a href="${html(locLocalArtifactId)}/${html(locPublication.publicationKind)}/${html(locPublication.publicationId)}/index.html">${html(locPublication.publicationKind)}/${html(locPublication.publicationId)}</a></li>"""
                        } + "</ul></li>"
                    } + "\n</ul>"
                }
            }</section><p><a href="../index.html">Back to generated documentation index</a></p></main></body></html>
            """.trimIndent(),
            Charsets.UTF_8
        )

        val locPublicationGroups = locPublications.groupBy { it.publicationKind to it.publicationId }
        locPublicationGroups.forEach { (locPublicationKey, locEntries) ->
            val (locPublicationKind, locPublicationId) = locPublicationKey
            val locPublicationDirectory = locPublicationsRootFile.resolve(locPublicationKind).resolve(locPublicationId)
            locPublicationDirectory.mkdirs()
            locPublicationDirectory.resolve("index.html").writeText(
                """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8"><title>${html(locRepositoryName)} ${html(locPublicationKind)}/${html(locPublicationId)}</title><meta name="viewport" content="width=device-width, initial-scale=1"><style>body{margin:0;font-family:system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;line-height:1.35;color:#1f2937;background:#f9fafb}main{max-width:980px;margin:0 auto;padding:1rem}.card{background:white;border:1px solid #e5e7eb;border-radius:.65rem;padding:.8rem .95rem;margin:.75rem 0}li{margin:.25rem 0}a{color:#2563eb}</style></head>
                <body><main><h1>${html(locRepositoryName)} ${html(locPublicationKind)}/${html(locPublicationId)}</h1><section class="card"><h2>Artifacts</h2><ul>${
                    locEntries.sortedBy { it.localArtifactId }.joinToString("\n") { locPublication ->
                        val locHref = relativeHref(locPublicationDirectory, locPublication.directory)
                        """<li><a href="${html(locHref)}">${html(locPublication.localArtifactId)}</a></li>"""
                    }
                }</ul></section><p><a href="../../index.html">Back to publications index</a></p></main></body></html>
                """.trimIndent(),
                Charsets.UTF_8
            )
        }

        locPublicationsRootFile.resolve("index.html").writeText(
            """
            <!doctype html>
            <html lang="en"><head><meta charset="utf-8"><title>${html(locRepositoryName)} publications</title><meta name="viewport" content="width=device-width, initial-scale=1"><style>body{margin:0;font-family:system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;line-height:1.35;color:#1f2937;background:#f9fafb}main{max-width:980px;margin:0 auto;padding:1rem}.card{background:white;border:1px solid #e5e7eb;border-radius:.65rem;padding:.8rem .95rem;margin:.75rem 0}li{margin:.25rem 0}a{color:#2563eb}</style></head>
            <body><main><h1>${html(locRepositoryName)} publications / versions</h1><section class="card">${
                if (locPublicationGroups.isEmpty()) {
                    "<p>No publication documentation has been generated yet.</p>"
                } else {
                    "<ul>\n" + locPublicationGroups.keys.sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second }).joinToString("\n") { (locPublicationKind, locPublicationId) ->
                        """<li><a href="${html(locPublicationKind)}/${html(locPublicationId)}/index.html">${html(locPublicationKind)}/${html(locPublicationId)}</a></li>"""
                    } + "\n</ul>"
                }
            }</section><p><a href="../index.html">Back to generated documentation index</a></p></main></body></html>
            """.trimIndent(),
            Charsets.UTF_8
        )

        logger.lifecycle("Generated Algites artifact/publication indexes for ${locPublications.size} artifact publication(s).")
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

        dependsOn("generateAlgitesDocsArtifactPublicationIndexes")
        outputs.file(locGeneratedDocsRoot.file("index.html"))

        commandLine(
            "python3",
            "-c",
            """
import html
import pathlib
import sys

loc_repository_id = sys.argv[1]
loc_repository_name = sys.argv[2]
loc_repository_description = sys.argv[3] or None
loc_artifacts_root = pathlib.Path(sys.argv[4]).resolve()
loc_publications_root = pathlib.Path(sys.argv[5]).resolve()
loc_index_file = pathlib.Path(sys.argv[6]).resolve()

def loc_has_index(path):
    return path.is_dir() and (path / 'index.html').is_file()

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
    body {{ margin: 0; font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; line-height: 1.35; color: #1f2937; background: #f9fafb; }}
    header, main {{ max-width: 1120px; margin: 0 auto; padding: 1rem; }}
    header {{ padding-top: 1.4rem; }}
    h1 {{ margin: 0 0 0.35rem 0; font-size: 1.65rem; }}
    h2 {{ margin: 0 0 0.5rem 0; font-size: 1.1rem; }}
    .muted {{ color: #6b7280; }}
    .card {{ background: white; border: 1px solid #e5e7eb; border-radius: 0.65rem; padding: 0.85rem 1rem; margin: 0.8rem 0; box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04); }}
    iframe {{ width: 100%; min-height: 18rem; border: 1px solid #e5e7eb; border-radius: 0.65rem; background: white; }}
    a {{ color: #2563eb; }}
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
      <p>Browse canonical documentation pages by local artifact ID.</p>
      <p><a href="artifacts/index.html">Open artifacts index</a></p>
      {('<iframe src="artifacts/index.html" title="Artifacts"></iframe>') if loc_has_index(loc_artifacts_root) else '<p>No artifact index has been generated yet.</p>'}
    </section>

    <section class="card">
      <h2>Publications / versions</h2>
      <p>Browse repository publication contexts such as preview, snapshot, or release.</p>
      <p><a href="publications/index.html">Open publications index</a></p>
      {('<iframe src="publications/index.html" title="Publications"></iframe>') if loc_has_index(loc_publications_root) else '<p>No publication index has been generated yet.</p>'}
    </section>

    <p><a href="../index.html">Repository documentation root</a></p>
  </main>
</body>
</html>
f''', encoding='utf-8')
            """.trimIndent(),
            locAlgitesDocsRepositoryId,
            locAlgitesDocsRepositoryNameForSite,
            locAlgitesDocsRepositoryDescription ?: "",
            locArtifactDocsRoot.asFile.absolutePath,
            locPublicationsDocsRoot.asFile.absolutePath,
            locGeneratedDocsRoot.file("index.html").asFile.absolutePath
        )
    }
}


if (tasks.findByName("generateAlgitesDocsPublicationGroupIndexes") == null) {
    tasks.register<Exec>("generateAlgitesDocsPublicationGroupIndexes") {
        group = "algites"
        description = "Generates index pages for Algites preview, snapshot, and release documentation groups."

        outputs.file(locPublicationsDocsRoot.file("preview/index.html"))
        outputs.file(locPublicationsDocsRoot.file("snapshot/index.html"))
        outputs.file(locPublicationsDocsRoot.file("release/index.html"))

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
      line-height: 1.5;
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
      padding: 1rem 1.25rem;
      margin: 1rem 0;
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
            locPublicationsDocsRoot.asFile.absolutePath,
            locAlgitesDocsRepositoryNameForSite
        )
    }
}


fun AIcDocsLocalArtifactIdFromPath(aPath: String?): String {
    return aPath?.trim()?.trim('/')?.replace('/', '.')?.takeIf { it.isNotBlank() } ?: "."
}

fun AIcDocsArtifactMetadataEntryLine(aArtifactDirectory: Map<String, String?>): String {
    val locLocalArtifactId = AIcDocsLocalArtifactIdFromPath(aArtifactDirectory["path"])
    val locArtifactId = "${locAlgitesDocsRepositoryId}_${locLocalArtifactId}"
    return listOf(
        locLocalArtifactId,
        locArtifactId,
        aArtifactDirectory["groupId"] ?: "",
        aArtifactDirectory["path"] ?: "",
        aArtifactDirectory["name"] ?: "",
        aArtifactDirectory["description"] ?: "",
        aArtifactDirectory["kind"] ?: "",
        aArtifactDirectory["type"] ?: "",
        aArtifactDirectory["contentsModel"] ?: "",
        aArtifactDirectory["gradleProjectPath"] ?: "",
        aArtifactDirectory["version.resolvedValue"] ?: "",
        aArtifactDirectory["version.lane"] ?: "",
        aArtifactDirectory["version.revision"] ?: "",
        aArtifactDirectory["version.qualifierKind"] ?: "",
        aArtifactDirectory["version.qualifierLabel"] ?: ""
    ).joinToString("\t") { it.replace('\t', ' ') }
}

val locAlgitesDocsArtifactMetadataEntryLines = locAlgitesDocsResolvedArtifactDirectories
    .filter { locArtifactDirectory -> locArtifactDirectory["kind"] != "repository" }
    .map { locArtifactDirectory -> AIcDocsArtifactMetadataEntryLine(locArtifactDirectory) }

if (tasks.findByName("generateAlgitesDocsArtifactPublicationIndexes") == null) {
    tasks.register<AIcGenerateAlgitesDocsArtifactPublicationIndexesTask>("generateAlgitesDocsArtifactPublicationIndexes") {
        group = "algites"
        description = "Generates canonical artifact pages and publication index pages."

        repositoryId.set(locAlgitesDocsRepositoryId)
        repositoryName.set(locAlgitesDocsRepositoryNameForSite)
        artifactMetadataEntries.set(locAlgitesDocsArtifactMetadataEntryLines)
        generatedDocsRoot.set(locGeneratedDocsRoot)
        artifactDocsRoot.set(locArtifactDocsRoot)
        publicationsDocsRoot.set(locPublicationsDocsRoot)
    }
}


if (tasks.findByName("generateAlgitesDocsSite") == null) {
    tasks.register("generateAlgitesDocsSite") {
        group = "algites"
        description = "Generic aggregate task for repository documentation site generation."

        dependsOn("generateAlgitesDocsRootIndex")
        dependsOn("generateAlgitesDocsArtifactPublicationIndexes")
        dependsOn("generateAlgitesDocsPublicationGroupIndexes")
        dependsOn("generateAlgitesDocsGeneratedIndex")
    }
}


afterEvaluate {
    val locGeneratedIndexTask = tasks.findByName("generateAlgitesDocsGeneratedIndex")
    val locArtifactPublicationIndexesTask = tasks.findByName("generateAlgitesDocsArtifactPublicationIndexes")
    val locPublicationGroupIndexesTask = tasks.findByName("generateAlgitesDocsPublicationGroupIndexes")

    listOf("generateJavaDocsSite", "generateDummyMpsDocs")
        .mapNotNull { locTaskName -> tasks.findByName(locTaskName) }
        .forEach { locDocumentationTask ->
            locArtifactPublicationIndexesTask?.mustRunAfter(locDocumentationTask)
            locGeneratedIndexTask?.mustRunAfter(locDocumentationTask)
            locPublicationGroupIndexesTask?.mustRunAfter(locDocumentationTask)
        }
}
