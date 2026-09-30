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

    public String buildOutputType() {
        return buildOutputType;
    }

    public boolean canBeProduced() {
        return canBeProduced;
    }

    public boolean canBeUsedInDependency() {
        return canBeUsedInDependency;
    }

    public Set<String> dependencyOutputAlternatives() {
        return dependencyOutputAlternatives;
    }

    public String configurationSchemaId() {
        return configurationSchemaId;
    }
}
