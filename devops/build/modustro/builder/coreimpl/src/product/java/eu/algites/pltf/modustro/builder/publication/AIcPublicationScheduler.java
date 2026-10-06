package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.execution.AIngBuildExecutionFailurePolicy_1;

import eu.algites.pltf.modustro.builder.catalog.AIcAdapterCatalog;
import eu.algites.pltf.modustro.builder.model.publication.*;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Executes publication graphs and the output/artifact/Version-Scope finalization barriers. */
public final class AIcPublicationScheduler implements AutoCloseable {
    private final AIcAdapterCatalog adapterCatalog;
    private final ExecutorService orchestrationExecutor;
    private final ExecutorService attemptExecutor;
    private final boolean ownsExecutors;
    private final Set<CompletableFuture<?>> activeOutputs = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closing = new AtomicBoolean(false);

    public AIcPublicationScheduler(AIcAdapterCatalog aAdapterCatalog) {
        this(aAdapterCatalog,
                Executors.newCachedThreadPool(AIcThreadFactory("modustro-publication-orchestration")),
                Executors.newCachedThreadPool(AIcThreadFactory("modustro-publication-attempt")), true);
    }

    public AIcPublicationScheduler(
            AIcAdapterCatalog aAdapterCatalog,
            ExecutorService aOrchestrationExecutor,
            ExecutorService aAttemptExecutor) {
        this(aAdapterCatalog, aOrchestrationExecutor, aAttemptExecutor, false);
    }

    private AIcPublicationScheduler(
            AIcAdapterCatalog aAdapterCatalog,
            ExecutorService aOrchestrationExecutor,
            ExecutorService aAttemptExecutor,
            boolean aOwnsExecutors) {
        adapterCatalog = Objects.requireNonNull(aAdapterCatalog, "adapterCatalog");
        orchestrationExecutor = Objects.requireNonNull(aOrchestrationExecutor, "orchestrationExecutor");
        attemptExecutor = Objects.requireNonNull(aAttemptExecutor, "attemptExecutor");
        ownsExecutors = aOwnsExecutors;
    }

    /** Schedules one complete output publication graph. */
    public AIcPublicationScheduleHandle schedule(
            AIcPublicationPayload aRootPayload,
            List<AIcPublicationJob> aJobs,
            List<AIcOutputPublicationFinalizationAction> aOutputFinalizationActions,
            Map<String, AIcPublicationEndpoint> aSnapshotPublicationEndpoints,
            AIiPublicationCredentialResolver aCredentialResolver,
            AIiPublicationProgressReporterFactory aProgressReporterFactory) {
        Objects.requireNonNull(aRootPayload, "rootPayload");
        Objects.requireNonNull(aJobs, "jobs");
        Objects.requireNonNull(aOutputFinalizationActions, "outputFinalizationActions");
        Objects.requireNonNull(aSnapshotPublicationEndpoints, "snapshotPublicationEndpoints");
        Objects.requireNonNull(aCredentialResolver, "credentialResolver");
        Objects.requireNonNull(aProgressReporterFactory, "progressReporterFactory");
        if (closing.get()) throw new IllegalStateException("Publication scheduler is closed.");

        LinkedHashMap<String, CompletableFuture<AIcPublicationResult>> locPublicationResults = new LinkedHashMap<>();
        LinkedHashMap<String, CompletableFuture<AIcFinalizationActionExecutionResult>> locActionResults = new LinkedHashMap<>();
        for (AIcPublicationJob locJob : aJobs) {
            if (!locJob.endpoint().executionEnabled()) continue;
            if (locPublicationResults.putIfAbsent(locJob.id(), new CompletableFuture<>()) != null) {
                throw new IllegalArgumentException("Duplicate root publication id '" + locJob.id() + "'.");
            }
            AIcValidatePublicationFinalizationActions(locJob.publicationFinalizationActions(), locJob.id(), locActionResults);
        }

        LinkedHashMap<String, CompletableFuture<Void>> locRequiredSteps = new LinkedHashMap<>();
        for (AIcPublicationJob locJob : aJobs) {
            if (!locJob.endpoint().executionEnabled()) continue;
            if (locJob.endpoint().executionFailurePolicy() != AIngBuildExecutionFailurePolicy_1.IGNORE_FAILURE) {
                locRequiredSteps.put(locJob.id(), new CompletableFuture<>());
            }
            AIcRegisterRequiredActions(
                    locJob.publicationFinalizationActions(), locJob.id(),
                    List.of(locJob.endpoint().executionFailurePolicy()), locRequiredSteps);
        }
        CompletableFuture<AIcOutputRun> locRun = CompletableFuture.supplyAsync(() -> AIcRunOutput(
                aRootPayload, aJobs, aOutputFinalizationActions, aSnapshotPublicationEndpoints,
                locPublicationResults, locActionResults, locRequiredSteps, aCredentialResolver, aProgressReporterFactory), orchestrationExecutor);
        activeOutputs.add(locRun);
        CompletableFuture<AIcOutputRun> locTerminatedRun = locRun.whenComplete((aResult, aFailure) -> {
            activeOutputs.remove(locRun);
            Throwable locReason = aFailure == null
                    ? new CancellationException("Publication execution was skipped by its parent or an earlier required failure.")
                    : AIcCause(aFailure);
            locPublicationResults.values().forEach(aFuture -> aFuture.completeExceptionally(locReason));
            locActionResults.values().forEach(aFuture -> aFuture.completeExceptionally(locReason));
            locRequiredSteps.values().forEach(aFuture -> {
                if (aFailure == null) aFuture.complete(null); else aFuture.completeExceptionally(AIcCause(aFailure));
            });
        });
        CompletableFuture<AIcOutputPublicationExecutionResult> locOutput = locTerminatedRun.thenApply(AIcOutputRun::result);
        CompletableFuture<Void> locRequired = CompletableFuture.allOf(
                locRequiredSteps.values().toArray(CompletableFuture[]::new));
        if (aOutputFinalizationActions.stream().anyMatch(aAction -> aAction.executionEnabled()
                && aAction.executionFailurePolicy() != AIngBuildExecutionFailurePolicy_1.IGNORE_FAILURE)) {
            locRequired = CompletableFuture.allOf(locRequired, locTerminatedRun.thenAccept(aCompleted -> {
                if (!aCompleted.failureFlow().AIcIsNone()) {
                    throw new CompletionException(aCompleted.failureFlow().AIcAsThrowable());
                }
            }));
        }
        return new AIcPublicationScheduleHandle(locRequired, locOutput, locPublicationResults, locActionResults);
    }

    /** Executes the flat artifact finalization barrier after every output execution is terminal. */
    public AIcArtifactPublicationExecutionResult finalizeArtifact(
            AIcArtifactPublicationExecutionResult aBase,
            List<AIcArtifactPublicationFinalizationAction> aActions,
            AIiPublicationProgressReporterFactory aProgressReporterFactory) {
        Objects.requireNonNull(aBase, "base");
        Objects.requireNonNull(aActions, "actions");
        if (!aBase.outputs().stream().allMatch(AIcOutputPublicationExecutionResult::success)) return aBase;
        List<AIcFinalizationActionExecutionResult> locResults = AIcRunArtifactFinalizationActions(
                aBase, aActions, aProgressReporterFactory);
        return new AIcArtifactPublicationExecutionResult(
                aBase.artifactIdentity(), aBase.artifactPath(), aBase.versionScopeId(), aBase.version(), aBase.stability(),
                aBase.outputs(), locResults);
    }

