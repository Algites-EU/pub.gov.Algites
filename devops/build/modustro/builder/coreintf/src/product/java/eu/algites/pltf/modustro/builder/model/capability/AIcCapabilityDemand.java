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

    public AIcCapabilityDemand(AIcCapabilityDemandKey aKey, Set<String> aReasons) {
        key = Objects.requireNonNull(aKey, "key");
        reasons = Set.copyOf(new LinkedHashSet<>(Objects.requireNonNull(aReasons, "reasons")));
        if (reasons.isEmpty()) {
            throw new IllegalArgumentException("Capability demand reasons must not be empty.");
        }
    }

    public AIcCapabilityDemandKey key() {
        return key;
    }

    public Set<String> reasons() {
        return reasons;
    }

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
