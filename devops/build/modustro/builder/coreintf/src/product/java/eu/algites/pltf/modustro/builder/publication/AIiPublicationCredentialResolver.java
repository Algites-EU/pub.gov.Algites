package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublicationEndpoint;

import java.util.Map;

/** Resolves credential values for one effective publication endpoint. */
@FunctionalInterface
public interface AIiPublicationCredentialResolver {
    /** Returns immutable-compatible credential values for the endpoint, or an empty map when no credentials are required. */
    Map<String, String> resolve(AIcPublicationEndpoint aEndpoint);
}