    /** Executes the flat Version-Scope finalization barrier after every artifact execution is terminal. */
    public AIcVersionScopePublicationExecutionResult finalizeVersionScope(
            AIcVersionScopePublicationExecutionResult aBase,
            List<AIcVersionScopePublicationFinalizationAction> aActions,
            Map<String, Map<String, String>> aCredentialsByEndpointId,
            AIiPublicationProgressReporterFactory aProgressReporterFactory) {
        Objects.requireNonNull(aCredentialsByEndpointId, "credentialsByEndpointId");
        LinkedHashMap<String, Map<String, String>> locCredentials = new LinkedHashMap<>();
        aCredentialsByEndpointId.forEach((aId, aValues) -> locCredentials.put(aId, Map.copyOf(aValues)));
        Map<String, Map<String, String>> locSnapshot = Map.copyOf(locCredentials);
        return finalizeVersionScope(aBase, aActions,
                aEndpoint -> locSnapshot.getOrDefault(aEndpoint.id(), Map.of()), aProgressReporterFactory);
    }

    /** Resolves endpoint credentials lazily within the individual finalizer attempt. */
    public AIcVersionScopePublicationExecutionResult finalizeVersionScope(
            AIcVersionScopePublicationExecutionResult aBase,
            List<AIcVersionScopePublicationFinalizationAction> aActions,
            AIiPublicationCredentialResolver aCredentialResolver,
            AIiPublicationProgressReporterFactory aProgressReporterFactory) {
        Objects.requireNonNull(aBase, "base");
        Objects.requireNonNull(aActions, "actions");
        if (aBase.state() == AInVersionScopePublicationAttemptState.COMPLETE) return aBase;
        if (!aBase.artifacts().stream().allMatch(AIcArtifactPublicationExecutionResult::success)) {
            return new AIcVersionScopePublicationExecutionResult(
                    aBase.versionScopeId(), aBase.version(), aBase.stability(), AInVersionScopePublicationAttemptState.FAILED,
                    aBase.artifacts(), List.of());
        }
        AIcVersionScopePublicationExecutionResult locFinalizing = new AIcVersionScopePublicationExecutionResult(
                aBase.versionScopeId(), aBase.version(), aBase.stability(), AInVersionScopePublicationAttemptState.FINALIZING,
                aBase.artifacts(), List.of());
        List<AIcFinalizationActionExecutionResult> locResults = AIcRunVersionScopeFinalizationActions(
                locFinalizing, aActions, aCredentialResolver, aProgressReporterFactory);
        boolean locRequiredSuccess = locResults.stream().allMatch(AIcPublicationScheduler::AIcRequiredFinalizationSuccess);
        return new AIcVersionScopePublicationExecutionResult(
                aBase.versionScopeId(), aBase.version(), aBase.stability(),
                locRequiredSuccess ? AInVersionScopePublicationAttemptState.COMPLETE : AInVersionScopePublicationAttemptState.FAILED,
                aBase.artifacts(), locResults);
    }

    private AIcOutputRun AIcRunOutput(
            AIcPublicationPayload aRootPayload,
            List<AIcPublicationJob> aJobs,
            List<AIcOutputPublicationFinalizationAction> aOutputFinalizationActions,
            Map<String, AIcPublicationEndpoint> aSnapshotPublicationEndpoints,
            Map<String, CompletableFuture<AIcPublicationResult>> aPublicationResults,
            Map<String, CompletableFuture<AIcFinalizationActionExecutionResult>> aActionResults,
            Map<String, CompletableFuture<Void>> aRequiredSteps,
            AIiPublicationCredentialResolver aCredentialResolver,
            AIiPublicationProgressReporterFactory aProgressReporterFactory) {
        List<AIcPublicationExecutionResult> locExecutions = new ArrayList<>();
        AIcExecutionFailureFlow locFailureFlow = AIcExecutionFailureFlow.AIcNone();
        TreeMap<Integer, List<AIcPublicationJob>> locGroups = new TreeMap<>();
        for (AIcPublicationJob locJob : aJobs) {
            if (locJob.endpoint().executionEnabled()) locGroups.computeIfAbsent(locJob.endpoint().executionOrder(), ignored -> new ArrayList<>()).add(locJob);
        }
        List<CompletableFuture<AIcPublicationRun>> locTrees = new ArrayList<>();
        for (List<AIcPublicationJob> locGroup : locGroups.values()) {
            List<CompletableFuture<AIcPublicationResult>> locAttempts = new ArrayList<>();
            for (AIcPublicationJob locJob : locGroup) {
                CompletableFuture<AIcPublicationResult> locAttempt = CompletableFuture.supplyAsync(
                        () -> AIcRunPublication(locJob.payload(), locJob.endpoint(), aCredentialResolver, aProgressReporterFactory), orchestrationExecutor)
                        .thenApply(aResult -> {
                            aPublicationResults.get(locJob.id()).complete(aResult);
                            AIcCompleteRequiredStep(aRequiredSteps, locJob.id(), aResult.success() || aResult.ignoredFailure() ? null : aResult.failure());
                            if (!aResult.success()) AIcSkipRequiredActions(locJob.publicationFinalizationActions(), locJob.id(), aRequiredSteps);
                            return aResult;
                        });
                locAttempts.add(locAttempt);
                locTrees.add(locAttempt.thenApplyAsync(aResult -> AIcRunPublicationTree(
                        locJob, aResult, aActionResults, aRequiredSteps, aCredentialResolver, aProgressReporterFactory), orchestrationExecutor));
            }
            boolean locFailed = false;
            for (CompletableFuture<AIcPublicationResult> locAttempt : locAttempts) {
                AIcPublicationResult locResult = locAttempt.join();
                if (!locResult.success() && !locResult.ignoredFailure()) locFailed = true;
            }
            if (locFailed) break;
        }
        for (CompletableFuture<AIcPublicationRun> locTree : locTrees) {
            AIcPublicationRun locRun = locTree.join();
            locExecutions.add(locRun.result());
            locFailureFlow = locFailureFlow.AIcMerge(locRun.failureFlow());
        }

        Map<String, AIcPublicationEndpoint> locRegistry = new LinkedHashMap<>();
        for (AIcPublicationJob locJob : aJobs) locRegistry.putAll(locJob.publicationEndpointRegistry());
        String locTechnology = aRootPayload.coordinates().getOrDefault("technologyKind", "unknown");
        AIcOutputPublicationExecutionResult locBase = new AIcOutputPublicationExecutionResult(
                aRootPayload.artifactIdentity(), locTechnology, aRootPayload.outputKind(), aRootPayload.stability(),
                aRootPayload.coordinates().getOrDefault("logicalVersion", aRootPayload.coordinates().getOrDefault("version", aRootPayload.version())),
                locRegistry, aSnapshotPublicationEndpoints, locExecutions, List.of());

        List<AIcFinalizationActionExecutionResult> locOutputFinalizers = List.of();
        if (locFailureFlow.AIcIsNone() && locBase.publications().stream().allMatch(AIcPublicationExecutionResult::success)) {
            locOutputFinalizers = AIcRunOutputFinalizationActions(locBase, aOutputFinalizationActions, aProgressReporterFactory);
            AIcFinalizationActionExecutionResult locFailed = locOutputFinalizers.stream()
                    .filter(locResult -> !AIcRequiredFinalizationSuccess(locResult)).findFirst().orElse(null);
            if (locFailed != null) locFailureFlow = locFailureFlow.AIcMerge(AIcExecutionFailureFlow.AIcPropagated(locFailed.result().failure()));
        }
        AIcOutputPublicationExecutionResult locResult = new AIcOutputPublicationExecutionResult(
                locBase.artifactIdentity(), locBase.technologyKind(), locBase.outputKind(), locBase.stability(), locBase.version(),
                locBase.publicationEndpointRegistry(), locBase.snapshotPublicationEndpoints(), locBase.publications(), locOutputFinalizers);
        return new AIcOutputRun(locResult, locFailureFlow);
    }

