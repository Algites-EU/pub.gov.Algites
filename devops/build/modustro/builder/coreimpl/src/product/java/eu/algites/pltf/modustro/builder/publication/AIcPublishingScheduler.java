package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublishingEndpoint;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublishingEndpointResult;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublishingPayload;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublishingStabilityConfiguration;
import eu.algites.pltf.modustro.builder.model.publication.AInPublishingFailurePolicy;
import eu.algites.pltf.modustro.builder.publication.AIcPublishingAttemptContext;
import eu.algites.pltf.modustro.builder.publication.AIcPublishingScheduleHandle;
import eu.algites.pltf.modustro.builder.publication.AIiCancellationToken;
import eu.algites.pltf.modustro.builder.publication.AIiPublishingAdapter;
import eu.algites.pltf.modustro.builder.publication.AIiPublishingCredentialResolver;
import eu.algites.pltf.modustro.builder.publication.AIiPublishingProgressReporter;
import eu.algites.pltf.modustro.builder.publication.AIiPublishingProgressReporterFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Executes output publishing with ordered start barriers, retries, deadlines, and non-blocking best-effort endpoints. */
public final class AIcPublishingScheduler implements AutoCloseable {
    private final Map<String, AIiPublishingAdapter> adapters;
    private final ExecutorService orchestrationExecutor;
    private final ExecutorService endpointControlExecutor;
    private final ExecutorService adapterAttemptExecutor;
    private final boolean ownsExecutors;

    /** Creates a scheduler with Builder-owned daemon executors. */
    public AIcPublishingScheduler(List<? extends AIiPublishingAdapter> aAdapters) {
        this(
                aAdapters,
                Executors.newSingleThreadExecutor(AIcThreadFactory("modustro-publishing-orchestration")),
                Executors.newCachedThreadPool(AIcThreadFactory("modustro-publishing-control")),
                Executors.newCachedThreadPool(AIcThreadFactory("modustro-publishing-attempt")),
                true);
    }

    /** Creates a scheduler using explicit executors, primarily for embedding and tests. */
    public AIcPublishingScheduler(
            List<? extends AIiPublishingAdapter> aAdapters,
            ExecutorService aOrchestrationExecutor,
            ExecutorService aEndpointControlExecutor,
            ExecutorService aAdapterAttemptExecutor) {
        this(aAdapters, aOrchestrationExecutor, aEndpointControlExecutor, aAdapterAttemptExecutor, false);
    }

    private AIcPublishingScheduler(
            List<? extends AIiPublishingAdapter> aAdapters,
            ExecutorService aOrchestrationExecutor,
            ExecutorService aEndpointControlExecutor,
            ExecutorService aAdapterAttemptExecutor,
            boolean aOwnsExecutors) {
        Objects.requireNonNull(aAdapters, "adapters");
        orchestrationExecutor = Objects.requireNonNull(aOrchestrationExecutor, "orchestrationExecutor");
        endpointControlExecutor = Objects.requireNonNull(aEndpointControlExecutor, "endpointControlExecutor");
        adapterAttemptExecutor = Objects.requireNonNull(aAdapterAttemptExecutor, "adapterAttemptExecutor");
        ownsExecutors = aOwnsExecutors;
        Map<String, AIiPublishingAdapter> locAdapters = new LinkedHashMap<>();
        for (AIiPublishingAdapter locAdapter : aAdapters) {
            Objects.requireNonNull(locAdapter, "adapter");
            AIiPublishingAdapter locPrevious = locAdapters.putIfAbsent(locAdapter.adapterId(), locAdapter);
            if (locPrevious != null) {
                throw new IllegalArgumentException("Duplicate publishing adapter id '" + locAdapter.adapterId() + "'.");
            }
        }
        adapters = Map.copyOf(locAdapters);
    }

