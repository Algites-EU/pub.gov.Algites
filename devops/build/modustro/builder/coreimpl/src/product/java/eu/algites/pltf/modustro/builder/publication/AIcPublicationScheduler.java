package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPostPublicationAction;
import eu.algites.pltf.modustro.builder.model.publication.AIcPostPublicationActionResult;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationEndpoint;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationExecutionLineage;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationExecutionStep;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayload;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationResult;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationStabilityConfiguration;
import eu.algites.pltf.modustro.builder.model.publication.AInPostPublicationActionFailurePolicy;
import eu.algites.pltf.modustro.builder.model.publication.AInPublicationFailurePolicy;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Executes root publications and recursive post-publication action trees. */
public final class AIcPublicationScheduler implements AutoCloseable {
    private final Map<String, AIiPublicationAdapter> publicationAdapters;
    private final Map<String, AIiPostPublicationActionAdapter> postPublicationActionAdapters;
    private final ExecutorService orchestrationExecutor;
    private final ExecutorService attemptExecutor;
    private final boolean ownsExecutors;

    /** Creates a scheduler containing only publication adapters. */
    public AIcPublicationScheduler(List<? extends AIiPublicationAdapter> aPublicationAdapters) {
        this(aPublicationAdapters, List.of());
    }

    /** Creates a scheduler with publication and post-publication action adapters. */
    public AIcPublicationScheduler(
            List<? extends AIiPublicationAdapter> aPublicationAdapters,
            List<? extends AIiPostPublicationActionAdapter> aPostPublicationActionAdapters) {
        this(aPublicationAdapters, aPostPublicationActionAdapters,
                Executors.newCachedThreadPool(AIcThreadFactory("modustro-publication-orchestration")),
                Executors.newCachedThreadPool(AIcThreadFactory("modustro-publication-attempt")), true);
    }

    /** Creates a scheduler using explicit executors, primarily for tests and embedding. */
    public AIcPublicationScheduler(
            List<? extends AIiPublicationAdapter> aPublicationAdapters,
            List<? extends AIiPostPublicationActionAdapter> aPostPublicationActionAdapters,
            ExecutorService aOrchestrationExecutor,
            ExecutorService aAttemptExecutor) {
        this(aPublicationAdapters, aPostPublicationActionAdapters, aOrchestrationExecutor, aAttemptExecutor, false);
    }

    private AIcPublicationScheduler(
            List<? extends AIiPublicationAdapter> aPublicationAdapters,
            List<? extends AIiPostPublicationActionAdapter> aPostPublicationActionAdapters,
            ExecutorService aOrchestrationExecutor,
            ExecutorService aAttemptExecutor,
            boolean aOwnsExecutors) {
        publicationAdapters = AIcPublicationAdapters(aPublicationAdapters);
        postPublicationActionAdapters = AIcActionAdapters(aPostPublicationActionAdapters);
        orchestrationExecutor = Objects.requireNonNull(aOrchestrationExecutor, "orchestrationExecutor");
        attemptExecutor = Objects.requireNonNull(aAttemptExecutor, "attemptExecutor");
        ownsExecutors = aOwnsExecutors;
    }

    /** Schedules one payload against a simple stability configuration without post-actions. */
    public AIcPublicationScheduleHandle schedule(
            AIcPublicationPayload aPayload,
            AIcPublicationStabilityConfiguration aConfiguration,
            AIiPublicationCredentialResolver aCredentialResolver,
            AIiPublicationProgressReporterFactory aProgressReporterFactory) {
        Objects.requireNonNull(aConfiguration, "configuration");
        if (!aConfiguration.publicationEnabled()) {
            return new AIcPublicationScheduleHandle(CompletableFuture.completedFuture(null), Map.of(), Map.of());
        }
        List<AIcPublicationJob> locJobs = new ArrayList<>();
        Map<String, AIcPublicationEndpoint> locRegistry = new LinkedHashMap<>();
        for (AIcPublicationEndpoint locEndpoint : aConfiguration.publicationEndpoints()) {
            locRegistry.put(locEndpoint.id(), locEndpoint);
        }
        for (AIcPublicationEndpoint locEndpoint : aConfiguration.publicationEndpoints()) {
            if (locEndpoint.enabled()) {
                locJobs.add(new AIcPublicationJob(locEndpoint.id(), locEndpoint, aPayload, Map.of(), List.of(), locRegistry));
            }
        }
        return schedule(locJobs, aCredentialResolver, aProgressReporterFactory);
    }

