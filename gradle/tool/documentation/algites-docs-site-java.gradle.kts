/*
 * Algites shared Java documentation site script.
 *
 * Intended location in governance repository:
 *   gradle/tool/documentation/algites-docs-site-java.gradle.kts
 */

import org.gradle.api.tasks.javadoc.Javadoc

val algitesDocsBaseScript = (findProperty("algites.docs.baseScript") as String?)
    ?: "https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/algites-docs-site-base.gradle.kts"

apply(from = uri(algitesDocsBaseScript))

val algitesJavaPublicationRoot = file(extra["algitesDocsPublicationRootPath"] as String)

fun Project.AIcResolveJavaModulePath(): String {
    return path.removePrefix(":").replace(":", ".").ifBlank { name }
}

tasks.register("generateJavaDocsSite") {
    group = "algites"
    description = "Generates and stages Java Javadoc into the Algites documentation site."

    val locJavadocTaskPathsProvider = provider {
        allprojects.mapNotNull { locProject -> locProject.tasks.findByName("javadoc")?.path }
    }

    dependsOn(locJavadocTaskPathsProvider)
    outputs.dir(algitesJavaPublicationRoot)

    doLast {
        val locDocumentedProjects = allprojects.mapNotNull { locProject ->
            val locJavadocTask = locProject.tasks.findByName("javadoc") as? Javadoc
                ?: return@mapNotNull null

            val locJavadocOutputDirectory = locJavadocTask.destinationDir
                ?: return@mapNotNull null

            if (!locJavadocOutputDirectory.isDirectory) {
                return@mapNotNull null
            }

            val locModulePath = locProject.AIcResolveJavaModulePath()
            val locTargetDirectory = File(algitesJavaPublicationRoot, locModulePath)

            locTargetDirectory.deleteRecursively()
            locTargetDirectory.mkdirs()

            copy {
                from(locJavadocOutputDirectory)
                into(locTargetDirectory)
            }

            locModulePath
        }.sorted()

        logger.lifecycle("Java documentation staged at: ${algitesJavaPublicationRoot.absolutePath}")
        logger.lifecycle("Documented Java artifact(s): ${locDocumentedProjects.size}")
    }
}

tasks.named("generateAlgitesGeneratedDocsIndex") {
    mustRunAfter("generateJavaDocsSite")
}

tasks.named("generateAlgitesDocsSite") {
    dependsOn("generateJavaDocsSite")
}
