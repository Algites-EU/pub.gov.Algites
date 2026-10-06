package eu.algites.pltf.modustro.builder.model.publication;

import java.util.List;
import java.util.Map;

/** Complete immutable result tree of one concrete publication and all of its finalization descendants. */
public record AIcPublicationExecutionResult(
        String publicationId,
        AIcPublicationEndpoint endpoint,
        AIcPublicationPayload payload,
        Map<String, Object> configuration,
        AIcPublicationResult result,
        List<AIcFinalizationActionExecutionResult> publicationFinalizationActions,
        boolean failureHandled) {
    public AIcPublicationExecutionResult {
        configuration = AIcPublicationValues.freeze(configuration);
        publicationFinalizationActions = List.copyOf(publicationFinalizationActions == null ? List.of() : publicationFinalizationActions);
    }
    /** Compatibility constructor retaining the previous handled-failure semantics. */
    public AIcPublicationExecutionResult(
            String aPublicationId, AIcPublicationEndpoint aEndpoint, AIcPublicationPayload aPayload,
            Map<String, Object> aConfiguration, AIcPublicationResult aResult,
            List<AIcFinalizationActionExecutionResult> aPublicationFinalizationActions) {
        this(aPublicationId, aEndpoint, aPayload, aConfiguration, aResult, aPublicationFinalizationActions,
                aPublicationFinalizationActions == null || aPublicationFinalizationActions.stream().allMatch(AIcFinalizationActionExecutionResult::failureHandled));
    }

    public boolean success() {
        return result != null && result.success() && failureHandled;
    }
}
