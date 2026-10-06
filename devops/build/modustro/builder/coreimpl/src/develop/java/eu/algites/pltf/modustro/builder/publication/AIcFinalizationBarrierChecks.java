package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.catalog.AIcAdapterCatalog;
import eu.algites.pltf.modustro.builder.catalog.AIcBuiltinAdapterCatalog;
import eu.algites.pltf.modustro.builder.model.publication.*;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Portable regression scenarios for all publication-finalization barriers and failure paths. */
public final class AIcFinalizationBarrierChecks {
    private AIcFinalizationBarrierChecks() { }

    public static void main(String[] aArguments) throws Exception {
        run();
        System.out.println("FINALIZATION_BARRIER_CHECKS_OK");
    }

    public static void run() throws Exception {
        AIcCheckBarriers();
        AIcCheckReturnedFailureAndCredentials();
        AIcCheckRetryAndTimeout();
        AIcCheckBestEffortCompletion();
        AIcCheckExecutionOrder();
        AIcCheckImmutableSnapshots();
    }

    private static void AIcCheckBarriers() throws Exception {
        List<String> locEvents = new CopyOnWriteArrayList<>();
        CountDownLatch locLaterSibling = new CountDownLatch(1);
        AIiPublicationFinalizationActionAdapter locPublicationFinalizer = new AIiPublicationFinalizationActionAdapter() {
            @Override public String adapterId() { return "probe"; }
            @Override public AIcPublicationFinalizationActionResult execute(AIcPublicationFinalizationActionAttemptContext aContext) throws Exception {
                String locId = aContext.action().id();
                if (locId.equals("child")) AIcCheck(locLaterSibling.await(3, TimeUnit.SECONDS), "Sibling ExecutionOrder must not await the earlier subtree.");
                if (locId.equals("later")) locLaterSibling.countDown();
                locEvents.add(locId);
                return AIcSuccess(locId);
            }
        };
        AIiOutputPublicationFinalizationActionAdapter locOutputFinalizer = new AIiOutputPublicationFinalizationActionAdapter() {
            @Override public String adapterId() { return "output-probe"; }
            @Override public AIcPublicationFinalizationActionResult execute(AIcOutputPublicationFinalizationActionAttemptContext aContext) {
                AIcCheck(locEvents.contains("child"), "Output barrier must await recursive descendants.");
                AIcCheck(aContext.outputExecution().publications().get(0).publicationFinalizationActions().get(0).finalizationActions().size() == 1,
                        "Output finalizer must receive the complete nested result tree.");
                locEvents.add("output");
                return AIcSuccess(aContext.action().id());
            }
        };
        AIiArtifactPublicationFinalizationActionAdapter locArtifactFinalizer = new AIiArtifactPublicationFinalizationActionAdapter() {
            @Override public String adapterId() { return "artifact-probe"; }
            @Override public AIcPublicationFinalizationActionResult execute(AIcArtifactPublicationFinalizationActionAttemptContext aContext) {
                AIcCheck(aContext.artifactExecution().outputs().get(0).outputPublicationFinalizationActions().size() == 1,
                        "Artifact barrier must receive completed output finalization results.");
                locEvents.add("artifact");
                return AIcSuccess(aContext.action().id());
            }
        };
        AIiVersionScopePublicationFinalizationActionAdapter locScopeFinalizer = new AIiVersionScopePublicationFinalizationActionAdapter() {
            @Override public String adapterId() { return "scope-probe"; }
            @Override public AIcPublicationFinalizationActionResult execute(AIcVersionScopePublicationFinalizationActionAttemptContext aContext) {
                AIcCheck(aContext.versionScopeExecution().artifacts().get(0).artifactPublicationFinalizationActions().size() == 1,
                        "Version Scope barrier must receive completed artifact finalization results.");
                locEvents.add("scope");
                return AIcSuccess(aContext.action().id());
            }
        };
        AIcAdapterCatalog locCatalog = new AIcAdapterCatalog(List.of(), List.of(AIcTransport()), List.of(locPublicationFinalizer),
                List.of(locOutputFinalizer), List.of(locArtifactFinalizer), List.of(locScopeFinalizer));
        AIcPublicationPayload locPayload = AIcPayload();
        AIcPublicationEndpoint locEndpoint = AIcEndpoint();
        AIcPublicationJob locJob = new AIcPublicationJob("root/standard", locEndpoint, locPayload, Map.of(),
                List.of(AIcAction("parent", 0, 0, null, List.of(AIcAction("child", 0, 0, null, List.of()))),
                        AIcAction("later", 1, 0, null, List.of())), Map.of("root", locEndpoint));
        try (AIcPublicationScheduler locScheduler = new AIcPublicationScheduler(locCatalog)) {
            AIcOutputPublicationFinalizationAction locOutputAction = new AIcOutputPublicationFinalizationAction(
                    "output", true, "output-probe", 0, AInFinalizationActionFailurePolicy.FAIL_BUILD_ON_FAILURE, 0, 0L, null, true, Map.of());
            var locHandle = locScheduler.schedule(locPayload, List.of(locJob), List.of(locOutputAction), Map.of(), aEndpoint -> Map.of(), AIcFinalizationBarrierChecks::AIcProgress);
            var locOutput = locHandle.outputExecutionResult().toCompletableFuture().get(5, TimeUnit.SECONDS);
            locHandle.requiredCompletion().toCompletableFuture().get(5, TimeUnit.SECONDS);
            AIcCheck(locOutput.success(), "Complete output must succeed.");
            var locArtifactBase = new AIcArtifactPublicationExecutionResult("test:artifact", "artifact", ".", "1.0", AInPublicationStability.RELEASE, List.of(locOutput), List.of());
            var locArtifactAction = new AIcArtifactPublicationFinalizationAction("artifact", true, "artifact-probe", 0,
                    AInFinalizationActionFailurePolicy.FAIL_BUILD_ON_FAILURE, 0, 0L, null, true, Map.of());
            var locArtifact = locScheduler.finalizeArtifact(locArtifactBase, List.of(locArtifactAction), AIcFinalizationBarrierChecks::AIcProgress);
            var locScopeAction = new AIcVersionScopePublicationFinalizationAction("scope", true, "scope-probe", 0,
                    AInFinalizationActionFailurePolicy.FAIL_BUILD_ON_FAILURE, 0, 0L, null, true, Map.of());
            var locScope = locScheduler.finalizeVersionScope(AIcScope(List.of(locArtifact)), List.of(locScopeAction), Map.of(), AIcFinalizationBarrierChecks::AIcProgress);
            AIcCheck(locScope.state() == AInVersionScopePublicationAttemptState.COMPLETE, "Successful scope must be COMPLETE.");
            int locCompletedEvents = locEvents.size();
            AIcCheck(locScheduler.finalizeVersionScope(locScope, List.of(locScopeAction), Map.of(), AIcFinalizationBarrierChecks::AIcProgress) == locScope
                    && locEvents.size() == locCompletedEvents, "COMPLETE must be immutable and must never run finalizers again.");
            AIcCheck(locEvents.indexOf("output") < locEvents.indexOf("artifact") && locEvents.indexOf("artifact") < locEvents.indexOf("scope"),
                    "Higher finalization barriers must run in scope executionOrder.");
            var locMissing = new AIcOutputPublicationExecutionResult("test:missing", "python", AInPublicationOutputKind.NATIVE_PRODUCT_BINARIES,
                    AInPublicationStability.RELEASE, "1.0", Map.of(), Map.of(), List.of(), List.of(), false);
            var locIncompleteArtifact = new AIcArtifactPublicationExecutionResult("test:missing", "missing", ".", "1.0", AInPublicationStability.RELEASE,
                    List.of(locMissing), List.of());
            int locBefore = locEvents.size();
            var locFailedScope = locScheduler.finalizeVersionScope(AIcScope(List.of(locArtifact, locIncompleteArtifact)), List.of(locScopeAction), Map.of(), AIcFinalizationBarrierChecks::AIcProgress);
            AIcCheck(locFailedScope.state() == AInVersionScopePublicationAttemptState.FAILED && locEvents.size() == locBefore,
                    "Missing expected outputs must prevent all Version Scope finalizers and COMPLETE.");
        }
        var locRefresh = AIcBuiltinAdapterCatalog.create().requireVersionScopePublicationFinalizationActionAdapter("modustro-refresh-docs-site");
        AIcCheck(locRefresh != null, "Repository docs refresh must be registered in the unified catalog.");
    }

