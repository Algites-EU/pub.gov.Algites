/*
 * Algites shared Java documentation site script.
 *
 * Intended location in governance repository:
 *   gradle/tool/documentation/algites-docs-site-java.gradle.kts
 *
 * A repository can apply only this script; it automatically applies the base
 * documentation-site script.
 */

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions

data class AIcJavaDocsSiteEntry(
    val locModulePath: String,
    val locJavadocOutputDirectory: File,
    val locArtifactPublicationDirectory: File,
    val locArtifactMetadata: Map<String, String>
) : java.io.Serializable

class AIcGenerateJavaDocsSiteAction(
    private val locRepositoryId: String,
    private val locArtifactDocsRootFile: File,
    private val locJavaDocsSiteEntries: List<AIcJavaDocsSiteEntry>
) : Action<Task>, java.io.Serializable {

    override fun execute(aTask: Task) {
        val locDocumentedProjects = locJavaDocsSiteEntries.mapNotNull { locEntry ->
            val locJavadocOutputDirectory = locEntry.locJavadocOutputDirectory

            if (!locJavadocOutputDirectory.isDirectory) {
                return@mapNotNull null
            }

            val locArtifactPublicationDirectory = locEntry.locArtifactPublicationDirectory
            val locTargetDirectory = File(locArtifactPublicationDirectory, "javadoc")

            locTargetDirectory.deleteRecursively()
            locJavadocOutputDirectory.copyRecursively(locTargetDirectory, overwrite = true)

            AIcWriteArtifactMetadataSidecar(locArtifactPublicationDirectory, locEntry.locArtifactMetadata)

            locEntry.locModulePath
        }.sorted()

        aTask.logger.lifecycle("Java documentation generated at: ${locArtifactDocsRootFile.absolutePath}")
        aTask.logger.lifecycle("Documented Java artifact(s): ${locDocumentedProjects.size}")
    }

    private fun AIcWriteArtifactMetadataSidecar(
        aArtifactPublicationDirectory: File,
        aArtifactMetadata: Map<String, String>
    ) {
        aArtifactPublicationDirectory.mkdirs()

        aArtifactPublicationDirectory
            .resolve(".algites-artifact-docs.properties")
            .writeText(
                aArtifactMetadata.entries
                    .sortedBy { it.key }
                    .joinToString(System.lineSeparator()) { locEntry ->
                        "${locEntry.key}=${locEntry.value.replace(System.lineSeparator(), " ")}"
                    } + System.lineSeparator(),
                Charsets.UTF_8
            )
    }
}

val locAlgitesDocsBaseScript = (findProperty("algites.docs.baseScript") as String?)
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/algites-docs-site-base.gradle.kts"

apply(from = uri(locAlgitesDocsBaseScript))

val locArtifactDocsRoot = layout.projectDirectory.dir(
    (extra.properties["algitesArtifactDocsRootPath"] as String?)
        ?: (findProperty("algites.docs.artifactRoot") as String?)
        ?: "docs-site/generated/artifacts"
)
val locArtifactDocsRootFile = locArtifactDocsRoot.asFile
val locPublicationKind = (extra.properties["algitesDocsPublicationKind"] as String?) ?: "generated"
val locPublicationId = (extra.properties["algitesDocsPublicationId"] as String?) ?: "current"
val locJavaDocsRepositoryId = (extra.properties["algitesDocsResolvedRepositoryId"] as String?) ?: rootProject.name

@Suppress("UNCHECKED_CAST")
val locAlgitesDocsResolvedArtifactDirectories =
    (extra.properties["algitesDocsResolvedArtifactDirectories"] as? List<Map<String, String?>>)
        ?: (rootProject.extra.properties["algitesDocsResolvedArtifactDirectories"] as? List<Map<String, String?>>)
        ?: emptyList()

fun Project.AIcResolveJavaModulePath(): String {
    return path.removePrefix(":").replace(":", ".")
}

fun Project.AIcResolveSourceRelativePath(): String {
    return rootProject.projectDir.toPath()
        .relativize(projectDir.toPath())
        .toString()
        .replace(File.separatorChar, '/')
}

