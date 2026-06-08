/*
 * Algites shared Java documentation site script.
 *
 * Intended location in governance repository:
 *   gradle/tool/documentation/algites-docs-site-java.gradle.kts
 *
 * A repository can apply only this script; it automatically applies the base
 * documentation-site script.
 */

import org.gradle.api.tasks.javadoc.Javadoc

data class AIcJavaDocsSiteEntry(
    val locModulePath: String,
    val locJavadocOutputDirectory: File
)

val locAlgitesDocsBaseScript = (findProperty("algites.docs.baseScript") as String?)
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/algites-docs-site-base.gradle.kts"

apply(from = uri(locAlgitesDocsBaseScript))

val locGeneratedDocsRoot = layout.projectDirectory.dir(
    (extra.properties["algitesPublicationDocsRootPath"] as String?)
        ?: (findProperty("algites.docs.generatedRoot") as String?)
        ?: "docs-site/generated"
)
val locGeneratedDocsRootFile = locGeneratedDocsRoot.asFile
val locJavaDocsRepositoryId = (extra.properties["algitesDocsResolvedRepositoryName"] as String?) ?: rootProject.name

fun AIcReadRepositoryId(): String {
    return locJavaDocsRepositoryId
}

fun Project.AIcResolveJavaModulePath(): String {
    return path.removePrefix(":").replace(":", ".")
}

val locJavaDocsSiteEntries = mutableListOf<AIcJavaDocsSiteEntry>()

val locGenerateJavaDocsSite = tasks.register("generateJavaDocsSite") {
    group = "algites"
    description = "Generates and stages Java Javadoc into the Algites documentation site."

    dependsOn("generateAlgitesDocsRootIndex")

    outputs.dir(locGeneratedDocsRootFile)

    doLast {
        val locRepositoryId = AIcReadRepositoryId()

        val locDocumentedProjects = locJavaDocsSiteEntries.mapNotNull { locEntry ->
            val locJavadocOutputDirectory = locEntry.locJavadocOutputDirectory

            if (!locJavadocOutputDirectory.isDirectory) {
                return@mapNotNull null
            }

            val locTargetDirectory = File(locGeneratedDocsRootFile, "${locEntry.locModulePath}/latest")

            locTargetDirectory.deleteRecursively()
            locJavadocOutputDirectory.copyRecursively(locTargetDirectory, overwrite = true)

            locEntry.locModulePath
        }.sorted()

        val locIndexFile = File(locGeneratedDocsRootFile, "index.html")
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
                locDocumentedProjects.joinToString("\n") { locModulePath ->
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

        logger.lifecycle("Java documentation generated at: ${locGeneratedDocsRootFile.absolutePath}")
        logger.lifecycle("Documented Java artifact(s): ${locDocumentedProjects.size}")
    }
}

subprojects.forEach { locSubproject ->
    locSubproject.plugins.withId("java") {
        val locJavadocTaskProvider = locSubproject.tasks.named("javadoc", Javadoc::class.java)

        locGenerateJavaDocsSite.configure {
            dependsOn(locJavadocTaskProvider)
        }

        locJavadocTaskProvider.configure { locJavadocTask ->
            val locJavadocOutputDirectory = locJavadocTask.destinationDir ?: return@configure
            locJavaDocsSiteEntries.add(
                AIcJavaDocsSiteEntry(
                    locModulePath = locSubproject.AIcResolveJavaModulePath(),
                    locJavadocOutputDirectory = locJavadocOutputDirectory
                )
            )
        }
    }
}

tasks.named("generateAlgitesDocsSite") {
    dependsOn(locGenerateJavaDocsSite)
}

