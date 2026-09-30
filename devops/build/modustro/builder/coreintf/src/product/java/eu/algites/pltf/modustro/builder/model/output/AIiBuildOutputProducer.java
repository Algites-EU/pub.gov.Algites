package eu.algites.pltf.modustro.builder.model.output;

import eu.algites.pltf.modustro.builder.model.source.AIcPreparedSourceSet;

/**
 * Gradle-independent producer contract for one concrete BuildOutputType.
 */
public interface AIiBuildOutputProducer {

    String technologyKind();

    String buildOutputType();

    AIcBuildOutputProductionPlan createProductionPlan(AIcPreparedSourceSet aPreparedSourceSet);
}
