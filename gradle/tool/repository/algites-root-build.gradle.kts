/*
 * Algites generic repository build conventions.
 *
 * Intended location in governance repository:
 *   gradle/tool/repository/algites-root-build.gradle.kts
 *
 * Public governance should keep the shared logic here. Private governance can
 * apply the same script after defining different default deployment URLs in
 * extra properties, without copying the implementation.
 */

import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.testing.Test

apply(plugin = "base")

val locAlgitesResolverWrapperScript = rootProject.file("gradle/tool/repository/algites-artifact-directory-metadata-resolver-wrapper.gradle.kts")
if (locAlgitesResolverWrapperScript.isFile) {
    apply(from = locAlgitesResolverWrapperScript)
} else {
    apply(from = uri("https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/repository/algites-artifact-directory-metadata-resolver-wrapper.gradle.kts"))
}

apply(from = uri("https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/algites-docs-site.gradle.kts"))

fun String.capitalizedForAlgitesName(): String =
    replaceFirstChar { locCharacter ->
        if (locCharacter.isLowerCase()) {
            locCharacter.titlecase()
        } else {
            locCharacter.toString()
        }
    }

fun algitesGradleOrEnvironmentValue(aName: String): String? =
    providers.gradleProperty(aName).orNull
        ?: providers.environmentVariable(aName).orNull

@Suppress("UNCHECKED_CAST")
val algitesResolvedRepositoryMetadata = rootProject.extra["algitesResolvedRepositoryMetadata"] as Map<String, Any?>

@Suppress("UNCHECKED_CAST")
val algitesResolvedArtifactDirectoriesByGradleProjectPath =
    rootProject.extra["algitesResolvedArtifactDirectoriesByGradleProjectPath"] as Map<String, Map<String, Any?>>

fun algitesResolvedArtifactDirectoryForProject(aProjectPath: String): Map<String, Any?>? {
    return algitesResolvedArtifactDirectoriesByGradleProjectPath[aProjectPath]
}

@Suppress("UNCHECKED_CAST")
fun algitesResolvedVersionValue(aArtifactDirectory: Map<String, Any?>?): String? {
    val locVersion = aArtifactDirectory?.get("version") as? Map<String, Any?>
    return locVersion?.get("resolvedValue")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
}

fun requireAlgitesGroupForPublish(aProjectPath: String, aProjectGroup: Any?) {
    val locGroupText = aProjectGroup?.toString()?.trim()

    if (locGroupText.isNullOrBlank() || locGroupText == "unspecified") {
        throw GradleException(
            "Project '$aProjectPath' is being published, but no Maven group could be resolved. " +
                "Define groupId in algites-artifact.yml, algites-artifact-set.yml, or algites-source-repository.yml."
        )
    }
}

val algitesRepositoryVisibility = (
    algitesGradleOrEnvironmentValue("ALGITES_VISIBILITY")
        ?: algitesResolvedRepositoryMetadata["visibility"]?.toString()
        ?: "pub"
).lowercase()
val algitesDeploymentDirection = (algitesGradleOrEnvironmentValue("ALGITES_DIRECTION") ?: "upload").lowercase()

val algitesReleaseRepositoryUrl = algitesGradleOrEnvironmentValue("ALGITES_MAVEN_RELEASES_URL")
    ?: algitesGradleOrEnvironmentValue("ALGITES_REPO_URL")

val algitesSnapshotRepositoryUrl = algitesGradleOrEnvironmentValue("ALGITES_MAVEN_SNAPSHOTS_URL")
    ?: algitesGradleOrEnvironmentValue("ALGITES_REPO_URL")

val algitesRepositoryUser = algitesGradleOrEnvironmentValue("ALGITES_REPO_USER")
    ?: providers.environmentVariable("GITHUB_ACTOR").orNull

val algitesRepositoryPassword = algitesGradleOrEnvironmentValue("ALGITES_REPO_PASS")
    ?: providers.environmentVariable("GITHUB_TOKEN").orNull
    ?: providers.environmentVariable("ALGITES_MAVEN_TOKEN").orNull

val algitesDocsPagesBranch = algitesGradleOrEnvironmentValue("ALGITES_DOCS_PAGES_BRANCH") ?: "gh-pages"

val algitesRemoteRepositoryName = buildString {
    append("algites")
    append(algitesRepositoryVisibility.capitalizedForAlgitesName())
    append(algitesDeploymentDirection.capitalizedForAlgitesName())
}

val algitesHasRemoteRepository = !algitesReleaseRepositoryUrl.isNullOrBlank() &&
    !algitesSnapshotRepositoryUrl.isNullOrBlank() &&
    !algitesRepositoryUser.isNullOrBlank() &&
    !algitesRepositoryPassword.isNullOrBlank()

