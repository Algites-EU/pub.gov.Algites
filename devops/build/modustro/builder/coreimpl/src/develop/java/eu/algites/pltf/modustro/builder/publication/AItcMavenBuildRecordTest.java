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
import org.testng.Assert;
import org.testng.annotations.Test;

/** Exercises a real Maven HTTP publication followed by the implicit build-record action. */
public final class AItcMavenBuildRecordTest {
    @Test
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
            Assert.assertEquals(locJobs.size(), 1);
            Assert.assertEquals(locJobs.get(0).postPublicationActions().size(), 1);
            Assert.assertEquals(locJobs.get(0).postPublicationActions().get(0).id(), "build-record");

            try (AIcPublicationScheduler locScheduler = new AIcPublicationScheduler(
                    List.of(new AIcMavenRepositoryPublicationAdapter()),
                    List.of(new AIcBuildRecordPostPublicationActionAdapter()))) {
                var locHandle = locScheduler.schedule(
                        locJobs,
                        aEndpoint -> Map.of(),
                        aEndpoint -> new AIiPublicationProgressReporter() {
                            @Override public void started(String aMessage) { }
                            @Override public void progress(long aCompleted, long aTotal, String aUnit, String aMessage) { }
                            @Override public void indeterminate(String aMessage) { }
                            @Override public void completed(String aMessage) { }
                        });
                locHandle.requiredCompletion().toCompletableFuture().get(10, TimeUnit.SECONDS);
                Assert.assertTrue(locHandle.publicationResults().get("remote/standard").join().success());
                Assert.assertTrue(locHandle.postPublicationActionResults().get("remote/standard/build-record").join().success());
            }

            String locBase = "/maven/test/library/1.0-SNAPSHOT/";
            String locMainName = locJobs.get(0).payload().files().get(0).logicalName();
            String locRecordName = locMainName + ".modustro-build-record.yml";
            Assert.assertTrue(locBytes.containsKey(locBase + locMainName));
            Assert.assertTrue(locBytes.containsKey(locBase + locRecordName));
            Assert.assertEquals(locPuts.get(locBase + locMainName).get(), 1);
            Assert.assertEquals(locPuts.get(locBase + locRecordName).get(), 1);
            Assert.assertTrue(locBytes.containsKey(locBase + "maven-metadata.xml"));
        } finally {
            locServer.stop(0);
        }
    }
}
