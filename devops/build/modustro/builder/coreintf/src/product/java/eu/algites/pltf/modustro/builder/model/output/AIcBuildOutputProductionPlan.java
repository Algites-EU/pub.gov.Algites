package eu.algites.pltf.modustro.builder.model.output;

import eu.algites.pltf.modustro.builder.model.source.AIcPreparedSourceSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Portable plan for producing one concrete BuildOutputType from a prepared source set.
 */
public final class AIcBuildOutputProductionPlan {

    private final String technologyKind;
    private final String buildOutputType;
    private final AInBuildOutputProductionKind productionKind;
    private final AIcPreparedSourceSet preparedSourceSet;
    private final Set<AInBuildOutputProductionInput> requiredInputs;

    public AIcBuildOutputProductionPlan(
        String aTechnologyKind,
        String aBuildOutputType,
        AInBuildOutputProductionKind aProductionKind,
        AIcPreparedSourceSet aPreparedSourceSet,
        Set<AInBuildOutputProductionInput> aRequiredInputs
    ) {
        technologyKind = requireText(aTechnologyKind, "technologyKind");
        buildOutputType = requireText(aBuildOutputType, "buildOutputType");
        productionKind = Objects.requireNonNull(aProductionKind, "productionKind");
        preparedSourceSet = Objects.requireNonNull(aPreparedSourceSet, "preparedSourceSet");
        if (!technologyKind.equals(preparedSourceSet.technologyKind())) {
            throw new IllegalArgumentException(
                "Production-plan TechnologyKind '" + technologyKind + "' does not match PreparedSourceSet TechnologyKind '" + preparedSourceSet.technologyKind() + "'."
            );
        }
        requiredInputs = Set.copyOf(new LinkedHashSet<>(Objects.requireNonNull(aRequiredInputs, "requiredInputs")));
    }

    private static String requireText(String aValue, String aName) {
        Objects.requireNonNull(aValue, aName);
        String locValue = aValue.trim();
        if (locValue.isEmpty()) {
            throw new IllegalArgumentException(aName + " must not be blank.");
        }
        return locValue;
    }

    public String technologyKind() {
        return technologyKind;
    }

    public String buildOutputType() {
        return buildOutputType;
    }

    public AInBuildOutputProductionKind productionKind() {
        return productionKind;
    }

    public AIcPreparedSourceSet preparedSourceSet() {
        return preparedSourceSet;
    }

    public Set<AInBuildOutputProductionInput> requiredInputs() {
        return requiredInputs;
    }
}
