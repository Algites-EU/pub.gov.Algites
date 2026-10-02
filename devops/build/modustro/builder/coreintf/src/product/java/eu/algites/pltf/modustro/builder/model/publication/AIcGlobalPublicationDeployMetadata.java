package eu.algites.pltf.modustro.builder.model.publication;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable effective deployment metadata for one globally published canonical definition.
 */
public final class AIcGlobalPublicationDeployMetadata {

    private final String globalPublicationPathId;
    private final AInGlobalPublicationState publicationState;
    private final long publicationRevision;
    private final Instant firstPublishedAt;
    private final Instant publishedAt;
    private final Instant releasedAt;
    private final String publishedBy;

    /**
     * Creates effective deployment metadata.
     *
     * @param aGlobalPublicationPathId canonical global publication path id
     * @param aPublicationState publication lifecycle state
     * @param aPublicationRevision positive publisher-controlled revision
     * @param aFirstPublishedAt first successful publication timestamp
     * @param aPublishedAt most recent successful deployment timestamp
     * @param aReleasedAt release timestamp, required only for released content
     * @param aPublishedBy optional trusted publisher identity
     */
    public AIcGlobalPublicationDeployMetadata(
        String aGlobalPublicationPathId,
        AInGlobalPublicationState aPublicationState,
        long aPublicationRevision,
        Instant aFirstPublishedAt,
        Instant aPublishedAt,
        Instant aReleasedAt,
        String aPublishedBy
    ) {
        globalPublicationPathId = AIcRequireText(aGlobalPublicationPathId, "globalPublicationPathId");
        publicationState = Objects.requireNonNull(aPublicationState, "publicationState");
        if (aPublicationRevision < 1L) {
            throw new IllegalArgumentException("publicationRevision must be positive.");
        }
        publicationRevision = aPublicationRevision;
        firstPublishedAt = Objects.requireNonNull(aFirstPublishedAt, "firstPublishedAt");
        publishedAt = Objects.requireNonNull(aPublishedAt, "publishedAt");
        if (publishedAt.isBefore(firstPublishedAt)) {
            throw new IllegalArgumentException("publishedAt must not precede firstPublishedAt.");
        }
        if (publicationState == AInGlobalPublicationState.RELEASE && aReleasedAt == null) {
            throw new IllegalArgumentException("releasedAt is required for released global publication metadata.");
        }
        if (publicationState == AInGlobalPublicationState.DRAFT && aReleasedAt != null) {
            throw new IllegalArgumentException("releasedAt must be absent for draft global publication metadata.");
        }
        if (aReleasedAt != null && aReleasedAt.isBefore(firstPublishedAt)) {
            throw new IllegalArgumentException("releasedAt must not precede firstPublishedAt.");
        }
        releasedAt = aReleasedAt;
        publishedBy = AIcNormalize(aPublishedBy);
    }

    private static String AIcRequireText(String aValue, String aName) {
        String locValue = AIcNormalize(aValue);
        if (locValue == null) {
            throw new IllegalArgumentException(aName + " must not be blank.");
        }
        return locValue;
    }

    private static String AIcNormalize(String aValue) {
        if (aValue == null) {
            return null;
        }
        String locValue = aValue.trim();
        return locValue.isEmpty() ? null : locValue;
    }

    /** @return canonical global publication path id */
    public String globalPublicationPathId() {
        return globalPublicationPathId;
    }

    /** @return deployment publication state */
    public AInGlobalPublicationState publicationState() {
        return publicationState;
    }

    /** @return publisher-controlled publication revision */
    public long publicationRevision() {
        return publicationRevision;
    }

    /** @return first successful deployment timestamp */
    public Instant firstPublishedAt() {
        return firstPublishedAt;
    }

    /** @return latest successful deployment timestamp */
    public Instant publishedAt() {
        return publishedAt;
    }

    /** @return release timestamp, or {@code null} for a draft */
    public Instant releasedAt() {
        return releasedAt;
    }

    /** @return optional trusted publisher identity */
    public String publishedBy() {
        return publishedBy;
    }
}
