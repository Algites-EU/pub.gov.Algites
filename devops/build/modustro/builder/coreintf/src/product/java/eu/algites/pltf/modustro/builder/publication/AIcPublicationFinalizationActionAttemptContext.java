package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.*;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Context supplied to exactly one publication-finalization-action attempt. */
public record AIcPublicationFinalizationActionAttemptContext(
        AIcPublicationFinalizationAction action,
        AIcPublicationExecutionLineage lineage,
        AIcPublicationPayload rootPublicationPayload,
        URI inputUri,
        AIcPublicationEndpoint targetPublicationEndpoint,
        int attemptNumber,
        int maximumAttemptCount,
        Long attemptTimeoutMillis,
        Instant deadline,
        Map<String, String> credentials,
        AIiCancellationToken cancellationToken,
        AIiPublicationProgressReporter progressReporter,
        AIiPublicationDelegate publicationDelegate) {
    public AIcPublicationFinalizationActionAttemptContext {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(lineage, "lineage");
        Objects.requireNonNull(rootPublicationPayload, "rootPublicationPayload");
        Objects.requireNonNull(inputUri, "inputUri");
        credentials = Map.copyOf(credentials == null ? Map.of() : credentials);
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Objects.requireNonNull(progressReporter, "progressReporter");
        Objects.requireNonNull(publicationDelegate, "publicationDelegate");
        if (attemptNumber < 1 || maximumAttemptCount < attemptNumber) throw new IllegalArgumentException("Invalid action attempt numbering.");
    }
}
