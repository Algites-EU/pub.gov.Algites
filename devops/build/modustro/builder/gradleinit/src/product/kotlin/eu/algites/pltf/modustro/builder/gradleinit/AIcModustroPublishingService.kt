package eu.algites.pltf.modustro.builder.gradleinit

import eu.algites.pltf.modustro.builder.model.publication.*
import eu.algites.pltf.modustro.builder.publication.*
import eu.algites.pltf.modustro.builder.publication.adapters.*
import groovy.json.JsonSlurper
import java.net.URI
import java.util.Collections
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.*

/** Owns invocation-scoped publishing work without retaining Settings, Project or script instances. */
abstract class AIcModustroPublishingService : BuildService<AIcModustroPublishingService.AIiParameters>, AutoCloseable {
    interface AIiParameters : BuildServiceParameters {
        val credentialBaseDirectory: DirectoryProperty
    }

    @get:Inject abstract val providers: ProviderFactory
    private val locLogger = Logging.getLogger(AIcModustroPublishingService::class.java)
    private val locHandles = Collections.synchronizedList(mutableListOf<AIcPublishingScheduleHandle>())
    private var locScheduler: AIcPublishingScheduler? = null
    private val locCredentialValues by lazy { AIcCredentialValues(providers) }

    @Synchronized private fun AIcScheduler(): AIcPublishingScheduler = locScheduler ?: AIcPublishingScheduler(
        listOf(AIcGitBranchPublishingAdapter(), AIcHttpDirectoryPublishingAdapter(), AIcLocalCopyPublishingAdapter(),
            AIcMavenLocalRepositoryPublishingAdapter(), AIcMavenRepositoryPublishingAdapter(), AIcPythonRepositoryPublishingAdapter())
    ).also { locScheduler = it }

    /** Uses the Core scheduler's barriers, retries and failure policies for one payload. */
    fun AIcPublish(aPayload: AIcPublishingPayload, aPlan: Map<String, Any?>, aProfiles: Map<String, Any?>) {
        @Suppress("UNCHECKED_CAST")
        val locEndpoints = (aPlan["publishingEndpoints"] as? List<Map<String, Any?>>).orEmpty().map { locEndpoint ->
            AIcPublishingEndpoint(
                locEndpoint["id"]?.toString() ?: throw GradleException("Publishing endpoint is missing id."),
                locEndpoint["enabled"] as? Boolean ?: true,
                locEndpoint["publishingUrl"]?.toString()?.takeIf(String::isNotBlank)?.let(URI::create),
                locEndpoint["publishingAdapter"]?.toString(),
                locEndpoint["publishingCredentialProfile"]?.toString()?.takeIf(String::isNotBlank),
                (locEndpoint["publishingOrder"] as? Number)?.toInt() ?: 0,
                AInPublishingFailurePolicy.valueOf(locEndpoint["publishingFailurePolicy"]?.toString() ?: "FAIL_BUILD_ON_PUBLISHING_FAILURE"),
                (locEndpoint["publishingRetryCount"] as? Number)?.toInt() ?: 0,
                (locEndpoint["publishingRetryDelayMillis"] as? Number)?.toLong() ?: 1000L,
                (locEndpoint["publishingAttemptTimeoutMillis"] as? Number)?.toLong(),
                locEndpoint["showPublishingProgressIfPossible"] as? Boolean ?: true
            )
        }
        val locHandle = AIcScheduler().schedule(aPayload,
            AIcPublishingStabilityConfiguration(aPlan["publishingEnabled"] as? Boolean ?: false, locEndpoints),
            { locEndpoint -> AIcCredentials(locEndpoint, aProfiles) },
            { locEndpoint -> object : AIiPublishingProgressReporter {
                override fun started(aMessage: String) { locLogger.lifecycle("[${locEndpoint.id()}] $aMessage") }
                override fun progress(aCompleted: Long, aTotal: Long, aUnit: String, aMessage: String) {
                    locLogger.lifecycle("[${locEndpoint.id()}] $aCompleted/$aTotal $aUnit - $aMessage")
                }
                override fun indeterminate(aMessage: String) { started(aMessage) }
                override fun completed(aMessage: String) { started(aMessage) }
            } })
        locHandles.add(locHandle)
        try { locHandle.requiredCompletion().toCompletableFuture().get() }
        catch (locFailure: Exception) {
            throw GradleException("Required publishing failed for '${aPayload.artifactIdentity()}' ${aPayload.outputKind()}.", locFailure)
        }
    }

