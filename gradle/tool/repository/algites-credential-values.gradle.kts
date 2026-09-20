/*
 * Algites universal credential value resolver.
 *
 * ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS contains the provider-independent credential document.
 * Each field is represented by { "source": <enum>, "value": <string> }.
 * The source determines how the value string is interpreted.
 */

import groovy.json.JsonSlurper
import java.io.File
import org.gradle.api.GradleException

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

val locAlgitesReadCredentialCliOutput = fun(vararg aArguments: String): String? {
    val locExecutable = System.getenv("ALGITES_CREDENTIAL_CLI")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: "algites-credentials"
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
    ?: locAlgitesReadCredentialCliOutput("bootstrap-document")

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
    val locType = locProfile[aCredentialType] as? Map<*, *> ?: return null
    val locField = locType[aField] as? Map<*, *> ?: return null
    val locSource = locField["source"]?.toString()
        ?: throw GradleException(
            "Credential '$aProfileId/$aCredentialType/$aField' is missing required property 'source'."
        )
    val locReference = locField["value"]?.toString()
        ?: throw GradleException(
            "Credential '$aProfileId/$aCredentialType/$aField' is missing required property 'value'."
        )

    return when (locSource) {
        "DIRECT_VALUE" -> locReference
        "FILE_CONTENT" -> {
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
        "SECRET_CONTENT" -> {
            val locContextValue = locAlgitesCredentialSecretContext[locReference]
            if (locContextValue != null) {
                locContextValue.toString()
            } else {
                locAlgitesReadCredentialCliOutput("bootstrap-secret", locReference)
                    ?: throw GradleException(
                        "Credential '$aProfileId/$aCredentialType/$aField' references unavailable secret '$locReference'."
                    )
            }
        }
        "ENVIRONMENT_VARIABLE_CONTENT" -> System.getenv(locReference)
            ?: throw GradleException(
                "Credential '$aProfileId/$aCredentialType/$aField' references unavailable environment variable '$locReference'."
            )
        else -> throw GradleException(
            "Credential '$aProfileId/$aCredentialType/$aField' uses unsupported source '$locSource'. " +
                "Supported sources: DIRECT_VALUE, FILE_CONTENT, SECRET_CONTENT, ENVIRONMENT_VARIABLE_CONTENT."
        )
    }
}

extra["algitesResolveCredentialValue"] = locAlgitesResolveCredentialValue
extra["algitesCredentialDocumentAvailable"] = locAlgitesCredentialDocument.isNotEmpty()
