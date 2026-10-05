package eu.algites.pltf.modustro.builder.model.publication;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Effective recursive post-publication action configuration. */
public record AIcPostPublicationAction(
        String id,
        boolean enabled,
        String postPublicationActionAdapter,
        String targetPublicationEndpointId,
        int order,
        AInPostPublicationActionFailurePolicy failurePolicy,
        int retryCount,
        long waitForNextAttemptMillis,
        Long attemptTimeoutMillis,
        boolean showProgressIfPossible,
        Map<String, Object> configuration,
        List<AIcPostPublicationAction> postPublicationActions) {
    public AIcPostPublicationAction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(failurePolicy, "failurePolicy");
        if (id.isBlank()) throw new IllegalArgumentException("Post-publication action id must not be blank.");
        if (enabled && (postPublicationActionAdapter == null || postPublicationActionAdapter.isBlank())) {
            throw new IllegalArgumentException("Enabled post-publication action requires PostPublicationActionAdapter.");
        }
        if (retryCount < 0 || waitForNextAttemptMillis < 0L || attemptTimeoutMillis != null && attemptTimeoutMillis <= 0L) {
            throw new IllegalArgumentException("Invalid post-publication action retry/timeout policy.");
        }
        configuration = Map.copyOf(configuration == null ? Map.of() : configuration);
        postPublicationActions = List.copyOf(postPublicationActions == null ? List.of() : postPublicationActions);
    }
}
