package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationFinalizationActionResult;

/** Executes exactly one artifact-publication finalization attempt. */
public interface AIiArtifactPublicationFinalizationActionAdapter {
    String adapterId();
    default boolean isRetrySafe(AIcArtifactPublicationFinalizationActionAttemptContext aContext) { return false; }
    AIcPublicationFinalizationActionResult execute(AIcArtifactPublicationFinalizationActionAttemptContext aContext) throws Exception;
}
