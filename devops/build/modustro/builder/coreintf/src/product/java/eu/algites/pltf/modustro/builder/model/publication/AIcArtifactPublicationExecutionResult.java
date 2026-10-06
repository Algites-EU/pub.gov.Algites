package eu.algites.pltf.modustro.builder.model.publication;

import java.util.List;

/** Complete immutable publication result for one logical artifact. */
public record AIcArtifactPublicationExecutionResult(
        String artifactIdentity,
        String artifactPath,
        String versionScopeId,
        String version,
        AInPublicationStability stability,
        List<AIcOutputPublicationExecutionResult> outputs,
        List<AIcFinalizationActionExecutionResult> artifactPublicationFinalizationActions) {
    public AIcArtifactPublicationExecutionResult {
        outputs = List.copyOf(outputs == null ? List.of() : outputs);
        artifactPublicationFinalizationActions = List.copyOf(artifactPublicationFinalizationActions == null ? List.of() : artifactPublicationFinalizationActions);
    }
    public boolean success() {
        return outputs.stream().allMatch(AIcOutputPublicationExecutionResult::success)
                && artifactPublicationFinalizationActions.stream().allMatch(loc -> loc.failureHandled());
    }
}
