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

    public AIcDependencyConstraintDefinition(
        AIcDependencyIdentity aIdentity,
        Set<AInDependencyUsage> aUsages,
        AIcVersionRequirement aVersionRequirement
    ) {
        identity = Objects.requireNonNull(aIdentity, "identity");
        usages = Set.copyOf(new LinkedHashSet<>(Objects.requireNonNull(aUsages, "usages")));
        versionRequirement = Objects.requireNonNull(aVersionRequirement, "versionRequirement");
    }

    public AIcDependencyIdentity identity() {
        return identity;
    }

    public Set<AInDependencyUsage> usages() {
        return usages;
    }

    public AIcVersionRequirement versionRequirement() {
        return versionRequirement;
    }
}
