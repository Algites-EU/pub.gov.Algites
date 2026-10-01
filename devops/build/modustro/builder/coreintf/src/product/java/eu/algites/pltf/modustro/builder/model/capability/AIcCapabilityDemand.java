package eu.algites.pltf.modustro.builder.model.capability;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * One deduplicated capability demand together with the reasons that requested it.
 */
public final class AIcCapabilityDemand {

    private final AIcCapabilityDemandKey key;
    private final Set<String> reasons;

    /**
     * Creates an {@code AIcCapabilityDemand} instance.
     *
     * @param aKey capability-demand key
     * @param aReasons reasons that requested the capability
     */
    public AIcCapabilityDemand(AIcCapabilityDemandKey aKey, Set<String> aReasons) {
        key = Objects.requireNonNull(aKey, "key");
        reasons = Set.copyOf(new LinkedHashSet<>(Objects.requireNonNull(aReasons, "reasons")));
        if (reasons.isEmpty()) {
            throw new IllegalArgumentException("Capability demand reasons must not be empty.");
        }
    }

    /**
     * Returns the stable key of this capability demand.
     * @return capability-demand key
     */
    public AIcCapabilityDemandKey key() {
        return key;
    }

    /**
     * Returns the reasons that requested this capability.
     * @return immutable set of demand reasons
     */
    public Set<String> reasons() {
        return reasons;
    }

    /**
     * Merges another demand for the same key into this demand.
     *
     * @param aOther other value to compare or merge
     * @return new demand containing the union of reasons
     * @throws IllegalArgumentException if the demand keys differ
     */
    public AIcCapabilityDemand merge(AIcCapabilityDemand aOther) {
        Objects.requireNonNull(aOther, "other");
        if (!key.equals(aOther.key)) {
            throw new IllegalArgumentException("Cannot merge capability demands with different keys.");
        }
        LinkedHashSet<String> locReasons = new LinkedHashSet<>(reasons);
        locReasons.addAll(aOther.reasons);
        return new AIcCapabilityDemand(key, locReasons);
    }
}
