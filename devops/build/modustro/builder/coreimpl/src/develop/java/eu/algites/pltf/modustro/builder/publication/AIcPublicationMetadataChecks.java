package eu.algites.pltf.modustro.builder.publication;

import com.sun.net.httpserver.HttpServer;
import eu.algites.pltf.modustro.builder.model.publication.*;
import eu.algites.pltf.modustro.builder.publication.adapters.AIcMavenRepositoryPublicationAdapter;
import eu.algites.pltf.modustro.builder.publication.adapters.AIcS3ObjectStoragePublicationAdapter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Real HTTP checks for mixed Maven metadata and bounded S3 error diagnostics. */
public final class AIcPublicationMetadataChecks {
    public static void main(String[] aArguments) throws Exception { run(); System.out.println("PUBLICATION_METADATA_CHECKS_OK"); }
    public static void run() throws Exception { AIcMavenMetadata(); AIcS3Error(); }
    private static final AIiPublicationProgressReporter AIcProgress = new AIiPublicationProgressReporter() {
        @Override public void started(String aMessage) { }
        @Override public void progress(long aCompleted, long aTotal, String aUnit, String aMessage) { }
        @Override public void indeterminate(String aMessage) { }
        @Override public void completed(String aMessage) { }
    };
    private static AIcPublicationAttemptContext AIcContext(AIcPublicationJob aJob, Map<String, String> aCredentials) {
        return new AIcPublicationAttemptContext(aJob.endpoint(), aJob.payload(), 1, 1, null, null, aCredentials, () -> false, AIcProgress);
    }
    private static void AIcMavenMetadata() throws Exception {
        Map<String, byte[]> locStored = new ConcurrentHashMap<>();
        HttpServer locServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        locServer.createContext("/", aExchange -> {
            String locPath = aExchange.getRequestURI().getPath();
            if (aExchange.getRequestMethod().equals("PUT")) {
                locStored.put(locPath, aExchange.getRequestBody().readAllBytes()); aExchange.sendResponseHeaders(201, -1);
            } else {
                byte[] locData = locStored.get(locPath);
                if (locData == null) aExchange.sendResponseHeaders(404, -1);
                else { aExchange.sendResponseHeaders(200, locData.length); aExchange.getResponseBody().write(locData); }
            }
            aExchange.close();
        });
        locServer.start();
        try {
            Path locRoot = Files.createTempDirectory("modustro-module-");
            Path locJar = locRoot.resolve("library-1.0-SNAPSHOT.jar"); Files.writeString(locJar, "jar bytes");
            Path locPom = locRoot.resolve("library-1.0-SNAPSHOT.pom"); Files.writeString(locPom, "<project/>");
            Path locModule = locRoot.resolve("library-1.0-SNAPSHOT.module");
            String locJson = "{\"formatVersion\":\"1.1\",\"component\":{\"version\":\"1.0-SNAPSHOT\"},\"variants\":[{\"name\":\"apiElements\",\"dependencies\":[{\"version\":{\"requires\":\"1.0-SNAPSHOT\"}}],\"files\":[{\"name\":\"library-1.0-SNAPSHOT.jar\",\"url\":\"library-1.0-SNAPSHOT.jar\",\"sha256\":\"original-hash\"}]},{\"name\":\"sourcesElements\",\"files\":[{\"name\":\"library-1.0-SNAPSHOT-sources.jar\",\"url\":\"library-1.0-SNAPSHOT-sources.jar\"}]}]}";
            Files.writeString(locModule, locJson);
            var locCoords = Map.of("groupId", "test", "artifactId", "library", "version", "1.0-SNAPSHOT", "technologyKind", "java", "extension", "jar");
            var locPayload = new AIcPublicationPayload(AInPublicationOutputKind.NATIVE_PRODUCT_BINARIES, AInPublicationStability.SNAPSHOT,
                    "test:library", "1.0-SNAPSHOT", List.of(new AIcPublicationPayloadFile(locJar, locJar.getFileName().toString()),
                    new AIcPublicationPayloadFile(locPom, locPom.getFileName().toString()), new AIcPublicationPayloadFile(locModule, locModule.getFileName().toString())), locCoords);
            var locEndpoint = Map.<String, Object>of("Id", "http", "PublicationUri", "http://127.0.0.1:" + locServer.getAddress().getPort() + "/",
                    "PublicationAdapter", "maven-repository", "Publications", List.of(Map.of("Id", "standard"), Map.of("Id", "other", "Classifier", "other")));
            var locContext = Map.<String, Object>of("Invocation", Map.of("StartedAt", "2026-10-07T20:00:00.123456789Z"));
            var locJobs = new AIcPublicationPlanner().plan(locPayload, Map.of("PublicationEnabled", true, "PublicationEndpoints", List.of(locEndpoint)), locContext, locRoot);
            AIcPublicationCheckAssertions.assertEquals(locJobs.size(), 2);
            AIcPublicationCheckAssertions.assertEquals(locJobs.get(1).payload().files().size(), 1);
            var locJob = locJobs.get(0); String locVersion = locJob.payload().version();
            new AIcMavenRepositoryPublicationAdapter().publish(AIcContext(locJob, Map.of()));
            String locBase = "/test/library/1.0-SNAPSHOT/";
            String locPublished = new String(locStored.get(locBase + "library-" + locVersion + ".module"), StandardCharsets.UTF_8);
            AIcPublicationCheckAssertions.assertTrue(locPublished.contains("\"url\":\"library-" + locVersion + ".jar\""));
            AIcPublicationCheckAssertions.assertTrue(locPublished.contains("library-" + locVersion + "-sources.jar"));
            AIcPublicationCheckAssertions.assertTrue(locPublished.contains("\"requires\":\"1.0-SNAPSHOT\""));
            AIcPublicationCheckAssertions.assertTrue(locPublished.contains("\"version\":\"1.0-SNAPSHOT\""));
            AIcPublicationCheckAssertions.assertTrue(locPublished.contains("original-hash"));
            AIcPublicationCheckAssertions.assertEquals(Files.readString(locModule), locJson);
            Path locSources = locRoot.resolve("library-1.0-SNAPSHOT-sources.jar"); Files.writeString(locSources, "source bytes");
            var locSourceCoords = new java.util.HashMap<>(locCoords); locSourceCoords.put("classifier", "sources");
            var locSourcesPayload = new AIcPublicationPayload(AInPublicationOutputKind.NATIVE_PRODUCT_SOURCES, AInPublicationStability.SNAPSHOT,
                    "test:library", "1.0-SNAPSHOT", List.of(new AIcPublicationPayloadFile(locSources, locSources.getFileName().toString())), locSourceCoords);
            var locSourcesJob = new AIcPublicationPlanner().plan(locSourcesPayload, Map.of("PublicationEnabled", true, "PublicationEndpoints", List.of(
                    Map.of("Id", "http", "PublicationUri", "http://127.0.0.1:" + locServer.getAddress().getPort() + "/", "PublicationAdapter", "maven-repository"))), locContext, locRoot).get(0);
            new AIcMavenRepositoryPublicationAdapter().publish(AIcContext(locSourcesJob, Map.of()));
            AIcPublicationCheckAssertions.assertTrue(locStored.containsKey(locBase + "library-" + locVersion + "-sources.jar"));
            var locFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            var locDocument = locFactory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(locStored.get(locBase + "maven-metadata.xml")));
            var locEntries = locDocument.getElementsByTagName("snapshotVersion");
            java.util.Set<String> locForms = new java.util.HashSet<>();
            for (int locIndex = 0; locIndex < locEntries.getLength(); locIndex++) {
                var locEntry = (org.w3c.dom.Element) locEntries.item(locIndex);
                String locExt = locEntry.getElementsByTagName("extension").item(0).getTextContent();
                var locClassifiers = locEntry.getElementsByTagName("classifier");
                locForms.add(locExt + ":" + (locClassifiers.getLength() == 0 ? "" : locClassifiers.item(0).getTextContent()));
            }
            AIcPublicationCheckAssertions.assertEquals(locForms, java.util.Set.of("jar:", "pom:", "module:", "jar:sources"));
        } finally { locServer.stop(0); }
    }
    private static void AIcS3Error() throws Exception {
        HttpServer locServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        locServer.createContext("/", aExchange -> {
            aExchange.getRequestBody().readAllBytes();
            String locBody = "<Error><Code>SignatureDoesNotMatch</Code><Message>Rejected test-access test-secret</Message><RequestId>request-123</RequestId><Region>fr-par</Region><AccessKeyId>test-access</AccessKeyId><StringToSign>DO-NOT-LOG</StringToSign></Error>";
            byte[] locBytes = locBody.getBytes(StandardCharsets.UTF_8);
            aExchange.sendResponseHeaders(403, locBytes.length); aExchange.getResponseBody().write(locBytes); aExchange.close();
        });
        locServer.start();
        try {
            Path locRoot = Files.createTempDirectory("modustro-s3-error-"); Path locFile = locRoot.resolve("one.json"); Files.writeString(locFile, "{}");
            var locEndpoint = AIcPublicationPlanner.endpoint(Map.of("Id", "s3", "PublicationAdapter", "s3-object-storage", "PublicationUri", "http://127.0.0.1:" + locServer.getAddress().getPort() + "/bucket/", "Configuration", Map.of("Region", "fr-par")), "s3");
            var locPayload = new AIcPublicationPayload(AInPublicationOutputKind.SCHEMA_SITE, AInPublicationStability.SNAPSHOT, "repo", "1.0-SNAPSHOT", List.of(new AIcPublicationPayloadFile(locFile, "api/one.json")), Map.of());
            try {
                new AIcS3ObjectStoragePublicationAdapter().publish(new AIcPublicationAttemptContext(locEndpoint, locPayload, 1, 1, null, null,
                        Map.of("username", "test-access", "password", "test-secret"), () -> false, AIcProgress));
                throw new AssertionError("Expected S3 HTTP failure.");
            } catch (IllegalStateException locFailure) {
                String locMessage = locFailure.getMessage();
                AIcPublicationCheckAssertions.assertTrue(locMessage.contains("HTTP 403") && locMessage.contains("SignatureDoesNotMatch") && locMessage.contains("request-123") && locMessage.contains("fr-par"));
                AIcPublicationCheckAssertions.assertFalse(locMessage.contains("test-access") || locMessage.contains("test-secret") || locMessage.contains("DO-NOT-LOG"));
            }
        } finally { locServer.stop(0); }
    }
}
