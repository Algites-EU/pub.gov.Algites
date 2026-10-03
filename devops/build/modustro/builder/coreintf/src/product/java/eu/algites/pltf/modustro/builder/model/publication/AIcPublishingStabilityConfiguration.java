package eu.algites.pltf.modustro.builder.model.publication;

import java.util.List;

/** Effective publishing configuration for one output kind and one stability. */
public record AIcPublishingStabilityConfiguration(
        boolean publishingEnabled,
        List<AIcPublishingEndpoint> publishingEndpoints) {

    /** Normalizes the endpoint list to an immutable copy. */
    public AIcPublishingStabilityConfiguration {
        publishingEndpoints = List.copyOf(publishingEndpoints == null ? List.of() : publishingEndpoints);
    }
}
