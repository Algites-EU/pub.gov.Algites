package eu.algites.pltf.modustro.builder.model.output;

import eu.algites.pltf.modustro.builder.model.source.AIcPreparedSourceSet;

/**
 * Gradle-independent producer contract for one concrete BuildOutputType.
 */
public interface AIiBuildOutputProducer {

    /**
     * Returns the TechnologyKind supported by this producer.
     *
     * @return TechnologyKind identifier
     */
    String technologyKind();

    /**
     * Returns the concrete BuildOutputType produced by this producer.
     *
     * @return BuildOutputType identifier
     */
    String buildOutputType();

    /**
     * Creates a portable production plan for prepared sources.
     *
     * @param aPreparedSourceSet prepared source set for the producer TechnologyKind
     * @return build-output production plan
     */
    AIcBuildOutputProductionPlan createProductionPlan(AIcPreparedSourceSet aPreparedSourceSet);
}
