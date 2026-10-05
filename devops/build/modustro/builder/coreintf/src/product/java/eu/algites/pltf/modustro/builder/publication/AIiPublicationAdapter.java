package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationEndpoint;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayload;

/** Executes exactly one publication attempt for one endpoint. */
public interface AIiPublicationAdapter {
    /** Returns the adapter id referenced by PublicationAdapter. */
    String adapterId();

    /** Returns whether automatic retries are safe for this adapter and payload. */
    boolean isRetrySafe(AIcPublicationPayload aPayload, AIcPublicationEndpoint aEndpoint);

    /** Performs one attempt. Retry, ordering, and build-failure policy are owned by the scheduler. */
    void publish(AIcPublicationAttemptContext aContext) throws Exception;
}
