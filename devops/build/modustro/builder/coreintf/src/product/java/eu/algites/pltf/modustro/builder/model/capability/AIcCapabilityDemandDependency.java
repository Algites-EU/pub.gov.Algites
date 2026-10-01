package eu.algites.pltf.modustro.builder.model.capability;

import java.util.Objects;

/**
 * Directed prerequisite edge between capability demands.
 */
public final class AIcCapabilityDemandDependency {

    private final AIcCapabilityDemandKey prerequisite;
    private final AIcCapabilityDemandKey dependent;

    /**
     * Creates an {@code AIcCapabilityDemandDependency} instance.
     *
     * @param aPrerequisite prerequisite capability-demand key
     * @param aDependent dependent capability-demand key
     */
    public AIcCapabilityDemandDependency(AIcCapabilityDemandKey aPrerequisite, AIcCapabilityDemandKey aDependent) {
        prerequisite = Objects.requireNonNull(aPrerequisite, "prerequisite");
        dependent = Objects.requireNonNull(aDependent, "dependent");
        if (prerequisite.equals(dependent)) {
            throw new IllegalArgumentException("Capability demand cannot depend on itself.");
        }
    }

    /**
     * Returns the prerequisite capability-demand key.
     * @return prerequisite key
     */
    public AIcCapabilityDemandKey prerequisite() {
        return prerequisite;
    }

    /**
     * Returns the dependent capability-demand key.
     * @return dependent key
     */
    public AIcCapabilityDemandKey dependent() {
        return dependent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object aOther) {
        if (this == aOther) {
            return true;
        }
        if (!(aOther instanceof AIcCapabilityDemandDependency locOther)) {
            return false;
        }
        return prerequisite.equals(locOther.prerequisite) && dependent.equals(locOther.dependent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(prerequisite, dependent);
    }
}
