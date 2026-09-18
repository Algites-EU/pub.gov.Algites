package eu.algites.tool.build.credentials.coreintf;

import java.util.Optional;

/**
 * Resolves a complete credential profile from one runtime source.
 */
public interface AIiCredentialProvider {
    Optional<AIcCredential> resolve(AIcCredentialProfile aProfile);
}