    /** Schedules independent root publications; each root owns a recursive post-action tree. */
    public AIcPublicationScheduleHandle schedule(
            List<AIcPublicationJob> aJobs,
            AIiPublicationCredentialResolver aCredentialResolver,
            AIiPublicationProgressReporterFactory aProgressReporterFactory) {
        Objects.requireNonNull(aJobs, "jobs");
        Objects.requireNonNull(aCredentialResolver, "credentialResolver");
        Objects.requireNonNull(aProgressReporterFactory, "progressReporterFactory");
        LinkedHashMap<String, CompletableFuture<AIcPublicationResult>> locPublicationResults = new LinkedHashMap<>();
        LinkedHashMap<String, CompletableFuture<AIcPostPublicationActionResult>> locActionResults = new LinkedHashMap<>();
        TreeMap<Integer, List<AIcPublicationJob>> locRootGroups = new TreeMap<>();
        for (AIcPublicationJob locJob : aJobs) {
            if (!locJob.endpoint().enabled()) {
                continue;
            }
            if (locPublicationResults.putIfAbsent(locJob.id(), new CompletableFuture<>()) != null) {
                throw new IllegalArgumentException("Duplicate root publication id '" + locJob.id() + "'.");
            }
            AIiPublicationAdapter locAdapter = AIcRequirePublicationAdapter(locJob.endpoint());
            if (locJob.endpoint().publicationRetryCount() > 0 && !locAdapter.isRetrySafe(locJob.payload(), locJob.endpoint())) {
                throw new IllegalArgumentException("Publication adapter '" + locJob.endpoint().publicationAdapter()
                        + "' does not permit automatic retries for '" + locJob.id() + "'.");
            }
            AIcValidateActions(locJob.postPublicationActions(), locJob.id(), locActionResults);
            locRootGroups.computeIfAbsent(locJob.endpoint().publicationOrder(), aIgnored -> new ArrayList<>()).add(locJob);
        }
        CompletableFuture<Void> locRequiredCompletion = new CompletableFuture<>();
        orchestrationExecutor.execute(() -> AIcRunRootGroups(
                locRootGroups, locPublicationResults, locActionResults, locRequiredCompletion,
                aCredentialResolver, aProgressReporterFactory));
        return new AIcPublicationScheduleHandle(locRequiredCompletion, locPublicationResults, locActionResults);
    }

    private void AIcRunRootGroups(
            TreeMap<Integer, List<AIcPublicationJob>> aGroups,
            Map<String, CompletableFuture<AIcPublicationResult>> aPublicationResults,
            Map<String, CompletableFuture<AIcPostPublicationActionResult>> aActionResults,
            CompletableFuture<Void> aRequiredCompletion,
            AIiPublicationCredentialResolver aCredentialResolver,
            AIiPublicationProgressReporterFactory aProgressReporterFactory) {
        Throwable locRequiredFailure = null;
        List<CompletableFuture<Void>> locDescendantSubtrees = new ArrayList<>();
        try {
            for (List<AIcPublicationJob> locGroup : aGroups.values()) {
                List<CompletableFuture<CompletableFuture<Void>>> locRootExecutions = new ArrayList<>();
                for (AIcPublicationJob locJob : locGroup) {
                    CompletableFuture<CompletableFuture<Void>> locRootExecution = CompletableFuture.supplyAsync(() -> {
                        AIcPublicationResult locResult = AIcRunPublication(
                                locJob.payload(), locJob.endpoint(), aCredentialResolver, aProgressReporterFactory);
                        aPublicationResults.get(locJob.id()).complete(locResult);
                        if (!locResult.success()) {
                            if (locJob.endpoint().publicationFailurePolicy() == AInPublicationFailurePolicy.FAIL_BUILD_ON_PUBLICATION_FAILURE) {
                                throw new CompletionException(locResult.failure());
                            }
                            return CompletableFuture.completedFuture(null);
                        }
                        URI locInputUri = AIcPublicationContent.inputUri(locJob.payload());
                        AIcPublicationExecutionStep locRootStep = new AIcPublicationExecutionStep(
                                locJob.id(), "publication", locInputUri, locResult.outputUri(),
                                locJob.configuration(), locResult.metadata());
                        AIcPublicationExecutionLineage locLineage = new AIcPublicationExecutionLineage(List.of(locRootStep));
                        return AIcRunActionGroups(
                                locJob, locJob.postPublicationActions(), locLineage,
                                locResult.outputUri() == null ? locInputUri : locResult.outputUri(),
                                aActionResults, aCredentialResolver, aProgressReporterFactory);
                    }, orchestrationExecutor);
                    locRootExecutions.add(locRootExecution);
                }

                /* PublicationOrder is a barrier only between direct root publications. Descendant post-actions
                 * belong to their parent tree and never delay eligibility of a later root PublicationOrder group. */
                try {
                    CompletableFuture.allOf(locRootExecutions.toArray(CompletableFuture[]::new)).join();
                    for (CompletableFuture<CompletableFuture<Void>> locRootExecution : locRootExecutions) {
                        locDescendantSubtrees.add(locRootExecution.join());
                    }
                } catch (CompletionException locFailure) {
                    if (locRequiredFailure == null) {
                        locRequiredFailure = AIcCause(locFailure);
                    }
                }
                if (locRequiredFailure != null) {
                    break;
                }
            }

            if (locRequiredFailure == null) {
                try {
                    CompletableFuture.allOf(locDescendantSubtrees.toArray(CompletableFuture[]::new)).join();
                } catch (CompletionException locFailure) {
                    locRequiredFailure = AIcCause(locFailure);
                }
            }
            if (locRequiredFailure == null) {
                aRequiredCompletion.complete(null);
            } else {
                aRequiredCompletion.completeExceptionally(locRequiredFailure);
            }
        } catch (Throwable locFailure) {
            aRequiredCompletion.completeExceptionally(AIcCause(locFailure));
        }
    }

