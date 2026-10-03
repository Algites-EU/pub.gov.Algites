package eu.algites.pltf.modustro.builder.model.publication;

import java.net.URI;
import java.util.Objects;

/** Immutable effective configuration of one publishing destination. */
public record AIcPublishingEndpoint(
        String id,
        boolean enabled,
        URI publishingUrl,
        String publishingAdapter,
        String publishingCredentialProfile,
        int publishingOrder,
        AInPublishingFailurePolicy publishingFailurePolicy,
        int publishingRetryCount,
        long publishingRetryDelayMillis,
        Long publishingAttemptTimeoutMillis,
        boolean showPublishingProgressIfPossible) {

    /** Validates endpoint invariants. */
    public AIcPublishingEndpoint {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(publishingFailurePolicy, "publishingFailurePolicy");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Publishing endpoint id must not be blank.");
        }
        if (publishingRetryCount < 0) {
            throw new IllegalArgumentException("PublishingRetryCount must be non-negative.");
        }
        if (publishingRetryDelayMillis < 0L) {
            throw new IllegalArgumentException("PublishingRetryDelayMillis must be non-negative.");
        }
        if (publishingAttemptTimeoutMillis != null && publishingAttemptTimeoutMillis <= 0L) {
            throw new IllegalArgumentException("PublishingAttemptTimeoutMillis must be positive when specified.");
        }
        if (enabled && publishingUrl == null) {
            throw new IllegalArgumentException("Enabled publishing endpoint requires PublishingUrl.");
        }
        if (enabled && (publishingAdapter == null || publishingAdapter.isBlank())) {
            throw new IllegalArgumentException("Enabled publishing endpoint requires PublishingAdapter.");
        }
    }
}
