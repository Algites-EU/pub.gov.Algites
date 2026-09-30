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

    public AIcDependencyIdentity identity() {
        return identity;
    }

    public boolean constraintOnly() {
        return constraintOnly;
    }

    public Set<AInDependencyUsage> usages() {
        return usages;
    }

    public Map<AInDependencyUsage, String> nativeUsageMappings() {
        return nativeUsageMappings;
    }

    public Set<String> requiredBuildOutputTypes() {
        return requiredBuildOutputTypes;
    }

    public AIcVersionRequirement versionRequirement() {
        return versionRequirement;
    }
}
