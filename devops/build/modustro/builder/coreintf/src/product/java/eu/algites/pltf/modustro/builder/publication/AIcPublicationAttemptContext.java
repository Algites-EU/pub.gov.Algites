package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationEndpoint;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayload;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Context provided to one publication adapter attempt. */
public record AIcPublicationAttemptContext(
        AIcPublicationEndpoint endpoint,
        AIcPublicationPayload payload,
        int attemptNumber,
        int maximumAttemptCount,
        Long publicationAttemptTimeoutMillis,
        Instant deadline,
        Map<String, String> credentials,
        AIiCancellationToken cancellationToken,
        AIiPublicationProgressReporter progressReporter) {

    /** Validates attempt numbering and mandatory collaborators. */
    public AIcPublicationAttemptContext {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(payload, "payload");
        credentials = Map.copyOf(credentials == null ? Map.of() : credentials);
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Objects.requireNonNull(progressReporter, "progressReporter");
        if (attemptNumber < 1 || maximumAttemptCount < attemptNumber) {
            throw new IllegalArgumentException("Invalid publication attempt numbering.");
        }
    }
}
