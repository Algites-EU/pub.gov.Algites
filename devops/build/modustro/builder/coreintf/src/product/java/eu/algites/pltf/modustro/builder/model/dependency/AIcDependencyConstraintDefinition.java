package eu.algites.pltf.modustro.builder.model.dependency;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Effective portable dependency constraint. It does not create a dependency edge.
 */
public final class AIcDependencyConstraintDefinition {

    private final AIcDependencyIdentity identity;
    private final Set<AInDependencyUsage> usages;
    private final AIcVersionRequirement versionRequirement;

    /**
     * Creates an {@code AIcDependencyConstraintDefinition} instance.
     *
     * @param aIdentity dependency or item identity
     * @param aUsages portable dependency usages
     * @param aVersionRequirement version requirement
     */
    public AIcDependencyConstraintDefinition(
        AIcDependencyIdentity aIdentity,
        Set<AInDependencyUsage> aUsages,
        AIcVersionRequirement aVersionRequirement
    ) {
        identity = Objects.requireNonNull(aIdentity, "identity");
        usages = Set.copyOf(new LinkedHashSet<>(Objects.requireNonNull(aUsages, "usages")));
        versionRequirement = Objects.requireNonNull(aVersionRequirement, "versionRequirement");
    }

    /**
     * Returns the dependency identity.
     * @return dependency identity
     */
    public AIcDependencyIdentity identity() {
        return identity;
    }

    /**
     * Returns the portable dependency usages.
     * @return immutable dependency-usage set
     */
    public Set<AInDependencyUsage> usages() {
        return usages;
    }

    /**
     * Returns the effective version requirement.
     * @return effective non-null version requirement
     */
    public AIcVersionRequirement versionRequirement() {
        return versionRequirement;
    }
}
