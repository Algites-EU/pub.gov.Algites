package eu.algites.pltf.modustro.builder.model.capability;

import eu.algites.pltf.modustro.builder.model.output.AIcBuildOutputProductionPlan;
import java.util.Collection;

/**
 * Creates a portable capability demand DAG from build-output and explicit capability demands.
 */
public interface AIiCapabilityDemandPlanner {

    AIcCapabilityDemandGraph createDemandGraph(
        String aArtifactScopeIdentity,
        Collection<AIcBuildOutputProductionPlan> aBuildOutputPlans,
        Collection<AIcCapabilityDemand> aAdditionalDemands
    );
}
