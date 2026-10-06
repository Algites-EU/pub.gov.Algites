package eu.algites.pltf.modustro.builder.gradleinit

import eu.algites.pltf.modustro.builder.catalog.AIcBuiltinAdapterCatalog
import eu.algites.pltf.modustro.builder.model.publication.*
import eu.algites.pltf.modustro.builder.publication.*
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
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

    private data class AIcdScheduledOutput(
        val outputId: String,
        val handle: AIcPublicationScheduleHandle?,
        val pendingResult: AIcOutputPublicationExecutionResult,
        val plan: Map<String, Any?>,
        val profiles: Map<String, Any?>
    )

    private data class AIcdCompletedOutput(
        val scheduled: AIcdScheduledOutput,
        val result: AIcOutputPublicationExecutionResult
    )

    @get:Inject abstract val providers: ProviderFactory
    private val locLogger = Logging.getLogger(AIcModustroPublicationService::class.java)
    private val locScheduledOutputs = Collections.synchronizedList(mutableListOf<AIcdScheduledOutput>())
    private val locExpectedOutputs = linkedMapOf<String, AIcdScheduledOutput>()
    private val locFinalizedScopes = mutableSetOf<String>()
    private val locRepositoryRefreshRequests = mutableSetOf<String>()
    private val locCrossDomainVersionScopes = linkedMapOf<String, String>()
    private val locArtifactMetadata = mutableListOf<Map<String, Any?>>()
    private val locDeclaredOutputSelections = mutableSetOf<String>()
    private val locEffectiveOutputSelections = linkedMapOf<String, Boolean>()
    private val locDomainScopeResults = linkedMapOf<String, AIcVersionScopePublicationExecutionResult>()
    private val locScopeActionValues = linkedMapOf<String, Any?>()
    private val locOutputProfiles = linkedMapOf<String, Map<String, Any?>>()
    private val locAttemptedScopeKeys = mutableSetOf<String>()
    private var locDomainMetadata: Map<String, Map<String, Any?>> = emptyMap()
    private var locDomainId = "."
    private var locRepositoryDirectory: Path? = null
    private var locPlanFingerprint = ""
    private var locContributionWritten = false
    private var locDomainsFinalized = false
    private var locDomainExportRequested = false
    private var locScheduler: AIcPublicationScheduler? = null
    private val locCredentialValues by lazy { AIcCredentialValues(providers) }

    @Synchronized private fun AIcScheduler(): AIcPublicationScheduler = locScheduler ?: AIcPublicationScheduler(
        AIcBuiltinAdapterCatalog.create()
    ).also { locScheduler = it }

    private fun AIcProgressReporterFactory(): AIiPublicationProgressReporterFactory = AIiPublicationProgressReporterFactory { locEndpoint ->
        object : AIiPublicationProgressReporter {
            override fun started(aMessage: String) { locLogger.lifecycle("[${locEndpoint.id()}] $aMessage") }
            override fun progress(aCompleted: Long, aTotal: Long, aUnit: String, aMessage: String) {
                locLogger.lifecycle("[${locEndpoint.id()}] $aCompleted/$aTotal $aUnit - $aMessage")
            }
            override fun indeterminate(aMessage: String) { started(aMessage) }
            override fun completed(aMessage: String) { started(aMessage) }
        }
    }

    /** Uses Core publication scheduling for one output and retains its complete execution tree for higher barriers. */
    fun AIcPublish(aPayload: AIcPublicationPayload, aPlan: Map<String, Any?>, aProfiles: Map<String, Any?>, aOutputId: String) {
        AIcBeginOutput(aPlan, aPayload.outputKind().name, AIcStability(aPayload.stability()), aPayload.version(), aPayload.coordinates())
        val locContext = AIcContext()
        val locDirectory = parameters.credentialBaseDirectory.get().asFile.toPath().resolve("build/run/publication-records")
        val locPlanner = AIcPublicationPlanner()
        val locJobs = locPlanner.plan(aPayload, aPlan as Map<String, Object>, locContext as Map<String, Object>, locDirectory)
        @Suppress("UNCHECKED_CAST")
        val locOutputFinalization = AIcPublicationPlanner.outputPublicationFinalizationActions(aPlan as Map<String, Object>)
        @Suppress("UNCHECKED_CAST")
        val locSnapshotEndpoints = AIcPublicationPlanner.snapshotPublicationEndpoints(aPlan as Map<String, Object>)
        val locHandle = AIcScheduler().schedule(
            aPayload,
            locJobs,
            locOutputFinalization,
            locSnapshotEndpoints,
            { locEndpoint -> AIcCredentials(locEndpoint, aProfiles) },
            AIcProgressReporterFactory()
        )
        val locPending = AIcOutputPublicationExecutionResult(
            aPayload.artifactIdentity(), aPayload.coordinates()["technologyKind"] ?: "unknown", aPayload.outputKind(),
            aPayload.stability(), aPayload.coordinates()["logicalVersion"] ?: aPayload.coordinates()["version"] ?: aPayload.version(),
            AIcPublicationPlanner.publicationEndpointRegistry(aPlan as Map<String, Object>), locSnapshotEndpoints,
            emptyList(), emptyList(), false)
        val locScheduled = AIcdScheduledOutput(aOutputId, locHandle, locPending, aPlan.toMap(), aProfiles.toMap())
        synchronized(this) {
            locExpectedOutputs[aOutputId] = locScheduled
            locScheduledOutputs.add(locScheduled)
        }
        try {
            locHandle.requiredCompletion().toCompletableFuture().get()
        } catch (locFailure: Exception) {
            throw GradleException("Required publishing failed for '${aPayload.artifactIdentity()}' ${aPayload.outputKind()}.", locFailure)
        }
    }

    /** Installs the whole invocation plan before any publication task can start. */
    @Synchronized fun AIcPrepare(aInputs: Map<String, String>, aCrossDomainScopes: Map<String, String>, aArtifactMetadata: Map<String, String>) {
        AIcPrepare(aInputs, aCrossDomainScopes, aArtifactMetadata, "{}", ".", parameters.credentialBaseDirectory.get().asFile.absolutePath)
    }

    /** Domain metadata and paths are ordinary task inputs, never live Project or Settings objects. */
    @Synchronized fun AIcPrepare(aInputs: Map<String, String>, aCrossDomainScopes: Map<String, String>, aArtifactMetadata: Map<String, String>,
        aDomainMetadataJson: String, aDomainId: String, aRepositoryDirectory: String) {
        @Suppress("UNCHECKED_CAST")
        val locParsedDomains = JsonSlurper().parseText(aDomainMetadataJson) as Map<String, Map<String, Any?>>
        locDomainMetadata = locParsedDomains
        locDomainId = aDomainId
        locRepositoryDirectory = Path.of(aRepositoryDirectory).toAbsolutePath().normalize()
        locPlanFingerprint = AIcPublicationDomainStore.fingerprint(JsonOutput.toJson(AIcCanonicalData(locDomainMetadata)))
        locCrossDomainVersionScopes.putAll(aCrossDomainScopes)
        @Suppress("UNCHECKED_CAST")
        aArtifactMetadata.values.forEach { locArtifactMetadata.add(JsonSlurper().parseText(it) as Map<String, Any?>) }
        aInputs.forEach { (locId, locJson) ->
            @Suppress("UNCHECKED_CAST")
            val locInput = JsonSlurper().parseText(locJson) as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val locPlan = JsonSlurper().parseText(locInput["plan"].toString()) as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val locInputCoordinates = locInput["coordinates"] as? Map<String, String> ?: emptyMap()
            val locInputKind = AInPublicationOutputKind.valueOf(locInput["outputKind"].toString())
            val locSelection = listOf(locPlan["artifactPath"]?.toString() ?: ".",
                locInputCoordinates["technologyKind"] ?: "unknown", locInputKind.descriptorName(),
                locInput["stability"].toString().lowercase()).joinToString("|")
            locDeclaredOutputSelections.add(locSelection)
            val locInputEndpoints = (locPlan["publicationEndpoints"] as? List<*>).orEmpty()
            locEffectiveOutputSelections[locSelection] = locPlan["publicationEnabled"] == true &&
                locInputEndpoints.any { (it as? Map<*, *>)?.get("enabled") != false }
            if (locPlan["publicationEnabled"] != true) return@forEach
            val locEndpoints = (locPlan["publicationEndpoints"] as? List<*>).orEmpty()
            if (locEndpoints.none { (it as? Map<*, *>)?.get("enabled") != false }) return@forEach
            val locKind = AInPublicationOutputKind.valueOf(locInput["outputKind"].toString())
            if (locKind == AInPublicationOutputKind.MODUSTRO_DOCS_SITE || locKind == AInPublicationOutputKind.SCHEMA_SITE) return@forEach
            @Suppress("UNCHECKED_CAST")
            val locCoordinates = locInput["coordinates"] as? Map<String, String> ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val locProfiles = JsonSlurper().parseText(locInput["profiles"].toString()) as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val locPending = AIcOutputPublicationExecutionResult(
                locInput["artifactIdentity"].toString(), locCoordinates["technologyKind"] ?: "unknown", locKind,
                AInPublicationStability.valueOf(locInput["stability"].toString().uppercase()),
                locCoordinates["logicalVersion"] ?: locCoordinates["version"] ?: locInput["version"].toString(),
                AIcPublicationPlanner.publicationEndpointRegistry(locPlan as Map<String, Object>),
                AIcPublicationPlanner.snapshotPublicationEndpoints(locPlan as Map<String, Object>), emptyList(), emptyList(), false)
            locExpectedOutputs.putIfAbsent(locId, AIcdScheduledOutput(locId, null, locPending, locPlan, locProfiles))
        }
        if (locDomainMetadata.isNotEmpty()) {
            AIcReadDomain(locDomainId, "artifact-results")?.let { locCommitted ->
                locCommitted.versionScopes().forEach { locScope ->
                    locDomainScopeResults[AIcScopeKey(locScope)] = locScope
                    locFinalizedScopes.add(AIcScopeKey(locScope))
                }
                locContributionWritten = true
            }
            AIcReadDomain(locDomainId, "scope-finalization")?.let { locReceipt ->
                locReceipt.versionScopes().forEach { locScope ->
                    locDomainScopeResults[AIcScopeKey(locScope)] = locScope
                    locFinalizedScopes.add(AIcScopeKey(locScope))
                    AIcCollectRefreshRequests(locScope)
                }
                locDomainsFinalized = true
            }
        }
    }

    private fun AIcScopeKey(aOutput: AIcdScheduledOutput): String = listOf(
        aOutput.plan["versionScopePath"]?.toString() ?: ".", aOutput.pendingResult.version(),
        AIcStability(aOutput.pendingResult.stability())).joinToString("|")

    private fun AIcScopeKey(aScope: AIcVersionScopePublicationExecutionResult): String =
        listOf(aScope.versionScopeId(), aScope.version(), AIcStability(aScope.stability())).joinToString("|")

    private fun AIcCommonInvocationId(): String? = providers.gradleProperty("modustro.build.invocationId")
        .orElse(providers.environmentVariable("MODUSTRO_BUILD_INVOCATION_ID")).orNull?.takeIf { it.isNotBlank() }

    private fun AIcInvocationId(): String = (AIcContext()["Invocation"] as Map<*, *>)["Id"].toString()

    private fun AIcCanonicalData(aValue: Any?): Any? = when (aValue) {
        is Map<*, *> -> aValue.entries.associate { it.key.toString() to AIcCanonicalData(it.value) }.toSortedMap()
        is List<*> -> aValue.map(::AIcCanonicalData)
        else -> aValue
    }

    private fun AIcScopeOwner(aScopePath: String): String = locDomainMetadata.keys
        .filter { it == "." || aScopePath == it || aScopePath.startsWith("$it/") }
        .maxByOrNull { if (it == ".") -1 else it.length } ?: locDomainId

    private fun AIcOutputProfileKey(aArtifactPath: String, aOutput: AIcOutputPublicationExecutionResult): String =
        listOf(aArtifactPath, aOutput.technologyKind(), aOutput.outputKind().name, aOutput.version(), AIcStability(aOutput.stability())).joinToString("|")

    /** Retains descriptor-declared outputs for which no producer/task was registered in this invocation. */
    @Suppress("UNCHECKED_CAST")
    private fun AIcAddUnrepresentedOutputs(aActiveScopes: Set<String>) {
        aActiveScopes.filter { it !in locFinalizedScopes }.forEach { locScopeKey ->
            val locAnchor = locExpectedOutputs.values.first { AIcScopeKey(it) == locScopeKey }
            val locScopePath = locAnchor.plan["versionScopePath"]?.toString() ?: "."
            val locStability = AIcStability(locAnchor.pendingResult.stability())
            locArtifactMetadata.filter { (it["versionScopePath"]?.toString() ?: ".") == locScopePath }.forEach { locMetadata ->
                val locPath = locMetadata["path"].toString()
                val locPolicies = locMetadata["outputPublications"] as? Map<String, Map<String, Any?>> ?: emptyMap()
                locPolicies.forEach locPolicy@{ (locOutputKey, locPolicy) ->
                    val locTechnology = locOutputKey.substringBeforeLast('.')
                    val locKind = locOutputKey.substringAfterLast('.')
                    if (!locKind.startsWith("native_")) return@locPolicy
                    val locSelection = listOf(locPath, locTechnology, locKind, locStability).joinToString("|")
                    if (locSelection in locDeclaredOutputSelections) return@locPolicy
                    val locBranch = locPolicy[locStability] as? Map<String, Any?> ?: return@locPolicy
                    val locEndpoints = (locBranch["publicationEndpoints"] as? List<Map<String, Any?>>).orEmpty()
                    if (locBranch["publicationEnabled"] != true || locEndpoints.none { it["executionEnabled"] != false }) return@locPolicy
                    val locSnapshot = locPolicy["snapshot"] as? Map<String, Any?> ?: emptyMap()
                    val locRegistry = listOf("snapshot", "release").flatMap { locLane ->
                        ((locPolicy[locLane] as? Map<String, Any?>)?.get("publicationEndpoints") as? List<Map<String, Any?>>).orEmpty()
                    }
                    val locArtifactActions = locMetadata["artifactPublicationFinalizationActions"] as? Map<String, Any?> ?: emptyMap()
                    val locScopeActions = locMetadata["versionScopePublicationFinalizationActions"] as? Map<String, Any?> ?: emptyMap()
                    val locPlan = mapOf<String, Any?>("artifactPath" to locPath, "versionScopePath" to locScopePath,
                        "publicationEnabled" to true, "publicationEndpoints" to locEndpoints, "publicationEndpointRegistry" to locRegistry,
                        "snapshotPublicationEndpoints" to (locSnapshot["publicationEndpoints"] ?: emptyList<Any>()),
                        "artifactPublicationFinalizationActions" to (locArtifactActions[locStability] ?: emptyList<Any>()),
                        "versionScopePublicationFinalizationActions" to (locScopeActions[locStability] ?: emptyList<Any>()))
                    val locArtifactIdentity = locExpectedOutputs.values.firstOrNull { it.plan["artifactPath"]?.toString() == locPath }
                        ?.pendingResult?.artifactIdentity() ?: "${locMetadata["groupId"]}:unrepresented:$locPath"
                    val locPending = AIcOutputPublicationExecutionResult(locArtifactIdentity, locTechnology,
                        AInPublicationOutputKind.valueOf(locKind.uppercase()), locAnchor.pendingResult.stability(), locAnchor.pendingResult.version(),
                        AIcPublicationPlanner.publicationEndpointRegistry(locPlan as Map<String, Object>),
                        AIcPublicationPlanner.snapshotPublicationEndpoints(locPlan as Map<String, Object>), emptyList(), emptyList(), false)
                    val locId = "descriptor:$locScopeKey|$locSelection"
                    locExpectedOutputs.putIfAbsent(locId, AIcdScheduledOutput(locId, null, locPending, locPlan,
                        locMetadata["credentialProfiles"] as? Map<String, Any?> ?: emptyMap()))
                }
            }
        }
    }

    @Synchronized fun AIcRepositoryRefreshRequested(aOutputKind: String): Boolean = aOutputKind in locRepositoryRefreshRequests

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
                    val identity = linkedMapOf<String, Any>("Role" to (if (type.name.contains("codegen")) "source-generator" else if (type == AIcModustroSettingsPlugin::class.java) "gradle-init" else "builder-core"), "Coordinate" to "unknown", "Sha256" to AIcBuildRecordPublicationFinalizationActionAdapter.hash(file.toPath()), "ProducerRevision" to "unknown")
                    java.util.zip.ZipFile(file).use { zip -> zip.getEntry("META-INF/modustro/modustro-artifact-manifest.yml")?.let { entry ->
                        val manifest = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                        fun field(name: String) = Regex("(?m)^  " + name + ": *([^\\n]+)").find(manifest)?.groupValues?.get(1)?.trim()?.trim('"', '\'')
                        val group = field("GroupId"); val artifact = field("ArtifactCoordinateId"); val version = field("Version")
                        if (group != null && artifact != null && version != null) identity["Coordinate"] = "$group:$artifact:$version"
                    } }
                    locTools.add(identity)
                }
            }
        }
        val locInvocation = linkedMapOf<String, Any>("Id" to (AIcCommonInvocationId() ?: java.util.UUID.randomUUID().toString()), "StartedAt" to java.time.Instant.now().toString())
        val server = providers.environmentVariable("GITHUB_SERVER_URL").orNull
        val repository = providers.environmentVariable("GITHUB_REPOSITORY").orNull
        val run = providers.environmentVariable("GITHUB_RUN_ID").orNull
        if (server != null && repository != null && run != null) locInvocation["LogUrl"] = "$server/$repository/actions/runs/$run"
        val context = linkedMapOf<String, Any?>("RepositoryId" to locId, "Invocation" to locInvocation, "Sources" to locSources, "Tools" to locTools, "OutputOrigin" to mapOf("Kind" to "unknown"))
        locLogger.lifecycle("Modustro invocation: {}", AIcBuildRecordPublicationFinalizationActionAdapter.json(context))
        locContext = context
        return context
    }

    override fun onFinish(event: org.gradle.tooling.events.FinishEvent) { AIcContext() }

    private fun AIcCredentials(aEndpoint: AIcPublicationEndpoint, aProfiles: Map<String, Any?>,
        aBaseDirectory: java.io.File = parameters.credentialBaseDirectory.get().asFile): Map<String, String> {
        val locId = aEndpoint.publicationCredentialProfile() ?: return emptyMap()
        val locProfile = aProfiles[locId] as? Map<*, *>
            ?: throw GradleException("Publication endpoint '${aEndpoint.id()}' references undefined credential profile '$locId'.")
        val locType = locProfile["type"]?.toString()?.trim()?.lowercase().orEmpty()
        fun AIcValue(aField: String): String = locCredentialValues.locAlgitesResolveCredentialValue(
            locId, locType, aField, aBaseDirectory
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

    @Suppress("UNCHECKED_CAST")
    private fun AIcArtifactActions(aPlan: Map<String, Any?>): List<AIcArtifactPublicationFinalizationAction> =
        AIcPublicationPlanner.artifactPublicationFinalizationActions(aPlan["artifactPublicationFinalizationActions"])

    @Suppress("UNCHECKED_CAST")
    private fun AIcVersionScopeActions(aPlan: Map<String, Any?>): List<AIcVersionScopePublicationFinalizationAction> =
        AIcPublicationPlanner.versionScopePublicationFinalizationActions(aPlan["versionScopePublicationFinalizationActions"])

    private fun AIcStability(aValue: AInPublicationStability): String = aValue.name.lowercase()

    private fun AIcFinalizationTreeMap(aNode: AIcFinalizationActionExecutionResult): Map<String, Any?> = linkedMapOf(
        "Id" to aNode.id(),
        "ScopeKind" to aNode.scopeKind(),
        "Success" to aNode.result()?.success(),
        "IgnoredFailure" to aNode.result()?.ignoredFailure(),
        "Attempts" to aNode.result()?.attempts(),
        "OutputUri" to aNode.result()?.outputUri()?.toString(),
        "Failure" to aNode.result()?.failure()?.message,
        "Metadata" to aNode.result()?.metadata(),
        "FinalizationActions" to aNode.finalizationActions().map(::AIcFinalizationTreeMap)
    )

    private fun AIcExecutionMap(aResult: AIcVersionScopePublicationExecutionResult): Map<String, Any?> = linkedMapOf(
        "VersionScopeId" to aResult.versionScopeId(),
        "Version" to aResult.version(),
        "Stability" to AIcStability(aResult.stability()),
        "State" to aResult.state().name,
        "PublicationDomains" to locCrossDomainVersionScopes[aResult.versionScopeId()],
        "Artifacts" to aResult.artifacts().map { locArtifact -> linkedMapOf(
            "ArtifactIdentity" to locArtifact.artifactIdentity(),
            "ArtifactPath" to locArtifact.artifactPath(),
            "Success" to locArtifact.success(),
            "Outputs" to locArtifact.outputs().map { locOutput -> linkedMapOf(
                "ArtifactIdentity" to locOutput.artifactIdentity(),
                "TechnologyKind" to locOutput.technologyKind(),
                "OutputKind" to locOutput.outputKind().descriptorName(),
                "Completed" to locOutput.completed(),
                "Success" to locOutput.success(),
                "Publications" to locOutput.publications().map { locPublication -> linkedMapOf(
                    "PublicationId" to locPublication.publicationId(),
                    "EndpointId" to locPublication.endpoint().id(),
                    "Success" to locPublication.result()?.success(),
                    "IgnoredFailure" to locPublication.result()?.ignoredFailure(),
                    "OutputUri" to locPublication.result()?.outputUri()?.toString(),
                    "Failure" to locPublication.result()?.failure()?.message,
                    "PublicationFinalizationActions" to locPublication.publicationFinalizationActions().map(::AIcFinalizationTreeMap)
                ) },
                "OutputPublicationFinalizationActions" to locOutput.outputPublicationFinalizationActions().map(::AIcFinalizationTreeMap)
            ) },
            "ArtifactPublicationFinalizationActions" to locArtifact.artifactPublicationFinalizationActions().map(::AIcFinalizationTreeMap)
        ) },
        "VersionScopePublicationFinalizationActions" to aResult.versionScopePublicationFinalizationActions().map(::AIcFinalizationTreeMap)
    )

    private fun AIcWriteVersionScopeRecord(aResult: AIcVersionScopePublicationExecutionResult, aMissingResults: List<String> = emptyList()) {
        val locInvocation = AIcInvocationId()
        val locInvocationId = locInvocation.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(100) + "-" + AIcPublicationDomainStore.fingerprint(locInvocation).take(16)
        val locRoot = parameters.credentialBaseDirectory.get().asFile.toPath().resolve("build/run/publication-records/$locInvocationId/version-scopes")
        Files.createDirectories(locRoot)
        val locSafe = (aResult.versionScopeId() + "-" + aResult.version() + "-" + AIcStability(aResult.stability()))
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        val locFile = locRoot.resolve("$locSafe-${AIcPublicationDomainStore.fingerprint(AIcScopeKey(aResult)).take(16)}.json")
        if (Files.isRegularFile(locFile)) {
            val locPrevious = JsonSlurper().parseText(Files.readString(locFile)) as? Map<*, *>
            check(locPrevious?.get("State") != "COMPLETE") {
                "Completed Version Scope record is immutable: $locFile. Start a new build invocation."
            }
        }
        val locTemporary = Files.createTempFile(locRoot, "scope-", ".json.tmp")
        try {
            Files.writeString(locTemporary, AIcBuildRecordPublicationFinalizationActionAdapter.json(AIcExecutionMap(aResult) + ("MissingDomainResults" to aMissingResults)) + "\n")
            try { Files.move(locTemporary, locFile, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
            catch (locFailure: java.nio.file.AtomicMoveNotSupportedException) { Files.move(locTemporary, locFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(locTemporary) }
        locLogger.lifecycle("Modustro Version Scope publication record: {}", locFile)
    }

    /** Drains all output graphs, then executes artifact and Version-Scope publication finalization barriers. */
    @Synchronized fun AIcAwait() {
        val locRepositoryOutputs = locScheduledOutputs.filter { it.pendingResult.outputKind() == AInPublicationOutputKind.MODUSTRO_DOCS_SITE ||
            it.pendingResult.outputKind() == AInPublicationOutputKind.SCHEMA_SITE }
        locRepositoryOutputs.forEach { it.handle?.outputExecutionResult()?.toCompletableFuture()?.get() }
        val locSharedExpectedScopes = locExpectedOutputs.values.filter {
            (locDomainExportRequested || locAttemptedScopeKeys.isNotEmpty()) &&
                (it.plan["versionScopePath"]?.toString() ?: ".") in locCrossDomainVersionScopes
        }.map(::AIcScopeKey)
        val locActiveScopes = (locScheduledOutputs.filter { it !in locRepositoryOutputs }.map(::AIcScopeKey) + locSharedExpectedScopes).toSet()
        AIcAddUnrepresentedOutputs(locActiveScopes)
        val locCopy = locExpectedOutputs.values.filter { AIcScopeKey(it) in locActiveScopes && AIcScopeKey(it) !in locFinalizedScopes }
        if (locCopy.isEmpty()) return
        val locCompleted = locCopy.map { locScheduled ->
            val locResult = try { locScheduled.handle?.outputExecutionResult()?.toCompletableFuture()?.get() ?: locScheduled.pendingResult }
                catch (locFailure: Exception) {
                    locLogger.warn("Output publication graph did not complete cleanly: ${locScheduled.outputId}", locFailure)
                    locScheduled.pendingResult
                }
            AIcdCompletedOutput(locScheduled, locResult)
        }.filter { it.result.outputKind() != AInPublicationOutputKind.MODUSTRO_DOCS_SITE &&
            it.result.outputKind() != AInPublicationOutputKind.SCHEMA_SITE }

        val locArtifactGroups = locCompleted.groupBy { locCompletedOutput ->
            val locPlan = locCompletedOutput.scheduled.plan
            listOf(
                locPlan["versionScopePath"]?.toString() ?: ".",
                locPlan["artifactPath"]?.toString() ?: ".",
                locCompletedOutput.result.version(),
                AIcStability(locCompletedOutput.result.stability())
            ).joinToString("|")
        }

        val locArtifactResults = mutableListOf<Pair<AIcArtifactPublicationExecutionResult, List<AIcdCompletedOutput>>>()
        locArtifactGroups.values.forEach { locGroup ->
            val locFirst = locGroup.first()
            val locVersionScope = locFirst.scheduled.plan["versionScopePath"]?.toString() ?: "."
            val locArtifactPath = locFirst.scheduled.plan["artifactPath"]?.toString() ?: "."
            locGroup.forEach { locOutput ->
                locOutputProfiles[AIcOutputProfileKey(locArtifactPath, locOutput.result)] = locOutput.scheduled.profiles
            }
            val locActions = AIcArtifactActions(locFirst.scheduled.plan)
            locGroup.drop(1).forEach { locOther ->
                if (AIcArtifactActions(locOther.scheduled.plan) != locActions) {
                    throw GradleException("ArtifactPublicationFinalizationActions resolve inconsistently for '$locArtifactPath'.")
                }
            }
            val locBase = AIcArtifactPublicationExecutionResult(
                locFirst.result.artifactIdentity(), locArtifactPath, locVersionScope, locFirst.result.version(), locFirst.result.stability(),
                locGroup.map { it.result }, emptyList())
            val locFinalized = AIcScheduler().finalizeArtifact(locBase, locActions, AIcProgressReporterFactory())
            locArtifactResults.add(locFinalized to locGroup)
        }

        val locVersionScopeGroups = locArtifactResults.groupBy { (locArtifact, _) ->
            listOf(locArtifact.versionScopeId(), locArtifact.version(), AIcStability(locArtifact.stability())).joinToString("|")
        }
        val locRequiredFailures = mutableListOf<Pair<String, Throwable>>()
        locVersionScopeGroups.values.forEach { locGroup ->
            val locArtifactResultsForScope = locGroup.map { it.first }
            val locScheduledForScope = locGroup.flatMap { it.second }
            val locFirst = locScheduledForScope.first()
            val locActions = AIcVersionScopeActions(locFirst.scheduled.plan)
            locScheduledForScope.drop(1).forEach { locOther ->
                if (AIcVersionScopeActions(locOther.scheduled.plan) != locActions) {
                    throw GradleException("VersionScopePublicationFinalizationActions resolve inconsistently for '${locArtifactResultsForScope.first().versionScopeId()}'.")
                }
            }
            val locCredentialResolver = AIiPublicationCredentialResolver { locEndpoint ->
                val locMatches = locScheduledForScope.filter { locOutput ->
                    locOutput.result.snapshotPublicationEndpoints().values.any { it == locEndpoint }
                }
                val locResolved = locMatches.map { AIcCredentials(locEndpoint, it.scheduled.profiles) }.distinct()
                if (locResolved.size > 1) throw GradleException("Snapshot publication endpoint '${locEndpoint.id()}' resolves inconsistent credentials inside one Version Scope.")
                locResolved.firstOrNull() ?: emptyMap()
            }
            val locBase = AIcVersionScopePublicationExecutionResult(
                locArtifactResultsForScope.first().versionScopeId(), locArtifactResultsForScope.first().version(),
                locArtifactResultsForScope.first().stability(), AInVersionScopePublicationAttemptState.PUBLISHING,
                locArtifactResultsForScope, emptyList())
            val locScopeKey = AIcScopeKey(locBase)
            locScopeActionValues[locScopeKey] = locFirst.scheduled.plan["versionScopePublicationFinalizationActions"] ?: emptyList<Any>()
            val locFinal = if (locBase.versionScopeId() !in locCrossDomainVersionScopes) {
                AIcScheduler().finalizeVersionScope(locBase, locActions, locCredentialResolver, AIcProgressReporterFactory())
            } else {
                AIcVersionScopePublicationExecutionResult(locBase.versionScopeId(), locBase.version(), locBase.stability(),
                    if (locBase.artifacts().all { it.success() }) AInVersionScopePublicationAttemptState.PUBLISHING else AInVersionScopePublicationAttemptState.FAILED,
                    locBase.artifacts(), emptyList())
            }
            locDomainScopeResults[locScopeKey] = locFinal
            if (locScopeKey in locAttemptedScopeKeys) AIcWriteVersionScopeRecord(locFinal)
            locFinalizedScopes.add(locScopeKey)
            AIcCollectRefreshRequests(locFinal)
            if (locFinal.state() == AInVersionScopePublicationAttemptState.FAILED) {
                val locFinalizationResults = locFinal.versionScopePublicationFinalizationActions() +
                    locFinal.artifacts().flatMap { it.artifactPublicationFinalizationActions() } +
                    locFinal.artifacts().flatMap { it.outputs() }.flatMap { it.outputPublicationFinalizationActions() }
                val locRequiredFinalizationFailure = locFinalizationResults.firstOrNull { locNode ->
                    locNode.result() != null && !locNode.result().success() && !locNode.result().ignoredFailure()
                }
                if (locRequiredFinalizationFailure != null) {
                    locRequiredFailures.add(locFinal.versionScopeId() to
                        (locRequiredFinalizationFailure.result().failure() ?: IllegalStateException("Required publication finalization failed.")))
                }
                locLogger.warn("Version Scope publication attempt is incomplete: {} {} {}", locFinal.versionScopeId(), locFinal.version(), locFinal.stability())
            }
        }
        locScheduledOutputs.removeAll(locCopy.toSet())
        if (locRequiredFailures.isNotEmpty()) {
            val locFailure = GradleException("Required publication finalization failed for Version Scope(s): " +
                locRequiredFailures.joinToString { it.first }, locRequiredFailures.first().second)
            locRequiredFailures.drop(1).forEach { locFailure.addSuppressed(it.second) }
            throw locFailure
        }
    }

    /** Marks an enabled native attempt before payload validation, so failed producers remain visible. */
    @Synchronized fun AIcBeginOutput(aPlan: Map<String, Any?>, aOutputKind: String, aStability: String,
        aVersion: String, aCoordinates: Map<String, String>) {
        if (aOutputKind in setOf("MODUSTRO_DOCS_SITE", "SCHEMA_SITE")) return
        if (locDomainMetadata.size > 1) {
            check(!AIcCommonInvocationId().isNullOrBlank()) {
                "Isolated-domain publication requires MODUSTRO_BUILD_INVOCATION_ID or -Pmodustro.build.invocationId. " +
                    "The Modustro phase controller supplies a fresh shared identity automatically."
            }
        }
        val locScopeKey = listOf(aPlan["versionScopePath"]?.toString() ?: ".",
            aCoordinates["logicalVersion"] ?: aCoordinates["version"] ?: aVersion, aStability.lowercase()).joinToString("|")
        check(locScopeKey !in locFinalizedScopes) { "Publication arrived after its Version Scope was finalized: $locScopeKey" }
        if (locDomainMetadata.isNotEmpty()) {
            val locFile = AIcPublicationDomainStore.resultFile(locRepositoryDirectory!!, AIcInvocationId(), locDomainId, "artifact-results")
            check(!Files.exists(locFile)) { "Publication results for domain '$locDomainId' are already committed. Start a new invocation." }
        }
        locAttemptedScopeKeys.add(locScopeKey)
    }

    private fun AIcCollectRefreshRequests(aScope: AIcVersionScopePublicationExecutionResult) {
        if (aScope.state() != AInVersionScopePublicationAttemptState.COMPLETE) return
        aScope.versionScopePublicationFinalizationActions().filter { it.result()?.success() == true }.forEach { locAction ->
            (locAction.result().metadata()["RepositoryPublicationRefreshRequests"] as? List<*>)?.forEach { locKind ->
                locKind?.toString()?.let(locRepositoryRefreshRequests::add)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun AIcDomainContribution(aReceipt: Boolean): AIcPublicationDomainContribution {
        val locScopes = locDomainScopeResults.values.filter { !aReceipt || AIcScopeOwner(it.versionScopeId()) == locDomainId }
            .sortedBy(::AIcScopeKey)
        val locExpectedArtifacts = locExpectedOutputs.values.groupBy(::AIcScopeKey).mapValues { (_, locOutputs) ->
            locOutputs.map { it.plan["artifactPath"]?.toString() ?: "." }.distinct().sorted()
        }
        val locMetadata = linkedMapOf<String, Any?>("PlanFingerprint" to locPlanFingerprint,
            "AttemptedScopes" to locAttemptedScopeKeys.sorted(), "ExpectedArtifactsByScope" to locExpectedArtifacts,
            "EffectiveSelections" to locEffectiveOutputSelections.toMap(),
            "ScopeActions" to locScopeActionValues.toMap(), "OutputCredentialProfiles" to locOutputProfiles.toMap(),
            "RepositoryPublicationRefreshRequests" to locRepositoryRefreshRequests.sorted())
        return AIcPublicationDomainContribution(AIcInvocationId(), locDomainId, locScopes, locMetadata as Map<String, Object>)
    }

    /** Commits terminal local artifact trees even when a required local finalizer failed. */
    @Synchronized fun AIcExportDomainResults(aCompleteExpectedPlan: Boolean = true) {
        if (aCompleteExpectedPlan) locDomainExportRequested = true
        if (locDomainMetadata.isEmpty()) { AIcAwait(); return }
        try { AIcAwait() } finally {
            if (!locContributionWritten && (locDomainExportRequested || locAttemptedScopeKeys.isNotEmpty())) {
                val locFile = AIcPublicationDomainStore.resultFile(locRepositoryDirectory!!, AIcInvocationId(), locDomainId, "artifact-results")
                if (locAttemptedScopeKeys.isEmpty() && Files.isRegularFile(locFile)) {
                    AIcReadDomain(locDomainId, "artifact-results")
                } else {
                    AIcPublicationDomainStore.write(locRepositoryDirectory!!, "artifact-results", AIcDomainContribution(false))
                }
                locContributionWritten = true
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun AIcReadDomain(aDomainId: String, aStage: String): AIcPublicationDomainContribution? {
        val locFile = AIcPublicationDomainStore.resultFile(locRepositoryDirectory!!, AIcInvocationId(), aDomainId, aStage)
        if (!Files.isRegularFile(locFile)) return null
        val locContribution = AIcPublicationDomainStore.read(locRepositoryDirectory!!, AIcInvocationId(), aDomainId, aStage) {
            JsonSlurper().parseText(it) as Map<String, Object>
        }
        check(locContribution.metadata()["PlanFingerprint"] == locPlanFingerprint) {
            "Publication domain '$aDomainId' resolved a different repository plan."
        }
        return locContribution
    }

    @Suppress("UNCHECKED_CAST")
    private fun AIcExpectedArtifactPaths(aDomain: String, aScope: AIcVersionScopePublicationExecutionResult,
        aContribution: AIcPublicationDomainContribution?): Set<String> {
        val locPaths = linkedSetOf<String>()
        val locDirectories = locDomainMetadata[aDomain]?.get("artifactDirectories") as? List<Map<String, Any?>> ?: emptyList()
        val locSelections = aContribution?.metadata()?.get("EffectiveSelections") as? Map<String, Boolean> ?: emptyMap()
        val locLane = AIcStability(aScope.stability())
        locDirectories.filter { it["structureKind"] == "artifact" && (it["versionScopePath"]?.toString() ?: ".") == aScope.versionScopeId() }
            .forEach { locDirectory ->
                val locPath = locDirectory["path"].toString()
                val locPolicies = locDirectory["outputPublications"] as? Map<String, Map<String, Any?>> ?: emptyMap()
                if (locPolicies.any { (locKey, locPolicy) ->
                    val locKind = locKey.substringAfterLast('.')
                    val locBranch = locPolicy[locLane] as? Map<String, Any?> ?: emptyMap()
                    val locSelection = listOf(locPath, locKey.substringBeforeLast('.'), locKind, locLane).joinToString("|")
                    locKind.startsWith("native_") && locSelections[locSelection] != false && locBranch["publicationEnabled"] == true &&
                        ((locBranch["publicationEndpoints"] as? List<Map<String, Any?>>).orEmpty().any { it["executionEnabled"] != false })
                }) locPaths.add(locPath)
            }
        val locRegistered = aContribution?.metadata()?.get("ExpectedArtifactsByScope") as? Map<String, List<String>> ?: emptyMap()
        locPaths.addAll(locRegistered[AIcScopeKey(aScope)].orEmpty())
        return locPaths
    }

    /** Runs only after descendant-domain finalizers/export tasks; the scope owner executes the shared barrier once. */
    @Synchronized fun AIcFinalizeDomains() {
        if (locDomainsFinalized) return
        if (locDomainMetadata.isEmpty()) { AIcAwait(); return }
        AIcExportDomainResults()
        val locReceiptFile = AIcPublicationDomainStore.resultFile(locRepositoryDirectory!!, AIcInvocationId(), locDomainId, "scope-finalization")
        if (Files.isRegularFile(locReceiptFile)) {
            val locReceipt = AIcReadDomain(locDomainId, "scope-finalization")!!
            locReceipt.versionScopes().forEach(::AIcCollectRefreshRequests)
            locDomainsFinalized = true
            return
        }
        val locRelevantDomains = locDomainMetadata.keys.filter { locDomainId == "." || it == locDomainId || it.startsWith("$locDomainId/") }.sorted()
        val locContributions = locRelevantDomains.mapNotNull { AIcReadDomain(it, "artifact-results") }
        val locActiveKeys = locContributions.flatMap { (it.metadata()["AttemptedScopes"] as? List<*>).orEmpty().map(Any?::toString) }.toSet()
        val locAnchors = locContributions.flatMap { it.versionScopes() }.filter { AIcScopeKey(it) in locActiveKeys }
            .distinctBy(::AIcScopeKey).filter { AIcScopeOwner(it.versionScopeId()) == locDomainId && it.versionScopeId() in locCrossDomainVersionScopes }
        val locFailures = mutableListOf<Throwable>()
        try {
            locRelevantDomains.filter { it != locDomainId }.forEach { locChild ->
                val locReceipt = AIcReadDomain(locChild, "scope-finalization")
                    ?: throw GradleException("Missing finalization receipt for publication domain '$locChild'.")
                locReceipt.versionScopes().forEach { locScope ->
                    check(AIcScopeOwner(locScope.versionScopeId()) == locChild) { "Domain '$locChild' finalized another domain's Version Scope." }
                    AIcCollectRefreshRequests(locScope)
                }
            }
            locAnchors.forEach { locAnchor ->
                val locExpected = linkedMapOf<String, Set<String>>()
                locRelevantDomains.forEach { locDomain ->
                    val locContribution = locContributions.firstOrNull { it.domainId() == locDomain }
                    val locPaths = AIcExpectedArtifactPaths(locDomain, locAnchor, locContribution)
                    if (locPaths.isNotEmpty() || locDomain == locDomainId) locExpected[locDomain] = locPaths
                }
                val locParticipants = locContributions.filter { it.domainId() in locExpected }
                val locCombined = AIcPublicationDomainBridge.aggregate(AIcInvocationId(), locAnchor.versionScopeId(), locAnchor.version(),
                    locAnchor.stability(), locExpected, locParticipants)
                val locScopeKey = AIcScopeKey(locAnchor)
                val locActionLists = locParticipants.filter { it.versionScopes().any { AIcScopeKey(it) == locScopeKey } }.map { locContribution ->
                    @Suppress("UNCHECKED_CAST")
                    val locScopeActions = locContribution.metadata()["ScopeActions"] as? Map<String, Any?> ?: emptyMap()
                    AIcPublicationPlanner.versionScopePublicationFinalizationActions(locScopeActions[locScopeKey])
                }.distinct()
                check(locActionLists.size <= 1) { "VersionScopePublicationFinalizationActions differ across publication domains: $locScopeKey" }
                val locCredentialResolver = AIiPublicationCredentialResolver { locEndpoint ->
                    val locCredentials = locParticipants.flatMap { locContribution ->
                        @Suppress("UNCHECKED_CAST")
                        val locProfiles = locContribution.metadata()["OutputCredentialProfiles"] as? Map<String, Map<String, Any?>> ?: emptyMap()
                        val locBaseDirectory = if (locContribution.domainId() == ".") locRepositoryDirectory!!.toFile()
                            else locRepositoryDirectory!!.resolve(locContribution.domainId()).toFile()
                        locContribution.versionScopes().filter { AIcScopeKey(it) == locScopeKey }.flatMap { it.artifacts() }.flatMap { locArtifact ->
                            locArtifact.outputs().filter { it.snapshotPublicationEndpoints().values.any { it == locEndpoint } }.map { locOutput ->
                                AIcCredentials(locEndpoint, locProfiles[AIcOutputProfileKey(locArtifact.artifactPath(), locOutput)] ?: emptyMap(), locBaseDirectory)
                            }
                        }
                    }.distinct()
                    check(locCredentials.size <= 1) { "Snapshot endpoint '${locEndpoint.id()}' resolves inconsistent credentials across publication domains." }
                    locCredentials.firstOrNull() ?: emptyMap()
                }
                val locFinal = if (locCombined.readyForFinalization()) {
                    AIcScheduler().finalizeVersionScope(locCombined.execution(), locActionLists.firstOrNull() ?: emptyList(),
                        locCredentialResolver, AIcProgressReporterFactory())
                } else locCombined.execution()
                locDomainScopeResults[locScopeKey] = locFinal
                locFinalizedScopes.add(locScopeKey)
                AIcWriteVersionScopeRecord(locFinal, locCombined.missingResults())
                AIcCollectRefreshRequests(locFinal)
                if (locFinal.state() == AInVersionScopePublicationAttemptState.FAILED) {
                    locFailures.add(GradleException("Shared Version Scope did not complete: $locScopeKey; missing ${locCombined.missingResults()}"))
                }
            }
        } finally {
            AIcPublicationDomainStore.write(locRepositoryDirectory!!, "scope-finalization", AIcDomainContribution(true))
            locDomainsFinalized = true
        }
        if (locFailures.isNotEmpty()) {
            val locFailure = GradleException("Shared publication finalization failed.", locFailures.first())
            locFailures.drop(1).forEach(locFailure::addSuppressed)
            throw locFailure
        }
    }

    override fun close() {
        try { AIcExportDomainResults(false) } finally { locScheduler?.close() }
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
    @get:Input abstract val requiresRepositoryRefreshRequest: Property<Boolean>

    init {
        credentialProfilesJson.convention("{}")
        coordinates.convention(emptyMap())
        publishedFileNames.convention(emptyMap())
        requiresSnapshotInstance.convention(false)
        snapshotInstanceId.convention("")
        requiresRepositoryRefreshRequest.convention(false)
        onlyIf {
            !requiresRepositoryRefreshRequest.get() || publicationService.get().AIcRepositoryRefreshRequested(outputKind.get())
        }
    }

    /** A serializable description for the prepare task, calculated from declared task inputs. */
    fun AIcExpectedPublicationInput(): Provider<String> = publicationPlanJson.zip(credentialProfilesJson) { aPlan, aProfiles ->
        mapOf<String, Any>("plan" to aPlan, "profiles" to aProfiles)
    }.zip(coordinates) { aInput, aCoordinates -> aInput + ("coordinates" to aCoordinates) }
        .zip(outputKind) { aInput, aKind -> aInput + ("outputKind" to aKind) }
        .zip(stability) { aInput, aStability -> aInput + ("stability" to aStability) }
        .zip(artifactIdentity) { aInput, aIdentity -> aInput + ("artifactIdentity" to aIdentity) }
        .zip(publicationVersion) { aInput, aVersion -> JsonOutput.toJson(aInput + ("version" to aVersion)) }

    @TaskAction fun AIcPublish() {
        @Suppress("UNCHECKED_CAST")
        val locPlan = JsonSlurper().parseText(publicationPlanJson.get()) as Map<String, Any?>
        if (locPlan["publicationEnabled"] != true) return
        val locEndpoints = (locPlan["publicationEndpoints"] as? List<*>).orEmpty()
        if (locEndpoints.none { (it as? Map<*, *>)?.get("enabled") != false }) return
        publicationService.get().AIcBeginOutput(locPlan, outputKind.get(), stability.get(), publicationVersion.get(), coordinates.get())
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
            locFiles, coordinates.get()), locPlan, locProfiles, path)
    }
}

/** Establishes the complete expected output set before publication work starts. */
abstract class AIcModustroPreparePublicationsTask : DefaultTask() {
    @get:Input abstract val expectedPublicationInputs: MapProperty<String, String>
    @get:Input abstract val crossDomainVersionScopes: MapProperty<String, String>
    @get:Input abstract val artifactMetadataJson: MapProperty<String, String>
    @get:Input abstract val domainMetadataJson: Property<String>
    @get:Input abstract val domainId: Property<String>
    @get:Internal abstract val repositoryDirectory: DirectoryProperty
    @get:Internal abstract val publicationService: Property<AIcModustroPublicationService>
    init {
        expectedPublicationInputs.convention(emptyMap())
        crossDomainVersionScopes.convention(emptyMap())
        artifactMetadataJson.convention(emptyMap())
        domainMetadataJson.convention("{}")
        domainId.convention(".")
    }
    @TaskAction fun AIcPrepare() {
        val locRepository = repositoryDirectory.orNull?.asFile?.absolutePath
            ?: publicationService.get().parameters.credentialBaseDirectory.get().asFile.absolutePath
        publicationService.get().AIcPrepare(expectedPublicationInputs.get(), crossDomainVersionScopes.get(), artifactMetadataJson.get(),
            domainMetadataJson.get(), domainId.get(), locRepository)
    }
}

/** Finalizes shared background publications without capturing a build script. */
abstract class AIcModustroAwaitPublicationsTask : DefaultTask() {
    @get:Internal abstract val publicationService: Property<AIcModustroPublicationService>
    @TaskAction fun AIcAwait() { publicationService.get().AIcAwait() }
}

/** Commits a complete data-only result handoff after the local publication graph becomes terminal. */
abstract class AIcModustroExportPublicationResultsTask : DefaultTask() {
    @get:Internal abstract val publicationService: Property<AIcModustroPublicationService>
    @TaskAction fun AIcExport() { publicationService.get().AIcExportDomainResults() }
}

/** Aggregates isolated-domain result files at the Version Scope's owning build boundary. */
abstract class AIcModustroFinalizePublicationScopesTask : DefaultTask() {
    @get:Internal abstract val publicationService: Property<AIcModustroPublicationService>
    @TaskAction fun AIcFinalize() { publicationService.get().AIcFinalizeDomains() }
}
