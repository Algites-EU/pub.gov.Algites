/*
 * Algites universal credential value resolver.
 *
 * ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS contains the provider-independent credential document.
 * Each field is represented by { "Source": <enum>, "Value": <string> }.
 * The source determines how the value string is interpreted.
 */

import groovy.json.JsonSlurper
import java.io.File
import org.gradle.api.GradleException

val locAlgitesCredentialPreflight = System.getenv("_TMP_ALGITES_CREDENTIAL_PREFLIGHT")
    ?.equals("true", ignoreCase = true) == true
val locAlgitesCredentialCi = System.getenv("CI")
    ?.equals("true", ignoreCase = true) == true

@Suppress("UNCHECKED_CAST")
fun AIcAlgitesParseCredentialJsonObject(aName: String, aRaw: String?): Map<String, Any?> {
    if (aRaw.isNullOrBlank()) return emptyMap()
    val locParsed = try {
        JsonSlurper().parseText(aRaw)
    } catch (aException: Exception) {
        throw GradleException("$aName does not contain valid JSON.", aException)
    }
    val locMap = locParsed as? Map<*, *>
        ?: throw GradleException("$aName must contain a JSON object.")
    return locMap.entries.associate { locEntry -> locEntry.key.toString() to locEntry.value }
}

fun AIcAlgitesFindExecutable(aExecutable: String): String? {
    val locExecutable = aExecutable.trim()
    if (locExecutable.isBlank()) return null

    val locDirectFile = File(locExecutable)
    if (locDirectFile.isAbsolute || locExecutable.contains('/') || locExecutable.contains('\\')) {
        return locDirectFile.takeIf { it.isFile }?.absolutePath
    }

    val locPath = System.getenv("PATH")?.takeIf { it.isNotBlank() } ?: return null
    val locIsWindows = System.getProperty("os.name").lowercase().contains("win")
    val locExtensions = if (locIsWindows) {
        val locPathExt = System.getenv("PATHEXT")
            ?.split(';')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        listOf("") + locPathExt
    } else {
        listOf("")
    }

    locPath.split(File.pathSeparatorChar).forEach { locDirectory ->
        if (locDirectory.isBlank()) return@forEach
        locExtensions.forEach { locExtension ->
            val locCandidate = File(locDirectory, locExecutable + locExtension)
            if (locCandidate.isFile) return locCandidate.absolutePath
        }
    }
    return null
}

val locAlgitesReadCredentialCliOutput = fun(aArguments: List<String>): String? {
    val locConfiguredExecutable = System.getenv("ALGITES_CREDENTIAL_CLI")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: "algites-credentials"
    val locExecutable = AIcAlgitesFindExecutable(locConfiguredExecutable) ?: return null
    val locCommand = mutableListOf(locExecutable)
    locCommand.addAll(aArguments.toList())
    val locOutput = try {
        providers.exec {
            commandLine(locCommand)
            isIgnoreExitValue = true
        }
    } catch (_: Exception) {
        return null
    }
    val locResult = try {
        locOutput.result.get()
    } catch (_: Exception) {
        return null
    }
    if (locResult.exitValue != 0) return null
    return try {
        locOutput.standardOutput.asText.get()
    } catch (_: Exception) {
        null
    }
}

val locAlgitesCredentialDocumentRaw = System.getenv("ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS")
    ?.takeIf { it.isNotBlank() }
    ?: if (locAlgitesCredentialPreflight || locAlgitesCredentialCi) {
        null
    } else {
        locAlgitesReadCredentialCliOutput(listOf("bootstrap-document"))
    }

val locAlgitesCredentialDocument = AIcAlgitesParseCredentialJsonObject(
    "ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS",
    locAlgitesCredentialDocumentRaw
)

val locAlgitesCredentialSecretContext = AIcAlgitesParseCredentialJsonObject(
    "_TMP_ALGITES_CREDENTIAL_SECRETS_JSON",
    System.getenv("_TMP_ALGITES_CREDENTIAL_SECRETS_JSON")
)

val locAlgitesResolveCredentialValue = fun(
    aProfileId: String,
    aCredentialType: String,
    aField: String,
    aBaseDirectory: File
): String? {
    val locProfile = locAlgitesCredentialDocument[aProfileId] as? Map<*, *> ?: return null
    val locTypeProperty = when (aCredentialType) {
        "basic" -> "Basic"
        "bearer" -> "Bearer"
        "api_key" -> "ApiKey"
        "certificate" -> "Certificate"
        else -> return null
    }
    val locType = locProfile[locTypeProperty] as? Map<*, *> ?: return null
    val locField = locType[aField] as? Map<*, *> ?: return null
    val locSource = locField["Source"]?.toString()
        ?: throw GradleException(
            "Credential '$aProfileId/$aCredentialType/$aField' is missing required property 'Source'."
        )
    val locReference = locField["Value"]?.toString()
        ?: throw GradleException(
            "Credential '$aProfileId/$aCredentialType/$aField' is missing required property 'Value'."
        )

    return when (locSource) {
        "direct_value" -> locReference
        "file_content" -> {
            val locFile = File(locReference).let { locCandidate ->
                if (locCandidate.isAbsolute) locCandidate else File(aBaseDirectory, locReference)
            }
            if (!locFile.isFile) {
                throw GradleException(
                    "Credential '$aProfileId/$aCredentialType/$aField' references missing file '${locFile.path}'."
                )
            }
            locFile.readText(Charsets.UTF_8)
        }
        "secret_content" -> {
            val locContextValue = locAlgitesCredentialSecretContext[locReference]
            if (locContextValue != null) {
                locContextValue.toString()
            } else if (locAlgitesCredentialPreflight) {
                null
            } else {
                locAlgitesReadCredentialCliOutput(listOf("bootstrap-secret", locReference))
                    ?: throw GradleException(
                        "Credential '$aProfileId/$aCredentialType/$aField' references unavailable secret '$locReference'."
                    )
            }
        }
        "environment_variable_content" -> System.getenv(locReference)
            ?: throw GradleException(
                "Credential '$aProfileId/$aCredentialType/$aField' references unavailable environment variable '$locReference'."
            )
        else -> throw GradleException(
            "Credential '$aProfileId/$aCredentialType/$aField' uses unsupported source '$locSource'. " +
                "Supported sources: direct_value, file_content, secret_content, environment_variable_content."
        )
    }
}

extra["algitesResolveCredentialValue"] = locAlgitesResolveCredentialValue
extra["algitesCredentialDocumentAvailable"] = locAlgitesCredentialDocument.isNotEmpty()
