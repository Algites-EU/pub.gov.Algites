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

/** Owns invocation-scoped publication work without retaining Settings, Project or script instances. */
abstract class AIcModustroPublicationService : BuildService<AIcModustroPublicationService.AIiParameters>, AutoCloseable, org.gradle.tooling.events.OperationCompletionListener {
    interface AIiParameters : BuildServiceParameters {
        val credentialBaseDirectory: DirectoryProperty
    }

    @get:Inject abstract val providers: ProviderFactory
    private val locLogger = Logging.getLogger(AIcModustroPublicationService::class.java)
    private val locHandles = Collections.synchronizedList(mutableListOf<AIcPublicationScheduleHandle>())
    private var locScheduler: AIcPublicationScheduler? = null
    private val locCredentialValues by lazy { AIcCredentialValues(providers) }

    @Synchronized private fun AIcScheduler(): AIcPublicationScheduler = locScheduler ?: AIcPublicationScheduler(
        listOf(AIcGitBranchPublicationAdapter(), AIcHttpDirectoryPublicationAdapter(), AIcLocalCopyPublicationAdapter(),
            AIcMavenLocalRepositoryPublicationAdapter(), AIcMavenRepositoryPublicationAdapter(), AIcPythonRepositoryPublicationAdapter(),
            AIcS3ObjectStoragePublicationAdapter()),
        listOf(AIcBuildRecordPostPublicationActionAdapter())
    ).also { locScheduler = it }

