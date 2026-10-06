package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationFinalizationActionResult;

/** Executes exactly one Version Scope publication-finalization attempt. */
public interface AIiVersionScopePublicationFinalizationActionAdapter {
    String adapterId();
    default boolean isRetrySafe(AIcVersionScopePublicationFinalizationActionAttemptContext aContext) { return false; }
    AIcPublicationFinalizationActionResult execute(AIcVersionScopePublicationFinalizationActionAttemptContext aContext) throws Exception;
}
