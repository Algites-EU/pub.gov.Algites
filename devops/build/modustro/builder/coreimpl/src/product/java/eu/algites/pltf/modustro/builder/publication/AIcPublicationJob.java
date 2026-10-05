package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPostPublicationAction;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationEndpoint;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationPayload;
import java.util.List;
import java.util.Map;

/** One independently scheduled root publication and its recursive post-publication action tree. */
public record AIcPublicationJob(
        String id,
        AIcPublicationEndpoint endpoint,
        AIcPublicationPayload payload,
        Map<String, Object> configuration,
        List<AIcPostPublicationAction> postPublicationActions,
        Map<String, AIcPublicationEndpoint> publicationEndpointRegistry) {
    public AIcPublicationJob {
        configuration = Map.copyOf(configuration == null ? Map.of() : configuration);
        postPublicationActions = List.copyOf(postPublicationActions == null ? List.of() : postPublicationActions);
        publicationEndpointRegistry = Map.copyOf(publicationEndpointRegistry == null ? Map.of() : publicationEndpointRegistry);
    }

    /** Compatibility constructor using the root endpoint as the complete endpoint registry. */
    public AIcPublicationJob(
            String aId, AIcPublicationEndpoint aEndpoint, AIcPublicationPayload aPayload, Map<String, Object> aConfiguration,
            List<AIcPostPublicationAction> aPostPublicationActions) {
        this(aId, aEndpoint, aPayload, aConfiguration, aPostPublicationActions, Map.of(aEndpoint.id(), aEndpoint));
    }
}
