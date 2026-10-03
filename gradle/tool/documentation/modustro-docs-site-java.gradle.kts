/*
 * Algites shared Java documentation site script.
 *
 * Intended location in governance repository:
 *   gradle/tool/documentation/modustro-docs-site-java.gradle.kts
 *
 * A repository can apply only this script; it automatically applies the base
 * documentation-site script.
 */

import eu.algites.pltf.modustro.builder.capability.AIcBuiltinCapabilityDemandPlanner
import eu.algites.pltf.modustro.builder.model.AInModelScope
import eu.algites.pltf.modustro.builder.model.capability.AIcCapabilityDemand
import eu.algites.pltf.modustro.builder.model.capability.AIcCapabilityDemandKey
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions

data class AIcdJavaDocsSiteEntry(
    val locModulePath: String,
    val locJavadocOutputDirectory: File,
    val locArtifactPublicationDirectory: File,
    val locArtifactMetadata: Map<String, String>
) : java.io.Serializable

class AIcGenerateJavaDocsSiteAction(
    private val locRepositoryId: String,
    private val locArtifactDocsRootFile: File,
    private val locJavaDocsSiteEntries: List<AIcdJavaDocsSiteEntry>
) : Action<Task>, java.io.Serializable {

    override fun execute(aTask: Task) {
        val locDocumentedProjects = locJavaDocsSiteEntries.mapNotNull { locEntry ->
            val locJavadocOutputDirectory = locEntry.locJavadocOutputDirectory

            if (!locJavadocOutputDirectory.isDirectory) {
                return@mapNotNull null
            }

            val locArtifactPublicationDirectory = locEntry.locArtifactPublicationDirectory
            val locTargetDirectory = File(locArtifactPublicationDirectory, "java")

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
        val locSidecarFile = aArtifactPublicationDirectory.resolve(".modustro-artifact-docs.properties")
        val locExistingMetadata = if (locSidecarFile.isFile) {
            locSidecarFile.readLines(Charsets.UTF_8)
                .mapNotNull { locLine ->
                    val locSeparatorIndex = locLine.indexOf('=')
                    if (locSeparatorIndex < 0) null else
                        locLine.substring(0, locSeparatorIndex) to locLine.substring(locSeparatorIndex + 1)
                }
                .toMap()
        } else {
            emptyMap()
        }
        val locMergedMetadata = locExistingMetadata + aArtifactMetadata

        locSidecarFile.writeText(
            locMergedMetadata.entries
                .sortedBy { it.key }
                .joinToString(System.lineSeparator()) { locEntry ->
                    "${locEntry.key}=${locEntry.value.replace(System.lineSeparator(), " ")}"
                } + System.lineSeparator(),
            Charsets.UTF_8
        )
    }
}

val locAlgitesDocsBaseScript = (findProperty("modustro.docs.baseScript") as String?)
    ?: rootProject.file("gradle/tool/documentation/modustro-docs-site-base.gradle.kts")
        .takeIf { it.isFile }?.toURI()?.toString()
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/modustro-docs-site-base.gradle.kts"

apply(from = uri(locAlgitesDocsBaseScript))

val locArtifactDocsRoot = layout.projectDirectory.dir(
    (extra.properties["modustroArtifactDocsRootPath"] as String?)
        ?: (findProperty("modustro.docs.artifactRoot") as String?)
        ?: "build/run/bld/algites-docs/site/generated/artifacts"
)
val locArtifactDocsRootFile = locArtifactDocsRoot.asFile
val locPublicationKind = (extra.properties["modustroDocsPublishingKind"] as String?) ?: "generated"
val locPublicationId = (extra.properties["modustroDocsPublishingId"] as String?) ?: "current"
val locJavaDocsRepositoryId = (extra.properties["modustroDocsResolvedRepositoryId"] as String?) ?: rootProject.name

@Suppress("UNCHECKED_CAST")
val locAlgitesDocsResolvedArtifactDirectories =
    (extra.properties["modustroDocsResolvedArtifactDirectories"] as? List<Map<String, String?>>)
        ?: (rootProject.extra.properties["modustroDocsResolvedArtifactDirectories"] as? List<Map<String, String?>>)
        ?: emptyList()

@Suppress("UNCHECKED_CAST")
val locAlgitesDocsPublicationMetadata =
    (extra.properties["modustroDocsPublishingMetadata"] as? Map<String, String>)
        ?: (rootProject.extra.properties["modustroDocsPublishingMetadata"] as? Map<String, String>)
        ?: emptyMap()

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
        "structureKind" to (locResolvedMetadata["structureKind"] ?: "artifact"),
        "technologyKinds" to (locResolvedMetadata["technologyKinds"] ?: "java"),
        "contentsModel" to (locResolvedMetadata["contentsModel"] ?: ""),
        "gradleProjectPath" to (locResolvedMetadata["gradleProjectPath"] ?: aSubproject.path),
        "version.resolvedValue" to (locResolvedMetadata["version.resolvedValue"] ?: ""),
        "version.lane" to (locResolvedMetadata["version.lane"] ?: ""),
        "version.revision" to (locResolvedMetadata["version.revision"] ?: ""),
        "version.qualifierKind" to (locResolvedMetadata["version.qualifierKind"] ?: ""),
        "java.maven.groupId" to (locResolvedMetadata["groupId"] ?: ""),
        "java.maven.artifactId" to locArtifactId,
        "java.maven.version" to (locResolvedMetadata["version.resolvedValue"] ?: "")
    ) + locAlgitesDocsPublicationMetadata
}

