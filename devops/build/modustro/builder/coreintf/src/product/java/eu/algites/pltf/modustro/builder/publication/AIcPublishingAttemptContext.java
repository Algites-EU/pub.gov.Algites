package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublishingEndpoint;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublishingPayload;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Context provided to one publishing adapter attempt. */
public record AIcPublishingAttemptContext(
        AIcPublishingEndpoint endpoint,
        AIcPublishingPayload payload,
        int attemptNumber,
        int maximumAttemptCount,
        Long publishingAttemptTimeoutMillis,
        Instant deadline,
        Map<String, String> credentials,
        AIiCancellationToken cancellationToken,
        AIiPublishingProgressReporter progressReporter) {

    /** Validates attempt numbering and mandatory collaborators. */
    public AIcPublishingAttemptContext {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(payload, "payload");
        credentials = Map.copyOf(credentials == null ? Map.of() : credentials);
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Objects.requireNonNull(progressReporter, "progressReporter");
        if (attemptNumber < 1 || maximumAttemptCount < attemptNumber) {
            throw new IllegalArgumentException("Invalid publishing attempt numbering.");
        }
    }
}