    private static void AIcCheckReturnedFailureAndCredentials() throws Exception {
        AIiPublicationFinalizationActionAdapter locAdapter = new AIiPublicationFinalizationActionAdapter() {
            @Override public String adapterId() { return "probe"; }
            @Override public AIcPublicationFinalizationActionResult execute(AIcPublicationFinalizationActionAttemptContext aContext) {
                return new AIcPublicationFinalizationActionResult(aContext.action().id(), true, false, false, 1, Duration.ZERO, null, Map.of(), new IllegalStateException("returned failure"));
            }
        };
        try (AIcPublicationScheduler locScheduler = new AIcPublicationScheduler(new AIcAdapterCatalog(List.of(), List.of(AIcTransport()), List.of(locAdapter), List.of(), List.of(), List.of()))) {
            AIcPublicationPayload locPayload = AIcPayload();
            AIcPublicationEndpoint locEndpoint = AIcEndpoint();
            var locJob = new AIcPublicationJob("root/standard", locEndpoint, locPayload, Map.of(),
                    List.of(AIcAction("failure", 0, 0, null, List.of(AIcAction("skipped", 0, 0, null, List.of())))), Map.of("root", locEndpoint));
            var locHandle = locScheduler.schedule(locPayload, List.of(locJob), List.of(), Map.of(), aEndpoint -> Map.of(), AIcFinalizationBarrierChecks::AIcProgress);
            AIcCheck(!locHandle.outputExecutionResult().toCompletableFuture().get(5, TimeUnit.SECONDS).success(), "Returned failure must never become success.");
            AIcCheck(locHandle.publicationFinalizationActionResults().values().stream().allMatch(aFuture -> aFuture.toCompletableFuture().isDone()),
                    "Skipped descendant handles must terminate.");
            var locCredentialFailure = locScheduler.schedule(locPayload, List.of(locJob), List.of(), Map.of(), aEndpoint -> { throw new IllegalStateException("credential failure"); }, AIcFinalizationBarrierChecks::AIcProgress);
            var locResult = locCredentialFailure.outputExecutionResult().toCompletableFuture().get(5, TimeUnit.SECONDS);
            AIcCheck(!locResult.success() && locResult.publications().get(0).result().failure().getMessage().contains("credential failure"),
                    "Credential errors must produce a terminal publication result.");
        }
    }

