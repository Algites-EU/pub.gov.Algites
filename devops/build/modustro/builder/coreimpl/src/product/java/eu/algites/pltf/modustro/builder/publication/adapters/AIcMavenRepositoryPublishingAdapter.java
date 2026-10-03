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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/** Publishes Maven-layout payload files to an HTTP(S) Maven repository using idempotent PUT requests. */
public final class AIcMavenRepositoryPublishingAdapter implements AIiPublishingAdapter {
    /** Canonical adapter id. */
    public static final String ADAPTER_ID = "maven-repository";

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
        Map<String, String> locCoordinates = aContext.payload().coordinates();
        String locGroupId = AIcRequiredCoordinate(locCoordinates, "groupId");
        String locArtifactId = AIcRequiredCoordinate(locCoordinates, "artifactId");
        String locVersion = AIcRequiredCoordinate(locCoordinates, "version");
        URI locRoot = AIcRepositoryRoot(aContext.endpoint().publishingUrl());
        String locBasePath = locGroupId.replace('.', '/') + "/" + locArtifactId + "/" + locVersion + "/";

        long locTotal = 0L;
        for (AIcPublishingPayloadFile locFile : aContext.payload().files()) {
            locTotal = Math.addExact(locTotal, Files.size(locFile.path()));
        }
        long locCompleted = 0L;
        aContext.progressReporter().started(
                "Publishing " + locGroupId + ":" + locArtifactId + ":" + locVersion + " to " + locRoot);

        for (AIcPublishingPayloadFile locPayloadFile : aContext.payload().files()) {
            AIcCheckCancellation(aContext);
            String locFileName = Path.of(locPayloadFile.logicalName()).getFileName().toString();
            URI locTarget = locRoot.resolve(locBasePath + locFileName);
            HttpURLConnection locConnection = (HttpURLConnection) new URL(locTarget.toString()).openConnection();
            locConnection.setRequestMethod("PUT");
            locConnection.setDoOutput(true);
            locConnection.setUseCaches(false);
            AIcApplyTimeouts(locConnection, aContext.deadline());
            AIcApplyCredentials(locConnection, aContext.credentials());
            long locSize = Files.size(locPayloadFile.path());
            if (locSize <= Integer.MAX_VALUE) {
                locConnection.setFixedLengthStreamingMode((int) locSize);
            } else {
                locConnection.setFixedLengthStreamingMode(locSize);
            }
            try (OutputStream locOutput = locConnection.getOutputStream(); var locInput = Files.newInputStream(locPayloadFile.path())) {
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
                        aContext.progressReporter().progress(
                                locCompleted,
                                locTotal,
                                "bytes",
                                "Publishing " + locFileName);
                    }
                }
            }
            int locStatus = locConnection.getResponseCode();
            if (locStatus < 200 || locStatus >= 300) {
                String locMessage = locConnection.getResponseMessage();
                locConnection.disconnect();
                throw new IllegalStateException(
                        "Maven repository PUT failed for '" + locTarget + "' with HTTP " + locStatus + " " + locMessage + ".");
            }
            locConnection.disconnect();
        }
        aContext.progressReporter().completed("Maven repository publication completed.");
    }

    private static URI AIcRepositoryRoot(URI aUri) {
        if (aUri == null || aUri.getScheme() == null) {
            throw new IllegalArgumentException("maven-repository publishing requires an absolute HTTP(S) PublishingUrl.");
        }
        String locScheme = aUri.getScheme().toLowerCase();
        if (!"http".equals(locScheme) && !"https".equals(locScheme)) {
            throw new IllegalArgumentException("maven-repository publishing supports only http: and https: PublishingUrl values.");
        }
        String locText = aUri.toString();
        return URI.create(locText.endsWith("/") ? locText : locText + "/");
    }

    private static void AIcApplyCredentials(HttpURLConnection aConnection, Map<String, String> aCredentials) {
        String locUsername = aCredentials.get("username");
        String locPassword = aCredentials.get("password");
        if (locUsername != null && locPassword != null) {
            String locValue = Base64.getEncoder().encodeToString((locUsername + ":" + locPassword).getBytes(java.nio.charset.StandardCharsets.UTF_8));
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

    private static String AIcRequiredCoordinate(Map<String, String> aCoordinates, String aKey) {
        String locValue = aCoordinates.get(aKey);
        if (locValue == null || locValue.isBlank()) {
            throw new IllegalArgumentException("maven-repository publishing requires payload coordinate '" + aKey + "'.");
        }
        return locValue;
    }

    private static void AIcCheckCancellation(AIcPublishingAttemptContext aContext) throws InterruptedException {
        if (aContext.cancellationToken().isCancellationRequested()) {
            throw new InterruptedException("Publishing attempt cancelled.");
        }
    }
}
