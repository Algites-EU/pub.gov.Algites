package eu.algites.pltf.modustro.builder.publication;

import com.sun.net.httpserver.HttpServer;
import eu.algites.pltf.modustro.builder.model.publication.*;
import eu.algites.pltf.modustro.builder.model.execution.AIngBuildExecutionFailurePolicy_1;
import java.time.Duration;
import eu.algites.pltf.modustro.builder.publication.adapters.AIcMavenRepositoryPublicationAdapter;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercises a real Maven HTTP publication followed by the implicit build-record action. */
public final class AIcMavenBuildRecordChecks {
    public static void main(String[] aArguments) throws Exception { run(); System.out.println("MAVEN_BUILD_RECORD_CHECKS_OK"); }
    public static void run() throws Exception {
        AIcMavenBuildRecordChecks locChecks = new AIcMavenBuildRecordChecks();
        locChecks.AIcPublishesPairedSnapshotAndBuildRecord();
        locChecks.AIcRecordsTransformedParent();
        locChecks.AIcRejectsRemoteSourceWithoutDownloadingIt();
    }

    /** A complete Maven package is uploaded once with its build record already attached. */
    public void AIcPublishesPairedSnapshotAndBuildRecord() throws Exception {
        Map<String, byte[]> locBytes = new ConcurrentHashMap<>();
        Map<String, AtomicInteger> locPuts = new ConcurrentHashMap<>();
        AtomicInteger locArtifactGets = new AtomicInteger();
        HttpServer locServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        locServer.createContext("/", aExchange -> {
            String locPath = aExchange.getRequestURI().getPath();
            if ("PUT".equals(aExchange.getRequestMethod())) {
                locBytes.put(locPath, aExchange.getRequestBody().readAllBytes());
                locPuts.computeIfAbsent(locPath, aIgnored -> new AtomicInteger()).incrementAndGet();
                aExchange.sendResponseHeaders(201, -1);
            } else if ("GET".equals(aExchange.getRequestMethod())) {
                if (!locPath.endsWith("maven-metadata.xml") && locBytes.containsKey(locPath)) {
                    locArtifactGets.incrementAndGet();
                    aExchange.sendResponseHeaders(403, -1);
                    aExchange.close();
                    return;
                }
                byte[] locContent = locBytes.get(locPath);
                if (locContent == null) {
                    aExchange.sendResponseHeaders(404, -1);
                } else {
                    aExchange.sendResponseHeaders(200, locContent.length);
                    aExchange.getResponseBody().write(locContent);
                }
            } else {
                aExchange.sendResponseHeaders(405, -1);
            }
            aExchange.close();
        });
        locServer.start();
        try {
            Path locDirectory = Files.createTempDirectory("modustro-maven-record-");
            String locLogicalPrefix = "library-1.0-SNAPSHOT";
            String locMainName = locLogicalPrefix + ".jar";
            String locPomName = locLogicalPrefix + ".pom";
            String locModuleName = locLogicalPrefix + ".module";
            String locSourcesName = locLogicalPrefix + "-sources.jar";
            Path locMain = locDirectory.resolve(locMainName);
            Path locPom = locDirectory.resolve(locPomName);
            Path locModule = locDirectory.resolve(locModuleName);
            Path locSources = locDirectory.resolve(locSourcesName);
            Files.writeString(locMain, "binary contents");
            Files.writeString(locPom, "<project></project>");
            Files.writeString(locModule, "{}");
            Files.writeString(locSources, "source contents");

            Map<String, String> locBinaryCoordinates = Map.of(
                    "groupId", "test", "artifactId", "library", "version", "1.0-SNAPSHOT", "technologyKind", "java");
            Map<String, String> locSourceCoordinates = Map.of(
                    "groupId", "test", "artifactId", "library", "version", "1.0-SNAPSHOT",
                    "technologyKind", "java", "classifier", "sources", "extension", "jar");
            AIcPublicationPayload locBinary = new AIcPublicationPayload(
                    AInPublicationOutputKind.NATIVE_PRODUCT_BINARIES, AInPublicationStability.SNAPSHOT,
                    "test:library", "1.0-SNAPSHOT", List.of(
                        new AIcPublicationPayloadFile(locMain, locMainName),
                        new AIcPublicationPayloadFile(locPom, locPomName),
                        new AIcPublicationPayloadFile(locModule, locModuleName)), locBinaryCoordinates);
            AIcPublicationPayload locSource = new AIcPublicationPayload(
                    AInPublicationOutputKind.NATIVE_PRODUCT_SOURCES, AInPublicationStability.SNAPSHOT,
                    "test:library", "1.0-SNAPSHOT",
                    List.of(new AIcPublicationPayloadFile(locSources, locSourcesName)), locSourceCoordinates);
            Map<String, Object> locEndpoint = Map.of(
                    "Id", "remote", "PublicationUri", "http://127.0.0.1:" + locServer.getAddress().getPort() + "/maven/",
                    "PublicationAdapter", "maven-repository");
            Map<String, Object> locConfig = Map.of("PublicationEnabled", true, "PublicationEndpoints", List.of(locEndpoint));
            Map<String, Object> locContext = Map.of("RepositoryId", "pub.test", "Invocation", Map.of(
                    "Id", "mock-http", "StartedAt", "2026-10-04T20:00:00.123456789Z"));
            AIcPublicationPlanner locPlanner = new AIcPublicationPlanner();
            List<AIcPublicationJob> locBinaryJobs = locPlanner.plan(locBinary, locConfig, locContext, locDirectory);
            List<AIcPublicationJob> locSourceJobs = locPlanner.plan(locSource, locConfig, locContext, locDirectory);
            AIcMavenPublicationBatchPlanner.AIcdBatch locBatch = new AIcMavenPublicationBatchPlanner()
                    .plan(List.of(locBinaryJobs.get(0), locSourceJobs.get(0)));
            AIcPublicationCheckAssertions.assertEquals(locBatch.physicalJobs().size(), 1);
            AIcPublicationCheckAssertions.assertTrue(locBatch.physicalJobs().get(0).publicationFinalizationActions().isEmpty());

            try (AIcPublicationScheduler locScheduler = new AIcPublicationScheduler(new eu.algites.pltf.modustro.builder.catalog.AIcAdapterCatalog(
                    List.of(), List.of(new AIcMavenRepositoryPublicationAdapter()),
                    List.of(new AIcBuildRecordPublicationFinalizationActionAdapter()), List.of(), List.of(), List.of()))) {
                var locHandle = locScheduler.schedule(
                        locBinary, locBatch.physicalJobs(), List.of(), Map.of(),
                        aEndpoint -> Map.of(),
                        aEndpoint -> new AIiPublicationProgressReporter() {
                            @Override public void started(String aMessage) { }
                            @Override public void progress(long aCompleted, long aTotal, String aUnit, String aMessage) { }
                            @Override public void indeterminate(String aMessage) { }
                            @Override public void completed(String aMessage) { }
                        });
                locHandle.requiredCompletion().toCompletableFuture().get(10, TimeUnit.SECONDS);
                AIcPublicationCheckAssertions.assertTrue(locHandle.publicationResults().get(
                        locBatch.physicalJobs().get(0).id()).toCompletableFuture().join().success());
            }

            String locBase = "/maven/test/library/1.0-SNAPSHOT/";
            for (AIcPublicationPayloadFile locFile : locBatch.physicalJobs().get(0).payload().files()) {
                String locPath = locBase + locFile.logicalName();
                AIcPublicationCheckAssertions.assertTrue(locBytes.containsKey(locPath));
                AIcPublicationCheckAssertions.assertEquals(locPuts.get(locPath).get(), 1);
            }
            AIcPublicationCheckAssertions.assertTrue(locBytes.containsKey(locBase + "maven-metadata.xml"));
            AIcPublicationCheckAssertions.assertEquals(locArtifactGets.get(), 0);
            AIcPublicationPayloadFile locRecordFile = locBatch.physicalJobs().get(0).payload().files().stream()
                    .filter(aFile -> aFile.logicalName().endsWith(".modustro-build-record.yml"))
                    .findFirst().orElseThrow();
            String locRecord = new String(locBytes.get(locBase + locRecordFile.logicalName()),
                    java.nio.charset.StandardCharsets.UTF_8);
            AIcPublicationCheckAssertions.assertTrue(locRecord.contains("\"Filename\":\"" +
                    locBatch.physicalJobs().get(0).payload().files().get(0).logicalName() + "\""));
            AIcPublicationCheckAssertions.assertTrue(locRecord.contains(AIcBuildRecordPublicationFinalizationActionAdapter.hash(locMain)));
            AIcPublicationCheckAssertions.assertTrue(locRecord.contains(AIcBuildRecordPublicationFinalizationActionAdapter.hash(locSources)));
            AIcPublicationCheckAssertions.assertTrue(!locRecord.contains("ExecutionLineage"));
        } finally {
            locServer.stop(0);
        }
    }
    /** A nested build record describes its transformed parent, rather than the root payload. */
    public void AIcRecordsTransformedParent() throws Exception {
        Path locDirectory = Files.createTempDirectory("modustro-record-parent-");
        Path locRootFile = locDirectory.resolve("root.jar");
        Path locParentFile = locDirectory.resolve("transformed.jar");
        Files.writeString(locRootFile, "root bytes");
        Files.writeString(locParentFile, "transformed bytes");
        AIcPublicationPayload locRoot = new AIcPublicationPayload(
                AInPublicationOutputKind.NATIVE_PRODUCT_BINARIES, AInPublicationStability.RELEASE,
                "test:library", "1.0", List.of(new AIcPublicationPayloadFile(locRootFile, "root.jar")),
                Map.of("groupId", "test", "artifactId", "library", "version", "1.0"));
        AIcPublicationExecutionLineage locLineage = new AIcPublicationExecutionLineage(List.of(
                new AIcPublicationExecutionStep("publication", "publication", locRootFile.toUri(), locRootFile.toUri(), Map.of(), Map.of()),
                new AIcPublicationExecutionStep("transform", "publication-finalization-action", locRootFile.toUri(), locParentFile.toUri(), Map.of(), Map.of())));
        AIcPublicationFinalizationAction locAction = new AIcPublicationFinalizationAction(
                "build-record", true, "modustro-build-record", null, 0,
                AIngBuildExecutionFailurePolicy_1.FAIL_BUILD_ON_FAILURE, 0, 1000L, null, false, Map.of(), List.of());
        AIcPublicationEndpoint locEndpoint = AIcPublicationPlanner.endpoint(Map.of(
                "Id", "target", "PublicationAdapter", "local-copy", "PublicationUri", locDirectory.toUri().toString()), "target");
        String[] locRecord = new String[1];
        AIcPublicationFinalizationActionAttemptContext locContext = new AIcPublicationFinalizationActionAttemptContext(
                locAction, locLineage, locRoot, locParentFile.toUri(), locEndpoint, 1, 1, null, null, Map.of(),
                () -> false, new AIiPublicationProgressReporter() {
                    @Override public void started(String aMessage) { }
                    @Override public void progress(long aCompleted, long aTotal, String aUnit, String aMessage) { }
                    @Override public void indeterminate(String aMessage) { }
                    @Override public void completed(String aMessage) { }
                }, (aPayload, aEndpoint) -> {
                    locRecord[0] = Files.readString(aPayload.files().get(0).path());
                    return new AIcPublicationResult("target", true, true, false, 1, Duration.ZERO,
                            aPayload.files().get(0).contentUri(), Map.of(), null);
                });
        new AIcBuildRecordPublicationFinalizationActionAdapter().execute(locContext);
        AIcPublicationCheckAssertions.assertTrue(locRecord[0].contains(AIcBuildRecordPublicationFinalizationActionAdapter.hash(locParentFile)));
        AIcPublicationCheckAssertions.assertTrue(!locRecord[0].contains(AIcBuildRecordPublicationFinalizationActionAdapter.hash(locRootFile)));
        AIcPublicationCheckAssertions.assertTrue(locRecord[0].contains("transformed.jar"));
    }

