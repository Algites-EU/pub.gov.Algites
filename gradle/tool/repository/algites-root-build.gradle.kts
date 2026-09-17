/*
 * Algites generic repository build conventions.
 *
 * Public entry-point location is stable. The implementation consumes the
 * effective metadata resolved from algites-source-repository.yml and nested
 * algites-artifact.yml files.
 */

import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.testing.Test

apply(plugin = "base")

val locAlgitesResolverWrapperScript = rootProject.file("gradle/tool/repository/algites-artifact-directory-metadata-resolver-wrapper.gradle.kts")
if (locAlgitesResolverWrapperScript.isFile) {
    apply(from = locAlgitesResolverWrapperScript)
} else {
    apply(from = uri("https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/repository/algites-artifact-directory-metadata-resolver-wrapper.gradle.kts"))
}

val locAlgitesDocsSiteScript = rootProject.file("gradle/tool/documentation/algites-docs-site.gradle.kts")
if (locAlgitesDocsSiteScript.isFile) {
    apply(from = locAlgitesDocsSiteScript)
} else {
    apply(from = uri("https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/documentation/algites-docs-site.gradle.kts"))
}

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

fun AIcAlgitesStringList(aValue: Any?): List<String> {
    return when (aValue) {
        is List<*> -> aValue.mapNotNull { it?.toString()?.trim()?.lowercase()?.takeIf(String::isNotBlank) }
        null -> emptyList()
        else -> aValue.toString().split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }
    }
}

@Suppress("UNCHECKED_CAST")
fun AIcAlgitesRepositoryMap(aValue: Any?): Map<String, String> {
    return (aValue as? Map<*, *>)
        ?.entries
        ?.associate { locEntry -> locEntry.key.toString() to locEntry.value.toString() }
        ?: emptyMap()
}

fun AIcAlgitesPythonDistributionName(aArtifactCoordinateId: String): String {
    val locNormalized = aArtifactCoordinateId
        .lowercase()
        .replace(Regex("[._-]+"), "-")
        .trim('-')
    return "algites-$locNormalized"
}

fun AIcAlgitesPythonIdentifierSegment(aValue: String): String {
    val locNormalized = aValue.lowercase().replace(Regex("[^a-z0-9_]"), "_")
    val locNonEmpty = locNormalized.ifBlank { "artifact" }
    return if (locNonEmpty.first().isDigit()) "_$locNonEmpty" else locNonEmpty
}

fun AIcAlgitesPythonImportNamespace(aRepositoryId: String, aModulePath: String): String {
    val locRepositorySegments = aRepositoryId.split('.').filter { it.isNotBlank() }
    val locModuleSegments = aModulePath.split('.').filter { it.isNotBlank() }
    return (listOf("algites") + locRepositorySegments + locModuleSegments)
        .map(::AIcAlgitesPythonIdentifierSegment)
        .joinToString(".")
}

fun AIcAlgitesPythonVersion(aVersion: String): String {
    val locSnapshotSuffix = "-SNAPSHOT"
    if (aVersion.endsWith(locSnapshotSuffix, ignoreCase = true)) {
        return aVersion.dropLast(locSnapshotSuffix.length) + ".dev0"
    }
    return aVersion.replace('-', '.')
}

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
                "Define groupId in algites-artifact.yml or algites-source-repository.yml."
        )
    }
}

val algitesRepositoryVisibility = (
    algitesGradleOrEnvironmentValue("ALGITES_VISIBILITY")
        ?: algitesResolvedRepositoryMetadata["visibility"]?.toString()
        ?: "pub"
).lowercase()

val algitesRepositoryUser = algitesGradleOrEnvironmentValue("ALGITES_REPO_USER")
    ?: providers.environmentVariable("GITHUB_ACTOR").orNull

val algitesRepositoryPassword = algitesGradleOrEnvironmentValue("ALGITES_REPO_PASS")
    ?: providers.environmentVariable("GITHUB_TOKEN").orNull
    ?: providers.environmentVariable("ALGITES_MAVEN_TOKEN").orNull

val algitesDocsPagesBranch = algitesGradleOrEnvironmentValue("ALGITES_DOCS_PAGES_BRANCH") ?: "gh-pages"
val algitesIsCi = providers.environmentVariable("CI")
    .map { locValue -> locValue.equals("true", ignoreCase = true) }
    .orElse(false)
    .get()

val algitesRequestedTechnologyKinds = (
    algitesGradleOrEnvironmentValue("ALGITES_TECHNOLOGY_KINDS")
        ?: algitesGradleOrEnvironmentValue("algites.technologyKinds")
)
    ?.split(',')
    ?.map { it.trim().lowercase() }
    ?.filter { it.isNotBlank() }
    ?.toSet()
    ?: emptySet()