    private AIcPublicationRun AIcRunPublicationTree(
            AIcPublicationJob aJob,
            AIcPublicationResult aPublicationResult,
            Map<String, CompletableFuture<AIcFinalizationActionExecutionResult>> aActionResults,
            Map<String, CompletableFuture<Void>> aRequiredSteps,
            AIiPublicationCredentialResolver aCredentialResolver,
            AIiPublicationProgressReporterFactory aProgressReporterFactory) {
        AIcPublicationResult locPublication = aPublicationResult;
        AIcExecutionFailureFlow locFailureFlow = AIcExecutionFailureFlow.AIcNone();
        List<AIcFinalizationActionExecutionResult> locActions = List.of();
        if (!locPublication.success()) {
            locFailureFlow = AIcApplyFailurePolicy(
                    aJob.endpoint().executionFailurePolicy(), AIcExecutionFailureFlow.AIcPropagated(locPublication.failure()));
        } else {
            URI locInputUri = AIcPublicationContent.inputUri(aJob.payload());
            AIcPublicationExecutionStep locRootStep = new AIcPublicationExecutionStep(
                    aJob.id(), "publication", locInputUri, locPublication.outputUri(), aJob.configuration(), locPublication.metadata());
            AIcPublicationExecutionLineage locLineage = new AIcPublicationExecutionLineage(List.of(locRootStep));
            AIcFinalizationTreeRun locTree = AIcRunPublicationFinalizationGroups(
                    aJob, aJob.publicationFinalizationActions(), aJob.endpoint().executionFailurePolicy(), locLineage,
                    locPublication.outputUri() == null ? locInputUri : locPublication.outputUri(), aActionResults,
                    aRequiredSteps, aCredentialResolver, aProgressReporterFactory);
            locActions = locTree.results();
            locFailureFlow = locTree.failureFlow();
        }
        return new AIcPublicationRun(new AIcPublicationExecutionResult(
                aJob.id(), aJob.endpoint(), aJob.payload(), aJob.configuration(), locPublication, locActions,
                locFailureFlow.AIcIsNone()), locFailureFlow);
    }

    private AIcFinalizationTreeRun AIcRunPublicationFinalizationGroups(
            AIcPublicationJob aJob,
            List<AIcPublicationFinalizationAction> aActions,
            AIngBuildExecutionFailurePolicy_1 aParentFailurePolicy,
            AIcPublicationExecutionLineage aLineage,
            URI aInputUri,
            Map<String, CompletableFuture<AIcFinalizationActionExecutionResult>> aObservableResults,
            Map<String, CompletableFuture<Void>> aRequiredSteps,
            AIiPublicationCredentialResolver aCredentialResolver,
            AIiPublicationProgressReporterFactory aProgressReporterFactory) {
        List<CompletableFuture<AIcFinalizationTreeRun>> locTrees = new ArrayList<>();
        TreeMap<Integer, List<AIcPublicationFinalizationAction>> locGroups = new TreeMap<>();
        for (AIcPublicationFinalizationAction locAction : aActions) if (locAction.executionEnabled()) {
            locGroups.computeIfAbsent(locAction.executionOrder(), ignored -> new ArrayList<>()).add(locAction);
        }
        for (List<AIcPublicationFinalizationAction> locGroup : locGroups.values()) {
            List<AIcPublicationFinalizationAction> locScheduledActions = new ArrayList<>();
            List<CompletableFuture<AIcPublicationFinalizationActionResult>> locAttempts = new ArrayList<>();
            for (AIcPublicationFinalizationAction locAction : locGroup) {
                CompletableFuture<AIcPublicationFinalizationActionResult> locAttempt = CompletableFuture.supplyAsync(
                        () -> AIcRunPublicationFinalizationAttempt(aJob, locAction, aLineage, aInputUri,
                                aCredentialResolver, aProgressReporterFactory), orchestrationExecutor).thenApply(aResult -> {
                            String locPath = AIcActionPath(aJob.id(), aLineage, locAction.id());
                            AIcCompleteRequiredStep(aRequiredSteps, locPath,
                                    aResult.success() || aResult.ignoredFailure() ? null : aResult.failure());
                            if (!aResult.success()) AIcSkipRequiredActions(locAction.finalizationActions(), locPath, aRequiredSteps);
                            return aResult;
                        });
                locScheduledActions.add(locAction);
                locAttempts.add(locAttempt);
                locTrees.add(locAttempt.thenApplyAsync(aResult -> AIcRunPublicationFinalizationAction(
                        aJob, locAction, aResult, aLineage, aInputUri, aObservableResults,
                        aRequiredSteps, aCredentialResolver, aProgressReporterFactory), orchestrationExecutor));
            }
            AIcExecutionFailureFlow locImmediateGroupFlow = AIcExecutionFailureFlow.AIcNone();
            for (int locIndex = 0; locIndex < locAttempts.size(); locIndex++) {
                AIcPublicationFinalizationActionResult locResult = locAttempts.get(locIndex).join();
                if (!locResult.success()) {
                    AIcPublicationFinalizationAction locAction = locScheduledActions.get(locIndex);
                    AIcExecutionFailureFlow locActionFlow = AIcApplyFailurePolicy(
                            locAction.executionFailurePolicy(), AIcExecutionFailureFlow.AIcPropagated(locResult.failure()));
                    locImmediateGroupFlow = locImmediateGroupFlow.AIcMerge(locActionFlow);
                }
            }
            AIcExecutionFailureFlow locImmediateParentFlow = AIcApplyFailurePolicy(aParentFailurePolicy, locImmediateGroupFlow);
            if (!locImmediateParentFlow.AIcIsNone()) break;
        }
        List<AIcFinalizationActionExecutionResult> locResults = new ArrayList<>();
        AIcExecutionFailureFlow locTreeFlow = AIcExecutionFailureFlow.AIcNone();
        for (CompletableFuture<AIcFinalizationTreeRun> locTree : locTrees) {
            AIcFinalizationTreeRun locRun = locTree.join();
            locResults.addAll(locRun.results());
            locTreeFlow = locTreeFlow.AIcMerge(locRun.failureFlow());
        }
        return new AIcFinalizationTreeRun(
                List.copyOf(locResults), AIcApplyFailurePolicy(aParentFailurePolicy, locTreeFlow));
    }

