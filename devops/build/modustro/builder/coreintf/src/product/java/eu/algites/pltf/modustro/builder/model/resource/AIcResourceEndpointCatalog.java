package eu.algites.pltf.modustro.builder.model.resource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable catalog of effective ResourceEndpoint definitions.
 */
public final class AIcResourceEndpointCatalog {

    private final List<AIcResourceEndpointDefinition> endpoints;
    private final Map<String, AIcResourceEndpointDefinition> endpointsById;

    /**
     * Creates an immutable catalog.
     *
     * @param aEndpoints effective ResourceEndpoint definitions
     */
    public AIcResourceEndpointCatalog(Iterable<AIcResourceEndpointDefinition> aEndpoints) {
        Objects.requireNonNull(aEndpoints, "endpoints");
        ArrayList<AIcResourceEndpointDefinition> locEndpoints = new ArrayList<>();
        LinkedHashMap<String, AIcResourceEndpointDefinition> locEndpointsById = new LinkedHashMap<>();
        for (AIcResourceEndpointDefinition locEndpoint : aEndpoints) {
            Objects.requireNonNull(locEndpoint, "endpoint");
            AIcResourceEndpointDefinition locPrevious = locEndpointsById.putIfAbsent(locEndpoint.id(), locEndpoint);
            if (locPrevious != null) {
                throw new IllegalArgumentException(
                    "ResourceEndpoint id '" + locEndpoint.id() + "' is duplicated across effective endpoint definitions."
                );
            }
            locEndpoints.add(locEndpoint);
        }
        endpoints = List.copyOf(locEndpoints);
        endpointsById = Map.copyOf(locEndpointsById);
    }

    /**
     * Returns all effective endpoints in deterministic declaration order.
     *
     * @return immutable endpoint list
     */
    public List<AIcResourceEndpointDefinition> all() {
        return endpoints;
    }

    /**
     * Resolves one effective endpoint by its globally stable identifier.
     *
     * @param aId ResourceEndpoint identifier
     * @return matching endpoint when present
     */
    public Optional<AIcResourceEndpointDefinition> byId(String aId) {
        return Optional.ofNullable(endpointsById.get(aId));
    }

    /**
     * Selects endpoints matching one effective resource-operation context.
     *
     * @param aTechnologyKind required TechnologyKind
     * @param aResourceKind required ResourceKind
     * @param aVisibility required visibility
     * @param aAction required action
     * @param aStability optional required stability; {@code null} accepts any effective stability
     * @param aEnabledOnly whether disabled endpoints must be excluded
     * @return immutable matching endpoint list
     */
    public List<AIcResourceEndpointDefinition> select(
        String aTechnologyKind,
        String aResourceKind,
        String aVisibility,
        AInResourceEndpointAction aAction,
        AInResourceStability aStability,
        boolean aEnabledOnly
    ) {
        Objects.requireNonNull(aAction, "action");
        return endpoints.stream()
            .filter(locEndpoint -> locEndpoint.technologyKind().equals(aTechnologyKind))
            .filter(locEndpoint -> locEndpoint.resourceKind().equals(aResourceKind))
            .filter(locEndpoint -> locEndpoint.visibility().equals(aVisibility))
            .filter(locEndpoint -> locEndpoint.action() == aAction)
            .filter(locEndpoint -> aStability == null || locEndpoint.stability() == aStability)
            .filter(locEndpoint -> !aEnabledOnly || locEndpoint.enabled())
            .toList();
    }
}