val locJavaDocsSiteEntries = mutableListOf<AIcdJavaDocsSiteEntry>()
val locJavaDocsCapabilityPlanner = AIcBuiltinCapabilityDemandPlanner()

val locGenerateJavaDocsSite = tasks.register("generateJavaDocsSite") {
    group = "modustro"
    description = "Generates and stages Java Javadoc into the Modustro documentation site."

    dependsOn("prepareModustroDocsPublishing")
    dependsOn("generateModustroDocsRootIndex")

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
    val locModulePath = locSubproject.AIcResolveJavaModulePath()
    val locResolvedMetadata = AIcFindJavaArtifactMetadata(locSubproject, locModulePath)
    val locDeclaredTechnologyKinds = locResolvedMetadata["technologyKinds"]
        ?.split(',')
        ?.map { it.trim().lowercase() }
        ?.filter { it.isNotBlank() }
        ?.toSet()
        ?: emptySet()

    if ("java" !in locDeclaredTechnologyKinds) {
        return@forEach
    }

    val locNativeDocumentationGraph = locJavaDocsCapabilityPlanner.createDemandGraph(
        locResolvedMetadata["path"]?.takeIf { it.isNotBlank() } ?: locSubproject.path,
        emptyList(),
        listOf(
            AIcCapabilityDemand(
                AIcCapabilityDemandKey(
                    "java",
                    "generation_of_native_documentation",
                    AInModelScope.ARTIFACT,
                    locResolvedMetadata["path"]?.takeIf { it.isNotBlank() } ?: locSubproject.path
                ),
                setOf("docs_site_content")
            )
        )
    )
    val locNativeDocumentationCapabilityIds = locNativeDocumentationGraph.topologicalOrder()
        .map { locDemand -> locDemand.key().capabilityId() }
        .toSet()

    gradle.projectsEvaluated {
        val locDocumentationDependencies = buildList {
            if ("source_native_processing" in locNativeDocumentationCapabilityIds) {
                add("processModustroJavaNativeSources")
            }
            if ("dependency_resolution" in locNativeDocumentationCapabilityIds) {
                add("resolveJavaDependencies")
            }
        }
            .filter { locName -> locName in locSubproject.tasks.names }
            .map { locName -> locSubproject.tasks.named(locName) }
        locGenerateJavaDocsSite.configure {
            dependsOn(locDocumentationDependencies)
        }
    }

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
            val locArtifactPublicationDirectory = File(
                locArtifactDocsRootFile,
                "${locModulePath}/${locPublicationKind}/${locPublicationId}"
            )

            locJavaDocsSiteEntries.add(
                AIcdJavaDocsSiteEntry(
                    locModulePath = locModulePath,
                    locJavadocOutputDirectory = locJavadocOutputDirectory,
                    locArtifactPublicationDirectory = locArtifactPublicationDirectory,
                    locArtifactMetadata = AIcBuildJavaArtifactMetadata(locSubproject, locModulePath)
                )
            )
        }
    }
}

@Suppress("UNCHECKED_CAST")
(rootProject.extra.properties["modustroDocsTechnologyTaskNames"] as? MutableSet<String>)
    ?.add("generateJavaDocsSite")

tasks.named("generateModustroDocsSite") {
    dependsOn(locGenerateJavaDocsSite)
}