    /** A publication without a local input must not anonymously download from a private Maven endpoint. */
    public void AIcRejectsRemoteSourceWithoutDownloadingIt() throws Exception {
        java.net.URI locRemoteUri = java.net.URI.create("https://maven.cloudsmith.io/example/private/library.jar");
        Path locDirectory = Files.createTempDirectory("modustro-record-remote-");
        AIcPublicationPayload locPayload = new AIcPublicationPayload(
                AInPublicationOutputKind.NATIVE_PRODUCT_BINARIES, AInPublicationStability.SNAPSHOT,
                "test:library", "1.0-SNAPSHOT",
                List.of(new AIcPublicationPayloadFile(locRemoteUri, "library.jar")),
                Map.of("groupId", "test", "artifactId", "library", "version", "1.0-SNAPSHOT"));
        AIcPublicationExecutionLineage locLineage = new AIcPublicationExecutionLineage(List.of(
                new AIcPublicationExecutionStep("publication", "publication", locRemoteUri, locRemoteUri, Map.of(), Map.of())));
        AIcPublicationFinalizationAction locAction = new AIcPublicationFinalizationAction(
                "build-record", true, "modustro-build-record", null, 0,
                AIngBuildExecutionFailurePolicy_1.FAIL_BUILD_ON_FAILURE, 0, 1000L, null, false, Map.of(), List.of());
        AIcPublicationEndpoint locEndpoint = AIcPublicationPlanner.endpoint(Map.of(
                "Id", "target", "PublicationAdapter", "local-copy", "PublicationUri", locDirectory.toUri().toString()), "target");
        AIcPublicationFinalizationActionAttemptContext locContext = new AIcPublicationFinalizationActionAttemptContext(
                locAction, locLineage, locPayload, locRemoteUri, locEndpoint, 1, 1, null, null, Map.of(),
                () -> false, new AIiPublicationProgressReporter() {
                    @Override public void started(String aMessage) { }
                    @Override public void progress(long aCompleted, long aTotal, String aUnit, String aMessage) { }
                    @Override public void indeterminate(String aMessage) { }
                    @Override public void completed(String aMessage) { }
                }, (aPayload, aEndpoint) -> {
                    throw new AssertionError("Remote source must be rejected before attempting sidecar publishing.");
                });
        try {
            new AIcBuildRecordPublicationFinalizationActionAdapter().execute(locContext);
            throw new AssertionError("Expected remote build-record source to be rejected.");
        } catch (IllegalStateException locExpected) {
            AIcPublicationCheckAssertions.assertTrue(locExpected.getMessage().contains("refusing an unauthenticated download"));
        }
    }

}
