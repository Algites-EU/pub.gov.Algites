package eu.algites.pltf.modustro.builder.model.publication;

import java.util.List;
import java.util.Map;

/** Immutable execution node for one finalization action, including recursive child results when applicable. */
public record AIcFinalizationActionExecutionResult(
        String id,
        String scopeKind,
        AIcPublicationFinalizationActionResult result,
        Map<String, Object> configuration,
        List<AIcFinalizationActionExecutionResult> finalizationActions) {
    public AIcFinalizationActionExecutionResult {
        configuration = AIcPublicationValues.freeze(configuration);
        finalizationActions = List.copyOf(finalizationActions == null ? List.of() : finalizationActions);
    }
}