    private CompletableFuture<Void> AIcRunActionGroups(
            AIcPublicationJob aJob,
            List<AIcPostPublicationAction> aActions,
            AIcPublicationExecutionLineage aLineage,
            URI aInputUri,
            Map<String, CompletableFuture<AIcPostPublicationActionResult>> aActionResults,
            AIiPublicationCredentialResolver aCredentialResolver,
            AIiPublicationProgressReporterFactory aProgressReporterFactory) {
        return CompletableFuture.runAsync(() -> {
            TreeMap<Integer, List<AIcPostPublicationAction>> locGroups = new TreeMap<>();
            for (AIcPostPublicationAction locAction : aActions) {
                if (locAction.enabled()) {
                    locGroups.computeIfAbsent(locAction.order(), aIgnored -> new ArrayList<>()).add(locAction);
                }
            }
            List<CompletableFuture<Void>> locDescendantSubtrees = new ArrayList<>();
            for (List<AIcPostPublicationAction> locGroup : locGroups.values()) {
                List<CompletableFuture<CompletableFuture<Void>>> locDirectActions = new ArrayList<>();
                for (AIcPostPublicationAction locAction : locGroup) {
                    String locPath = AIcActionPath(aJob.id(), aLineage, locAction.id());
                    CompletableFuture<CompletableFuture<Void>> locDirectAction = CompletableFuture.supplyAsync(() -> {
                        AIcPostPublicationActionResult locResult = AIcRunAction(
                                aJob, locAction, aLineage, aInputUri, aCredentialResolver, aProgressReporterFactory);
                        CompletableFuture<AIcPostPublicationActionResult> locResultFuture = aActionResults.get(locPath);
                        if (locResultFuture != null) {
                            locResultFuture.complete(locResult);
                        }
                        if (!locResult.success()) {
                            if (locAction.failurePolicy() == AInPostPublicationActionFailurePolicy.FAIL_BUILD_ON_FAILURE) {
                                throw new CompletionException(locResult.failure());
                            }
                            return CompletableFuture.completedFuture(null);
                        }
                        URI locOutputUri = locResult.outputUri() == null ? aInputUri : locResult.outputUri();
                        AIcPublicationExecutionStep locStep = new AIcPublicationExecutionStep(
                                locAction.id(), "post-publication-action", aInputUri, locResult.outputUri(),
                                locAction.configuration(), locResult.metadata());
                        AIcPublicationExecutionLineage locChildLineage = aLineage.append(locStep);
                        return AIcRunActionGroups(
                                aJob, locAction.postPublicationActions(), locChildLineage, locOutputUri,
                                aActionResults, aCredentialResolver, aProgressReporterFactory);
                    }, orchestrationExecutor);
                    locDirectActions.add(locDirectAction);
                }

                /* Order is local to direct siblings. A later sibling-order group waits for the direct actions
                 * in the previous group, never for those actions' descendants. The tree itself expresses
                 * descendant dependencies and every child still has a hard parent-completion barrier. */
                CompletableFuture.allOf(locDirectActions.toArray(CompletableFuture[]::new)).join();
                for (CompletableFuture<CompletableFuture<Void>> locDirectAction : locDirectActions) {
                    locDescendantSubtrees.add(locDirectAction.join());
                }
            }
            CompletableFuture.allOf(locDescendantSubtrees.toArray(CompletableFuture[]::new)).join();
        }, orchestrationExecutor);
    }

