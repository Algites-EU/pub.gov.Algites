package eu.algites.pltf.modustro.builder.publication;

import eu.algites.pltf.modustro.builder.model.publication.AIcPublishingEndpoint;

import java.util.Map;

/** Resolves credential values for one effective publishing endpoint. */
@FunctionalInterface
public interface AIiPublishingCredentialResolver {
    /** Returns immutable-compatible credential values for the endpoint, or an empty map when no credentials are required. */
    Map<String, String> resolve(AIcPublishingEndpoint aEndpoint);
}
