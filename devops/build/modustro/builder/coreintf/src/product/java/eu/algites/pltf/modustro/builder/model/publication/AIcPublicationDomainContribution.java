package eu.algites.pltf.modustro.builder.model.publication;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable result handoff from one isolated build domain, containing no resolved credential values. */
public record AIcPublicationDomainContribution(
        String invocationId,
        String domainId,
        List<AIcVersionScopePublicationExecutionResult> versionScopes,
        Map<String, Object> metadata) {
    public AIcPublicationDomainContribution {
        Objects.requireNonNull(invocationId, "invocationId");
        Objects.requireNonNull(domainId, "domainId");
        if (invocationId.isBlank()) throw new IllegalArgumentException("Publication invocation Id must not be blank.");
        if (!domainId.equals(".") && (!domainId.matches("[^/]+(?:/[^/]+)*")
                || List.of(domainId.split("/")).stream().anyMatch(aPart -> aPart.equals(".") || aPart.equals("..")))) {
            throw new IllegalArgumentException("Publication domain must be a canonical repository-relative path.");
        }
        versionScopes = List.copyOf(versionScopes == null ? List.of() : versionScopes);
        metadata = AIcPublicationValues.freeze(metadata);
    }
}
