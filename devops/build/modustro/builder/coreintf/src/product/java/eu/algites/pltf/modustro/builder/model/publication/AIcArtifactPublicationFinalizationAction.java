package eu.algites.pltf.modustro.builder.model.publication;

import eu.algites.pltf.modustro.builder.model.execution.AIngBuildExecutionFailurePolicy_1;

import java.util.Map;
import java.util.Objects;

/** Flat finalization action executed after every output of one artifact is complete. */
public record AIcArtifactPublicationFinalizationAction(
        String id, boolean executionEnabled, String artifactPublicationFinalizationActionAdapter, int executionOrder,
        AIngBuildExecutionFailurePolicy_1 executionFailurePolicy, int retryCount, long waitForNextAttemptMillis,
        Long attemptTimeoutMillis, boolean showProgressIfPossible, Map<String, Object> configuration) {
    public AIcArtifactPublicationFinalizationAction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(executionFailurePolicy, "executionFailurePolicy");
        if (id.isBlank()) throw new IllegalArgumentException("Artifact publication finalization action id must not be blank.");
        if (executionEnabled && (artifactPublicationFinalizationActionAdapter == null || artifactPublicationFinalizationActionAdapter.isBlank()))
            throw new IllegalArgumentException("ExecutionEnabled artifact publication finalization action requires ArtifactPublicationFinalizationActionAdapter.");
        if (retryCount < 0 || waitForNextAttemptMillis < 0L || attemptTimeoutMillis != null && attemptTimeoutMillis <= 0L)
            throw new IllegalArgumentException("Invalid artifact publication finalization retry/timeout policy.");
        configuration = AIcPublicationValues.freeze(configuration);
    }
}