    private static void AIcCheckRetryAndTimeout() throws Exception {
        AtomicInteger locAttempts = new AtomicInteger();
        CountDownLatch locCancelled = new CountDownLatch(1);
        AIiPublicationFinalizationActionAdapter locAdapter = new AIiPublicationFinalizationActionAdapter() {
            @Override public String adapterId() { return "probe"; }
            @Override public boolean isRetrySafe(AIcPublicationFinalizationActionAttemptContext aContext) { return true; }
            @Override public AIcPublicationFinalizationActionResult execute(AIcPublicationFinalizationActionAttemptContext aContext) throws Exception {
                if (aContext.action().id().equals("timeout")) {
                    try { new CountDownLatch(1).await(5, TimeUnit.SECONDS); }
                    finally { if (aContext.cancellationToken().isCancellationRequested()) locCancelled.countDown(); }
                }
                if (locAttempts.incrementAndGet() == 1) throw new IllegalStateException("retry once");
                return AIcSuccess(aContext.action().id());
            }
        };
        try (AIcPublicationScheduler locScheduler = new AIcPublicationScheduler(new AIcAdapterCatalog(List.of(), List.of(AIcTransport()), List.of(locAdapter), List.of(), List.of(), List.of()))) {
            var locPayload = AIcPayload();
            var locEndpoint = AIcEndpoint();
            var locRetryJob = new AIcPublicationJob("root/retry", locEndpoint, locPayload, Map.of(), List.of(AIcAction("retry", 0, 1, null, List.of())), Map.of());
            var locRetry = locScheduler.schedule(locPayload, List.of(locRetryJob), List.of(), Map.of(), aEndpoint -> Map.of(), AIcFinalizationBarrierChecks::AIcProgress)
                    .outputExecutionResult().toCompletableFuture().get(5, TimeUnit.SECONDS);
            AIcCheck(locRetry.success() && locAttempts.get() == 2, "Scheduler must own safe retry attempts.");
            var locTimeoutJob = new AIcPublicationJob("root/timeout", locEndpoint, locPayload, Map.of(), List.of(AIcAction("timeout", 0, 0, 50L, List.of())), Map.of());
            var locTimeout = locScheduler.schedule(locPayload, List.of(locTimeoutJob), List.of(), Map.of(), aEndpoint -> Map.of(), AIcFinalizationBarrierChecks::AIcProgress)
                    .outputExecutionResult().toCompletableFuture().get(5, TimeUnit.SECONDS);
            AIcCheck(!locTimeout.success() && locCancelled.await(3, TimeUnit.SECONDS), "Timeout must cancel the attempt and leave a failed result.");
        }
    }

