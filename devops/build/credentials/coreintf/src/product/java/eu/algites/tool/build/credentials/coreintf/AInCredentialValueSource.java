package eu.algites.tool.build.credentials.coreintf;

/**
 * Closed set of algorithms for resolving a credential field value.
 */
public enum AInCredentialValueSource {
    DIRECT_VALUE,
    FILE_CONTENT,
    SECRET_CONTENT,
    ENVIRONMENT_VARIABLE_CONTENT;

    public static AInCredentialValueSource fromId(String aId) {
        if (aId == null) {
            throw new IllegalArgumentException("Credential value source must not be null.");
        }
        try {
            return valueOf(aId.trim());
        } catch (IllegalArgumentException aException) {
            throw new IllegalArgumentException("Unsupported credential value source: " + aId, aException);
        }
    }
}