    private AIcPublicationResult AIcRunPublication(
            AIcPublicationPayload aPayload,
            AIcPublicationEndpoint aEndpoint,
            AIiPublicationCredentialResolver aCredentialResolver,
            AIiPublicationProgressReporterFactory aProgressReporterFactory) {
        Instant locStarted = Instant.now();
        int locMaximumAttempts = aEndpoint.publicationRetryCount() + 1;
        Throwable locFailure = null;
        for (int locAttempt = 1; locAttempt <= locMaximumAttempts; locAttempt++) {
            AtomicBoolean locCancelled = new AtomicBoolean(false);
            Instant locDeadline = aEndpoint.publicationAttemptTimeoutMillis() == null
                    ? null : Instant.now().plusMillis(aEndpoint.publicationAttemptTimeoutMillis());
            AIiPublicationProgressReporter locProgress = aProgressReporterFactory.create(aEndpoint);
            AIcPublicationAttemptContext locContext = new AIcPublicationAttemptContext(
                    aEndpoint, aPayload, locAttempt, locMaximumAttempts, aEndpoint.publicationAttemptTimeoutMillis(), locDeadline,
                    aCredentialResolver.resolve(aEndpoint), locCancelled::get, locProgress);
            try {
                AIcExecutePublicationAttempt(AIcRequirePublicationAdapter(aEndpoint), locContext, locCancelled);
                URI locOutputUri = AIcPublicationContent.publishedUri(aPayload, aEndpoint);
                return new AIcPublicationResult(
                        aEndpoint.id(), true, true, false, locAttempt, Duration.between(locStarted, Instant.now()),
                        locOutputUri, Map.of("PublicationAdapter", aEndpoint.publicationAdapter(),
                                "PublicationEndpointId", aEndpoint.id()), null);
            } catch (Throwable locAttemptFailure) {
                locFailure = AIcCause(locAttemptFailure);
                if (locAttempt < locMaximumAttempts) {
                    AIcSleep(aEndpoint.publicationWaitForNextAttemptMillis());
                }
            }
        }
        boolean locIgnored = aEndpoint.publicationFailurePolicy() == AInPublicationFailurePolicy.IGNORE_PUBLICATION_FAILURE;
        return new AIcPublicationResult(
                aEndpoint.id(), true, false, locIgnored, locMaximumAttempts, Duration.between(locStarted, Instant.now()),
                null, Map.of("PublicationAdapter", aEndpoint.publicationAdapter(),
                        "PublicationEndpointId", aEndpoint.id()), locFailure);
    }

