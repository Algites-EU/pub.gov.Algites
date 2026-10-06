package eu.algites.pltf.modustro.builder.publication;

import com.sun.net.httpserver.HttpServer;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayload;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayloadFile;
import eu.algites.pltf.modustro.builder.model.publication.AInPublicationOutputKind;
import eu.algites.pltf.modustro.builder.model.publication.AInPublicationStability;
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
    public static void run() throws Exception { new AIcMavenBuildRecordChecks().AIcPublishesPairedSnapshotAndBuildRecord(); }

    public void AIcPublishesPairedSnapshotAndBuildRecord() throws Exception {
        Map<String, byte[]> locBytes = new ConcurrentHashMap<>();
        Map<String, AtomicInteger> locPuts = new ConcurrentHashMap<>();
        HttpServer locServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        locServer.createContext("/", aExchange -> {
            String locPath = aExchange.getRequestURI().getPath();
            if ("PUT".equals(aExchange.getRequestMethod())) {
                locBytes.put(locPath, aExchange.getRequestBody().readAllBytes());
                locPuts.computeIfAbsent(locPath, aIgnored -> new AtomicInteger()).incrementAndGet();
                aExchange.sendResponseHeaders(201, -1);
            } else if ("GET".equals(aExchange.getRequestMethod())) {
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
            Path locJar = locDirectory.resolve("library-1.0-SNAPSHOT-javadoc.jar");
            Files.writeString(locJar, "unchanged documentation");
            AIcPublicationPayload locPayload = new AIcPublicationPayload(
                    AInPublicationOutputKind.NATIVE_PRODUCT_DOCUMENTATION,
                    AInPublicationStability.SNAPSHOT,
                    "test:library",
                    "1.0-SNAPSHOT",
                    List.of(new AIcPublicationPayloadFile(locJar, locJar.getFileName().toString())),
                    Map.of(
                            "groupId", "test",
                            "artifactId", "library",
                            "version", "1.0-SNAPSHOT",
                            "logicalVersion", "1.0-SNAPSHOT",
                            "technologyKind", "java",
                            "classifier", "javadoc",
                            "extension", "jar"));
            Map<String, Object> locEndpoint = Map.of(
                    "Id", "remote",
                    "PublicationUri", "http://127.0.0.1:" + locServer.getAddress().getPort() + "/maven/",
                    "PublicationAdapter", "maven-repository");
            List<AIcPublicationJob> locJobs = new AIcPublicationPlanner().plan(
                    locPayload,
                    Map.of("PublicationEnabled", true, "PublicationEndpoints", List.of(locEndpoint)),
                    Map.of("RepositoryId", "pub.test", "Invocation", Map.of(
                            "Id", "mock-http",
                            "StartedAt", "2026-10-04T20:00:00.123456789Z")),
                    locDirectory);
            AIcPublicationCheckAssertions.assertEquals(locJobs.size(), 1);
            AIcPublicationCheckAssertions.assertEquals(locJobs.get(0).publicationFinalizationActions().size(), 1);
            AIcPublicationCheckAssertions.assertEquals(locJobs.get(0).publicationFinalizationActions().get(0).id(), "build-record");

            try (AIcPublicationScheduler locScheduler = new AIcPublicationScheduler(new eu.algites.pltf.modustro.builder.catalog.AIcAdapterCatalog(
                    List.of(), List.of(new AIcMavenRepositoryPublicationAdapter()),
                    List.of(new AIcBuildRecordPublicationFinalizationActionAdapter()), List.of(), List.of(), List.of()))) {
                var locHandle = locScheduler.schedule(
                        locPayload, locJobs, List.of(), Map.of(),
                        aEndpoint -> Map.of(),
                        aEndpoint -> new AIiPublicationProgressReporter() {
                            @Override public void started(String aMessage) { }
                            @Override public void progress(long aCompleted, long aTotal, String aUnit, String aMessage) { }
                            @Override public void indeterminate(String aMessage) { }
                            @Override public void completed(String aMessage) { }
                        });
                locHandle.requiredCompletion().toCompletableFuture().get(10, TimeUnit.SECONDS);
                AIcPublicationCheckAssertions.assertTrue(locHandle.publicationResults().get("remote/standard").toCompletableFuture().join().success());
                AIcPublicationCheckAssertions.assertTrue(locHandle.publicationFinalizationActionResults().get("remote/standard/build-record").toCompletableFuture().join().result().success());
            }

            String locBase = "/maven/test/library/1.0-SNAPSHOT/";
            String locMainName = locJobs.get(0).payload().files().get(0).logicalName();
            String locRecordName = locMainName + ".modustro-build-record.yml";
            AIcPublicationCheckAssertions.assertTrue(locBytes.containsKey(locBase + locMainName));
            AIcPublicationCheckAssertions.assertTrue(locBytes.containsKey(locBase + locRecordName));
            AIcPublicationCheckAssertions.assertEquals(locPuts.get(locBase + locMainName).get(), 1);
            AIcPublicationCheckAssertions.assertEquals(locPuts.get(locBase + locRecordName).get(), 1);
            AIcPublicationCheckAssertions.assertTrue(locBytes.containsKey(locBase + "maven-metadata.xml"));
        } finally {
            locServer.stop(0);
        }
    }
}
