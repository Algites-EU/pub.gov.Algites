/*
 * Algites shared Java documentation site script.
 *
 * Intended location in governance repository:
 *   gradle/tool/documentation/algites-docs-site-java.gradle.kts
 *
 * A repository can apply only this script; it automatically applies the base
 * documentation-site script.
 */

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import java.io.File

abstract class AIcGenerateJavaDocsSiteIndexTask : DefaultTask() {

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val repositoryConfigFile: RegularFileProperty

    @get:Input
    abstract val fallbackRepositoryId: Property<String>

    @get:Input
    abstract val documentedJavaModulePaths: ListProperty<String>

    @get:OutputDirectory
    abstract val generatedDocsRootDirectory: DirectoryProperty

    @TaskAction
    fun AIcGenerateIndex() {
        val locRepositoryId = AIcReadRepositoryId(
            repositoryConfigFile.asFile.orNull,
            fallbackRepositoryId.get()
        )
        val locSortedDocumentedJavaModulePaths = documentedJavaModulePaths.get().sorted()
        val locGeneratedDocsRootDirectory = generatedDocsRootDirectory.get().asFile
        val locIndexFile = File(locGeneratedDocsRootDirectory, "index.html")

        locIndexFile.parentFile.mkdirs()
        locIndexFile.writeText(
            """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <title>Generated ${locRepositoryId} Java Documentation</title>
            </head>
            <body>
              <main>
                <h1>Generated ${locRepositoryId} Java Documentation</h1>
                <ul>
            ${
                locSortedDocumentedJavaModulePaths.joinToString("\n") { locModulePath ->
                    "      <li><a href=\"${locModulePath}/latest/index.html\">${locModulePath}</a></li>"
                }
            }
                </ul>
              </main>
            </body>
            </html>
            """.trimIndent(),
            Charsets.UTF_8
        )

        logger.lifecycle("Java documentation generated at: ${locGeneratedDocsRootDirectory.absolutePath}")
        logger.lifecycle("Documented Java artifact(s): ${locSortedDocumentedJavaModulePaths.size}")
    }

    private fun AIcReadRepositoryId(aRepositoryConfigFile: File?, aFallbackRepositoryId: String): String {
        if (aRepositoryConfigFile == null || !aRepositoryConfigFile.isFile) {
            return aFallbackRepositoryId
        }

        val locMatchResult = Regex("""(?m)^\s*id\s*:\s*([^\s#]+)\s*$""").find(
            aRepositoryConfigFile.readText(Charsets.UTF_8)
        )

        return locMatchResult?.groupValues?.get(1)?.AIcNormalizeYamlScalar()
            ?: aFallbackRepositoryId
    }

    private fun String.AIcNormalizeYamlScalar(): String {
        return trim().removeSurrounding("\"").removeSurrounding("'")
    }
}

val locAlgitesDocsBaseScript = (findProperty("algites.docs.baseScript") as String?)
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/algites-docs-site-base.gradle.kts"

apply(from = uri(locAlgitesDocsBaseScript))

val locGeneratedDocsRoot = layout.projectDirectory.dir(
    (extra.properties["algitesPublicationDocsRootPath"] as String?)
        ?: (findProperty("algites.docs.generatedRoot") as String?)
        ?: "docs-site/generated"
)

val locRepositoryConfigFile = layout.projectDirectory.file("algites-source-repository.yml")
val locFallbackRepositoryId = rootProject.name
val locDocumentedJavaModulePaths = mutableListOf<String>()

fun Project.AIcResolveJavaModulePath(): String {
    return path.removePrefix(":").replace(":", ".")
}

fun Project.AIcResolveDocsTaskNameSuffix(): String {
    return path
        .removePrefix(":")
        .split(":")
        .filter { it.isNotBlank() }
        .joinToString("") { locPart ->
            locPart
                .replace(Regex("[^A-Za-z0-9]"), "_")
                .replaceFirstChar { locCharacter ->
                    if (locCharacter.isLowerCase()) {
                        locCharacter.titlecase()
                    } else {
                        locCharacter.toString()
                    }
                }
        }
}

val locGenerateJavaDocsSiteTaskProvider = tasks.register<AIcGenerateJavaDocsSiteIndexTask>("generateJavaDocsSite") {
    group = "algites"
    description = "Generates and stages Java Javadoc into the Algites documentation site."

    dependsOn("generateAlgitesDocsRootIndex")

    repositoryConfigFile.set(locRepositoryConfigFile)
    fallbackRepositoryId.set(locFallbackRepositoryId)
    generatedDocsRootDirectory.set(locGeneratedDocsRoot)
    documentedJavaModulePaths.set(provider { locDocumentedJavaModulePaths.sorted() })
}

subprojects.forEach { locSubproject ->
    locSubproject.plugins.withId("java") {
        val locModulePath = locSubproject.AIcResolveJavaModulePath()
        val locStageTaskName = "stageJavaDocsSiteFor${locSubproject.AIcResolveDocsTaskNameSuffix()}"
        val locJavadocDestinationDirectory = locSubproject.layout.buildDirectory.dir("docs/javadoc")

        if (!locDocumentedJavaModulePaths.contains(locModulePath)) {
            locDocumentedJavaModulePaths.add(locModulePath)
        }

        val locJavadocTaskProvider = locSubproject.tasks.named<Javadoc>("javadoc") {
            destinationDir = locJavadocDestinationDirectory.get().asFile

            val locOptions = options as StandardJavadocDocletOptions
            locOptions.encoding = "UTF-8"
            locOptions.charSet = "UTF-8"
            locOptions.addBooleanOption("Xdoclint:none", true)
            locOptions.addBooleanOption("quiet", true)
            locOptions.addStringOption("tag", "date:a:Date:")
        }

        val locStageJavaDocsTaskProvider = tasks.register<Sync>(locStageTaskName) {
            group = "algites"
            description = "Stages Java Javadoc for ${locSubproject.path} into the Algites documentation site."

            dependsOn(locJavadocTaskProvider)
            from(locJavadocDestinationDirectory)
            into(locGeneratedDocsRoot.dir("${locModulePath}/latest"))
        }

        locGenerateJavaDocsSiteTaskProvider.configure {
            dependsOn(locStageJavaDocsTaskProvider)
        }
    }
}

tasks.named("generateAlgitesDocsSite") {
    dependsOn(locGenerateJavaDocsSiteTaskProvider)
}
