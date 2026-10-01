package eu.algites.pltf.modustro.builder.model.capability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Portable deduplicated capability demand DAG.
 */
public final class AIcCapabilityDemandGraph {

    private final List<AIcCapabilityDemand> demands;
    private final Set<AIcCapabilityDemandDependency> dependencies;

    /**
     * Creates an {@code AIcCapabilityDemandGraph} instance.
     *
     * @param aDemands capability demands
     * @param aDependencies directed prerequisite edges
     */
    public AIcCapabilityDemandGraph(
        List<AIcCapabilityDemand> aDemands,
        Set<AIcCapabilityDemandDependency> aDependencies
    ) {
        demands = List.copyOf(Objects.requireNonNull(aDemands, "demands"));
        dependencies = Set.copyOf(new LinkedHashSet<>(Objects.requireNonNull(aDependencies, "dependencies")));
        validate();
    }

    private void validate() {
        Set<AIcCapabilityDemandKey> locKeys = new LinkedHashSet<>();
        demands.forEach(locDemand -> {
            if (!locKeys.add(locDemand.key())) {
                throw new IllegalArgumentException("Capability demand graph contains duplicate key '" + locDemand.key() + "'.");
            }
        });
        dependencies.forEach(locDependency -> {
            if (!locKeys.contains(locDependency.prerequisite()) || !locKeys.contains(locDependency.dependent())) {
                throw new IllegalArgumentException("Capability demand dependency references a missing node.");
            }
        });
        topologicalOrder();
    }

    /**
     * Returns all demand nodes in deterministic insertion order.
     * @return immutable demand list
     */
    public List<AIcCapabilityDemand> demands() {
        return demands;
    }

    /**
     * Returns all directed prerequisite edges.
     * @return immutable prerequisite-edge set
     */
    public Set<AIcCapabilityDemandDependency> dependencies() {
        return dependencies;
    }

    /**
     * Returns the demands in prerequisite-before-dependent order.
     * @return immutable topologically ordered demand list
     */
    public List<AIcCapabilityDemand> topologicalOrder() {
        Map<AIcCapabilityDemandKey, AIcCapabilityDemand> locByKey = new LinkedHashMap<>();
        Map<AIcCapabilityDemandKey, Integer> locIncoming = new LinkedHashMap<>();
        Map<AIcCapabilityDemandKey, List<AIcCapabilityDemandKey>> locDependents = new LinkedHashMap<>();
        demands.forEach(locDemand -> {
            locByKey.put(locDemand.key(), locDemand);
            locIncoming.put(locDemand.key(), 0);
            locDependents.put(locDemand.key(), new ArrayList<>());
        });
        dependencies.forEach(locDependency -> {
            locIncoming.compute(locDependency.dependent(), (locKey, locValue) -> Objects.requireNonNull(locValue) + 1);
            locDependents.get(locDependency.prerequisite()).add(locDependency.dependent());
        });
        List<AIcCapabilityDemandKey> locReady = new ArrayList<>();
        locIncoming.forEach((locKey, locCount) -> {
            if (locCount == 0) {
                locReady.add(locKey);
            }
        });
        List<AIcCapabilityDemand> locResult = new ArrayList<>();
        int locIndex = 0;
        while (locIndex < locReady.size()) {
            AIcCapabilityDemandKey locKey = locReady.get(locIndex++);
            locResult.add(locByKey.get(locKey));
            locDependents.get(locKey).forEach(locDependent -> {
                int locRemaining = locIncoming.compute(locDependent, (aKey, aValue) -> Objects.requireNonNull(aValue) - 1);
                if (locRemaining == 0) {
                    locReady.add(locDependent);
                }
            });
        }
        if (locResult.size() != demands.size()) {
            throw new IllegalArgumentException("Capability demand graph contains a cycle.");
        }
        return List.copyOf(locResult);
    }
}
