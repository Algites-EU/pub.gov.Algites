package eu.algites.pltf.modustro.builder.model.publication;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Effective recursive finalization action attached to one concrete publication. */
public record AIcPublicationFinalizationAction(
        String id,
        boolean executionEnabled,
        String finalizationActionAdapter,
        String targetPublicationEndpointId,
        int executionOrder,
        AInFinalizationActionFailurePolicy failurePolicy,
        int retryCount,
        long waitForNextAttemptMillis,
        Long attemptTimeoutMillis,
        boolean showProgressIfPossible,
        Map<String, Object> configuration,
        List<AIcPublicationFinalizationAction> finalizationActions) {
    public AIcPublicationFinalizationAction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(failurePolicy, "failurePolicy");
        if (id.isBlank()) throw new IllegalArgumentException("Publication finalization action id must not be blank.");
        if (executionEnabled && (finalizationActionAdapter == null || finalizationActionAdapter.isBlank())) {
            throw new IllegalArgumentException("ExecutionEnabled publication finalization action requires an adapter.");
        }
        if (retryCount < 0 || waitForNextAttemptMillis < 0L || attemptTimeoutMillis != null && attemptTimeoutMillis <= 0L) {
            throw new IllegalArgumentException("Invalid publication finalization action retry/timeout policy.");
        }
        configuration = AIcPublicationValues.freeze(configuration);
        finalizationActions = List.copyOf(finalizationActions == null ? List.of() : finalizationActions);
    }
}