    private AIcFinalizationTreeRun AIcRunPublicationFinalizationAction(
            AIcPublicationJob aJob,
            AIcPublicationFinalizationAction aAction,
            AIcPublicationFinalizationActionResult aActionResult,
            AIcPublicationExecutionLineage aLineage,
            URI aInputUri,
            Map<String, CompletableFuture<AIcFinalizationActionExecutionResult>> aObservableResults,
            Map<String, CompletableFuture<Void>> aRequiredSteps,
            AIiPublicationCredentialResolver aCredentialResolver,
            AIiPublicationProgressReporterFactory aProgressReporterFactory) {
        AIcPublicationFinalizationActionResult locActionResult = aActionResult;
        List<AIcFinalizationActionExecutionResult> locChildren = List.of();
        AIcExecutionFailureFlow locFailureFlow;
        if (locActionResult.success()) {
            URI locChildInput = locActionResult.outputUri() == null ? aInputUri : locActionResult.outputUri();
            AIcPublicationExecutionStep locStep = new AIcPublicationExecutionStep(
                    aAction.id(), "publication-finalization-action", aInputUri, locActionResult.outputUri(),
                    aAction.configuration(), locActionResult.metadata());
            AIcFinalizationTreeRun locChildRun = AIcRunPublicationFinalizationGroups(
                    aJob, aAction.finalizationActions(), aAction.executionFailurePolicy(),
                    aLineage.append(locStep), locChildInput, aObservableResults, aRequiredSteps,
                    aCredentialResolver, aProgressReporterFactory);
            locChildren = locChildRun.results();
            locFailureFlow = locChildRun.failureFlow();
        } else {
            locFailureFlow = AIcApplyFailurePolicy(
                    aAction.executionFailurePolicy(), AIcExecutionFailureFlow.AIcPropagated(locActionResult.failure()));
        }
        AIcFinalizationActionExecutionResult locNode = new AIcFinalizationActionExecutionResult(
                aAction.id(), "publication", locActionResult, aAction.configuration(), locChildren, locFailureFlow.AIcIsNone());
        String locPath = AIcActionPath(aJob.id(), aLineage, aAction.id());
        CompletableFuture<AIcFinalizationActionExecutionResult> locObservable = aObservableResults.get(locPath);
        if (locObservable != null) locObservable.complete(locNode);
        return new AIcFinalizationTreeRun(List.of(locNode), locFailureFlow);
    }

    private AIcPublicationFinalizationActionResult AIcRunPublicationFinalizationAttempt(
            AIcPublicationJob aJob,
            AIcPublicationFinalizationAction aAction,
            AIcPublicationExecutionLineage aLineage,
            URI aInputUri,
            AIiPublicationCredentialResolver aCredentialResolver,
            AIiPublicationProgressReporterFactory aProgressReporterFactory) {
        AIiPublicationFinalizationActionAdapter locAdapter;
        try {
            locAdapter = adapterCatalog.requirePublicationFinalizationActionAdapter(aAction.finalizationActionAdapter());
        } catch (Throwable locFailure) {
            return AIcFinalizationFailure(aAction.id(), aAction.executionFailurePolicy(), false, 0, Duration.ZERO, locFailure);
        }
        AIcPublicationEndpoint locTarget = aAction.targetPublicationEndpointId() == null
                ? aJob.endpoint() : aJob.publicationEndpointRegistry().get(aAction.targetPublicationEndpointId());
        if (aAction.targetPublicationEndpointId() != null && locTarget == null) {
            return AIcFinalizationFailure(aAction.id(), aAction.executionFailurePolicy(), false, 0, Duration.ZERO,
                    new IllegalArgumentException("Unknown TargetPublicationEndpointId '" + aAction.targetPublicationEndpointId() + "'."));
        }
        int locMaximumAttempts = aAction.retryCount() + 1;
        Instant locStarted = Instant.now();
        Throwable locFailure = null;
        for (int locAttempt = 1; locAttempt <= locMaximumAttempts; locAttempt++) {
            AtomicBoolean locCancelled = new AtomicBoolean(false);
            Instant locDeadline = aAction.attemptTimeoutMillis() == null ? null : Instant.now().plusMillis(aAction.attemptTimeoutMillis());
            AIiPublicationProgressReporter locProgress = aAction.showProgressIfPossible()
                    ? aProgressReporterFactory.create(locTarget == null ? aJob.endpoint() : locTarget) : AIcNoProgress();
            AIiPublicationDelegate locDelegate = (aPayload, aEndpoint) -> AIcRunPublication(aPayload, aEndpoint, aCredentialResolver, aProgressReporterFactory);
            try {
                AIcPublicationFinalizationActionAttemptContext locContext = new AIcPublicationFinalizationActionAttemptContext(
                        aAction, aLineage, aJob.payload(), aInputUri, locTarget, locAttempt, locMaximumAttempts,
                        aAction.attemptTimeoutMillis(), locDeadline,
                        locTarget == null ? Map.of() : aCredentialResolver.resolve(locTarget),
                        locCancelled::get, locProgress, locDelegate);
                if (aAction.retryCount() > 0 && !locAdapter.isRetrySafe(locContext)) {
                    throw new IllegalArgumentException("Publication finalization adapter '" + locAdapter.adapterId()
                            + "' does not permit automatic retries for action '" + aAction.id() + "'.");
                }
                AIcPublicationFinalizationActionResult locRaw = AIcExecuteFinalizationAttempt(
                        () -> locAdapter.execute(locContext), aAction.attemptTimeoutMillis(), locCancelled,
                        "Publication finalization action attempt timed out.");
                AIcRequireSuccessfulAttempt(locRaw);
                return new AIcPublicationFinalizationActionResult(
                        aAction.id(), true, true, false, locAttempt, Duration.between(locStarted, Instant.now()),
                        locRaw.outputUri(), locRaw.metadata(), null);
            } catch (Throwable locAttemptFailure) {
                locFailure = AIcCause(locAttemptFailure);
                if (locAttempt < locMaximumAttempts) AIcSleep(aAction.waitForNextAttemptMillis());
            }
        }
        return AIcFinalizationFailure(aAction.id(), aAction.executionFailurePolicy(), true, locMaximumAttempts,
                Duration.between(locStarted, Instant.now()), locFailure);
    }

