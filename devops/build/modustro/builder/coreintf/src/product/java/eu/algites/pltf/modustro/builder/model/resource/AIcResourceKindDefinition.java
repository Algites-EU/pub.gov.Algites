package eu.algites.pltf.modustro.builder.model.resource;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Gradle-independent definition of one ResourceKind handled by Modustro Builder.
 */
public final class AIcResourceKindDefinition {

    private final String resourceKind;
    private final Set<String> technologyKinds;
    private final Set<AInResourceEndpointAction> actions;
    private final AInResourceStabilityRequirement stabilityRequirement;

    /**
     * Creates a ResourceKind definition.
     *
     * @param aResourceKind ResourceKind identifier
     * @param aTechnologyKinds TechnologyKinds that may expose endpoints for the ResourceKind
     * @param aActions actions supported for the ResourceKind
     * @param aStabilityRequirement rule governing endpoint stability
     */
    public AIcResourceKindDefinition(
        String aResourceKind,
        Set<String> aTechnologyKinds,
        Set<AInResourceEndpointAction> aActions,
        AInResourceStabilityRequirement aStabilityRequirement
    ) {
        resourceKind = requireText(aResourceKind, "resourceKind");
        technologyKinds = Set.copyOf(new LinkedHashSet<>(Objects.requireNonNull(aTechnologyKinds, "technologyKinds")));
        actions = Set.copyOf(new LinkedHashSet<>(Objects.requireNonNull(aActions, "actions")));
        stabilityRequirement = Objects.requireNonNull(aStabilityRequirement, "stabilityRequirement");
        if (technologyKinds.isEmpty()) {
            throw new IllegalArgumentException("technologyKinds must not be empty.");
        }
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("actions must not be empty.");
        }
        technologyKinds.forEach(locTechnologyKind -> requireText(locTechnologyKind, "technologyKind"));
    }

    private static String requireText(String aValue, String aName) {
        String locValue = aValue == null ? null : aValue.trim();
        if (locValue == null || locValue.isEmpty()) {
            throw new IllegalArgumentException(aName + " must not be blank.");
        }
        return locValue;
    }

    /**
     * Returns the ResourceKind identifier.
     * @return ResourceKind identifier
     */
    public String resourceKind() {
        return resourceKind;
    }

    /**
     * Returns TechnologyKinds that may use this ResourceKind.
     * @return immutable TechnologyKind set
     */
    public Set<String> technologyKinds() {
        return technologyKinds;
    }

    /**
     * Returns actions supported by this ResourceKind.
     * @return immutable action set
     */
    public Set<AInResourceEndpointAction> actions() {
        return actions;
    }

    /**
     * Returns the stability declaration rule for endpoints of this ResourceKind.
     * @return stability requirement
     */
    public AInResourceStabilityRequirement stabilityRequirement() {
        return stabilityRequirement;
    }
}