    private static void AIcCheckImmutableSnapshots() {
        List<Object> locNested = new ArrayList<>(List.of("original"));
        Map<String, Object> locConfiguration = new LinkedHashMap<>();
        locConfiguration.put("Nested", locNested);
        var locAction = new AIcPublicationFinalizationAction("frozen", false, null, null, 0,
                AInFinalizationActionFailurePolicy.FAIL_BUILD_ON_FAILURE, 0, 0L, null, false, locConfiguration, List.of());
        locNested.add("changed");
        AIcCheck(((List<?>) locAction.configuration().get("Nested")).size() == 1, "Nested configuration must be snapshotted.");
        try { ((List<?>) locAction.configuration().get("Nested")).clear(); throw new AssertionError("Nested result values must be immutable."); }
        catch (UnsupportedOperationException locExpected) { }
    }

    private static void AIcCheckBestEffortCompletion() throws Exception {
        for (boolean locOptionalPublication : List.of(true, false)) {
            CountDownLatch locStarted = new CountDownLatch(1);
            CountDownLatch locRelease = new CountDownLatch(1);
            AIiPublicationAdapter locTransport = new AIiPublicationAdapter() {
                @Override public String adapterId() { return "transport"; }
                @Override public boolean isRetrySafe(AIcPublicationPayload aPayload, AIcPublicationEndpoint aEndpoint) { return true; }
                @Override public void publish(AIcPublicationAttemptContext aContext) throws Exception {
                    if (locOptionalPublication) {
                        locStarted.countDown();
                        AIcCheck(locRelease.await(3, TimeUnit.SECONDS), "Optional publication was not released.");
                    }
                }
            };
            AIiPublicationFinalizationActionAdapter locActionAdapter = new AIiPublicationFinalizationActionAdapter() {
                @Override public String adapterId() { return "probe"; }
                @Override public AIcPublicationFinalizationActionResult execute(AIcPublicationFinalizationActionAttemptContext aContext) throws Exception {
                    locStarted.countDown();
                    AIcCheck(locRelease.await(3, TimeUnit.SECONDS), "Optional finalization was not released.");
                    return AIcSuccess(aContext.action().id());
                }
            };
            var locEndpoint = new AIcPublicationEndpoint("root", true, URI.create("file:///virtual/"), "transport", null, 0,
                    locOptionalPublication ? AInPublicationFailurePolicy.IGNORE_PUBLICATION_FAILURE : AInPublicationFailurePolicy.FAIL_BUILD_ON_PUBLICATION_FAILURE,
                    0, 0L, null, false, Map.of());
            var locAction = new AIcPublicationFinalizationAction("optional", true, "probe", null, 0,
                    AInFinalizationActionFailurePolicy.IGNORE_FAILURE, 0, 0L, null, false, Map.of(), List.of());
            var locPayload = AIcPayload();
            var locJob = new AIcPublicationJob("root/standard", locEndpoint, locPayload, Map.of(),
                    locOptionalPublication ? List.of() : List.of(locAction), Map.of());
            try (var locScheduler = new AIcPublicationScheduler(new AIcAdapterCatalog(List.of(), List.of(locTransport), List.of(locActionAdapter), List.of(), List.of(), List.of()))) {
                var locHandle = locScheduler.schedule(locPayload, List.of(locJob), List.of(), Map.of(), aEndpoint -> Map.of(), AIcFinalizationBarrierChecks::AIcProgress);
                try {
                    AIcCheck(locStarted.await(3, TimeUnit.SECONDS), "Best-effort attempt must start.");
                    locHandle.requiredCompletion().toCompletableFuture().get(1, TimeUnit.SECONDS);
                    AIcCheck(!locHandle.outputExecutionResult().toCompletableFuture().isDone(), "Required completion must not await unrelated optional work.");
                } finally { locRelease.countDown(); }
                AIcCheck(locHandle.outputExecutionResult().toCompletableFuture().get(3, TimeUnit.SECONDS).success(),
                        "The full output barrier must retain completed best-effort results.");
            }
        }
    }

