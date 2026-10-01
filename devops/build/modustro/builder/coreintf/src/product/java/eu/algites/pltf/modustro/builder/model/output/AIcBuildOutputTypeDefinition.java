package eu.algites.pltf.modustro.builder.model.output;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Definition of one concrete or virtual build-output type.
 */
public final class AIcBuildOutputTypeDefinition {

    private final String buildOutputType;
    private final boolean canBeProduced;
    private final boolean canBeUsedInDependency;
    private final Set<String> dependencyOutputAlternatives;
    private final String configurationSchemaId;

    /**
     * Creates an {@code AIcBuildOutputTypeDefinition} instance.
     *
     * @param aBuildOutputType BuildOutputType identifier
     * @param aCanBeProduced whether this output type can be directly produced
     * @param aCanBeUsedInDependency whether this output type can be requested by dependencies
     * @param aDependencyOutputAlternatives concrete alternatives satisfying a virtual dependency output
     * @param aConfigurationSchemaId optional identifier of the capability configuration schema
     */
    public AIcBuildOutputTypeDefinition(
        String aBuildOutputType,
        boolean aCanBeProduced,
        boolean aCanBeUsedInDependency,
        Set<String> aDependencyOutputAlternatives,
        String aConfigurationSchemaId
    ) {
        buildOutputType = requireText(aBuildOutputType, "buildOutputType");
        canBeProduced = aCanBeProduced;
        canBeUsedInDependency = aCanBeUsedInDependency;
        dependencyOutputAlternatives = Set.copyOf(new LinkedHashSet<>(Objects.requireNonNull(aDependencyOutputAlternatives, "dependencyOutputAlternatives")));
        configurationSchemaId = normalize(aConfigurationSchemaId);
    }

    private static String requireText(String aValue, String aName) {
        String locValue = normalize(aValue);
        if (locValue == null) {
            throw new IllegalArgumentException(aName + " must not be blank.");
        }
        return locValue;
    }

    private static String normalize(String aValue) {
        if (aValue == null) {
            return null;
        }
        String locValue = aValue.trim();
        return locValue.isEmpty() ? null : locValue;
    }

    /**
     * Returns the BuildOutputType identifier.
     * @return BuildOutputType identifier
     */
    public String buildOutputType() {
        return buildOutputType;
    }

    /**
     * Returns whether this BuildOutputType can be produced directly.
     * @return whether the output is directly producible
     */
    public boolean canBeProduced() {
        return canBeProduced;
    }

    /**
     * Returns whether this BuildOutputType may be requested by a dependency.
     * @return whether the output may be used in dependencies
     */
    public boolean canBeUsedInDependency() {
        return canBeUsedInDependency;
    }

    /**
     * Returns concrete outputs that can satisfy this virtual dependency output.
     * @return immutable set of alternative BuildOutputTypes
     */
    public Set<String> dependencyOutputAlternatives() {
        return dependencyOutputAlternatives;
    }

    /**
     * Returns the optional configuration-schema identifier.
     * @return configuration-schema identifier, or {@code null} when no schema is defined
     */
    public String configurationSchemaId() {
        return configurationSchemaId;
    }
}
