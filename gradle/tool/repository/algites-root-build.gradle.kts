/*
 * Algites generic repository build conventions.
 *
 * Public entry-point location is stable. The implementation consumes the
 * effective metadata resolved from algites-source-repository.yml and nested
 * algites-artifact.yml files.
 */

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.authentication.http.HttpHeaderAuthentication
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

apply(plugin = "base")

val locAlgitesResolverWrapperScript = rootProject.file("gradle/tool/repository/algites-artifact-directory-metadata-resolver-wrapper.gradle.kts")
if (locAlgitesResolverWrapperScript.isFile) {
    apply(from = locAlgitesResolverWrapperScript)
} else {
    apply(from = uri("https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/repository/algites-artifact-directory-metadata-resolver-wrapper.gradle.kts"))
}

val locAlgitesCredentialValuesScript = rootProject.file("gradle/tool/repository/algites-credential-values.gradle.kts")
if (locAlgitesCredentialValuesScript.isFile) {
    apply(from = locAlgitesCredentialValuesScript)
} else {
    apply(from = uri("https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/repository/algites-credential-values.gradle.kts"))
}

val locAlgitesLicensingScript = rootProject.file("gradle/tool/licensing/algites-licensing.gradle.kts")
if (locAlgitesLicensingScript.isFile) {
    apply(from = locAlgitesLicensingScript)
} else {
    apply(from = uri("https://raw.githubusercontent.com/Algites-EU/pub.gov.Algites/main/gradle/tool/licensing/algites-licensing.gradle.kts"))
}

@Suppress("UNCHECKED_CAST")
val locAlgitesLicensesForPathAndContentKind = rootProject.extra["algitesLicensesForPathAndContentKind"] as (String, String) -> List<Map<String, String?>>

@Suppress("UNCHECKED_CAST")
val locAlgitesResolveCredentialValue = extra["algitesResolveCredentialValue"] as (String, String, String, File) -> String?

val algitesCredentialPreflight = System.getenv("_TMP_ALGITES_CREDENTIAL_PREFLIGHT")
    ?.equals("true", ignoreCase = true) == true

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
    (providers.gradleProperty(aName).orNull
        ?: providers.environmentVariable(aName).orNull)
        ?.trim()
        ?.takeIf { it.isNotBlank() }

fun AIcAlgitesStringList(aValue: Any?): List<String> {
    return when (aValue) {
        is List<*> -> aValue.mapNotNull { it?.toString()?.trim()?.lowercase()?.takeIf(String::isNotBlank) }
        null -> emptyList()
        else -> aValue.toString().split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }
    }
}

data class AIcdAlgitesRepositoryEndpoint(
    val cell: String,
    val id: String,
    val url: String,
    val credentialProfile: String?,
    val usageProviderAdapter: String?
)

data class AIcdAlgitesCredentialProfile(
    val id: String,
    val type: String,
    val configuration: Map<String, String>
)

@Suppress("UNCHECKED_CAST")
fun AIcAlgitesRepositoryEndpoints(aValue: Any?, aCell: String): List<AIcdAlgitesRepositoryEndpoint> {
    val locRepositories = aValue as? Map<*, *> ?: return emptyList()
    val locItems = locRepositories[aCell] as? List<*> ?: return emptyList()
    return locItems.mapNotNull { locItem ->
        val locMap = locItem as? Map<*, *> ?: return@mapNotNull null
        val locEnabled = locMap["enabled"]?.toString()?.toBooleanStrictOrNull() ?: true
        if (!locEnabled) return@mapNotNull null
        val locId = locMap["id"]?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val locUrl = locMap["url"]?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val locCredentialProfile = locMap["credentialProfile"]?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        val locUsageProviderAdapter = locMap["usageProviderAdapter"]?.toString()?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it != "null" }
        AIcdAlgitesRepositoryEndpoint(aCell, locId, locUrl, locCredentialProfile, locUsageProviderAdapter)
    }
}

@Suppress("UNCHECKED_CAST")
fun AIcAlgitesCredentialProfiles(aValue: Any?): Map<String, AIcdAlgitesCredentialProfile> {
    val locProfiles = aValue as? Map<*, *> ?: return emptyMap()
    val locResult = linkedMapOf<String, AIcdAlgitesCredentialProfile>()
    locProfiles.forEach { (locRawId, locRawDefinition) ->
        val locId = locRawId?.toString() ?: return@forEach
        val locDefinition = locRawDefinition as? Map<*, *> ?: return@forEach
        val locType = locDefinition["type"]?.toString()?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return@forEach
        val locConfiguration = (locDefinition["configuration"] as? Map<*, *>)
            ?.entries
            ?.associate { locEntry -> locEntry.key.toString() to locEntry.value.toString() }
            ?: emptyMap()
        locResult[locId] = AIcdAlgitesCredentialProfile(locId, locType, locConfiguration)
    }
    return locResult
}

fun AIcAlgitesCredentialValue(aProfile: AIcdAlgitesCredentialProfile, aField: String): String? =
    locAlgitesResolveCredentialValue(aProfile.id, aProfile.type, aField, rootProject.projectDir)

