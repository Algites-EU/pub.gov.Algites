package eu.algites.pltf.modustro.builder.model.publication;

import java.util.List;

/** Effective publication configuration for one output kind and one stability. */
public record AIcPublicationStabilityConfiguration(
        boolean publicationEnabled,
        List<AIcPublicationEndpoint> publicationEndpoints) {

    /** Normalizes the endpoint list to an immutable copy. */
    public AIcPublicationStabilityConfiguration {
        publicationEndpoints = List.copyOf(publicationEndpoints == null ? List.of() : publicationEndpoints);
    }
}
