package eu.algites.pltf.modustro.builder.model.publication;

import eu.algites.pltf.modustro.builder.model.resource.AIcResourceEndpointDefinition;
import java.util.List;
import java.util.Objects;

/**
 * Immutable publication destination selection for one publication capability invocation.
 */
public final class AIcPublicationDestinationSelection {

    private final String resourceKind;
    private final List<AIcResourceEndpointDefinition> endpoints;

    /**
     * Creates a publication destination selection.
     *
     * @param aResourceKind selected ResourceKind
     * @param aEndpoints selected effective endpoints
     */
    public AIcPublicationDestinationSelection(
        String aResourceKind,
        List<AIcResourceEndpointDefinition> aEndpoints
    ) {
        resourceKind = Objects.requireNonNull(aResourceKind, "resourceKind");
        endpoints = List.copyOf(Objects.requireNonNull(aEndpoints, "endpoints"));
    }

    /** @return selected ResourceKind */
    public String resourceKind() {
        return resourceKind;
    }

    /** @return immutable selected endpoint list */
    public List<AIcResourceEndpointDefinition> endpoints() {
        return endpoints;
    }
}
