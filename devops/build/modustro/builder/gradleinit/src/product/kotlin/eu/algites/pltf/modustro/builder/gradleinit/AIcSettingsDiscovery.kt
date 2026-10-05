package eu.algites.pltf.modustro.builder.gradleinit

import java.io.File
import org.gradle.api.initialization.Settings
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.authentication.http.HttpHeaderAuthentication
import org.gradle.kotlin.dsl.*

fun AIcFindModustroSourceRepositoryRoot(aBuildRoot: File): File {
    val locMatches = mutableListOf<File>()
    var locCurrent: File? = aBuildRoot.canonicalFile
    while (locCurrent != null) {
        if (locCurrent.resolve("modustro-source-repository.yml").isFile) locMatches.add(locCurrent)
        locCurrent = locCurrent.parentFile
    }
    if (locMatches.size != 1) {
        error("Expected exactly one modustro-source-repository.yml on the ancestor path of Gradle build root '${aBuildRoot.path}', found ${locMatches.size}.")
    }
    return locMatches.single().canonicalFile
}

internal fun AIcDiscover(aSettings: Settings, aRuntime: AIcModustroGradleRuntime) = with(aSettings) {
    val locModustroBuildRoot = rootDir.canonicalFile
    val locModustroSourceRepositoryRoot = AIcFindModustroSourceRepositoryRoot(locModustroBuildRoot)
    val locModustroBuildRootRelativePath = locModustroSourceRepositoryRoot.toPath().relativize(locModustroBuildRoot.toPath())
        .toString().replace(File.separatorChar, '/').ifBlank { "." }

    val locAlgitesResolveCredentialValue = aRuntime.credentials.locAlgitesResolveCredentialValue
    val locAlgitesCredentialPreflight = System.getenv("_TMP_ALGITES_CREDENTIAL_PREFLIGHT")
        ?.equals("true", ignoreCase = true) == true
    val locAlgitesResolveMetadataMap = aRuntime.resolveMap

    val locAlgitesRawMetadata = locAlgitesResolveMetadataMap(
        locModustroSourceRepositoryRoot, locModustroBuildRootRelativePath, "current-with-subdirs",
        providers.gradleProperty("repository.name").orNull,
        providers.gradleProperty("repository.visibility").orNull
    )

    val locAlgitesResolvedMetadata = AIcMetadataForBuildDomain(locAlgitesRawMetadata, locModustroBuildRootRelativePath)
    aRuntime.buildRootRelativePath = locModustroBuildRootRelativePath
    aRuntime.repositoryRoot = locModustroSourceRepositoryRoot
    aRuntime.resolvedMetadata = locAlgitesResolvedMetadata

    @Suppress("UNCHECKED_CAST")
    val locAlgitesRepositoryMetadata = locAlgitesResolvedMetadata["repository"] as Map<String, Any?>
    @Suppress("UNCHECKED_CAST")
    val locAlgitesArtifactDirectories = locAlgitesResolvedMetadata["artifactDirectories"] as List<Map<String, Any?>>

    @Suppress("UNCHECKED_CAST")
    val locModustroIsolatedBuildDirectories = (locAlgitesResolvedMetadata["isolatedBuildDirectories"] as? List<*>)
        .orEmpty().mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
    val locRepositoryId = locAlgitesRepositoryMetadata["id"]?.toString()?.takeIf { it.isNotBlank() }
        ?: locAlgitesRepositoryMetadata["name"]?.toString()?.takeIf { it.isNotBlank() }
        ?: locModustroSourceRepositoryRoot.name
    rootProject.name = if (locModustroBuildRootRelativePath == ".") locRepositoryId
    else locRepositoryId + "_" + locModustroBuildRootRelativePath.replace('/', '.')

    val locIncludedProjectPaths = linkedSetOf<String>()
    locAlgitesArtifactDirectories
        .filter { locArtifactDirectory ->
            val locTechnologyKinds = (locArtifactDirectory["technologyKinds"] as? List<*>)
                .orEmpty().mapNotNull { it?.toString()?.trim()?.lowercase() }
            locArtifactDirectory["hasGradleBuild"] == true ||
                (locArtifactDirectory["structureKind"]?.toString() == "artifact" && locTechnologyKinds.any { it in setOf("java", "python") })
        }
        .forEach { locArtifactDirectory ->
            val locArtifactDirectoryPath = locArtifactDirectory["path"]?.toString() ?: return@forEach
            val locDomainRelativePath = when {
                locModustroBuildRootRelativePath == "." -> locArtifactDirectoryPath
                locArtifactDirectoryPath == locModustroBuildRootRelativePath -> "."
                locArtifactDirectoryPath.startsWith("$locModustroBuildRootRelativePath/") -> locArtifactDirectoryPath.removePrefix("$locModustroBuildRootRelativePath/")
                else -> return@forEach
            }
            val locGradleProjectPath = if (locDomainRelativePath == "." || locDomainRelativePath.isBlank()) ":"
                else ":" + locDomainRelativePath.split('/').filter(String::isNotBlank).joinToString(":")
            if (locGradleProjectPath != ":" && locIncludedProjectPaths.add(locGradleProjectPath)) {
                include(locGradleProjectPath)
                project(locGradleProjectPath).projectDir = File(locModustroSourceRepositoryRoot, locArtifactDirectoryPath)
            }
        }

    locModustroIsolatedBuildDirectories.sorted().forEach { locRelativeBuildPath ->
        val locIncludedBuildDirectory = File(locModustroSourceRepositoryRoot, locRelativeBuildPath).canonicalFile
        if (!locIncludedBuildDirectory.toPath().startsWith(locModustroBuildRoot.toPath())) {
            error("Isolated build '$locRelativeBuildPath' is outside current Gradle build root '${locModustroBuildRoot.path}'.")
        }
        includeBuild(locIncludedBuildDirectory)
    }

    val locRepositoryVisibility = locAlgitesRepositoryMetadata["visibility"]?.toString()?.lowercase() ?: "pub"
    val locAllowedDownloadVisibilities = when (locRepositoryVisibility) {
        "pub" -> listOf("public")
        "priv" -> listOf("public", "private")
        else -> error("Unsupported Algites source repository visibility '$locRepositoryVisibility'.")
    }

    data class AIcdSettingsCredentialProfile(
        val id: String,
        val type: String,
        val configuration: Map<String, String>
    )

    data class AIcdSettingsInputSubscription(
        val id: String,
        val uri: String,
        val credentialProfile: AIcdSettingsCredentialProfile?,
        val stability: String,
        val order: Int
    )

    @Suppress("UNCHECKED_CAST")
    fun AIcSettingsCredentialProfiles(aValue: Any?): Map<String, AIcdSettingsCredentialProfile> {
        val locProfiles = aValue as? Map<*, *> ?: return emptyMap()
        return locProfiles.entries.mapNotNull { locEntry ->
            val locId = locEntry.key?.toString() ?: return@mapNotNull null
            val locDefinition = locEntry.value as? Map<*, *> ?: return@mapNotNull null
            val locType = locDefinition["type"]?.toString()?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val locConfiguration = (locDefinition["configuration"] as? Map<*, *>)
                ?.entries
                ?.associate { it.key.toString() to it.value.toString() }
                ?: emptyMap()
            locId to AIcdSettingsCredentialProfile(locId, locType, locConfiguration)
        }.toMap()
    }

    @Suppress("UNCHECKED_CAST")
    fun AIcCollectJavaInputSubscriptions(
        aInputSubscriptions: Any?,
        aCredentialProfiles: Any?,
        aTarget: MutableMap<String, AIcdSettingsInputSubscription>
    ) {
        val locConfigurations = aInputSubscriptions as? Map<*, *> ?: return
        val locItems = locConfigurations["java.native_product_binaries"] as? List<*> ?: return
        val locProfiles = AIcSettingsCredentialProfiles(aCredentialProfiles)
        locItems.forEach subscriptionLoop@ { locItem ->
            val locMap = locItem as? Map<*, *>
                ?: error("Effective InputSubscriptions java.native_product_binaries contains a non-object item.")
            val locEnabled = locMap["enabled"] as? Boolean
                ?: error("Effective input subscription has no boolean enabled state.")
            if (!locEnabled) return@subscriptionLoop
            val locVisibility = locMap["visibility"]?.toString()?.trim()?.lowercase() ?: "public"
            if (locVisibility !in locAllowedDownloadVisibilities) return@subscriptionLoop
            val locAdapter = locMap["subscriptionAdapter"]?.toString()?.trim()?.lowercase()
                ?: error("Enabled Java input subscription has no SubscriptionAdapter.")
            if (locAdapter != "maven-repository") return@subscriptionLoop
            val locId = locMap["id"]?.toString()?.trim()?.takeIf { it.isNotBlank() }
                ?: error("Enabled Java input subscription has no id.")
            val locUri = locMap["subscriptionUri"]?.toString()?.trim()?.takeIf { it.isNotBlank() }
                ?: error("Enabled Java input subscription '$locId' has no SubscriptionUri.")
            val locStability = locMap["stability"]?.toString()?.trim()?.lowercase()?.takeIf { it in setOf("snapshot", "release") }
                ?: error("Enabled Java input subscription '$locId' has no valid Stability.")
            val locOrder = (locMap["subscriptionOrder"] as? Number)?.toInt()
                ?: locMap["subscriptionOrder"]?.toString()?.toIntOrNull() ?: 0
            val locProfileId = locMap["subscriptionCredentialProfile"]?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" }
            val locProfile = locProfileId?.let { locIdValue ->
                locProfiles[locIdValue]
                    ?: error("Input subscription '$locId' references undefined credential profile '$locIdValue'.")
            }
            val locSubscription = AIcdSettingsInputSubscription(locId, locUri, locProfile, locStability, locOrder)
            val locPrevious = aTarget[locId]
            if (locPrevious != null && locPrevious != locSubscription) {
                error("Input subscription '$locId' resolves inconsistently across the repository build.")
            }
            aTarget[locId] = locSubscription
        }
    }

    val locJavaInputSubscriptions = linkedMapOf<String, AIcdSettingsInputSubscription>()
    AIcCollectJavaInputSubscriptions(
        locAlgitesRepositoryMetadata["inputSubscriptions"],
        locAlgitesRepositoryMetadata["credentialProfiles"],
        locJavaInputSubscriptions
    )
    locAlgitesArtifactDirectories.forEach { locArtifactDirectory ->
        AIcCollectJavaInputSubscriptions(
            locArtifactDirectory["inputSubscriptions"],
            locArtifactDirectory["credentialProfiles"],
            locJavaInputSubscriptions
        )
    }

    dependencyResolutionManagement.repositories {
        locJavaInputSubscriptions.values.sortedWith(compareBy<AIcdSettingsInputSubscription> { it.order }.thenBy { it.id }).forEach { locEndpoint ->
            val locStability = locEndpoint.stability
            maven {
                name = locEndpoint.id.replace('-', '_')
                url = java.net.URI(locEndpoint.uri)
                mavenContent {
                    if (locStability == "snapshot") snapshotsOnly() else releasesOnly()
                }
                val locProfile = locEndpoint.credentialProfile
                if (locProfile != null && !locAlgitesCredentialPreflight) {
                    when (locProfile.type) {
                        "basic" -> {
                            val locUsername = locAlgitesResolveCredentialValue(locProfile.id, locProfile.type, "Username", rootDir)
                            val locPassword = locAlgitesResolveCredentialValue(locProfile.id, locProfile.type, "Password", rootDir)
                            if (locUsername.isNullOrEmpty() || locPassword.isNullOrEmpty()) {
                                error(
                                    "Credential profile '${locProfile.id}' type 'basic' is required by '${locEndpoint.id}' but is not available in ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS or the local Algites secure-store credential document."
                                )
                            }
                            credentials {
                                username = locUsername
                                password = locPassword
                            }
                        }
                        "bearer" -> {
                            val locToken = locAlgitesResolveCredentialValue(locProfile.id, locProfile.type, "Token", rootDir)
                                ?: error(
                                    "Credential profile '${locProfile.id}' type 'bearer' is required by '${locEndpoint.id}' but is not available in ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS or the local Algites secure-store credential document."
                                )
                            credentials(HttpHeaderCredentials::class) {
                                name = "Authorization"
                                value = "Bearer $locToken"
                            }
                            authentication { create<HttpHeaderAuthentication>("header") }
                        }
                        "api_key" -> {
                            val locApiKey = locAlgitesResolveCredentialValue(locProfile.id, locProfile.type, "ApiKey", rootDir)
                                ?: error(
                                    "Credential profile '${locProfile.id}' type 'api_key' is required by '${locEndpoint.id}' but is not available in ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS or the local Algites secure-store credential document."
                                )
                            val locHeaderName = locProfile.configuration["headerName"]?.takeIf { it.isNotBlank() }
                                ?: error(
                                    "Credential profile '${locProfile.id}' type 'api_key' requires configuration.headerName for Java/Maven repository access."
                                )
                            credentials(HttpHeaderCredentials::class) {
                                name = locHeaderName
                                value = locApiKey
                            }
                            authentication { create<HttpHeaderAuthentication>("header") }
                        }
                        else -> error(
                            "Java/Maven input subscription '${locEndpoint.id}' uses credential type '${locProfile.type}', " +
                                "which is not supported by the Java/Maven repository adapter. Supported types: basic, bearer, api_key."
                        )
                    }
                }
            }
        }
    }

    println(
        "Algites settings discovery included ${locIncludedProjectPaths.size} Gradle artifact project(s) and " +
            "${locJavaInputSubscriptions.size} resolved Java native-product-binary input subscription(s)."
    )

}
