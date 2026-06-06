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

fun algitesExtraString(aName: String): String? =
    if (rootProject.extra.has(aName)) {
        rootProject.extra[aName]?.toString()
    } else {
        null
    }

fun algitesSetting(aPropertyName: String, aExtraDefaultName: String, aHardDefaultValue: String? = null): String? =
    providers.gradleProperty(aPropertyName).orNull
        ?: providers.environmentVariable(aPropertyName.replace('.', '_').uppercase()).orNull
        ?: algitesExtraString(aExtraDefaultName)
        ?: aHardDefaultValue

val algitesRepositoryVisibility = algitesSetting(
    aPropertyName = "algites.repository.visibility",
    aExtraDefaultName = "algites.repository.visibility.default",
    aHardDefaultValue = "public"
)!!.lowercase()

val algitesDeploymentVisibility = algitesSetting(
    aPropertyName = "algites.deployment.visibility",
    aExtraDefaultName = "algites.deployment.visibility.default",
    aHardDefaultValue = algitesRepositoryVisibility
)!!.lowercase()

val algitesDeploymentProfile = algitesSetting(
    aPropertyName = "algites.deployment.profile",
    aExtraDefaultName = "algites.deployment.profile.default",
    aHardDefaultValue = if (algitesDeploymentVisibility == "private") "private" else "public"
)!!.lowercase()

val algitesReleaseRepositoryUrl = algitesSetting(
    aPropertyName = "algites.deployment.maven.releases.url",
    aExtraDefaultName = "algites.deployment.maven.releases.url.default",
    aHardDefaultValue = if (algitesDeploymentProfile == "private") null else "https://maven.pkg.github.com/Algites-EU/pub.lib.Java"
)

val algitesSnapshotRepositoryUrl = algitesSetting(
    aPropertyName = "algites.deployment.maven.snapshots.url",
    aExtraDefaultName = "algites.deployment.maven.snapshots.url.default",
    aHardDefaultValue = if (algitesDeploymentProfile == "private") null else "https://maven.pkg.github.com/Algites-EU/pub.lib.Java"
)

val algitesRepositoryUser = algitesSetting(
    aPropertyName = "algites.deployment.maven.username",
    aExtraDefaultName = "algites.deployment.maven.username.default",
    aHardDefaultValue = providers.environmentVariable("GITHUB_ACTOR").orNull
)

val algitesRepositoryPassword = algitesSetting(
    aPropertyName = "algites.deployment.maven.password",
    aExtraDefaultName = "algites.deployment.maven.password.default",
    aHardDefaultValue = providers.environmentVariable("GITHUB_TOKEN").orNull
        ?: providers.environmentVariable("ALGITES_MAVEN_TOKEN").orNull
)

val algitesDocsPagesBranch = algitesSetting(
    aPropertyName = "algites.deployment.docs.pagesBranch",
    aExtraDefaultName = "algites.deployment.docs.pagesBranch.default",
    aHardDefaultValue = "gh-pages"
)

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

if (algitesIsCi && algitesIsPublishRequested && algitesRepositoryPassword.isNullOrBlank()) {
    throw GradleException("CI publish build requires deployment credentials. Provide GITHUB_TOKEN or ALGITES_MAVEN_TOKEN.")
}

allprojects {
    layout.buildDirectory.set(
        rootProject.layout.projectDirectory.dir("run/bld/gradle/${project.path.removePrefix(":").replace(':', '/')}")
    )

    group = providers.gradleProperty("algites.group")
        .orElse("eu.algites.lib")
        .get()

    version = providers.gradleProperty("algites.version")
        .orElse("0.0.1-SNAPSHOT")
        .get()
}

subprojects {
    plugins.withId("java") {
        tasks.withType<Test>().configureEach {
            useTestNG()
        }
    }

    plugins.withId("base") {
        val locPathDots = project.path.removePrefix(":").replace(':', '.')
        val locCanonicalArtifactId = if (locPathDots.isBlank()) {
            rootProject.name
        } else {
            "${rootProject.name}_${locPathDots}"
        }

        extensions.configure<BasePluginExtension>("base") {
            archivesName.set(locCanonicalArtifactId)
        }
    }

    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension>("publishing") {
            publications.withType(MavenPublication::class.java).configureEach {
                val locPathDots = project.path.removePrefix(":").replace(':', '.')
                artifactId = if (locPathDots.isBlank()) {
                    rootProject.name
                } else {
                    "${rootProject.name}_${locPathDots}"
                }
            }

            repositories {
                val locRepositoryUrl = if (project.version.toString().endsWith("SNAPSHOT")) {
                    algitesSnapshotRepositoryUrl
                } else {
                    algitesReleaseRepositoryUrl
                }

                if (!locRepositoryUrl.isNullOrBlank() && !algitesRepositoryUser.isNullOrBlank() && !algitesRepositoryPassword.isNullOrBlank()) {
                    maven {
                        name = "algites" + algitesDeploymentProfile.capitalizedForAlgitesName()
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
}

tasks.register("printAlgitesDeploymentPlan") {
    group = "algites"
    description = "Prints the effective Algites deployment configuration."

    doLast {
        println("Algites deployment plan for ${rootProject.name}:")
        println(" - repository visibility: $algitesRepositoryVisibility")
        println(" - deployment visibility: $algitesDeploymentVisibility")
        println(" - deployment profile: $algitesDeploymentProfile")
        println(" - Maven releases URL: ${algitesReleaseRepositoryUrl ?: \"not configured\"}")
        println(" - Maven snapshots URL: ${algitesSnapshotRepositoryUrl ?: \"not configured\"}")
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
