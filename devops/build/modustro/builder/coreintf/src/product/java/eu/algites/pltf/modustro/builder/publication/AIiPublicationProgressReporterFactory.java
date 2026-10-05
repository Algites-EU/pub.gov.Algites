package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationEndpoint;

/** Creates a progress reporter for one scheduled publication endpoint. */
@FunctionalInterface
public interface AIiPublicationProgressReporterFactory {
    /** Returns the reporter used for the endpoint. */
    AIiPublicationProgressReporter create(AIcPublicationEndpoint aEndpoint);
}
