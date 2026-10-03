package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublishingEndpoint;

/** Creates a progress reporter for one scheduled publishing endpoint. */
@FunctionalInterface
public interface AIiPublishingProgressReporterFactory {
    /** Returns the reporter used for the endpoint. */
    AIiPublishingProgressReporter create(AIcPublishingEndpoint aEndpoint);
}