fun AIcAlgitesRequireBasicCredential(aProfile: AIcdAlgitesCredentialProfile): Pair<String, String> {
    val locUsername = AIcAlgitesCredentialValue(aProfile, "username")
    val locPassword = AIcAlgitesCredentialValue(aProfile, "password")
    if (locUsername.isNullOrEmpty() || locPassword.isNullOrEmpty()) {
        throw GradleException(
            "Credential profile '${aProfile.id}' type 'basic' is not available in ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS or the local Algites secure-store credential document."
        )
    }
    return locUsername to locPassword
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


fun AIcAlgitesCanonicalArtifactId(aProjectPath: String): String {
    val locPathDots = aProjectPath.removePrefix(":").replace(':', '.')
    return if (locPathDots.isBlank()) rootProject.name else "${rootProject.name}_$locPathDots"
}

fun AIcAlgitesSnapshotVersionForTechnology(aReleaseVersion: String, aTechnology: String): String = when (aTechnology) {
    "java" -> "$aReleaseVersion-SNAPSHOT"
    "python" -> AIcAlgitesPythonVersion("$aReleaseVersion-SNAPSHOT")
    else -> "$aReleaseVersion-SNAPSHOT"
}

fun AIcAlgitesCloudsmithHeaders(aEndpoint: AIcdAlgitesRepositoryEndpoint, aProfiles: Map<String, AIcdAlgitesCredentialProfile>): Map<String, String> {
    val locProfileId = aEndpoint.credentialProfile
        ?: throw GradleException("Cloudsmith manage endpoint '${aEndpoint.id}' requires a credentialProfile.")
    val locProfile = aProfiles[locProfileId]
        ?: throw GradleException("Cloudsmith manage endpoint '${aEndpoint.id}' references undefined credential profile '$locProfileId'.")
    return when (locProfile.type) {
        "api-key" -> {
            val locApiKey = AIcAlgitesCredentialValue(locProfile, "apiKey")
                ?: throw GradleException("Credential profile '$locProfileId' does not provide required apiKey.")
            val locHeaderName = locProfile.configuration["headerName"]?.takeIf { it.isNotBlank() } ?: "Authorization"
            val locPrefix = locProfile.configuration["headerValuePrefix"] ?: "token "
            mapOf(locHeaderName to "$locPrefix$locApiKey")
        }
        "bearer" -> {
            val locToken = AIcAlgitesCredentialValue(locProfile, "token")
                ?: throw GradleException("Credential profile '$locProfileId' does not provide required token.")
            mapOf("Authorization" to "Bearer $locToken")
        }
        else -> throw GradleException(
            "Cloudsmith manage endpoint '${aEndpoint.id}' uses credential type '${locProfile.type}'. " +
                "The Cloudsmith management adapter supports 'api-key' and 'bearer'."
        )
    }
}

fun AIcAlgitesHttpRequest(aMethod: String, aUrl: String, aHeaders: Map<String, String>): Pair<Int, String> {
    val locConnection = URL(aUrl).openConnection() as HttpURLConnection
    locConnection.requestMethod = aMethod
    locConnection.connectTimeout = 30_000
    locConnection.readTimeout = 60_000
    locConnection.instanceFollowRedirects = true
    locConnection.setRequestProperty("Accept", "application/json")
    aHeaders.forEach { (locName, locValue) -> locConnection.setRequestProperty(locName, locValue) }
    return try {
        val locStatus = locConnection.responseCode
        val locStream = if (locStatus in 200..299) locConnection.inputStream else locConnection.errorStream
        val locBody = locStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        locStatus to locBody
    } finally {
        locConnection.disconnect()
    }
}

fun AIcAlgitesHttpJsonRequest(
    aMethod: String,
    aUrl: String,
    aHeaders: Map<String, String>,
    aBody: String
): Pair<Int, String> {
    val locConnection = URL(aUrl).openConnection() as HttpURLConnection
    locConnection.requestMethod = aMethod
    locConnection.connectTimeout = 30_000
    locConnection.readTimeout = 60_000
    locConnection.instanceFollowRedirects = true
    locConnection.doOutput = true
    locConnection.setRequestProperty("Accept", "application/json")
    locConnection.setRequestProperty("Content-Type", "application/json")
    aHeaders.forEach { (locName, locValue) -> locConnection.setRequestProperty(locName, locValue) }
    return try {
        locConnection.outputStream.use { locStream ->
            locStream.write(aBody.toByteArray(Charsets.UTF_8))
        }
        val locStatus = locConnection.responseCode
        val locStream = if (locStatus in 200..299) locConnection.inputStream else locConnection.errorStream
        val locResponseBody = locStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        locStatus to locResponseBody
    } finally {
        locConnection.disconnect()
    }
}

fun AIcAlgitesUrlPathSegment(aValue: String): String =
    URLEncoder.encode(aValue, StandardCharsets.UTF_8.toString()).replace("+", "%20")

fun AIcAlgitesRepsyLoginUrl(aEndpointUrl: String): String {
    val locMarker = "/api/"
    val locMarkerIndex = aEndpointUrl.indexOf(locMarker)
    if (locMarkerIndex < 0) {
        throw GradleException(
            "Repsy manage URL '$aEndpointUrl' must contain '/api/' so the adapter can derive the Repsy authentication endpoint."
        )
    }
    return aEndpointUrl.substring(0, locMarkerIndex).trimEnd('/') + "/api/auth/login"
}

@Suppress("UNCHECKED_CAST")
fun AIcAlgitesRepsyHeaders(
    aEndpoint: AIcdAlgitesRepositoryEndpoint,
    aProfiles: Map<String, AIcdAlgitesCredentialProfile>
): Map<String, String> {
    val locProfileId = aEndpoint.credentialProfile
        ?: throw GradleException("Repsy manage endpoint '${aEndpoint.id}' requires a credentialProfile.")
    val locProfile = aProfiles[locProfileId]
        ?: throw GradleException("Repsy manage endpoint '${aEndpoint.id}' references undefined credential profile '$locProfileId'.")
    val locToken = when (locProfile.type) {
        "bearer" -> AIcAlgitesCredentialValue(locProfile, "token")
            ?: throw GradleException("Credential profile '$locProfileId' does not provide required token.")
        "basic" -> {
            val (locUsername, locPassword) = AIcAlgitesRequireBasicCredential(locProfile)
            val locLoginBody = JsonOutput.toJson(mapOf("username" to locUsername, "password" to locPassword))
            val (locStatus, locBody) = AIcAlgitesHttpJsonRequest(
                "POST",
                AIcAlgitesRepsyLoginUrl(aEndpoint.url),
                emptyMap(),
                locLoginBody
            )
            if (locStatus !in 200..299) {
                throw GradleException(
                    "Repsy authentication failed for endpoint '${aEndpoint.id}' with HTTP $locStatus: $locBody"
                )
            }
            val locParsed = JsonSlurper().parseText(locBody) as? Map<*, *>
            val locData = locParsed?.get("data") as? Map<*, *>
            locData?.get("token")?.toString()?.takeIf { it.isNotBlank() }
                ?: throw GradleException("Repsy authentication response for endpoint '${aEndpoint.id}' did not contain data.token.")
        }
        else -> throw GradleException(
            "Repsy manage endpoint '${aEndpoint.id}' uses credential type '${locProfile.type}'. " +
                "The Repsy management adapter supports 'basic' and 'bearer'."
        )
    }
    return mapOf("Authorization" to "Bearer $locToken")
}

fun AIcAlgitesDeleteRepsySnapshot(
    aEndpoint: AIcdAlgitesRepositoryEndpoint,
    aProfiles: Map<String, AIcdAlgitesCredentialProfile>,
    aPackageName: String,
    aVersion: String,
    aFormat: String,
    aGroupId: String?
): Int {
    val locBaseUrl = aEndpoint.url.trimEnd('/')
    val locExpectedSuffix = when (aFormat.lowercase()) {
        "maven" -> "/api/mvn/artifacts/"
        "python" -> "/api/pypi/packages/"
        else -> throw GradleException(
            "Repsy management adapter does not support package format '$aFormat' for endpoint '${aEndpoint.id}'."
        )
    }
    if (!locBaseUrl.contains(locExpectedSuffix)) {
        throw GradleException(
            "Repsy manage endpoint '${aEndpoint.id}' URL '$locBaseUrl' must identify the configured repository using " +
                "'$locExpectedSuffix<repoName>' for format '$aFormat'."
        )
    }
    val locDeleteUrl = when (aFormat.lowercase()) {
        "maven" -> {
            val locGroupId = aGroupId?.takeIf { it.isNotBlank() }
                ?: throw GradleException("Repsy Maven snapshot cleanup for '$aPackageName' requires an effective groupId.")
            "$locBaseUrl/${AIcAlgitesUrlPathSegment(locGroupId)}/${AIcAlgitesUrlPathSegment(aPackageName)}/versions/${AIcAlgitesUrlPathSegment(aVersion)}"
        }
        "python" ->
            "$locBaseUrl/${AIcAlgitesUrlPathSegment(aPackageName)}/releases/${AIcAlgitesUrlPathSegment(aVersion)}"
        else -> error("unreachable")
    }
    val locHeaders = AIcAlgitesRepsyHeaders(aEndpoint, aProfiles)
    val (locStatus, locBody) = AIcAlgitesHttpRequest("DELETE", locDeleteUrl, locHeaders)
    return when {
        locStatus in 200..299 -> 1
        locStatus == 404 -> 0
        else -> throw GradleException(
            "Repsy package deletion failed for '$aPackageName/$aVersion' at endpoint '${aEndpoint.id}' " +
                "with HTTP $locStatus: $locBody"
        )
    }
}

@Suppress("UNCHECKED_CAST")
fun AIcAlgitesDeleteCloudsmithSnapshot(
    aEndpoint: AIcdAlgitesRepositoryEndpoint,
    aProfiles: Map<String, AIcdAlgitesCredentialProfile>,
    aPackageName: String,
    aVersion: String,
    aFormat: String
): Int {
    val locBaseUrl = aEndpoint.url.trimEnd('/') + "/"
    val locHeaders = AIcAlgitesCloudsmithHeaders(aEndpoint, aProfiles)
    val locQuery = "name:$aPackageName AND version:$aVersion AND format:$aFormat"
    val locEncodedQuery = URLEncoder.encode(locQuery, StandardCharsets.UTF_8.toString())
    val locListUrl = "${locBaseUrl}?page_size=100&query=$locEncodedQuery"
    val (locStatus, locBody) = AIcAlgitesHttpRequest("GET", locListUrl, locHeaders)
    if (locStatus !in 200..299) {
        throw GradleException("Cloudsmith package lookup failed for endpoint '${aEndpoint.id}' with HTTP $locStatus: $locBody")
    }
    val locParsed = JsonSlurper().parseText(locBody)
    val locItems = when (locParsed) {
        is List<*> -> locParsed
        is Map<*, *> -> (locParsed["results"] as? List<*>) ?: (locParsed["data"] as? List<*>) ?: emptyList<Any?>()
        else -> emptyList<Any?>()
    }
    val locMatches = locItems.mapNotNull { it as? Map<*, *> }.filter { locItem ->
        locItem["name"]?.toString() == aPackageName &&
            locItem["version"]?.toString() == aVersion &&
            locItem["format"]?.toString()?.equals(aFormat, ignoreCase = true) == true
    }
    var locDeleted = 0
    locMatches.forEach { locItem ->
        val locIdentifier = locItem["slug_perm"]?.toString()?.takeIf { it.isNotBlank() }
            ?: locItem["identifier_perm"]?.toString()?.takeIf { it.isNotBlank() }
            ?: throw GradleException("Cloudsmith package '$aPackageName/$aVersion' did not expose a permanent identifier.")
        val (locDeleteStatus, locDeleteBody) = AIcAlgitesHttpRequest("DELETE", "$locBaseUrl$locIdentifier/", locHeaders)
        if (locDeleteStatus !in setOf(204, 404)) {
            throw GradleException(
                "Cloudsmith package deletion failed for '$aPackageName/$aVersion' at endpoint '${aEndpoint.id}' " +
                    "with HTTP $locDeleteStatus: $locDeleteBody"
            )
        }
        if (locDeleteStatus == 204) locDeleted++
    }
    return locDeleted
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
                "Define top-level groupId in algites-source-repository.yml, algites-artifact-set.yml, or algites-artifact.yml."
        )
    }
}

