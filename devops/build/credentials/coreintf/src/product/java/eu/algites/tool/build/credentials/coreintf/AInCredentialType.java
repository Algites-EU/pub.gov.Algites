package eu.algites.tool.build.credentials.coreintf;

import java.util.List;
import java.util.Locale;

/**
 * Closed set of authentication mechanisms supported by the Algites credential model.
 */
public enum AInCredentialType {
    BASIC(
        "basic",
        List.of(AInCredentialField.USERNAME, AInCredentialField.PASSWORD),
        List.of()
    ),
    BEARER(
        "bearer",
        List.of(AInCredentialField.TOKEN),
        List.of()
    ),
    API_KEY(
        "api-key",
        List.of(AInCredentialField.API_KEY),
        List.of()
    ),
    CERTIFICATE(
        "certificate",
        List.of(AInCredentialField.CERTIFICATE),
        List.of(AInCredentialField.PRIVATE_KEY, AInCredentialField.PRIVATE_KEY_PASSWORD)
    );

    private final String id;
    private final List<AInCredentialField> requiredFields;
    private final List<AInCredentialField> optionalFields;

    AInCredentialType(
        String aId,
        List<AInCredentialField> aRequiredFields,
        List<AInCredentialField> aOptionalFields
    ) {
        id = aId;
        requiredFields = List.copyOf(aRequiredFields);
        optionalFields = List.copyOf(aOptionalFields);
    }

    public String getId() {
        return id;
    }

    public String getEnvironmentSegment() {
        return id.toUpperCase(Locale.ROOT).replace('-', '_');
    }

    public List<AInCredentialField> getRequiredFields() {
        return requiredFields;
    }

    public List<AInCredentialField> getOptionalFields() {
        return optionalFields;
    }

    public List<AInCredentialField> getSupportedFields() {
        return java.util.stream.Stream.concat(requiredFields.stream(), optionalFields.stream()).toList();
    }

    public static AInCredentialType fromId(String aId) {
        if (aId == null) {
            throw new IllegalArgumentException("Credential type id must not be null.");
        }
        String locId = aId.trim().toLowerCase(Locale.ROOT);
        for (AInCredentialType locType : values()) {
            if (locType.id.equals(locId)) {
                return locType;
            }
        }
        throw new IllegalArgumentException("Unsupported credential type: " + aId);
    }
}
