package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublishingEndpointResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Observable handle for one scheduled output publication. */
public final class AIcPublishingScheduleHandle {
    private final CompletionStage<Void> requiredCompletion;
    private final Map<String, CompletionStage<AIcPublishingEndpointResult>> endpointResults;

    /** Creates an immutable publishing schedule handle. */
    public AIcPublishingScheduleHandle(
            CompletionStage<Void> aRequiredCompletion,
            Map<String, ? extends CompletionStage<AIcPublishingEndpointResult>> aEndpointResults) {
        requiredCompletion = Objects.requireNonNull(aRequiredCompletion, "requiredCompletion");
        endpointResults = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(aEndpointResults, "endpointResults")));
    }

    /** Completes when all build-blocking publication work succeeds or one such endpoint finally fails. */
    public CompletionStage<Void> requiredCompletion() {
        return requiredCompletion;
    }

    /** Returns completion stages for all started or planned endpoint publications, including best-effort work. */
    public Map<String, CompletionStage<AIcPublishingEndpointResult>> endpointResults() {
        return endpointResults;
    }
}
