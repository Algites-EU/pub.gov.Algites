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

    /**
     * Creates an {@code AIcTechnologyKindDefinition} instance.
     *
     * @param aTechnologyKind TechnologyKind identifier
     * @param aCapabilities capability definitions
     * @param aBuildOutputTypes build-output type definitions
     * @param aDefaultBuildOutputTypes default artifact build-output types
     * @param aDefaultDependencyOutputTypes default dependency output types
     */
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

    /**
     * Returns the TechnologyKind identifier.
     * @return TechnologyKind identifier
     */
    public String technologyKind() {
        return technologyKind;
    }

    /**
     * Returns capabilities defined for this TechnologyKind.
     * @return immutable capability-definition list
     */
    public List<AIcCapabilityDefinition> capabilities() {
        return capabilities;
    }

    /**
     * Returns BuildOutputTypes defined for this TechnologyKind.
     * @return immutable build-output definition list
     */
    public List<AIcBuildOutputTypeDefinition> buildOutputTypes() {
        return buildOutputTypes;
    }

    /**
     * Returns the default BuildOutputTypes for a TechnologyKind.
     * @return immutable default BuildOutputType set
     */
    public Set<String> defaultBuildOutputTypes() {
        return defaultBuildOutputTypes;
    }

    /**
     * Returns BuildOutputTypes required by default when depending on this TechnologyKind.
     * @return immutable default dependency-output set
     */
    public Set<String> defaultDependencyOutputTypes() {
        return defaultDependencyOutputTypes;
    }
}
