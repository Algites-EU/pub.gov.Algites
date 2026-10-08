package eu.algites.pltf.modustro.builder.publication.adapters;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import eu.algites.pltf.modustro.builder.model.publication.*;
import eu.algites.pltf.modustro.builder.publication.AIcGlobalPublicationDeployMetadataResolver;
import eu.algites.pltf.modustro.builder.publication.AIcGlobalPublicationPathValidator;
import eu.algites.pltf.modustro.builder.publication.AIcPublicationAttemptContext;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Validates global schema state before writing, then commits each definition and its deploy sidecar together. */
final class AIcS3SchemaPublication {
    private static final String AIcSchema = "https://defs.dev.algites.eu/api/yamldefs/eu/algites/pltf/modustro/builder/publication/global-publication-deploy-metadata_1.yamldef.schema.json";
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION));
    private record AIcRemote(byte[] bytes, String etag) { }
    private record AIcPair(String name, byte[] content, byte[] metadata, AIcRemote previousContent, AIcRemote previousMetadata, boolean writeContent) { }

    void publish(AIcPublicationAttemptContext aContext) throws Exception {
        Map<String, AIcPublicationPayloadFile> locFiles = new TreeMap<>();
        for (var locFile : aContext.payload().files()) {
            if (locFiles.putIfAbsent(locFile.logicalName(), locFile) != null) throw new IllegalArgumentException("Duplicate schema payload file.");
        }
        String locKey = aContext.credentials().get("username"), locSecret = aContext.credentials().get("password");
        if (locKey == null || locSecret == null || locKey.isBlank() || locSecret.isBlank()) throw new IllegalArgumentException("Schema publication requires non-empty access and secret keys.");
        aContext.progressReporter().started("Validating schema pairs. UsernamePrefix=" +
                AIcS3ObjectStoragePublicationAdapter.AIcUsernamePrefix(locKey, locSecret) + ";");
        List<AIcPair> locPairs = new ArrayList<>();
        var locResolver = new AIcGlobalPublicationDeployMetadataResolver();
        var locState = switch (Objects.toString(aContext.payload().version(), "")) {
            case "draft" -> AInGlobalPublicationState.DRAFT;
            case "release" -> AInGlobalPublicationState.RELEASE;
            default -> throw new IllegalArgumentException("Schema publication requires draft or release state.");
        };
        boolean locAdopt = "true".equals(aContext.payload().coordinates().get("schemaAdoptUntrackedDrafts"));
        Instant locNow = Instant.now();
        /* Validate every local pair before making any network requests. */
        for (var locEntry : locFiles.entrySet()) {
            String locName = locEntry.getKey();
            if (locName.endsWith(".meta.yml")) {
                if (!locFiles.containsKey(locName.substring(0, locName.length() - 9))) throw new IllegalArgumentException("Orphan schema user sidecar: " + locName);
                continue;
            }
            if (!locName.matches("api/(jsondefs|yamldefs|xmldefs)/.+")) throw new IllegalArgumentException("Unexpected schema payload: " + locName);
            var locSidecar = locFiles.get(locName + ".meta.yml");
            if (locSidecar == null || !Files.isRegularFile(locSidecar.path())) throw new IllegalArgumentException("Missing required schema user sidecar: " + locName + ".meta.yml; no objects uploaded.");
            String locPathId = locName.substring(locName.indexOf('/', 4) + 1);
            JsonNode locUser = AIcDocument(Files.readAllBytes(locSidecar.path()), Set.of("$schema", "GlobalPublicationPathId"));
            if (!locPathId.equals(AIcText(locUser, "GlobalPublicationPathId"))) throw new IllegalArgumentException("Schema user sidecar path mismatch: " + locName);
            new AIcGlobalPublicationPathValidator().validate(locPathId);
        }
        /* Read and validate all existing deployment state before the first PUT. A read failure is never absence. */
        for (var locEntry : locFiles.entrySet()) {
            String locName = locEntry.getKey();
            if (locName.endsWith(".meta.yml")) continue;
            String locPathId = locName.substring(locName.indexOf('/', 4) + 1);
            if (Files.size(locEntry.getValue().path()) > 16 * 1024 * 1024) throw new IllegalArgumentException("Schema exceeds 16 MiB: " + locName);
            byte[] locContent = Files.readAllBytes(locEntry.getValue().path());
            AIcRemote locPreviousMetadata = AIcRequest(aContext, "GET", locName + ".meta.yml", null, null);
            AIcRemote locPreviousContent = AIcRequest(aContext, "GET", locName, null, null);
            AIcGlobalPublicationDeployMetadata locMetadata;
            boolean locWriteContent = true;
            if (locPreviousMetadata == null) {
                if (locPreviousContent != null) {
                    if (!locAdopt || locState != AInGlobalPublicationState.DRAFT || !Arrays.equals(locContent, locPreviousContent.bytes())) {
                        throw new IllegalStateException("Existing schema has no deploy sidecar: " + locName + "; refusing overwrite. " +
                                "Only an explicit adoption of byte-identical draft content may initialize metadata.");
                    }
                    locWriteContent = false;
                }
                locMetadata = locResolver.initial(locPathId, locState, locNow, null);
            } else {
                if (locPreviousContent == null) throw new IllegalStateException("Deploy sidecar exists without its schema: " + locName + "; no objects uploaded.");
                JsonNode locStoredMetadata = AIcDocument(locPreviousMetadata.bytes(), Set.of("$schema", "GlobalPublicationPathId", "PublicationState", "PublicationRevision",
                        "FirstPublishedAt", "PublishedAt", "ReleasedAt", "PublishedBy", "ContentSha256"));
                String locStoredHash = AIcText(locStoredMetadata, "ContentSha256");
                if (!locStoredHash.equals(AIcS3ObjectStoragePublicationAdapter.AIcSha256(locPreviousContent.bytes()))) {
                    throw new IllegalStateException("Schema content does not match its deploy sidecar hash: " + locName + "; refusing overwrite.");
                }
                var locPrevious = AIcMetadata(locPreviousMetadata.bytes());
                if (!locPathId.equals(locPrevious.globalPublicationPathId())) throw new IllegalStateException("Existing deploy sidecar path mismatch: " + locName);
                boolean locChanged = !Arrays.equals(locContent, locPreviousContent.bytes());
                locMetadata = locResolver.transition(locPrevious, locState, locChanged, locNow, null);
                if (locPrevious.publicationState() == AInGlobalPublicationState.RELEASE) continue;
                locWriteContent = locChanged;
            }
            locPairs.add(new AIcPair(locName, locContent, AIcSerialize(locMetadata, locContent), locPreviousContent, locPreviousMetadata, locWriteContent));
        }
        aContext.progressReporter().started("Publishing validated schema/deploy-sidecar pairs.");
        long locCompleted = 0;
        for (var locPair : locPairs) {
            if (locPair.writeContent()) AIcRequest(aContext, "PUT", locPair.name(), locPair.content(), locPair.previousContent());
            try {
                AIcRequest(aContext, "PUT", locPair.name() + ".meta.yml", locPair.metadata(), locPair.previousMetadata());
            } catch (Exception locFailure) {
                throw new IllegalStateException("Required deploy sidecar upload failed for " + locPair.name() +
                        "; publication stopped before the next definition. The current two-object pair may be incomplete.", locFailure);
            }
            locCompleted++;
            aContext.progressReporter().progress(locCompleted, locPairs.size(), "schema pairs", "Published " + locPair.name() + " and deploy sidecar");
        }
        aContext.progressReporter().completed("Schema publication completed with required deploy sidecars.");
    }

    private JsonNode AIcDocument(byte[] aBytes, Set<String> aFields) throws Exception {
        if (aBytes.length > 65536) throw new IllegalArgumentException("Schema sidecar exceeds 64 KiB.");
        try (var locParser = mapper.createParser(aBytes)) {
            JsonNode locRoot = mapper.readTree(locParser);
            if (locRoot == null || !locRoot.isObject() || locParser.nextToken() != null) throw new IllegalArgumentException("Sidecar must contain one metadata object.");
            var locFields = locRoot.fieldNames();
            while (locFields.hasNext()) {
                String locField = locFields.next();
                if (!aFields.contains(locField)) throw new IllegalArgumentException("Unexpected sidecar field: " + locField);
            }
            return locRoot;
        }
    }
    private AIcGlobalPublicationDeployMetadata AIcMetadata(byte[] aBytes) throws Exception {
        JsonNode locRoot = AIcDocument(aBytes, Set.of("$schema", "GlobalPublicationPathId", "PublicationState", "PublicationRevision",
                "FirstPublishedAt", "PublishedAt", "ReleasedAt", "PublishedBy", "ContentSha256"));
        var locRevision = locRoot.get("PublicationRevision");
        if (locRevision == null || !locRevision.isIntegralNumber() || !locRevision.canConvertToLong()) throw new IllegalArgumentException("Invalid PublicationRevision.");
        var locState = switch (AIcText(locRoot, "PublicationState")) {
            case "draft" -> AInGlobalPublicationState.DRAFT;
            case "release" -> AInGlobalPublicationState.RELEASE;
            default -> throw new IllegalArgumentException("Invalid PublicationState.");
        };
        return new AIcGlobalPublicationDeployMetadata(AIcText(locRoot, "GlobalPublicationPathId"), locState, locRevision.longValue(),
                Instant.parse(AIcText(locRoot, "FirstPublishedAt")), Instant.parse(AIcText(locRoot, "PublishedAt")),
                locRoot.has("ReleasedAt") ? Instant.parse(AIcText(locRoot, "ReleasedAt")) : null,
                locRoot.has("PublishedBy") ? AIcText(locRoot, "PublishedBy") : null);
    }
    private static String AIcText(JsonNode aRoot, String aName) {
        var locValue = aRoot.get(aName);
        if (locValue == null || !locValue.isTextual() || locValue.textValue().isBlank()) throw new IllegalArgumentException("Missing or invalid sidecar field: " + aName);
        return locValue.textValue();
    }
    private byte[] AIcSerialize(AIcGlobalPublicationDeployMetadata aMetadata, byte[] aContent) throws Exception {
        Map<String, Object> locFields = new LinkedHashMap<>();
        locFields.put("$schema", AIcSchema);
        locFields.put("ContentSha256", AIcS3ObjectStoragePublicationAdapter.AIcSha256(aContent));
        locFields.put("GlobalPublicationPathId", aMetadata.globalPublicationPathId());
        locFields.put("PublicationState", aMetadata.publicationState() == AInGlobalPublicationState.DRAFT ? "draft" : "release");
        locFields.put("PublicationRevision", aMetadata.publicationRevision());
        locFields.put("FirstPublishedAt", aMetadata.firstPublishedAt().toString());
        locFields.put("PublishedAt", aMetadata.publishedAt().toString());
        if (aMetadata.releasedAt() != null) locFields.put("ReleasedAt", aMetadata.releasedAt().toString());
        return mapper.writeValueAsBytes(locFields);
    }

    private static AIcRemote AIcRequest(AIcPublicationAttemptContext aContext, String aMethod, String aName,
            byte[] aBytes, AIcRemote aPrevious) throws Exception {
        AIcS3ObjectStoragePublicationAdapter.AIcCheckCancellation(aContext);
        var locEndpoint = aContext.endpoint();
        URI locTarget = AIcS3ObjectStoragePublicationAdapter.AIcTarget(AIcS3ObjectStoragePublicationAdapter.AIcRoot(locEndpoint.publicationUri()), aName);
        String locKey = aContext.credentials().get("username"), locSecret = aContext.credentials().get("password");
        if (locKey == null || locSecret == null) throw new IllegalArgumentException("Schema publication requires access key Username and secret key Password.");
        String locRegion = Objects.toString(locEndpoint.configuration().getOrDefault("Region", locEndpoint.configuration().getOrDefault("region", "us-east-1")));
        String locService = Objects.toString(locEndpoint.configuration().getOrDefault("Service", locEndpoint.configuration().getOrDefault("service", "s3")));
        String locHost = locTarget.getHost() + (locTarget.getPort() < 0 ? "" : ":" + locTarget.getPort());
        Instant locNow = Instant.now();
        String locDate = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC).format(locNow);
        String locAmzDate = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(locNow);
        String locHash = AIcS3ObjectStoragePublicationAdapter.AIcSha256(aBytes == null ? new byte[0] : aBytes);
        String locHeaders = "host:" + locHost + "\nx-amz-content-sha256:" + locHash + "\nx-amz-date:" + locAmzDate + "\n";
        String locSigned = "host;x-amz-content-sha256;x-amz-date";
        String locScope = locDate + "/" + locRegion + "/" + locService + "/aws4_request";
        String locCanonical = aMethod + "\n" + locTarget.getRawPath() + "\n\n" + locHeaders + "\n" + locSigned + "\n" + locHash;
        String locToSign = "AWS4-HMAC-SHA256\n" + locAmzDate + "\n" + locScope + "\n" + AIcS3ObjectStoragePublicationAdapter.AIcSha256(locCanonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String locSignature = HexFormat.of().formatHex(AIcS3ObjectStoragePublicationAdapter.AIcHmac(
                AIcS3ObjectStoragePublicationAdapter.AIcSigningKey(locSecret, locDate, locRegion, locService), locToSign));
        HttpURLConnection locConnection = (HttpURLConnection) locTarget.toURL().openConnection();
        try {
            locConnection.setInstanceFollowRedirects(false);
            locConnection.setRequestMethod(aMethod);
            locConnection.setUseCaches(false);
            AIcS3ObjectStoragePublicationAdapter.AIcApplyTimeouts(locConnection, aContext.deadline());
            locConnection.setRequestProperty("Host", locHost);
            locConnection.setRequestProperty("x-amz-content-sha256", locHash);
            locConnection.setRequestProperty("x-amz-date", locAmzDate);
            locConnection.setRequestProperty("Authorization", "AWS4-HMAC-SHA256 Credential=" + locKey + "/" + locScope + ", SignedHeaders=" + locSigned + ", Signature=" + locSignature);
            if ("PUT".equals(aMethod)) {
                if (aPrevious == null) locConnection.setRequestProperty("If-None-Match", "*");
                else {
                    if (aPrevious.etag() == null || aPrevious.etag().isBlank()) throw new IllegalStateException("Missing ETag; refusing unguarded schema overwrite.");
                    locConnection.setRequestProperty("If-Match", aPrevious.etag());
                }
                locConnection.setRequestProperty("Content-Type", aName.endsWith(".meta.yml") ? "application/yaml" : aName.endsWith(".json") ? "application/json" : "application/xml");
                locConnection.setDoOutput(true);
                locConnection.setFixedLengthStreamingMode(aBytes.length);
                try (var locOut = locConnection.getOutputStream()) { locOut.write(aBytes); }
            }
            int locStatus = locConnection.getResponseCode();
            if ("GET".equals(aMethod) && locStatus == 404) return null;
            if (locStatus < 200 || locStatus >= 300) {
                throw new IllegalStateException("S3-compatible " + aMethod + " failed for '" + locTarget + "' with HTTP " + locStatus + "." +
                        AIcS3ObjectStoragePublicationAdapter.AIcErrorDetails(locConnection, locKey, locSecret) +
                        " UsernamePrefix=" + AIcS3ObjectStoragePublicationAdapter.AIcUsernamePrefix(locKey, locSecret) + "; Region=" + locRegion + ";");
            }
            if ("PUT".equals(aMethod)) return new AIcRemote(aBytes, locConnection.getHeaderField("ETag"));
            int locLimit = aName.endsWith(".meta.yml") ? 65536 : 16 * 1024 * 1024;
            try (var locIn = locConnection.getInputStream()) {
                byte[] locBytes = locIn.readNBytes(locLimit + 1);
                if (locBytes.length > locLimit) throw new IllegalStateException("Schema object exceeds supported read limit: " + aName);
                return new AIcRemote(locBytes, locConnection.getHeaderField("ETag"));
            }
        } finally { locConnection.disconnect(); }
    }
}
