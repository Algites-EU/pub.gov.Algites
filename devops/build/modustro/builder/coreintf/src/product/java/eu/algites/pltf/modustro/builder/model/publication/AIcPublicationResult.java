package eu.algites.pltf.modustro.builder.model.publication;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/** Final observable result of one root publication schedule. */
public record AIcPublicationResult(
        String publicationId,
        boolean started,
        boolean success,
        boolean ignoredFailure,
        int attempts,
        Duration duration,
        URI outputUri,
        Map<String, Object> metadata,
        Throwable failure) {
    public AIcPublicationResult {
        metadata = AIcPublicationValues.freeze(metadata);
    }

    /** Compatibility constructor for callers that do not yet provide URI/result metadata. */
    public AIcPublicationResult(String aPublicationId, boolean aStarted, boolean aSuccess, boolean aIgnoredFailure,
            int aAttempts, Duration aDuration, Throwable aFailure) {
        this(aPublicationId, aStarted, aSuccess, aIgnoredFailure, aAttempts, aDuration, null, Map.of(), aFailure);
    }
}
