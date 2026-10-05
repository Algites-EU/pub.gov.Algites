package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationEndpoint;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayload;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationResult;

/** Allows a post-publication action to perform one nested publication through the scheduler-owned transport path. */
@FunctionalInterface
public interface AIiPublicationDelegate {
    AIcPublicationResult publish(AIcPublicationPayload aPayload, AIcPublicationEndpoint aEndpoint) throws Exception;
}
