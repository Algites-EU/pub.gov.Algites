package eu.algites.tool.build.credentials.coreintf;

/**
 * Availability status reported by one persistent credential-store backend.
 */
public enum AInCredentialStoreAvailabilityStatus {
    AVAILABLE,
    UNSUPPORTED_PLATFORM,
    MISSING_DEPENDENCY,
    SERVICE_UNAVAILABLE,
    SERVICE_LOCKED,
    ERROR
}
