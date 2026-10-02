package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.AIxModelValidationException;
import eu.algites.pltf.modustro.builder.model.publication.AIcGlobalPublicationDeployMetadata;
import eu.algites.pltf.modustro.builder.model.publication.AInGlobalPublicationState;

import java.time.Instant;
import java.util.Objects;

/**
 * Applies the server-controlled global-publication metadata lifecycle.
 */
public final class AIcGlobalPublicationDeployMetadataResolver {

    private final AIcGlobalPublicationPathValidator pathValidator;

    /** Creates the built-in deploy-metadata resolver. */
    public AIcGlobalPublicationDeployMetadataResolver() {
        pathValidator = new AIcGlobalPublicationPathValidator();
    }

    /**
     * Creates metadata for the first successful publication of one path.
     *
     * @param aGlobalPublicationPathId validated source publication path id
     * @param aRequestedState requested publication state
     * @param aNow trusted publisher timestamp
     * @param aPublishedBy optional trusted publisher identity
     * @return initial effective deploy metadata
     */
    public AIcGlobalPublicationDeployMetadata initial(
        String aGlobalPublicationPathId,
        AInGlobalPublicationState aRequestedState,
        Instant aNow,
        String aPublishedBy
    ) {
        String locPath = pathValidator.validate(aGlobalPublicationPathId);
        AInGlobalPublicationState locState = Objects.requireNonNull(aRequestedState, "requestedState");
        Instant locNow = Objects.requireNonNull(aNow, "now");
        return new AIcGlobalPublicationDeployMetadata(
            locPath,
            locState,
            1L,
            locNow,
            locNow,
            locState == AInGlobalPublicationState.RELEASE ? locNow : null,
            aPublishedBy
        );
    }

    /**
     * Resolves a subsequent successful deployment.
     *
     * @param aPrevious trusted previous deploy metadata
     * @param aRequestedState requested publication state
     * @param aContentChanged whether deployed canonical content differs from the previous content
     * @param aNow trusted publisher timestamp
     * @param aPublishedBy optional trusted publisher identity
     * @return next effective deploy metadata
     */
    public AIcGlobalPublicationDeployMetadata transition(
        AIcGlobalPublicationDeployMetadata aPrevious,
        AInGlobalPublicationState aRequestedState,
        boolean aContentChanged,
        Instant aNow,
        String aPublishedBy
    ) {
        AIcGlobalPublicationDeployMetadata locPrevious = Objects.requireNonNull(aPrevious, "previous");
        AInGlobalPublicationState locRequestedState = Objects.requireNonNull(aRequestedState, "requestedState");
        Instant locNow = Objects.requireNonNull(aNow, "now");

        if (locNow.isBefore(locPrevious.publishedAt())) {
            throw new AIxModelValidationException("Publication timestamp must not move backwards.");
        }

        if (locPrevious.publicationState() == AInGlobalPublicationState.RELEASE) {
            if (locRequestedState != AInGlobalPublicationState.RELEASE) {
                throw new AIxModelValidationException(
                    "Released GlobalPublicationPathId '" + locPrevious.globalPublicationPathId() + "' cannot transition back to draft."
                );
            }
            if (aContentChanged) {
                throw new AIxModelValidationException(
                    "Released GlobalPublicationPathId '" + locPrevious.globalPublicationPathId() + "' is immutable."
                );
            }
            return locPrevious;
        }

        long locRevision = aContentChanged
            ? Math.addExact(locPrevious.publicationRevision(), 1L)
            : locPrevious.publicationRevision();
        Instant locReleasedAt = locRequestedState == AInGlobalPublicationState.RELEASE ? locNow : null;
        return new AIcGlobalPublicationDeployMetadata(
            locPrevious.globalPublicationPathId(),
            locRequestedState,
            locRevision,
            locPrevious.firstPublishedAt(),
            locNow,
            locReleasedAt,
            aPublishedBy
        );
    }
}