    /** Schedules one output payload against the effective stability configuration. */
    public AIcPublishingScheduleHandle schedule(
            AIcPublishingPayload aPayload,
            AIcPublishingStabilityConfiguration aConfiguration,
            AIiPublishingCredentialResolver aCredentialResolver,
            AIiPublishingProgressReporterFactory aProgressReporterFactory) {
        Objects.requireNonNull(aPayload, "payload");
        Objects.requireNonNull(aConfiguration, "configuration");
        Objects.requireNonNull(aCredentialResolver, "credentialResolver");
        Objects.requireNonNull(aProgressReporterFactory, "progressReporterFactory");

        if (!aConfiguration.publishingEnabled()) {
            return new AIcPublishingScheduleHandle(CompletableFuture.completedFuture(null), Map.of());
        }

        List<AIcPublishingEndpoint> locEndpoints = aConfiguration.publishingEndpoints().stream()
                .filter(AIcPublishingEndpoint::enabled)
                .sorted(Comparator.comparingInt(AIcPublishingEndpoint::publishingOrder).thenComparing(AIcPublishingEndpoint::id))
                .toList();

        for (AIcPublishingEndpoint locEndpoint : locEndpoints) {
            AIiPublishingAdapter locAdapter = adapters.get(locEndpoint.publishingAdapter());
            if (locAdapter == null) {
                throw new IllegalArgumentException(
                        "Unknown publishing adapter '" + locEndpoint.publishingAdapter() + "' for endpoint '" + locEndpoint.id() + "'.");
            }
            if (locEndpoint.publishingRetryCount() > 0 && !locAdapter.isRetrySafe(aPayload, locEndpoint)) {
                throw new IllegalArgumentException(
                        "Publishing adapter '" + locEndpoint.publishingAdapter() + "' does not permit automatic retries for endpoint '"
                                + locEndpoint.id() + "'.");
            }
        }

        Map<String, CompletableFuture<AIcPublishingEndpointResult>> locResultFutures = new LinkedHashMap<>();
        for (AIcPublishingEndpoint locEndpoint : locEndpoints) {
            if (locResultFutures.putIfAbsent(locEndpoint.id(), new CompletableFuture<>()) != null) {
                throw new IllegalArgumentException("Duplicate effective publishing endpoint id '" + locEndpoint.id() + "'.");
            }
        }

        CompletableFuture<Void> locRequiredCompletion = new CompletableFuture<>();
        TreeMap<Integer, List<AIcPublishingEndpoint>> locGroups = new TreeMap<>();
        for (AIcPublishingEndpoint locEndpoint : locEndpoints) {
            locGroups.computeIfAbsent(locEndpoint.publishingOrder(), aIgnored -> new ArrayList<>()).add(locEndpoint);
        }

        orchestrationExecutor.execute(() -> AIcRunGroups(
                aPayload,
                locGroups,
                locResultFutures,
                locRequiredCompletion,
                aCredentialResolver,
                aProgressReporterFactory));

        return new AIcPublishingScheduleHandle(locRequiredCompletion, locResultFutures);
    }

