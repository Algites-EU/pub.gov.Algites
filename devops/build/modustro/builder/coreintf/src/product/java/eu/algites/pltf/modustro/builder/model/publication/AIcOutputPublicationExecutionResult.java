package eu.algites.pltf.modustro.builder.model.publication;

import java.util.List;
import java.util.Map;

/** Complete publication result for one artifact output and stability lane. */
public record AIcOutputPublicationExecutionResult(
        String artifactIdentity,
        String technologyKind,
        AInPublicationOutputKind outputKind,
        AInPublicationStability stability,
        String version,
        Map<String, AIcPublicationEndpoint> publicationEndpointRegistry,
        Map<String, AIcPublicationEndpoint> snapshotPublicationEndpoints,
        List<AIcPublicationExecutionResult> publications,
        List<AIcFinalizationActionExecutionResult> outputPublicationFinalizationActions,
        boolean completed) {
    /** Constructs a terminal result for a scheduled output. */
    public AIcOutputPublicationExecutionResult(
            String aArtifactIdentity, String aTechnologyKind, AInPublicationOutputKind aOutputKind,
            AInPublicationStability aStability, String aVersion,
            Map<String, AIcPublicationEndpoint> aPublicationEndpointRegistry,
            Map<String, AIcPublicationEndpoint> aSnapshotPublicationEndpoints,
            List<AIcPublicationExecutionResult> aPublications,
            List<AIcFinalizationActionExecutionResult> aOutputPublicationFinalizationActions) {
        this(aArtifactIdentity, aTechnologyKind, aOutputKind, aStability, aVersion,
                aPublicationEndpointRegistry, aSnapshotPublicationEndpoints, aPublications,
                aOutputPublicationFinalizationActions, true);
    }
    public AIcOutputPublicationExecutionResult {
        publicationEndpointRegistry = Map.copyOf(publicationEndpointRegistry == null ? Map.of() : publicationEndpointRegistry);
        snapshotPublicationEndpoints = Map.copyOf(snapshotPublicationEndpoints == null ? Map.of() : snapshotPublicationEndpoints);
        publications = List.copyOf(publications == null ? List.of() : publications);
        outputPublicationFinalizationActions = List.copyOf(outputPublicationFinalizationActions == null ? List.of() : outputPublicationFinalizationActions);
    }
    public boolean success() {
        return completed && publications.stream().allMatch(AIcPublicationExecutionResult::success)
                && outputPublicationFinalizationActions.stream().allMatch(AIcOutputPublicationExecutionResult::AIcFinalizationSuccess);
    }
    private static boolean AIcFinalizationSuccess(AIcFinalizationActionExecutionResult aNode) {
        return aNode.result() != null && (aNode.result().success() || aNode.result().ignoredFailure());
    }
}