val algitesRepositoryVisibility = algitesResolvedRepositoryMetadata["visibility"]
    ?.toString()
    ?.lowercase()
    ?.takeIf { it.isNotBlank() }
    ?: throw GradleException("Algites source repository visibility could not be resolved.")

val algitesRequestedRepositoryVisibility = algitesGradleOrEnvironmentValue("ALGITES_VISIBILITY")
    ?.lowercase()
    ?.takeIf { it.isNotBlank() }
if (algitesRequestedRepositoryVisibility != null && algitesRequestedRepositoryVisibility != algitesRepositoryVisibility) {
    throw GradleException(
        "Requested Algites visibility '$algitesRequestedRepositoryVisibility' does not match " +
            "source repository visibility '$algitesRepositoryVisibility'."
    )
}

val algitesPublicationRepositoryVisibility = when (algitesRepositoryVisibility) {
    "pub" -> "public"
    "priv" -> "private"
    else -> throw GradleException("Unsupported Algites repository visibility '$algitesRepositoryVisibility'.")
}

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

val algitesValidateReleaseTechnologyKinds = tasks.register("validateAlgitesReleaseTechnologyKinds") {
    group = "algites"
    description = "Validates that a release TechnologyKind selection is complete unless incomplete release was explicitly allowed."

    doLast {
        val locAllowIncomplete = (
            algitesGradleOrEnvironmentValue("algites.release.allowIncompleteTechnologyKinds")
                ?: System.getenv("ALGITES_RELEASE_ALLOW_INCOMPLETE_TECHNOLOGY_KINDS")
                ?: "false"
            ).toBooleanStrictOrNull()
            ?: throw GradleException("algites.release.allowIncompleteTechnologyKinds must be true or false.")

        val locIncompleteArtifacts = mutableListOf<String>()
        algitesResolvedArtifactDirectoriesByGradleProjectPath.toSortedMap().forEach { (locProjectPath, locMetadata) ->
            val locDeclaredTechnologyKinds = AIcAlgitesStringList(locMetadata["technologyKinds"]).toSet()
            if (locDeclaredTechnologyKinds.isEmpty()) return@forEach

            val locSelectedTechnologyKinds = if (algitesRequestedTechnologyKinds.isEmpty()) {
                locDeclaredTechnologyKinds
            } else {
                locDeclaredTechnologyKinds.intersect(algitesRequestedTechnologyKinds)
            }
            val locMissingTechnologyKinds = locDeclaredTechnologyKinds - locSelectedTechnologyKinds
            if (locMissingTechnologyKinds.isNotEmpty()) {
                locIncompleteArtifacts += buildString {
                    append(locProjectPath)
                    append(": declared=")
                    append(locDeclaredTechnologyKinds.sorted().joinToString(","))
                    append(" selected=")
                    append(locSelectedTechnologyKinds.sorted().joinToString(",").ifBlank { "<none>" })
                    append(" missing=")
                    append(locMissingTechnologyKinds.sorted().joinToString(","))
                }
            }
        }

        if (locIncompleteArtifacts.isNotEmpty()) {
            val locMessage = buildString {
                appendLine("Incomplete TechnologyKind selection for release.")
                locIncompleteArtifacts.forEach { locEntry -> appendLine(" - $locEntry") }
                append("A release is complete by default. Explicitly allow an incomplete release only when the omitted TechnologyKinds are intentionally excluded from this release version.")
            }
            if (!locAllowIncomplete) {
                throw GradleException(locMessage)
            }
            logger.warn(locMessage)
            logger.warn("Incomplete release explicitly allowed; omitted TechnologyKinds cannot be added later under the same logical release version.")
        } else {
            logger.lifecycle("Release TechnologyKind selection is complete.")
        }
    }
}