val algitesIsCi = providers.environmentVariable("CI")
    .map { locValue -> locValue.equals("true", ignoreCase = true) }
    .orElse(false)
    .get()

val algitesRequestedTasks = gradle.startParameter.taskNames
val algitesIsPublishRequested = algitesRequestedTasks.any { locTaskName ->
    locTaskName == "publish" ||
        locTaskName.startsWith("publish") ||
        locTaskName.contains("publish", ignoreCase = true)
}

if (algitesIsCi && algitesIsPublishRequested && !algitesHasRemoteRepository) {
    throw GradleException("CI publish build requires ALGITES_REPO_* credentials and repository URLs.")
}

allprojects {
    layout.buildDirectory.set(
        rootProject.layout.projectDirectory.dir("run/bld/gradle/${project.path.removePrefix(":").replace(':', '/')}")
    )

    val algitesArtifactDirectory = algitesResolvedArtifactDirectoryForProject(project.path)
    val algitesResolvedProjectGroup = algitesArtifactDirectory?.get("groupId")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        ?: algitesResolvedRepositoryMetadata["groupId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }

    if (!algitesResolvedProjectGroup.isNullOrBlank()) {
        group = algitesResolvedProjectGroup
    }

    version = algitesResolvedVersionValue(algitesArtifactDirectory)
        ?: algitesResolvedVersionValue(algitesResolvedArtifactDirectoryForProject(":"))
        ?: "0.0.1-SNAPSHOT"

    tasks.withType<Test>().configureEach {
        useTestNG()
    }
}

subprojects {
    val algitesSubprojectPathDots = project.path
        .removePrefix(":")
        .replace(':', '.')

    val algitesCanonicalArtifactId = if (algitesSubprojectPathDots.isBlank()) {
        rootProject.name
    } else {
        "${rootProject.name}_${algitesSubprojectPathDots}"
    }

    plugins.withId("base") {
        extensions.configure<BasePluginExtension>("base") {
            archivesName.set(algitesCanonicalArtifactId)
        }
    }

    plugins.withId("maven-publish") {
        if (algitesIsPublishRequested) {
            requireAlgitesGroupForPublish(project.path, project.group)
        }

        plugins.withId("java") {
            extensions.configure<PublishingExtension>("publishing") {
                publications {
                    if (findByName("mavenJava") == null && components.findByName("java") != null) {
                        create<MavenPublication>("mavenJava") {
                            from(components["java"])
                        }
                    }
                }
            }
        }

        extensions.configure<PublishingExtension>("publishing") {
            publications.withType(MavenPublication::class.java).configureEach {
                artifactId = algitesCanonicalArtifactId
            }

            repositories {
                val locRepositoryUrl = if (project.version.toString().endsWith("SNAPSHOT")) {
                    algitesSnapshotRepositoryUrl
                } else {
                    algitesReleaseRepositoryUrl
                }

                if (!locRepositoryUrl.isNullOrBlank() && !algitesRepositoryUser.isNullOrBlank() && !algitesRepositoryPassword.isNullOrBlank()) {
                    maven {
                        name = algitesRemoteRepositoryName
                        url = uri(locRepositoryUrl)
                        credentials {
                            username = algitesRepositoryUser
                            password = algitesRepositoryPassword
                        }
                    }
                }
            }
        }
    }

    tasks.matching { locTask -> locTask.name == "publish" }.configureEach {
        if (!algitesIsCi && !algitesHasRemoteRepository) {
            dependsOn("publishToMavenLocal")
        }
    }
}

tasks.withType<PublishToMavenRepository>().configureEach {
    enabled = false
}

tasks.matching { locTask -> locTask.name == "publish" || locTask.name == "publishToMavenLocal" }.configureEach {
    enabled = false
}

tasks.register("printAlgitesDeploymentPlan") {
    group = "algites"
    description = "Prints the effective Algites deployment configuration."

    doLast {
        val locReleaseRepositoryDisplay = algitesReleaseRepositoryUrl ?: "not configured"
        val locSnapshotRepositoryDisplay = algitesSnapshotRepositoryUrl ?: "not configured"

        println("Algites deployment plan for ${rootProject.name}:")
        println(" - repository visibility: $algitesRepositoryVisibility")
        println(" - deployment direction: $algitesDeploymentDirection")
        println(" - Maven repository name: $algitesRemoteRepositoryName")
        println(" - Maven releases URL: $locReleaseRepositoryDisplay")
        println(" - Maven snapshots URL: $locSnapshotRepositoryDisplay")
        println(" - Maven remote repository configured: $algitesHasRemoteRepository")
        println(" - docs pages branch: $algitesDocsPagesBranch")
    }
}

tasks.register("ciHelp") {
    group = "algites"
    description = "Prints a small marker proving that the Algites root Gradle build was detected."

    doLast {
        println("Algites root Gradle build detected: ${rootProject.name}")
    }
}
