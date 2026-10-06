package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationFinalizationActionResult;

/** Executes exactly one output-publication finalization attempt. */
public interface AIiOutputPublicationFinalizationActionAdapter {
    String adapterId();
    default boolean isRetrySafe(AIcOutputPublicationFinalizationActionAttemptContext aContext) { return false; }
    AIcPublicationFinalizationActionResult execute(AIcOutputPublicationFinalizationActionAttemptContext aContext) throws Exception;
}