val algitesLegacyReleaseRepositoryUrl = algitesGradleOrEnvironmentValue("ALGITES_MAVEN_RELEASES_URL")
    ?: algitesGradleOrEnvironmentValue("ALGITES_REPO_URL")
val algitesLegacySnapshotRepositoryUrl = algitesGradleOrEnvironmentValue("ALGITES_MAVEN_SNAPSHOTS_URL")
    ?: algitesGradleOrEnvironmentValue("ALGITES_REPO_URL")

val algitesRequestedTasks = gradle.startParameter.taskNames
val algitesIsPublishRequested = algitesRequestedTasks.any { locTaskName ->
    locTaskName == "publish" ||
        locTaskName.startsWith("publish") ||
        locTaskName.contains("publish", ignoreCase = true)
}

allprojects {
    layout.buildDirectory.set(
        rootProject.layout.projectDirectory.dir("run/bld/gradle/${project.path.removePrefix(":").replace(':', '/')}")
    )

    val locAlgitesArtifactDirectory = algitesResolvedArtifactDirectoryForProject(project.path)
    val locAlgitesResolvedProjectGroup = locAlgitesArtifactDirectory?.get("groupId")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        ?: algitesResolvedRepositoryMetadata["groupId"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }

    if (!locAlgitesResolvedProjectGroup.isNullOrBlank()) {
        group = locAlgitesResolvedProjectGroup
    }

    version = algitesResolvedVersionValue(locAlgitesArtifactDirectory)
        ?: algitesResolvedVersionValue(algitesResolvedArtifactDirectoryForProject(":"))
        ?: "0.0.1-SNAPSHOT"

    tasks.withType<Test>().configureEach {
        useTestNG()
    }
}

val algitesPrepareDevelopment = tasks.register("prepareDevelopment") {
    group = "algites"
    description = "Generates effective development metadata required by supported technology kinds."
}

val algitesRefreshDevelopment = tasks.register("refreshDevelopment") {
    group = "algites"
    description = "Forces regeneration of effective development metadata required by supported technology kinds."
}

val algitesBuild = tasks.register("algitesBuild") {
    group = "algites"
    description = "Builds all effective or explicitly selected Algites TechnologyKinds."
}

val algitesPublish = tasks.register("algitesPublish") {
    group = "publishing"
    description = "Publishes all effective or explicitly selected Algites TechnologyKinds."
}