    private List<AIcFinalizationActionExecutionResult> AIcRunOutputFinalizationActions(
            AIcOutputPublicationExecutionResult aBase,
            List<AIcOutputPublicationFinalizationAction> aActions,
            AIiPublicationProgressReporterFactory aProgressReporterFactory) {
        TreeMap<Integer, List<AIcOutputPublicationFinalizationAction>> locGroups = new TreeMap<>();
        for (AIcOutputPublicationFinalizationAction locAction : aActions) if (locAction.executionEnabled()) {
            locGroups.computeIfAbsent(locAction.executionOrder(), ignored -> new ArrayList<>()).add(locAction);
        }
        List<AIcFinalizationActionExecutionResult> locResults = new ArrayList<>();
        for (List<AIcOutputPublicationFinalizationAction> locGroup : locGroups.values()) {
            List<CompletableFuture<AIcFinalizationActionExecutionResult>> locFutures = locGroup.stream().map(locAction ->
                    CompletableFuture.supplyAsync(() -> AIcRunOutputFinalizationAction(aBase, locAction, aProgressReporterFactory), orchestrationExecutor)).toList();
            for (CompletableFuture<AIcFinalizationActionExecutionResult> locFuture : locFutures) locResults.add(locFuture.join());
            if (locResults.stream().anyMatch(locResult -> !AIcRequiredFinalizationSuccess(locResult))) break;
        }
        return List.copyOf(locResults);
    }

    private AIcFinalizationActionExecutionResult AIcRunOutputFinalizationAction(
            AIcOutputPublicationExecutionResult aBase,
            AIcOutputPublicationFinalizationAction aAction,
            AIiPublicationProgressReporterFactory aProgressReporterFactory) {
        AIiOutputPublicationFinalizationActionAdapter locAdapter;
        try {
            locAdapter = adapterCatalog.requireOutputPublicationFinalizationActionAdapter(aAction.outputPublicationFinalizationActionAdapter());
        } catch (Throwable locFailure) {
            return new AIcFinalizationActionExecutionResult(aAction.id(), "output",
                    AIcFinalizationFailure(aAction.id(), aAction.executionFailurePolicy(), false, 0, Duration.ZERO, locFailure),
                    aAction.configuration(), List.of());
        }
        AIcPublicationFinalizationActionResult locResult = AIcRunFlatFinalization(
                aAction.id(), aAction.executionFailurePolicy(), aAction.retryCount(), aAction.waitForNextAttemptMillis(), aAction.attemptTimeoutMillis(),
                (locAttempt, locMaximum, locDeadline, locCancelled) -> {
                    AIcOutputPublicationFinalizationActionAttemptContext locContext = new AIcOutputPublicationFinalizationActionAttemptContext(
                            aAction, aBase, locAttempt, locMaximum, aAction.attemptTimeoutMillis(), locDeadline,
                            locCancelled::get, aAction.showProgressIfPossible() ? AIcProgress(aBase, aProgressReporterFactory) : AIcNoProgress());
                    if (aAction.retryCount() > 0 && !locAdapter.isRetrySafe(locContext)) {
                        throw new IllegalArgumentException("Output finalization adapter '" + locAdapter.adapterId() + "' is not retry-safe.");
                    }
                    return locAdapter.execute(locContext);
                });
        return new AIcFinalizationActionExecutionResult(aAction.id(), "output", locResult, aAction.configuration(), List.of());
    }

    private List<AIcFinalizationActionExecutionResult> AIcRunArtifactFinalizationActions(
            AIcArtifactPublicationExecutionResult aBase,
            List<AIcArtifactPublicationFinalizationAction> aActions,
            AIiPublicationProgressReporterFactory aProgressReporterFactory) {
        TreeMap<Integer, List<AIcArtifactPublicationFinalizationAction>> locGroups = new TreeMap<>();
        for (AIcArtifactPublicationFinalizationAction locAction : aActions) if (locAction.executionEnabled()) {
            locGroups.computeIfAbsent(locAction.executionOrder(), ignored -> new ArrayList<>()).add(locAction);
        }
        List<AIcFinalizationActionExecutionResult> locResults = new ArrayList<>();
        for (List<AIcArtifactPublicationFinalizationAction> locGroup : locGroups.values()) {
            List<CompletableFuture<AIcFinalizationActionExecutionResult>> locFutures = locGroup.stream().map(locAction ->
                    CompletableFuture.supplyAsync(() -> AIcRunArtifactFinalizationAction(aBase, locAction, aProgressReporterFactory), orchestrationExecutor)).toList();
            for (CompletableFuture<AIcFinalizationActionExecutionResult> locFuture : locFutures) locResults.add(locFuture.join());
            if (locResults.stream().anyMatch(locResult -> !AIcRequiredFinalizationSuccess(locResult))) break;
        }
        return List.copyOf(locResults);
    }

    private AIcFinalizationActionExecutionResult AIcRunArtifactFinalizationAction(
            AIcArtifactPublicationExecutionResult aBase,
            AIcArtifactPublicationFinalizationAction aAction,
            AIiPublicationProgressReporterFactory aProgressReporterFactory) {
        AIiArtifactPublicationFinalizationActionAdapter locAdapter;
        try {
            locAdapter = adapterCatalog.requireArtifactPublicationFinalizationActionAdapter(aAction.artifactPublicationFinalizationActionAdapter());
        } catch (Throwable locFailure) {
            return new AIcFinalizationActionExecutionResult(aAction.id(), "artifact",
                    AIcFinalizationFailure(aAction.id(), aAction.executionFailurePolicy(), false, 0, Duration.ZERO, locFailure),
                    aAction.configuration(), List.of());
        }
        AIcPublicationFinalizationActionResult locResult = AIcRunFlatFinalization(
                aAction.id(), aAction.executionFailurePolicy(), aAction.retryCount(), aAction.waitForNextAttemptMillis(), aAction.attemptTimeoutMillis(),
                (locAttempt, locMaximum, locDeadline, locCancelled) -> {
                    AIcArtifactPublicationFinalizationActionAttemptContext locContext = new AIcArtifactPublicationFinalizationActionAttemptContext(
                            aAction, aBase, locAttempt, locMaximum, aAction.attemptTimeoutMillis(), locDeadline,
                            locCancelled::get, aAction.showProgressIfPossible() ? AIcProgress(aBase, aProgressReporterFactory) : AIcNoProgress());
                    if (aAction.retryCount() > 0 && !locAdapter.isRetrySafe(locContext)) {
                        throw new IllegalArgumentException("Artifact finalization adapter '" + locAdapter.adapterId() + "' is not retry-safe.");
                    }
                    return locAdapter.execute(locContext);
                });
        return new AIcFinalizationActionExecutionResult(aAction.id(), "artifact", locResult, aAction.configuration(), List.of());
    }

    private List<AIcFinalizationActionExecutionResult> AIcRunVersionScopeFinalizationActions(
            AIcVersionScopePublicationExecutionResult aBase,
            List<AIcVersionScopePublicationFinalizationAction> aActions,
            AIiPublicationCredentialResolver aCredentialResolver,
            AIiPublicationProgressReporterFactory aProgressReporterFactory) {
        TreeMap<Integer, List<AIcVersionScopePublicationFinalizationAction>> locGroups = new TreeMap<>();
        for (AIcVersionScopePublicationFinalizationAction locAction : aActions) if (locAction.executionEnabled()) {
            locGroups.computeIfAbsent(locAction.executionOrder(), ignored -> new ArrayList<>()).add(locAction);
        }
        List<AIcFinalizationActionExecutionResult> locResults = new ArrayList<>();
        for (List<AIcVersionScopePublicationFinalizationAction> locGroup : locGroups.values()) {
            List<CompletableFuture<AIcFinalizationActionExecutionResult>> locFutures = locGroup.stream().map(locAction ->
                    CompletableFuture.supplyAsync(() -> AIcRunVersionScopeFinalizationAction(
                            aBase, locAction, aCredentialResolver, aProgressReporterFactory), orchestrationExecutor)).toList();
            for (CompletableFuture<AIcFinalizationActionExecutionResult> locFuture : locFutures) locResults.add(locFuture.join());
            if (locResults.stream().anyMatch(locResult -> !AIcRequiredFinalizationSuccess(locResult))) break;
        }
        return List.copyOf(locResults);
    }

