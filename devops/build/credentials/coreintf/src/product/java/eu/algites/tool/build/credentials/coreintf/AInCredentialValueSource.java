package eu.algites.tool.build.credentials.coreintf;

/**
 * Closed set of algorithms for resolving a credential field value.
 */
public enum AInCredentialValueSource {
    DIRECT_VALUE("direct_value"),
    FILE_CONTENT("file_content"),
    SECRET_CONTENT("secret_content"),
    ENVIRONMENT_VARIABLE_CONTENT("environment_variable_content");

    private final String id;

    AInCredentialValueSource(String aId) {
        id = aId;
    }

    public String getId() {
        return id;
    }

    public static AInCredentialValueSource fromId(String aId) {
        if (aId == null) {
            throw new IllegalArgumentException("Credential value source must not be null.");
        }
        String locId = aId.trim();
        for (AInCredentialValueSource locSource : values()) {
            if (locSource.id.equals(locId)) {
                return locSource;
            }
        }
        throw new IllegalArgumentException("Unsupported credential value source: " + aId);
    }
}
