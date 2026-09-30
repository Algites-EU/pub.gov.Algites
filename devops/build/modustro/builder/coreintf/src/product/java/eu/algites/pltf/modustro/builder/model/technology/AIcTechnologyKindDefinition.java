package eu.algites.pltf.modustro.builder.model.technology;

import eu.algites.pltf.modustro.builder.model.capability.AIcCapabilityDefinition;
import eu.algites.pltf.modustro.builder.model.output.AIcBuildOutputTypeDefinition;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Gradle-independent definition of one Modustro Builder TechnologyKind.
 */
public final class AIcTechnologyKindDefinition {

    private final String technologyKind;
    private final List<AIcCapabilityDefinition> capabilities;
    private final List<AIcBuildOutputTypeDefinition> buildOutputTypes;
    private final Set<String> defaultBuildOutputTypes;
    private final Set<String> defaultDependencyOutputTypes;

    public AIcTechnologyKindDefinition(
        String aTechnologyKind,
        List<AIcCapabilityDefinition> aCapabilities,
        List<AIcBuildOutputTypeDefinition> aBuildOutputTypes,
        Set<String> aDefaultBuildOutputTypes,
        Set<String> aDefaultDependencyOutputTypes
    ) {
        technologyKind = Objects.requireNonNull(aTechnologyKind, "technologyKind").trim();
        if (technologyKind.isEmpty()) {
            throw new IllegalArgumentException("technologyKind must not be blank.");
        }
        capabilities = List.copyOf(Objects.requireNonNull(aCapabilities, "capabilities"));
        buildOutputTypes = List.copyOf(Objects.requireNonNull(aBuildOutputTypes, "buildOutputTypes"));
        defaultBuildOutputTypes = Set.copyOf(new LinkedHashSet<>(Objects.requireNonNull(aDefaultBuildOutputTypes, "defaultBuildOutputTypes")));
        defaultDependencyOutputTypes = Set.copyOf(new LinkedHashSet<>(Objects.requireNonNull(aDefaultDependencyOutputTypes, "defaultDependencyOutputTypes")));
    }

    public String technologyKind() {
        return technologyKind;
    }

    public List<AIcCapabilityDefinition> capabilities() {
        return capabilities;
    }

    public List<AIcBuildOutputTypeDefinition> buildOutputTypes() {
        return buildOutputTypes;
    }

    public Set<String> defaultBuildOutputTypes() {
        return defaultBuildOutputTypes;
    }

    public Set<String> defaultDependencyOutputTypes() {
        return defaultDependencyOutputTypes;
    }
}