    private AIcFinalizationActionExecutionResult AIcRunVersionScopeFinalizationAction(
            AIcVersionScopePublicationExecutionResult aBase,
            AIcVersionScopePublicationFinalizationAction aAction,
            AIiPublicationCredentialResolver aCredentialResolver,
            AIiPublicationProgressReporterFactory aProgressReporterFactory) {
        AIiVersionScopePublicationFinalizationActionAdapter locAdapter;
        try {
            locAdapter = adapterCatalog.requireVersionScopePublicationFinalizationActionAdapter(aAction.versionScopePublicationFinalizationActionAdapter());
        } catch (Throwable locFailure) {
            return new AIcFinalizationActionExecutionResult(aAction.id(), "version-scope",
                    AIcFinalizationFailure(aAction.id(), aAction.executionFailurePolicy(), false, 0, Duration.ZERO, locFailure),
                    aAction.configuration(), List.of());
        }
        AIcPublicationFinalizationActionResult locResult = AIcRunFlatFinalization(
                aAction.id(), aAction.executionFailurePolicy(), aAction.retryCount(), aAction.waitForNextAttemptMillis(), aAction.attemptTimeoutMillis(),
                (locAttempt, locMaximum, locDeadline, locCancelled) -> {
                    AIcVersionScopePublicationFinalizationActionAttemptContext locContext = new AIcVersionScopePublicationFinalizationActionAttemptContext(
                            aAction, aBase, locAttempt, locMaximum, aAction.attemptTimeoutMillis(), locDeadline,
                            Map.of(), locCancelled::get, aAction.showProgressIfPossible() ? AIcProgress(aBase, aProgressReporterFactory) : AIcNoProgress(),
                            aCredentialResolver);
                    if (aAction.retryCount() > 0 && !locAdapter.isRetrySafe(locContext)) {
                        throw new IllegalArgumentException("Version Scope finalization adapter '" + locAdapter.adapterId() + "' is not retry-safe.");
                    }
                    return locAdapter.execute(locContext);
                });
        return new AIcFinalizationActionExecutionResult(aAction.id(), "version-scope", locResult, aAction.configuration(), List.of());
    }

    private AIcPublicationFinalizationActionResult AIcRunFlatFinalization(
            String aId, AIngBuildExecutionFailurePolicy_1 aFailurePolicy, int aRetryCount,
            long aWaitMillis, Long aTimeoutMillis, AIiFlatFinalizationAttempt aAttempt) {
        int locMaximumAttempts = aRetryCount + 1;
        Instant locStarted = Instant.now();
        Throwable locFailure = null;
        for (int locAttempt = 1; locAttempt <= locMaximumAttempts; locAttempt++) {
            final int locAttemptNumber = locAttempt;
            AtomicBoolean locCancelled = new AtomicBoolean(false);
            Instant locDeadline = aTimeoutMillis == null ? null : Instant.now().plusMillis(aTimeoutMillis);
            try {
                AIcPublicationFinalizationActionResult locRaw = AIcExecuteFinalizationAttempt(
                        () -> aAttempt.execute(locAttemptNumber, locMaximumAttempts, locDeadline, locCancelled),
                        aTimeoutMillis, locCancelled, "Finalization action attempt timed out.");
                AIcRequireSuccessfulAttempt(locRaw);
                return new AIcPublicationFinalizationActionResult(
                        aId, true, true, false, locAttempt, Duration.between(locStarted, Instant.now()),
                        locRaw.outputUri(), locRaw.metadata(), null);
            } catch (Throwable locAttemptFailure) {
                locFailure = AIcCause(locAttemptFailure);
                if (locAttempt < locMaximumAttempts) AIcSleep(aWaitMillis);
            }
        }
        return AIcFinalizationFailure(aId, aFailurePolicy, true, locMaximumAttempts,
                Duration.between(locStarted, Instant.now()), locFailure);
    }

    private AIcPublicationResult AIcRunPublication(
            AIcPublicationPayload aPayload,
            AIcPublicationEndpoint aEndpoint,
            AIiPublicationCredentialResolver aCredentialResolver,
            AIiPublicationProgressReporterFactory aProgressReporterFactory) {
        Instant locStarted = Instant.now();
        int locMaximumAttempts = aEndpoint.publicationRetryCount() + 1;
        Throwable locFailure = null;
        AIiPublicationAdapter locAdapter;
        try {
            locAdapter = adapterCatalog.requirePublicationAdapter(aEndpoint.publicationAdapter());
            if (aEndpoint.publicationRetryCount() > 0 && !locAdapter.isRetrySafe(aPayload, aEndpoint)) {
                throw new IllegalArgumentException("Publication adapter '" + aEndpoint.publicationAdapter() + "' does not permit automatic retries.");
            }
        } catch (Throwable locAdapterFailure) {
            boolean locIgnored = aEndpoint.executionFailurePolicy() == AIngBuildExecutionFailurePolicy_1.IGNORE_FAILURE;
            return new AIcPublicationResult(aEndpoint.id(), false, false, locIgnored, 0, Duration.ZERO, null, Map.of(), AIcCause(locAdapterFailure));
        }
        for (int locAttempt = 1; locAttempt <= locMaximumAttempts; locAttempt++) {
            AtomicBoolean locCancelled = new AtomicBoolean(false);
            Instant locDeadline = aEndpoint.publicationAttemptTimeoutMillis() == null
                    ? null : Instant.now().plusMillis(aEndpoint.publicationAttemptTimeoutMillis());
            AIiPublicationProgressReporter locProgress = aEndpoint.showPublicationProgressIfPossible()
                    ? aProgressReporterFactory.create(aEndpoint) : AIcNoProgress();
            try {
                AIcPublicationAttemptContext locContext = new AIcPublicationAttemptContext(
                        aEndpoint, aPayload, locAttempt, locMaximumAttempts, aEndpoint.publicationAttemptTimeoutMillis(), locDeadline,
                        aCredentialResolver.resolve(aEndpoint), locCancelled::get, locProgress);
                Future<?> locFuture = attemptExecutor.submit(() -> {
                    try { locAdapter.publish(locContext); }
                    catch (Exception locFailureValue) { throw new CompletionException(locFailureValue); }
                });
                AIcAwaitVoid(locFuture, aEndpoint.publicationAttemptTimeoutMillis(), locCancelled, "Publication attempt timed out.");
                URI locOutputUri = AIcPublicationContent.publishedUri(aPayload, aEndpoint);
                return new AIcPublicationResult(
                        aEndpoint.id(), true, true, false, locAttempt, Duration.between(locStarted, Instant.now()),
                        locOutputUri, Map.of("PublicationAdapter", aEndpoint.publicationAdapter(), "PublicationEndpointId", aEndpoint.id()), null);
            } catch (Throwable locAttemptFailure) {
                locFailure = AIcCause(locAttemptFailure);
                if (locAttempt < locMaximumAttempts) AIcSleep(aEndpoint.publicationWaitForNextAttemptMillis());
            }
        }
        boolean locIgnored = aEndpoint.executionFailurePolicy() == AIngBuildExecutionFailurePolicy_1.IGNORE_FAILURE;
        return new AIcPublicationResult(
                aEndpoint.id(), true, false, locIgnored, locMaximumAttempts, Duration.between(locStarted, Instant.now()),
                null, Map.of("PublicationAdapter", aEndpoint.publicationAdapter(), "PublicationEndpointId", aEndpoint.id()), locFailure);
    }

