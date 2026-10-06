package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationFinalizationActionResult;

/** Executes exactly one attempt of a finalization action attached to a concrete publication. */
public interface AIiPublicationFinalizationActionAdapter {
    String adapterId();
    default boolean isRetrySafe(AIcPublicationFinalizationActionAttemptContext aContext) { return false; }
    AIcPublicationFinalizationActionResult execute(AIcPublicationFinalizationActionAttemptContext aContext) throws Exception;
}
