package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPostPublicationActionResult;
import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationResult;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Observable handle for one scheduled output-publication graph. */
public final class AIcPublicationScheduleHandle {
    private final CompletionStage<Void> requiredCompletion;
    private final Map<String, CompletionStage<AIcPublicationResult>> publicationResults;
    private final Map<String, CompletionStage<AIcPostPublicationActionResult>> postPublicationActionResults;

    public AIcPublicationScheduleHandle(CompletionStage<Void> aRequiredCompletion,
            Map<String, ? extends CompletionStage<AIcPublicationResult>> aPublicationResults) {
        this(aRequiredCompletion, aPublicationResults, Map.of());
    }

    public AIcPublicationScheduleHandle(CompletionStage<Void> aRequiredCompletion,
            Map<String, ? extends CompletionStage<AIcPublicationResult>> aPublicationResults,
            Map<String, ? extends CompletionStage<AIcPostPublicationActionResult>> aPostPublicationActionResults) {
        requiredCompletion = Objects.requireNonNull(aRequiredCompletion, "requiredCompletion");
        publicationResults = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(aPublicationResults, "publicationResults")));
        postPublicationActionResults = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(aPostPublicationActionResults, "postPublicationActionResults")));
    }

    public CompletionStage<Void> requiredCompletion() { return requiredCompletion; }
    public Map<String, CompletionStage<AIcPublicationResult>> publicationResults() { return publicationResults; }
    public Map<String, CompletionStage<AIcPostPublicationActionResult>> postPublicationActionResults() { return postPublicationActionResults; }
}
