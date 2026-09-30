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

    public AIcDependencyIdentity identity() {
        return identity;
    }

    public Set<AInDependencyUsage> usages() {
        return usages;
    }

    public Set<String> requiredBuildOutputTypes() {
        return requiredBuildOutputTypes;
    }

    public AIcVersionRequirement versionRequirement() {
        return versionRequirement;
    }
}
