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

val locAlgitesDocsBaseScript = (findProperty("algites.docs.baseScript") as String?)
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/algites-docs-site-base.gradle.kts"

apply(from = uri(locAlgitesDocsBaseScript))

val locGeneratedDocsRoot = layout.projectDirectory.dir(
    (extra.properties["algitesPublicationDocsRootPath"] as String?)
        ?: (findProperty("algites.docs.generatedRoot") as String?)
        ?: "docs-site/generated"
)


fun AIcReadRepositoryId(): String {
    return (extra.properties["algitesDocsResolvedRepositoryName"] as String?) ?: rootProject.name
}

fun Project.AIcResolveJavaModulePath(): String {
    return path.removePrefix(":").replace(":", ".")
}

tasks.register("generateJavaDocsSite") {
    group = "algites"
    description = "Generates and stages Java Javadoc into the Algites documentation site."

    dependsOn("generateAlgitesDocsRootIndex")

    dependsOn(
        subprojects.mapNotNull { locSubproject ->
            locSubproject.tasks.findByName("javadoc")?.path
        }
    )

    outputs.dir(locGeneratedDocsRoot)

    doLast {
        val locRepositoryId = AIcReadRepositoryId()

        val locDocumentedProjects = subprojects.mapNotNull { locSubproject ->
            val locJavadocTask = locSubproject.tasks.findByName("javadoc") as? Javadoc
                ?: return@mapNotNull null

            val locJavadocOutputDirectory = locJavadocTask.destinationDir
                ?: return@mapNotNull null

            if (!locJavadocOutputDirectory.isDirectory) {
                return@mapNotNull null
            }

            val locModulePath = locSubproject.AIcResolveJavaModulePath()
            val locTargetDirectory = locGeneratedDocsRoot.dir("${locModulePath}/latest").asFile

            locTargetDirectory.deleteRecursively()
            locTargetDirectory.mkdirs()

            copy {
                from(locJavadocOutputDirectory)
                into(locTargetDirectory)
            }

            locModulePath
        }.sorted()

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

        logger.lifecycle("Java documentation generated at: ${locGeneratedDocsRoot.asFile.absolutePath}")
        logger.lifecycle("Documented Java artifact(s): ${locDocumentedProjects.size}")
    }
}

tasks.named("generateAlgitesDocsSite") {
    dependsOn("generateJavaDocsSite")
}
