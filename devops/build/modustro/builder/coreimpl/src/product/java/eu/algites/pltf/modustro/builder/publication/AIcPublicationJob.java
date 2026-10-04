package eu.algites.pltf.modustro.builder.publication;
import eu.algites.pltf.modustro.builder.model.publication.*;
/** One independently scheduled form or recursive extension, identified by its full path. */
public record AIcPublicationJob(String id,String parentId,AIcPublishingEndpoint endpoint,AIiPayloadFactory payloadFactory) {
 @FunctionalInterface public interface AIiPayloadFactory {AIcPublishingPayload prepare(AIcPublishingPayload parent,AIcPublishingEndpointResult result) throws Exception;}
}
