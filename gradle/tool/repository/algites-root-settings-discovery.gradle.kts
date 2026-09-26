/*
 * Algites repository settings discovery.
 *
 * This script is a thin Settings adapter over the shared artifact directory
 * metadata resolver. It includes discovered Gradle projects and exposes the
 * effective Java download repositories required by those projects.
 */

import java.io.File
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.authentication.http.HttpHeaderAuthentication

val locAlgitesResolverCoreScript = File(rootDir, "gradle/tool/repository/algites-artifact-directory-metadata-resolver.gradle.kts")
if (locAlgitesResolverCoreScript.isFile) {
    apply(from = locAlgitesResolverCoreScript)
} else {
    apply(from = uri("https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/repository/algites-artifact-directory-metadata-resolver.gradle.kts"))
}

val locAlgitesCredentialValuesScript = File(rootDir, "gradle/tool/repository/algites-credential-values.gradle.kts")
if (locAlgitesCredentialValuesScript.isFile) {
    apply(from = locAlgitesCredentialValuesScript)
} else {
    apply(from = uri("https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/repository/algites-credential-values.gradle.kts"))
}

@Suppress("UNCHECKED_CAST")
val locAlgitesResolveCredentialValue = extra["algitesResolveCredentialValue"] as (String, String, String, File) -> String?

val locAlgitesCredentialPreflight = System.getenv("_TMP_ALGITES_CREDENTIAL_PREFLIGHT")
    ?.equals("true", ignoreCase = true) == true

@Suppress("UNCHECKED_CAST")
val locAlgitesResolveMetadataMap = extra["algitesResolveArtifactDirectoryMetadataMap"] as (
    File,
    String?,
    String?,
    String?,
    String?
) -> Map<String, Any?>

val locAlgitesResolvedMetadata = locAlgitesResolveMetadataMap(rootDir, "", "current-with-subdirs", null, null)

@Suppress("UNCHECKED_CAST")
val locAlgitesRepositoryMetadata = locAlgitesResolvedMetadata["repository"] as Map<String, Any?>
@Suppress("UNCHECKED_CAST")
val locAlgitesArtifactDirectories = locAlgitesResolvedMetadata["artifactDirectories"] as List<Map<String, Any?>>

rootProject.name =
    locAlgitesRepositoryMetadata["id"]?.toString()?.takeIf { it.isNotBlank() }
        ?: locAlgitesRepositoryMetadata["name"]?.toString()?.takeIf { it.isNotBlank() }
        ?: rootDir.name

val locIncludedProjectPaths = linkedSetOf<String>()
locAlgitesArtifactDirectories
    .filter { locArtifactDirectory -> locArtifactDirectory["hasGradleBuild"] == true }
    .forEach { locArtifactDirectory ->
        val locArtifactDirectoryPath = locArtifactDirectory["path"]?.toString() ?: return@forEach
        val locGradleProjectPath = locArtifactDirectory["gradleProjectPath"]?.toString() ?: return@forEach
        if (locGradleProjectPath != ":" && locIncludedProjectPaths.add(locGradleProjectPath)) {
            include(locGradleProjectPath)
            project(locGradleProjectPath).projectDir = File(rootDir, locArtifactDirectoryPath)
        }
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

data class AIcdSettingsRepositoryEndpoint(
    val cell: String,
    val id: String,
    val url: String,
    val credentialProfile: String?,
    val profiles: Map<String, AIcdSettingsCredentialProfile>
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
fun AIcCollectJavaDownloadRepositories(
    aRepositories: Any?,
    aCredentialProfiles: Any?,
    aTarget: MutableMap<String, AIcdSettingsRepositoryEndpoint>
) {
    val locRepositories = aRepositories as? Map<*, *> ?: return
    val locProfiles = AIcSettingsCredentialProfiles(aCredentialProfiles)
    locAllowedDownloadVisibilities.forEach { locVisibility ->
        listOf("release", "snapshot").forEach { locStability ->
            val locCell = "java.$locVisibility.$locStability.download"
            val locItems = locRepositories[locCell] as? List<*> ?: return@forEach
            locItems.forEach { locItem ->
                val locMap = locItem as? Map<*, *> ?: return@forEach
                val locEnabled = locMap["enabled"]?.toString()?.toBooleanStrictOrNull() ?: true
                if (!locEnabled) return@forEach
                val locId = locMap["id"]?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: return@forEach
                val locUrl = locMap["url"]?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: return@forEach
                val locProfile = locMap["credentialProfile"]?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" }
                val locEndpoint = AIcdSettingsRepositoryEndpoint(locCell, locId, locUrl, locProfile, locProfiles)
                val locPrevious = aTarget[locId]
                if (locPrevious != null && locPrevious != locEndpoint) {
                    error("Repository endpoint '$locId' resolves inconsistently across the repository build.")
                }
                aTarget[locId] = locEndpoint
            }
        }
    }
}

val locJavaDownloadRepositories = linkedMapOf<String, AIcdSettingsRepositoryEndpoint>()
AIcCollectJavaDownloadRepositories(
    locAlgitesRepositoryMetadata["repositories"],
    locAlgitesRepositoryMetadata["credentialProfiles"],
    locJavaDownloadRepositories
)
locAlgitesArtifactDirectories.forEach { locArtifactDirectory ->
    AIcCollectJavaDownloadRepositories(
        locArtifactDirectory["repositories"],
        locArtifactDirectory["credentialProfiles"],
        locJavaDownloadRepositories
    )
}

dependencyResolutionManagement.repositories {
    locJavaDownloadRepositories.values.forEach { locEndpoint ->
        val locSegments = locEndpoint.cell.split('.')
        val locStability = locSegments[2]
        maven {
            name = locEndpoint.id.replace('-', '_')
            url = uri(locEndpoint.url)
            mavenContent {
                if (locStability == "snapshot") snapshotsOnly() else releasesOnly()
            }
            val locProfileId = locEndpoint.credentialProfile
            if (!locProfileId.isNullOrBlank() && !locAlgitesCredentialPreflight) {
                val locProfile = locEndpoint.profiles[locProfileId]
                    ?: error("Repository endpoint '${locEndpoint.id}' references undefined credential profile '$locProfileId'.")
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
                        "Java/Maven download endpoint '${locEndpoint.id}' uses credential type '${locProfile.type}', " +
                            "which is not supported by the Java/Maven repository adapter. Supported types: basic, bearer, api_key."
                    )
                }
            }
        }
    }
}

println(
    "Algites settings discovery included ${locIncludedProjectPaths.size} Gradle artifact project(s) and " +
        "${locJavaDownloadRepositories.size} resolved Java download repository endpoint(s)."
)
