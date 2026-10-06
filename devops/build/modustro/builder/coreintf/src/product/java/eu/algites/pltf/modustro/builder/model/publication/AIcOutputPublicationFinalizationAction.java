package eu.algites.pltf.modustro.builder.model.publication;

import java.util.Map;
import java.util.Objects;

/** Flat finalization action executed after one output publication graph is complete. */
public record AIcOutputPublicationFinalizationAction(
        String id, boolean executionEnabled, String outputPublicationFinalizationActionAdapter, int executionOrder,
        AInFinalizationActionFailurePolicy failurePolicy, int retryCount, long waitForNextAttemptMillis,
        Long attemptTimeoutMillis, boolean showProgressIfPossible, Map<String, Object> configuration) {
    public AIcOutputPublicationFinalizationAction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(failurePolicy, "failurePolicy");
        if (id.isBlank()) throw new IllegalArgumentException("Output publication finalization action id must not be blank.");
        if (executionEnabled && (outputPublicationFinalizationActionAdapter == null || outputPublicationFinalizationActionAdapter.isBlank()))
            throw new IllegalArgumentException("ExecutionEnabled output publication finalization action requires OutputPublicationFinalizationActionAdapter.");
        if (retryCount < 0 || waitForNextAttemptMillis < 0L || attemptTimeoutMillis != null && attemptTimeoutMillis <= 0L)
            throw new IllegalArgumentException("Invalid output publication finalization retry/timeout policy.");
        configuration = AIcPublicationValues.freeze(configuration);
    }
}
