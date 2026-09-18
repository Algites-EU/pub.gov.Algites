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
                new AIcEnvironmentCredentialProvider(),
                new AIcCredentialStoreProvider()
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
            .append("Provide the required environment variables:");
        for (String locVariable : AIcEnvironmentCredentialProvider.getRequiredEnvironmentVariables(aProfile)) {
            locMessage.append(System.lineSeparator()).append("  ").append(locVariable);
        }
        List<String> locOptional = AIcEnvironmentCredentialProvider.getOptionalEnvironmentVariables(aProfile);
        if (!locOptional.isEmpty()) {
            locMessage.append(System.lineSeparator()).append("Optional variables:");
            for (String locVariable : locOptional) {
                locMessage.append(System.lineSeparator()).append("  ").append(locVariable);
            }
        }
        locMessage.append(System.lineSeparator())
            .append("Alternatively provision the profile with the Algites credential CLI into an available OS credential store.");
        providers.stream()
            .filter(AIcCredentialStoreProvider.class::isInstance)
            .map(AIcCredentialStoreProvider.class::cast)
            .findFirst()
            .ifPresent(locStoreProvider -> locMessage.append(System.lineSeparator())
                .append(locStoreProvider.buildUnavailableStoreMessage()));
        throw new AIxCredentialException(locMessage.toString());
    }
}