    private AIcPublicationFinalizationActionResult AIcExecuteFinalizationAttempt(
            Callable<AIcPublicationFinalizationActionResult> aAttempt,
            Long aTimeoutMillis,
            AtomicBoolean aCancelled,
            String aTimeoutMessage) throws Exception {
        Future<AIcPublicationFinalizationActionResult> locFuture = attemptExecutor.submit(aAttempt);
        return AIcAwait(locFuture, aTimeoutMillis, aCancelled, aTimeoutMessage);
    }

    private static AIcPublicationFinalizationActionResult AIcFinalizationFailure(
            String aId, AIngBuildExecutionFailurePolicy_1 aFailurePolicy, boolean aStarted, int aAttempts,
            Duration aDuration, Throwable aFailure) {
        return new AIcPublicationFinalizationActionResult(
                aId, aStarted, false, aFailurePolicy == AIngBuildExecutionFailurePolicy_1.IGNORE_FAILURE,
                aAttempts, aDuration, null, Map.of(), aFailure);
    }

    private static void AIcRequireSuccessfulAttempt(AIcPublicationFinalizationActionResult aResult) throws Exception {
        if (aResult == null) throw new IllegalStateException("Finalization adapter returned no result.");
        if (!aResult.success()) {
            Throwable locFailure = aResult.failure();
            if (locFailure instanceof Exception locException) throw locException;
            if (locFailure instanceof Error locError) throw locError;
            throw new IllegalStateException("Finalization adapter returned an unsuccessful result.", locFailure);
        }
    }

    private static boolean AIcRequiredFinalizationSuccess(AIcFinalizationActionExecutionResult aResult) {
        return aResult.failureHandled();
    }

    private static AIiPublicationProgressReporter AIcProgress(
            AIcOutputPublicationExecutionResult aExecution,
            AIiPublicationProgressReporterFactory aFactory) {
        AIcPublicationEndpoint locEndpoint = aExecution.publicationEndpointRegistry().values().stream().findFirst().orElse(null);
        return locEndpoint == null ? AIcNoProgress() : aFactory.create(locEndpoint);
    }

    private static AIiPublicationProgressReporter AIcProgress(
            AIcArtifactPublicationExecutionResult aExecution,
            AIiPublicationProgressReporterFactory aFactory) {
        for (AIcOutputPublicationExecutionResult locOutput : aExecution.outputs()) {
            if (!locOutput.publicationEndpointRegistry().isEmpty()) return aFactory.create(locOutput.publicationEndpointRegistry().values().iterator().next());
        }
        return AIcNoProgress();
    }

    private static AIiPublicationProgressReporter AIcProgress(
            AIcVersionScopePublicationExecutionResult aExecution,
            AIiPublicationProgressReporterFactory aFactory) {
        for (AIcArtifactPublicationExecutionResult locArtifact : aExecution.artifacts()) {
            AIiPublicationProgressReporter locReporter = AIcProgress(locArtifact, aFactory);
            if (locReporter != null) return locReporter;
        }
        return AIcNoProgress();
    }

    private static AIiPublicationProgressReporter AIcNoProgress() {
        return new AIiPublicationProgressReporter() {
            @Override public void started(String aMessage) { }
            @Override public void progress(long aCompleted, long aTotal, String aUnit, String aMessage) { }
            @Override public void indeterminate(String aMessage) { }
            @Override public void completed(String aMessage) { }
        };
    }

    private static void AIcRegisterRequiredActions(
            List<AIcPublicationFinalizationAction> aActions,
            String aPrefix,
            List<AIngBuildExecutionFailurePolicy_1> aAncestorPolicies,
            Map<String, CompletableFuture<Void>> aRequiredSteps) {
        for (AIcPublicationFinalizationAction locAction : aActions) {
            if (!locAction.executionEnabled()) continue;
            String locPath = aPrefix + "/" + locAction.id();
            if (AIcFailureCanReachBuild(locAction.executionFailurePolicy(), aAncestorPolicies)) {
                aRequiredSteps.put(locPath, new CompletableFuture<>());
            }
            ArrayList<AIngBuildExecutionFailurePolicy_1> locChildAncestors = new ArrayList<>();
            locChildAncestors.add(locAction.executionFailurePolicy());
            locChildAncestors.addAll(aAncestorPolicies);
            AIcRegisterRequiredActions(
                    locAction.finalizationActions(), locPath, List.copyOf(locChildAncestors), aRequiredSteps);
        }
    }

    private static boolean AIcFailureCanReachBuild(
            AIngBuildExecutionFailurePolicy_1 aPolicy,
            List<AIngBuildExecutionFailurePolicy_1> aAncestorPolicies) {
        if (aPolicy == AIngBuildExecutionFailurePolicy_1.FAIL_BUILD_ON_FAILURE) return true;
        if (aPolicy == AIngBuildExecutionFailurePolicy_1.IGNORE_FAILURE) return false;
        for (AIngBuildExecutionFailurePolicy_1 locPolicy : aAncestorPolicies) {
            if (locPolicy == AIngBuildExecutionFailurePolicy_1.FAIL_BUILD_ON_FAILURE) return true;
            if (locPolicy == AIngBuildExecutionFailurePolicy_1.IGNORE_FAILURE) return false;
        }
        return true;
    }

    private static void AIcCompleteRequiredStep(Map<String, CompletableFuture<Void>> aRequiredSteps, String aPath, Throwable aFailure) {
        CompletableFuture<Void> locFuture = aRequiredSteps.get(aPath);
        if (locFuture != null) {
            if (aFailure == null) locFuture.complete(null); else locFuture.completeExceptionally(aFailure);
        }
    }

    private static void AIcSkipRequiredActions(
            List<AIcPublicationFinalizationAction> aActions, String aPrefix, Map<String, CompletableFuture<Void>> aRequiredSteps) {
        for (AIcPublicationFinalizationAction locAction : aActions) {
            String locPath = aPrefix + "/" + locAction.id();
            AIcCompleteRequiredStep(aRequiredSteps, locPath, null);
            AIcSkipRequiredActions(locAction.finalizationActions(), locPath, aRequiredSteps);
        }
    }

