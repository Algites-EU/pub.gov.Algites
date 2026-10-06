package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.output.AInBuildOutputTypeGroup;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationFinalizationAction;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationFinalizationActionResult;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationEndpoint;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayload;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayloadFile;
import eu.algites.pltf.modustro.builder.model.publication.AInFinalizationActionFailurePolicy;
import eu.algites.pltf.modustro.builder.model.publication.AInPublicationFailurePolicy;
import eu.algites.pltf.modustro.builder.model.publication.AInPublicationOutputKind;
import eu.algites.pltf.modustro.builder.model.publication.AInPublicationStability;
import eu.algites.pltf.modustro.builder.model.subscription.AIcInputSubscription;
import eu.algites.pltf.modustro.builder.publication.adapters.AIcLocalCopyPublicationAdapter;
import eu.algites.pltf.modustro.builder.subscription.AIcInputSubscriptionResolver;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/** Executable regression scenarios also run by the normal TestNG suite. */
public final class AIcPublicationGraphChecks {
    private AIcPublicationGraphChecks() {
    }

    private static void AIcCheck(boolean aCondition, String aMessage) {
        if (!aCondition) {
            throw new AssertionError(aMessage);
        }
    }

    private static void AIcFails(AIiRunnable aAction) throws Exception {
        try {
            aAction.run();
            throw new AssertionError("Expected failure.");
        } catch (IllegalArgumentException locExpected) {
        }
    }

    @FunctionalInterface
    private interface AIiRunnable {
        void run() throws Exception;
    }

    /** Runs the complete portable publication/subscription regression set. */
    public static void main(String[] aArguments) throws Exception {
        run();
        System.out.println("PUBLICATION_GRAPH_CHECKS_OK");
    }

    /** Covers selector precedence, subscriptions, planning, lineage and local action ordering. */
    public static void run() throws Exception {
        AIcCheckPublicationConfiguration();
        AIcCheckInputSubscriptions();
        AIcCheckPlanningAndBuildRecord();
        AIcCheckActionLineageAndOrdering();
        AIcCheckTargetPublicationEndpointResolution();
    }

