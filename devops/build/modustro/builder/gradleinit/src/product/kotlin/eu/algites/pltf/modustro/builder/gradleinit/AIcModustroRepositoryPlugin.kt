package eu.algites.pltf.modustro.builder.gradleinit

import java.io.File
import groovy.json.JsonOutput
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*

class AIcModustroRepositoryPlugin : Plugin<Project> {
    override fun apply(aProject: Project) = with(aProject) {
        require(aProject == rootProject) { "Modustro repository plugin must be applied to the root Project." }
        val locPublicationService = gradle.sharedServices.registerIfAbsent(
            "modustroPublications", AIcModustroPublicationService::class.java
        ) {
            parameters.credentialBaseDirectory.set(layout.projectDirectory)
        }
        extra["modustroPublicationService"] = locPublicationService
        val locPreparePublications = tasks.register<AIcModustroPreparePublicationsTask>("modustroPreparePublications") {
            group = "publishing"
            description = "Establishes the complete expected output set for publication finalization."
            publicationService.set(locPublicationService)
            usesService(locPublicationService)
        }
        val locExportPublications = tasks.register<AIcModustroExportPublicationResultsTask>("modustroExportPublicationResults") {
            group = "publishing"
            description = "Commits complete local publication results for isolated-domain aggregation."
            publicationService.set(locPublicationService)
            usesService(locPublicationService)
            dependsOn(locPreparePublications)
        }
        val locFinalizeScopes = tasks.register<AIcModustroFinalizePublicationScopesTask>("modustroFinalizePublicationScopes") {
            group = "publishing"
            description = "Finalizes owned Version Scopes after all participating isolated build domains."
            publicationService.set(locPublicationService)
            usesService(locPublicationService)
            dependsOn(locExportPublications)
        }
        allprojects {
            val locNativePublicationTasks = tasks.withType<AIcModustroPublishFilesTask>().matching {
                it.outputKind.orNull !in setOf("MODUSTRO_DOCS_SITE", "SCHEMA_SITE")
            }
            locExportPublications.configure { mustRunAfter(locNativePublicationTasks) }
            locFinalizeScopes.configure { mustRunAfter(locNativePublicationTasks) }
            tasks.withType<AIcModustroPublishFilesTask>().all {
                val locOutputId = path
                val locInput = AIcExpectedPublicationInput()
                dependsOn(locPreparePublications)
                locPreparePublications.configure { expectedPublicationInputs.put(locOutputId, locInput) }
            }
        }
        val aRuntime = gradle.extra["modustroGradleRuntime"] as AIcModustroGradleRuntime
        aRuntime.install(extensions.extraProperties)
        @Suppress("UNCHECKED_CAST")
        val locAlgitesResolveMetadataMap = extra["modustroResolveArtifactDirectoryMetadataMap"] as (
            File,
            String?,
            String?,
            String?,
            String?
        ) -> Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val locAlgitesResolveMetadataText = extra["modustroResolveArtifactDirectoryMetadataText"] as (
            File,
            String?,
            String?,
            String?,
            String?,
            String?
        ) -> String

        @Suppress("UNCHECKED_CAST")
        val locAlgitesFlattenMetadata = extra["modustroFlattenArtifactDirectoryMetadata"] as (Map<String, Any?>) -> Map<String, String>

        val locAlgitesResolvedMetadata = aRuntime.resolvedMetadata ?: locAlgitesResolveMetadataMap(
            aRuntime.repositoryRoot ?: rootProject.projectDir,
            "", "current-with-subdirs",
            providers.gradleProperty("repository.name").orNull,
            providers.gradleProperty("repository.visibility").orNull
        )
        val locAlgitesResolvedProperties = locAlgitesFlattenMetadata(locAlgitesResolvedMetadata)

        @Suppress("UNCHECKED_CAST")
        val locAlgitesResolvedRepository = locAlgitesResolvedMetadata["repository"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val locAlgitesResolvedArtifactDirectories = locAlgitesResolvedMetadata["artifactDirectories"] as List<Map<String, Any?>>

        val locRepositoryDirectory = aRuntime.repositoryRoot ?: rootProject.projectDir
        val locAllDomainMetadata = linkedMapOf<String, Map<String, Any?>>()
        val locPendingDomains = java.util.ArrayDeque<String>()
        locPendingDomains.add(".")
        while (locPendingDomains.isNotEmpty()) {
            val locDomain = locPendingDomains.removeFirst()
            if (locDomain in locAllDomainMetadata) continue
            val locMetadata = locAlgitesResolveMetadataMap(locRepositoryDirectory, locDomain, "current-with-subdirs",
                providers.gradleProperty("repository.name").orNull, providers.gradleProperty("repository.visibility").orNull)
            locAllDomainMetadata[locDomain] = AIcMetadataForBuildDomain(locMetadata, locDomain)
            (locMetadata["isolatedBuildDirectories"] as? List<*>).orEmpty().mapNotNull { it?.toString() }.sorted().forEach(locPendingDomains::addLast)
        }
        val locScopeParticipants = linkedMapOf<String, MutableSet<String>>()
        locAllDomainMetadata.forEach { (locDomain, locMetadata) ->
            @Suppress("UNCHECKED_CAST")
            val locDirectories = locMetadata["artifactDirectories"] as? List<Map<String, Any?>> ?: emptyList()
            locDirectories.filter { it["structureKind"] == "artifact" }.forEach { locDirectory ->
                locScopeParticipants.getOrPut(locDirectory["versionScopePath"]?.toString() ?: ".") { linkedSetOf() }.add(locDomain)
            }
        }
        locScopeParticipants.forEach { (locScope, locDomains) ->
            val locOwner = locAllDomainMetadata.keys.filter { it == "." || locScope == it || locScope.startsWith("$it/") }
                .maxByOrNull { if (it == ".") -1 else it.length } ?: "."
            locDomains.add(locOwner)
        }
        val locCrossDomainScopes = locScopeParticipants.filterValues { it.size > 1 }
        locPreparePublications.configure {
            crossDomainVersionScopes.set(locCrossDomainScopes.mapValues { (_, locDomains) -> locDomains.sorted().joinToString(", ") })
            artifactMetadataJson.set(locAlgitesResolvedArtifactDirectories.filter { it["structureKind"] == "artifact" }
                .associate { it["path"].toString() to JsonOutput.toJson(it) })
            domainMetadataJson.set(JsonOutput.toJson(locAllDomainMetadata.toSortedMap()))
            domainId.set(aRuntime.buildRootRelativePath)
            repositoryDirectory.set(locRepositoryDirectory)
        }
        val locManagedDomainDirectories = locAllDomainMetadata.keys.map {
            if (it == ".") locRepositoryDirectory.canonicalFile else locRepositoryDirectory.resolve(it).canonicalFile
        }.toSet()
        val locIncludedPublicationBuilds = gradle.includedBuilds.filter { locIncluded ->
            locIncluded.projectDir.canonicalFile in locManagedDomainDirectories &&
                locIncluded.projectDir.canonicalFile.toPath().startsWith(rootProject.projectDir.canonicalFile.toPath()) &&
                locIncluded.projectDir.canonicalFile != rootProject.projectDir.canonicalFile
        }
        locFinalizeScopes.configure {
            dependsOn(locIncludedPublicationBuilds.map { it.task(":modustroFinalizePublicationScopes") })
        }
        tasks.matching { it.name == "modustroPublish" }.configureEach {
            dependsOn(locIncludedPublicationBuilds.map { it.task(":modustroPublish") })
            finalizedBy(locFinalizeScopes)
        }
        tasks.matching { it.name == "modustroPublishPhase" }.configureEach { finalizedBy(locFinalizeScopes) }
        tasks.matching { it.name in setOf("modustroPublish", "modustroPublishPhase") }.all {
            val locPhase = this
            locExportPublications.configure { mustRunAfter(locPhase) }
            locFinalizeScopes.configure { mustRunAfter(locPhase) }
        }
        if (aRuntime.buildRootRelativePath == ".") {
            tasks.matching { it.name == "refreshModustroDocsSite" }.all {
                val locRefresh = this
                mustRunAfter(locFinalizeScopes)
                locFinalizeScopes.configure { finalizedBy(locRefresh) }
            }
            tasks.matching { it.name in setOf("generateModustroDocsRootIndex", "generateModustroDocsArtifactPublishingIndexes",
                "generateModustroDocsPublishingGroupIndexes", "generateModustroDocsGeneratedIndex", "generateModustroDocsSite") }
                .configureEach { mustRunAfter(locFinalizeScopes) }
        } else {
            tasks.matching { it.name in setOf("publishModustroDocsSite", "refreshModustroDocsSite") }.configureEach { enabled = false }
        }

        rootProject.extra["modustroResolvedArtifactDirectoryMetadata"] = locAlgitesResolvedMetadata
        rootProject.extra["modustroResolvedArtifactDirectoryMetadataProperties"] = locAlgitesResolvedProperties
        rootProject.extra["modustroResolvedRepositoryMetadata"] = locAlgitesResolvedRepository
        rootProject.extra["modustroResolvedArtifactDirectories"] = locAlgitesResolvedArtifactDirectories
        rootProject.extra["modustroResolvedArtifactDirectoriesByGradleProjectPath"] = locAlgitesResolvedArtifactDirectories
            .associateBy { locArtifactDirectory -> locArtifactDirectory["gradleProjectPath"]?.toString() ?: "" }
        rootProject.extra["modustroResolvedArtifactDirectoriesByPath"] = locAlgitesResolvedArtifactDirectories
            .associateBy { locArtifactDirectory -> locArtifactDirectory["path"]?.toString() ?: "" }

        if (tasks.findByName("resolveModustroArtifactDirectoryMetadata") == null) {
            tasks.register("resolveModustroArtifactDirectoryMetadata") {
                group = "modustro"
                description = "Resolves Algites artifact directory metadata."

                val locArtifactDirectoryPath = providers.gradleProperty("directory.path").map { locPath ->
                    if (aRuntime.buildRootRelativePath == ".") locPath else
                        aRuntime.buildRootRelativePath + "/" + locPath.trim('/').takeUnless { it == "." }.orEmpty()
                }.orElse(aRuntime.buildRootRelativePath)
                val locResolutionKind = providers.gradleProperty("resolution.kind").orElse("current-with-subdirs")
                val locOutputKind = providers.gradleProperty("output.kind").orElse("yaml")
                val locRepositoryNameOverride = providers.gradleProperty("repository.name").orElse("")
                val locRepositoryVisibilityOverride = providers.gradleProperty("repository.visibility").orElse("")

                inputs.dir(layout.projectDirectory)
                inputs.property("directory.path", locArtifactDirectoryPath)
                inputs.property("resolution.kind", locResolutionKind)
                inputs.property("output.kind", locOutputKind)
                inputs.property("repository.name", locRepositoryNameOverride)
                inputs.property("repository.visibility", locRepositoryVisibilityOverride)

                doLast {
                    print(
                        locAlgitesResolveMetadataText(
                            (aRuntime.repositoryRoot ?: rootProject.projectDir),
                            locArtifactDirectoryPath.get(),
                            locResolutionKind.get(),
                            locRepositoryNameOverride.get(),
                            locRepositoryVisibilityOverride.get(),
                            locOutputKind.get()
                        )
                    )
                }
            }
        }

        if (tasks.findByName("resolveAllModustroArtifactDirectoryMetadata") == null) {
            tasks.register("resolveAllModustroArtifactDirectoryMetadata") {
                group = "modustro"
                description = "Resolves all Algites artifact directory metadata."

                val locOutputKind = providers.gradleProperty("output.kind").orElse("yaml")
                val locRepositoryNameOverride = providers.gradleProperty("repository.name").orElse("")
                val locRepositoryVisibilityOverride = providers.gradleProperty("repository.visibility").orElse("")

                inputs.dir(layout.projectDirectory)
                inputs.property("output.kind", locOutputKind)
                inputs.property("repository.name", locRepositoryNameOverride)
                inputs.property("repository.visibility", locRepositoryVisibilityOverride)

                doLast {
                    print(
                        locAlgitesResolveMetadataText(
                            (aRuntime.repositoryRoot ?: rootProject.projectDir),
                            aRuntime.buildRootRelativePath,
                            "current-with-subdirs",
                            locRepositoryNameOverride.get(),
                            locRepositoryVisibilityOverride.get(),
                            locOutputKind.get()
                        )
                    )
                }
            }
        }

    }
}
