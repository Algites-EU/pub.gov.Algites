package eu.algites.tool.build.credentials.coreintf;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Stable, non-secret description of a named credential profile.
 */
public final class AIcCredentialProfile {
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    private final String id;
    private final AInCredentialType type;
    private final Map<String, String> configuration;

    public AIcCredentialProfile(String aId, AInCredentialType aType) {
        this(aId, aType, Map.of());
    }

    public AIcCredentialProfile(String aId, AInCredentialType aType, Map<String, String> aConfiguration) {
        Objects.requireNonNull(aId, "Credential profile id must not be null.");
        Objects.requireNonNull(aType, "Credential profile type must not be null.");
        Objects.requireNonNull(aConfiguration, "Credential profile configuration must not be null.");
        if (!ID_PATTERN.matcher(aId).matches()) {
            throw new IllegalArgumentException(
                "Credential profile id must use canonical lowercase dash-separated form: " + aId
            );
        }
        id = aId;
        type = aType;
        configuration = Map.copyOf(aConfiguration);
    }

    public String getId() {
        return id;
    }

    public AInCredentialType getType() {
        return type;
    }

    public Map<String, String> getConfiguration() {
        return configuration;
    }

    public String getEnvironmentPrefix() {
        return "ALGITES_CREDENTIAL_" +
            id.toUpperCase(Locale.ROOT).replace('-', '_') +
            "_" + type.getEnvironmentSegment();
    }

    public String getEnvironmentVariable(AInCredentialField aField) {
        Objects.requireNonNull(aField, "Credential field must not be null.");
        if (!type.getSupportedFields().contains(aField)) {
            throw new IllegalArgumentException(
                "Credential field " + aField + " is not supported by credential type " + type.getId() + "."
            );
        }
        return getEnvironmentPrefix() + "_" + aField.name();
    }

    public String getStorageKey() {
        return id + "/" + type.getId();
    }
}