    private static void AIcCheckPublicationConfiguration() throws Exception {
        Map<String, String> locValues = new LinkedHashMap<>();
        locValues.put("OutputPublications.0.OutputSelector", "native_develop_binaries");
        locValues.put("OutputPublications.0.TechnologyKind", "java");
        locValues.put("OutputPublications.0.Snapshot.PublicationEnabled", "true");
        locValues.put("OutputPublications.1.OutputSelector", "native_sources");
        locValues.put("OutputPublications.1.TechnologyKind", "java");
        locValues.put("OutputPublications.1.Snapshot.PublicationEnabled", "false");
        locValues.put("OutputPublications.2.OutputSelector", "native_develop_outputs");
        locValues.put("OutputPublications.2.TechnologyKind", "java");
        locValues.put("OutputPublications.2.Snapshot.PublicationEnabled", "false");
        locValues.put("OutputPublications.3.OutputSelector", "native_outputs");
        locValues.put("OutputPublications.3.TechnologyKind", "java");
        locValues.put("OutputPublications.3.Snapshot.PublicationEnabled", "true");
        locValues.put("OutputPublications.4.OutputSelector", "native_product_binaries");
        locValues.put("OutputPublications.4.TechnologyKind", "java");
        locValues.put("OutputPublications.4.Snapshot.PublicationEndpoints.0.Id", "configured");
        locValues.put("OutputPublications.4.Snapshot.PublicationEndpoints.0.PublicationUri", "file:///tmp/publications/");
        locValues.put("OutputPublications.4.Snapshot.PublicationEndpoints.0.PublicationAdapter", "local-copy");
        locValues.put("OutputPublications.4.Snapshot.PublicationEndpoints.0.Configuration.Region", "test-region");
        Map<String, String> locExpanded = AIcPublicationConfiguration.expand(locValues);
        AIcCheck("true".equals(locExpanded.get("java.native_develop_binaries.Snapshot.PublicationEnabled")),
                "Concrete selector must override group selectors.");
        AIcCheck("false".equals(locExpanded.get("java.native_develop_sources.Snapshot.PublicationEnabled")),
                "Overlapping non-global selectors must retain declaration executionOrder.");
        AIcCheck("true".equals(locExpanded.get("java.native_product_documentation.Snapshot.PublicationEnabled")),
                "native_outputs must provide the lowest-precedence defaults.");
        AIcCheck("test-region".equals(locExpanded.get(
                "java.native_product_binaries.Snapshot.PublicationEndpoints.0.Configuration.Region")),
                "Provider Configuration must survive publication expansion.");
        AIcCheck(AInBuildOutputTypeGroup.values().length == 6, "Six virtual native-output groups are expected.");

        Map<String, String> locMissingTechnology = new LinkedHashMap<>();
        locMissingTechnology.put("OutputPublications.0.OutputSelector", "native_product_binaries");
        locMissingTechnology.put("OutputPublications.0.Snapshot.PublicationEnabled", "true");
        AIcFails(() -> AIcPublicationConfiguration.expand(locMissingTechnology));

        List<Map<String, Object>> locBase = List.of(Map.of(
                "Id", "standard",
                "Classifier", "old",
                "PublicationFinalizationActions", List.of(Map.of("Id", "build-record", "RetryCount", 2L))));
        List<Map<String, Object>> locMerged = AIcPublicationConfiguration.merge(locBase, List.of(Map.of(
                "Id", "standard",
                "Classifier", "custom",
                "PublicationFinalizationActions", List.of(Map.of("Id", "build-record", "WaitForNextAttemptMillis", 0L)))));
        AIcCheck(locMerged.size() == 1 && "custom".equals(locMerged.get(0).get("Classifier")),
                "Publications must merge by stable Id.");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> locActions = (List<Map<String, Object>>) locMerged.get(0).get("PublicationFinalizationActions");
        AIcCheck(Long.valueOf(2L).equals(locActions.get(0).get("RetryCount")),
                "Recursive finalization action merge must preserve inherited properties.");
        AIcCheck(AIcPublicationConfiguration.merge(locBase, List.of()).isEmpty(),
                "An explicit empty list must clear an inherited list.");
    }

    private static void AIcCheckInputSubscriptions() {
        AIcInputSubscription locBase = new AIcInputSubscription(
                "java", "native_product_binaries", "central", true, "public", "release",
                URI.create("https://repo.example.invalid/releases/"), "maven-repository", null, 0,
                Map.of("Mirror", "primary"));
        AIcInputSubscription locOverride = new AIcInputSubscription(
                "java", "native_product_binaries", "central", false, "public", null,
                null, null, null, 10, Map.of("Mirror", "disabled"));
        List<AIcInputSubscription> locMerged = new AIcInputSubscriptionResolver().merge(
                List.of(locBase), List.of(locOverride));
        AIcCheck(locMerged.size() == 1, "Subscriptions must merge by technology, selector and Id.");
        AIcCheck(!locMerged.get(0).enabled(), "Descendant subscription enablement must override its base.");
        AIcCheck(locMerged.get(0).subscriptionUri().equals(locBase.subscriptionUri()),
                "Sparse subscription override must preserve inherited URI.");
        AIcCheck("disabled".equals(locMerged.get(0).configuration().get("Mirror")),
                "Subscription Configuration must merge sparsely.");
    }

