package eu.algites.tool.build.credentials.coreintf;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory credential value with explicit wiping support.
 */
public final class AIcCredential implements AutoCloseable {
    private final EnumMap<AInCredentialField, char[]> values = new EnumMap<>(AInCredentialField.class);

    public AIcCredential(Map<AInCredentialField, char[]> aValues) {
        Objects.requireNonNull(aValues, "Credential values must not be null.");
        aValues.forEach((locField, locValue) -> {
            if (locField == null || locValue == null) {
                throw new IllegalArgumentException("Credential fields and values must not be null.");
            }
            values.put(locField, Arrays.copyOf(locValue, locValue.length));
        });
    }

    public boolean contains(AInCredentialField aField) {
        return values.containsKey(aField);
    }

    public Optional<char[]> getValue(AInCredentialField aField) {
        Objects.requireNonNull(aField, "Credential field must not be null.");
        char[] locValue = values.get(aField);
        return locValue == null
            ? Optional.empty()
            : Optional.of(Arrays.copyOf(locValue, locValue.length));
    }

    public boolean satisfies(AIcCredentialProfile aProfile) {
        Objects.requireNonNull(aProfile, "Credential profile must not be null.");
        return aProfile.getType().getRequiredFields().stream().allMatch(values::containsKey);
    }

    @Override
    public void close() {
        values.values().forEach(locValue -> Arrays.fill(locValue, '\0'));
        values.clear();
    }
}