    private void AIcRunGroups(
            AIcPublishingPayload aPayload,
            TreeMap<Integer, List<AIcPublishingEndpoint>> aGroups,
            Map<String, CompletableFuture<AIcPublishingEndpointResult>> aResultFutures,
            CompletableFuture<Void> aRequiredCompletion,
            AIiPublishingCredentialResolver aCredentialResolver,
            AIiPublishingProgressReporterFactory aProgressReporterFactory) {
        try {
            List<AIcPublishingEndpoint> locNotStarted = new ArrayList<>();
            boolean locStop = false;
            for (Map.Entry<Integer, List<AIcPublishingEndpoint>> locGroup : aGroups.entrySet()) {
                if (locStop) {
                    locNotStarted.addAll(locGroup.getValue());
                    continue;
                }

                List<CompletableFuture<AIcPublishingEndpointResult>> locRequiredFutures = new ArrayList<>();
                for (AIcPublishingEndpoint locEndpoint : locGroup.getValue()) {
                    CompletableFuture<AIcPublishingEndpointResult> locTarget = aResultFutures.get(locEndpoint.id());
                    CompletableFuture<AIcPublishingEndpointResult> locExecution = CompletableFuture.supplyAsync(
                            () -> AIcRunEndpoint(aPayload, locEndpoint, aCredentialResolver, aProgressReporterFactory),
                            endpointControlExecutor);
                    locExecution.whenComplete((locResult, locFailure) -> {
                        if (locFailure != null) {
                            locTarget.complete(new AIcPublishingEndpointResult(
                                    locEndpoint.id(), true, false, false, 0, Duration.ZERO, AIcUnwrap(locFailure)));
                        } else {
                            locTarget.complete(locResult);
                        }
                    });
                    if (locEndpoint.publishingFailurePolicy() == AInPublishingFailurePolicy.FAIL_BUILD_ON_PUBLISHING_FAILURE) {
                        locRequiredFutures.add(locTarget);
                    }
                }

                for (CompletableFuture<AIcPublishingEndpointResult> locRequiredFuture : locRequiredFutures) {
                    AIcPublishingEndpointResult locResult = locRequiredFuture.join();
                    if (!locResult.success()) {
                        locStop = true;
                    }
                }
            }

            if (locStop) {
                Throwable locRequiredFailure = null;
                for (CompletableFuture<AIcPublishingEndpointResult> locFuture : aResultFutures.values()) {
                    if (locFuture.isDone()) {
                        AIcPublishingEndpointResult locResult = locFuture.getNow(null);
                        if (locResult != null && locResult.started() && !locResult.success() && !locResult.ignoredFailure()) {
                            locRequiredFailure = locResult.failure();
                            break;
                        }
                    }
                }
                for (AIcPublishingEndpoint locEndpoint : locNotStarted) {
                    aResultFutures.get(locEndpoint.id()).complete(new AIcPublishingEndpointResult(
                            locEndpoint.id(), false, false, false, 0, Duration.ZERO,
                            new IllegalStateException("Publishing endpoint was not started because an earlier required publishing endpoint failed.")));
                }
                aRequiredCompletion.completeExceptionally(
                        locRequiredFailure == null ? new IllegalStateException("Required publishing failed.") : locRequiredFailure);
            } else {
                aRequiredCompletion.complete(null);
            }
        } catch (Throwable locFailure) {
            aRequiredCompletion.completeExceptionally(AIcUnwrap(locFailure));
            for (CompletableFuture<AIcPublishingEndpointResult> locFuture : aResultFutures.values()) {
                if (!locFuture.isDone()) {
                    locFuture.complete(new AIcPublishingEndpointResult(
                            "<not-started>", false, false, false, 0, Duration.ZERO, AIcUnwrap(locFailure)));
                }
            }
        }
    }

