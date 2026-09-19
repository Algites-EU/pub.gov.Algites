package eu.algites.tool.build.credentials.coreintf;

import java.util.Locale;

/**
 * Canonical fields supported by Algites credential types.
 */
public enum AInCredentialField {
    USERNAME("username"),
    PASSWORD("password"),
    TOKEN("token"),
    API_KEY("apiKey"),
    CERTIFICATE("certificate"),
    PRIVATE_KEY("privateKey"),
    PRIVATE_KEY_PASSWORD("privateKeyPassword");

    private final String id;

    AInCredentialField(String aId) {
        id = aId;
    }

    public String getId() {
        return id;
    }

    public static AInCredentialField fromId(String aId) {
        if (aId == null) {
            throw new IllegalArgumentException("Credential field id must not be null.");
        }
        String locId = aId.trim();
        for (AInCredentialField locField : values()) {
            if (locField.id.equals(locId) || locField.name().equals(locId.toUpperCase(Locale.ROOT))) {
                return locField;
            }
        }
        throw new IllegalArgumentException("Unsupported credential field: " + aId);
    }
}