    private AIcPostPublicationActionResult AIcRunAction(
            AIcPublicationJob aJob,
            AIcPostPublicationAction aAction,
            AIcPublicationExecutionLineage aLineage,
            URI aInputUri,
            AIiPublicationCredentialResolver aCredentialResolver,
            AIiPublicationProgressReporterFactory aProgressReporterFactory) {
        AIiPostPublicationActionAdapter locAdapter = postPublicationActionAdapters.get(aAction.postPublicationActionAdapter());
        if (locAdapter == null) {
            return new AIcPostPublicationActionResult(aAction.id(), false, false,
                    aAction.failurePolicy() == AInPostPublicationActionFailurePolicy.IGNORE_FAILURE, 0, Duration.ZERO, null, Map.of(),
                    new IllegalArgumentException("Unknown post-publication action adapter '" + aAction.postPublicationActionAdapter() + "'."));
        }
        AIcPublicationEndpoint locTarget = aAction.targetPublicationEndpointId() == null
                ? aJob.endpoint() : aJob.publicationEndpointRegistry().get(aAction.targetPublicationEndpointId());
        if (aAction.targetPublicationEndpointId() != null && locTarget == null) {
            return new AIcPostPublicationActionResult(aAction.id(), false, false,
                    aAction.failurePolicy() == AInPostPublicationActionFailurePolicy.IGNORE_FAILURE, 0, Duration.ZERO, null, Map.of(),
                    new IllegalArgumentException("Unknown TargetPublicationEndpointId '" + aAction.targetPublicationEndpointId() + "'."));
        }
        int locMaximumAttempts = aAction.retryCount() + 1;
        Instant locStarted = Instant.now();
        Throwable locFailure = null;
        for (int locAttempt = 1; locAttempt <= locMaximumAttempts; locAttempt++) {
            AtomicBoolean locCancelled = new AtomicBoolean(false);
            Instant locDeadline = aAction.attemptTimeoutMillis() == null
                    ? null : Instant.now().plusMillis(aAction.attemptTimeoutMillis());
            AIiPublicationProgressReporter locProgress = aProgressReporterFactory.create(locTarget == null ? aJob.endpoint() : locTarget);
            AIiPublicationDelegate locDelegate = (aPayload, aEndpoint) ->
                    AIcRunPublication(aPayload, aEndpoint, aCredentialResolver, aProgressReporterFactory);
            AIcPostPublicationActionAttemptContext locContext = new AIcPostPublicationActionAttemptContext(
                    aAction, aLineage, aJob.payload(), aInputUri, locTarget, locAttempt, locMaximumAttempts,
                    aAction.attemptTimeoutMillis(), locDeadline,
                    locTarget == null ? Map.of() : aCredentialResolver.resolve(locTarget),
                    locCancelled::get, locProgress, locDelegate);
            try {
                if (aAction.retryCount() > 0 && !locAdapter.isRetrySafe(locContext)) {
                    throw new IllegalArgumentException("Post-publication action adapter '" + locAdapter.adapterId()
                            + "' does not permit automatic retries for action '" + aAction.id() + "'.");
                }
                AIcPostPublicationActionResult locResult = AIcExecuteActionAttempt(locAdapter, locContext, locCancelled);
                return new AIcPostPublicationActionResult(
                        aAction.id(), true, true, false, locAttempt, Duration.between(locStarted, Instant.now()),
                        locResult.outputUri(), locResult.metadata(), null);
            } catch (Throwable locAttemptFailure) {
                locFailure = AIcCause(locAttemptFailure);
                if (locAttempt < locMaximumAttempts) {
                    AIcSleep(aAction.waitForNextAttemptMillis());
                }
            }
        }
        boolean locIgnored = aAction.failurePolicy() == AInPostPublicationActionFailurePolicy.IGNORE_FAILURE;
        return new AIcPostPublicationActionResult(
                aAction.id(), true, false, locIgnored, locMaximumAttempts, Duration.between(locStarted, Instant.now()),
                null, Map.of(), locFailure);
    }

    private void AIcExecutePublicationAttempt(
            AIiPublicationAdapter aAdapter, AIcPublicationAttemptContext aContext, AtomicBoolean aCancelled) throws Exception {
        Future<?> locFuture = attemptExecutor.submit(() -> {
            try {
                aAdapter.publish(aContext);
            } catch (Exception locFailure) {
                throw new CompletionException(locFailure);
            }
        });
        AIcAwaitVoid(locFuture, aContext.publicationAttemptTimeoutMillis(), aCancelled, "Publication attempt timed out.");
    }

    private AIcPostPublicationActionResult AIcExecuteActionAttempt(
            AIiPostPublicationActionAdapter aAdapter, AIcPostPublicationActionAttemptContext aContext, AtomicBoolean aCancelled) throws Exception {
        Future<AIcPostPublicationActionResult> locFuture = attemptExecutor.submit(() -> aAdapter.execute(aContext));
        return AIcAwait(locFuture, aContext.attemptTimeoutMillis(), aCancelled, "Post-publication action attempt timed out.");
    }

