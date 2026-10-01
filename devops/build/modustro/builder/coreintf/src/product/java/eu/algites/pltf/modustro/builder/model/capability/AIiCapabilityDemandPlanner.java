package eu.algites.pltf.modustro.builder.model.capability;

import eu.algites.pltf.modustro.builder.model.output.AIcBuildOutputProductionPlan;
import java.util.Collection;

/**
 * Creates a portable capability demand DAG from build-output and explicit capability demands.
 */
public interface AIiCapabilityDemandPlanner {

    /**
     * Builds a deduplicated capability demand graph for one artifact scope.
     *
     * @param aArtifactScopeIdentity stable identity of the artifact scope
     * @param aBuildOutputPlans selected build-output production plans
     * @param aAdditionalDemands additional explicit capability demands
     * @return validated capability demand graph
     */
    AIcCapabilityDemandGraph createDemandGraph(
        String aArtifactScopeIdentity,
        Collection<AIcBuildOutputProductionPlan> aBuildOutputPlans,
        Collection<AIcCapabilityDemand> aAdditionalDemands
    );
}
