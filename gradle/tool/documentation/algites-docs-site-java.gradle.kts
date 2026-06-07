/*
 * Algites shared Java documentation site script.
 *
 * Intended location in governance repository:
 *   gradle/tool/documentation/algites-docs-site-java.gradle.kts
 *
 * A repository can apply only this script; it automatically applies the base
 * documentation-site script.
 */

import org.gradle.api.Project
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.javadoc.Javadoc
import java.io.File

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

fun String.AIcNormalizeYamlScalar(): String {
    return trim().removeSurrounding("\"").removeSurrounding("'")
}

fun AIcReadRepositoryId(aRepositoryConfigFile: File, aFallbackRepositoryId: String): String {
    if (!aRepositoryConfigFile.isFile) {
        return aFallbackRepositoryId
    }

    val locMatchResult = Regex("""(?m)^\s*id\s*:\s*([^\s#]+)\s*$""").find(
        aRepositoryConfigFile.readText(Charsets.UTF_8)
    )

    return locMatchResult?.groupValues?.get(1)?.AIcNormalizeYamlScalar()
        ?: aFallbackRepositoryId
}

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

val locGenerateJavaDocsSiteTaskProvider = tasks.register("generateJavaDocsSite") {
    group = "algites"
    description = "Generates and stages Java Javadoc into the Algites documentation site."

    dependsOn("generateAlgitesDocsRootIndex")

    inputs.file(locRepositoryConfigFile)
        .optional()
        .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)

    inputs.property("algitesDocumentedJavaModulePaths", provider {
        locDocumentedJavaModulePaths.sorted().joinToString("\n")
    })

    outputs.dir(locGeneratedDocsRoot)

    doLast {
        val locRepositoryId = AIcReadRepositoryId(locRepositoryConfigFile.asFile, locFallbackRepositoryId)
        val locSortedDocumentedJavaModulePaths = locDocumentedJavaModulePaths.sorted()

        val locIndexFile = locGeneratedDocsRoot.file("index.html").asFile
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

        logger.lifecycle("Java documentation generated at: ${locGeneratedDocsRoot.asFile.absolutePath}")
        logger.lifecycle("Documented Java artifact(s): ${locSortedDocumentedJavaModulePaths.size}")
    }
}

subprojects.forEach { locSubproject ->
    locSubproject.plugins.withId("java") {
        val locJavadocTaskProvider = locSubproject.tasks.named<Javadoc>("javadoc")
        val locModulePath = locSubproject.AIcResolveJavaModulePath()
        val locStageTaskName = "stageJavaDocsSiteFor${locSubproject.AIcResolveDocsTaskNameSuffix()}"

        if (!locDocumentedJavaModulePaths.contains(locModulePath)) {
            locDocumentedJavaModulePaths.add(locModulePath)
        }

        val locStageJavaDocsTaskProvider = tasks.register<Sync>(locStageTaskName) {
            group = "algites"
            description = "Stages Java Javadoc for ${locSubproject.path} into the Algites documentation site."

            dependsOn(locJavadocTaskProvider)

            from(locJavadocTaskProvider.map { locJavadocTask ->
                locJavadocTask.destinationDir
                    ?: locSubproject.layout.buildDirectory.dir("docs/javadoc").get().asFile
            })
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