subprojects {
    val locAlgitesArtifactDirectory = algitesResolvedArtifactDirectoryForProject(project.path)
    val locAlgitesTechnologyKinds = AIcAlgitesStringList(locAlgitesArtifactDirectory?.get("technologyKinds"))
    val locEffectiveTechnologyKinds = if (algitesRequestedTechnologyKinds.isEmpty()) {
        locAlgitesTechnologyKinds.toSet()
    } else {
        locAlgitesTechnologyKinds.filter { it in algitesRequestedTechnologyKinds }.toSet()
    }

    val locAlgitesSubprojectPathDots = project.path
        .removePrefix(":")
        .replace(':', '.')

    val locAlgitesCanonicalArtifactId = if (locAlgitesSubprojectPathDots.isBlank()) {
        rootProject.name
    } else {
        "${rootProject.name}_${locAlgitesSubprojectPathDots}"
    }

    val locEffectiveRepositories = AIcAlgitesRepositoryMap(locAlgitesArtifactDirectory?.get("repositories"))

    plugins.withId("base") {
        extensions.configure<BasePluginExtension>("base") {
            archivesName.set(locAlgitesCanonicalArtifactId)
        }
        if ("java" in locEffectiveTechnologyKinds) {
            val locJavaBuildTask = tasks.named("build")
            algitesBuild.configure { dependsOn(locJavaBuildTask) }
        }
    }

    if ("java" in locAlgitesTechnologyKinds) {
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
                    artifactId = locAlgitesCanonicalArtifactId
                }

                repositories {
                    val locIsSnapshot = project.version.toString().endsWith("SNAPSHOT", ignoreCase = true)
                    val locStability = if (locIsSnapshot) "snapshot" else "release"
                    val locRepositoryUrl = locEffectiveRepositories["java.$locStability.upload"]
                        ?: if (locIsSnapshot) algitesLegacySnapshotRepositoryUrl else algitesLegacyReleaseRepositoryUrl

                    if (!locRepositoryUrl.isNullOrBlank()) {
                        maven {
                            name = "algitesJava${locStability.capitalizedForAlgitesName()}Upload"
                            url = uri(locRepositoryUrl)
                            if (!algitesRepositoryUser.isNullOrBlank() && !algitesRepositoryPassword.isNullOrBlank()) {
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

        if ("java" in locEffectiveTechnologyKinds) {
            plugins.withId("maven-publish") {
                val locJavaPublishTask = tasks.named("publish")
                algitesPublish.configure { dependsOn(locJavaPublishTask) }
            }
        }
    }

    if ("python" in locAlgitesTechnologyKinds) {
        val locPythonTemplateFile = layout.projectDirectory.file("pyproject.toml.tpl")
        val locPythonProjectFile = layout.projectDirectory.file("pyproject.toml")
        val locPythonDistributionName = AIcAlgitesPythonDistributionName(locAlgitesCanonicalArtifactId)
        val locPythonImportNamespace = AIcAlgitesPythonImportNamespace(rootProject.name, locAlgitesSubprojectPathDots)

        val locDeletePythonDevelopmentMetadata = tasks.register("deletePythonDevelopmentMetadata") {
            group = "algites"
            description = "Deletes generated Python development metadata for this artifact."
            doLast {
                locPythonProjectFile.asFile.delete()
            }
        }

        val locGeneratePythonProjectMetadata = tasks.register("generatePythonProjectMetadata") {
            group = "algites"
            description = "Generates the effective pyproject.toml for this Algites Python artifact."

            inputs.file(project.file("algites-artifact.yml")).optional()
            inputs.file(project.file("algites-artifact.yaml")).optional()
            inputs.file(locPythonTemplateFile).optional()
            inputs.property("artifactCoordinateId", locAlgitesCanonicalArtifactId)
            inputs.property("distributionName", locPythonDistributionName)
            inputs.property("importNamespace", locPythonImportNamespace)
            inputs.property("version", project.provider { project.version.toString() })
            outputs.file(locPythonProjectFile)

            doLast {
                val locTemplateText = if (locPythonTemplateFile.asFile.isFile) {
                    locPythonTemplateFile.asFile.readText(Charsets.UTF_8)
                } else {
                    ""
                }

                if (Regex("(?m)^\\s*\\[project]\\s*$").containsMatchIn(locTemplateText)) {
                    throw GradleException("pyproject.toml.tpl must not define [project]; Algites owns generated Python project identity and version metadata.")
                }
                if (Regex("(?m)^\\s*\\[build-system]\\s*$").containsMatchIn(locTemplateText)) {
                    throw GradleException("pyproject.toml.tpl must not define [build-system]; the Algites Python adapter owns the effective build backend.")
                }

                val locPythonVersion = AIcAlgitesPythonVersion(project.version.toString())
                val locDescription = locAlgitesArtifactDirectory?.get("description")?.toString()?.replace("\"", "\\\"") ?: ""
                val locGenerated = buildString {
                    appendLine("# Generated by Algites. Do not edit or commit this file.")
                    appendLine("[build-system]")
                    appendLine("requires = [\"setuptools>=77\", \"wheel\"]")
                    appendLine("build-backend = \"setuptools.build_meta\"")
                    appendLine()
                    appendLine("[project]")
                    appendLine("name = \"$locPythonDistributionName\"")
                    appendLine("version = \"$locPythonVersion\"")
                    if (locDescription.isNotBlank()) {
                        appendLine("description = \"$locDescription\"")
                    }
                    appendLine()
                    appendLine("[tool.setuptools.packages.find]")
                    appendLine("where = [\"src/product/python\", \"src/product/python.gen\"]")
                    appendLine("namespaces = true")
                    if (locTemplateText.isNotBlank()) {
                        appendLine()
                        appendLine(locTemplateText.trim())
                        appendLine()
                    }
                }

                locPythonProjectFile.asFile.writeText(locGenerated, Charsets.UTF_8)
            }
        }

        val locRefreshPythonDevelopment = tasks.register("refreshPythonDevelopment") {
            group = "algites"
            description = "Forces regeneration of Python development metadata for this artifact."
            dependsOn(locDeletePythonDevelopmentMetadata)
            dependsOn(locGeneratePythonProjectMetadata)
            locGeneratePythonProjectMetadata.configure {
                mustRunAfter(locDeletePythonDevelopmentMetadata)
            }
        }

        val locPythonDistDirectory = rootProject.layout.projectDirectory.dir(
            "run/bld/python/${project.path.removePrefix(":").replace(':', '/')}/dist"
        )

        val locBuildPython = tasks.register<Exec>("buildPython") {
            group = "build"
            description = "Builds Python wheel and source distribution for this Algites artifact."
            dependsOn(locGeneratePythonProjectMetadata)
            workingDir(project.projectDir)
            commandLine(
                algitesGradleOrEnvironmentValue("ALGITES_PYTHON_EXECUTABLE") ?: "python3",
                "-m",
                "build",
                "--outdir",
                locPythonDistDirectory.asFile.absolutePath
            )
            inputs.dir(project.layout.projectDirectory.dir("src/product/python")).optional()
            inputs.dir(project.layout.projectDirectory.dir("src/product/python.gen")).optional()
            inputs.file(locPythonProjectFile)
            outputs.dir(locPythonDistDirectory)
        }

        val locPublishPython = tasks.register<Exec>("publishPython") {
            group = "publishing"
            description = "Publishes Python distributions for this Algites artifact."
            dependsOn(locBuildPython)

            doFirst {
                val locIsSnapshot = project.version.toString().endsWith("SNAPSHOT", ignoreCase = true)
                val locStability = if (locIsSnapshot) "snapshot" else "release"
                val locRepositoryUrl = locEffectiveRepositories["python.$locStability.upload"]
                    ?: throw GradleException(
                        "No Python $locStability upload repository is configured for project '${project.path}'. " +
                            "Configure repositories.python.$locStability.upload in Algites metadata or its inherited defaults."
                    )
                val locDistributionFiles = locPythonDistDirectory.asFile.listFiles()
                    ?.filter { locFile -> locFile.isFile }
                    ?.sortedBy { locFile -> locFile.name }
                    ?: emptyList()

                if (locDistributionFiles.isEmpty()) {
                    throw GradleException("No Python distribution files were produced for project '${project.path}'.")
                }

                val locPythonRepositoryUser = algitesGradleOrEnvironmentValue("ALGITES_PYTHON_REPO_USER")
                    ?: algitesRepositoryUser
                val locPythonRepositoryPassword = algitesGradleOrEnvironmentValue("ALGITES_PYTHON_REPO_PASS")
                    ?: algitesRepositoryPassword

                val locCommand = mutableListOf(
                    algitesGradleOrEnvironmentValue("ALGITES_PYTHON_EXECUTABLE") ?: "python3",
                    "-m",
                    "twine",
                    "upload",
                    "--repository-url",
                    locRepositoryUrl
                )
                if (!locPythonRepositoryUser.isNullOrBlank()) {
                    locCommand.addAll(listOf("--username", locPythonRepositoryUser))
                }
                if (!locPythonRepositoryPassword.isNullOrBlank()) {
                    locCommand.addAll(listOf("--password", locPythonRepositoryPassword))
                }
                locCommand.addAll(locDistributionFiles.map { locFile -> locFile.absolutePath })
                commandLine(locCommand)
            }
        }

        algitesPrepareDevelopment.configure { dependsOn(locGeneratePythonProjectMetadata) }
        algitesRefreshDevelopment.configure { dependsOn(locRefreshPythonDevelopment) }
        if ("python" in locEffectiveTechnologyKinds) {
            algitesBuild.configure { dependsOn(locBuildPython) }
            algitesPublish.configure { dependsOn(locPublishPython) }
        }
    }
}

tasks.register("printAlgitesDeploymentPlan") {
    group = "algites"
    description = "Prints the effective Algites deployment configuration."

    doLast {
        println("Algites deployment plan for ${rootProject.name}:")
        println(" - repository visibility: $algitesRepositoryVisibility")
        println(" - requested technology kinds: ${if (algitesRequestedTechnologyKinds.isEmpty()) "all effective technology kinds" else algitesRequestedTechnologyKinds.joinToString(",")}")
        println(" - docs pages branch: $algitesDocsPagesBranch")
        println(" - legacy Maven repository URL override present: ${!algitesLegacyReleaseRepositoryUrl.isNullOrBlank() || !algitesLegacySnapshotRepositoryUrl.isNullOrBlank()}")

        algitesResolvedArtifactDirectoriesByGradleProjectPath.toSortedMap().forEach { locEntry ->
            val locMetadata = locEntry.value
            println(" - ${locEntry.key}: technologyKinds=${AIcAlgitesStringList(locMetadata["technologyKinds"])}")
            AIcAlgitesRepositoryMap(locMetadata["repositories"]).toSortedMap().forEach { locRepositoryEntry ->
                println("     ${locRepositoryEntry.key}=${locRepositoryEntry.value}")
            }
        }
    }
}

tasks.register("ciHelp") {
    group = "algites"
    description = "Prints a small marker proving that the Algites root Gradle build was detected."

    doLast {
        println("Algites root Gradle build detected: ${rootProject.name}")
    }
}
