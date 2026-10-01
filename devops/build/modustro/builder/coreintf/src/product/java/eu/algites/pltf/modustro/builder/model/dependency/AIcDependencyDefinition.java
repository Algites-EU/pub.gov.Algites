package eu.algites.pltf.modustro.builder.model.dependency;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Effective portable dependency definition.
 */
public final class AIcDependencyDefinition {

    private final AIcDependencyIdentity identity;
    private final Set<AInDependencyUsage> usages;
    private final Set<String> requiredBuildOutputTypes;
    private final AIcVersionRequirement versionRequirement;

    /**
     * Creates an {@code AIcDependencyDefinition} instance.
     *
     * @param aIdentity dependency or item identity
     * @param aUsages portable dependency usages
     * @param aRequiredBuildOutputTypes required dependency build-output types
     * @param aVersionRequirement version requirement
     */
    public AIcDependencyDefinition(
        AIcDependencyIdentity aIdentity,
        Set<AInDependencyUsage> aUsages,
        Set<String> aRequiredBuildOutputTypes,
        AIcVersionRequirement aVersionRequirement
    ) {
        identity = Objects.requireNonNull(aIdentity, "identity");
        usages = Set.copyOf(new LinkedHashSet<>(Objects.requireNonNull(aUsages, "usages")));
        requiredBuildOutputTypes = Set.copyOf(new LinkedHashSet<>(Objects.requireNonNull(aRequiredBuildOutputTypes, "requiredBuildOutputTypes")));
        versionRequirement = aVersionRequirement;
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
     * Returns the dependency build-output types required by the consumer.
     * @return immutable set of required build-output types
     */
    public Set<String> requiredBuildOutputTypes() {
        return requiredBuildOutputTypes;
    }

    /**
     * Returns the effective version requirement.
     * @return version requirement, or {@code null} when no requirement is declared
     */
    public AIcVersionRequirement versionRequirement() {
        return versionRequirement;
    }
}
