package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPostPublicationActionResult;

/** Executes exactly one attempt of a post-publication action. */
public interface AIiPostPublicationActionAdapter {
    String adapterId();
    default boolean isRetrySafe(AIcPostPublicationActionAttemptContext aContext) { return false; }
    AIcPostPublicationActionResult execute(AIcPostPublicationActionAttemptContext aContext) throws Exception;
}