    private static void AIcValidatePublicationFinalizationActions(
            List<AIcPublicationFinalizationAction> aActions,
            String aPrefix,
            Map<String, CompletableFuture<AIcFinalizationActionExecutionResult>> aResults) {
        for (AIcPublicationFinalizationAction locAction : aActions) {
            String locPath = aPrefix + "/" + locAction.id();
            if (aResults.putIfAbsent(locPath, new CompletableFuture<>()) != null) {
                throw new IllegalArgumentException("Duplicate publication finalization action path '" + locPath + "'.");
            }
            AIcValidatePublicationFinalizationActions(locAction.finalizationActions(), locPath, aResults);
        }
    }

    private static String AIcActionPath(String aRootId, AIcPublicationExecutionLineage aLineage, String aActionId) {
        StringBuilder locPath = new StringBuilder(aRootId);
        List<AIcPublicationExecutionStep> locSteps = aLineage.steps();
        for (int locIndex = 1; locIndex < locSteps.size(); locIndex++) locPath.append('/').append(locSteps.get(locIndex).id());
        return locPath.append('/').append(aActionId).toString();
    }

    private static <T> T AIcAwait(Future<T> aFuture, Long aTimeoutMillis, AtomicBoolean aCancelled, String aTimeoutMessage) throws Exception {
        try {
            return aTimeoutMillis == null ? aFuture.get() : aFuture.get(aTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (ExecutionException locFailure) {
            Throwable locCause = AIcCause(locFailure);
            if (locCause instanceof Exception locException) throw locException;
            if (locCause instanceof Error locError) throw locError;
            throw new RuntimeException(locCause);
        } catch (TimeoutException locFailure) {
            aCancelled.set(true);
            aFuture.cancel(true);
            throw new TimeoutException(aTimeoutMessage);
        }
    }

    private static void AIcAwaitVoid(Future<?> aFuture, Long aTimeoutMillis, AtomicBoolean aCancelled, String aTimeoutMessage) throws Exception {
        try {
            if (aTimeoutMillis == null) aFuture.get(); else aFuture.get(aTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (ExecutionException locFailure) {
            Throwable locCause = AIcCause(locFailure);
            if (locCause instanceof Exception locException) throw locException;
            if (locCause instanceof Error locError) throw locError;
            throw new RuntimeException(locCause);
        } catch (TimeoutException locFailure) {
            aCancelled.set(true);
            aFuture.cancel(true);
            throw new TimeoutException(aTimeoutMessage);
        }
    }

    private static void AIcSleep(long aMillis) {
        if (aMillis <= 0L) return;
        try { Thread.sleep(aMillis); }
        catch (InterruptedException locFailure) { Thread.currentThread().interrupt(); throw new CompletionException(locFailure); }
    }

    private static Throwable AIcCause(Throwable aFailure) {
        Throwable locFailure = aFailure;
        while ((locFailure instanceof CompletionException || locFailure instanceof ExecutionException) && locFailure.getCause() != null) {
            locFailure = locFailure.getCause();
        }
        return locFailure;
    }

    private static ThreadFactory AIcThreadFactory(String aPrefix) {
        return new ThreadFactory() {
            private int counter;
            @Override public synchronized Thread newThread(Runnable aRunnable) {
                Thread locThread = new Thread(aRunnable, aPrefix + "-" + (++counter));
                locThread.setDaemon(true);
                return locThread;
            }
        };
    }

    @Override public void close() {
        if (!closing.compareAndSet(false, true)) return;
        try {
            CompletableFuture.allOf(activeOutputs.toArray(CompletableFuture[]::new)).join();
        } finally {
            if (ownsExecutors) {
                orchestrationExecutor.shutdownNow();
                attemptExecutor.shutdownNow();
            }
        }
    }

    private interface AIiFlatFinalizationAttempt {
        AIcPublicationFinalizationActionResult execute(int aAttempt, int aMaximumAttempts, Instant aDeadline, AtomicBoolean aCancelled) throws Exception;
    }

    private enum AInExecutionFailureFlowState { NONE, PROPAGATED_FAILURE, BUILD_FAILURE }

    private record AIcExecutionFailureFlow(AInExecutionFailureFlowState state, List<Throwable> failures) {
        private AIcExecutionFailureFlow {
            Objects.requireNonNull(state, "state");
            failures = List.copyOf(failures == null ? List.of() : failures);
        }

        static AIcExecutionFailureFlow AIcNone() {
            return new AIcExecutionFailureFlow(AInExecutionFailureFlowState.NONE, List.of());
        }

        static AIcExecutionFailureFlow AIcPropagated(Throwable aFailure) {
            return aFailure == null ? AIcNone()
                    : new AIcExecutionFailureFlow(AInExecutionFailureFlowState.PROPAGATED_FAILURE, List.of(aFailure));
        }

        boolean AIcIsNone() {
            return state == AInExecutionFailureFlowState.NONE;
        }

        AIcExecutionFailureFlow AIcMerge(AIcExecutionFailureFlow aOther) {
            if (aOther == null || aOther.AIcIsNone()) return this;
            if (AIcIsNone()) return aOther;
            ArrayList<Throwable> locFailures = new ArrayList<>(failures);
            locFailures.addAll(aOther.failures);
            AInExecutionFailureFlowState locState = state == AInExecutionFailureFlowState.BUILD_FAILURE
                    || aOther.state == AInExecutionFailureFlowState.BUILD_FAILURE
                    ? AInExecutionFailureFlowState.BUILD_FAILURE : AInExecutionFailureFlowState.PROPAGATED_FAILURE;
            return new AIcExecutionFailureFlow(locState, locFailures);
        }

        Throwable AIcAsThrowable() {
            if (failures.isEmpty()) return new IllegalStateException("Build execution failed without a recorded cause.");
            if (failures.size() == 1) return failures.get(0);
            IllegalStateException locCombined = new IllegalStateException("Multiple build execution failures occurred.");
            failures.forEach(locCombined::addSuppressed);
            return locCombined;
        }
    }

    private static AIcExecutionFailureFlow AIcApplyFailurePolicy(
            AIngBuildExecutionFailurePolicy_1 aPolicy, AIcExecutionFailureFlow aFailureFlow) {
        Objects.requireNonNull(aPolicy, "policy");
        if (aFailureFlow == null || aFailureFlow.AIcIsNone()) return AIcExecutionFailureFlow.AIcNone();
        if (aFailureFlow.state() == AInExecutionFailureFlowState.BUILD_FAILURE) return aFailureFlow;
        return switch (aPolicy) {
            case FAIL_BUILD_ON_FAILURE -> new AIcExecutionFailureFlow(
                    AInExecutionFailureFlowState.BUILD_FAILURE, aFailureFlow.failures());
            case PROPAGATE_FAILURE -> aFailureFlow;
            case IGNORE_FAILURE -> AIcExecutionFailureFlow.AIcNone();
        };
    }

    private record AIcPublicationRun(AIcPublicationExecutionResult result, AIcExecutionFailureFlow failureFlow) { }
    private record AIcFinalizationTreeRun(List<AIcFinalizationActionExecutionResult> results, AIcExecutionFailureFlow failureFlow) { }
    private record AIcOutputRun(AIcOutputPublicationExecutionResult result, AIcExecutionFailureFlow failureFlow) { }
}
