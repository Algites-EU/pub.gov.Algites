package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.*;
import java.time.Instant;
import java.util.Objects;

/** Context supplied to exactly one output-publication finalization attempt. */
public record AIcOutputPublicationFinalizationActionAttemptContext(
        AIcOutputPublicationFinalizationAction action,
        AIcOutputPublicationExecutionResult outputExecution,
        int attemptNumber,
        int maximumAttemptCount,
        Long attemptTimeoutMillis,
        Instant deadline,
        AIiCancellationToken cancellationToken,
        AIiPublicationProgressReporter progressReporter) {
    public AIcOutputPublicationFinalizationActionAttemptContext {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(outputExecution, "outputExecution");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Objects.requireNonNull(progressReporter, "progressReporter");
    }
}
