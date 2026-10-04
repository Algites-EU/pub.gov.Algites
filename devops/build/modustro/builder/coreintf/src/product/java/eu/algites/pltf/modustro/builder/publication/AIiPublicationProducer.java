package eu.algites.pltf.modustro.builder.publication;
import eu.algites.pltf.modustro.builder.model.publication.*;
import java.nio.file.Path;import java.util.Map;
/** Produces a derived immutable payload for its immediate successful parent publication. */
public interface AIiPublicationProducer {
 String producerId();
 AIcPublishingPayload produce(AIcPublishingPayload parent,AIcPublishingEndpointResult parentResult,Map<String,Object> context,Path directory) throws Exception;
}
