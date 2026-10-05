package eu.algites.pltf.modustro.builder.model.publication;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

/** Immutable effective configuration of one publication destination. */
public record AIcPublicationEndpoint(
        String id,
        boolean enabled,
        URI publicationUri,
        String publicationAdapter,
        String publicationCredentialProfile,
        int publicationOrder,
        AInPublicationFailurePolicy publicationFailurePolicy,
        int publicationRetryCount,
        long publicationWaitForNextAttemptMillis,
        Long publicationAttemptTimeoutMillis,
        boolean showPublicationProgressIfPossible,
        Map<String, Object> configuration) {

    /** Validates endpoint invariants. */
    public AIcPublicationEndpoint {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(publicationFailurePolicy, "publicationFailurePolicy");
        configuration = Map.copyOf(configuration == null ? Map.of() : configuration);
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
        if (enabled && publicationUri == null) {
            throw new IllegalArgumentException("Enabled publication endpoint requires PublicationUri.");
        }
        if (enabled && (publicationAdapter == null || publicationAdapter.isBlank())) {
            throw new IllegalArgumentException("Enabled publication endpoint requires PublicationAdapter.");
        }
    }

    /** Compatibility constructor for endpoint declarations without provider-specific configuration. */
    public AIcPublicationEndpoint(
            String aId, boolean aEnabled, URI aPublicationUri, String aPublicationAdapter,
            String aPublicationCredentialProfile, int aPublicationOrder, AInPublicationFailurePolicy aPublicationFailurePolicy,
            int aPublicationRetryCount, long aPublicationWaitForNextAttemptMillis, Long aPublicationAttemptTimeoutMillis,
            boolean aShowPublicationProgressIfPossible) {
        this(aId, aEnabled, aPublicationUri, aPublicationAdapter, aPublicationCredentialProfile, aPublicationOrder,
                aPublicationFailurePolicy, aPublicationRetryCount, aPublicationWaitForNextAttemptMillis,
                aPublicationAttemptTimeoutMillis, aShowPublicationProgressIfPossible, Map.of());
    }
}
