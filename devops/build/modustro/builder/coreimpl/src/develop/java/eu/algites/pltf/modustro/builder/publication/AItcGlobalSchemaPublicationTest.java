package eu.algites.pltf.modustro.builder.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.sun.net.httpserver.HttpServer;
import eu.algites.pltf.modustro.builder.model.publication.*;
import eu.algites.pltf.modustro.builder.publication.adapters.AIcS3ObjectStoragePublicationAdapter;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Executable transport regressions with synthetic schemas and an S3-like server enforcing conditional writes. */
public final class AItcGlobalSchemaPublicationTest {
    private static final String AIcA = "api/jsondefs/test/a.json", AIcB = "api/jsondefs/test/b.json";
    private static final ObjectMapper AIcMapper = new ObjectMapper(new YAMLFactory());
    private static final Map<String, byte[]> AIcStored = new HashMap<>();
    private static final List<String> AIcPuts = new ArrayList<>();
    private static final List<String> AIcReads = new ArrayList<>();
    private static String AIcFailPut = "", AIcFailRead = "", AIcConflictPut = "";
    private static HttpServer AIcServer;
    private static Path AIcRoot;
    private static final AIiPublicationProgressReporter AIcProgress = new AIiPublicationProgressReporter() {
        @Override public void started(String aMessage) { }
        @Override public void progress(long aCompleted, long aTotal, String aUnit, String aMessage) { }
        @Override public void indeterminate(String aMessage) { }
        @Override public void completed(String aMessage) { }
    };
    public static void main(String[] aArguments) throws Exception {
        AIcRoot = Files.createTempDirectory("schema-regression-");
        AIcServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AIcServer.createContext("/", aExchange -> {
            String locName = aExchange.getRequestURI().getPath().substring(1);
            String locAuth = aExchange.getRequestHeaders().getFirst("Authorization");
            if (locAuth == null || !locAuth.startsWith("AWS4-HMAC-SHA256 Credential=fixture-access/")) throw new AssertionError("Unsigned request.");
            int locStatus;
            byte[] locBody = new byte[0];
            if ("GET".equals(aExchange.getRequestMethod())) {
                AIcReads.add(locName);
                AIcVerifySignature(aExchange, new byte[0]);
                if (locName.equals(AIcFailRead)) locStatus = 403;
                else {
                    locBody = AIcStored.get(locName);
                    locStatus = locBody == null ? 404 : 200;
                    if (locBody == null) locBody = new byte[0];
                    else aExchange.getResponseHeaders().set("ETag", AIcEtag(locBody));
                }
            } else {
                AIcPuts.add(locName);
                byte[] locBytes = aExchange.getRequestBody().readAllBytes();
                AIcVerifySignature(aExchange, locBytes);
                if (locName.equals(AIcFailPut)) locStatus = 403;
                else if (locName.equals(AIcConflictPut)) locStatus = 412;
                else {
                    byte[] locOld = AIcStored.get(locName);
                    String locIfNone = aExchange.getRequestHeaders().getFirst("If-None-Match");
                    String locIfMatch = aExchange.getRequestHeaders().getFirst("If-Match");
                    boolean locAllowed = locOld == null ? "*".equals(locIfNone) : AIcEtag(locOld).equals(locIfMatch);
                    locStatus = locAllowed ? 200 : 412;
                    if (locAllowed) AIcStored.put(locName, locBytes);
                }
            }
            aExchange.sendResponseHeaders(locStatus, locBody.length == 0 ? -1 : locBody.length);
            if (locBody.length > 0) aExchange.getResponseBody().write(locBody);
            aExchange.close();
        });
        AIcServer.start();
        try { AIcTests(); System.out.println("GLOBAL_SCHEMA_PUBLICATION_TESTS_OK"); }
        finally { AIcServer.stop(0); }
    }
    private static void AIcVerifySignature(com.sun.net.httpserver.HttpExchange aExchange, byte[] aBytes) {
        try {
            String locHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(aBytes));
            AIcPublicationCheckAssertions.assertEquals(aExchange.getRequestHeaders().getFirst("x-amz-content-sha256"), locHash);
            String locDateTime = aExchange.getRequestHeaders().getFirst("x-amz-date"), locDate = locDateTime.substring(0, 8);
            String locHost = aExchange.getRequestHeaders().getFirst("Host");
            String locScope = locDate + "/fr-par/s3/aws4_request";
            String locSignedHeaders = "host;x-amz-content-sha256;x-amz-date";
            String locCanonical = aExchange.getRequestMethod() + "\n" + aExchange.getRequestURI().getRawPath() +
                    "\n\nhost:" + locHost + "\nx-amz-content-sha256:" + locHash + "\nx-amz-date:" + locDateTime +
                    "\n\n" + locSignedHeaders + "\n" + locHash;
            String locCanonicalHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(locCanonical.getBytes(StandardCharsets.UTF_8)));
            byte[] locKey = AIcHmac(("AWS4fixture-secret").getBytes(StandardCharsets.UTF_8), locDate);
            locKey = AIcHmac(AIcHmac(AIcHmac(locKey, "fr-par"), "s3"), "aws4_request");
            String locSignature = java.util.HexFormat.of().formatHex(AIcHmac(locKey, "AWS4-HMAC-SHA256\n" + locDateTime + "\n" + locScope + "\n" + locCanonicalHash));
            AIcPublicationCheckAssertions.assertEquals(aExchange.getRequestHeaders().getFirst("Authorization"),
                    "AWS4-HMAC-SHA256 Credential=fixture-access/" + locScope + ", SignedHeaders=" + locSignedHeaders + ", Signature=" + locSignature);
        } catch (Exception locFailure) { throw new IllegalStateException(locFailure); }
    }
    private static byte[] AIcHmac(byte[] aKey, String aValue) throws Exception {
        var locMac = javax.crypto.Mac.getInstance("HmacSHA256");
        locMac.init(new javax.crypto.spec.SecretKeySpec(aKey, "HmacSHA256"));
        return locMac.doFinal(aValue.getBytes(StandardCharsets.UTF_8));
    }
    private static String AIcEtag(byte[] aBytes) { return "\"" + Arrays.hashCode(aBytes) + "\""; }
    private static void AIcReset() { AIcStored.clear(); AIcPuts.clear(); AIcReads.clear(); AIcFailPut = ""; AIcFailRead = ""; AIcConflictPut = ""; }
    private static List<AIcPublicationPayloadFile> AIcLocal(String... aNames) throws Exception {
        List<AIcPublicationPayloadFile> locFiles = new ArrayList<>();
        for (String locName : aNames) {
            Path locPath = AIcRoot.resolve(locName); Files.createDirectories(locPath.getParent()); Files.writeString(locPath, "{\"title\":\"new\"}");
            Path locMeta = Path.of(locPath + ".meta.yml"); Files.writeString(locMeta, "GlobalPublicationPathId: " + locName.substring(locName.indexOf('/', 4) + 1) + "\n");
            locFiles.add(new AIcPublicationPayloadFile(locPath, locName)); locFiles.add(new AIcPublicationPayloadFile(locMeta, locName + ".meta.yml"));
        }
        return locFiles;
    }
    private static void AIcPublish(List<AIcPublicationPayloadFile> aFiles, String aState, boolean aAdopt) throws Exception {
        var locPayload = new AIcPublicationPayload(AInPublicationOutputKind.SCHEMA_SITE,
                "release".equals(aState) ? AInPublicationStability.RELEASE : AInPublicationStability.SNAPSHOT,
                "fixture", aState, aFiles, Map.of("groupId", "test", "schemaAdoptUntrackedDrafts", Boolean.toString(aAdopt)));
        var locEndpoint = AIcPublicationPlanner.endpoint(Map.of("Id", "fixture", "PublicationAdapter", "s3-object-storage", "Configuration", Map.of("Region", "fr-par"),
                "PublicationUri", "http://127.0.0.1:" + AIcServer.getAddress().getPort() + "/"), "fixture");
        new AIcS3ObjectStoragePublicationAdapter().publish(new AIcPublicationAttemptContext(locEndpoint, locPayload, 1, 1, null, null,
                Map.of("username", "fixture-access", "password", "fixture-secret"), () -> false, AIcProgress));
    }
    private static Exception AIcFailure(List<AIcPublicationPayloadFile> aFiles, String aState, boolean aAdopt) throws Exception {
        try { AIcPublish(aFiles, aState, aAdopt); } catch (Exception locExpected) { return locExpected; }
        throw new AssertionError("Expected failure.");
    }
    private static JsonNodeHolder AIcMetadata() throws Exception { return new JsonNodeHolder(AIcMapper.readTree(AIcStored.get(AIcA + ".meta.yml"))); }
    private record JsonNodeHolder(com.fasterxml.jackson.databind.JsonNode root) { }
    private static void AIcExisting(String aState, long aRevision, String aBody) throws Exception {
        AIcStored.put(AIcA, aBody.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> locMetadata = new LinkedHashMap<>(Map.of("GlobalPublicationPathId", "test/a.json", "PublicationState", aState,
                "PublicationRevision", aRevision, "FirstPublishedAt", "2026-01-01T00:00:00Z", "PublishedAt", "2026-01-02T00:00:00Z"));
        locMetadata.put("ContentSha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(aBody.getBytes(StandardCharsets.UTF_8))));
        if (aState.equals("release")) locMetadata.put("ReleasedAt", "2026-01-02T00:00:00Z");
        AIcStored.put(AIcA + ".meta.yml", AIcMapper.writeValueAsBytes(locMetadata));
    }
    private static void AIcTests() throws Exception {
        AIcReset(); var locFiles = AIcLocal(AIcA, AIcB); AIcPublish(locFiles, "draft", false);
        AIcPublicationCheckAssertions.assertEquals(AIcPuts, List.of(AIcA, AIcA + ".meta.yml", AIcB, AIcB + ".meta.yml"));
        AIcPublicationCheckAssertions.assertEquals(AIcMetadata().root().get("PublicationRevision").longValue(), 1L);
        AIcPublicationCheckAssertions.assertTrue(AIcMetadata().root().has("$schema"));
        /* A canonical definition may live at a flat physical source path while its
         * author-declared publication identity contains a stable package namespace. */
        AIcReset();
        String locLogicalId = "eu/algites/tool/build/yamldefs/algites-credential-profiles_1.yamldef.schema.json";
        String locLogicalTarget = "api/yamldefs/" + locLogicalId;
        Path locPhysicalDefinition = AIcRoot.resolve("physical-yamldefs/algites-credential-profiles_1.yamldef.schema.json");
        Files.createDirectories(locPhysicalDefinition.getParent());
        Files.writeString(locPhysicalDefinition, "{\"title\":\"credential profiles\"}");
        Path locPhysicalSidecar = Path.of(locPhysicalDefinition + ".meta.yml");
        Files.writeString(locPhysicalSidecar, "GlobalPublicationPathId: " + locLogicalId + "\n");
        AIcPublish(List.of(new AIcPublicationPayloadFile(locPhysicalDefinition, locLogicalTarget),
                new AIcPublicationPayloadFile(locPhysicalSidecar, locLogicalTarget + ".meta.yml")), "draft", false);
        AIcPublicationCheckAssertions.assertEquals(AIcPuts, List.of(locLogicalTarget, locLogicalTarget + ".meta.yml"));
        AIcPublicationCheckAssertions.assertEquals(
                AIcMapper.readTree(AIcStored.get(locLogicalTarget + ".meta.yml")).get("GlobalPublicationPathId").asText(), locLogicalId);
        AIcReset(); locFiles = AIcLocal(AIcA); AIcFailure(List.of(locFiles.get(0)), "draft", false);
        AIcPublicationCheckAssertions.assertTrue(AIcReads.isEmpty() && AIcPuts.isEmpty());
        AIcReset(); locFiles = AIcLocal(AIcA); Files.writeString(locFiles.get(1).path(), "GlobalPublicationPathId: test/a.json\nPublicationState: release\n");
        AIcFailure(locFiles, "draft", false); AIcPublicationCheckAssertions.assertTrue(AIcReads.isEmpty());
        AIcReset(); locFiles = AIcLocal(AIcA); Files.writeString(locFiles.get(1).path(), "GlobalPublicationPathId: test/a.json\nGlobalPublicationPathId: test/a.json\n");
        AIcFailure(locFiles, "draft", false); AIcPublicationCheckAssertions.assertTrue(AIcReads.isEmpty());
        AIcReset(); locFiles = AIcLocal(AIcA); AIcStored.put(AIcA, Files.readAllBytes(locFiles.get(0).path()));
        AIcFailure(locFiles, "draft", false); AIcPublicationCheckAssertions.assertTrue(AIcPuts.isEmpty());
        AIcPublish(locFiles, "draft", true); AIcPublicationCheckAssertions.assertEquals(AIcPuts, List.of(AIcA + ".meta.yml"));
        AIcReset(); locFiles = AIcLocal(AIcA); AIcStored.put(AIcA, "different".getBytes(StandardCharsets.UTF_8));
        AIcFailure(locFiles, "draft", true); AIcPublicationCheckAssertions.assertTrue(AIcPuts.isEmpty());
        AIcReset(); locFiles = AIcLocal(AIcA); AIcStored.put(AIcA, Files.readAllBytes(locFiles.get(0).path()));
        AIcFailure(locFiles, "release", true); AIcPublicationCheckAssertions.assertTrue(AIcPuts.isEmpty());
        AIcReset(); locFiles = AIcLocal(AIcA, AIcB); AIcFailRead = AIcB + ".meta.yml";
        AIcFailure(locFiles, "draft", false); AIcPublicationCheckAssertions.assertTrue(AIcPuts.isEmpty());
        AIcReset(); locFiles = AIcLocal(AIcA, AIcB); AIcFailPut = AIcA + ".meta.yml";
        AIcFailure(locFiles, "draft", false); AIcPublicationCheckAssertions.assertEquals(AIcPuts, List.of(AIcA, AIcA + ".meta.yml"));
        AIcPublicationCheckAssertions.assertFalse(AIcStored.containsKey(AIcB));
        AIcReset(); locFiles = AIcLocal(AIcA); AIcExisting("draft", 6, "old"); AIcPublish(locFiles, "draft", false);
        AIcPublicationCheckAssertions.assertEquals(AIcMetadata().root().get("PublicationRevision").longValue(), 7L);
        AIcPublicationCheckAssertions.assertEquals(AIcMetadata().root().get("FirstPublishedAt").asText(), "2026-01-01T00:00:00Z");
        AIcPuts.clear(); AIcPublish(locFiles, "release", false);
        AIcPublicationCheckAssertions.assertEquals(AIcMetadata().root().get("PublicationRevision").longValue(), 7L);
        AIcPublicationCheckAssertions.assertTrue(AIcMetadata().root().has("ReleasedAt"));
        AIcPuts.clear(); AIcPublish(locFiles, "release", false); AIcPublicationCheckAssertions.assertTrue(AIcPuts.isEmpty());
        AIcFailure(locFiles, "draft", false); AIcPublicationCheckAssertions.assertTrue(AIcPuts.isEmpty());
        Files.writeString(locFiles.get(0).path(), "modified-release"); AIcFailure(locFiles, "release", false); AIcPublicationCheckAssertions.assertTrue(AIcPuts.isEmpty());
        AIcReset(); locFiles = AIcLocal(AIcA); AIcExisting("draft", 1, "old"); AIcFailPut = AIcA + ".meta.yml";
        AIcFailure(locFiles, "draft", false); AIcPuts.clear(); AIcFailPut = "";
        AIcFailure(locFiles, "draft", false); AIcPublicationCheckAssertions.assertTrue(AIcPuts.isEmpty());
        AIcReset(); locFiles = AIcLocal(AIcA); AIcConflictPut = AIcA;
        AIcFailure(locFiles, "draft", false); AIcPublicationCheckAssertions.assertEquals(AIcPuts, List.of(AIcA));
        AIcReset(); locFiles = AIcLocal(AIcA); AIcStored.put(AIcA + ".meta.yml", "bad".getBytes(StandardCharsets.UTF_8));
        AIcFailure(locFiles, "draft", false); AIcPublicationCheckAssertions.assertTrue(AIcPuts.isEmpty());
        AIcReset(); locFiles = AIcLocal(AIcA); AIcExisting("draft", 1, "old"); AIcStored.put(AIcA + ".meta.yml", "broken".getBytes(StandardCharsets.UTF_8));
        AIcFailure(locFiles, "draft", false); AIcPublicationCheckAssertions.assertTrue(AIcPuts.isEmpty());
    }
}
