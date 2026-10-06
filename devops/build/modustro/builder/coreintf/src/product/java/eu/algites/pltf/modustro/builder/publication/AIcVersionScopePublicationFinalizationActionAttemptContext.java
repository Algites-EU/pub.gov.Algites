package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.*;
import java.time.Instant;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;

/** Context supplied to exactly one Version Scope publication-finalization attempt. */
public record AIcVersionScopePublicationFinalizationActionAttemptContext(
        AIcVersionScopePublicationFinalizationAction action,
        AIcVersionScopePublicationExecutionResult versionScopeExecution,
        int attemptNumber,
        int maximumAttemptCount,
        Long attemptTimeoutMillis,
        Instant deadline,
        Map<String, Map<String, String>> credentialsByEndpointId,
        AIiCancellationToken cancellationToken,
        AIiPublicationProgressReporter progressReporter,
        AIiPublicationCredentialResolver credentialResolver) {
    /** Supplies credentials retained by callers that already resolved their endpoint profiles. */
    public AIcVersionScopePublicationFinalizationActionAttemptContext(
            AIcVersionScopePublicationFinalizationAction aAction, AIcVersionScopePublicationExecutionResult aVersionScopeExecution,
            int aAttemptNumber, int aMaximumAttemptCount, Long aAttemptTimeoutMillis, Instant aDeadline,
            Map<String, Map<String, String>> aCredentialsByEndpointId, AIiCancellationToken aCancellationToken,
            AIiPublicationProgressReporter aProgressReporter) {
        this(aAction, aVersionScopeExecution, aAttemptNumber, aMaximumAttemptCount, aAttemptTimeoutMillis, aDeadline,
                aCredentialsByEndpointId, aCancellationToken, aProgressReporter,
                AIcRetainedCredentialResolver(aCredentialsByEndpointId));
    }
    public AIcVersionScopePublicationFinalizationActionAttemptContext {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(versionScopeExecution, "versionScopeExecution");
        Map<String, Map<String, String>> locCredentials = new LinkedHashMap<>();
        if (credentialsByEndpointId != null) credentialsByEndpointId.forEach((aId, aValues) -> locCredentials.put(aId, Map.copyOf(aValues)));
        credentialsByEndpointId = Map.copyOf(locCredentials);
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Objects.requireNonNull(progressReporter, "progressReporter");
        Objects.requireNonNull(credentialResolver, "credentialResolver");
    }

    /** Resolves only the endpoint used by this attempt, inside scheduler retry/timeout/failure policy. */
    public Map<String, String> credentialsFor(AIcPublicationEndpoint aEndpoint) throws Exception {
        return Map.copyOf(credentialResolver.resolve(aEndpoint));
    }

    private static AIiPublicationCredentialResolver AIcRetainedCredentialResolver(Map<String, Map<String, String>> aValues) {
        Map<String, Map<String, String>> locSnapshot = new LinkedHashMap<>();
        if (aValues != null) aValues.forEach((aId, aFields) -> locSnapshot.put(aId, Map.copyOf(aFields)));
        return aEndpoint -> locSnapshot.getOrDefault(aEndpoint.id(), Map.of());
    }
}