    private static void AIcCheckPlanningAndBuildRecord() throws Exception {
        Path locDirectory = Files.createTempDirectory("modustro-publication-record-");
        Path locPublishedDirectory = Files.createDirectories(locDirectory.resolve("published"));
        Path locJar = locDirectory.resolve("library-1.0.jar");
        Files.writeString(locJar, "immutable binary contents");
        String locBefore = AIcBuildRecordPublicationFinalizationActionAdapter.hash(locJar);
        AIcPublicationPayload locPayload = new AIcPublicationPayload(
                AInPublicationOutputKind.NATIVE_PRODUCT_BINARIES,
                AInPublicationStability.RELEASE,
                "test:library",
                "1.0",
                List.of(new AIcPublicationPayloadFile(locJar, locJar.getFileName().toString())),
                Map.of(
                        "groupId", "test",
                        "artifactId", "library",
                        "version", "1.0",
                        "technologyKind", "java",
                        "classifier", "",
                        "extension", "jar"));
        Map<String, Object> locEndpoint = new LinkedHashMap<>();
        locEndpoint.put("Id", "local");
        locEndpoint.put("PublicationUri", locPublishedDirectory.toUri().toString());
        locEndpoint.put("PublicationAdapter", "local-copy");
        locEndpoint.put("Publications", List.of(Map.of("Id", "standard")));
        List<AIcPublicationJob> locPlan = new AIcPublicationPlanner().plan(
                locPayload,
                Map.of("PublicationEnabled", true, "PublicationEndpoints", List.of(locEndpoint)),
                Map.of("RepositoryId", "pub.test"),
                locDirectory);
        AIcCheck(locPlan.size() == 1, "One root Publication must remain one scheduler root.");
        AIcCheck(locPlan.get(0).publicationFinalizationActions().stream().anyMatch(aAction -> "build-record".equals(aAction.id())),
                "A root Publication must receive one implicit build-record action.");
        try (AIcPublicationScheduler locScheduler = new AIcPublicationScheduler(new eu.algites.pltf.modustro.builder.catalog.AIcAdapterCatalog(
                List.of(), List.of(new AIcLocalCopyPublicationAdapter()),
                List.of(new AIcBuildRecordPublicationFinalizationActionAdapter()), List.of(), List.of(), List.of()))) {
            AIcPublicationScheduleHandle locHandle = locScheduler.schedule(
                    locPayload, locPlan, List.of(), Map.of(), aEndpoint -> Map.of(), AIcPublicationGraphChecks::AIcProgress);
            locHandle.requiredCompletion().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
        Path locPublishedJar = locPublishedDirectory.resolve("library-1.0.jar");
        Path locRecord = locPublishedDirectory.resolve("library-1.0.jar.modustro-build-record.yml");
        AIcCheck(Files.isRegularFile(locPublishedJar), "Root publication must write its payload.");
        AIcCheck(Files.isRegularFile(locRecord), "Implicit build-record must use the exact published filename suffix.");
        AIcCheck(locBefore.equals(AIcBuildRecordPublicationFinalizationActionAdapter.hash(locJar)),
                "Build-record generation must not modify the root artifact.");

        Map<String, Object> locDisabledRecordEndpoint = new LinkedHashMap<>(locEndpoint);
        locDisabledRecordEndpoint.put("Publications", List.of(Map.of(
                "Id", "standard",
                "PublicationFinalizationActions", List.of(Map.of("Id", "build-record", "ExecutionEnabled", false)))));
        List<AIcPublicationJob> locNoRecordPlan = new AIcPublicationPlanner().plan(
                locPayload,
                Map.of("PublicationEnabled", true, "PublicationEndpoints", List.of(locDisabledRecordEndpoint)),
                Map.of(), locDirectory);
        AIcCheck(locNoRecordPlan.get(0).publicationFinalizationActions().size() == 1
                        && !locNoRecordPlan.get(0).publicationFinalizationActions().get(0).executionEnabled(),
                "An explicit disabled build-record action must suppress the implicit action.");
    }

    private static void AIcCheckActionLineageAndOrdering() throws Exception {
        Path locDirectory = Files.createTempDirectory("modustro-action-executionOrder-");
        Path locFile = locDirectory.resolve("artifact.bin");
        Files.writeString(locFile, "data");
        AIcPublicationPayload locPayload = new AIcPublicationPayload(
                AInPublicationOutputKind.NATIVE_PRODUCT_BINARIES,
                AInPublicationStability.RELEASE,
                "test:artifact",
                "1",
                List.of(new AIcPublicationPayloadFile(locFile, locFile.getFileName().toString())),
                Map.of("groupId", "test", "artifactId", "artifact", "version", "1", "technologyKind", "java"));
        AIcPublicationEndpoint locEndpoint = new AIcPublicationEndpoint(
                "root", true, locDirectory.resolve("out").toUri(), "mock", null, 0,
                AInPublicationFailurePolicy.FAIL_BUILD_ON_PUBLICATION_FAILURE, 0, 0L, null, true, Map.of());
        List<String> locEvents = new CopyOnWriteArrayList<>();
        AIiPublicationAdapter locPublicationAdapter = new AIiPublicationAdapter() {
            @Override
            public String adapterId() {
                return "mock";
            }

            @Override
            public boolean isRetrySafe(AIcPublicationPayload aPayload, AIcPublicationEndpoint aEndpoint) {
                return true;
            }

            @Override
            public void publish(AIcPublicationAttemptContext aContext) {
                locEvents.add("publication");
            }
        };
        AIiPublicationFinalizationActionAdapter locActionAdapter = new AIiPublicationFinalizationActionAdapter() {
            @Override
            public String adapterId() {
                return "probe";
            }

            @Override
            public AIcPublicationFinalizationActionResult execute(AIcPublicationFinalizationActionAttemptContext aContext) throws Exception {
                List<String> locIds = aContext.lineage().steps().stream().map(aStep -> aStep.id()).toList();
                locEvents.add("start:" + aContext.action().id() + ":" + String.join(">", locIds));
                if ("child".equals(aContext.action().id())) {
                    Thread.sleep(200L);
                }
                URI locOutput = URI.create("file:///virtual/" + aContext.action().id());
                locEvents.add("end:" + aContext.action().id());
                return new AIcPublicationFinalizationActionResult(
                        aContext.action().id(), true, true, false, 1, Duration.ZERO, locOutput,
                        Map.of("LineageSize", aContext.lineage().steps().size()), null);
            }
        };
        AIcPublicationFinalizationAction locChild = AIcAction("child", 0, List.of());
        AIcPublicationFinalizationAction locFirst = AIcAction("first", 0, List.of(locChild));
        AIcPublicationFinalizationAction locSecond = AIcAction("second", 1, List.of());
        AIcPublicationJob locJob = new AIcPublicationJob(
                "root/standard", locEndpoint, locPayload, Map.of(), List.of(locFirst, locSecond), Map.of("root", locEndpoint));
        try (AIcPublicationScheduler locScheduler = new AIcPublicationScheduler(new eu.algites.pltf.modustro.builder.catalog.AIcAdapterCatalog(
                List.of(), List.of(locPublicationAdapter), List.of(locActionAdapter), List.of(), List.of(), List.of()))) {
            locScheduler.schedule(locPayload, List.of(locJob), List.of(), Map.of(), aEndpoint -> Map.of(), AIcPublicationGraphChecks::AIcProgress)
                    .requiredCompletion().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
        int locFirstEnd = locEvents.indexOf("end:first");
        int locSecondStart = AIcIndexPrefix(locEvents, "start:second:");
        int locChildEnd = locEvents.indexOf("end:child");
        int locChildStart = AIcIndexPrefix(locEvents, "start:child:");
        AIcCheck(locFirstEnd >= 0 && locChildStart > locFirstEnd,
                "A child finalization action must never start before its parent completes.");
        AIcCheck(locSecondStart > locFirstEnd,
                "A later local ExecutionOrder group must wait for direct siblings in the previous group.");
        AIcCheck(locSecondStart < locChildEnd,
                "A later sibling ExecutionOrder group must not wait for descendants of an earlier sibling.");
        String locChildEvent = locEvents.get(locChildStart);
        AIcCheck(locChildEvent.contains("root/standard>first"),
                "Every action must receive the complete ancestor lineage.");
    }

    private static void AIcCheckTargetPublicationEndpointResolution() throws Exception {
        Path locDirectory = Files.createTempDirectory("modustro-action-target-");
        Path locFile = locDirectory.resolve("artifact.jar");
        Files.writeString(locFile, "data");
        AIcPublicationPayload locPayload = new AIcPublicationPayload(
                AInPublicationOutputKind.NATIVE_PRODUCT_BINARIES,
                AInPublicationStability.RELEASE,
                "test:artifact",
                "1.0",
                List.of(new AIcPublicationPayloadFile(locFile, locFile.getFileName().toString())),
                Map.of("groupId", "test", "artifactId", "artifact", "version", "1.0",
                        "logicalVersion", "1.0", "technologyKind", "java"));
        AIcPublicationEndpoint locRelease = new AIcPublicationEndpoint(
                "release", true, URI.create("https://release.invalid/"), "root-probe", null, 0,
                AInPublicationFailurePolicy.FAIL_BUILD_ON_PUBLICATION_FAILURE, 0, 0L, null, true, Map.of());
        AIcPublicationEndpoint locSnapshot = new AIcPublicationEndpoint(
                "snapshot", true, URI.create("https://snapshot.invalid/"), "snapshot-probe", "snapshot-credentials", 0,
                AInPublicationFailurePolicy.FAIL_BUILD_ON_PUBLICATION_FAILURE, 0, 0L, null, true,
                Map.of("Provider", "repsy", "Repository", "snapshots"));
        AIcPublicationFinalizationAction locCleanup = new AIcPublicationFinalizationAction(
                "cleanup", true, "target-probe", "snapshot", 0,
                AInFinalizationActionFailurePolicy.FAIL_BUILD_ON_FAILURE, 0, 0L, null, true, Map.of(), List.of());
        AIiPublicationAdapter locRootAdapter = new AIiPublicationAdapter() {
            @Override
            public String adapterId() {
                return "root-probe";
            }

            @Override
            public boolean isRetrySafe(AIcPublicationPayload aPayload, AIcPublicationEndpoint aEndpoint) {
                return true;
            }

            @Override
            public void publish(AIcPublicationAttemptContext aContext) {
            }
        };
        AIiPublicationFinalizationActionAdapter locTargetProbe = new AIiPublicationFinalizationActionAdapter() {
            @Override
            public String adapterId() {
                return "target-probe";
            }

            @Override
            public AIcPublicationFinalizationActionResult execute(AIcPublicationFinalizationActionAttemptContext aContext) {
                AIcCheck("snapshot".equals(aContext.targetPublicationEndpoint().id()),
                        "TargetPublicationEndpointId must resolve from the publication endpoint registry.");
                AIcCheck("repsy".equals(aContext.targetPublicationEndpoint().configuration().get("Provider")),
                        "Target endpoint provider configuration must be available to the action.");
                AIcCheck("secret".equals(aContext.credentials().get("password")),
                        "Action credentials must be resolved from the target endpoint, not the release endpoint.");
                return new AIcPublicationFinalizationActionResult(
                        aContext.action().id(), true, true, false, 1, Duration.ZERO, null, Map.of(), null);
            }
        };
        AIcPublicationJob locJob = new AIcPublicationJob(
                "release/standard", locRelease, locPayload, Map.of(), List.of(locCleanup),
                Map.of("release", locRelease, "snapshot", locSnapshot));
        try (AIcPublicationScheduler locScheduler = new AIcPublicationScheduler(new eu.algites.pltf.modustro.builder.catalog.AIcAdapterCatalog(
                List.of(), List.of(locRootAdapter), List.of(locTargetProbe), List.of(), List.of(), List.of()))) {
            locScheduler.schedule(
                    locPayload, List.of(locJob), List.of(), Map.of(),
                    aEndpoint -> "snapshot".equals(aEndpoint.id())
                            ? Map.of("username", "user", "password", "secret") : Map.of(),
                    AIcPublicationGraphChecks::AIcProgress)
                    .requiredCompletion().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }

    private static AIcPublicationFinalizationAction AIcAction(
            String aId, int aOrder, List<AIcPublicationFinalizationAction> aChildren) {
        return new AIcPublicationFinalizationAction(
                aId, true, "probe", null, aOrder,
                AInFinalizationActionFailurePolicy.FAIL_BUILD_ON_FAILURE,
                0, 0L, null, true, Map.of(), aChildren);
    }

    private static int AIcIndexPrefix(List<String> aValues, String aPrefix) {
        for (int locIndex = 0; locIndex < aValues.size(); locIndex++) {
            if (aValues.get(locIndex).startsWith(aPrefix)) {
                return locIndex;
            }
        }
        return -1;
    }

    private static AIiPublicationProgressReporter AIcProgress(AIcPublicationEndpoint aEndpoint) {
        return new AIiPublicationProgressReporter() {
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
        };
    }
}
