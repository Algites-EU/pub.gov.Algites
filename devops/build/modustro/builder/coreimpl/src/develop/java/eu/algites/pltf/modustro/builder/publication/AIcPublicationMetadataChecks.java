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

/**
 * Executable regression tests using synthetic publication payloads and a local HTTP server.
 * These tests exercise publisher behavior; they do not validate the metadata of the current project build.
 */
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
        AIcMavenMetadata("1.0-SNAPSHOT", "4.2.0", true);
        AIcMavenMetadata("2.1-SNAPSHOT", "8.3.0", true);
        AIcMavenMetadata("2.7.13-SNAPSHOT", "0.8.4-SNAPSHOT", true);
        AIcMavenMetadata("0.9.0-rc.2-SNAPSHOT", "7.1.3", true);
        AIcMavenMetadata("2.7.13", "4.2.0-SNAPSHOT", false);
        AIcMavenMetadata("0.9.0-rc.2", "7.1.3", false);
        AIcMavenMetadata("3.14.159", "0.8.4", false);
    }
    private static void AIcMavenMetadata(String aArtifactVersion, String aDependencyVersion, boolean aSnapshot) throws Exception {
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
            String locPrefix = "library-" + aArtifactVersion;
            Path locJar = locRoot.resolve(locPrefix + ".jar"); Files.writeString(locJar, "jar bytes");
            Path locPom = locRoot.resolve(locPrefix + ".pom"); Files.writeString(locPom, "<project/>");
            Path locModule = locRoot.resolve(locPrefix + ".module");
            String locJson = """
                    {"formatVersion":"1.1","component":{"version":"%s"},"variants":[
                      {"name":"apiElements","dependencies":[{"version":{"requires":"%s"}}],
                       "files":[{"name":"%s.jar","url":"%s.jar","sha256":"original-hash"}]},
                      {"name":"sourcesElements","files":[{"name":"%s-sources.jar","url":"%s-sources.jar"}]}]}
                    """.formatted(aArtifactVersion, aDependencyVersion, locPrefix, locPrefix, locPrefix, locPrefix);
            Files.writeString(locModule, locJson);
            var locCoords = Map.of("groupId", "test", "artifactId", "library", "version", aArtifactVersion, "technologyKind", "java", "extension", "jar");
            AInPublicationStability locStability = aSnapshot ? AInPublicationStability.SNAPSHOT : AInPublicationStability.RELEASE;
            var locPayload = new AIcPublicationPayload(AInPublicationOutputKind.NATIVE_PRODUCT_BINARIES,
                    locStability, "test:library", aArtifactVersion, List.of(new AIcPublicationPayloadFile(locJar, locJar.getFileName().toString()),
                    new AIcPublicationPayloadFile(locPom, locPom.getFileName().toString()), new AIcPublicationPayloadFile(locModule, locModule.getFileName().toString())), locCoords);
            var locEndpoint = Map.<String, Object>of("Id", "http", "PublicationUri", "http://127.0.0.1:" + locServer.getAddress().getPort() + "/",
                    "PublicationAdapter", "maven-repository", "Publications", List.of(Map.of("Id", "standard"), Map.of("Id", "other", "Classifier", "other")));
            var locContext = Map.<String, Object>of("Invocation", Map.of("StartedAt", "2026-10-07T20:00:00.123456789Z"));
            var locJobs = new AIcPublicationPlanner().plan(locPayload, Map.of("PublicationEnabled", true, "PublicationEndpoints", List.of(locEndpoint)), locContext, locRoot);
            AIcPublicationCheckAssertions.assertEquals(locJobs.size(), 2);
            AIcPublicationCheckAssertions.assertEquals(locJobs.get(1).payload().files().size(), 1);
            var locJob = locJobs.get(0); String locVersion = locJob.payload().version();
            new AIcMavenRepositoryPublicationAdapter().publish(AIcContext(locJob, Map.of()));
            String locExpectedVersion = aSnapshot
                    ? aArtifactVersion.substring(0, aArtifactVersion.length() - "-SNAPSHOT".length()) + "-20261007.200000-123456790"
                    : aArtifactVersion;
            AIcPublicationCheckAssertions.assertEquals(locVersion, locExpectedVersion);
            String locBase = "/test/library/" + aArtifactVersion + "/";
            String locPublished = new String(locStored.get(locBase + "library-" + locVersion + ".module"), StandardCharsets.UTF_8);
            /* Standard publication must preserve every logical filename, constraint and hash byte for byte. */
            AIcPublicationCheckAssertions.assertEquals(locPublished, locJson);
            AIcPublicationCheckAssertions.assertTrue(locPublished.contains("\"url\":\"" + locPrefix + ".jar\""));
            AIcPublicationCheckAssertions.assertTrue(locPublished.contains(locPrefix + "-sources.jar"));
            AIcPublicationCheckAssertions.assertTrue(locPublished.contains("\"requires\":\"" + aDependencyVersion + "\""));
            AIcPublicationCheckAssertions.assertTrue(locPublished.contains("\"version\":\"" + aArtifactVersion + "\""));
            AIcPublicationCheckAssertions.assertEquals(Files.readString(locModule), locJson);
            Path locSources = locRoot.resolve(locPrefix + "-sources.jar"); Files.writeString(locSources, "source bytes");
            var locSourceCoords = new java.util.HashMap<>(locCoords); locSourceCoords.put("classifier", "sources");
            var locSourcesPayload = new AIcPublicationPayload(AInPublicationOutputKind.NATIVE_PRODUCT_SOURCES, locStability,
                    "test:library", aArtifactVersion, List.of(new AIcPublicationPayloadFile(locSources, locSources.getFileName().toString())), locSourceCoords);
            var locSourcesJob = new AIcPublicationPlanner().plan(locSourcesPayload, Map.of("PublicationEnabled", true, "PublicationEndpoints", List.of(
                    Map.of("Id", "http", "PublicationUri", "http://127.0.0.1:" + locServer.getAddress().getPort() + "/", "PublicationAdapter", "maven-repository"))), locContext, locRoot).get(0);
            new AIcMavenRepositoryPublicationAdapter().publish(AIcContext(locSourcesJob, Map.of()));
            AIcPublicationCheckAssertions.assertTrue(locStored.containsKey(locBase + "library-" + locVersion + "-sources.jar"));
            if (aSnapshot) {
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
                for (int locIndex = 0; locIndex < locEntries.getLength(); locIndex++) {
                    var locEntry = (org.w3c.dom.Element) locEntries.item(locIndex);
                    AIcPublicationCheckAssertions.assertEquals(locEntry.getElementsByTagName("value").item(0).getTextContent(), locExpectedVersion);
                }
            } else {
                AIcPublicationCheckAssertions.assertFalse(locStored.containsKey(locBase + "maven-metadata.xml"));
            }
            System.out.println("MAVEN_METADATA_OK " + aArtifactVersion + " dependency=" + aDependencyVersion);
        } finally { locServer.stop(0); }
    }
    private static void AIcS3Error() throws Exception {
        String locUsername = "fixture-public-access-key";
        String locSecret = "fixture-private-signing-secret";
        String locPrefix = locUsername.substring(0, locUsername.length() / 2) + "...";
        java.util.List<String> locMessages = new java.util.concurrent.CopyOnWriteArrayList<>();
        AIiPublicationProgressReporter locReporter = new AIiPublicationProgressReporter() {
            @Override public void started(String aMessage) { locMessages.add(aMessage); }
            @Override public void progress(long aCompleted, long aTotal, String aUnit, String aMessage) { locMessages.add(aMessage); }
            @Override public void indeterminate(String aMessage) { locMessages.add(aMessage); }
            @Override public void completed(String aMessage) { locMessages.add(aMessage); }
        };
        HttpServer locServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        locServer.createContext("/", aExchange -> {
            aExchange.getRequestBody().readAllBytes();
            String locBody = "<Error><Code>SignatureDoesNotMatch</Code><Message>Rejected " + locUsername + " " + locSecret + "</Message><RequestId>request-123</RequestId><Region>fr-par</Region><AccessKeyId>" + locUsername + "</AccessKeyId><StringToSign>DO-NOT-LOG</StringToSign></Error>";
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
                        Map.of("username", locUsername, "password", locSecret), () -> false, locReporter));
                throw new AssertionError("Expected S3 HTTP failure.");
            } catch (IllegalStateException locFailure) {
                String locMessage = locFailure.getMessage();
                AIcPublicationCheckAssertions.assertTrue(locMessage.contains("HTTP 403") && locMessage.contains("SignatureDoesNotMatch") && locMessage.contains("request-123") && locMessage.contains("fr-par"));
                AIcPublicationCheckAssertions.assertTrue(locMessage.contains("UsernamePrefix=" + locPrefix));
                AIcPublicationCheckAssertions.assertTrue(locMessages.stream().anyMatch(aMessage -> aMessage.contains("UsernamePrefix=" + locPrefix) && aMessage.contains("Region=fr-par")));
                for (String locProgressMessage : locMessages) {
                    AIcPublicationCheckAssertions.assertFalse(locProgressMessage.contains(locUsername) || locProgressMessage.contains(locSecret) || locProgressMessage.contains("Signature="));
                }
                AIcPublicationCheckAssertions.assertFalse(locMessage.contains(locUsername) || locMessage.contains(locSecret) || locMessage.contains("DO-NOT-LOG"));
            }
        } finally { locServer.stop(0); }
    }
}
