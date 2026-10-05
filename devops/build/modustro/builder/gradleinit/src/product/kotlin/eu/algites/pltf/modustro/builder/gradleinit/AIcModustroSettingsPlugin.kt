package eu.algites.pltf.modustro.builder.gradleinit

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.resolve.RepositoriesMode
import org.gradle.kotlin.dsl.*

abstract class AIcModustroSettingsPlugin : Plugin<Settings> {
    @get:javax.inject.Inject abstract val buildEvents: org.gradle.build.event.BuildEventsListenerRegistry
    override fun apply(aSettings: Settings) = with(aSettings) {
        val locUseMavenLocal = providers.gradleProperty("modustro.useMavenLocalForResolution")
            .orElse(providers.environmentVariable("MODUSTRO_USE_MAVEN_LOCAL_FOR_RESOLUTION"))
            .map { it.equals("true", ignoreCase = true) }.orElse(false).get()
        pluginManagement.repositories.apply {
            gradlePluginPortal()
            if (locUseMavenLocal) mavenLocal()
            mavenCentral()
            maven {
                name = "algites-public-snapshots"
                url = java.net.URI("https://dl.cloudsmith.io/public/algites/java-snapshots-pub/maven/")
                mavenContent { snapshotsOnly() }
            }
        }
        dependencyResolutionManagement.repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
        dependencyResolutionManagement.repositories.apply {
            if (locUseMavenLocal) mavenLocal()
            mavenCentral()
        }
        val locService = gradle.sharedServices.registerIfAbsent("modustroPublications", AIcModustroPublicationService::class.java) {
            parameters.credentialBaseDirectory.set(settingsDir)
        }
        buildEvents.onTaskCompletion(locService)
        val locRuntime = AIcModustroGradleRuntime(aSettings)
        gradle.extra["modustroGradleRuntime"] = locRuntime
        locRuntime.install(extensions.extraProperties)
        gradle.beforeProject { locRuntime.install(extensions.extraProperties) }
        if (!providers.gradleProperty("modustro.gradleinit.metadataOnly")
                .map { it.equals("true", ignoreCase = true) }.orElse(false).get()) {
            AIcDiscover(aSettings, locRuntime)
        }
    }
}
