package eu.algites.tool.build.credentials.coreintf;

import java.util.List;
import java.util.Objects;

/**
 * Diagnostic availability result for a persistent credential-store backend.
 */
public final class AIcCredentialStoreAvailability {
    private final AInCredentialStoreAvailabilityStatus status;
    private final String message;
    private final List<String> remediation;

    public AIcCredentialStoreAvailability(
        AInCredentialStoreAvailabilityStatus aStatus,
        String aMessage,
        List<String> aRemediation
    ) {
        status = Objects.requireNonNull(aStatus, "Credential-store status must not be null.");
        message = Objects.requireNonNull(aMessage, "Credential-store message must not be null.");
        remediation = List.copyOf(Objects.requireNonNull(aRemediation, "Credential-store remediation must not be null."));
    }

    public AInCredentialStoreAvailabilityStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getRemediation() {
        return remediation;
    }

    public boolean isAvailable() {
        return status == AInCredentialStoreAvailabilityStatus.AVAILABLE;
    }

    public static AIcCredentialStoreAvailability available(String aMessage) {
        return new AIcCredentialStoreAvailability(AInCredentialStoreAvailabilityStatus.AVAILABLE, aMessage, List.of());
    }
}
