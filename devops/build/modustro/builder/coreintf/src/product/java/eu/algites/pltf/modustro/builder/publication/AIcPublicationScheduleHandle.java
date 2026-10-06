package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcFinalizationActionExecutionResult;
import eu.algites.pltf.modustro.builder.model.publication.AIcOutputPublicationExecutionResult;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationResult;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Observable handle for one scheduled output-publication graph. */
public final class AIcPublicationScheduleHandle {
    private final CompletionStage<Void> requiredCompletion;
    private final CompletionStage<AIcOutputPublicationExecutionResult> outputExecutionResult;
    private final Map<String, CompletionStage<AIcPublicationResult>> publicationResults;
    private final Map<String, CompletionStage<AIcFinalizationActionExecutionResult>> publicationFinalizationActionResults;

    public AIcPublicationScheduleHandle(
            CompletionStage<Void> aRequiredCompletion,
            CompletionStage<AIcOutputPublicationExecutionResult> aOutputExecutionResult,
            Map<String, ? extends CompletionStage<AIcPublicationResult>> aPublicationResults,
            Map<String, ? extends CompletionStage<AIcFinalizationActionExecutionResult>> aPublicationFinalizationActionResults) {
        requiredCompletion = Objects.requireNonNull(aRequiredCompletion, "requiredCompletion");
        outputExecutionResult = Objects.requireNonNull(aOutputExecutionResult, "outputExecutionResult");
        publicationResults = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(aPublicationResults, "publicationResults")));
        publicationFinalizationActionResults = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(aPublicationFinalizationActionResults, "publicationFinalizationActionResults")));
    }

    public CompletionStage<Void> requiredCompletion() { return requiredCompletion; }
    public CompletionStage<AIcOutputPublicationExecutionResult> outputExecutionResult() { return outputExecutionResult; }
    public Map<String, CompletionStage<AIcPublicationResult>> publicationResults() { return publicationResults; }
    public Map<String, CompletionStage<AIcFinalizationActionExecutionResult>> publicationFinalizationActionResults() { return publicationFinalizationActionResults; }
}