abstract class AIcResolveAlgitesRequiredCredentialsTask : DefaultTask() {
    @get:Input
    abstract val planJson: Property<String>

    @get:Input
    abstract val credentialCount: Property<Int>

    @get:Optional
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun resolve() {
        val locJson = planJson.get()
        if (!outputFile.isPresent) {
            println(locJson)
            return
        }

        val locOutputFile = outputFile.get().asFile
        locOutputFile.parentFile?.mkdirs()
        locOutputFile.writeText(locJson + System.lineSeparator(), Charsets.UTF_8)
        println(
            "Algites credential preflight wrote ${credentialCount.get()} required credential profile/type pair(s) " +
                "to ${locOutputFile.path}."
        )
    }
}

val locAlgitesRequiredCredentialsPlan = run {
    val locRequestedUsages = (
        algitesGradleOrEnvironmentValue("algites.credential.usages")
            ?: System.getenv("ALGITES_CREDENTIAL_USAGES")
            ?: "download"
        ).split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
    val locDownloadStabilities = (
        algitesGradleOrEnvironmentValue("algites.credential.download.stabilities")
            ?: System.getenv("ALGITES_CREDENTIAL_DOWNLOAD_STABILITIES")
            ?: "release,snapshot"
        ).split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
    val locUploadStabilities = (
        algitesGradleOrEnvironmentValue("algites.credential.upload.stabilities")
            ?: System.getenv("ALGITES_CREDENTIAL_UPLOAD_STABILITIES")
            ?: "release,snapshot"
        ).split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
    val locManageStabilities = (
        algitesGradleOrEnvironmentValue("algites.credential.manage.stabilities")
            ?: System.getenv("ALGITES_CREDENTIAL_MANAGE_STABILITIES")
            ?: "release,snapshot"
        ).split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()

    val locSupportedUsages = setOf("download", "upload", "manage")
    val locSupportedStabilities = setOf("release", "snapshot")
    if (!locSupportedUsages.containsAll(locRequestedUsages)) {
        throw GradleException("Unsupported credential usage. Supported values: download, upload, manage.")
    }
    if (!locSupportedStabilities.containsAll(locDownloadStabilities + locUploadStabilities + locManageStabilities)) {
        throw GradleException("Unsupported credential stability. Supported values: release, snapshot.")
    }

    val locDownloadVisibilities = when (algitesRepositoryVisibility) {
        "pub" -> setOf("public")
        "priv" -> setOf("public", "private")
        else -> throw GradleException("Unsupported Algites repository visibility '$algitesRepositoryVisibility'.")
    }
    val locUploadVisibilities = setOf(algitesPublicationRepositoryVisibility)
    val locManageVisibilities = setOf(algitesPublicationRepositoryVisibility)
    val locOperationTechnologyKinds = if (algitesRequestedTechnologyKinds.isNotEmpty()) {
        algitesRequestedTechnologyKinds
    } else {
        algitesResolvedArtifactDirectoriesByGradleProjectPath.values
            .flatMap { locMetadata -> AIcAlgitesStringList(locMetadata["technologyKinds"]) }
            .toSet()
    }
    val locRepositories = linkedMapOf<String, Map<String, String?>>()
    val locCredentials = linkedMapOf<String, Map<String, String>>()

    fun AIcCollect(aMetadata: Map<String, Any?>, aScope: String) {
        val locScopeTechnologyKinds = AIcAlgitesStringList(aMetadata["technologyKinds"]).toSet()
        val locRepositoryMap = aMetadata["repositories"] as? Map<*, *> ?: return
        val locProfiles = AIcAlgitesCredentialProfiles(aMetadata["credentialProfiles"])

        locRepositoryMap.keys.mapNotNull { it?.toString() }.sorted().forEach { locCell ->
            val locSegments = locCell.split('.')
            if (locSegments.size != 4) return@forEach
            val (locTechnology, locVisibility, locStability, locUsage) = locSegments
            if (locUsage !in locRequestedUsages) return@forEach
            if (locUsage == "download" && locVisibility !in locDownloadVisibilities) return@forEach
            if (locUsage == "upload" && locVisibility !in locUploadVisibilities) return@forEach
            if (locUsage == "manage" && locVisibility !in locManageVisibilities) return@forEach
            if (locUsage == "download" && locStability !in locDownloadStabilities) return@forEach
            if (locUsage == "upload" && locStability !in locUploadStabilities) return@forEach
            if (locUsage == "manage" && locStability !in locManageStabilities) return@forEach
            if (locUsage == "manage" && aScope == "repository") return@forEach
            if (locUsage == "manage" && aMetadata["deleteSnapshotWhenReleased"]?.toString()?.toBooleanStrictOrNull() == false) return@forEach
            if (locOperationTechnologyKinds.isNotEmpty() && locTechnology !in locOperationTechnologyKinds) return@forEach
            if (locScopeTechnologyKinds.isNotEmpty() && locTechnology !in locScopeTechnologyKinds) return@forEach

            AIcAlgitesRepositoryEndpoints(aMetadata["repositories"], locCell).forEach { locEndpoint ->
                val locProfileId = locEndpoint.credentialProfile
                val locProfile = if (locProfileId.isNullOrBlank()) null else locProfiles[locProfileId]
                    ?: throw GradleException(
                        "Repository endpoint '${locEndpoint.id}' references undefined credential profile '$locProfileId'."
                    )
                val locRepositoryKey = "$aScope|$locCell|${locEndpoint.id}"
                locRepositories[locRepositoryKey] = linkedMapOf(
                    "scope" to aScope,
                    "cell" to locCell,
                    "id" to locEndpoint.id,
                    "credentialProfile" to locProfileId,
                    "credentialType" to locProfile?.type
                )
                if (locProfile != null) {
                    val locCredentialKey = "${locProfile.id}|${locProfile.type}"
                    locCredentials[locCredentialKey] = linkedMapOf(
                        "profileId" to locProfile.id,
                        "type" to locProfile.type
                    )
                }
            }
        }
    }

    AIcCollect(algitesResolvedRepositoryMetadata, "repository")
    algitesResolvedArtifactDirectoriesByGradleProjectPath.toSortedMap().forEach { (locPath, locMetadata) ->
        AIcCollect(locMetadata, "artifact:$locPath")
    }

    val locPlan = linkedMapOf<String, Any>(
        "repositories" to locRepositories.values.toList(),
        "credentials" to locCredentials.values.toList()
    )
    JsonOutput.toJson(locPlan) to locCredentials.size
}