    private fun AIcCredentials(aEndpoint: AIcPublishingEndpoint, aProfiles: Map<String, Any?>): Map<String, String> {
        val locId = aEndpoint.publishingCredentialProfile() ?: return emptyMap()
        val locProfile = aProfiles[locId] as? Map<*, *>
            ?: throw GradleException("Publishing endpoint '${aEndpoint.id()}' references undefined credential profile '$locId'.")
        val locType = locProfile["type"]?.toString()?.trim()?.lowercase().orEmpty()
        fun AIcValue(aField: String): String = locCredentialValues.locAlgitesResolveCredentialValue(
            locId, locType, aField, parameters.credentialBaseDirectory.get().asFile
        ) ?: throw GradleException("Credential profile '$locId' does not resolve '$aField'.")
        return when (locType) {
            "basic" -> mapOf("username" to AIcValue("Username"), "password" to AIcValue("Password"))
            "bearer" -> mapOf("bearerToken" to AIcValue("Token"))
            "api_key" -> mapOf("apiKey" to AIcValue("ApiKey"), "apiKeyHeader" to
                ((locProfile["configuration"] as? Map<*, *>)?.get("headerName")?.toString()
                    ?: throw GradleException("Credential profile '$locId' requires configuration.headerName.")))
            else -> throw GradleException("Unsupported publishing credential profile type '$locType'.")
        }
    }

    /** Drains all background attempts, including ignored failures, before invocation teardown. */
    fun AIcAwait() {
        val locCopy = synchronized(locHandles) { locHandles.toList() }
        locCopy.forEach { locHandle -> locHandle.endpointResults().forEach { (locId, locStage) ->
            val locResult = try { locStage.toCompletableFuture().get() }
                catch (locFailure: Exception) { throw GradleException("Publishing endpoint '$locId' did not complete cleanly.", locFailure) }
            if (!locResult.success() && locResult.ignoredFailure()) {
                locLogger.warn("Ignored Modustro publishing failure: $locId: ${locResult.failure()?.message ?: "unknown failure"}")
            }
        } }
        locHandles.removeAll(locCopy.toSet())
    }

    override fun close() {
        try { AIcAwait() } finally { locScheduler?.close() }
    }
}

/** Serializable task inputs describe a payload; credentials and scheduler instances exist only at execution. */
abstract class AIcModustroPublishFilesTask : DefaultTask() {
    @get:Input abstract val publishingPlanJson: Property<String>
    @get:Input abstract val credentialProfilesJson: Property<String>
    @get:Input abstract val outputKind: Property<String>
    @get:Input abstract val stability: Property<String>
    @get:Input abstract val artifactIdentity: Property<String>
    @get:Input abstract val publicationVersion: Property<String>
    @get:Input abstract val coordinates: MapProperty<String, String>
    @get:Input abstract val publishedFileNames: MapProperty<String, String>
    @get:Input abstract val requiresSnapshotInstance: Property<Boolean>
    @get:Input abstract val snapshotInstanceId: Property<String>
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val payloadFiles: ConfigurableFileCollection
    @get:Internal abstract val payloadRoot: DirectoryProperty
    @get:Internal abstract val publishingService: Property<AIcModustroPublishingService>

    init {
        credentialProfilesJson.convention("{}")
        coordinates.convention(emptyMap())
        publishedFileNames.convention(emptyMap())
        requiresSnapshotInstance.convention(false)
        snapshotInstanceId.convention("")
    }

    @TaskAction fun AIcPublish() {
        @Suppress("UNCHECKED_CAST")
        val locPlan = JsonSlurper().parseText(publishingPlanJson.get()) as Map<String, Any?>
        if (locPlan["publishingEnabled"] != true) return
        val locEndpoints = (locPlan["publishingEndpoints"] as? List<*>).orEmpty()
        if (locEndpoints.none { (it as? Map<*, *>)?.get("enabled") != false }) return
        if (requiresSnapshotInstance.get() && stability.get() == "snapshot" && snapshotInstanceId.get().isBlank()) {
            throw GradleException("Publishing a Python snapshot requires an immutable snapshot instance id. " +
                "Set -Palgites.snapshot.instanceId=<UTC timestamp> or ALGITES_SNAPSHOT_INSTANCE_ID.")
        }
        val locRoot = payloadRoot.orNull?.asFile
        val locFiles = payloadFiles.files.filter { it.isFile }.map { locFile ->
            val locName = locRoot?.let { locFile.relativeTo(it).invariantSeparatorsPath } ?: locFile.name
            AIcPublishingPayloadFile(locFile.toPath(), publishedFileNames.get()[locName] ?: locName)
        }.sortedBy { it.logicalName() }
        if (locFiles.isEmpty()) throw GradleException("No publishing payload files for '${artifactIdentity.get()}' ${outputKind.get()}.")
        @Suppress("UNCHECKED_CAST")
        val locProfiles = JsonSlurper().parseText(credentialProfilesJson.get()) as Map<String, Any?>
        publishingService.get().AIcPublish(AIcPublishingPayload(AInPublishingOutputKind.valueOf(outputKind.get()),
            AInPublishingStability.valueOf(stability.get().uppercase()), artifactIdentity.get(), publicationVersion.get(),
            locFiles, coordinates.get()), locPlan, locProfiles)
    }
}

/** Finalizes shared background publishing without capturing a build script. */
abstract class AIcModustroAwaitPublishingTask : DefaultTask() {
    @get:Internal abstract val publishingService: Property<AIcModustroPublishingService>
    @TaskAction fun AIcAwait() { publishingService.get().AIcAwait() }
}
