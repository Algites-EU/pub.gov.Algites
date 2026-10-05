package eu.algites.pltf.modustro.builder.gradleinit

import java.io.File
import org.gradle.api.initialization.Settings
import org.gradle.api.plugins.ExtraPropertiesExtension

/* One instance per Settings domain; Project adapters reuse the same typed API. */
class AIcModustroGradleRuntime(aSettings: Settings) {
    internal val credentials = AIcCredentialValues(aSettings.providers)
    internal var repositoryRoot: File? = null
    internal var buildRootRelativePath: String = "."
    internal var resolvedMetadata: Map<String, Any?>? = null

    val inputSubscriptionClass: Class<*> = eu.algites.pltf.modustro.builder.model.subscription.AIcInputSubscription::class.java
    val resolveMap = fun(aRoot: File, aPath: String?, aKind: String?, aName: String?, aVisibility: String?): Map<String, Any?> =
        AIcToMap(AIcResolveModustroArtifactDirectoryMetadata(aRoot, aPath, aKind, aName, aVisibility))
    val resolveText = fun(aRoot: File, aPath: String?, aKind: String?, aName: String?, aVisibility: String?, aOutput: String?): String =
        AIcFormatOutput(AIcResolveModustroArtifactDirectoryMetadata(aRoot, aPath, aKind, aName, aVisibility), aOutput?.takeIf { it.isNotBlank() } ?: "yaml")

    internal fun install(aExtra: ExtraPropertiesExtension) {
        aExtra["modustroResolveArtifactDirectoryMetadata"] = ::AIcResolveModustroArtifactDirectoryMetadata
        aExtra["modustroResolveArtifactDirectoryMetadataMap"] = resolveMap
        aExtra["modustroResolveArtifactDirectoryMetadataText"] = resolveText
        aExtra["modustroFlattenArtifactDirectoryMetadata"] = ::AIcFlattenDottedProperties
        aExtra["modustroResolveCredentialValue"] = credentials.locAlgitesResolveCredentialValue
        aExtra["modustroCredentialDocumentAvailable"] = credentials.documentAvailable
    }
}

/* Canonical artifact paths stay repository-relative; Gradle project paths belong to this domain. */
internal fun AIcMetadataForBuildDomain(aMetadata: Map<String, Any?>, aBuildRootPath: String): Map<String, Any?> {
    if (aBuildRootPath == ".") return aMetadata
    @Suppress("UNCHECKED_CAST")
    val locDirectories = aMetadata["artifactDirectories"] as List<Map<String, Any?>>
    return aMetadata + ("artifactDirectories" to locDirectories.map { locDirectory ->
        val locPath = locDirectory["path"].toString()
        require(locPath == aBuildRootPath || locPath.startsWith("$aBuildRootPath/")) {
            "Artifact '$locPath' is outside Gradle domain '$aBuildRootPath'."
        }
        val locRelative = if (locPath == aBuildRootPath) "." else locPath.removePrefix("$aBuildRootPath/")
        val locProjectPath = if (locRelative == ".") ":" else ":" + locRelative.replace('/', ':')
        locDirectory + ("gradleProjectPath" to locProjectPath)
    })
}