    private AIcPublishingEndpointResult AIcRunEndpoint(
            AIcPublishingPayload aPayload,
            AIcPublishingEndpoint aEndpoint,
            AIiPublishingCredentialResolver aCredentialResolver,
            AIiPublishingProgressReporterFactory aProgressReporterFactory) {
        Instant locEndpointStart = Instant.now();
        AIiPublishingAdapter locAdapter = adapters.get(aEndpoint.publishingAdapter());
        if (locAdapter == null) {
            return AIcFailureResult(aEndpoint, 0, locEndpointStart,
                    new IllegalArgumentException("Unknown publishing adapter '" + aEndpoint.publishingAdapter() + "'."));
        }
        if (aEndpoint.publishingRetryCount() > 0 && !locAdapter.isRetrySafe(aPayload, aEndpoint)) {
            return AIcFailureResult(aEndpoint, 0, locEndpointStart,
                    new IllegalArgumentException("Publishing adapter '" + aEndpoint.publishingAdapter()
                            + "' does not permit automatic retries for endpoint '" + aEndpoint.id() + "'."));
        }

        Map<String, String> locCredentials;
        try {
            locCredentials = Map.copyOf(aCredentialResolver.resolve(aEndpoint));
        } catch (Throwable locFailure) {
            return AIcFailureResult(aEndpoint, 0, locEndpointStart, locFailure);
        }
        AIiPublishingProgressReporter locReporter = aEndpoint.showPublishingProgressIfPossible()
                ? Objects.requireNonNull(aProgressReporterFactory.create(aEndpoint), "progressReporter")
                : AIcNoOpProgressReporter.INSTANCE;

        int locMaximumAttemptCount = aEndpoint.publishingRetryCount() + 1;
        Throwable locLastFailure = null;
        for (int locAttemptNumber = 1; locAttemptNumber <= locMaximumAttemptCount; locAttemptNumber++) {
            AIcCancellationSource locCancellation = new AIcCancellationSource();
            Long locTimeoutMillis = aEndpoint.publishingAttemptTimeoutMillis();
            Instant locDeadline = locTimeoutMillis == null ? null : Instant.now().plusMillis(locTimeoutMillis);
            AIcPublishingAttemptContext locContext = new AIcPublishingAttemptContext(
                    aEndpoint,
                    aPayload,
                    locAttemptNumber,
                    locMaximumAttemptCount,
                    locTimeoutMillis,
                    locDeadline,
                    locCredentials,
                    locCancellation,
                    locReporter);
            Future<?> locAttempt = adapterAttemptExecutor.submit(() -> {
                locAdapter.publish(locContext);
                return null;
            });
            try {
                if (locTimeoutMillis == null) {
                    locAttempt.get();
                } else {
                    locAttempt.get(locTimeoutMillis, TimeUnit.MILLISECONDS);
                }
                return new AIcPublishingEndpointResult(
                        aEndpoint.id(), true, true, false, locAttemptNumber,
                        Duration.between(locEndpointStart, Instant.now()), null);
            } catch (TimeoutException locFailure) {
                locCancellation.AIcCancel();
                locAttempt.cancel(true);
                locLastFailure = new TimeoutException(
                        "Publishing attempt " + locAttemptNumber + " for endpoint '" + aEndpoint.id()
                                + "' exceeded " + locTimeoutMillis + " ms.");
            } catch (InterruptedException locFailure) {
                Thread.currentThread().interrupt();
                locCancellation.AIcCancel();
                locAttempt.cancel(true);
                locLastFailure = locFailure;
                break;
            } catch (ExecutionException locFailure) {
                locLastFailure = AIcUnwrap(locFailure);
            }

            if (locAttemptNumber < locMaximumAttemptCount && aEndpoint.publishingRetryDelayMillis() > 0L) {
                try {
                    Thread.sleep(aEndpoint.publishingRetryDelayMillis());
                } catch (InterruptedException locFailure) {
                    Thread.currentThread().interrupt();
                    locLastFailure = locFailure;
                    break;
                }
            }
        }
        return AIcFailureResult(aEndpoint, locMaximumAttemptCount, locEndpointStart, locLastFailure);
    }

    private AIcPublishingEndpointResult AIcFailureResult(
            AIcPublishingEndpoint aEndpoint,
            int aAttempts,
            Instant aStart,
            Throwable aFailure) {
        boolean locIgnored = aEndpoint.publishingFailurePolicy() == AInPublishingFailurePolicy.IGNORE_PUBLISHING_FAILURE;
        return new AIcPublishingEndpointResult(
                aEndpoint.id(), true, false, locIgnored, aAttempts,
                Duration.between(aStart, Instant.now()), aFailure);
    }

    private static Throwable AIcUnwrap(Throwable aFailure) {
        Throwable locFailure = aFailure;
        while ((locFailure instanceof ExecutionException || locFailure instanceof java.util.concurrent.CompletionException)
                && locFailure.getCause() != null) {
            locFailure = locFailure.getCause();
        }
        return locFailure;
    }

    private static ThreadFactory AIcThreadFactory(String aPrefix) {
        return new ThreadFactory() {
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
        if (!ownsExecutors) {
            return;
        }
        orchestrationExecutor.shutdownNow();
        endpointControlExecutor.shutdownNow();
        adapterAttemptExecutor.shutdownNow();
    }

    private static final class AIcCancellationSource implements AIiCancellationToken {
        private final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public boolean isCancellationRequested() {
            return cancelled.get() || Thread.currentThread().isInterrupted();
        }

        private void AIcCancel() {
            cancelled.set(true);
        }
    }

    private enum AIcNoOpProgressReporter implements AIiPublishingProgressReporter {
        INSTANCE;

        @Override
        public void started(String aMessage) {
        }

        @Override
        public void progress(long aCompleted, long aTotal, String aUnit, String aMessage) {
        }

        @Override
        public void indeterminate(String aMessage) {
        }

        @Override
        public void completed(String aMessage) {
        }
    }
}
