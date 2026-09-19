package eu.algites.tool.build.credentials.coreimpl;

import eu.algites.tool.build.credentials.coreintf.AIcCredential;
import eu.algites.tool.build.credentials.coreintf.AIcCredentialProfile;
import eu.algites.tool.build.credentials.coreintf.AIiCredentialProvider;
import eu.algites.tool.build.credentials.coreintf.AIxCredentialException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Ordered credential-provider chain used by builds and command-line tooling.
 */
public final class AIcCredentialResolver {
    private final List<AIiCredentialProvider> providers;

    public AIcCredentialResolver(List<AIiCredentialProvider> aProviders) {
        providers = List.copyOf(Objects.requireNonNull(aProviders, "Credential providers must not be null."));
    }

    public static AIcCredentialResolver standard() {
        return new AIcCredentialResolver(
            List.of(
                new AIcCredentialDocumentProvider()
            )
        );
    }

    public Optional<AIcCredential> resolve(AIcCredentialProfile aProfile) {
        Objects.requireNonNull(aProfile, "Credential profile must not be null.");
        for (AIiCredentialProvider locProvider : providers) {
            Optional<AIcCredential> locCredential = locProvider.resolve(aProfile);
            if (locCredential.isPresent()) {
                return locCredential;
            }
        }
        return Optional.empty();
    }

    public AIcCredential require(AIcCredentialProfile aProfile) {
        Optional<AIcCredential> locCredential = resolve(aProfile);
        if (locCredential.isPresent()) {
            return locCredential.get();
        }

        StringBuilder locMessage = new StringBuilder()
            .append("Credential profile '").append(aProfile.getId()).append("' with type '")
            .append(aProfile.getType().getId()).append("' is not available.")
            .append(System.lineSeparator())
            .append("Provide the profile through the universal ALGITES_DEVOPS_BUILD_REPOSITORY_CREDENTIALS document in the environment or in an available Algites OS credential store.");
        throw new AIxCredentialException(locMessage.toString());
    }
}
