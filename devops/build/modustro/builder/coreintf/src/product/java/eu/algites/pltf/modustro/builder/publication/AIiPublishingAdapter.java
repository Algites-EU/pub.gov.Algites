package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublishingEndpoint;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublishingPayload;

/** Executes exactly one publishing attempt for one endpoint. */
public interface AIiPublishingAdapter {
    /** Returns the adapter id referenced by PublishingAdapter. */
    String adapterId();

    /** Returns whether automatic retries are safe for this adapter and payload. */
    boolean isRetrySafe(AIcPublishingPayload aPayload, AIcPublishingEndpoint aEndpoint);

    /** Performs one attempt. Retry, ordering, and build-failure policy are owned by the scheduler. */
    void publish(AIcPublishingAttemptContext aContext) throws Exception;
}
