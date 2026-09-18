package eu.algites.tool.build.credentials.coreimpl;

import eu.algites.tool.build.credentials.coreintf.AIcCredential;
import eu.algites.tool.build.credentials.coreintf.AIcCredentialProfile;
import eu.algites.tool.build.credentials.coreintf.AIiCredentialProvider;
import eu.algites.tool.build.credentials.coreintf.AInCredentialField;
import eu.algites.tool.build.credentials.coreintf.AIxCredentialException;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves credentials injected through process environment variables.
 */
public final class AIcEnvironmentCredentialProvider implements AIiCredentialProvider {
    private final Map<String, String> environment;

    public AIcEnvironmentCredentialProvider() {
        this(System.getenv());
    }

    public AIcEnvironmentCredentialProvider(Map<String, String> aEnvironment) {
        environment = Map.copyOf(Objects.requireNonNull(aEnvironment, "Environment must not be null."));
    }

    @Override
    public Optional<AIcCredential> resolve(AIcCredentialProfile aProfile) {
        Objects.requireNonNull(aProfile, "Credential profile must not be null.");
        EnumMap<AInCredentialField, char[]> locValues = new EnumMap<>(AInCredentialField.class);
        List<String> locMissingRequired = new ArrayList<>();
        int locPresentCount = 0;

        for (AInCredentialField locField : aProfile.getType().getSupportedFields()) {
            String locVariable = aProfile.getEnvironmentVariable(locField);
            String locValue = environment.get(locVariable);
            if (locValue != null && !locValue.isEmpty()) {
                locValues.put(locField, locValue.toCharArray());
                locPresentCount++;
            } else if (aProfile.getType().getRequiredFields().contains(locField)) {
                locMissingRequired.add(locVariable);
            }
        }

        if (locPresentCount == 0) {
            return Optional.empty();
        }
        if (!locMissingRequired.isEmpty()) {
            locValues.values().forEach(locValue -> java.util.Arrays.fill(locValue, '\0'));
            throw new AIxCredentialException(
                "Credential profile '" + aProfile.getId() + "' is only partially defined in the environment. " +
                    "Missing: " + String.join(", ", locMissingRequired) + "."
            );
        }

        try {
            return Optional.of(new AIcCredential(locValues));
        } finally {
            locValues.values().forEach(locValue -> java.util.Arrays.fill(locValue, '\0'));
        }
    }

    public static List<String> getRequiredEnvironmentVariables(AIcCredentialProfile aProfile) {
        return aProfile.getType().getRequiredFields().stream().map(aProfile::getEnvironmentVariable).toList();
    }

    public static List<String> getOptionalEnvironmentVariables(AIcCredentialProfile aProfile) {
        return aProfile.getType().getOptionalFields().stream().map(aProfile::getEnvironmentVariable).toList();
    }
}
