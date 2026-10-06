package eu.algites.pltf.modustro.builder.model.publication;

import java.util.Map;
import java.util.Objects;

/** Flat finalization action executed after the complete publication graph of one Version Scope. */
public record AIcVersionScopePublicationFinalizationAction(
        String id, boolean executionEnabled, String versionScopePublicationFinalizationActionAdapter, int executionOrder,
        AInFinalizationActionFailurePolicy failurePolicy, int retryCount, long waitForNextAttemptMillis,
        Long attemptTimeoutMillis, boolean showProgressIfPossible, Map<String, Object> configuration) {
    public AIcVersionScopePublicationFinalizationAction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(failurePolicy, "failurePolicy");
        if (id.isBlank()) throw new IllegalArgumentException("Version Scope publication finalization action id must not be blank.");
        if (executionEnabled && (versionScopePublicationFinalizationActionAdapter == null || versionScopePublicationFinalizationActionAdapter.isBlank()))
            throw new IllegalArgumentException("ExecutionEnabled Version Scope publication finalization action requires VersionScopePublicationFinalizationActionAdapter.");
        if (retryCount < 0 || waitForNextAttemptMillis < 0L || attemptTimeoutMillis != null && attemptTimeoutMillis <= 0L)
            throw new IllegalArgumentException("Invalid Version Scope publication finalization retry/timeout policy.");
        configuration = AIcPublicationValues.freeze(configuration);
    }
}