    /** Uses the Core scheduler's barriers, retries and failure policies for one payload. */
    fun AIcPublish(aPayload: AIcPublicationPayload, aPlan: Map<String, Any?>, aProfiles: Map<String, Any?>) {
        val locContext = AIcContext()
        val locDirectory = parameters.credentialBaseDirectory.get().asFile.toPath().resolve("build/run/publication-records")
        val locJobs = AIcPublicationPlanner().plan(aPayload, aPlan, locContext, locDirectory)
        val locHandle = AIcScheduler().schedule(locJobs,
            { locEndpoint -> AIcCredentials(locEndpoint, aProfiles) },
            { locEndpoint -> object : AIiPublicationProgressReporter {
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

    private var locContext: Map<String, Any?>? = null
    /** Runs at execution, including when Gradle reuses its configuration cache. */
    @Synchronized private fun AIcContext(): Map<String, Any?> {
        locContext?.let { return it }
        var locRoot = parameters.credentialBaseDirectory.get().asFile
        while (!java.io.File(locRoot, "modustro-source-repository.yml").isFile && locRoot.parentFile != null) locRoot = locRoot.parentFile
        val locDescriptor = java.io.File(locRoot, "modustro-source-repository.yml")
        val locId = if (locDescriptor.isFile) AIcReadSimpleYamlScalars(locDescriptor)["SourceRepository.Id"] ?: locRoot.name else locRoot.name
        fun git(vararg args: String): String? = try {
            val p = ProcessBuilder(listOf("git", "-C", locRoot.path) + args).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            val t = p.inputStream.bufferedReader().use { it.readText() }.trim()
            if (p.waitFor() == 0) t else null
        } catch (failure: java.io.IOException) { null }
        val locSource = linkedMapOf<String, Any>("RepositoryId" to locId, "Revision" to (git("rev-parse", "HEAD") ?: "unknown"))
        git("status", "--porcelain")?.let { locSource["Dirty"] = it.isNotEmpty() }
        val locSources = mutableListOf<Map<String, Any>>(locSource)
        listOf("pub.gov.Algites" to "MODUSTRO_PUBLIC_GOVERNANCE_REVISION", "priv.gov.Algites" to "MODUSTRO_PRIVATE_GOVERNANCE_REVISION").forEach { (id, env) ->
            providers.environmentVariable(env).orNull?.takeIf { it.isNotBlank() }?.let { locSources.add(mapOf("RepositoryId" to id, "Revision" to it)) }
        }
        val locTools = mutableListOf<Map<String, Any>>()
        val locClasses = mutableListOf(AIcModustroSettingsPlugin::class.java, AIcPublicationPlanner::class.java)
        try { locClasses.add(Class.forName("eu.algites.tool.codegen.defs.AIcDefaultDefsCodegenService")) } catch (failure: ClassNotFoundException) { }
        locClasses.forEach { type ->
            type.protectionDomain?.codeSource?.location?.takeIf { it.protocol == "file" }?.let { url ->
                val file = java.io.File(url.toURI())
                if (file.isFile) {
                    val identity = linkedMapOf<String, Any>("Role" to (if (type.name.contains("codegen")) "source-generator" else if (type == AIcModustroSettingsPlugin::class.java) "gradle-init" else "builder-core"), "Coordinate" to "unknown", "Sha256" to AIcBuildRecordPostPublicationActionAdapter.hash(file.toPath()), "ProducerRevision" to "unknown")
                    java.util.zip.ZipFile(file).use { zip -> zip.getEntry("META-INF/modustro/modustro-artifact-manifest.yml")?.let { entry ->
                        val manifest = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                        fun field(name: String) = Regex("(?m)^  " + name + ": *([^\n]+)").find(manifest)?.groupValues?.get(1)?.trim()?.trim('"', '\'')
                        val group = field("GroupId"); val artifact = field("ArtifactCoordinateId"); val version = field("Version")
                        if (group != null && artifact != null && version != null) identity["Coordinate"] = "$group:$artifact:$version"
                    } }
                    locTools.add(identity)
                }
            }
        }
        val locInvocation = linkedMapOf<String, Any>("Id" to (providers.environmentVariable("MODUSTRO_BUILD_INVOCATION_ID").orNull ?: java.util.UUID.randomUUID().toString()), "StartedAt" to java.time.Instant.now().toString())
        val server = providers.environmentVariable("GITHUB_SERVER_URL").orNull
        val repository = providers.environmentVariable("GITHUB_REPOSITORY").orNull
        val run = providers.environmentVariable("GITHUB_RUN_ID").orNull
        if (server != null && repository != null && run != null) locInvocation["LogUrl"] = "$server/$repository/actions/runs/$run"
        val context = linkedMapOf<String, Any?>("RepositoryId" to locId, "Invocation" to locInvocation, "Sources" to locSources, "Tools" to locTools, "OutputOrigin" to mapOf("Kind" to "unknown"))
        locLogger.lifecycle("Modustro invocation: {}", AIcBuildRecordPostPublicationActionAdapter.json(context))
        locContext = context
        return context
    }
    override fun onFinish(event: org.gradle.tooling.events.FinishEvent) { AIcContext() }

    private fun AIcCredentials(aEndpoint: AIcPublicationEndpoint, aProfiles: Map<String, Any?>): Map<String, String> {
        val locId = aEndpoint.publicationCredentialProfile() ?: return emptyMap()
        val locProfile = aProfiles[locId] as? Map<*, *>
            ?: throw GradleException("Publication endpoint '${aEndpoint.id()}' references undefined credential profile '$locId'.")
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
            else -> throw GradleException("Unsupported publication credential profile type '$locType'.")
        }
    }

    /** Drains all background publication and post-action attempts before invocation teardown. */
    fun AIcAwait() {
        val locCopy = synchronized(locHandles) { locHandles.toList() }
        locCopy.forEach { locHandle ->
            locHandle.publicationResults().forEach { (locId, locStage) ->
                val locResult = try { locStage.toCompletableFuture().get() }
                    catch (locFailure: Exception) { throw GradleException("Publication '$locId' did not complete cleanly.", locFailure) }
                if (!locResult.success() && locResult.ignoredFailure()) {
                    locLogger.warn("Ignored Modustro publication failure: $locId: ${locResult.failure()?.message ?: "unknown failure"}")
                }
            }
            locHandle.postPublicationActionResults().forEach { (locId, locStage) ->
                val locResult = try { locStage.toCompletableFuture().get() }
                    catch (locFailure: Exception) { throw GradleException("Post-publication action '$locId' did not complete cleanly.", locFailure) }
                if (!locResult.success() && locResult.ignoredFailure()) {
                    locLogger.warn("Ignored Modustro post-publication action failure: $locId: ${locResult.failure()?.message ?: "unknown failure"}")
                }
            }
        }
        locHandles.removeAll(locCopy.toSet())
    }

    override fun close() {
        try { AIcAwait() } finally { locScheduler?.close() }
    }
}

/** Serializable task inputs describe a payload; credentials and scheduler instances exist only at execution. */
abstract class AIcModustroPublishFilesTask : DefaultTask() {
    @get:Input abstract val publicationPlanJson: Property<String>
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
    @get:Internal abstract val publicationService: Property<AIcModustroPublicationService>

    init {
        credentialProfilesJson.convention("{}")
        coordinates.convention(emptyMap())
        publishedFileNames.convention(emptyMap())
        requiresSnapshotInstance.convention(false)
        snapshotInstanceId.convention("")
    }

    @TaskAction fun AIcPublish() {
        @Suppress("UNCHECKED_CAST")
        val locPlan = JsonSlurper().parseText(publicationPlanJson.get()) as Map<String, Any?>
        if (locPlan["publicationEnabled"] != true) return
        val locEndpoints = (locPlan["publicationEndpoints"] as? List<*>).orEmpty()
        if (locEndpoints.none { (it as? Map<*, *>)?.get("enabled") != false }) return
        if (requiresSnapshotInstance.get() && stability.get() == "snapshot" && snapshotInstanceId.get().isBlank()) {
            throw GradleException("Publishing a Python snapshot requires an immutable snapshot instance id. " +
                "Set -Palgites.snapshot.instanceId=<UTC timestamp> or ALGITES_SNAPSHOT_INSTANCE_ID.")
        }
        val locRoot = payloadRoot.orNull?.asFile
        val locFiles = payloadFiles.files.filter { it.isFile }.map { locFile ->
            val locName = locRoot?.let { locFile.relativeTo(it).invariantSeparatorsPath } ?: locFile.name
            AIcPublicationPayloadFile(locFile.toPath(), publishedFileNames.get()[locName] ?: locName)
        }.sortedBy { it.logicalName() }
        if (locFiles.isEmpty()) throw GradleException("No publication payload files for '${artifactIdentity.get()}' ${outputKind.get()}.")
        @Suppress("UNCHECKED_CAST")
        val locProfiles = JsonSlurper().parseText(credentialProfilesJson.get()) as Map<String, Any?>
        publicationService.get().AIcPublish(AIcPublicationPayload(AInPublicationOutputKind.valueOf(outputKind.get()),
            AInPublicationStability.valueOf(stability.get().uppercase()), artifactIdentity.get(), publicationVersion.get(),
            locFiles, coordinates.get()), locPlan, locProfiles)
    }
}

/** Finalizes shared background publications without capturing a build script. */
abstract class AIcModustroAwaitPublicationsTask : DefaultTask() {
    @get:Internal abstract val publicationService: Property<AIcModustroPublicationService>
    @TaskAction fun AIcAwait() { publicationService.get().AIcAwait() }
}