    private static void AIcCheckExecutionOrder() throws Exception {
        CountDownLatch locLaterRoot = new CountDownLatch(1);
        AIiPublicationAdapter locTransport = new AIiPublicationAdapter() {
            @Override public String adapterId() { return "transport"; }
            @Override public boolean isRetrySafe(AIcPublicationPayload aPayload, AIcPublicationEndpoint aEndpoint) { return true; }
            @Override public void publish(AIcPublicationAttemptContext aContext) {
                if (aContext.endpoint().id().equals("later")) locLaterRoot.countDown();
            }
        };
        AIiPublicationFinalizationActionAdapter locFinalizer = new AIiPublicationFinalizationActionAdapter() {
            @Override public String adapterId() { return "probe"; }
            @Override public AIcPublicationFinalizationActionResult execute(AIcPublicationFinalizationActionAttemptContext aContext) throws Exception {
                AIcCheck(locLaterRoot.await(3, TimeUnit.SECONDS), "ExecutionOrder must await direct publications rather than descendant trees.");
                return AIcSuccess(aContext.action().id());
            }
        };
        var locPayload = AIcPayload();
        var locFirst = AIcEndpoint();
        var locLater = new AIcPublicationEndpoint("later", true, URI.create("file:///virtual/later/"), "transport", null, 1,
                AInPublicationFailurePolicy.FAIL_BUILD_ON_PUBLICATION_FAILURE, 0, 0L, null, false, Map.of());
        var locJobs = List.of(new AIcPublicationJob("first/standard", locFirst, locPayload, Map.of(), List.of(AIcAction("child", 0, 0, null, List.of())), Map.of()),
                new AIcPublicationJob("later/standard", locLater, locPayload, Map.of(), List.of(), Map.of()));
        try (var locScheduler = new AIcPublicationScheduler(new AIcAdapterCatalog(List.of(), List.of(locTransport), List.of(locFinalizer), List.of(), List.of(), List.of()))) {
            var locResult = locScheduler.schedule(locPayload, locJobs, List.of(), Map.of(), aEndpoint -> Map.of(), AIcFinalizationBarrierChecks::AIcProgress)
                    .outputExecutionResult().toCompletableFuture().get(5, TimeUnit.SECONDS);
            AIcCheck(locResult.success(), "Root ordering must retain both complete publication trees.");
        }
    }

    private static AIcVersionScopePublicationExecutionResult AIcScope(List<AIcArtifactPublicationExecutionResult> aArtifacts) {
        return new AIcVersionScopePublicationExecutionResult(".", "1.0", AInPublicationStability.RELEASE, AInVersionScopePublicationAttemptState.PUBLISHING, aArtifacts, List.of());
    }
    private static AIcPublicationFinalizationAction AIcAction(String aId, int aOrder, int aRetries, Long aTimeout, List<AIcPublicationFinalizationAction> aChildren) {
        return new AIcPublicationFinalizationAction(aId, true, "probe", null, aOrder, AInFinalizationActionFailurePolicy.FAIL_BUILD_ON_FAILURE,
                aRetries, 0L, aTimeout, false, Map.of(), aChildren);
    }
    private static AIcPublicationFinalizationActionResult AIcSuccess(String aId) {
        return new AIcPublicationFinalizationActionResult(aId, true, true, false, 1, Duration.ZERO, null, Map.of(), null);
    }
    private static AIcPublicationPayload AIcPayload() throws Exception {
        var locFile = Files.createTempFile("modustro-barrier-", ".jar");
        Files.writeString(locFile, "payload");
        return new AIcPublicationPayload(AInPublicationOutputKind.NATIVE_PRODUCT_BINARIES, AInPublicationStability.RELEASE, "test:artifact", "1.0",
                List.of(new AIcPublicationPayloadFile(locFile, "artifact.jar")), Map.of("groupId", "test", "artifactId", "artifact", "technologyKind", "java"));
    }
    private static AIcPublicationEndpoint AIcEndpoint() {
        return new AIcPublicationEndpoint("root", true, URI.create("file:///virtual/"), "transport", null, 0,
                AInPublicationFailurePolicy.FAIL_BUILD_ON_PUBLICATION_FAILURE, 0, 0L, null, false, Map.of());
    }
    private static AIiPublicationAdapter AIcTransport() {
        return new AIiPublicationAdapter() {
            @Override public String adapterId() { return "transport"; }
            @Override public boolean isRetrySafe(AIcPublicationPayload aPayload, AIcPublicationEndpoint aEndpoint) { return true; }
            @Override public void publish(AIcPublicationAttemptContext aContext) { }
        };
    }
    private static AIiPublicationProgressReporter AIcProgress(AIcPublicationEndpoint aEndpoint) {
        return new AIiPublicationProgressReporter() {
            @Override public void started(String aMessage) { }
            @Override public void progress(long aCompleted, long aTotal, String aUnit, String aMessage) { }
            @Override public void indeterminate(String aMessage) { }
            @Override public void completed(String aMessage) { }
        };
    }
    private static void AIcCheck(boolean aCondition, String aMessage) { if (!aCondition) throw new AssertionError(aMessage); }
}
