package eu.algites.pltf.modustro.builder.publication.adapters;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationEndpoint;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayload;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayloadFile;
import eu.algites.pltf.modustro.builder.publication.AIcPublicationAttemptContext;
import eu.algites.pltf.modustro.builder.publication.AIiPublicationAdapter;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Publishes a logical file tree to an S3-compatible object-storage HTTP endpoint using AWS Signature V4. */
public final class AIcS3ObjectStoragePublicationAdapter implements AIiPublicationAdapter {
    public static final String ADAPTER_ID = "s3-object-storage";
    private static final DateTimeFormatter AIcAmzDate = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter AIcDate = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    @Override
    public String adapterId() { return ADAPTER_ID; }

    @Override
    public boolean isRetrySafe(AIcPublicationPayload aPayload, AIcPublicationEndpoint aEndpoint) { return true; }

    @Override
    public void publish(AIcPublicationAttemptContext aContext) throws Exception {
        AIcPublicationEndpoint locEndpoint = aContext.endpoint();
        URI locRoot = AIcRoot(locEndpoint.publicationUri());
        String locRegion = Objects.toString(
                locEndpoint.configuration().getOrDefault("Region", locEndpoint.configuration().getOrDefault("region", "us-east-1")),
                "us-east-1");
        String locService = Objects.toString(
                locEndpoint.configuration().getOrDefault("Service", locEndpoint.configuration().getOrDefault("service", "s3")),
                "s3");
        String locAccessKey = aContext.credentials().get("username");
        String locSecretKey = aContext.credentials().get("password");
        if (locAccessKey == null || locSecretKey == null) {
            throw new IllegalArgumentException("s3-object-storage requires a basic credential profile whose Username is the access key and Password is the secret key.");
        }
        String locIdentityDetails = " UsernamePrefix=" + AIcUsernamePrefix(locAccessKey, locSecretKey) + "; Region=" + locRegion + ";";
        long locTotal = 0L;
        for (AIcPublicationPayloadFile locFile : aContext.payload().files()) locTotal = Math.addExact(locTotal, Files.size(locFile.path()));
        long locCompleted = 0L;
        aContext.progressReporter().started("Publishing object-storage payload to " + locRoot + "." + locIdentityDetails);
        for (AIcPublicationPayloadFile locFile : aContext.payload().files()) {
            AIcCheckCancellation(aContext);
            URI locTarget = AIcTarget(locRoot, locFile.logicalName());
            String locPayloadHash = AIcSha256(locFile.path());
            Instant locNow = Instant.now();
            String locAmzDate = AIcAmzDate.format(locNow);
            String locDate = AIcDate.format(locNow);
            String locHost = locTarget.getPort() < 0 ? locTarget.getHost() : locTarget.getHost() + ":" + locTarget.getPort();
            String locCanonicalUri = locTarget.getRawPath();
            String locCanonicalHeaders = "host:" + locHost + "\n" +
                    "x-amz-content-sha256:" + locPayloadHash + "\n" +
                    "x-amz-date:" + locAmzDate + "\n";
            String locSignedHeaders = "host;x-amz-content-sha256;x-amz-date";
            String locCanonicalRequest = "PUT\n" + locCanonicalUri + "\n\n" + locCanonicalHeaders + "\n" + locSignedHeaders + "\n" + locPayloadHash;
            String locScope = locDate + "/" + locRegion + "/" + locService + "/aws4_request";
            String locStringToSign = "AWS4-HMAC-SHA256\n" + locAmzDate + "\n" + locScope + "\n" + AIcSha256(locCanonicalRequest.getBytes(StandardCharsets.UTF_8));
            byte[] locSigningKey = AIcSigningKey(locSecretKey, locDate, locRegion, locService);
            String locSignature = HexFormat.of().formatHex(AIcHmac(locSigningKey, locStringToSign));
            String locAuthorization = "AWS4-HMAC-SHA256 Credential=" + locAccessKey + "/" + locScope +
                    ", SignedHeaders=" + locSignedHeaders + ", Signature=" + locSignature;

            HttpURLConnection locConnection = (HttpURLConnection) new URL(locTarget.toString()).openConnection();
            locConnection.setRequestMethod("PUT");
            locConnection.setDoOutput(true);
            locConnection.setUseCaches(false);
            AIcApplyTimeouts(locConnection, aContext.deadline());
            locConnection.setRequestProperty("Host", locHost);
            locConnection.setRequestProperty("x-amz-content-sha256", locPayloadHash);
            locConnection.setRequestProperty("x-amz-date", locAmzDate);
            locConnection.setRequestProperty("Authorization", locAuthorization);
            long locSize = Files.size(locFile.path());
            locConnection.setFixedLengthStreamingMode(locSize);
            try (OutputStream locOutput = locConnection.getOutputStream(); var locInput = Files.newInputStream(locFile.path())) {
                byte[] locBuffer = new byte[1024 * 1024];
                int locRead;
                while ((locRead = locInput.read(locBuffer)) >= 0) {
                    AIcCheckCancellation(aContext);
                    locOutput.write(locBuffer, 0, locRead);
                    locCompleted += locRead;
                    if (locTotal > 0L) aContext.progressReporter().progress(locCompleted, locTotal, "bytes", "Publishing " + locFile.logicalName());
                }
            }
            int locStatus = locConnection.getResponseCode();
            if (locStatus < 200 || locStatus >= 300) {
                String locMessage = locConnection.getResponseMessage();
                String locDetails = AIcErrorDetails(locConnection, locAccessKey, locSecretKey);
                locConnection.disconnect();
                throw new IllegalStateException("S3-compatible PUT failed for '" + locTarget + "' with HTTP " + locStatus + " " + locMessage + "." + locDetails + locIdentityDetails);
            }
            locConnection.disconnect();
        }
        aContext.progressReporter().completed("Object-storage publication completed.");
    }