    private static <T> T AIcAwait(Future<T> aFuture, Long aTimeoutMillis, AtomicBoolean aCancelled, String aTimeoutMessage) throws Exception {
        try {
            return aTimeoutMillis == null ? aFuture.get() : aFuture.get(aTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.ExecutionException locFailure) {
            Throwable locCause = AIcCause(locFailure);
            if (locCause instanceof Exception locException) {
                throw locException;
            }
            if (locCause instanceof Error locError) {
                throw locError;
            }
            throw new RuntimeException(locCause);
        } catch (TimeoutException locFailure) {
            aCancelled.set(true);
            aFuture.cancel(true);
            throw new TimeoutException(aTimeoutMessage);
        }
    }

    private static void AIcAwaitVoid(Future<?> aFuture, Long aTimeoutMillis, AtomicBoolean aCancelled, String aTimeoutMessage) throws Exception {
        try {
            if (aTimeoutMillis == null) {
                aFuture.get();
            } else {
                aFuture.get(aTimeoutMillis, TimeUnit.MILLISECONDS);
            }
        } catch (java.util.concurrent.ExecutionException locFailure) {
            Throwable locCause = AIcCause(locFailure);
            if (locCause instanceof Exception locException) {
                throw locException;
            }
            if (locCause instanceof Error locError) {
                throw locError;
            }
            throw new RuntimeException(locCause);
        } catch (TimeoutException locFailure) {
            aCancelled.set(true);
            aFuture.cancel(true);
            throw new TimeoutException(aTimeoutMessage);
        }
    }

    private static Map<String, AIiPublicationAdapter> AIcPublicationAdapters(List<? extends AIiPublicationAdapter> aAdapters) {
        LinkedHashMap<String, AIiPublicationAdapter> locResult = new LinkedHashMap<>();
        for (AIiPublicationAdapter locAdapter : aAdapters) {
            if (locResult.putIfAbsent(locAdapter.adapterId(), locAdapter) != null) {
                throw new IllegalArgumentException("Duplicate publication adapter id '" + locAdapter.adapterId() + "'.");
            }
        }
        return Map.copyOf(locResult);
    }

    private static Map<String, AIiPostPublicationActionAdapter> AIcActionAdapters(
            List<? extends AIiPostPublicationActionAdapter> aAdapters) {
        LinkedHashMap<String, AIiPostPublicationActionAdapter> locResult = new LinkedHashMap<>();
        for (AIiPostPublicationActionAdapter locAdapter : aAdapters) {
            if (locResult.putIfAbsent(locAdapter.adapterId(), locAdapter) != null) {
                throw new IllegalArgumentException("Duplicate post-publication action adapter id '" + locAdapter.adapterId() + "'.");
            }
        }
        return Map.copyOf(locResult);
    }

    private AIiPublicationAdapter AIcRequirePublicationAdapter(AIcPublicationEndpoint aEndpoint) {
        AIiPublicationAdapter locAdapter = publicationAdapters.get(aEndpoint.publicationAdapter());
        if (locAdapter == null) {
            throw new IllegalArgumentException("Unknown publication adapter '" + aEndpoint.publicationAdapter()
                    + "' for endpoint '" + aEndpoint.id() + "'.");
        }
        return locAdapter;
    }

    private static void AIcValidateActions(
            List<AIcPostPublicationAction> aActions, String aPrefix,
            Map<String, CompletableFuture<AIcPostPublicationActionResult>> aResults) {
        for (AIcPostPublicationAction locAction : aActions) {
            String locPath = aPrefix + "/" + locAction.id();
            if (aResults.putIfAbsent(locPath, new CompletableFuture<>()) != null) {
                throw new IllegalArgumentException("Duplicate post-publication action path '" + locPath + "'.");
            }
            AIcValidateActions(locAction.postPublicationActions(), locPath, aResults);
        }
    }

    private static String AIcActionPath(
            String aRootId, AIcPublicationExecutionLineage aLineage, String aActionId) {
        StringBuilder locPath = new StringBuilder(aRootId);
        List<AIcPublicationExecutionStep> locSteps = aLineage.steps();
        for (int locIndex = 1; locIndex < locSteps.size(); locIndex++) {
            locPath.append('/').append(locSteps.get(locIndex).id());
        }
        return locPath.append('/').append(aActionId).toString();
    }

    private static void AIcSleep(long aMillis) {
        if (aMillis <= 0L) {
            return;
        }
        try {
            Thread.sleep(aMillis);
        } catch (InterruptedException locFailure) {
            Thread.currentThread().interrupt();
            throw new CompletionException(locFailure);
        }
    }

    private static Throwable AIcCause(Throwable aFailure) {
        Throwable locFailure = aFailure;
        while ((locFailure instanceof CompletionException || locFailure instanceof java.util.concurrent.ExecutionException)
                && locFailure.getCause() != null) {
            locFailure = locFailure.getCause();
        }
        return locFailure;
    }

    private static java.util.concurrent.ThreadFactory AIcThreadFactory(String aPrefix) {
        return new java.util.concurrent.ThreadFactory() {
            private int counter;

            @Override
            public synchronized Thread newThread(Runnable aRunnable) {
                Thread locThread = new Thread(aRunnable, aPrefix + "-" + (++counter));
                locThread.setDaemon(true);
                return locThread;
            }
        };
    }

    @Override
    public void close() {
        if (ownsExecutors) {
            orchestrationExecutor.shutdownNow();
            attemptExecutor.shutdownNow();
        }
    }
}
