package eu.algites.pltf.modustro.builder.publication.adapters;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublishingEndpoint;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublishingPayload;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublishingPayloadFile;
import eu.algites.pltf.modustro.builder.publication.AIcPublishingAttemptContext;
import eu.algites.pltf.modustro.builder.publication.AIiPublishingAdapter;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/** Publishes a logical file tree through idempotent HTTP(S) PUT operations. */
public final class AIcHttpDirectoryPublishingAdapter implements AIiPublishingAdapter {
    /** Canonical adapter id. */
    public static final String ADAPTER_ID = "http-directory";

    @Override
    public String adapterId() {
        return ADAPTER_ID;
    }

    @Override
    public boolean isRetrySafe(AIcPublishingPayload aPayload, AIcPublishingEndpoint aEndpoint) {
        return true;
    }

    @Override
    public void publish(AIcPublishingAttemptContext aContext) throws Exception {
        URI locRoot = AIcRoot(aContext.endpoint().publishingUrl());
        long locTotal = 0L;
        for (AIcPublishingPayloadFile locFile : aContext.payload().files()) {
            locTotal = Math.addExact(locTotal, Files.size(locFile.path()));
        }
        long locCompleted = 0L;
        aContext.progressReporter().started("Publishing directory payload to " + locRoot);
        for (AIcPublishingPayloadFile locFile : aContext.payload().files()) {
            AIcCheckCancellation(aContext);
            String locLogicalName = AIcLogicalName(locFile.logicalName());
            URI locTarget = locRoot.resolve(AIcEncodeLogicalPath(locLogicalName));
            HttpURLConnection locConnection = (HttpURLConnection) new URL(locTarget.toString()).openConnection();
            locConnection.setRequestMethod("PUT");
            locConnection.setDoOutput(true);
            locConnection.setUseCaches(false);
            AIcApplyTimeouts(locConnection, aContext.deadline());
            AIcApplyCredentials(locConnection, aContext.credentials());
            long locSize = Files.size(locFile.path());
            locConnection.setFixedLengthStreamingMode(locSize);
            try (OutputStream locOutput = locConnection.getOutputStream(); var locInput = Files.newInputStream(locFile.path())) {
                byte[] locBuffer = new byte[1024 * 1024];
                while (true) {
                    AIcCheckCancellation(aContext);
                    int locRead = locInput.read(locBuffer);
                    if (locRead < 0) {
                        break;
                    }
                    locOutput.write(locBuffer, 0, locRead);
                    locCompleted += locRead;
                    if (locTotal > 0L) {
                        aContext.progressReporter().progress(locCompleted, locTotal, "bytes", "Publishing " + locLogicalName);
                    }
                }
            }
            int locStatus = locConnection.getResponseCode();
            if (locStatus < 200 || locStatus >= 300) {
                String locMessage = locConnection.getResponseMessage();
                locConnection.disconnect();
                throw new IllegalStateException(
                        "HTTP directory PUT failed for '" + locTarget + "' with HTTP " + locStatus + " " + locMessage + ".");
            }
            locConnection.disconnect();
        }
        aContext.progressReporter().completed("HTTP directory publication completed.");
    }

    private static URI AIcRoot(URI aUri) {
        if (aUri == null || aUri.getScheme() == null) {
            throw new IllegalArgumentException("http-directory publishing requires an absolute HTTP(S) PublishingUrl.");
        }
        String locScheme = aUri.getScheme().toLowerCase();
        if (!"http".equals(locScheme) && !"https".equals(locScheme)) {
            throw new IllegalArgumentException("http-directory publishing supports only http: and https: PublishingUrl values.");
        }
        String locText = aUri.toString();
        return URI.create(locText.endsWith("/") ? locText : locText + "/");
    }

    private static String AIcLogicalName(String aLogicalName) {
        String locValue = aLogicalName == null ? "" : aLogicalName.replace('\\', '/');
        while (locValue.startsWith("/")) {
            locValue = locValue.substring(1);
        }
        if (locValue.isBlank() || locValue.contains("../") || locValue.equals("..")) {
            throw new IllegalArgumentException("Invalid logical publication path '" + aLogicalName + "'.");
        }
        return locValue;
    }

    private static String AIcEncodeLogicalPath(String aLogicalName) {
        StringBuilder locResult = new StringBuilder();
        for (String locSegment : aLogicalName.split("/")) {
            if (locResult.length() > 0) {
                locResult.append('/');
            }
            locResult.append(java.net.URLEncoder.encode(locSegment, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return locResult.toString();
    }

    private static void AIcApplyCredentials(HttpURLConnection aConnection, Map<String, String> aCredentials) {
        String locUsername = aCredentials.get("username");
        String locPassword = aCredentials.get("password");
        if (locUsername != null && locPassword != null) {
            String locValue = Base64.getEncoder().encodeToString((locUsername + ":" + locPassword).getBytes(StandardCharsets.UTF_8));
            aConnection.setRequestProperty("Authorization", "Basic " + locValue);
            return;
        }
        String locBearerToken = aCredentials.get("bearerToken");
        if (locBearerToken != null) {
            aConnection.setRequestProperty("Authorization", "Bearer " + locBearerToken);
            return;
        }
        String locApiKey = aCredentials.get("apiKey");
        String locApiKeyHeader = aCredentials.get("apiKeyHeader");
        if (locApiKey != null && locApiKeyHeader != null) {
            aConnection.setRequestProperty(locApiKeyHeader, locApiKey);
        }
    }

    private static void AIcApplyTimeouts(HttpURLConnection aConnection, Instant aDeadline) {
        if (aDeadline == null) {
            return;
        }
        long locMillis = Math.max(1L, Duration.between(Instant.now(), aDeadline).toMillis());
        int locTimeout = (int) Math.min(Integer.MAX_VALUE, locMillis);
        aConnection.setConnectTimeout(locTimeout);
        aConnection.setReadTimeout(locTimeout);
    }

    private static void AIcCheckCancellation(AIcPublishingAttemptContext aContext) throws InterruptedException {
        if (aContext.cancellationToken().isCancellationRequested()) {
            throw new InterruptedException("Publishing attempt cancelled.");
        }
    }
}
