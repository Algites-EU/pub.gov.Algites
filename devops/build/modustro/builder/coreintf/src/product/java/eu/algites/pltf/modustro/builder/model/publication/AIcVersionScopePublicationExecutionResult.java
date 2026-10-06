package eu.algites.pltf.modustro.builder.model.publication;

import java.util.List;

/** Complete immutable execution tree of one Version Scope publication attempt. */
public record AIcVersionScopePublicationExecutionResult(
        String versionScopeId,
        String version,
        AInPublicationStability stability,
        AInVersionScopePublicationAttemptState state,
        List<AIcArtifactPublicationExecutionResult> artifacts,
        List<AIcFinalizationActionExecutionResult> versionScopePublicationFinalizationActions) {
    public AIcVersionScopePublicationExecutionResult {
        artifacts = List.copyOf(artifacts == null ? List.of() : artifacts);
        versionScopePublicationFinalizationActions = List.copyOf(versionScopePublicationFinalizationActions == null ? List.of() : versionScopePublicationFinalizationActions);
    }
    public boolean success() { return state == AInVersionScopePublicationAttemptState.COMPLETE; }
}
