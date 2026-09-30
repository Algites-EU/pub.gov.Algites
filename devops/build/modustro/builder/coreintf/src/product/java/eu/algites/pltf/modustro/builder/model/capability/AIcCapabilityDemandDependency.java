package eu.algites.pltf.modustro.builder.model.capability;

import java.util.Objects;

/**
 * Directed prerequisite edge between capability demands.
 */
public final class AIcCapabilityDemandDependency {

    private final AIcCapabilityDemandKey prerequisite;
    private final AIcCapabilityDemandKey dependent;

    public AIcCapabilityDemandDependency(AIcCapabilityDemandKey aPrerequisite, AIcCapabilityDemandKey aDependent) {
        prerequisite = Objects.requireNonNull(aPrerequisite, "prerequisite");
        dependent = Objects.requireNonNull(aDependent, "dependent");
        if (prerequisite.equals(dependent)) {
            throw new IllegalArgumentException("Capability demand cannot depend on itself.");
        }
    }

    public AIcCapabilityDemandKey prerequisite() {
        return prerequisite;
    }

    public AIcCapabilityDemandKey dependent() {
        return dependent;
    }

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

    @Override
    public int hashCode() {
        return Objects.hash(prerequisite, dependent);
    }
}
