package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.*;
import java.time.Instant;
import java.util.Objects;

/** Context supplied to exactly one artifact-publication finalization attempt. */
public record AIcArtifactPublicationFinalizationActionAttemptContext(
        AIcArtifactPublicationFinalizationAction action,
        AIcArtifactPublicationExecutionResult artifactExecution,
        int attemptNumber,
        int maximumAttemptCount,
        Long attemptTimeoutMillis,
        Instant deadline,
        AIiCancellationToken cancellationToken,
        AIiPublicationProgressReporter progressReporter) {
    public AIcArtifactPublicationFinalizationActionAttemptContext {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(artifactExecution, "artifactExecution");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Objects.requireNonNull(progressReporter, "progressReporter");
    }
}
