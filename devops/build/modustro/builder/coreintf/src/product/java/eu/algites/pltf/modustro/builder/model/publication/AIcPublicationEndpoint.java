package eu.algites.pltf.modustro.builder.model.publication;

import eu.algites.pltf.modustro.builder.model.execution.AIngBuildExecutionFailurePolicy_1;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

/** Immutable effective configuration of one publication destination. */
public record AIcPublicationEndpoint(
        String id,
        boolean executionEnabled,
        URI publicationUri,
        String publicationAdapter,
        String publicationCredentialProfile,
        int executionOrder,
        AIngBuildExecutionFailurePolicy_1 executionFailurePolicy,
        int publicationRetryCount,
        long publicationWaitForNextAttemptMillis,
        Long publicationAttemptTimeoutMillis,
        boolean showPublicationProgressIfPossible,
        Map<String, Object> configuration) {

    /** Validates endpoint invariants. */
    public AIcPublicationEndpoint {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(executionFailurePolicy, "executionFailurePolicy");
        configuration = AIcPublicationValues.freeze(configuration);
        if (id.isBlank()) {
            throw new IllegalArgumentException("Publication endpoint id must not be blank.");
        }
        if (publicationRetryCount < 0) {
            throw new IllegalArgumentException("PublicationRetryCount must be non-negative.");
        }
        if (publicationWaitForNextAttemptMillis < 0L) {
            throw new IllegalArgumentException("PublicationWaitForNextAttemptMillis must be non-negative.");
        }
        if (publicationAttemptTimeoutMillis != null && publicationAttemptTimeoutMillis <= 0L) {
            throw new IllegalArgumentException("PublicationAttemptTimeoutMillis must be positive when specified.");
        }
        if (executionEnabled && publicationUri == null) {
            throw new IllegalArgumentException("ExecutionEnabled publication endpoint requires PublicationUri.");
        }
        if (executionEnabled && (publicationAdapter == null || publicationAdapter.isBlank())) {
            throw new IllegalArgumentException("ExecutionEnabled publication endpoint requires PublicationAdapter.");
        }
    }

    /** Compatibility constructor for endpoint declarations without provider-specific configuration. */
    public AIcPublicationEndpoint(
            String aId, boolean aExecutionEnabled, URI aPublicationUri, String aPublicationAdapter,
            String aPublicationCredentialProfile, int aExecutionOrder, AIngBuildExecutionFailurePolicy_1 aExecutionFailurePolicy,
            int aPublicationRetryCount, long aPublicationWaitForNextAttemptMillis, Long aPublicationAttemptTimeoutMillis,
            boolean aShowPublicationProgressIfPossible) {
        this(aId, aExecutionEnabled, aPublicationUri, aPublicationAdapter, aPublicationCredentialProfile, aExecutionOrder,
                aExecutionFailurePolicy, aPublicationRetryCount, aPublicationWaitForNextAttemptMillis,
                aPublicationAttemptTimeoutMillis, aShowPublicationProgressIfPossible, Map.of());
    }
}
