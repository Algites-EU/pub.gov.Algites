package eu.algites.pltf.modustro.builder.model.dependency.resolution;

import eu.algites.pltf.modustro.builder.model.dependency.AIcDependencyIdentity;
import eu.algites.pltf.modustro.builder.model.dependency.AIcVersionRequirement;
import eu.algites.pltf.modustro.builder.model.dependency.AInDependencyUsage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One portable dependency or constraint translated into technology-native logical roles.
 *
 * Native usage values are stable bridge identifiers such as Gradle configuration names or
 * Python dependency-environment roles. They intentionally do not expose Gradle API types.
 */
public final class AIcDependencyTechnologyPlanEntry {

    private final AIcDependencyIdentity identity;
    private final boolean constraintOnly;
    private final Set<AInDependencyUsage> usages;
    private final Map<AInDependencyUsage, String> nativeUsageMappings;
    private final Set<String> requiredBuildOutputTypes;
    private final AIcVersionRequirement versionRequirement;

    /**
     * Creates an {@code AIcDependencyTechnologyPlanEntry} instance.
     *
     * @param aIdentity dependency or item identity
     * @param aConstraintOnly whether the entry represents a constraint rather than a dependency edge
     * @param aUsages portable dependency usages
     * @param aNativeUsageMappings technology-native usage mappings
     * @param aRequiredBuildOutputTypes required dependency build-output types
     * @param aVersionRequirement version requirement
     */
    public AIcDependencyTechnologyPlanEntry(
        AIcDependencyIdentity aIdentity,
        boolean aConstraintOnly,
        Set<AInDependencyUsage> aUsages,
        Map<AInDependencyUsage, String> aNativeUsageMappings,
        Set<String> aRequiredBuildOutputTypes,
        AIcVersionRequirement aVersionRequirement
    ) {
        identity = Objects.requireNonNull(aIdentity, "identity");
        constraintOnly = aConstraintOnly;
        usages = Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(aUsages, "usages")));
        nativeUsageMappings = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(aNativeUsageMappings, "nativeUsageMappings")));
        requiredBuildOutputTypes = Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(aRequiredBuildOutputTypes, "requiredBuildOutputTypes")));
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
     * Returns whether this entry represents only a dependency constraint.
     * @return whether the entry is constraint-only
     */
    public boolean constraintOnly() {
        return constraintOnly;
    }

    /**
     * Returns the portable dependency usages.
     * @return immutable dependency-usage set
     */
    public Set<AInDependencyUsage> usages() {
        return usages;
    }

    /**
     * Returns the technology-native mapping for each portable usage.
     * @return immutable usage mapping
     */
    public Map<AInDependencyUsage, String> nativeUsageMappings() {
        return nativeUsageMappings;
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
