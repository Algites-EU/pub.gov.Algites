package eu.algites.pltf.modustro.builder.model.publication;

import java.util.List;
import java.util.Map;

/** Immutable execution node for one finalization action, including recursive child results when applicable. */
public record AIcFinalizationActionExecutionResult(
        String id,
        String scopeKind,
        AIcPublicationFinalizationActionResult result,
        Map<String, Object> configuration,
        List<AIcFinalizationActionExecutionResult> finalizationActions,
        boolean failureHandled) {
    public AIcFinalizationActionExecutionResult {
        configuration = AIcPublicationValues.freeze(configuration);
        finalizationActions = List.copyOf(finalizationActions == null ? List.of() : finalizationActions);
    }

    /** Compatibility constructor retaining the previous handled-failure semantics. */
    public AIcFinalizationActionExecutionResult(
            String aId, String aScopeKind, AIcPublicationFinalizationActionResult aResult,
            Map<String, Object> aConfiguration, List<AIcFinalizationActionExecutionResult> aFinalizationActions) {
        this(aId, aScopeKind, aResult, aConfiguration, aFinalizationActions,
                aResult != null && (aResult.success() || aResult.ignoredFailure())
                        && (aFinalizationActions == null || aFinalizationActions.stream().allMatch(AIcFinalizationActionExecutionResult::failureHandled)));
    }
}