fun AIcFindJavaArtifactMetadata(aSubproject: Project, aModulePath: String): Map<String, String?> {
    val locProjectPath = aSubproject.path
    val locSourceRelativePath = aSubproject.AIcResolveSourceRelativePath()

    return locAlgitesDocsResolvedArtifactDirectories.firstOrNull { locArtifactDirectory ->
        locArtifactDirectory["gradleProjectPath"] == locProjectPath ||
            locArtifactDirectory["path"] == locSourceRelativePath ||
            locArtifactDirectory["path"]?.replace('/', '.') == aModulePath
    } ?: emptyMap()
}

fun AIcBuildJavaArtifactMetadata(
    aSubproject: Project,
    aModulePath: String
): Map<String, String> {
    val locResolvedMetadata = AIcFindJavaArtifactMetadata(aSubproject, aModulePath)
    val locArtifactId = "${locJavaDocsRepositoryId}_${aModulePath}"

    return mapOf(
        "localArtifactId" to aModulePath,
        "artifactId" to locArtifactId,
        "groupId" to (locResolvedMetadata["groupId"] ?: ""),
        "path" to (locResolvedMetadata["path"] ?: aSubproject.AIcResolveSourceRelativePath()),
        "name" to (locResolvedMetadata["name"] ?: aModulePath),
        "description" to (locResolvedMetadata["description"] ?: ""),
        "kind" to (locResolvedMetadata["kind"] ?: "artifact"),
        "type" to (locResolvedMetadata["type"] ?: "java"),
        "contentsModel" to (locResolvedMetadata["contentsModel"] ?: ""),
        "gradleProjectPath" to (locResolvedMetadata["gradleProjectPath"] ?: aSubproject.path),
        "version.resolvedValue" to (locResolvedMetadata["version.resolvedValue"] ?: ""),
        "version.lane" to (locResolvedMetadata["version.lane"] ?: ""),
        "version.revision" to (locResolvedMetadata["version.revision"] ?: ""),
        "version.qualifierKind" to (locResolvedMetadata["version.qualifierKind"] ?: ""),
        "version.qualifierLabel" to (locResolvedMetadata["version.qualifierLabel"] ?: "")
    )
}

val locJavaDocsSiteEntries = mutableListOf<AIcJavaDocsSiteEntry>()

val locGenerateJavaDocsSite = tasks.register("generateJavaDocsSite") {
    group = "algites"
    description = "Generates and stages Java Javadoc into the Algites documentation site."

    dependsOn("generateAlgitesDocsRootIndex")

    outputs.dir(locArtifactDocsRootFile)

    doLast(
        AIcGenerateJavaDocsSiteAction(
            locJavaDocsRepositoryId,
            locArtifactDocsRootFile,
            locJavaDocsSiteEntries
        )
    )
}

subprojects.forEach { locSubproject ->
    locSubproject.plugins.withId("java") {
        val locJavadocTaskProvider = locSubproject.tasks.named("javadoc", Javadoc::class.java)

        locGenerateJavaDocsSite.configure {
            dependsOn(locJavadocTaskProvider)
        }

        locJavadocTaskProvider.configure {
            (options as? StandardJavadocDocletOptions)?.apply {
                /* Accept the legacy Algites @date block tag used in source Javadocs. */
                tags("date:a:Date:")

                /* Keep generated documentation tolerant of existing source comments. */
                addBooleanOption("Xdoclint:none", true)
            }

            val locJavadocOutputDirectory = destinationDir ?: return@configure
            val locModulePath = locSubproject.AIcResolveJavaModulePath()
            val locArtifactPublicationDirectory = File(
                locArtifactDocsRootFile,
                "${locModulePath}/${locPublicationKind}/${locPublicationId}"
            )

            locJavaDocsSiteEntries.add(
                AIcJavaDocsSiteEntry(
                    locModulePath = locModulePath,
                    locJavadocOutputDirectory = locJavadocOutputDirectory,
                    locArtifactPublicationDirectory = locArtifactPublicationDirectory,
                    locArtifactMetadata = AIcBuildJavaArtifactMetadata(locSubproject, locModulePath)
                )
            )
        }
    }
}

tasks.named("generateAlgitesDocsSite") {
    dependsOn(locGenerateJavaDocsSite)
}
