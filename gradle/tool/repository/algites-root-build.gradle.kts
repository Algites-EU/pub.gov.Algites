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
apply(from = uri("https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/repository/algites-artifact-model.gradle.kts"))
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

fun readAlgitesSourceRepositoryYamlText(): String? {
    val locRepositoryFile = rootProject.layout.projectDirectory.file("algites-source-repository.yml").asFile

    return if (locRepositoryFile.isFile) {
        locRepositoryFile.readText()
    } else {
        null
    }
}

fun readAlgitesYamlScalar(aYamlText: String, aParentKey: String, aKey: String): String? {
    var locInsideParent = false
    val locParentPrefix = "$aParentKey:"
    val locKeyPrefix = "$aKey:"

    aYamlText.lineSequence().forEach { locRawLine ->
        val locLine = locRawLine.trim()

        if (locLine.isBlank() || locLine.startsWith("#")) {
            return@forEach
        }

        if (!locRawLine.startsWith(" ") && !locRawLine.startsWith("\t")) {
            locInsideParent = locLine == locParentPrefix
            return@forEach
        }

        if (locInsideParent && locLine.startsWith(locKeyPrefix)) {
            return locLine
                .removePrefix(locKeyPrefix)
                .trim()
                .removeSurrounding("\"")
                .removeSurrounding("'")
        }
    }

    return null
}

fun readAlgitesYamlScalarFromFile(aFile: java.io.File, aParentKey: String, aKey: String): String? =
    if (aFile.isFile) {
        readAlgitesYamlScalar(aFile.readText(), aParentKey, aKey)
    } else {
        null
    }

fun algitesDirectorySequenceFromProjectToRoot(aStartDirectory: java.io.File): Sequence<java.io.File> =
    generateSequence(aStartDirectory.canonicalFile) { locDirectory ->
        if (locDirectory == rootProject.projectDir.canonicalFile) {
            null
        } else {
            locDirectory.parentFile
        }
    }

fun findNearestAlgitesMetadataFile(aStartDirectory: java.io.File, aFileName: String): java.io.File? =
    algitesDirectorySequenceFromProjectToRoot(aStartDirectory)
        .map { locDirectory -> java.io.File(locDirectory, aFileName) }
        .firstOrNull { locFile -> locFile.isFile }

fun resolveAlgitesGroupForProject(aProjectDirectory: java.io.File): String? {
    val locArtifactGroup = findNearestAlgitesMetadataFile(aProjectDirectory, "algites-artifact.yml")
        ?.let { locArtifactFile -> readAlgitesYamlScalarFromFile(locArtifactFile, "artifact", "groupId") }

    if (!locArtifactGroup.isNullOrBlank()) {
        return locArtifactGroup
    }

    val locArtifactSetGroup = findNearestAlgitesMetadataFile(aProjectDirectory, "algites-artifact-set.yml")
        ?.let { locArtifactSetFile -> readAlgitesYamlScalarFromFile(locArtifactSetFile, "artifactSet", "groupId") }

    if (!locArtifactSetGroup.isNullOrBlank()) {
        return locArtifactSetGroup
    }

    val locSourceRepositoryGroup = readAlgitesSourceRepositoryYamlText()
        ?.let { locYamlText -> readAlgitesYamlScalar(locYamlText, "sourceRepository", "groupId") }

    if (!locSourceRepositoryGroup.isNullOrBlank()) {
        return locSourceRepositoryGroup
    }

    return null
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

fun resolveAlgitesVersionFromSourceRepository(): String {
    val locYamlText = readAlgitesSourceRepositoryYamlText()

    if (locYamlText == null) {
        return "0.0.1-SNAPSHOT"
    }

    val locReleaseLine = readAlgitesYamlScalar(locYamlText, "versionContext", "releaseLine")
        ?: return "0.0.1-SNAPSHOT"

    val locRevision = readAlgitesYamlScalar(locYamlText, "versionContext", "revision")
        ?: "0"

    val locQualifierKind = readAlgitesYamlScalar(locYamlText, "versionContext", "qualifierKind")
    val locQualifierLabel = readAlgitesYamlScalar(locYamlText, "versionContext", "qualifierLabel")

    val locBaseVersion = "$locReleaseLine.$locRevision"
    val locEffectiveQualifier = locQualifierLabel
        ?: locQualifierKind

    return if (locEffectiveQualifier.isNullOrBlank() || locEffectiveQualifier.equals("RELEASE", ignoreCase = true)) {
        locBaseVersion
    } else {
        "$locBaseVersion-${locEffectiveQualifier.uppercase()}"
    }
}

val algitesRepositoryVisibility = (algitesGradleOrEnvironmentValue("ALGITES_VISIBILITY") ?: "pub").lowercase()
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

    val algitesResolvedProjectGroup = resolveAlgitesGroupForProject(project.projectDir)

    if (!algitesResolvedProjectGroup.isNullOrBlank()) {
        group = algitesResolvedProjectGroup
    }

    version = resolveAlgitesVersionFromSourceRepository()

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