    /** Identifies the configured username with at most its first half, without exposing the secret. */
    private static String AIcUsernamePrefix(String aUsername, String aSecret) {
        String locPrefix = aUsername.substring(0, aUsername.length() / 2);
        if (!aSecret.isEmpty()) locPrefix = locPrefix.replace(aSecret, "[redacted]");
        locPrefix = locPrefix.replaceAll("[\\p{Cntrl}\\s]+", " ");
        return locPrefix + "...";
    }

    /** Reports only bounded S3 error fields, never the signed request or credential fields. */
    private static String AIcErrorDetails(HttpURLConnection aConnection, String aAccessKey, String aSecretKey) {
        try (var locInput = aConnection.getErrorStream()) {
            if (locInput == null) return "";
            byte[] locBody = locInput.readNBytes(16384);
            var locFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            locFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            locFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            locFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            locFactory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
            locFactory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var locBuilder = locFactory.newDocumentBuilder();
            locBuilder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            var locDocument = locBuilder.parse(new java.io.ByteArrayInputStream(locBody));
            StringBuilder locDetails = new StringBuilder();
            for (String locField : new String[]{"Code", "Message", "RequestId", "Region"}) {
                var locNodes = locDocument.getElementsByTagName(locField);
                if (locNodes.getLength() == 0) continue;
                String locValue = locNodes.item(0).getTextContent().replace(aAccessKey, "[redacted]").replace(aSecretKey, "[redacted]")
                        .replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
                if (locValue.length() > 512) locValue = locValue.substring(0, 512);
                if (!locValue.isBlank()) locDetails.append(" ").append(locField).append("=").append(locValue).append(";");
            }
            return locDetails.toString();
        } catch (Exception locIgnored) {
            /* A missing or non-XML response body must not hide the original HTTP failure. */
            return "";
        }
    }

    private static URI AIcRoot(URI aUri) {
        if (aUri == null || aUri.getScheme() == null || !("https".equalsIgnoreCase(aUri.getScheme()) || "http".equalsIgnoreCase(aUri.getScheme()))) {
            throw new IllegalArgumentException("s3-object-storage requires an absolute HTTP(S) PublicationUri.");
        }
        String locText = aUri.toString();
        return URI.create(locText.endsWith("/") ? locText : locText + "/");
    }

    private static URI AIcTarget(URI aRoot, String aLogicalName) {
        String locValue = aLogicalName == null ? "" : aLogicalName.replace('\\', '/');
        while (locValue.startsWith("/")) locValue = locValue.substring(1);
        if (locValue.isBlank() || locValue.equals("..") || locValue.contains("../")) throw new IllegalArgumentException("Invalid logical publication path '" + aLogicalName + "'.");
        StringBuilder locEncoded = new StringBuilder();
        for (String locSegment : locValue.split("/")) {
            if (locEncoded.length() > 0) locEncoded.append('/');
            locEncoded.append(java.net.URLEncoder.encode(locSegment, StandardCharsets.UTF_8).replace("+", "%20").replace("%7E", "~"));
        }
        return aRoot.resolve(locEncoded.toString());
    }

    private static String AIcSha256(java.nio.file.Path aPath) throws Exception {
        MessageDigest locDigest = MessageDigest.getInstance("SHA-256");
        try (var locInput = Files.newInputStream(aPath)) {
            byte[] locBuffer = new byte[65536];
            int locRead;
            while ((locRead = locInput.read(locBuffer)) >= 0) locDigest.update(locBuffer, 0, locRead);
        }
        return HexFormat.of().formatHex(locDigest.digest());
    }

    private static String AIcSha256(byte[] aValue) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(aValue));
    }

    private static byte[] AIcHmac(byte[] aKey, String aValue) throws Exception {
        Mac locMac = Mac.getInstance("HmacSHA256");
        locMac.init(new SecretKeySpec(aKey, "HmacSHA256"));
        return locMac.doFinal(aValue.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] AIcSigningKey(String aSecret, String aDate, String aRegion, String aService) throws Exception {
        byte[] locDate = AIcHmac(("AWS4" + aSecret).getBytes(StandardCharsets.UTF_8), aDate);
        byte[] locRegion = AIcHmac(locDate, aRegion);
        byte[] locService = AIcHmac(locRegion, aService);
        return AIcHmac(locService, "aws4_request");
    }

    private static void AIcApplyTimeouts(HttpURLConnection aConnection, Instant aDeadline) {
        if (aDeadline == null) return;
        long locMillis = Math.max(1L, Duration.between(Instant.now(), aDeadline).toMillis());
        int locTimeout = (int) Math.min(Integer.MAX_VALUE, locMillis);
        aConnection.setConnectTimeout(locTimeout);
        aConnection.setReadTimeout(locTimeout);
    }

    private static void AIcCheckCancellation(AIcPublicationAttemptContext aContext) throws InterruptedException {
        if (aContext.cancellationToken().isCancellationRequested()) throw new InterruptedException("Publication attempt cancelled.");
    }
}
