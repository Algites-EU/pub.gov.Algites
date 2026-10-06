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
        List<AIcFinalizationActionExecutionResult> publicationFinalizationActions) {
    public AIcPublicationExecutionResult {
        configuration = AIcPublicationValues.freeze(configuration);
        publicationFinalizationActions = List.copyOf(publicationFinalizationActions == null ? List.of() : publicationFinalizationActions);
    }
    public boolean success() {
        return result != null && result.success() && publicationFinalizationActions.stream().allMatch(AIcPublicationExecutionResult::AIcFinalizationSuccess);
    }
    private static boolean AIcFinalizationSuccess(AIcFinalizationActionExecutionResult aNode) {
        return aNode.result() != null && (aNode.result().success() || aNode.result().ignoredFailure())
                && aNode.finalizationActions().stream().allMatch(AIcPublicationExecutionResult::AIcFinalizationSuccess);
    }
}