val algitesResolveRequiredCredentials = tasks.register<AIcResolveAlgitesRequiredCredentialsTask>("resolveAlgitesRequiredCredentials") {
    group = "algites"
    description = "Resolves enabled repository endpoints and the credential profiles required by the selected repository context."

    planJson.set(locAlgitesRequiredCredentialsPlan.first)
    credentialCount.set(locAlgitesRequiredCredentialsPlan.second)

    val locOutputPath = algitesGradleOrEnvironmentValue("algites.credential.output")
        ?: System.getenv("ALGITES_CREDENTIAL_OUTPUT")
    if (!locOutputPath.isNullOrBlank()) {
        outputFile.set(File(locOutputPath))
    }
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
    val locAlgitesProjectVersion = project.version.toString()

    val locEffectiveRepositories = locAlgitesArtifactDirectory?.get("repositories")
    val locEffectiveCredentialProfiles = AIcAlgitesCredentialProfiles(locAlgitesArtifactDirectory?.get("credentialProfiles"))

    val locAlgitesArtifactDirectoryPath = locAlgitesArtifactDirectory?.get("path")?.toString()?.takeIf { it.isNotBlank() } ?: "."
    val locAlgitesProductLicenses = locAlgitesLicensesForPathAndContentKind(locAlgitesArtifactDirectoryPath, "product")

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
        plugins.withId("java") {
            tasks.withType(Jar::class.java).configureEach {
                dependsOn(rootProject.tasks.named("verifyAlgitesLicensing"))
                locAlgitesProductLicenses.forEach { locLicense ->
                    val locLicenseId = locLicense["id"] ?: return@forEach
                    val locLicenseFile = locLicense["file"] ?: return@forEach
                    from(rootProject.file(locLicenseFile)) {
                        into("META-INF/LICENSES")
                        rename { "$locLicenseId.txt" }
                    }
                }
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
                    artifactId = locAlgitesCanonicalArtifactId
                    pom {
                        licenses {
                            locAlgitesProductLicenses.forEach { locLicense ->
                                license {
                                    name.set(locLicense["name"] ?: locLicense["id"] ?: "Unknown license")
                                    locLicense["url"]?.takeIf { it.isNotBlank() }?.let { locUrl -> url.set(locUrl) }
                                    distribution.set("repo")
                                }
                            }
                        }
                    }
                }

                repositories {
                    val locIsSnapshot = locAlgitesProjectVersion.endsWith("SNAPSHOT", ignoreCase = true)
                    val locStability = if (locIsSnapshot) "snapshot" else "release"
                    val locCell = "java.$algitesPublicationRepositoryVisibility.$locStability.upload"
                    val locEndpoints = AIcAlgitesRepositoryEndpoints(locEffectiveRepositories, locCell)

                    locEndpoints.forEach { locEndpoint ->
                        maven {
                            name = locEndpoint.id.replace('-', '_')
                            url = uri(locEndpoint.url)
                            val locProfileId = locEndpoint.credentialProfile
                            if (!locProfileId.isNullOrBlank() && !algitesCredentialPreflight) {
                                val locProfile = locEffectiveCredentialProfiles[locProfileId]
                                    ?: throw GradleException(
                                        "Repository endpoint '${locEndpoint.id}' references undefined credential profile '$locProfileId'."
                                    )
                                when (locProfile.type) {
                                    "basic" -> {
                                        val locCredential = AIcAlgitesRequireBasicCredential(locProfile)
                                        credentials {
                                            username = locCredential.first
                                            password = locCredential.second
                                        }
                                    }
                                    "bearer" -> {
                                        val locToken = AIcAlgitesCredentialValue(locProfile, "token")
                                            ?: throw GradleException(
                                                "Credential profile '${locProfile.id}' type 'bearer' is not available in ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS or the local Algites secure-store credential document."
                                            )
                                        credentials(HttpHeaderCredentials::class) {
                                            name = "Authorization"
                                            value = "Bearer $locToken"
                                        }
                                        authentication { create<HttpHeaderAuthentication>("header") }
                                    }
                                    "api-key" -> {
                                        val locApiKey = AIcAlgitesCredentialValue(locProfile, "apiKey")
                                            ?: throw GradleException(
                                                "Credential profile '${locProfile.id}' type 'api-key' is not available in ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS or the local Algites secure-store credential document."
                                            )
                                        val locHeaderName = locProfile.configuration["headerName"]?.takeIf { it.isNotBlank() }
                                            ?: throw GradleException(
                                                "Credential profile '${locProfile.id}' type 'api-key' requires configuration.headerName for Java/Maven publication."
                                            )
                                        credentials(HttpHeaderCredentials::class) {
                                            name = locHeaderName
                                            value = locApiKey
                                        }
                                        authentication { create<HttpHeaderAuthentication>("header") }
                                    }
                                    else -> throw GradleException(
                                        "Java/Maven upload endpoint '${locEndpoint.id}' uses credential type '${locProfile.type}', " +
                                            "which is not supported by the Java/Maven repository adapter."
                                    )
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

        val locPythonLicenseDirectory = project.layout.projectDirectory.dir("run/bld/algites-licensing/product")

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
            dependsOn(rootProject.tasks.named("verifyAlgitesLicensing"))

            inputs.file(project.file("algites-artifact.yml")).optional()
            inputs.file(project.file("algites-artifact.yaml")).optional()
            inputs.file(locPythonTemplateFile).optional()
            inputs.property("artifactCoordinateId", locAlgitesCanonicalArtifactId)
            inputs.property("distributionName", locPythonDistributionName)
            inputs.property("importNamespace", locPythonImportNamespace)
            inputs.property("version", project.provider { project.version.toString() })
            inputs.files(locAlgitesProductLicenses.mapNotNull { it["file"]?.let(rootProject::file) })
            outputs.file(locPythonProjectFile)
            outputs.dir(locPythonLicenseDirectory)

            doLast {
                val locLicenseDirectoryFile = locPythonLicenseDirectory.asFile
                if (locLicenseDirectoryFile.exists()) locLicenseDirectoryFile.deleteRecursively()
                if (locAlgitesProductLicenses.isNotEmpty()) {
                    locLicenseDirectoryFile.mkdirs()
                    locAlgitesProductLicenses.forEach { locLicense ->
                        val locId = locLicense["id"] ?: return@forEach
                        val locSource = locLicense["file"]?.let(rootProject::file) ?: return@forEach
                        locSource.copyTo(File(locLicenseDirectoryFile, "$locId.txt"), overwrite = true)
                    }
                }

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
                    if (locAlgitesProductLicenses.size == 1) {
                        appendLine("license = \"${locAlgitesProductLicenses.single()["id"]}\"")
                    }
                    if (locAlgitesProductLicenses.isNotEmpty()) {
                        val locLicensePaths = locAlgitesProductLicenses.joinToString(", ") { locLicense ->
                            "\"run/bld/algites-licensing/product/${locLicense["id"]}.txt\""
                        }
                        appendLine("license-files = [$locLicensePaths]")
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

        val locPythonProjectPath = project.path
        val locPythonProjectDirectory = project.projectDir
        val locPythonDistDirectory = rootProject.layout.projectDirectory.dir(
            "run/bld/python/${locPythonProjectPath.removePrefix(":").replace(':', '/')}/dist"
        )

        val locBuildPython = tasks.register<Exec>("buildPython") {
            group = "build"
            description = "Builds Python wheel and source distribution for this Algites artifact."
            dependsOn(locGeneratePythonProjectMetadata)
            workingDir(locPythonProjectDirectory)
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

        val locPublishPython = tasks.register("publishPython") {
            group = "publishing"
            description = "Publishes Python distributions for this Algites artifact."
            dependsOn(locBuildPython)

            doLast {
                val locIsSnapshot = locAlgitesProjectVersion.endsWith("SNAPSHOT", ignoreCase = true)
                val locStability = if (locIsSnapshot) "snapshot" else "release"
                val locCell = "python.$algitesPublicationRepositoryVisibility.$locStability.upload"
                val locEndpoints = AIcAlgitesRepositoryEndpoints(locEffectiveRepositories, locCell)
                if (locEndpoints.isEmpty()) {
                    throw GradleException(
                        "No enabled Python $locStability upload repository endpoint is configured for project '$locPythonProjectPath'. " +
                            "Configure repositories.python.$algitesPublicationRepositoryVisibility.$locStability.upload in Algites metadata or its inherited defaults."
                    )
                }
                val locDistributionFiles = locPythonDistDirectory.asFile.listFiles()
                    ?.filter { locFile -> locFile.isFile }
                    ?.sortedBy { locFile -> locFile.name }
                    ?: emptyList()

                if (locDistributionFiles.isEmpty()) {
                    throw GradleException("No Python distribution files were produced for project '$locPythonProjectPath'.")
                }

                locEndpoints.forEach { locEndpoint ->
                    val locCommand = mutableListOf(
                        algitesGradleOrEnvironmentValue("ALGITES_PYTHON_EXECUTABLE") ?: "python3",
                        "-m",
                        "twine",
                        "upload",
                        "--repository-url",
                        locEndpoint.url
                    )
                    val locProfileId = locEndpoint.credentialProfile
                    if (!locProfileId.isNullOrBlank()) {
                        val locProfile = locEffectiveCredentialProfiles[locProfileId]
                            ?: throw GradleException(
                                "Repository endpoint '${locEndpoint.id}' references undefined credential profile '$locProfileId'."
                            )
                        when (locProfile.type) {
                            "basic" -> {
                                val locCredential = AIcAlgitesRequireBasicCredential(locProfile)
                                locCommand.addAll(listOf("--username", locCredential.first, "--password", locCredential.second))
                            }
                            else -> throw GradleException(
                                "Python upload endpoint '${locEndpoint.id}' uses credential type '${locProfile.type}'. " +
                                    "The current Twine adapter supports 'basic'. Define a TechnologyKind-specific adapter before using another type."
                            )
                        }
                    }
                    locCommand.addAll(locDistributionFiles.map { locFile -> locFile.absolutePath })
                    val locExitValue = ProcessBuilder(locCommand)
                        .directory(locPythonProjectDirectory)
                        .inheritIO()
                        .start()
                        .waitFor()
                    if (locExitValue != 0) {
                        throw GradleException(
                            "Python upload command failed for endpoint '${locEndpoint.id}' with exit code $locExitValue."
                        )
                    }
                }
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


val algitesDeleteReleasedSnapshots = tasks.register("algitesDeleteReleasedSnapshots") {
    group = "publishing"
    description = "Deletes snapshot packages corresponding to a successfully published release according to effective Algites lifecycle policy."

    doLast {
        var locConfiguredTargets = 0
        var locDeletedPackages = 0
        algitesResolvedArtifactDirectoriesByGradleProjectPath.toSortedMap().forEach { (locProjectPath, locMetadata) ->
            val locDeclaredTechnologyKinds = AIcAlgitesStringList(locMetadata["technologyKinds"]).toSet()
            val locTechnologyKinds = if (algitesRequestedTechnologyKinds.isEmpty()) {
                locDeclaredTechnologyKinds
            } else {
                locDeclaredTechnologyKinds.intersect(algitesRequestedTechnologyKinds)
            }
            if (locTechnologyKinds.isEmpty()) return@forEach
            val locDeleteEnabled = locMetadata["deleteSnapshotWhenReleased"]?.toString()?.toBooleanStrictOrNull() ?: true
            if (!locDeleteEnabled) {
                logger.lifecycle("Skipping released-snapshot cleanup for '$locProjectPath': deleteSnapshotWhenReleased=false.")
                return@forEach
            }
            val locProject = rootProject.findProject(locProjectPath)
                ?: throw GradleException("Resolved Algites artifact project '$locProjectPath' is not present in the Gradle build.")
            val locReleaseVersion = algitesGradleOrEnvironmentValue("algites.cleanup.releaseVersion")
                ?: System.getenv("ALGITES_CLEANUP_RELEASE_VERSION")
                ?: locProject.version.toString()
            if (locReleaseVersion.endsWith("SNAPSHOT", ignoreCase = true)) {
                throw GradleException("Released-snapshot cleanup requires a release version, but '$locProjectPath' resolved '$locReleaseVersion'.")
            }
            val locRepositories = locMetadata["repositories"]
            val locProfiles = AIcAlgitesCredentialProfiles(locMetadata["credentialProfiles"])
            val locGroupId = locMetadata["groupId"]?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" }
            val locArtifactId = AIcAlgitesCanonicalArtifactId(locProjectPath)

            locTechnologyKinds.sorted().forEach { locTechnology ->
                val locCell = "$locTechnology.$algitesPublicationRepositoryVisibility.snapshot.manage"
                val locEndpoints = AIcAlgitesRepositoryEndpoints(locRepositories, locCell)
                if (locEndpoints.isEmpty()) {
                    logger.lifecycle("No enabled $locTechnology snapshot manage endpoint is configured for '$locProjectPath'; cleanup is skipped for this TechnologyKind.")
                    return@forEach
                }
                val locSnapshotVersion = AIcAlgitesSnapshotVersionForTechnology(locReleaseVersion, locTechnology)
                val locPackageName = when (locTechnology) {
                    "java" -> locArtifactId
                    "python" -> AIcAlgitesPythonDistributionName(locArtifactId)
                    else -> throw GradleException(
                        "Released-snapshot cleanup for TechnologyKind '$locTechnology' has no management package-coordinate adapter yet."
                    )
                }
                val locFormat = when (locTechnology) {
                    "java" -> "maven"
                    "python" -> "python"
                    else -> locTechnology
                }

                locEndpoints.forEach { locEndpoint ->
                    locConfiguredTargets++
                    val locDeleted = when (locEndpoint.usageProviderAdapter) {
                        "cloudsmith" -> AIcAlgitesDeleteCloudsmithSnapshot(
                            locEndpoint,
                            locProfiles,
                            locPackageName,
                            locSnapshotVersion,
                            locFormat
                        )
                        "repsy" -> AIcAlgitesDeleteRepsySnapshot(
                            locEndpoint,
                            locProfiles,
                            locPackageName,
                            locSnapshotVersion,
                            locFormat,
                            locGroupId
                        )
                        null -> throw GradleException("Manage endpoint '${locEndpoint.id}' has no usageProviderAdapter.")
                        else -> throw GradleException(
                            "Manage endpoint '${locEndpoint.id}' uses unsupported usageProviderAdapter '${locEndpoint.usageProviderAdapter}'."
                        )
                    }
                    locDeletedPackages += locDeleted
                    logger.lifecycle(
                        "Released-snapshot cleanup endpoint '${locEndpoint.id}': package=$locPackageName version=$locSnapshotVersion deleted=$locDeleted"
                    )
                }
            }
        }
        logger.lifecycle(
            "Algites released-snapshot cleanup completed: configuredTargets=$locConfiguredTargets deletedPackages=$locDeletedPackages"
        )
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

        algitesResolvedArtifactDirectoriesByGradleProjectPath.toSortedMap().forEach { locEntry ->
            val locMetadata = locEntry.value
            println(" - ${locEntry.key}: technologyKinds=${AIcAlgitesStringList(locMetadata["technologyKinds"])}")
            val locRepositories = locMetadata["repositories"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
            locRepositories.toSortedMap(compareBy { it.toString() }).forEach { (locCell, locEndpoints) ->
                println("     $locCell=$locEndpoints")
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
