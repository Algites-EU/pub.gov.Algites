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
    private final Set<String> requiredCapabilityIds;

    /**
     * Creates an {@code AIcBuildOutputProductionPlan} instance.
     *
     * @param aTechnologyKind TechnologyKind identifier
     * @param aBuildOutputType BuildOutputType identifier
     * @param aProductionKind native production primitive
     * @param aPreparedSourceSet prepared source set
     * @param aRequiredInputs logical inputs required for production
     * @param aRequiredCapabilityIds capability identifiers required for production
     */
    public AIcBuildOutputProductionPlan(
        String aTechnologyKind,
        String aBuildOutputType,
        AInBuildOutputProductionKind aProductionKind,
        AIcPreparedSourceSet aPreparedSourceSet,
        Set<AInBuildOutputProductionInput> aRequiredInputs,
        Set<String> aRequiredCapabilityIds
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
        requiredCapabilityIds = Set.copyOf(new LinkedHashSet<>(Objects.requireNonNull(aRequiredCapabilityIds, "requiredCapabilityIds")));
        requiredCapabilityIds.forEach(locCapabilityId -> {
            if (locCapabilityId == null || locCapabilityId.trim().isEmpty()) {
                throw new IllegalArgumentException("requiredCapabilityIds must not contain blank values.");
            }
        });
    }

    private static String requireText(String aValue, String aName) {
        Objects.requireNonNull(aValue, aName);
        String locValue = aValue.trim();
        if (locValue.isEmpty()) {
            throw new IllegalArgumentException(aName + " must not be blank.");
        }
        return locValue;
    }

    /**
     * Returns the TechnologyKind identifier.
     * @return TechnologyKind identifier
     */
    public String technologyKind() {
        return technologyKind;
    }

    /**
     * Returns the BuildOutputType identifier.
     * @return BuildOutputType identifier
     */
    public String buildOutputType() {
        return buildOutputType;
    }

    /**
     * Returns the built-in production primitive used by this plan.
     * @return production primitive
     */
    public AInBuildOutputProductionKind productionKind() {
        return productionKind;
    }

    /**
     * Returns the prepared sources consumed by this production plan.
     * @return prepared source set
     */
    public AIcPreparedSourceSet preparedSourceSet() {
        return preparedSourceSet;
    }

    /**
     * Returns additional logical inputs required by this production plan.
     * @return immutable set of required logical inputs
     */
    public Set<AInBuildOutputProductionInput> requiredInputs() {
        return requiredInputs;
    }

    /**
     * Returns capability identifiers required before this output can be produced.
     * @return immutable set of capability identifiers
     */
    public Set<String> requiredCapabilityIds() {
        return requiredCapabilityIds;
    }
}
